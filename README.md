# KoMedia

KoMedia is a DSL wrapper for the standard Android media tools.

## Description

The library uses experimental Kotlin channels and is based on three Android components:
* MediaExtractor,
* MediaCodec,
* MediaMuxer

## Download

Download via Gradle:

```gradle
implementation 'com.featzima:komedia:0.1.0'
```

## How To Use
1. Create pipeline with simple DSL and invoke it:
```kotlin
val sampleFile = File(appContext.cacheDir, "input.mp3")
val resultFile = File(appContext.cacheDir, "output.mp3")

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
```

### License

```
Copyright 2019 Dmytro Glynskyi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```