package com.lin0721.linmusic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicNavigation
import com.lin0721.linmusic.feature.localmusic.ui.collection.LocalAlbumScreen
import com.lin0721.linmusic.feature.localmusic.ui.collection.LocalAlbumsScreen
import com.lin0721.linmusic.feature.localmusic.ui.collection.LocalArtistScreen
import com.lin0721.linmusic.feature.localmusic.ui.collection.LocalArtistsScreen
import com.lin0721.linmusic.feature.localmusic.ui.collection.LocalFolderScreen
import com.lin0721.linmusic.feature.localmusic.ui.collection.LocalFoldersScreen
import com.lin0721.linmusic.feature.localmusic.ui.home.LocalMusicHomeScreen
import com.lin0721.linmusic.feature.localmusic.ui.playlist.LocalPlaylistScreen
import com.lin0721.linmusic.feature.localmusic.ui.playlist.LocalPlaylistsScreen
import com.lin0721.linmusic.feature.localmusic.ui.songs.LocalSongsScreen
import com.lin0721.linmusic.feature.localmusic.ui.tageditor.LocalTagEditorScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.feature.home.ui.HomeScreen
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.profile.ui.FollowListMode

// ────────────────────────────────────────────────────────────────────────────
// 六个主屏幕之间的切换动画与路由分发
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun MelodiaNavHost(
    currentEntry: NavEntry,
    liveEntryIds: Set<Long>,
    homeViewModel: HomeViewModel,
    homeTab: Int,
    showMusicNewWorks: Boolean,
    searchAutoFocus: Boolean,
    onOpenSidebar: () -> Unit,
    onLoginScreenVisibilityChanged: (Boolean) -> Unit,
    onNavigateToPlaylist: (id: Long, isAlbum: Boolean) -> Unit,
    onNavigateToArtist: (Long) -> Unit,
    onNavigateToRadio: (Long) -> Unit,
    onNavigateToPlaylistCategory: (String) -> Unit,
    onNavigateToProfile: (Long) -> Unit,
    onNavigateToFollowList: (Long, FollowListMode) -> Unit,
    onHomeTabSelected: (Int) -> Unit,
    onShowMusicNewWorksChanged: (Boolean) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLocalMusic: () -> Unit,
    onNavigateToScreen: (Screen) -> Unit,
    onOpenRecognition: () -> Unit,
    onBack: () -> Unit
) {
    val motion = LocalUiMotion.current
    val localMusicNavigation = remember(onBack, onNavigateToScreen, onNavigateToArtist, onNavigateToPlaylist, onLoginScreenVisibilityChanged) {
        LocalMusicNavigation(
            onBack = onBack,
            openSongs = { onNavigateToScreen(Screen.LocalSongs) },
            openArtists = { onNavigateToScreen(Screen.LocalArtists) },
            openAlbums = { onNavigateToScreen(Screen.LocalAlbums) },
            openFolders = { onNavigateToScreen(Screen.LocalFolders) },
            openArtist = { onNavigateToScreen(Screen.LocalArtist(it)) },
            openAlbum = { onNavigateToScreen(Screen.LocalAlbum(it)) },
            openFolder = { onNavigateToScreen(Screen.LocalFolder(it)) },
            openPlaylists = { onNavigateToScreen(Screen.LocalPlaylists) },
            openPlaylist = { onNavigateToScreen(Screen.LocalPlaylist(it)) },
            openSettings = { onNavigateToScreen(Screen.LocalMusicSettings) },
            openOnlineArtist = onNavigateToArtist,
            openOnlineAlbum = { onNavigateToPlaylist(it, true) },
            openTagEditor = { onNavigateToScreen(Screen.LocalTagEditor(it)) },
            onLoginScreenVisibilityChanged = onLoginScreenVisibilityChanged
        )
    }
    val userProfile by homeViewModel.userProfile.collectAsStateWithLifecycle()
    val saveableStateHolder = rememberSaveableStateHolder()
    val frameStores = viewModel<NavFrameStores>()
    val scope = rememberCoroutineScope()
    val latestLiveEntryIds by rememberUpdatedState(liveEntryIds)
    val frameRegistry = remember(saveableStateHolder, frameStores) {
        NavFrameRegistry(saveableStateHolder, frameStores, { latestLiveEntryIds }, scope)
    }
    LaunchedEffect(liveEntryIds) { frameRegistry.reconcile() }

    AnimatedContent(
        targetState = currentEntry,
        transitionSpec = {
            val forward = targetState.screen != Screen.Home
            val offsetY = 40
            if (forward) {
                (fadeIn(motion.tween(420, delayMillis = 100, easing = FastOutSlowInEasing))
                        + slideInVertically(motion.tween(420, delayMillis = 100, easing = FastOutSlowInEasing)) { offsetY })
                    .togetherWith(
                        fadeOut(motion.tween(300, easing = FastOutSlowInEasing))
                                + slideOutVertically(motion.tween(300, easing = FastOutSlowInEasing)) { -offsetY }
                    )
            } else {
                (fadeIn(motion.tween(420, delayMillis = 100, easing = FastOutSlowInEasing))
                        + slideInVertically(motion.tween(420, delayMillis = 100, easing = FastOutSlowInEasing)) { -offsetY })
                    .togetherWith(
                        fadeOut(motion.tween(300, easing = FastOutSlowInEasing))
                                + slideOutVertically(motion.tween(300, easing = FastOutSlowInEasing)) { offsetY }
                    )
            }.using(SizeTransform(clip = false))
        },
        label = "screen_transition"
    ) { entry ->
        frameRegistry.Provide(entry) {
            val screen = entry.screen
            when (screen) {
                is Screen.Home -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        selectedTab = homeTab,
                        onTabSelected = onHomeTabSelected,
                        showNewWorksFeed = showMusicNewWorks,
                        onShowNewWorksFeedChanged = onShowMusicNewWorksChanged,
                        onPlaylistClick = onNavigateToPlaylist,
                        onArtistClick = onNavigateToArtist,
                        onRadioClick = onNavigateToRadio,
                        onPodcastSubscribedClick = { onNavigateToScreen(Screen.PodcastSubscribed) },
                        onPodcastToplistClick = { onNavigateToScreen(Screen.PodcastToplist) },
                        onPodcastCategoryClick = { id, name -> onNavigateToScreen(Screen.PodcastCategory(id, name)) },
                        onStyleClick = { id, name -> onNavigateToScreen(Screen.Style(id, name)) },
                        onSearchClick = onNavigateToSearch,
                        onOpenSidebar = onOpenSidebar,
                        onLoginScreenVisibilityChanged = onLoginScreenVisibilityChanged
                    )
                }
                is Screen.Playlist -> {
                    com.lin0721.linmusic.feature.playlist.ui.PlaylistScreen(
                        playlistId = screen.id,
                        isAlbum = screen.isAlbum,
                        onBack = onBack,
                        onArtistClick = onNavigateToArtist,
                        onAlbumClick = { albumId -> onNavigateToPlaylist(albumId, true) },
                        onNavigateToProfile = onNavigateToProfile
                    )
                }
                is Screen.Search -> {
                    com.lin0721.linmusic.feature.search.ui.SearchScreen(
                        autoFocus = searchAutoFocus,
                        onOpenSidebar = onOpenSidebar,
                        onPlaylistClick = onNavigateToPlaylist,
                        onArtistClick = onNavigateToArtist,
                        onRadioClick = onNavigateToRadio,
                        onPlaylistCategoryClick = onNavigateToPlaylistCategory,
                        onOpenRecognition = onOpenRecognition
                    )
                }
                is Screen.Library -> {
                    com.lin0721.linmusic.feature.library.ui.LibraryScreen(
                        onPlaylistClick = { id -> onNavigateToPlaylist(id, false) },
                        onArtistClick = onNavigateToArtist,
                        onAlbumClick = { id -> onNavigateToPlaylist(id, true) },
                        onBack = onBack,
                        onOpenSidebar = onOpenSidebar,
                        onLoginScreenVisibilityChanged = onLoginScreenVisibilityChanged,
                        onNavigateToLocalMusic = onNavigateToLocalMusic
                    )
                }
                is Screen.LocalMusic -> LocalMusicHomeScreen(navigation = localMusicNavigation)
                is Screen.LocalSongs -> LocalSongsScreen(navigation = localMusicNavigation)
                is Screen.LocalArtists -> LocalArtistsScreen(navigation = localMusicNavigation)
                is Screen.LocalAlbums -> LocalAlbumsScreen(navigation = localMusicNavigation)
                is Screen.LocalFolders -> LocalFoldersScreen(navigation = localMusicNavigation)
                is Screen.LocalArtist -> LocalArtistScreen(name = screen.name, navigation = localMusicNavigation)
                is Screen.LocalAlbum -> LocalAlbumScreen(albumKey = screen.key, navigation = localMusicNavigation)
                is Screen.LocalFolder -> LocalFolderScreen(path = screen.path, navigation = localMusicNavigation)
                is Screen.LocalPlaylists -> LocalPlaylistsScreen(navigation = localMusicNavigation)
                is Screen.LocalPlaylist -> LocalPlaylistScreen(playlistId = screen.id, navigation = localMusicNavigation)
                is Screen.LocalMusicSettings -> {
                    com.lin0721.linmusic.feature.localmusic.ui.settings.LocalMusicSettingsScreen(onBack = onBack)
                }
                is Screen.LocalTagEditor -> LocalTagEditorScreen(trackUri = screen.uri, navigation = localMusicNavigation)
                is Screen.Settings -> {
                    com.lin0721.linmusic.feature.settings.ui.SettingsScreen(
                        onBack = onBack
                    )
                }
                is Screen.Radio -> {
                    com.lin0721.linmusic.feature.podcast.ui.RadioDetailScreen(
                        radioId = screen.id,
                        onBack = onBack
                    )
                }
                is Screen.PodcastSubscribed -> {
                    com.lin0721.linmusic.feature.podcast.ui.PodcastSubscribedScreen(
                        onBack = onBack,
                        onRadioClick = onNavigateToRadio
                    )
                }
                is Screen.PodcastToplist -> {
                    com.lin0721.linmusic.feature.podcast.ui.PodcastToplistScreen(
                        onBack = onBack,
                        onRadioClick = onNavigateToRadio
                    )
                }
                is Screen.PodcastCategory -> {
                    com.lin0721.linmusic.feature.podcast.ui.PodcastCategoryScreen(
                        categoryId = screen.id,
                        name = screen.name,
                        onBack = onBack,
                        onRadioClick = onNavigateToRadio
                    )
                }
                is Screen.Style -> {
                    com.lin0721.linmusic.feature.music.ui.StyleDetailScreen(
                        tagId = screen.id,
                        name = screen.name,
                        onBack = onBack,
                        onPlaylistClick = { playlistId -> onNavigateToPlaylist(playlistId, false) },
                        onAlbumClick = { albumId -> onNavigateToPlaylist(albumId, true) },
                        onArtistClick = onNavigateToArtist
                    )
                }
                is Screen.Artist -> {
                    com.lin0721.linmusic.feature.artist.ui.ArtistScreen(
                        artistId = screen.id,
                        onBack = onBack,
                        onArtistClick = onNavigateToArtist,
                        onPlaylistClick = { playlistId -> onNavigateToPlaylist(playlistId, false) },
                        onAlbumClick = { albumId -> onNavigateToPlaylist(albumId, true) }
                    )
                }
                is Screen.RecentPlay -> {
                    com.lin0721.linmusic.feature.recent.ui.RecentPlayScreen(
                        onBack = onBack,
                        onPlaylistClick = { id -> onNavigateToPlaylist(id, false) },
                        onAlbumClick = { id -> onNavigateToPlaylist(id, true) }
                    )
                }
                is Screen.ListenData -> {
                    com.lin0721.linmusic.feature.listendata.ui.ListenDataScreen(
                        onBack = onBack,
                        onArtistClick = onNavigateToArtist
                    )
                }
                is Screen.Cloud -> {
                    com.lin0721.linmusic.feature.cloud.ui.CloudScreen(onBack = onBack)
                }
                is Screen.Downloads -> {
                    com.lin0721.linmusic.feature.downloads.ui.DownloadsScreen(onBack = onBack)
                }
                is Screen.Message -> {
                    com.lin0721.linmusic.feature.message.ui.MessageScreen(
                        onBack = onBack,
                        onUserClick = onNavigateToProfile
                    )
                }
                is Screen.MyProfile -> {
                    val profile = userProfile
                    if (profile != null) {
                        com.lin0721.linmusic.feature.profile.ui.ProfileScreen(
                            uid = profile.uid,
                            onBack = onBack,
                            onNavigateToFollowList = onNavigateToFollowList,
                            onPlaylistClick = { playlistId -> onNavigateToPlaylist(playlistId, false) }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MelodiaColors.PageFill)
                        )
                    }
                }
                is Screen.PlaylistCategory -> {
                    com.lin0721.linmusic.feature.search.ui.PlaylistCategoryScreen(
                        category = screen.category,
                        onBack = onBack,
                        onPlaylistClick = { id -> onNavigateToPlaylist(id, false) }
                    )
                }
                is Screen.Profile -> {
                    com.lin0721.linmusic.feature.profile.ui.ProfileScreen(
                        uid = screen.uid,
                        onBack = onBack,
                        onNavigateToFollowList = onNavigateToFollowList,
                        onPlaylistClick = { playlistId -> onNavigateToPlaylist(playlistId, false) }
                    )
                }
                is Screen.FollowList -> {
                    com.lin0721.linmusic.feature.profile.ui.FollowListScreen(
                        uid = screen.uid,
                        mode = screen.mode,
                        onBack = onBack,
                        onUserClick = onNavigateToProfile
                    )
                }
            }
        }
    }
}
