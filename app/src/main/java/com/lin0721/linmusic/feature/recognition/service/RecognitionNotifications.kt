package com.lin0721.linmusic.feature.recognition.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lin0721.linmusic.R
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.player.ui.formatTime
import com.lin0721.linmusic.feature.recognition.domain.RecognitionCandidate
import com.lin0721.linmusic.feature.recognition.domain.RecognitionProgress
import com.lin0721.linmusic.feature.recognition.ui.RecognitionFailedReason

private const val TAG = "RecognitionNotifications"
// 渠道重要性创建后不可改，进度与结果拆成两个渠道；旧的单一渠道在初始化时删除
private const val LEGACY_CHANNEL_ID = "recognition"
private const val PROGRESS_CHANNEL_ID = "recognition_progress"
private const val RESULT_CHANNEL_ID = "recognition_result"

const val ACTION_OPEN_RECOGNITION_RESULT = "com.lin0721.linmusic.action.OPEN_RECOGNITION_RESULT"

class RecognitionNotifications(private val context: Context) {

    companion object {
        const val ONGOING_ID = 9101
        private const val RESULT_ID = 9102

        private const val REQUEST_CANCEL = 1
        private const val REQUEST_OPEN = 2
        private const val REQUEST_PLAY = 3
        private const val REQUEST_RETRY = 4
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            val progress = NotificationChannel(PROGRESS_CHANNEL_ID, "识曲进度", NotificationManager.IMPORTANCE_LOW).apply {
                description = "后台内录识曲进行中"
                setShowBadge(false)
            }
            // HIGH 才会弹出横幅；不响铃，避免打断正在看的视频
            val result = NotificationChannel(RESULT_CHANNEL_ID, "识曲结果", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "后台内录识曲的结果"
                setSound(null, null)
            }
            manager?.createNotificationChannels(listOf(progress, result))
        }
    }

    fun ongoing(progress: RecognitionProgress): Notification {
        val seconds = progress.recordedMs / 1000
        val matching = progress.attempts.size
        val base = if (matching > 0) "已录 $seconds 秒 · 正在匹配第 $matching 个片段" else "已录 $seconds 秒"
        val text = if (progress.round > 1) "第 ${progress.round} 轮 · $base" else base
        return NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("正在识别手机正在播放的声音")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "取消", servicePendingIntent(PlaybackRecognitionService.cancelIntent(context), REQUEST_CANCEL))
            .build()
    }

    fun updateOngoing(progress: RecognitionProgress) {
        notify(ONGOING_ID, ongoing(progress))
    }

    fun showFound(candidate: RecognitionCandidate, cover: Bitmap?) {
        val position = formatTime(candidate.startTimeMs)
        val playIntent = RecognitionActionReceiver.playIntent(context, candidate)
        val notification = NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentTitle("识别到：${candidate.title}")
            .setContentText("${candidate.artists} · 命中 $position")
            .setLargeIcon(cover)
            .setAutoCancel(true)
            .setContentIntent(openResultPendingIntent())
            .addAction(
                0,
                "从 $position 播放",
                PendingIntent.getBroadcast(context, REQUEST_PLAY, playIntent, immutableUpdateFlags())
            )
            .build()
        notify(RESULT_ID, notification) { "识别到：${candidate.title}" }
    }

    fun showFailed(reason: RecognitionFailedReason, rounds: Int = 1) {
        val (title, text) = failureCopy(reason, rounds)
        val retryIntent = Intent(context, ProjectionConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notification = NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openResultPendingIntent())
            .addAction(0, "重试", PendingIntent.getActivity(context, REQUEST_RETRY, retryIntent, immutableUpdateFlags()))
            .build()
        notify(RESULT_ID, notification) { title }
    }

    fun cancelResult() {
        NotificationManagerCompat.from(context).cancel(RESULT_ID)
    }

    private fun failureCopy(reason: RecognitionFailedReason, rounds: Int): Pair<String, String> = when (reason) {
        RecognitionFailedReason.NOT_FOUND ->
            "未识别到" to if (rounds > 1) "录了 $rounds 轮都没有匹配结果，换一段副歌再试试" else "换一段副歌再试试"
        RecognitionFailedReason.SILENT -> "未识别到" to "没有录到声音，可能是对方 App 禁止了内录，可改用麦克风重试"
        RecognitionFailedReason.NETWORK -> "识别失败" to "网络异常，请检查网络后重试"
        RecognitionFailedReason.ENGINE_UNAVAILABLE -> "识别失败" to "识别组件加载失败，请确认系统 WebView 可用"
        RecognitionFailedReason.RECORDER_UNAVAILABLE -> "识别失败" to "内录启动失败，请重试"
        RecognitionFailedReason.STOPPED, RecognitionFailedReason.UNKNOWN -> "识别失败" to "出现未知错误，请重试"
    }

    // 复用启动 Intent 指向主界面，不直接引用宿主 Activity 类
    private fun openResultPendingIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        intent.action = ACTION_OPEN_RECOGNITION_RESULT
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, REQUEST_OPEN, intent, immutableUpdateFlags())
    }

    private fun servicePendingIntent(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getService(context, requestCode, intent, immutableUpdateFlags())

    private fun immutableUpdateFlags() = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    // 通知权限被关时退化为 Toast
    private fun notify(id: Int, notification: Notification, fallbackText: (() -> String)? = null) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            try {
                NotificationManagerCompat.from(context).notify(id, notification)
                return
            } catch (e: SecurityException) {
                AppLogger.w(TAG, "发送识曲通知失败", e)
            }
        }
        fallbackText?.let { Toast.makeText(context, it(), Toast.LENGTH_LONG).show() }
    }
}
