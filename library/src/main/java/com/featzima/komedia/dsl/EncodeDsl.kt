package com.featzima.komedia.dsl

import android.media.MediaFormat
import com.featzima.komedia.pipeline.PipelineElement

class EncodeDsl {
    val mediaFormat = MediaFormat()
    val element: PipelineElement.Encoder by lazy {
        PipelineElement.Encoder(mediaFormat)
    }

    fun mediaFormat(block: MediaFormat.() -> Unit) {
        mediaFormat.apply(block)
    }
}