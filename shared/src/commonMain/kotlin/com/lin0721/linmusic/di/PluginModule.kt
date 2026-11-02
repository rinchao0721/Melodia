package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.plugin.PlayerCardSource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.plugin.PluginAudioSourceProvider
import com.lin0721.linmusic.core.plugin.PluginCommands
import com.lin0721.linmusic.core.plugin.PluginConfirmations
import com.lin0721.linmusic.core.plugin.PluginNeteaseApi
import com.lin0721.linmusic.core.plugin.RepositoryPluginNeteaseBackend
import com.lin0721.linmusic.core.plugin.PluginEventDispatcher
import com.lin0721.linmusic.core.plugin.PluginHostApi
import com.lin0721.linmusic.core.plugin.PluginLyricsSource
import com.lin0721.linmusic.core.plugin.PluginNotices
import com.lin0721.linmusic.core.plugin.PluginPageHost
import com.lin0721.linmusic.core.plugin.PluginTrackQuery
import com.lin0721.linmusic.core.plugin.PluginRepository
import com.lin0721.linmusic.core.plugin.PluginRuntime
import com.lin0721.linmusic.core.plugin.PluginStore
import com.lin0721.linmusic.core.source.AudioSourceProvider
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.flow.first
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import java.io.File

// 插件系统依赖注入，插件目录与 ScriptSandboxFactory 由各端提供
fun pluginModule(directory: Scope.() -> File): Module = module {
    single { PluginStore(directory()) }
    single { PluginNotices() }
    single { PluginConfirmations() }
    single {
        val scope = this
        val backend = RepositoryPluginNeteaseBackend(
            user = { scope.get() },
            player = { scope.get() },
            playback = { scope.get() },
            search = { scope.get() },
            playlists = { scope.get() },
            artists = { scope.get() },
            userPlaylists = { scope.get() },
            likes = { scope.get() },
            create = { scope.get() }
        )
        PluginNeteaseApi(backend, get(), get())
    }
    single {
        val scope = this
        PluginHostApi(get(), playback = { scope.getOrNull<PlaybackController>() }, notices = get(), netease = get(), content = get())
    }
    single { PluginRuntime(get(), get(), registry = get()) }
    single { PluginRepository(get(), get(), get(), get()) }
    single { PluginPageHost(get(), get()) }
    single { PluginCommands(get(), get(), confirmations = get(), content = get()) }
    single { PlayerCardSource(get(), get<PluginRepository>().pages) }
    single {
        val scope = this
        PluginEventDispatcher(get(), playback = { scope.getOrNull<PlaybackController>() })
    }
    single<AudioSourceProvider>(named("plugin")) { PluginAudioSourceProvider(get()) }
    single {
        val scope = this
        PluginLyricsSource(get()) { songId ->
            scope.get<PlaybackRepository>().getSongDetail(songId).first().getOrNull()?.let { track ->
                PluginTrackQuery(songId.toString(), track.name, track.ar.joinToString("/") { it.name }, track.al.name, track.dt, MusicPlatform.NETEASE)
            }
        }
    }
}
