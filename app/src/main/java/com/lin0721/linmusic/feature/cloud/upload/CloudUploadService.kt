package com.lin0721.linmusic.feature.cloud.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lin0721.linmusic.MainActivity
import com.lin0721.linmusic.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private const val NOTIFICATION_CHANNEL_ID = "cloud_upload"
private const val NOTIFICATION_ID = 9001

const val ACTION_CLOUD_UPLOAD_ENQUEUE = "com.lin0721.linmusic.action.CLOUD_UPLOAD_ENQUEUE"
const val ACTION_CLOUD_UPLOAD_RETRY = "com.lin0721.linmusic.action.CLOUD_UPLOAD_RETRY"
const val EXTRA_CLOUD_UPLOAD_URIS = "extra_cloud_upload_uris"
const val EXTRA_CLOUD_UPLOAD_TASK_ID = "extra_cloud_upload_task_id"

// 云盘上传前台服务：只负责保活与通知，队列与流水线由共享层 CloudUploadRunner 执行，
// 独立于 CloudViewModel 的生命周期，退出云盘页/切后台都不中断
class CloudUploadService : Service() {

    private val uploadManager: CloudUploadManager by inject()
    private val uploadRunner: CloudUploadRunner by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLOUD_UPLOAD_ENQUEUE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(EXTRA_CLOUD_UPLOAD_URIS).orEmpty()
                uploadRunner.enqueue(uris.map { UriUploadSource(applicationContext, it) })
            }

            ACTION_CLOUD_UPLOAD_RETRY -> {
                intent.getStringExtra(EXTRA_CLOUD_UPLOAD_TASK_ID)?.let(uploadRunner::retry)
            }
        }

        startForegroundWithNotification()
        ensureWatching()
        return START_NOT_STICKY
    }

    // 跟随队列刷新通知，队列跑完后退出前台
    private fun ensureWatching() {
        if (watchJob?.isActive == true) return
        watchJob = serviceScope.launch {
            val notifyJob = launch {
                uploadManager.state.collect { updateNotification() }
            }
            uploadRunner.running.first { !it }
            notifyJob.cancel()
            updateNotification()
            stopForegroundAndSelf()
        }
    }

    // ======================= 通知 =======================

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "云盘上传",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        // 未授权通知权限时静默跳过，不阻断上传本身
        val granted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted) return

        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val tasks = uploadManager.state.value
        val total = tasks.size
        val doneCount = tasks.count { it.status == UploadStatus.SUCCESS }
        val activeTask = tasks.firstOrNull {
            it.status != UploadStatus.SUCCESS && it.status != UploadStatus.FAILED && it.status != UploadStatus.QUEUED
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(activeTask != null)

        return if (activeTask != null) {
            val percent = (activeTask.progress * 100).toInt()
            builder
                .setContentTitle("正在上传 ($doneCount/$total)")
                .setContentText("${activeTask.fileName} · $percent%")
                .setProgress(100, percent, activeTask.status != UploadStatus.UPLOADING)
                .build()
        } else {
            val failedCount = tasks.count { it.status == UploadStatus.FAILED }
            val summary = if (failedCount > 0) "完成，$failedCount 首失败" else "全部上传完成"
            builder
                .setContentTitle(summary)
                .setContentText("$doneCount/$total 首成功")
                .setProgress(0, 0, false)
                .build()
        }
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
