package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.auth.NeteaseAuthProvider
import com.lin0721.linmusic.core.auth.PlatformSessionRegistry
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.kugou.KugouAuthProvider
import com.lin0721.linmusic.core.kugou.KugouAuthService
import com.lin0721.linmusic.core.kugou.KugouConfig
import com.lin0721.linmusic.core.kugou.KugouDeviceStore
import com.lin0721.linmusic.core.kugou.KugouGatewayClient
import com.lin0721.linmusic.core.kugou.KugouLikeService
import com.lin0721.linmusic.core.kugou.KugouPlaybackService
import com.lin0721.linmusic.core.player.KugouPlaybackProvider
import com.lin0721.linmusic.core.player.PlatformPlaybackRouter
import org.koin.dsl.module

// 酷狗独立网络/加密/鉴权/播放模块与跨平台会话及播放路由注册表
val kugouModule = module {
    single { KugouConfig() }
    single { KugouDeviceStore(get<UserPreferences>().preferencesDataStore) }
    single { KugouGatewayClient(get(), get(), get()) }
    single { KugouAuthService(get(), get(), get(), get()) }
    single { KugouAuthProvider(get(), get()) }
    single { KugouPlaybackService(get(), get(), get(), get()) }
    single { KugouLikeService(get(), get()) }
    single { KugouPlaybackProvider(get(), getOrNull()) }
    single {
        PlatformPlaybackRouter(
            providers = listOf(
                get<KugouPlaybackProvider>()
            )
        )
    }
    single { NeteaseAuthProvider(get(), get()) }
    single {
        PlatformSessionRegistry(
            providers = listOf(
                get<NeteaseAuthProvider>(),
                get<KugouAuthProvider>()
            )
        )
    }
}
