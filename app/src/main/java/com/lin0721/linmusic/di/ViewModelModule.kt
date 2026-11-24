package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.auth.KugouLoginViewModel
import com.lin0721.linmusic.core.auth.LoginViewModel
import com.lin0721.linmusic.core.kugou.KugouAuthProvider
import com.lin0721.linmusic.feature.settings.ui.SettingsViewModel
import com.lin0721.linmusic.core.onboarding.OnboardingCoverSource
import com.lin0721.linmusic.feature.onboarding.OnboardingViewModel
import java.io.File
import org.koin.android.ext.koin.androidContext
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.music.ui.MusicViewModel
import com.lin0721.linmusic.feature.music.ui.StyleDetailViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastCategoryViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastHomeViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastSubscribedViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastToplistViewModel
import com.lin0721.linmusic.feature.podcast.ui.RadioDetailViewModel
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import com.lin0721.linmusic.feature.listendata.ui.ListenDataViewModel
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicViewModel
import com.lin0721.linmusic.feature.localmusic.ui.settings.LocalMusicSettingsViewModel
import com.lin0721.linmusic.feature.localmusic.ui.tageditor.LocalTagEditorViewModel
import com.lin0721.linmusic.feature.newworks.ui.NewWorksViewModel
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import com.lin0721.linmusic.feature.playlist.ui.PlaylistViewModel
import com.lin0721.linmusic.feature.profile.ui.FollowListViewModel
import com.lin0721.linmusic.feature.listentogether.ui.ListenTogetherViewModel
import com.lin0721.linmusic.feature.listentogether.ui.RoomSongPickerViewModel
import com.lin0721.linmusic.feature.message.ui.MessageViewModel
import com.lin0721.linmusic.feature.message.ui.PrivateChatViewModel
import com.lin0721.linmusic.feature.message.ui.ShareToFriendViewModel
import com.lin0721.linmusic.feature.profile.ui.ProfileViewModel
import com.lin0721.linmusic.feature.recent.ui.RecentPlayViewModel
import com.lin0721.linmusic.feature.cloud.ui.CloudViewModel
import com.lin0721.linmusic.feature.downloads.ui.DownloadsViewModel
import com.lin0721.linmusic.feature.artist.ui.ArtistViewModel
import com.lin0721.linmusic.core.source.PlatformSearchProvider
import com.lin0721.linmusic.feature.search.ui.SearchViewModel
import com.lin0721.linmusic.feature.search.ui.PlaylistCategoryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin ViewModel 层依赖注入模块
 *
 * 使用 [viewModelOf] 委托自动解析 ViewModel 的构造参数。
 */
val viewModelModule = module {

    viewModel {
        HomeViewModel(
            syncProfileAfterLoginUseCase = get(),
            accountSwitcher = get(),
            playbackRepository = get(),
            homeRepository = get(),
            recentRepository = get(),
            playerManager = get(),
            userPreferences = get(),
            authRepository = get(),
            resourceProvider = get(),
            loadLikedSongIdsUseCase = get(),
            songLikeRepository = get(),
            songCollectDelegate = get(),
            podcastPlayer = get(),
            playlistRepository = get(),
            onlineState = get(),
            userPlaylistRepository = get(),
            songDownloadManager = get(),
            playlistMutationBus = get(),
            settingsPreferences = get(),
            playbackPreferences = get(),
            platformDiscoverRouter = getOrNull(),
            platformPlaylistRouter = getOrNull(),
            platformAlbumRouter = getOrNull(),
            platformLibraryRepository = getOrNull()
        )
    }
    viewModelOf(::MusicViewModel)
    viewModelOf(::StyleDetailViewModel)
    viewModelOf(::PodcastHomeViewModel)
    viewModelOf(::PodcastSubscribedViewModel)
    viewModelOf(::PodcastCategoryViewModel)
    viewModelOf(::PodcastToplistViewModel)
    viewModelOf(::RadioDetailViewModel)
    // 参数超过 Koin 构造引用的 22 个上限，*Of(::) 会把后面带默认值的参数静默置空，必须显式列出
    viewModel {
        PlaylistViewModel(
            songCollectDelegate = get(),
            syncProfileAfterLoginUseCase = get(),
            loadLikedSongIdsUseCase = get(),
            homeRepository = get(),
            libraryRepository = get(),
            commentRepository = get(),
            playlistRepository = get(),
            songLikeRepository = get(),
            playbackRepository = get(),
            createPlaylistAndAddSongUseCase = get(),
            updatePlaylistCoverUseCase = get(),
            playerManager = get(),
            userPreferences = get(),
            resourceProvider = get(),
            playlistMutationBus = get(),
            searchRepository = get(),
            songDownloadManager = get(),
            offlinePlayability = get(),
            trackBatchActions = get(),
            externalStore = get(),
            pluginContent = get(),
            downloadRecognizer = get(),
            songQualityCache = getOrNull(),
            platformPlaylistRouter = getOrNull(),
            platformAlbumRouter = getOrNull(),
            platformCommentRouter = getOrNull(),
            platformLibraryRouter = getOrNull(),
            platformSearchRouter = getOrNull(),
            settingsPreferences = getOrNull(),
            platformDiscoverRouter = getOrNull()
        )
    }
    viewModelOf(::ArtistViewModel)
    viewModel {
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
            linkPreviewResolver = getOrNull(),
            songQualityCache = getOrNull(),
            platformSearchRouter = getOrNull(),
            platformArtistRouter = getOrNull()
        )
    }
    viewModelOf(::PlaylistCategoryViewModel)
    viewModelOf(::LibraryViewModel)
    viewModel {
        RecentPlayViewModel(
            recentRepository = get(),
            playerManager = get(),
            resourceProvider = get(),
            songQualityCache = getOrNull(),
            userPreferences = get(),
            playbackPreferences = get(),
            platformLibraryRepository = getOrNull()
        )
    }
    viewModelOf(::CloudViewModel)
    viewModelOf(::DownloadsViewModel)
    viewModelOf(::ListenDataViewModel)
    viewModelOf(::LocalMusicViewModel)
    viewModelOf(::LocalMusicSettingsViewModel)
    viewModelOf(::LocalTagEditorViewModel)
    viewModelOf(::NewWorksViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::LoginViewModel)
    single { OnboardingCoverSource.fromRepository(get(), File(androidContext().cacheDir, "onboarding_covers.json")) }
    viewModelOf(::OnboardingViewModel)
    viewModel { KugouLoginViewModel(get<KugouAuthProvider>(), get(), get()) }

    viewModelOf(::MessageViewModel)
    viewModelOf(::PrivateChatViewModel)
    viewModelOf(::ShareToFriendViewModel)
    viewModelOf(::ListenTogetherViewModel)
    viewModelOf(::RoomSongPickerViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::FollowListViewModel)

}
