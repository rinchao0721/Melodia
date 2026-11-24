package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.auth.NeteaseAuthProvider
import com.lin0721.linmusic.core.auth.PlatformSessionRegistry
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.kugou.KugouAlbumService
import com.lin0721.linmusic.core.kugou.KugouArtistService
import com.lin0721.linmusic.core.kugou.KugouAuthProvider
import com.lin0721.linmusic.core.kugou.KugouAuthService
import com.lin0721.linmusic.core.kugou.KugouCloudService
import com.lin0721.linmusic.core.kugou.KugouCommentService
import com.lin0721.linmusic.core.kugou.KugouConfig
import com.lin0721.linmusic.core.kugou.KugouDeviceStore
import com.lin0721.linmusic.core.kugou.KugouDiscoverService
import com.lin0721.linmusic.core.kugou.KugouGatewayClient
import com.lin0721.linmusic.core.kugou.KugouLikeService
import com.lin0721.linmusic.core.kugou.KugouPlaybackService
import com.lin0721.linmusic.core.kugou.KugouArtistLink
import com.lin0721.linmusic.core.kugou.KugouTrackLinkResolver
import com.lin0721.linmusic.core.kugou.KugouPlaylistService
import com.lin0721.linmusic.core.kugou.KugouSearchService
import com.lin0721.linmusic.core.platform.repository.KugouPlatformAlbumRepository
import com.lin0721.linmusic.core.platform.repository.KugouPlatformArtistRepository
import com.lin0721.linmusic.core.platform.repository.KugouPlatformCloudRepository
import com.lin0721.linmusic.core.platform.repository.KugouPlatformCommentRepository
import com.lin0721.linmusic.core.platform.repository.KugouPlatformDiscoverRepository
import com.lin0721.linmusic.core.platform.repository.KugouPlatformLibraryRepository
import com.lin0721.linmusic.core.platform.repository.KugouPlatformPlaylistRepository
import com.lin0721.linmusic.core.platform.repository.KugouPlatformSearchRepository
import com.lin0721.linmusic.core.platform.repository.NeteasePlatformAlbumRepository
import com.lin0721.linmusic.core.platform.repository.NeteasePlatformArtistRepository
import com.lin0721.linmusic.core.platform.repository.NeteasePlatformCloudRepository
import com.lin0721.linmusic.core.platform.repository.NeteasePlatformCommentRepository
import com.lin0721.linmusic.core.platform.repository.NeteasePlatformDiscoverRepository
import com.lin0721.linmusic.core.platform.repository.NeteasePlatformLibraryRepository
import com.lin0721.linmusic.core.platform.repository.NeteasePlatformPlaylistRepository
import com.lin0721.linmusic.core.platform.repository.NeteasePlatformSearchRepository
import com.lin0721.linmusic.core.platform.repository.PlatformAlbumRepository
import com.lin0721.linmusic.core.platform.repository.PlatformAlbumRouter
import com.lin0721.linmusic.core.platform.repository.PlatformArtistRepository
import com.lin0721.linmusic.core.platform.repository.PlatformArtistRouter
import com.lin0721.linmusic.core.platform.repository.PlatformCloudRepository
import com.lin0721.linmusic.core.platform.repository.PlatformCloudRouter
import com.lin0721.linmusic.core.platform.repository.PlatformCommentRepository
import com.lin0721.linmusic.core.platform.repository.PlatformCommentRouter
import com.lin0721.linmusic.core.platform.repository.PlatformDiscoverRepository
import com.lin0721.linmusic.core.platform.repository.PlatformDiscoverRouter
import com.lin0721.linmusic.core.platform.repository.PlatformLibraryRepository
import com.lin0721.linmusic.core.platform.repository.PlatformLibraryRouter
import com.lin0721.linmusic.core.platform.repository.PlatformPlaylistRepository
import com.lin0721.linmusic.core.platform.repository.PlatformPlaylistRouter
import com.lin0721.linmusic.core.platform.repository.PlatformSearchRepository
import com.lin0721.linmusic.core.platform.repository.PlatformSearchRouter
import com.lin0721.linmusic.core.platform.repository.QQPlatformCommentRepository
import com.lin0721.linmusic.core.player.KugouPlaybackProvider
import com.lin0721.linmusic.core.player.PlatformPlaybackRouter
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.PlaybackRepositoryImpl
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.QQCommentApi
import com.lin0721.linmusic.feature.playlist.domain.UpdatePlaylistCoverUseCase
import kotlinx.coroutines.flow.first
import org.koin.dsl.module

// 酷狗独立网络/加密/鉴权/播放/业务服务模块与跨平台路由注册表
val kugouModule = module {
    val isKugouEnabled: org.koin.core.scope.Scope.() -> (suspend () -> Boolean) = {
        { getOrNull<SettingsPreferences>()?.kugouPlatformEnabled?.first() ?: true }
    }

    single {
        KugouConfig(
            modeSynchronizer = {
                getOrNull<SettingsPreferences>()?.kugouClientMode?.first() ?: KugouConfig().mode
            }
        )
    }
    single { KugouDeviceStore(get<UserPreferences>().preferencesDataStore) }
    single { KugouGatewayClient(get(), get(), get()) }
    single { KugouAuthService(get(), get(), get(), get()) }
    single { KugouAuthProvider(get(), get()) }
    single {
        KugouPlaybackService(
            config = get(),
            deviceStore = get(),
            gatewayClient = get(),
            userPreferences = get(),
            verifyPlaybackAuth = { get<KugouAuthService>().verifyPlaybackAuth() }
        )
    }
    single { KugouLikeService(get(), get()) }
    single { KugouPlaylistService(get(), get(), get()) }
    single { KugouSearchService(get(), get()) }
    single { KugouArtistService(get(), get(), get(), get()) }
    single { KugouAlbumService(get()) }
    single {
        val playback = get<KugouPlaybackService>()
        val album = get<KugouAlbumService>()
        KugouTrackLinkResolver(
            albumIdOfHash = { hash -> playback.getAudioMetadata(listOf(hash)).getOrNull()?.firstOrNull()?.albumId },
            authorsOfAlbum = { albumId ->
                album.getAlbumDetail(albumId).getOrNull()?.authors.orEmpty()
                    .map { KugouArtistLink(id = it.id, name = it.name) }
            }
        )
    }
    single { KugouCommentService(get(), get(), get(), get()) }
    single { KugouDiscoverService(get(), get(), get(), get()) }
    single { KugouCloudService(get(), get(), get(), get()) }
    single {
        KugouPlaybackProvider(
            playbackService = get(),
            lyricsCache = getOrNull(),
            isEnabled = isKugouEnabled(),
            cloudService = get()
        )
    }
    single {
        PlatformPlaybackRouter(
            providers = listOf(
                get<KugouPlaybackProvider>()
            ),
            playbackPreferences = getOrNull(),
            userPreferences = getOrNull()
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

    single { NeteasePlatformLibraryRepository(get(), get(), get()) }
    single { KugouPlatformLibraryRepository(get(), get(), get(), isKugouEnabled()) }
    single {
        PlatformLibraryRouter(
            netease = get<NeteasePlatformLibraryRepository>(),
            kugou = get<KugouPlatformLibraryRepository>(),
            isKugouEnabled = isKugouEnabled()
        )
    }
    single<PlatformLibraryRepository> { get<PlatformLibraryRouter>() }

    single {
        NeteasePlatformPlaylistRepository(
            playlistRepository = get(),
            createRepository = get(),
            playerRepository = get(),
            coverUploader = { playlistId, imageBytes ->
                getOrNull<UpdatePlaylistCoverUseCase>()?.invoke(playlistId, imageBytes)
                    ?: Result.failure(IllegalStateException("未注册网易云封面上传用例"))
            }
        )
    }
    single { KugouPlatformPlaylistRepository(get(), isKugouEnabled()) }
    single {
        PlatformPlaylistRouter(
            netease = get<NeteasePlatformPlaylistRepository>(),
            kugou = get<KugouPlatformPlaylistRepository>(),
            externalStore = get(),
            isKugouEnabled = isKugouEnabled()
        )
    }
    single<PlatformPlaylistRepository> { get<PlatformPlaylistRouter>() }

    single { NeteasePlatformSearchRepository(get()) }
    single { KugouPlatformSearchRepository(get(), isKugouEnabled()) }
    single {
        PlatformSearchRouter(
            netease = get<NeteasePlatformSearchRepository>(),
            kugou = get<KugouPlatformSearchRepository>(),
            isKugouEnabled = isKugouEnabled()
        )
    }
    single<PlatformSearchRepository> { get<PlatformSearchRouter>() }

    single { NeteasePlatformArtistRepository(get()) }
    single { KugouPlatformArtistRepository(get(), isKugouEnabled()) }
    single {
        PlatformArtistRouter(
            netease = get<NeteasePlatformArtistRepository>(),
            kugou = get<KugouPlatformArtistRepository>(),
            isKugouEnabled = isKugouEnabled()
        )
    }
    single<PlatformArtistRepository> { get<PlatformArtistRouter>() }

    single { NeteasePlatformAlbumRepository(get()) }
    single { KugouPlatformAlbumRepository(get(), isKugouEnabled()) }
    single {
        PlatformAlbumRouter(
            netease = get<NeteasePlatformAlbumRepository>(),
            kugou = get<KugouPlatformAlbumRepository>(),
            isKugouEnabled = isKugouEnabled()
        )
    }
    single<PlatformAlbumRepository> { get<PlatformAlbumRouter>() }

    single { NeteasePlatformCommentRepository(get()) }
    single { KugouPlatformCommentRepository(get(), get(), isKugouEnabled()) }
    single { QQPlatformCommentRepository(QQCommentApi()) }
    single {
        PlatformCommentRouter(
            netease = get<NeteasePlatformCommentRepository>(),
            kugou = get<KugouPlatformCommentRepository>(),
            qq = get<QQPlatformCommentRepository>(),
            isKugouEnabled = isKugouEnabled()
        )
    }
    single<PlatformCommentRepository> { get<PlatformCommentRouter>() }

    single {
        NeteasePlatformDiscoverRepository(
            homeRepository = get(),
            playbackRepository = getOrNull<PlaybackRepository>() ?: get<PlaybackRepositoryImpl>(),
            playlistRepository = get()
        )
    }
    single { KugouPlatformDiscoverRepository(get(), isKugouEnabled()) }
    single {
        PlatformDiscoverRouter(
            netease = get<NeteasePlatformDiscoverRepository>(),
            kugou = get<KugouPlatformDiscoverRepository>(),
            isKugouEnabled = isKugouEnabled()
        )
    }
    single<PlatformDiscoverRepository> { get<PlatformDiscoverRouter>() }

    single { NeteasePlatformCloudRepository(get()) }
    single { KugouPlatformCloudRepository(get(), isKugouEnabled()) }
    single {
        PlatformCloudRouter(
            netease = get<NeteasePlatformCloudRepository>(),
            kugou = get<KugouPlatformCloudRepository>(),
            isKugouEnabled = isKugouEnabled()
        )
    }
    single<PlatformCloudRepository> { get<PlatformCloudRouter>() }
}
