package com.lin0721.linmusic.core.download

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.lin0721.linmusic.core.download.data.DownloadUrlResolver
import com.lin0721.linmusic.core.download.recognition.DownloadRecognizer
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import okhttp3.OkHttpClient

// 自定义 WorkerFactory
class DownloadWorkerFactory(
    private val downloadPreferences: DownloadPreferences,
    private val settingsPreferences: SettingsPreferences,
    private val notificationHelper: DownloadNotificationHelper,
    private val playbackRepository: PlaybackRepository,
    private val downloadClient: OkHttpClient,
    private val taskStore: DownloadTaskStore,
    private val queueGate: DownloadQueueGate,
    private val downloadRecognizer: DownloadRecognizer,
    private val urlResolver: DownloadUrlResolver
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        SongDownloadWorker::class.java.name -> SongDownloadWorker(
            appContext,
            workerParameters,
            downloadPreferences,
            settingsPreferences,
            notificationHelper,
            playbackRepository,
            downloadClient,
            taskStore,
            queueGate,
            downloadRecognizer,
            urlResolver
        )
        else -> null
    }
}
