package com.lin0721.linmusic.feature.recognition.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.recognition.data.RecognitionHistoryPreferences
import com.lin0721.linmusic.feature.recognition.data.RecognitionPreferences
import com.lin0721.linmusic.feature.recognition.data.RecognitionRepository
import com.lin0721.linmusic.feature.recognition.domain.RecognitionMode
import com.lin0721.linmusic.feature.recognition.domain.RecognitionProgress
import com.lin0721.linmusic.feature.recognition.engine.AfpFingerprintEngine
import com.lin0721.linmusic.feature.recognition.engine.PlaybackCaptureRecorder
import com.lin0721.linmusic.feature.recognition.engine.RecognitionSession
import com.lin0721.linmusic.feature.recognition.ui.RecognitionFailedReason
import com.lin0721.linmusic.feature.recognition.ui.RecognitionUiState
import com.lin0721.linmusic.feature.recognition.ui.runRecognition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject

private const val TAG = "PlaybackRecognitionService"
private const val ACTION_START = "com.lin0721.linmusic.action.PLAYBACK_RECOGNITION_START"
private const val ACTION_CANCEL = "com.lin0721.linmusic.action.PLAYBACK_RECOGNITION_CANCEL"
private const val EXTRA_RESULT_CODE = "extra_result_code"
private const val EXTRA_RESULT_DATA = "extra_result_data"
private const val COVER_TIMEOUT_MS = 3_000L

// 视频常有片头或人声，第一轮未命中时自动再录一轮
private const val PLAYBACK_ROUNDS = 2

// 后台内录识曲：前台服务类型 mediaProjection，同一时间只跑一个会话，状态写入 PlaybackRecognitionState
class PlaybackRecognitionService : Service() {

    companion object {
        fun startIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, PlaybackRecognitionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)

        fun cancelIntent(context: Context): Intent =
            Intent(context, PlaybackRecognitionService::class.java).setAction(ACTION_CANCEL)
    }

    private val repository: RecognitionRepository by inject()
    private val historyPreferences: RecognitionHistoryPreferences by inject()
    private val state: PlaybackRecognitionState by inject()
    private val notifications: RecognitionNotifications by inject()
    private val preferences: RecognitionPreferences by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sessionJob: Job? = null
    private var projection: MediaProjection? = null
    private var engine: AfpFingerprintEngine? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        // 用户从系统的"停止共享"入口结束投屏
        override fun onStop() {
            cancelSession()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || data == null) {
                    AppLogger.w(TAG, "缺少录屏授权结果或系统版本过低，放弃内录")
                    stopSelf()
                } else {
                    startSession(resultCode, data)
                }
            }
            ACTION_CANCEL -> cancelSession()
            else -> if (sessionJob?.isActive != true) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startSession(resultCode: Int, data: Intent) {
        sessionJob?.cancel()
        releaseCapture()
        notifications.cancelResult()

        // Android 14+ 必须先以 mediaProjection 类型进入前台，再取 MediaProjection
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, RecognitionNotifications.ONGOING_ID, notifications.ongoing(RecognitionProgress()), type)
        } catch (e: Exception) {
            AppLogger.e(TAG, "内录前台服务启动失败", e)
            state.update(RecognitionUiState.Failed(RecognitionFailedReason.RECORDER_UNAVAILABLE, RecognitionProgress()))
            stopSelf()
            return
        }

        val manager = getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = runCatching { manager?.getMediaProjection(resultCode, data) }
            .onFailure { AppLogger.e(TAG, "获取 MediaProjection 失败", it) }
            .getOrNull()
        if (mediaProjection == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            finishWith(RecognitionUiState.Failed(RecognitionFailedReason.RECORDER_UNAVAILABLE, RecognitionProgress()))
            return
        }
        mediaProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
        projection = mediaProjection

        val fingerprintEngine = AfpFingerprintEngine(applicationContext).also { engine = it }
        state.update(RecognitionUiState.Listening(RecognitionProgress()))
        scope.launch { preferences.setLastMode(RecognitionMode.PLAYBACK) }

        sessionJob = scope.launch {
            var result: RecognitionUiState = RecognitionUiState.Idle
            for (round in 1..PLAYBACK_ROUNDS) {
                val session = RecognitionSession(
                    capture = PlaybackCaptureRecorder(mediaProjection),
                    fingerprinter = fingerprintEngine,
                    round = round,
                    matcher = repository
                )
                var lastSecond = -1L
                var lastAttempts = 0
                result = runRecognition(session, historyPreferences) { progress ->
                    state.update(RecognitionUiState.Listening(progress))
                    // 进度每 100 ms 刷新一次，通知只在秒数或片段数变化时更新，避免系统限流
                    val second = progress.recordedMs / 1000
                    if (second != lastSecond || progress.attempts.size != lastAttempts) {
                        lastSecond = second
                        lastAttempts = progress.attempts.size
                        notifications.updateOngoing(progress)
                    }
                }
                // 只有"有声音但没匹配上"才值得再录；静音多半是对方 App 禁止内录，重录也一样
                val notFound = result is RecognitionUiState.Failed && result.reason == RecognitionFailedReason.NOT_FOUND
                if (!notFound) break
            }
            finishWith(result)
        }
    }

    private suspend fun loadCover(url: String): Bitmap? = withTimeoutOrNull(COVER_TIMEOUT_MS) {
        runCatching {
            val request = ImageRequest.Builder(this@PlaybackRecognitionService)
                .data("$url?param=200y200")
                .allowHardware(false)
                .build()
            imageLoader.execute(request).image?.toBitmap()
        }.onFailure { AppLogger.w(TAG, "通知封面加载失败", it) }.getOrNull()
    }

    private fun finishWith(result: RecognitionUiState) {
        state.update(result)
        releaseCapture()
        when (result) {
            is RecognitionUiState.Found -> {
                val best = result.candidates.first()
                scope.launch {
                    notifications.showFound(best, loadCover(best.coverUrl))
                    stopService()
                }
                return
            }
            is RecognitionUiState.Failed -> notifications.showFailed(result.reason, result.progress.round)
            is RecognitionUiState.PermissionRequired -> notifications.showFailed(RecognitionFailedReason.RECORDER_UNAVAILABLE)
            else -> Unit
        }
        stopService()
    }

    private fun cancelSession() {
        sessionJob?.cancel()
        sessionJob = null
        releaseCapture()
        state.update(RecognitionUiState.Idle)
        stopService()
    }

    // 释放顺序：先停录音（取消会话），再停投屏，最后释放 WebView
    private fun releaseCapture() {
        projection?.let {
            it.unregisterCallback(projectionCallback)
            it.stop()
        }
        projection = null
        engine?.release()
        engine = null
    }

    private fun stopService() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        sessionJob?.cancel()
        releaseCapture()
        if (state.isRunning) state.update(RecognitionUiState.Idle)
        scope.cancel()
        super.onDestroy()
    }
}
