package com.featzima.komedia.dsl

import com.featzima.komedia.pipeline.PipelineElement

class EncodeDsl {
    var mediaFormatProperties: Map<String, Any>? = null

    val element: PipelineElement.Encoder by lazy {
        PipelineElement.Encoder(mediaFormatProperties!!)
    }

    fun mediaFormat(block: MediaFormatDsl.() -> Unit) {
        mediaFormatProperties = MediaFormatDsl().apply(block).properties
    }
}