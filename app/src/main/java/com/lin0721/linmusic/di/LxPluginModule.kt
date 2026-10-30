package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.plugin.ScriptSandboxFactory
import com.lin0721.linmusic.core.source.AudioSourceProvider
import com.lin0721.linmusic.core.source.LxAudioSourceProvider
import com.lin0721.linmusic.core.source.LxPluginRuntime
import com.lin0721.linmusic.core.source.PlatformSearchProvider
import com.lin0721.linmusic.feature.source.plugin.LxPluginEngine
import com.lin0721.linmusic.feature.source.plugin.WebViewScriptSandboxFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

// LX Music 插件沙盒与音源注入模块；脚本沙盒工厂与插件系统共用
val lxPluginModule = module {
    single<ScriptSandboxFactory> { WebViewScriptSandboxFactory(androidContext()) }
    single { LxPluginEngine(get()) } bind LxPluginRuntime::class
    single<AudioSourceProvider>(named("lx")) {
        LxAudioSourceProvider(get<LxPluginRuntime>(), get(), getAll<PlatformSearchProvider>())
    }
}
