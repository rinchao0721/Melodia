package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import com.lin0721.linmusic.desktop.ui.artist.ArtistPage
import com.lin0721.linmusic.desktop.ui.cloud.CloudPage
import com.lin0721.linmusic.desktop.ui.home.HomePage
import com.lin0721.linmusic.desktop.ui.home.StyleDetailPage
import com.lin0721.linmusic.desktop.ui.local.LocalPlaylistPage
import com.lin0721.linmusic.desktop.ui.navigation.DesktopEntry
import com.lin0721.linmusic.desktop.ui.navigation.DesktopFrameHost
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.playlist.PlaylistPage
import com.lin0721.linmusic.desktop.ui.podcast.PodcastCategoryPage
import com.lin0721.linmusic.desktop.ui.podcast.PodcastSubscribedPage
import com.lin0721.linmusic.desktop.ui.podcast.PodcastToplistPage
import com.lin0721.linmusic.desktop.ui.podcast.RadioDetailPage
import com.lin0721.linmusic.desktop.ui.profile.FollowListPage
import com.lin0721.linmusic.desktop.ui.profile.ProfilePage
import com.lin0721.linmusic.desktop.ui.profile.RecentPlayPage
import com.lin0721.linmusic.desktop.ui.search.BrowsePage
import com.lin0721.linmusic.desktop.ui.search.PlaylistCategoryPage
import com.lin0721.linmusic.desktop.ui.search.SearchPage
import com.lin0721.linmusic.desktop.ui.settings.SettingsPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

// 取该栈帧专属的页面 ViewModel；toasts 为需要转发到全局提示的事件流
private inline fun <reified T : ViewModel> DesktopFrameHost.pageViewModel(
    entry: DesktopEntry,
    factory: PageViewModelFactory,
    noinline toasts: (T) -> Flow<String> = { emptyFlow() }
): T = viewModelFor(entry.id, T::class, toasts) { factory.create(T::class) }

// 路由到页面的映射；对密封的 DesktopRoute 穷尽匹配，新增路由漏写时编译报错
@Composable
internal fun RouteContent(
    entry: DesktopEntry,
    frameHost: DesktopFrameHost,
    deps: DesktopAppDependencies,
    state: DesktopAppState
) {
    val factory = deps.pageViewModels
    val controller = deps.playbackController
    when (val route = entry.route) {
        DesktopRoute.Home -> HomePage(
            viewModel = deps.homeViewModel,
            musicViewModel = deps.musicViewModel,
            podcastViewModel = deps.podcastViewModel,
            newWorksViewModel = deps.newWorksViewModel,
            selectedTab = state.homeTab,
            onTabSelected = state::selectHomeTab,
            newWorksSelected = state.showNewWorks,
            onNewWorksSelectedChange = state::selectNewWorks,
            onPlaylistClick = { id, title -> state.navigate(DesktopRoute.Playlist(id, title)) },
            onStyleClick = { id, name -> state.navigate(DesktopRoute.Style(id, name)) }
        )
        is DesktopRoute.Style -> StyleDetailPage(
            tagId = route.id,
            name = route.name,
            viewModel = frameHost.pageViewModel(entry, factory) { it.toastEvent },
            controller = controller
        )
        is DesktopRoute.Playlist -> PlaylistPage(
            playlistId = route.id,
            isAlbum = route.isAlbum,
            viewModel = frameHost.pageViewModel(entry, factory) { it.toastEvent },
            controller = controller
        )
        is DesktopRoute.LocalPlaylist -> LocalPlaylistPage(
            playlistId = route.id,
            actions = deps.localMusic,
            cloud = deps.cloudMusic,
            controller = controller,
            songActions = deps.songActions
        )
        DesktopRoute.Cloud -> CloudPage(
            actions = deps.cloudMusic,
            controller = controller,
            songActions = deps.songActions
        )
        DesktopRoute.Browse -> BrowsePage(
            viewModel = deps.searchViewModel,
            onHotSearchClick = { keyword ->
                state.navigate(DesktopRoute.Search)
                deps.searchViewModel.searchWithKeyword(keyword)
            },
            onCategoryClick = { state.navigate(DesktopRoute.PlaylistCategory(it)) }
        )
        is DesktopRoute.PlaylistCategory -> PlaylistCategoryPage(
            category = route.name,
            viewModel = frameHost.pageViewModel(entry, factory) { it.toastEvent },
            onPlaylistClick = { id, title -> state.navigate(DesktopRoute.Playlist(id, title)) }
        )
        is DesktopRoute.Artist -> ArtistPage(
            artistId = route.id,
            viewModel = frameHost.pageViewModel(entry, factory) { it.toastEvent },
            controller = controller
        )
        is DesktopRoute.Radio -> RadioDetailPage(
            radioId = route.id,
            viewModel = frameHost.pageViewModel(entry, factory) { it.toastEvent }
        )
        DesktopRoute.PodcastSubscribed -> PodcastSubscribedPage(viewModel = frameHost.pageViewModel(entry, factory))
        DesktopRoute.PodcastToplist -> PodcastToplistPage(viewModel = frameHost.pageViewModel(entry, factory))
        is DesktopRoute.PodcastCategory -> PodcastCategoryPage(
            categoryId = route.id,
            name = route.name,
            viewModel = frameHost.pageViewModel(entry, factory)
        )
        DesktopRoute.RecentPlay -> RecentPlayPage(
            viewModel = frameHost.pageViewModel(entry, factory),
            controller = controller
        )
        is DesktopRoute.Profile -> ProfilePage(
            uid = route.uid,
            viewModel = frameHost.pageViewModel(entry, factory) { it.toastEvent },
            controller = controller
        )
        is DesktopRoute.FollowList -> FollowListPage(
            uid = route.uid,
            mode = route.mode,
            viewModel = frameHost.pageViewModel(entry, factory) { it.toastEvent }
        )
        DesktopRoute.Settings -> SettingsPage(
            common = deps.commonSettingsViewModel,
            desktop = deps.desktopSettingsViewModel,
            lxPlugins = deps.lxPluginSettingsViewModel,
            plugins = deps.plugins,
            updateChecker = deps.updateChecker,
            localLibrary = deps.localMusic.repository,
            focusCategory = state.settingsFocus,
            onFocusConsumed = { state.settingsFocus = null }
        )
        DesktopRoute.Search -> SearchPage(
            viewModel = deps.searchViewModel,
            controller = controller,
            onOpenPlaylist = { id, title, isAlbum -> state.navigate(DesktopRoute.Playlist(id, title, isAlbum)) }
        )
    }
}
