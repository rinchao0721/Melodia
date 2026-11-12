package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.download.DownloadFolderScanner
import com.lin0721.linmusic.core.download.DownloadNotificationHelper
import com.lin0721.linmusic.core.download.DownloadPreferences
import com.lin0721.linmusic.core.download.DownloadQueueGate
import com.lin0721.linmusic.core.download.DownloadTaskStore
import com.lin0721.linmusic.core.download.DownloadWorkerFactory
import com.lin0721.linmusic.core.download.SongDownloadManager
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.download.data.DownloadApi
import com.lin0721.linmusic.core.download.recognition.DownloadRecognizer
import com.lin0721.linmusic.core.download.recognition.LocalAudioIndexStore
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.TimeUnit

const val DOWNLOAD_CLIENT = "song_download"
private const val MAX_CONCURRENT_DOWNLOADS = 3

/**
 * 歌曲下载依赖注入模块
 */
val downloadModule = module {

    // 下载专用 OkHttpClient
    single(named(DOWNLOAD_CLIENT)) {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    single { DownloadNotificationHelper(context = get()) }

    single {
        DownloadWorkerFactory(
            downloadApi = get<DownloadApi>(),
            downloadPreferences = get(),
            settingsPreferences = get(),
            notificationHelper = get(),
            playbackRepository = get(),
            downloadClient = get(named(DOWNLOAD_CLIENT)),
            taskStore = get(),
            queueGate = get(),
            downloadRecognizer = get(),
            sourcePreferences = get()
        )
    }

    single { LocalAudioIndexStore(File(androidContext().filesDir, "download_recognition_index.json")) }
    single { DownloadFolderScanner(context = androidContext(), indexStore = get()) }
    single {
        val downloadPreferences: DownloadPreferences = get()
        DownloadRecognizer(
            indexStore = get(),
            records = { downloadPreferences.records.first() },
            addRecords = downloadPreferences::addRecords,
            isAlive = downloadPreferences::fileExists
        )
    }

    single { DownloadTaskStore(context = get()) }

    // 同时最多 3 首在下载，其余按排队顺序等待
    single {
        val taskStore: DownloadTaskStore = get()
        DownloadQueueGate(maxConcurrent = MAX_CONCURRENT_DOWNLOADS) { workIds ->
            val ids = workIds.toHashSet()
            taskStore.tasks.first().filter { it.workId in ids }.associate { it.workId to it.queueOrder }
        }
    }

    single {
        SongDownloadManager(context = get(), downloadPreferences = get(), taskStore = get(), downloadRecognizer = get())
    }
    single<SongDownloader> { get<SongDownloadManager>() }
}
