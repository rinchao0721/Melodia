package com.lin0721.linmusic.core.player.external

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import com.lin0721.linmusic.MainActivity
import com.lin0721.linmusic.R
import com.lin0721.linmusic.core.log.AppLogger

class FluidCloudLyricNotifier(private val context: Context) {

    companion object {
        private const val TAG = "FluidCloudLyricNotifier"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "melodia_fluid_cloud_lyric_channel"
        private const val CHANNEL_NAME = "状态栏歌词"
        // 进程被杀后通知不会自动移除，用超时兜底
        private const val STALE_TIMEOUT_MS = 10 * 60 * 1000L
        // compileSdk 36 未暴露 Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS
        private const val ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS =
            "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.BAKLAVA)
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

        fun canPostPromoted(context: Context): Boolean {
            if (!isSupported()) return false
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            return manager.canPostPromotedNotifications()
        }

        fun buildManagePromotedIntent(context: Context): Intent? {
            if (!isSupported()) return null
            return Intent(ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private var lastPostedKey: String? = null

    private val contentIntent: PendingIntent by lazy {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    fun show(title: String, artist: String, lyric: String?, translation: String?) {
        if (!isSupported()) return
        val capsuleText = lyric?.takeIf { it.isNotBlank() } ?: title
        if (capsuleText.isBlank()) return
        val key = "$title\u0000$artist\u0000$capsuleText\u0000${translation.orEmpty()}"
        if (key == lastPostedKey) return
        try {
            ensureChannel()
            notificationManager?.notify(
                NOTIFICATION_ID,
                buildNotification(title, artist, capsuleText, translation)
            )
            lastPostedKey = key
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "发布状态栏歌词通知失败，可能缺少通知权限", e)
        } catch (e: Exception) {
            AppLogger.w(TAG, "发布状态栏歌词通知失败", e)
        }
    }

    fun dismiss() {
        if (lastPostedKey == null) return
        lastPostedKey = null
        try {
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            AppLogger.w(TAG, "移除状态栏歌词通知失败", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildNotification(
        title: String,
        artist: String,
        capsuleText: String,
        translation: String?
    ): Notification {
        val songLine = if (artist.isNotBlank()) "$title - $artist" else title
        val bodyText = translation?.takeIf { it.isNotBlank() } ?: songLine
        val extras = Bundle().apply {
            putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
        }
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(capsuleText)
            .setContentText(bodyText)
            .setSubText(songLine)
            .setStyle(Notification.BigTextStyle().bigText(bodyText))
            .setShortCriticalText(capsuleText)
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setTimeoutAfter(STALE_TIMEOUT_MS)
            .addExtras(extras)
            .build()
    }

    private fun ensureChannel() {
        val manager = notificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "在状态栏胶囊中显示当前歌词"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }
}
