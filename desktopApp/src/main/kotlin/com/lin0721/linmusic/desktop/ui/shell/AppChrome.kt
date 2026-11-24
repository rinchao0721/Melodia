package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.platform.PlatformCapabilities
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.desktop.data.LibraryShortcut
import com.lin0721.linmusic.desktop.data.TitleBarButton
import com.lin0721.linmusic.desktop.player.AudioOutputControl
import com.lin0721.linmusic.desktop.ui.cloud.uploadBatchMessage
import com.lin0721.linmusic.desktop.ui.library.activeLibraryShortcuts
import com.lin0721.linmusic.desktop.ui.library.performLibraryShortcutClick
import com.lin0721.linmusic.desktop.ui.library.shortcutTooltip
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.nowplaying.DockOverlay
import com.lin0721.linmusic.core.lanlink.PlaybackTarget
import com.lin0721.linmusic.desktop.ui.player.PlayerBar
import com.lin0721.linmusic.desktop.ui.player.collectSelectedAudioDevice
import com.lin0721.linmusic.desktop.ui.search.SearchDropdownState
import com.lin0721.linmusic.feature.search.ui.DiscoveryUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// 标题栏接线：导航、搜索、自定义按钮入口与窗口按钮
@Composable
internal fun WindowScope.AppTitleBar(
    state: DesktopAppState,
    deps: DesktopAppDependencies,
    windowState: WindowState,
    userProfile: UserProfile?,
    searchFocusRequests: Flow<Unit>,
    dropdown: SearchDropdownState,
    onTitleBarBoundsChange: (Rect) -> Unit,
    onClose: () -> Unit
) {
    val searchViewModel = deps.searchViewModel
    val searchInput by searchViewModel.inputState.collectAsState()
    val discovery by searchViewModel.discoveryState.collectAsState()
    val kugouDiscovery by searchViewModel.kugouDiscoveryState.collectAsState()
    val selectedSearchPlatform by searchViewModel.selectedPlatform.collectAsState()
    val downloadTasks by deps.downloader.tasks.collectAsState()
    val availableUpdate by deps.updateChecker.availableUpdate.collectAsState()
    val privateUnreadCount by deps.privateUnreadMonitor.unreadCount.collectAsState()
    val allAccounts by deps.userPreferences.allAccounts.collectAsState(initial = emptyList())
    val currentAccountId by deps.userPreferences.currentAccountId.collectAsState(initial = null)
    val currentPlatform by deps.userPreferences.currentPlatform.collectAsState(initial = MusicPlatform.NETEASE)
    val activeNeteaseAccount by deps.userPreferences.activeAccountOf(MusicPlatform.NETEASE).collectAsState(initial = null)
    val activeKugouAccount by deps.userPreferences.activeAccountOf(MusicPlatform.KUGOU).collectAsState(initial = null)
    val kugouPlatformEnabled by deps.settingsPreferences.kugouPlatformEnabled.collectAsState(initial = true)
    val browsePlatform by deps.homeViewModel.browsePlatform.collectAsState()
    val capabilities = PlatformCapabilities.of(browsePlatform)
    val titleBarLayout by deps.desktopSettingsViewModel.titleBarLayout.collectAsState()
    val playContext by deps.playbackController.playContext.collectAsState()
    val currentRoute = state.backStack.current
    val activeShortcuts = remember(playContext, currentRoute) {
        activeLibraryShortcuts(playContext, currentRoute)
    }
    val accountScope = rememberCoroutineScope()
    val accountEntries = remember(allAccounts, currentAccountId, activeNeteaseAccount, kugouPlatformEnabled) {
        buildAccountMenuEntries(allAccounts, currentAccountId, activeNeteaseAccount != null, kugouPlatformEnabled)
    }
    val addablePlatforms = remember(allAccounts, kugouPlatformEnabled) {
        addableAccountPlatforms(allAccounts, kugouPlatformEnabled)
    }
    var pendingSwitch by remember { mutableStateOf<AccountMenuEntry?>(null) }
    val performSwitch: (AccountMenuEntry) -> Unit = { entry ->
        accountScope.launch {
            val accountId = entry.accountId
            if (accountId == null) {
                if (deps.accountSwitcher.browseAsGuest()) state.showMessage("已切换至网易云游客")
            } else if (deps.accountSwitcher.switchToAccount(accountId)) {
                state.showMessage("已切换至 ${entry.displayName}")
            }
        }
    }
    LaunchedEffect(Unit) { searchViewModel.songHitsEnabled = true }
    LaunchedEffect(Unit) {
        deps.cloudMusic.uploadRunner.batchFinished.collect { state.showMessage(uploadBatchMessage(it.succeeded, it.failed)) }
    }
    val activeDiscovery = if (selectedSearchPlatform == MusicPlatform.KUGOU && kugouPlatformEnabled) {
        kugouDiscovery
    } else {
        discovery
    }
    val defaultKeyword = (activeDiscovery as? DiscoveryUiState.Success)?.defaultKeyword.orEmpty()
        .ifBlank { (discovery as? DiscoveryUiState.Success)?.defaultKeyword.orEmpty() }
    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val openSearch = { state.navigate(DesktopRoute.Search) }

    val isHomeAll = state.isHomeAll
    val recognitionOpen = state.showRecognition
    val messagesOpen = currentRoute is DesktopRoute.Messages
    val downloadsOpen = state.isOverlayOpen(DockOverlay.Downloads)
    val isPersonalFmActive = LibraryShortcut.PERSONAL_FM in activeShortcuts
    val isDailyRecommendActive = LibraryShortcut.DAILY_RECOMMEND in activeShortcuts
    val settingsOpen = currentRoute == DesktopRoute.Settings
    val isLocalMusicActive = LibraryShortcut.LOCAL_MUSIC in activeShortcuts
    val recentPlayOpen = currentRoute == DesktopRoute.RecentPlay

    val activeTitleBarButtons = remember(
        isHomeAll, recognitionOpen, messagesOpen, downloadsOpen,
        isPersonalFmActive, isDailyRecommendActive, settingsOpen, isLocalMusicActive, recentPlayOpen
    ) {
        buildSet {
            if (isHomeAll) add(TitleBarButton.HOME)
            if (recognitionOpen) add(TitleBarButton.RECOGNITION)
            if (messagesOpen) add(TitleBarButton.MESSAGES)
            if (downloadsOpen) add(TitleBarButton.DOWNLOADS)
            if (isPersonalFmActive) add(TitleBarButton.PERSONAL_FM)
            if (isDailyRecommendActive) add(TitleBarButton.DAILY_RECOMMEND)
            if (settingsOpen) add(TitleBarButton.SETTINGS)
            if (isLocalMusicActive) add(TitleBarButton.LOCAL_MUSIC)
            if (recentPlayOpen) add(TitleBarButton.RECENT_PLAY)
        }
    }
    val titleBarTooltips = remember(isPersonalFmActive, isDailyRecommendActive, isLocalMusicActive) {
        mapOf(
            TitleBarButton.PERSONAL_FM to shortcutTooltip(LibraryShortcut.PERSONAL_FM, isPersonalFmActive),
            TitleBarButton.DAILY_RECOMMEND to shortcutTooltip(LibraryShortcut.DAILY_RECOMMEND, isDailyRecommendActive),
            TitleBarButton.LOCAL_MUSIC to shortcutTooltip(LibraryShortcut.LOCAL_MUSIC, isLocalMusicActive)
        )
    }

    TitleBar(
        canGoBack = state.canGoBack,
        canGoForward = state.canGoForward,
        onBack = state::navigateBack,
        onForward = state::navigateForward,
        titleBarLayout = titleBarLayout,
        onTitleBarLayoutChange = deps.desktopSettingsViewModel::updateTitleBarLayout,
        onToggleTitleBarButton = deps.desktopSettingsViewModel::toggleTitleBarButton,
        editMode = state.titleBarEditMode,
        onEditModeChange = { state.titleBarEditMode = it },
        onTitleBarBoundsChange = onTitleBarBoundsChange,
        activeTitleBarButtons = activeTitleBarButtons,
        titleBarTooltips = titleBarTooltips,
        onTitleBarButtonClick = { button ->
            when (button) {
                TitleBarButton.HOME -> state.goHomeAll()
                TitleBarButton.RECOGNITION -> state.showRecognition = true
                TitleBarButton.MESSAGES -> {
                    if (userProfile != null) {
                        state.navigate(DesktopRoute.Messages())
                    } else {
                        state.showMessage("请先登录账号")
                        state.showLogin = true
                    }
                }
                TitleBarButton.DOWNLOADS -> state.toggleOverlay(DockOverlay.Downloads)
                TitleBarButton.PERSONAL_FM -> performLibraryShortcutClick(
                    LibraryShortcut.PERSONAL_FM,
                    isPersonalFmActive,
                    state,
                    deps,
                    userProfile != null,
                    browsePlatform = browsePlatform,
                    isKugouLoggedIn = activeKugouAccount != null,
                    kugouPlatformEnabled = kugouPlatformEnabled
                )
                TitleBarButton.DAILY_RECOMMEND -> performLibraryShortcutClick(
                    LibraryShortcut.DAILY_RECOMMEND,
                    isDailyRecommendActive,
                    state,
                    deps,
                    userProfile != null,
                    browsePlatform = browsePlatform,
                    isKugouLoggedIn = activeKugouAccount != null,
                    kugouPlatformEnabled = kugouPlatformEnabled
                )
                TitleBarButton.SETTINGS -> state.navigate(DesktopRoute.Settings)
                TitleBarButton.LOCAL_MUSIC -> performLibraryShortcutClick(
                    LibraryShortcut.LOCAL_MUSIC,
                    isLocalMusicActive,
                    state,
                    deps,
                    userProfile != null,
                    browsePlatform = browsePlatform,
                    isKugouLoggedIn = activeKugouAccount != null,
                    kugouPlatformEnabled = kugouPlatformEnabled
                )
                TitleBarButton.RECENT_PLAY -> state.navigate(DesktopRoute.RecentPlay)
            }
        },
        isMaximized = isMaximized,
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
                val keyword = searchInput.query.ifBlank { defaultKeyword }
                if (keyword.isNotBlank()) {
                    openSearch()
                    searchViewModel.searchWithKeyword(keyword)
                }
            }
        },
        isBrowseActive = currentRoute == DesktopRoute.Browse,
        // 已在浏览页再点即返回上一页，按钮作开关用
        onBrowseClick = {
            if (currentRoute == DesktopRoute.Browse && state.canGoBack) state.navigateBack() else state.navigate(DesktopRoute.Browse)
        },
        cloudMusic = deps.cloudMusic,
        privateUnreadCount = privateUnreadCount,
        onDismissUnread = deps.privateUnreadMonitor::markAllRead,
        downloadTasks = downloadTasks,
        accountEntries = accountEntries,
        addablePlatforms = addablePlatforms,
        onSelectAccount = { entry ->
            if (needsSwitchConfirm(entry, currentPlatform)) pendingSwitch = entry else performSwitch(entry)
        },
        onAddAccount = { platform ->
            val hasAccounts = allAccounts.any { it.platform == platform }
            if (platform == MusicPlatform.KUGOU) {
                state.kugouLoginAddAccount = hasAccounts
                state.showKugouLogin = true
            } else {
                state.loginAddAccount = hasAccounts
                state.showLogin = true
            }
        },
        onLogoutAccount = { accountId ->
            accountScope.launch {
                val target = accountEntries.firstOrNull { it.accountId == accountId }
                deps.accountSwitcher.logout(accountId)
                state.showMessage(if (target != null) "已退出 ${target.displayName}" else "已退出账户")
            }
        },
        canOpenProfile = currentPlatform == MusicPlatform.NETEASE && userProfile != null,
        canOpenRecent = currentAccountId != null,
        hideMessagesButton = !capabilities.supportsPrivateMessage,
        onProfileClick = { userProfile?.let { state.navigate(DesktopRoute.Profile(it.uid)) } },
        onRecentClick = { state.navigate(DesktopRoute.RecentPlay) },
        onCloudClick = state::openCloud,
        onSettingsClick = { state.navigate(DesktopRoute.Settings) },
        hasUpdate = availableUpdate != null,
        onMinimize = { windowState.isMinimized = true },
        onToggleMaximize = {
            windowState.placement = if (isMaximized) WindowPlacement.Floating else WindowPlacement.Maximized
        },
        onClose = onClose
    )

    pendingSwitch?.let { entry ->
        DesktopDialog(
            title = "切换到${entry.platform.displayName}",
            onDismiss = { pendingSwitch = null },
            width = 340.dp,
            actions = {
                DialogButton("取消", { pendingSwitch = null })
                DialogButton("切换", {
                    pendingSwitch = null
                    performSwitch(entry)
                }, primary = true)
            }
        ) {
            Text("切换后将清空当前播放队列", color = DesktopColors.TextGray, fontSize = 13.sp)
        }
    }
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
    val lanLinkEnabled by deps.lanLink.enabled.collectAsState()
    val connectedPeer by deps.lanLink.connectedDevice.collectAsState()
    val pairedDevices by deps.lanLink.pairedDevices.collectAsState()
    val activeTarget by deps.remoteCoordinator.activeTarget.collectAsState()
    val peerForSwitch = connectedPeer?.takeIf { lanLinkEnabled }
    val remoteDeviceName = (activeTarget as? PlaybackTarget.Remote)?.peer?.deviceName
    val isRemote = remoteDeviceName != null
    val volume = controller.volume.collectAsState().value.takeIf { audioOutput != null || isRemote }
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
        // 占位播放器且无任何已连接或已配对联动设备时没有设备面板入口
        onToggleDevices = if (audioOutput != null || peerForSwitch != null || pairedDevices.isNotEmpty()) {
            { state.toggleOverlay(DockOverlay.Devices) }
        } else null,
        outputDevice = if (isRemote) null else audioOutput?.let { collectSelectedAudioDevice(it) },
        isFullscreen = fullscreen.isFullscreen,
        onToggleFullscreen = fullscreen::toggle,
        lyricVisible = showDesktopLyric,
        onToggleLyric = { scope.launch { deps.settingsPreferences.saveShowDesktopLrc(!showDesktopLyric) } },
        lyricsViewOpen = lyricsView.isOpen,
        onToggleLyricsView = state::toggleLyricsView,
        onEnterMiniPlayer = onEnterMiniPlayer,
        remoteDeviceName = remoteDeviceName
    )
}
