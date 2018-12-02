package com.featzima.komedia.dsl

class MediaFormatDsl {
    val properties = mutableMapOf<String, Any>()

    fun property(block : () -> Pair<String, Any>) {
        val (key, value) = block()
        properties[key] = value
    }

}