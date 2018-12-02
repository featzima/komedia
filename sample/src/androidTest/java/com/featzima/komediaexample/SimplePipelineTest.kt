package com.featzima.komediaexample

import android.media.MediaCodecInfo
import android.media.MediaFormat
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.featzima.komedia.dsl.pipeline
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File


@RunWith(AndroidJUnit4::class)
class SimplePipelineTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getTargetContext()
        val sampleFile = File(appContext.cacheDir, "input.mp3")
        appContext.assets.open("Beethoven_12_Variation.mp3").use { inputStream ->
            sampleFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        val resultFile = File(appContext.cacheDir, "result.aac")
        if (resultFile.exists()) resultFile.delete()

        runBlocking {
            val converterPipeline = pipeline {
                extract {
                    dataSource { path = sampleFile.absolutePath }
                }
                decode {}
                encode {
                    mediaFormat {
                        property { MediaFormat.KEY_BIT_RATE to 128000 }
                        property { MediaFormat.KEY_MIME to MediaFormat.MIMETYPE_AUDIO_AAC }
                        property { MediaFormat.KEY_AAC_PROFILE to MediaCodecInfo.CodecProfileLevel.AACObjectLC }
                    }
                }
                mux {
                    outputFile = resultFile
                }
            }
            converterPipeline.invoke()
        }
    }
}
