package com.featzima.komedia.pipeline

class PipelineElementCapabilityException(
    val source: PipelineElement,
    val destination: PipelineElement
) : Exception("Can't pass data from ${source::class.java.simpleName} to ${destination::class.java.simpleName}")
