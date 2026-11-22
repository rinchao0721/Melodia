package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.auth.NeteaseAuthProvider
import com.lin0721.linmusic.core.auth.PlatformSessionRegistry
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.kugou.KugouAuthProvider
import com.lin0721.linmusic.core.kugou.KugouAuthService
import com.lin0721.linmusic.core.kugou.KugouConfig
import com.lin0721.linmusic.core.kugou.KugouDeviceStore
import com.lin0721.linmusic.core.kugou.KugouGatewayClient
import org.koin.dsl.module

// 酷狗独立网络/加密/鉴权模块与跨平台会话注册表
val kugouModule = module {
    single { KugouConfig() }
    single { KugouDeviceStore(get<UserPreferences>().preferencesDataStore) }
    single { KugouGatewayClient(get(), get(), get()) }
    single { KugouAuthService(get(), get(), get(), get()) }
    single { KugouAuthProvider(get(), get()) }
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
