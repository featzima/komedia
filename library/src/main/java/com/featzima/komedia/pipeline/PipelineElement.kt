package com.featzima.komedia.pipeline

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import java.io.File

sealed class PipelineElement {

    class Extractor() : PipelineElement() {
        val mediaExtractor = MediaExtractor()
    }

    class Decoder() : PipelineElement() {
        var decoderChannel = ConflatedBroadcastChannel<MediaCodec>()
    }

    class Encoder(val mediaFormat: MediaFormat) : PipelineElement() {
        var encoderChannel = ConflatedBroadcastChannel<MediaCodec>()
    }

    class Muxer(val outputFile: File) : PipelineElement() {
        val muxurChannel = ConflatedBroadcastChannel<MediaMuxer>()
    }
}