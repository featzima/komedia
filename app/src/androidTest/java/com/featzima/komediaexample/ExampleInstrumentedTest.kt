package com.featzima.komediaexample

import android.media.*
import android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM
import android.util.Log
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlin.experimental.and
import kotlin.system.measureTimeMillis


fun ByteBuffer.transferToAsMuchAsPossible(destinationBuffer: ByteBuffer): Int {
    val nTransfer = Math.min(destinationBuffer.remaining(), remaining())
    if (nTransfer > 0) {
        destinationBuffer.put(array(), arrayOffset() + position(), nTransfer)
        position(position() + nTransfer)
    }
    return nTransfer
}

fun ByteBuffer.exactlyCopy(): ByteBuffer {
    val copy = ByteBuffer.allocate(limit())
    copy.put(this)
    copy.position(0)
    return copy
}

suspend fun MediaExtractor.channel(capacity: Int = 4096 * 8, autoRelease: Boolean = true): ReceiveChannel<Pair<Long, ByteBuffer>> {
    return GlobalScope.produce(context = coroutineContext) {
        val buffer1 = ByteBuffer.allocate(capacity)
        val buffer2 = ByteBuffer.allocate(capacity)
        var buffersPair = Pair(buffer1, buffer2)

        while (readSampleData(buffersPair.first, 0) >= 0) {
            send(Pair(sampleTime, buffersPair.first))
            buffersPair = buffersPair.swiped()
            advance()
        }
        if (autoRelease) release()
        send(Pair(0L, ByteBuffer.allocate(0)))
    }
}

fun <A, B> Pair<A, B>.swiped() = Pair(first = second, second = first)

fun MediaCodec.input2(sampleTimeAndBuffer: Pair<Long, ByteBuffer>) {
//    Log.e("!!!", "MediaCodec.input2(${sampleTimeAndBuffer})")
    val sampleTime = sampleTimeAndBuffer.first
    val buffer = sampleTimeAndBuffer.second
    while (true) {
        val inputBufferId = dequeueInputBuffer(3000)
//        Log.e("!!!", "dequeueInputBuffer($inputBufferId)")
        if (inputBufferId >= 0) {
            val inputBuffer = getInputBuffer(inputBufferId)
            inputBuffer.put(buffer)
            inputBuffer.position(0)
            inputBuffer.limit(buffer.limit())
            val flags = if (buffer.limit() == 0) BUFFER_FLAG_END_OF_STREAM else 0
            queueInputBuffer(inputBufferId, 0, buffer.limit(), sampleTime, flags)
            return
        }
    }
}

fun MediaCodec.input(bufferAndInfo: Pair<ByteBuffer, MediaCodec.BufferInfo>) {
//    Log.e("!!!", "MediaCodec.input(${bufferAndInfo.first}, ${bufferAndInfo.second})")
    val bufferInfo = bufferAndInfo.second
    val buffer = bufferAndInfo.first
    while (true) {
        val inputBufferId = dequeueInputBuffer(3000)
//        Log.e("!!!", "input:dequeueOutputBuffer($inputBufferId)")
        if (inputBufferId >= 0) {
            val inputBuffer = getInputBuffer(inputBufferId)
            buffer.transferToAsMuchAsPossible(inputBuffer)
            queueInputBuffer(
                inputBufferId,
                0,
                inputBuffer.position(),
                bufferInfo.presentationTimeUs,
                bufferInfo.flags
            )
            if (buffer.remaining() == 0) return
        }
    }
}

fun MediaMuxer.input(bufferAndInfo: Pair<ByteBuffer, MediaCodec.BufferInfo>) {
//    Log.e("!!!", "MediaMuxer.input(${bufferAndInfo.first}, ${bufferAndInfo.second.presentationTimeUs}, ${bufferAndInfo.second.flags})")
    val buffer = bufferAndInfo.first
    val bufferInfo = bufferAndInfo.second
    if (bufferInfo.flags != 4) {
        writeSampleData(0, buffer, bufferInfo)
    }
}

suspend fun MediaCodec.channel(timeoutUs: Long = 3000): ReceiveChannel<Pair<ByteBuffer, MediaCodec.BufferInfo>> {
    return GlobalScope.produce(context = coroutineContext) {
        while (true) {
            val bufferInfo = MediaCodec.BufferInfo()
            val outputBufferId = dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputBufferId >= 0) {
//                Log.e("!!!", "dequeueOutputBuffer($outputBufferId)")
                val outputBuffer = getOutputBuffer(outputBufferId)
//                Log.e("!!!", "MediaCodec.send(timeUs = ${bufferInfo.presentationTimeUs})")
                if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    bufferInfo.size = 0
                }

                send(Pair(outputBuffer.exactlyCopy(), bufferInfo))
                releaseOutputBuffer(outputBufferId, false)
            }
            if(outputBufferId == -2 ){
                Log.e("!-!", ": \\")
            }
            if (bufferInfo.flags and BUFFER_FLAG_END_OF_STREAM > 0) {
                break
            }
        }
    }
}

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getTargetContext()
        assertEquals("com.featzima.komediaexample", appContext.packageName)

        val ouputFile = File(appContext.filesDir, "output.mp3")

        runBlocking {
            val convertationTime = measureTimeMillis {
                val extractor = MediaExtractor()
                extractor.setDataSource(appContext.assets.openFd("Metallica.mp3"))
                extractor.selectTrack(0)

                val sourceMediaFormat = extractor.getTrackFormat(0)
                val decoder = MediaCodec.createDecoderByType("audio/mpeg")
                decoder.configure(sourceMediaFormat, null, null, 0)
                decoder.start()

                val outputMediaFormat = MediaFormat()
                outputMediaFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm")
                outputMediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2)
                outputMediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100) // 11025
                outputMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                outputMediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE)
                val encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
                encoder.configure(outputMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoder.start()

                val muxer = MediaMuxer(ouputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer.addTrack(encoder.outputFormat)
                muxer.start()


                val extractorDeferred = async(Dispatchers.Default) {
                    extractor.channel().consumeEach {
                        //                    Log.e("---", "$it")
                        decoder.input2(it)
                    }
                }

                val outputWav = ByteBuffer.allocate(20000000)
                val decoderDeferred = async(Dispatchers.Default) {
                    decoder.channel().consumeEach {
                        encoder.input(it)
//                    outputWav.put(it.first)
                    }
                }
                val encoderDeferred = async(Dispatchers.Default) {
                    encoder.channel().consumeEach {
                        muxer.input(it)
                    }
                }

                extractorDeferred.await()
                decoderDeferred.await()
                encoderDeferred.await()

                outputWav.position(0)
                properWAV(outputWav)
//            delay(4000)

                muxer.stop()
                muxer.release()

                Unit
            }
            Log.e("!!!!!!!!!", "$convertationTime")
        }
    }


    private fun properWAV(pcmBuffer: ByteBuffer) {
        try {
            val mySubChunk1Size: Long = 16
            val myBitsPerSample = 16
            val myFormat = 1
            val myChannels: Long = 2
            val mySampleRate: Long = 44100 //22100
            val myByteRate = mySampleRate * myChannels * myBitsPerSample.toLong() / 8
            val myBlockAlign = (myChannels * myBitsPerSample / 8).toInt()

            val myDataSize = pcmBuffer.limit().toLong()
            val myChunk2Size = myDataSize * myChannels * myBitsPerSample.toLong() / 8
            val myChunkSize = 36 + myChunk2Size

            val os: OutputStream
            os = FileOutputStream(File(InstrumentationRegistry.getTargetContext().filesDir, "proper.wav"))
            val bos = BufferedOutputStream(os)
            val outFile = DataOutputStream(bos)

            outFile.writeBytes("RIFF")                                          // 00 - RIFF
            outFile.write(intToByteArray(myChunkSize.toInt()), 0, 4)            // 04 - how big is the rest of this file?
            outFile.writeBytes("WAVE")                                          // 08 - WAVE
            outFile.writeBytes("fmt ")                                          // 12 - fmt
            outFile.write(intToByteArray(mySubChunk1Size.toInt()), 0, 4)        // 16 - size of this chunk
            outFile.write(shortToByteArray(myFormat.toShort()), 0, 2)           // 20 - what is the audio format? 1 for PCM = Pulse Code Modulation
            outFile.write(shortToByteArray(myChannels.toShort()), 0, 2)         // 22 - mono or stereo? 1 or 2?  (or 5 or ???)
            outFile.write(intToByteArray(mySampleRate.toInt()), 0, 4)           // 24 - samples per second (numbers per second)
            outFile.write(intToByteArray(myByteRate.toInt()), 0, 4)             // 28 - bytes per second
            outFile.write(shortToByteArray(myBlockAlign.toShort()), 0, 2)       // 32 - # of bytes in one sample, for all channels
            outFile.write(shortToByteArray(myBitsPerSample.toShort()), 0, 2)    // 34 - how many bits in a sample(number)?  usually 16 or 24
            outFile.writeBytes("data")                                          // 36 - data
            outFile.write(intToByteArray(myDataSize.toInt()), 0, 4)             // 40 - how big is this data chunk
            outFile.write(pcmBuffer.array())                                    // 44 - the actual data itself - just a long string of numbers

            outFile.flush()
            outFile.close()

        } catch (e: IOException) {
            e.printStackTrace()
        }

    }


    private fun intToByteArray(i: Int): ByteArray {
        val b = ByteArray(4)
        b[0] = (i and 0x00FF).toByte()
        b[1] = (i shr 8 and 0x000000FF).toByte()
        b[2] = (i shr 16 and 0x000000FF).toByte()
        b[3] = (i shr 24 and 0x000000FF).toByte()
        return b
    }

    // convert a short to a byte array
    fun shortToByteArray(data: Short): ByteArray {
        /*
         * NB have also tried:
         * return new byte[]{(byte)(data & 0xff),(byte)((data >> 8) & 0xff)};
         *
         */

        return byteArrayOf((data and 0xff).toByte(), (data.toInt().ushr(8) and 0xff).toByte())
    }
}
//
//public suspend inline fun <E> ReceiveChannel<E>.consumeEach(action: suspend (E) -> Unit) =
//    consume {
//        for (e in this) action(e)
//    }
