package com.lin0721.linmusic.desktop.platform.windows.audio

import com.lin0721.linmusic.feature.recognition.domain.RecognitionException
import com.lin0721.linmusic.feature.recognition.domain.RecognitionFailure
import com.lin0721.linmusic.feature.recognition.engine.AudioCapture
import com.lin0721.linmusic.feature.recognition.engine.Downsampler
import com.lin0721.linmusic.feature.recognition.engine.RECOGNITION_SAMPLE_RATE
import com.sun.jna.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// 录制系统默认输出设备正在播放的声音（WASAPI 共享模式 loopback）
class WasapiLoopbackCapture : AudioCapture {

    companion object {
        val isSupported: Boolean get() = Platform.isWindows()
    }

    override fun capture(): Flow<FloatArray> = flow {
        if (!isSupported) {
            throw RecognitionException(RecognitionFailure.RECORDER_UNAVAILABLE, "当前系统不支持内录")
        }
        var downsampler: Downsampler? = null
        try {
            loopbackBlocks { ole32 -> openDefaultLoopback(ole32, RECOGNITION_SAMPLE_RATE) }.collect { block ->
                val converter = downsampler ?: Downsampler(block.sampleRate, RECOGNITION_SAMPLE_RATE).also { downsampler = it }
                val output = converter.process(block.samples)
                if (output.isNotEmpty()) emit(output)
            }
        } catch (e: WasapiException) {
            throw RecognitionException(RecognitionFailure.RECORDER_UNAVAILABLE, e.message.orEmpty(), e)
        }
    }
}
