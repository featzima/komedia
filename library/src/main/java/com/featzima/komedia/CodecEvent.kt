package com.featzima.komedia

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

sealed class CodecEvent {

    data class Format(
        val mediaFormat: MediaFormat
    ) : CodecEvent()

    data class Data(
        val buffer: ByteBuffer,
        val bufferInfo: MediaCodec.BufferInfo
    ) : CodecEvent() {
        override fun toString(): String {
            return "$buffer, bufferInfo.presentationTimeUs=${bufferInfo.presentationTimeUs}, bufferInfo.flags=${bufferInfo.flags}"
        }
    }
}