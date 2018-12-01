package com.featzima.komedia

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import java.io.*
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlin.experimental.and

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

suspend fun MediaExtractor.channel(
    capacity: Int = 16384,
    trackIndex: Int = 0,
    autoRelease: Boolean = true
): ReceiveChannel<CodecEvent> {
    return GlobalScope.produce(context = coroutineContext) {
        val buffer1 = ByteBuffer.allocate(capacity)
        val buffer2 = ByteBuffer.allocate(capacity)
        var buffersPair = Pair(buffer1, buffer2)

        val mediaFormat = getTrackFormat(trackIndex)
        send(CodecEvent.Format(mediaFormat))
        Log.e("komedia", "extractor.send($mediaFormat)")

        selectTrack(trackIndex)
        while (readSampleData(buffersPair.first, 0) >= 0) {
            val bufferInfo = MediaCodec.BufferInfo()
            bufferInfo.flags = 0
            bufferInfo.presentationTimeUs = sampleTime
            send(CodecEvent.Data(buffersPair.first, bufferInfo))
            Log.e("komedia", "extractor.send($bufferInfo)")
            buffersPair = buffersPair.swiped()
            advance()
        }
        if (autoRelease) release()
        val bufferInfo = MediaCodec.BufferInfo()
        bufferInfo.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
        val codecEvent = CodecEvent.Data(ByteBuffer.allocate(0), bufferInfo)
        Log.e("komedia", "extractor.send($bufferInfo)")
        send(codecEvent)
    }
}

fun <A, B> Pair<A, B>.swiped() = Pair(first = second, second = first)

fun MediaCodec.input2(event: CodecEvent.Data) {
    Log.e("komedia", "extractor -> decoder $event")
    val presentationTimeUs = event.bufferInfo.presentationTimeUs
    val buffer = event.buffer
    while (true) {
        val inputBufferId = dequeueInputBuffer(3000)
        if (inputBufferId >= 0) {
            val inputBuffer = getInputBuffer(inputBufferId)
            inputBuffer.put(buffer)
            inputBuffer.position(0)
            inputBuffer.limit(buffer.limit())
            val flags = if (buffer.limit() == 0) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            queueInputBuffer(inputBufferId, 0, buffer.limit(), presentationTimeUs, flags)
            return
        }
    }
}

fun MediaCodec.input(event: CodecEvent.Data) {
    Log.e("komedia", "decoder -> encoder $event")
    val bufferInfo = event.bufferInfo
    val buffer = event.buffer
    while (true) {
        val inputBufferId = dequeueInputBuffer(3000)
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

fun MediaMuxer.input(event: CodecEvent.Data) {
    Log.e("komedia", "encoder -> muxer $event")
    val buffer = event.buffer
    val bufferInfo = event.bufferInfo
    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM == 0) {
        writeSampleData(0, buffer, bufferInfo)
    }
}

suspend fun MediaCodec.channel(timeoutUs: Long = 3000): ReceiveChannel<CodecEvent.Data> {
    return GlobalScope.produce(context = coroutineContext) {
        while (true) {
            val bufferInfo = MediaCodec.BufferInfo()
            val outputBufferId = dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputBufferId >= 0) {
                val outputBuffer = getOutputBuffer(outputBufferId)
                if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    bufferInfo.size = 0
                }

                send(CodecEvent.Data(outputBuffer.exactlyCopy(), bufferInfo))
                releaseOutputBuffer(outputBufferId, false)
            }
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM > 0) {
                break
            }
        }
    }
}