package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.PlaybackRepositoryImpl
import com.lin0721.linmusic.core.source.AudioSourceProvider
import com.lin0721.linmusic.core.plugin.PluginContent
import com.lin0721.linmusic.core.plugin.PluginPlatformSearchProvider
import com.lin0721.linmusic.core.source.AudioSourceRouter
import com.lin0721.linmusic.core.source.ExternalTrackResolver
import com.lin0721.linmusic.core.source.LxPluginManager
import com.lin0721.linmusic.core.source.PlatformSearchProvider
import com.lin0721.linmusic.core.source.QQSongIdResolver
import com.lin0721.linmusic.core.source.UnmApiClient
import com.lin0721.linmusic.core.source.provider.KugouSearchProvider
import com.lin0721.linmusic.core.source.provider.KuwoSearchProvider
import com.lin0721.linmusic.core.source.provider.QQSearchProvider
import org.koin.core.qualifier.named
import org.koin.dsl.module
// 多音源换源能力依赖注入模块
val sourceModule = module {

    // UNM API 客户端单例
    single { UnmApiClient() }

    // 社区源插件的导入与启停编排，运行时由各端模块提供
    single { LxPluginManager(get(), get()) }

    // 聚合搜索平台，仅搜索，不参与换源
    single<PlatformSearchProvider>(named("kugou")) { KugouSearchProvider() }
    single<PlatformSearchProvider>(named("kuwo")) { KuwoSearchProvider() }
    single<PlatformSearchProvider>(named("qq")) { QQSearchProvider() }
    single<PlatformSearchProvider>(named("plugin")) { PluginPlatformSearchProvider(get()) }

    // 外部平台歌曲的直链解析，搜索与播放器共用
    single { ExternalTrackResolver(getAll(), getOrNull(), getOrNull(), getOrNull(), get<UnmApiClient>(), getOrNull(), get<PluginContent>()) }

    // 外部歌曲评论取自 QQ 音乐，酷狗/酷我歌曲先到 QQ 搜索匹配
    single { QQSongIdResolver(getOrNull<PlatformSearchProvider>(named("qq"))) }

    // AudioSourceRouter 包装 PlaybackRepositoryImpl，实现透明换源
    single<PlaybackRepository> {
        AudioSourceRouter(
            delegate = get<PlaybackRepositoryImpl>(),
            unmApiClient = get<UnmApiClient>(),
            sourcePreferences = get(),
            settingsPreferences = get(),
            networkStateProvider = get(),
            providers = getAll<AudioSourceProvider>()
        )
    }
}
