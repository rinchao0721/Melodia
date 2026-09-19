package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.download.DownloadNotificationHelper
import com.lin0721.linmusic.core.download.DownloadWorkerFactory
import com.lin0721.linmusic.core.download.SongDownloadManager
import com.lin0721.linmusic.core.download.data.DownloadApi
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

const val DOWNLOAD_CLIENT = "song_download"

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
            downloadClient = get(named(DOWNLOAD_CLIENT))
        )
    }

    single { SongDownloadManager(context = get(), downloadPreferences = get()) }
}
