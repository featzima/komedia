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
        appContext.assets.open("Bloch_Prayer.mp3").use { inputStream ->
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
                        setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC)
                        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE)
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
