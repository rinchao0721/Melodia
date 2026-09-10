package com.lin0721.linmusic.di

import com.lin0721.linmusic.BuildConfig
import com.lin0721.linmusic.core.update.UpdateManager
import com.lin0721.linmusic.core.update.data.ApkDownloader
import com.lin0721.linmusic.core.update.data.ApkInstaller
import com.lin0721.linmusic.core.update.data.GithubReleaseApi
import com.lin0721.linmusic.core.update.data.UpdateRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

private const val GITHUB_BASE_URL = "https://api.github.com/"
private const val GITHUB_CLIENT = "github"

/**
 * 软件更新（GitHub Releases）依赖注入模块
 *
 * GitHub API 与主业务网络完全隔离
 */
val updateModule = module {

    //GitHub 专用的 OkHttpClient（不做请求体日志）
    single(named(GITHUB_CLIENT)) {
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                }
            )
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    single(named(GITHUB_CLIENT)) {
        val json: Json = get()
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(get(named(GITHUB_CLIENT)))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    single<GithubReleaseApi> { get<Retrofit>(named(GITHUB_CLIENT)).create(GithubReleaseApi::class.java) }

    single { UpdateRepository(get()) }
    single { ApkDownloader(context = get(), downloadClient = get(named(GITHUB_CLIENT))) }
    single { ApkInstaller(context = get()) }
    single { com.lin0721.linmusic.core.update.data.UpdateNotificationHelper(context = get()) }
    single {
        UpdateManager(
            context = get(),
            updateRepository = get(),
            apkDownloader = get(),
            apkInstaller = get(),
            settingsPreferences = get(),
            notificationHelper = get()
        )
    }
}
