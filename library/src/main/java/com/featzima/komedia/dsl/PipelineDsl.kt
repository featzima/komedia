package com.featzima.komedia.dsl

import com.featzima.komedia.pipeline.Pipeline
import com.featzima.komedia.pipeline.PipelineElement

class PipelineDsl {
    val elements = mutableListOf<PipelineElement>()

    fun extract(block: ExtractDsl.() -> Unit) {
        val dsl = ExtractDsl().apply(block)
        elements.add(dsl.element)
    }

    fun decode(block: DecodeDsl.() -> Unit) {
        val dsl = DecodeDsl().apply(block)
        elements.add(dsl.element)
    }

    fun encode(block: EncodeDsl.() -> Unit) {
        val dsl = EncodeDsl().apply(block)
        elements.add(dsl.element)
    }

    fun mux(block: MuxDsl.() -> Unit) {
        val dsl = MuxDsl().apply(block)
        elements.add(dsl.element)
    }
}

fun pipeline(block: PipelineDsl.() -> Unit): Pipeline {
    val dsl = PipelineDsl().apply(block)
    return Pipeline().apply {
        elements.addAll(dsl.elements)
    }
}
