package com.featzima.komedia.dsl

import com.featzima.komedia.pipeline.PipelineElement

class DecodeDsl {
    val element: PipelineElement.Decoder by lazy {
        PipelineElement.Decoder()
    }
}