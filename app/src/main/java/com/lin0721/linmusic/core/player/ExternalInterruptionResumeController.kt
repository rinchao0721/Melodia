package com.lin0721.linmusic.core.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ExternalInterruptionResumeController"
// 防抖窗口：防止短视频快速滑动切屏期间频繁触发起播
private const val DEBOUNCE_DELAY_MS = 1500L
// 打断等待超时时间：超过 10 分钟自动作废，防止用户长时间离开后突发放歌
private const val MAX_INTERRUPTION_WINDOW_MS = 10 * 60 * 1000L

// 外部视频/音频打断后自动恢复播放控制器
class ExternalInterruptionResumeController(
    private val context: Context,
    private val settingsPreferences: SettingsPreferences
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var scope: CoroutineScope? = null
    private var onResumeRequested: (() -> Unit)? = null

    @Volatile
    private var isEnabled: Boolean = true

    @Volatile
    private var isAwaitingResume: Boolean = false

    private var interruptionTimestamp: Long = 0L
    private var debounceJob: Job? = null
    private var pollingJob: Job? = null
    private var isRegistered: Boolean = false

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            super.onPlaybackConfigChanged(configs)
            checkAndScheduleResume()
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                AppLogger.i(TAG, "监听到耳机拔出，取消外部打断恢复等待")
                cancelWaiting()
            }
        }
    }

    fun start(scope: CoroutineScope, onResumeRequested: () -> Unit) {
        this.scope = scope
        this.onResumeRequested = onResumeRequested

        scope.launch {
            settingsPreferences.resumeAfterExternalInterruption.collect { enabled ->
                isEnabled = enabled
                if (enabled) {
                    registerListeners()
                } else {
                    cancelWaiting()
                    unregisterListeners()
                }
            }
        }
    }

    fun onAudioFocusLoss() {
        if (!isEnabled) return
        AppLogger.i(TAG, "收到外部音频焦点丢失，开始等待外部音频停止")
        isAwaitingResume = true
        interruptionTimestamp = SystemClock.elapsedRealtime()
        debounceJob?.cancel()
        debounceJob = null
        startPollingFallback()
    }

    fun onUserOrSystemPause() {
        if (isAwaitingResume) {
            AppLogger.i(TAG, "用户或系统触发非焦点丢失暂停，重置外部恢复等待")
            cancelWaiting()
        }
    }

    fun onPlaybackStarted() {
        if (isAwaitingResume) {
            cancelWaiting()
        }
    }

    fun release() {
        cancelWaiting()
        unregisterListeners()
        scope = null
        onResumeRequested = null
    }

    private fun cancelWaiting() {
        isAwaitingResume = false
        debounceJob?.cancel()
        debounceJob = null
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun isExternalAudioActive(): Boolean {
        return audioManager.isMusicActive || audioManager.mode != AudioManager.MODE_NORMAL
    }

    private fun checkAndScheduleResume() {
        if (!isAwaitingResume) return

        val elapsed = SystemClock.elapsedRealtime() - interruptionTimestamp
        if (elapsed > MAX_INTERRUPTION_WINDOW_MS) {
            AppLogger.i(TAG, "外部打断已超过最大等待窗口(${elapsed}ms)，放弃自动恢复")
            cancelWaiting()
            return
        }

        if (isExternalAudioActive()) {
            debounceJob?.cancel()
            debounceJob = null
        } else {
            if (debounceJob?.isActive == true) {
                return
            }
            debounceJob = scope?.launch {
                delay(DEBOUNCE_DELAY_MS)
                if (isAwaitingResume && !isExternalAudioActive()) {
                    val currentElapsed = SystemClock.elapsedRealtime() - interruptionTimestamp
                    if (currentElapsed <= MAX_INTERRUPTION_WINDOW_MS) {
                        AppLogger.i(TAG, "外部音视频已停止发声，防抖确认通过，触发恢复播放")
                        isAwaitingResume = false
                        pollingJob?.cancel()
                        pollingJob = null
                        onResumeRequested?.invoke()
                    } else {
                        cancelWaiting()
                    }
                }
            }
        }
    }

    private fun startPollingFallback() {
        pollingJob?.cancel()
        pollingJob = scope?.launch {
            while (isAwaitingResume) {
                delay(1000L)
                if (isAwaitingResume) {
                    checkAndScheduleResume()
                }
            }
        }
    }

    private fun registerListeners() {
        if (isRegistered) return
        try {
            audioManager.registerAudioPlaybackCallback(playbackCallback, null)
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            ContextCompat.registerReceiver(
                context,
                becomingNoisyReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isRegistered = true
        } catch (e: Exception) {
            AppLogger.w(TAG, "注册音频监听失败", e)
        }
    }

    private fun unregisterListeners() {
        if (!isRegistered) return
        try {
            audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        } catch (e: Exception) {
            AppLogger.w(TAG, "注销 AudioPlaybackCallback 失败", e)
        }
        try {
            context.unregisterReceiver(becomingNoisyReceiver)
        } catch (e: Exception) {
            AppLogger.w(TAG, "注销 becomingNoisyReceiver 失败", e)
        }
        isRegistered = false
    }
}
