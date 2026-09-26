package com.lin0721.linmusic.feature.recognition.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.lin0721.linmusic.core.log.AppLogger

private const val TAG = "PlaybackRecognitionLauncher"

// 识别页、磁贴跳板页启动或取消后台内录的统一入口
class PlaybackRecognitionLauncher(
    private val context: Context,
    private val state: PlaybackRecognitionState
) {

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    // resultCode / data 为录屏授权弹窗的返回值；返回服务是否成功拉起
    fun start(resultCode: Int, data: Intent): Boolean {
        if (!isSupported) return false
        return runCatching {
            ContextCompat.startForegroundService(context, PlaybackRecognitionService.startIntent(context, resultCode, data))
        }.onFailure { AppLogger.e(TAG, "启动内录服务失败", it) }.isSuccess
    }

    fun cancel() {
        if (!state.isRunning) return
        runCatching { context.startService(PlaybackRecognitionService.cancelIntent(context)) }
            .onFailure { AppLogger.e(TAG, "取消内录失败", it) }
    }
}
