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

/**
 * OPPO 流体云歌词胶囊。
 *
 * ColorOS 16 起流体云完整接入了 Android 16 的实时更新（Live Updates）接口：满足条件的通知会被系统提升为
 * 实时活动，以胶囊形式展示在状态栏 / 流体云中，胶囊文字取自 shortCriticalText。
 * 媒体通知（MediaStyle）不符合实时更新条件，因此这里单独发一条只承载当前歌词的通知。
 *
 * 实时更新通知的硬性要求：标准样式 / BigTextStyle、ongoing、有 contentTitle、无自定义 RemoteViews、
 * 非群组摘要、未 colorized、渠道重要性不能是 MIN，且清单里声明了 POST_PROMOTED_NOTIFICATIONS。
 */
class FluidCloudLyricNotifier(private val context: Context) {

    companion object {
        private const val TAG = "FluidCloudLyricNotifier"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "melodia_fluid_cloud_lyric_channel"
        private const val CHANNEL_NAME = "流体云歌词"
        // 普通通知不随进程退出而消失；每次刷新都会重置计时，进程被杀后胶囊最多残留这么久
        private const val STALE_TIMEOUT_MS = 10 * 60 * 1000L
        // Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS，当前 compileSdk 未暴露该常量，直接使用字面值
        private const val ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS =
            "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.BAKLAVA)
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

        // 用户是否允许本应用发布实时更新通知；低版本系统一律视为不可用
        fun canPostPromoted(context: Context): Boolean {
            if (!isSupported()) return false
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            return manager.canPostPromotedNotifications()
        }

        // 跳转到系统「实时更新」授权页，用户关闭过该权限时引导重新开启
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

    /**
     * 发布或刷新歌词胶囊；内容未变化时直接跳过，避免高频 notify 被系统限流。
     * [lyric] 为空时用歌名兜底（前奏、间奏或无歌词歌曲）。
     */
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
            // 未授予通知权限时 notify 会抛出，静默降级即可
            AppLogger.w(TAG, "发布流体云歌词通知失败，可能缺少通知权限", e)
        } catch (e: Exception) {
            AppLogger.w(TAG, "发布流体云歌词通知失败", e)
        }
    }

    fun dismiss() {
        if (lastPostedKey == null) return
        lastPostedKey = null
        try {
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            AppLogger.w(TAG, "移除流体云歌词通知失败", e)
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
        // 实时更新要求渠道重要性高于 MIN；LOW 不响铃不弹横幅，适合高频刷新的歌词
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "在 ColorOS 流体云 / 状态栏胶囊中显示当前歌词"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }
}
