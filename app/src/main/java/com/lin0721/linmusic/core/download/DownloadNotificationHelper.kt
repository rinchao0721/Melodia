package com.lin0721.linmusic.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lin0721.linmusic.MainActivity
import com.lin0721.linmusic.R
import com.lin0721.linmusic.core.log.AppLogger

private const val TAG = "DownloadNotification"
private const val CHANNEL_ID = "song_download"
private const val NOTIFICATION_ID_BASE = 9000
// 结果通知与前台通知使用不同 ID 区间
private const val TERMINAL_NOTIFICATION_ID_BASE = 19000
private const val BATCH_NOTIFICATION_ID_BASE = 29000
private const val BATCH_TERMINAL_NOTIFICATION_ID_BASE = 39000
private const val NOTIFICATION_ID_SLOTS = 1000

// 歌曲下载通知管理
class DownloadNotificationHelper(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "歌曲下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "歌曲下载进度与结果提示"
                setShowBadge(false)
            }
            val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            systemManager?.createNotificationChannel(channel)
        }
    }

    fun notificationIdFor(songId: Long): Int =
        NOTIFICATION_ID_BASE + (songId % NOTIFICATION_ID_SLOTS).toInt()

    private fun terminalNotificationIdFor(songId: Long): Int =
        TERMINAL_NOTIFICATION_ID_BASE + (songId % NOTIFICATION_ID_SLOTS).toInt()

    fun batchNotificationIdFor(batchTag: String): Int =
        BATCH_NOTIFICATION_ID_BASE + (Math.floorMod(batchTag.hashCode(), NOTIFICATION_ID_SLOTS))

    private fun batchTerminalNotificationIdFor(batchTag: String): Int =
        BATCH_TERMINAL_NOTIFICATION_ID_BASE + (Math.floorMod(batchTag.hashCode(), NOTIFICATION_ID_SLOTS))

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    fun buildProgressNotification(songName: String, progress: Int) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("正在下载 $songName")
            .setContentText(if (progress > 0) "已下载 $progress%" else "准备下载…")
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .build()

    // 批量下载聚合进度通知
    fun buildBatchProgressNotification(batchLabel: String, settled: Int, total: Int) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("正在下载 $batchLabel")
            .setContentText("已完成 $settled/$total")
            .setProgress(total.coerceAtLeast(1), settled, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .build()

    fun showSuccess(songId: Long, songName: String) {
        if (!notificationManager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$songName 下载完成")
            .setContentText("已保存到 Music/Melodia")
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        notify(terminalNotificationIdFor(songId), notification)
    }

    fun showFailed(songId: Long, songName: String, reason: String) {
        if (!notificationManager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$songName 下载失败")
            .setContentText(reason)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notify(terminalNotificationIdFor(songId), notification)
    }

    // 批量下载完成汇总通知
    fun showBatchSummary(batchTag: String, batchLabel: String, total: Int) {
        if (!notificationManager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$batchLabel 下载完成")
            .setContentText("共 $total 首，已保存到 Music/Melodia/$batchLabel")
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        notify(batchTerminalNotificationIdFor(batchTag), notification)
    }

    private fun notify(id: Int, notification: android.app.Notification) {
        try {
            notificationManager.notify(id, notification)
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "发送下载通知无权限", e)
        }
    }
}
