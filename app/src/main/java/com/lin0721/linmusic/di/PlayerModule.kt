package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.plugin.PluginLyricsSource
import com.lin0721.linmusic.core.localmusic.LocalMusicApi
import com.lin0721.linmusic.core.offline.CachedAudioIndex
import com.lin0721.linmusic.core.player.AndroidCachedAudioIndex
import com.lin0721.linmusic.core.player.LyricsResolver
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.PlaybackTrackMeta
import com.lin0721.linmusic.core.player.PlatformPlaybackRouter
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.SleepTimerRequests
import com.lin0721.linmusic.core.player.ExternalInterruptionResumeController
import com.lin0721.linmusic.core.player.ffmpeg.FfmpegComponentManager
import com.lin0721.linmusic.core.player.data.AmllLyricsClient
import com.lin0721.linmusic.core.player.data.LyricsCache
import com.lin0721.linmusic.core.player.data.QqLyricsClient
import com.lin0721.linmusic.core.player.data.QqLyricsSource
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricRequests
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.NeteaseSongLookup
import com.lin0721.linmusic.core.songquality.SongQualityCache
import com.lin0721.linmusic.core.songquality.warmQueue
import com.lin0721.linmusic.feature.podcast.data.PodcastProgressTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import com.lin0721.linmusic.core.lanlink.LanLinkAndroidClient
import com.lin0721.linmusic.core.lanlink.LanLinkRemoteCoordinator
import com.lin0721.linmusic.core.lanlink.RoutingPlaybackController
import com.lin0721.linmusic.core.player.AndroidVolumeBridge
import com.lin0721.linmusic.core.player.LanLinkMediaRouteCoordinator
import com.lin0721.linmusic.core.player.external.ExternalLyricCoordinator
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val playerModule = module {
    single { FfmpegComponentManager(androidContext()) }
    single { ExternalInterruptionResumeController(androidContext(), get()) }
    single {
        PlayerManager(
            androidContext(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            getOrNull(),
            getOrNull(),
            getOrNull()
        ).also { manager ->
            // 随播放器一同创建，进程内常驻记录播客收听进度
            PodcastProgressTracker(manager, get()).start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        }
    }
    single { AndroidVolumeBridge(androidContext()) }
    single {
        LanLinkRemoteCoordinator(
            localController = get<PlayerManager>(),
            transport = get<LanLinkAndroidClient>(),
            volumeBridge = get<AndroidVolumeBridge>(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            playbackModeStore = get<SettingsPreferences>()
        )
    }
    single {
        RoutingPlaybackController(
            localController = get<PlayerManager>(),
            coordinator = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        ).also { controller ->
            getOrNull<SongQualityCache>()?.warmQueue(controller.queue, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        }
    }
    single<PlaybackController> { get<RoutingPlaybackController>() }
    single { LanLinkMediaRouteCoordinator(androidContext(), get(), get(), get()) }
    single { FloatingLyricRequests() }
    single { SleepTimerRequests() }
    // 离线时的可播放判定：已下载 + 播放缓存已完整
    single<CachedAudioIndex> { AndroidCachedAudioIndex(androidContext(), get(), get()) }
    // AMLL 歌词源：TTML 原文按原始 XML 缓存在 cacheDir 下，解析统一由 shared 的 TtmlLyricParser 负责
    single { AmllLyricsClient(LyricsCache(androidContext().cacheDir)) }
    single { QqLyricsClient() }
    single { QqLyricsSource(get(), get()) }
    single {
        val playbackController = get<PlaybackController>()
        val localMusicApi = get<LocalMusicApi>()
        val amllLyricsClient = get<AmllLyricsClient>()
        val settingsPreferences = get<SettingsPreferences>()
        val qqLyricsSource = get<QqLyricsSource>()
        val neteaseLookup = get<NeteaseSongLookup>()
        val pluginLyrics = get<PluginLyricsSource>()
        val platformRouter = getOrNull<PlatformPlaybackRouter>()
        LyricsResolver(
            playbackRepository = get(),
            readLocalLyrics = localMusicApi::readLyrics,
            localUriOf = { songId -> playbackController.queue.value.firstOrNull { it.songId == songId }?.localUri },
            readAmllLyrics = { songId -> amllLyricsClient.fetch(songId) },
            isAmllEnabled = { settingsPreferences.amllLyricsEnabled.first() },
            isQqEnabled = { settingsPreferences.qqLyricsEnabled.first() },
            isNeteaseEnabled = { settingsPreferences.neteaseLyricsEnabled.first() },
            readQqLyrics = qqLyricsSource::fetch,
            externalOf = { songId -> playbackController.queue.value.firstOrNull { it.songId == songId }?.external },
            matchNeteaseId = neteaseLookup::find,
            readExternalQqLyrics = qqLyricsSource::fetch,
            hasPluginLyrics = pluginLyrics::hasSources,
            readPluginLyrics = pluginLyrics::fetch,
            lyricFilterConfig = { settingsPreferences.lyricFilterConfig.first() },
            readPlatformLyrics = { trackId, kugouMeta, external ->
                platformRouter?.getLyrics(
                    trackId,
                    PlaybackTrackMeta(
                        title = external?.name.orEmpty(),
                        artists = external?.artists.orEmpty(),
                        albumName = external?.albumName.orEmpty(),
                        durationMs = external?.durationMs ?: 0L,
                        kugouMeta = kugouMeta
                    )
                )?.getOrNull().orEmpty()
            },
            kugouMetaOf = { songId -> playbackController.queue.value.firstOrNull { it.songId == songId }?.kugouMeta },
            isKugouEnabled = { settingsPreferences.kugouPlatformEnabled.first() }
        )
    }
    single { ExternalLyricCoordinator(androidContext(), get(), get(), get()) }
}

