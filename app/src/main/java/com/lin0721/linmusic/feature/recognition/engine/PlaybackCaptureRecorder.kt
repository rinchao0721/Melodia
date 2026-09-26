package com.lin0721.linmusic.feature.recognition.engine

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import com.lin0721.linmusic.feature.recognition.domain.RecognitionException
import com.lin0721.linmusic.feature.recognition.domain.RecognitionFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

// 系统内录：采其他 App 的媒体/游戏声音，排除 Melodia 自身。声明禁止被内录的 App 会录到静音
@RequiresApi(Build.VERSION_CODES.Q)
class PlaybackCaptureRecorder(private val projection: MediaProjection) : AudioCapture {

    @SuppressLint("MissingPermission") // AudioPlaybackCapture 同样要求 RECORD_AUDIO，由发起方申请
    override fun capture(): Flow<FloatArray> = flow {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .excludeUid(Process.myUid())
            .build()
        val opened = try {
            openAudioRecord("内录") { rate, encoding, bufferSize ->
                AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(rate)
                            .setEncoding(encoding)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(config)
                    .build()
            }
        } catch (e: SecurityException) {
            throw RecognitionException(RecognitionFailure.PERMISSION_DENIED, "录音权限未授予", e)
        } ?: throw RecognitionException(RecognitionFailure.RECORDER_UNAVAILABLE, "没有可用的内录配置")
        emitAll(opened.asPcmFlow())
    }.flowOn(Dispatchers.IO)
}
