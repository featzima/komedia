package com.featzima.komedia.pipeline

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaFormat.*
import android.media.MediaMuxer
import android.util.Log
import com.featzima.komedia.CodecEvent
import com.featzima.komedia.channel
import com.featzima.komedia.input
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.first
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

class Pipeline : CoroutineScope {

    val elementJobs = mutableListOf<Job>()
    val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
        Log.e("komedia", "Pipeline", throwable)
    }
    val elements = mutableListOf<PipelineElement>()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + exceptionHandler

    operator fun invoke() {
        elements.zipWithNext().forEach { (source, destination) ->
            when (source) {
                is PipelineElement.Extractor -> when (destination) {
                    is PipelineElement.Extractor -> throw PipelineElementCapabilityException(source, destination)
                    is PipelineElement.Decoder -> elementJobs += launch { connect(source, destination) }
                    is PipelineElement.Encoder -> throw PipelineElementCapabilityException(source, destination)
                    is PipelineElement.Muxer -> throw PipelineElementCapabilityException(source, destination)
                }
                is PipelineElement.Decoder -> when (destination) {
                    is PipelineElement.Extractor -> throw PipelineElementCapabilityException(source, destination)
                    is PipelineElement.Decoder -> throw PipelineElementCapabilityException(source, destination)
                    is PipelineElement.Encoder -> elementJobs += launch { connect(source, destination) }
                    is PipelineElement.Muxer -> throw PipelineElementCapabilityException(source, destination)
                }
                is PipelineElement.Encoder -> when (destination) {
                    is PipelineElement.Extractor -> throw PipelineElementCapabilityException(source, destination)
                    is PipelineElement.Decoder -> throw PipelineElementCapabilityException(source, destination)
                    is PipelineElement.Encoder -> throw PipelineElementCapabilityException(source, destination)
                    is PipelineElement.Muxer -> elementJobs += launch { connect(source, destination) }
                }
                is PipelineElement.Muxer -> throw PipelineElementCapabilityException(source, destination)
            }
        }
        runBlocking {
            elementJobs.forEach { it.join() }
        }
    }

    private suspend fun connect(source: PipelineElement.Extractor, destination: PipelineElement.Decoder) {
        val mediaExtractor = source.mediaExtractor
        mediaExtractor.channel().consumeEach { event ->
            when (event) {
                is CodecEvent.Format -> {
                    if (destination.decoderChannel.valueOrNull != null) throw IllegalStateException("Can't reinitialize decoder")
                    val mimeType = event.mediaFormat.getString(MediaFormat.KEY_MIME)
                    destination.decoderChannel.offer(MediaCodec.createDecoderByType(mimeType).apply {
                        configure(event.mediaFormat, null, null, 0)
                        start()
                    })
                }
                is CodecEvent.Data -> {
                    destination.decoderChannel.value.input(event)
                }
            }
        }
        Log.e("komedia", "Extractor::complete")
    }

    private suspend fun connect(source: PipelineElement.Decoder, destination: PipelineElement.Encoder) {
        val decoder = source.decoderChannel.consume { first() }
        decoder.channel().consumeEach { event ->
            when (event) {
                is CodecEvent.Format -> {
                    val outputMediaFormat = event.mediaFormat.apply {
                        destination.mediaFormatProperties.forEach { key, value ->
                            when (value) {
                                is String -> setString(key, value)
                                is Int -> setInteger(key, value)
                                is Long -> setLong(key, value)
                                is ByteBuffer -> setByteBuffer(key, value)
                                is Float -> setFloat(key, value)
                                else -> throw IllegalStateException("Unsupported media format property type")
                            }
                        }
                    }
                    val encoder = MediaCodec.createEncoderByType(outputMediaFormat.getString(KEY_MIME))
                    encoder.configure(outputMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    encoder.start()
                    destination.encoderChannel.send(encoder)
                }
                is CodecEvent.Data -> {
                    val encoder = destination.encoderChannel.consume { first() }
                    encoder.input(event)
                }
            }
        }
        Log.e("komedia", "decoder::complete")
    }

    private suspend fun connect(source: PipelineElement.Encoder, destination: PipelineElement.Muxer) {
        val encoder = source.encoderChannel.consume { first() }
        encoder.channel().consumeEach {event ->
            when (event) {
                is CodecEvent.Format -> {
                    val muxer = MediaMuxer(destination.outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    muxer.addTrack(event.mediaFormat)
                    muxer.start()
                    destination.muxurChannel.offer(muxer)
                }
                is CodecEvent.Data -> {
                    val muxer = destination.muxurChannel.value
                    muxer.input(event)
                }
            }
        }
        Log.e("komedia", "encoder::complete")
        destination.muxurChannel.value.stop()
        destination.muxurChannel.value.release()

        Log.e("komedia", "muxer::complete")
    }
}