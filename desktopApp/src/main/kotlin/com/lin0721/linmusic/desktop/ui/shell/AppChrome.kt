package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.desktop.player.AudioOutputControl
import com.lin0721.linmusic.desktop.ui.cloud.uploadBatchMessage
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.nowplaying.DockOverlay
import com.lin0721.linmusic.desktop.ui.player.PlayerBar
import com.lin0721.linmusic.desktop.ui.player.collectSelectedAudioDevice
import com.lin0721.linmusic.desktop.ui.search.SearchDropdownState
import com.lin0721.linmusic.feature.search.ui.DiscoveryUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// 标题栏接线：导航、搜索、下载入口与窗口按钮
@Composable
internal fun WindowScope.AppTitleBar(
    state: DesktopAppState,
    deps: DesktopAppDependencies,
    windowState: WindowState,
    userProfile: UserProfile?,
    searchFocusRequests: Flow<Unit>,
    dropdown: SearchDropdownState,
    onClose: () -> Unit
) {
    val searchViewModel = deps.searchViewModel
    val searchInput by searchViewModel.inputState.collectAsState()
    val discovery by searchViewModel.discoveryState.collectAsState()
    val downloadTasks by deps.downloader.tasks.collectAsState()
    val availableUpdate by deps.updateChecker.availableUpdate.collectAsState()
    LaunchedEffect(Unit) { searchViewModel.songHitsEnabled = true }
    LaunchedEffect(Unit) {
        deps.cloudMusic.uploadRunner.batchFinished.collect { state.showMessage(uploadBatchMessage(it.succeeded, it.failed)) }
    }
    val defaultKeyword = (discovery as? DiscoveryUiState.Success)?.defaultKeyword.orEmpty()
    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val openSearch = { state.navigate(DesktopRoute.Search) }
    TitleBar(
        canGoBack = state.canGoBack,
        canGoForward = state.canGoForward,
        onBack = state::navigateBack,
        onForward = state::navigateForward,
        isHomeAll = state.isHomeAll,
        onHomeClick = state::goHomeAll,
        isMaximized = isMaximized,
        userProfile = userProfile,
        searchQuery = searchInput.query,
        searchPlaceholder = defaultKeyword.ifBlank { "想播放什么？" },
        onSearchQueryChange = { query ->
            dropdown.selected = SearchDropdownState.NO_SELECTION
            searchViewModel.updateDraft(query)
        },
        onSearchFocusChange = { focused ->
            dropdown.onFocusChange(focused)
            // 提交搜索后联想被清空，再次聚焦时按当前文字补拉
            if (focused && searchInput.query.isNotBlank() && searchInput.currentSuggestions.isEmpty()) {
                searchViewModel.updateDraft(searchInput.query)
            }
        },
        onSearchBoundsChange = { dropdown.anchor = it },
        onSearchMove = dropdown::move,
        searchFocusRequests = searchFocusRequests,
        onSearchSubmit = {
            if (!dropdown.activateSelected()) {
                dropdown.close()
                openSearch()
                searchViewModel.searchWithKeyword(searchInput.query.ifBlank { defaultKeyword })
            }
        },
        isBrowseActive = state.backStack.current == DesktopRoute.Browse,
        onBrowseClick = { state.navigate(DesktopRoute.Browse) },
        recognitionOpen = state.showRecognition,
        onRecognitionClick = { state.showRecognition = true },
        cloudMusic = deps.cloudMusic,
        downloadTasks = downloadTasks,
        downloadsOpen = state.isOverlayOpen(DockOverlay.Downloads),
        onDownloadsClick = { state.toggleOverlay(DockOverlay.Downloads) },
        onLoginClick = { state.showLogin = true },
        onProfileClick = { userProfile?.let { state.navigate(DesktopRoute.Profile(it.uid)) } },
        onRecentClick = { state.navigate(DesktopRoute.RecentPlay) },
        onCloudClick = state::openCloud,
        onSettingsClick = { state.navigate(DesktopRoute.Settings) },
        hasUpdate = availableUpdate != null,
        onLogoutClick = deps.homeViewModel::logout,
        onMinimize = { windowState.isMinimized = true },
        onToggleMaximize = {
            windowState.placement = if (isMaximized) WindowPlacement.Floating else WindowPlacement.Maximized
        },
        onClose = onClose
    )
}

// 播放栏接线：音量、右侧栏面板开关、全屏与歌词入口
@Composable
internal fun AppPlayerBar(
    state: DesktopAppState,
    deps: DesktopAppDependencies,
    audioOutput: AudioOutputControl?,
    fullscreen: FullscreenState,
    onEnterMiniPlayer: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val controller = deps.playbackController
    val lyricsView = state.lyricsView
    val volume = controller.volume.collectAsState().value.takeIf { audioOutput != null }
    val lastAudibleVolume by controller.lastAudibleVolume.collectAsState()
    val showDesktopLyric by deps.settingsPreferences.showDesktopLrc.collectAsState(initial = false)
    PlayerBar(
        controller = controller,
        playerViewModel = deps.playerViewModel,
        volume = volume,
        lastAudibleVolume = lastAudibleVolume,
        onVolumeChange = controller::setVolume,
        nowPlayingOpen = state.dockOpen && state.dockOverlay == null,
        onToggleNowPlaying = state::toggleNowPlaying,
        queueOpen = state.isOverlayOpen(DockOverlay.Queue),
        onToggleQueue = { state.toggleOverlay(DockOverlay.Queue) },
        devicesOpen = state.isOverlayOpen(DockOverlay.Devices),
        // 占位播放器没有输出设备能力时不显示按钮
        onToggleDevices = audioOutput?.let { { state.toggleOverlay(DockOverlay.Devices) } },
        outputDevice = audioOutput?.let { collectSelectedAudioDevice(it) },
        isFullscreen = fullscreen.isFullscreen,
        onToggleFullscreen = fullscreen::toggle,
        lyricVisible = showDesktopLyric,
        onToggleLyric = { scope.launch { deps.settingsPreferences.saveShowDesktopLrc(!showDesktopLyric) } },
        lyricsViewOpen = lyricsView.isOpen,
        onToggleLyricsView = state::toggleLyricsView,
        onEnterMiniPlayer = onEnterMiniPlayer
    )
}
