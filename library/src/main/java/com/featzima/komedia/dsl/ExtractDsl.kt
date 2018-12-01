package com.featzima.komedia.dsl

import com.featzima.komedia.pipeline.PipelineElement

class ExtractDsl {
    private var dataSourceDsl: DataSourceDsl? = null
    val element: PipelineElement.Extractor by lazy {
        PipelineElement.Extractor().apply {
            dataSourceDsl!!.fileDescriptor?.apply(mediaExtractor::setDataSource)
            dataSourceDsl!!.path?.apply(mediaExtractor::setDataSource)
        }
    }

    fun dataSource(block: DataSourceDsl.() -> Unit) {
        dataSourceDsl = DataSourceDsl().apply(block)
    }
}