package com.featzima.komedia.dsl

import com.featzima.komedia.pipeline.PipelineElement
import java.io.File

class MuxDsl {
    var outputFile: File? = null
    val element: PipelineElement.Muxer by lazy {
        PipelineElement.Muxer(outputFile!!)
    }
}