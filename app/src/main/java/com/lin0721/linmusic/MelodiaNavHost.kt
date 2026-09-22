package com.lin0721.linmusic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import com.lin0721.linmusic.feature.home.ui.HomeScreen
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.profile.ui.FollowListMode

// ────────────────────────────────────────────────────────────────────────────
// 六个主屏幕之间的切换动画与路由分发
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun MelodiaNavHost(
    currentScreen: Screen,
    homeViewModel: HomeViewModel,
    homeTab: Int,
    showMusicNewWorks: Boolean,
    searchAutoFocus: Boolean,
    onOpenSidebar: () -> Unit,
    onLoginScreenVisibilityChanged: (Boolean) -> Unit,
    onNavigateToPlaylist: (id: Long, isAlbum: Boolean) -> Unit,
    onNavigateToArtist: (Long) -> Unit,
    onNavigateToRadio: (Long) -> Unit,
    onNavigateToMv: (Long, String) -> Unit,
    onMvFullscreenChanged: (Boolean) -> Unit,
    onNavigateToPlaylistCategory: (String) -> Unit,
    onNavigateToProfile: (Long) -> Unit,
    onNavigateToFollowList: (Long, FollowListMode) -> Unit,
    onHomeTabSelected: (Int) -> Unit,
    onShowMusicNewWorksChanged: (Boolean) -> Unit,
    onNavigateToSearch: () -> Unit,
    onBack: () -> Unit
) {
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            val forward = targetState != Screen.Home
            val offsetY = 40
            if (forward) {
                (fadeIn(tween(420, delayMillis = 100, easing = FastOutSlowInEasing))
                        + slideInVertically(tween(420, delayMillis = 100, easing = FastOutSlowInEasing)) { offsetY })
                    .togetherWith(
                        fadeOut(tween(300, easing = FastOutSlowInEasing))
                                + slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { -offsetY }
                    )
            } else {
                (fadeIn(tween(420, delayMillis = 100, easing = FastOutSlowInEasing))
                        + slideInVertically(tween(420, delayMillis = 100, easing = FastOutSlowInEasing)) { -offsetY })
                    .togetherWith(
                        fadeOut(tween(300, easing = FastOutSlowInEasing))
                                + slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { offsetY }
                    )
            }.using(SizeTransform(clip = false))
        },
        label = "screen_transition"
    ) { screen ->
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
                    onMvClick = onNavigateToMv,
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
                    onPlaylistCategoryClick = onNavigateToPlaylistCategory
                )
            }
            is Screen.Library -> {
                com.lin0721.linmusic.feature.library.ui.LibraryScreen(
                    onPlaylistClick = { id -> onNavigateToPlaylist(id, false) },
                    onArtistClick = onNavigateToArtist,
                    onAlbumClick = { id -> onNavigateToPlaylist(id, true) },
                    onBack = onBack,
                    onOpenSidebar = onOpenSidebar,
                    onLoginScreenVisibilityChanged = onLoginScreenVisibilityChanged
                )
            }
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
            is Screen.Artist -> {
                com.lin0721.linmusic.feature.artist.ui.ArtistScreen(
                    artistId = screen.id,
                    onBack = onBack,
                    onArtistClick = onNavigateToArtist,
                    onPlaylistClick = { playlistId -> onNavigateToPlaylist(playlistId, false) },
                    onAlbumClick = { albumId -> onNavigateToPlaylist(albumId, true) },
                    onMvClick = onNavigateToMv
                )
            }
            is Screen.MvPlayer -> {
                com.lin0721.linmusic.feature.artist.ui.ArtistMvPlayerScreen(
                    mvId = screen.id,
                    mvName = screen.name,
                    onBack = onBack,
                    onArtistClick = onNavigateToArtist,
                    onMvClick = onNavigateToMv,
                    onFullscreenChanged = onMvFullscreenChanged,
                    onNavigateToProfile = onNavigateToProfile
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
            is Screen.Message -> {
                com.lin0721.linmusic.feature.message.ui.MessageScreen(onBack = onBack)
            }
            is Screen.Account -> {
                com.lin0721.linmusic.feature.account.ui.AccountScreen(onBack = onBack)
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
