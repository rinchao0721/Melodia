package com.lin0721.linmusic.feature.recognition.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import com.lin0721.linmusic.feature.recognition.domain.RecognitionException
import com.lin0721.linmusic.feature.recognition.domain.RecognitionFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

// 采集源抽象，便于识别流程脱离真机单测
fun interface AudioCapture {
    // 持续发射 8 kHz 单声道 Float32 块，直到收集方取消
    fun capture(): Flow<FloatArray>
}

// 麦克风采集：优先 8 kHz 直采，设备不支持时退回更高采样率再抽样
class MicrophoneRecorder(private val context: Context) : AudioCapture {

    @SuppressLint("MissingPermission") // 权限由界面层申请，未授权时 AudioRecord 抛 SecurityException 并在此转换
    override fun capture(): Flow<FloatArray> = flow {
        val opened = try {
            candidateSources().firstNotNullOfOrNull { source ->
                openAudioRecord("麦克风 source=$source") { rate, encoding, bufferSize ->
                    AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, encoding, bufferSize)
                }
            }
        } catch (e: SecurityException) {
            throw RecognitionException(RecognitionFailure.PERMISSION_DENIED, "麦克风权限未授予", e)
        } ?: throw RecognitionException(RecognitionFailure.RECORDER_UNAVAILABLE, "没有可用的录音配置")
        emitAll(opened.asPcmFlow())
    }.flowOn(Dispatchers.IO)

    // UNPROCESSED 需设备声明支持；VOICE_RECOGNITION 按兼容性要求关闭降噪与自动增益，最后兜底 MIC
    private fun candidateSources(): List<Int> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val unprocessedSupported =
            audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return buildList {
            if (unprocessedSupported) add(MediaRecorder.AudioSource.UNPROCESSED)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }
    }
}
