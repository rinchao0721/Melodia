package com.lin0721.linmusic.desktop.di

import com.lin0721.linmusic.core.auth.LoginViewModel
import com.lin0721.linmusic.core.auth.SessionMonitor
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.cache.MetadataCache
import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.network.OnlineStateProvider
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.crypto.XeapiKeyStore
import com.lin0721.linmusic.core.network.crypto.XeapiKeyStoreImpl
import com.lin0721.linmusic.core.offline.CachedAudioIndex
import com.lin0721.linmusic.core.player.LyricsResolver
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.data.AmllLyricsClient
import com.lin0721.linmusic.core.player.data.LyricsCache
import com.lin0721.linmusic.core.player.data.QqLyricsClient
import com.lin0721.linmusic.core.player.data.QqLyricsSource
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.AudioSourceProvider
import com.lin0721.linmusic.core.source.ExternalTrackResolver
import com.lin0721.linmusic.core.source.NeteaseSongLookup
import com.lin0721.linmusic.core.source.LxAudioSourceProvider
import com.lin0721.linmusic.core.source.LxPluginRuntime
import com.lin0721.linmusic.core.source.PlatformSearchProvider
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.source.SourcePreferences
import com.lin0721.linmusic.core.ui.skin.SkinRepository
import com.lin0721.linmusic.core.ui.skin.SkinStore
import com.lin0721.linmusic.desktop.app.DesktopPaths
import com.lin0721.linmusic.desktop.data.CacheRelocator
import com.lin0721.linmusic.desktop.data.DesktopCacheManager
import com.lin0721.linmusic.desktop.data.DesktopLibraryPreferences
import com.lin0721.linmusic.desktop.data.DesktopOnlineStateProvider
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import com.lin0721.linmusic.desktop.data.DesktopResourceProvider
import com.lin0721.linmusic.desktop.data.cloud.CloudLibrary
import com.lin0721.linmusic.desktop.data.recognition.DesktopRecognition
import com.lin0721.linmusic.desktop.data.recognition.GraalAfpFingerprintEngine
import com.lin0721.linmusic.desktop.data.download.DesktopDownloadPreferences
import com.lin0721.linmusic.desktop.data.download.DesktopSongDownloader
import com.lin0721.linmusic.desktop.data.local.LocalLibraryRepository
import com.lin0721.linmusic.desktop.data.local.LocalLibraryStore
import com.lin0721.linmusic.desktop.data.local.LocalMatcher
import com.lin0721.linmusic.desktop.data.local.LocalScanner
import com.lin0721.linmusic.desktop.plugin.GraalLxPluginRuntime
import com.lin0721.linmusic.desktop.player.DesktopPlaybackController
import com.lin0721.linmusic.desktop.player.MpvPlaybackController
import com.lin0721.linmusic.desktop.player.SilentPlaybackController
import com.lin0721.linmusic.desktop.player.cache.AudioCache
import com.lin0721.linmusic.desktop.ui.cloud.CloudMusicActions
import com.lin0721.linmusic.desktop.ui.local.LocalMusicActions
import com.lin0721.linmusic.desktop.ui.settings.DesktopSettingsViewModel
import com.lin0721.linmusic.feature.artist.ui.ArtistViewModel
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.library.data.LibraryPreferences
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import com.lin0721.linmusic.feature.music.ui.MusicViewModel
import com.lin0721.linmusic.feature.music.ui.StyleDetailViewModel
import com.lin0721.linmusic.feature.newworks.ui.NewWorksViewModel
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import com.lin0721.linmusic.feature.playlist.ui.PlaylistViewModel
import com.lin0721.linmusic.feature.podcast.data.PodcastProgressPreferences
import com.lin0721.linmusic.feature.podcast.data.PodcastSeenPreferences
import com.lin0721.linmusic.feature.podcast.ui.PodcastCategoryViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastHomeViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastSubscribedViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastToplistViewModel
import com.lin0721.linmusic.feature.podcast.ui.RadioDetailViewModel
import com.lin0721.linmusic.feature.profile.ui.FollowListViewModel
import com.lin0721.linmusic.feature.profile.ui.ProfileViewModel
import com.lin0721.linmusic.feature.recent.ui.RecentPlayViewModel
import com.lin0721.linmusic.feature.recognition.data.RecognitionHistoryPreferences
import com.lin0721.linmusic.feature.recognition.data.RecognitionPreferences
import com.lin0721.linmusic.feature.search.data.SearchHistoryPreferences
import com.lin0721.linmusic.feature.search.ui.PlaylistCategoryViewModel
import com.lin0721.linmusic.feature.search.ui.SearchViewModel
import com.lin0721.linmusic.feature.settings.ui.CommonSettingsViewModel
import com.lin0721.linmusic.feature.settings.ui.LxPluginSettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File

private const val TAG = "DesktopModule"

// libmpv 缺失或加载失败时退回不出声的占位实现，保证界面仍可使用
private fun createPlaybackController(
    repository: PlaybackRepository,
    settingsPreferences: SettingsPreferences,
    playbackPreferences: PlaybackPreferences,
    desktopPreferences: DesktopPreferences,
    downloadPreferences: DesktopDownloadPreferences,
    audioCache: AudioCache,
    cacheManager: DesktopCacheManager,
    externalResolver: ExternalTrackResolver
): DesktopPlaybackController = try {
    MpvPlaybackController(
        repository, settingsPreferences, playbackPreferences, desktopPreferences,
        localAudioOf = { songId -> downloadPreferences.findVerifiedRecord(songId)?.mediaStoreUri },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        audioCache = audioCache,
        cacheManager = cacheManager,
        externalResolver = externalResolver,
        loudnessStoreFile = File(DesktopPaths.dataDir, "loudness_gain.json")
    )
} catch (e: LinkageError) {
    AppLogger.e(TAG, "libmpv 加载失败，播放不可用", e)
    SilentPlaybackController()
} catch (e: IllegalStateException) {
    AppLogger.e(TAG, "libmpv 初始化失败，播放不可用", e)
    SilentPlaybackController()
}

private fun store(name: String) = PreferencesStores.get(DesktopPaths.preferencesFile(name))

val desktopPlatformModule = module {
    single { UserPreferences(store(PreferencesStores.USER)) }
    single { SettingsPreferences(store(PreferencesStores.SETTINGS)) }
    single { SkinStore(File(DesktopPaths.dataDir, "skins")) }
    single { SkinRepository(get(), get()) }
    single { SourcePreferences(store(PreferencesStores.SOURCE)) }
    single { PlaylistExternalTrackStore(store(PreferencesStores.PLAYLIST_EXTERNAL)) }
    single { GraalLxPluginRuntime() } bind LxPluginRuntime::class
    single<AudioSourceProvider>(named("lx")) { LxAudioSourceProvider(get<LxPluginRuntime>(), get(), getAll<PlatformSearchProvider>()) }
    single { SessionMonitor(get(), get()) }
    single { SearchHistoryPreferences(store(PreferencesStores.SEARCH_HISTORY)) }
    single { PlaybackPreferences(store(PreferencesStores.PLAYBACK)) }
    single { PodcastProgressPreferences(store(PreferencesStores.PODCAST)) }
    single { PodcastSeenPreferences(store(PreferencesStores.PODCAST)) }
    single { DesktopPreferences(store(DesktopPreferences.STORE_NAME)) }
    single<XeapiKeyStore> { XeapiKeyStoreImpl(store(PreferencesStores.XEAPI_KEY)) }
    single { ContentFilter(get()) }
    single<ResourceProvider> { DesktopResourceProvider() }
    // 桌面端不区分 Wi-Fi 与移动网络，统一按 Wi-Fi 音质
    single<NetworkStateProvider> { NetworkStateProvider { true } }
    single<OnlineStateProvider> { DesktopOnlineStateProvider() }
    single {
        val userPreferences = get<UserPreferences>()
        MetadataCache(DesktopPaths.metadataCacheDir) { userPreferences.userProfile.first()?.uid ?: 0L }
    }
    single { DesktopDownloadPreferences(store(DesktopDownloadPreferences.STORE_NAME)) }
    single { AudioCache(DesktopPaths.audioCacheDir) }
    single { LyricsCache(DesktopPaths.localCacheDir) }
    single {
        DesktopCacheManager(
            audioCache = get(),
            lyricsCache = get(),
            lyricsDir = DesktopPaths.lyricsCacheDir,
            metadataDir = DesktopPaths.metadataCacheDir
        )
    }
    single { CacheRelocator(get()) }
    single<CachedAudioIndex> {
        val downloadPreferences = get<DesktopDownloadPreferences>()
        val audioCache = get<AudioCache>()
        CachedAudioIndex { ids ->
            downloadPreferences.findVerifiedRecords(ids).mapTo(HashSet()) { it.songId } + audioCache.playableIds(ids)
        }
    }
    single<LibraryPreferences> { DesktopLibraryPreferences() }
    single {
        DesktopSongDownloader(
            downloadApi = get(),
            playbackRepository = get(),
            settingsPreferences = get(),
            downloadPreferences = get(),
            tempDir = DesktopPaths.downloadTempDir,
            defaultDir = DesktopPaths.defaultDownloadDir
        )
    }
    single<SongDownloader> { get<DesktopSongDownloader>() }
    single<DesktopPlaybackController> { createPlaybackController(get(), get(), get(), get(), get(), get(), get(), get()) }
    single<PlaybackController> { get<DesktopPlaybackController>() }
    single { AmllLyricsClient(get<LyricsCache>()) }
    single { QqLyricsClient() }
    single { QqLyricsSource(get(), get()) }
    // 启动即加载并后台扫描
    single(createdAtStart = true) {
        LocalLibraryRepository(
            store = LocalLibraryStore(DesktopPaths.localLibraryFile),
            scanner = LocalScanner { DesktopPaths.localCoverDir },
            matcher = LocalMatcher(get()),
            downloadPreferences = get(),
            onlineState = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
    }
    single { LocalMusicActions(get(), get<PlaybackController>(), CoroutineScope(SupervisorJob() + Dispatchers.Main)) }
    // 启动即监听账号变化，退出登录时清空云盘数据与上传队列
    single(createdAtStart = true) { CloudLibrary(get(), get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Main)) }
    single { CloudMusicActions(get(), get(), get(), get(), get<PlaybackController>()) }
    single { RecognitionHistoryPreferences(store(PreferencesStores.RECOGNITION_HISTORY)) }
    single { RecognitionPreferences(store(PreferencesStores.RECOGNITION)) }
    single { GraalAfpFingerprintEngine() }
    single {
        val engine = get<GraalAfpFingerprintEngine>()
        DesktopRecognition(get(), get(), get(), engine, get(), CoroutineScope(SupervisorJob() + Dispatchers.Main), warmUp = engine::warmUp)
    }
    single {
        val amllLyricsClient = get<AmllLyricsClient>()
        val settingsPreferences = get<SettingsPreferences>()
        val qqLyricsSource = get<QqLyricsSource>()
        val localLibrary = get<LocalLibraryRepository>()
        val neteaseLookup = get<NeteaseSongLookup>()
        // 播放器创建时不依赖歌词，延迟到查询时再取，避免组装顺序问题
        val queueOf = { get<PlaybackController>().queue.value }
        LyricsResolver(
            playbackRepository = get(),
            readLocalLyrics = { path -> withContext(Dispatchers.IO) { localLibrary.readLyrics(path) } },
            localUriOf = localLibrary::pathForSongId,
            readAmllLyrics = { songId -> amllLyricsClient.fetch(songId) },
            isAmllEnabled = { settingsPreferences.amllLyricsEnabled.first() },
            isQqEnabled = { settingsPreferences.qqLyricsEnabled.first() },
            isNeteaseEnabled = { settingsPreferences.neteaseLyricsEnabled.first() },
            readQqLyrics = qqLyricsSource::fetch,
            externalOf = { songId -> queueOf().firstOrNull { it.songId == songId }?.external },
            matchNeteaseId = neteaseLookup::find,
            readExternalQqLyrics = qqLyricsSource::fetch
        )
    }
}

// 单窗口应用，页面级 ViewModel 随窗口常驻
val desktopViewModelModule = module {
    singleOf(::LoginViewModel)
    singleOf(::HomeViewModel)
    singleOf(::MusicViewModel)
    factoryOf(::StyleDetailViewModel)
    singleOf(::PodcastHomeViewModel)
    factoryOf(::PodcastSubscribedViewModel)
    factoryOf(::PodcastCategoryViewModel)
    factoryOf(::PodcastToplistViewModel)
    factoryOf(::RadioDetailViewModel)
    singleOf(::NewWorksViewModel)
    singleOf(::LibraryViewModel)
    factoryOf(::RecentPlayViewModel)
    factoryOf(::ProfileViewModel)
    factoryOf(::FollowListViewModel)
    single {
        SearchViewModel(
            repository = get(),
            historyPreferences = get(),
            playerManager = get(),
            userPreferences = get(),
            resourceProvider = get(),
            songCollectDelegate = get(),
            loadLikedSongIdsUseCase = get(),
            songLikeRepository = get(),
            syncProfileAfterLoginUseCase = get(),
            externalResolver = get(),
            searchProviders = getAll<PlatformSearchProvider>(),
            settingsPreferences = getOrNull(),
            sourcePreferences = getOrNull(),
            collectionActions = get(),
            artistRepository = get(),
            podcastRepository = get()
        )
    }
    factoryOf(::PlaylistCategoryViewModel)
    factoryOf(::PlaylistViewModel)
    singleOf(::PlayerViewModel)
    factoryOf(::ArtistViewModel)
    singleOf(::CommonSettingsViewModel)
    singleOf(::LxPluginSettingsViewModel)
    single { DesktopSettingsViewModel(get(), get(), get(), get(), get(), get()) }
}
