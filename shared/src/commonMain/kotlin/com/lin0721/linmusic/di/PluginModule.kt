package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.plugin.PluginHostApi
import com.lin0721.linmusic.core.plugin.PluginRepository
import com.lin0721.linmusic.core.plugin.PluginRuntime
import com.lin0721.linmusic.core.plugin.PluginStore
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module
import java.io.File

// 插件系统依赖注入，插件目录与 ScriptSandboxFactory 由各端提供
fun pluginModule(directory: Scope.() -> File): Module = module {
    single { PluginStore(directory()) }
    single {
        val scope = this
        PluginHostApi(get(), playback = { scope.getOrNull<PlaybackController>() })
    }
    single { PluginRuntime(get(), get()) }
    single { PluginRepository(get(), get(), get()) }
}
