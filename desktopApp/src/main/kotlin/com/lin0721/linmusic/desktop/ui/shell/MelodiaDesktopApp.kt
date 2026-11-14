package com.lin0721.linmusic.desktop.ui.shell

import com.lin0721.linmusic.desktop.ui.listentogether.LocalListenTogetherViewModel
import com.lin0721.linmusic.desktop.ui.plugin.DesktopPluginPageUi
import com.lin0721.linmusic.core.plugin.PluginCardContext
import com.lin0721.linmusic.core.plugin.LocalPluginCards
import com.lin0721.linmusic.core.plugin.LocalPluginCommands
import com.lin0721.linmusic.core.songquality.LocalSongQualityCache
import com.lin0721.linmusic.desktop.ui.plugin.PluginConfirmDialog
import com.lin0721.linmusic.core.plugin.LocalPlayerLayouts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.lin0721.linmusic.core.deeplink.ClipboardLink
import com.lin0721.linmusic.core.deeplink.LinkPreviewState
import com.lin0721.linmusic.core.deeplink.NeteaseLink
import com.lin0721.linmusic.core.deeplink.SongLinkTarget
import com.lin0721.linmusic.core.deeplink.enqueueLinkTrackNext
import com.lin0721.linmusic.core.deeplink.playLinkTrack
import com.lin0721.linmusic.core.ui.theme.skinBackdropRoot
import com.lin0721.linmusic.desktop.player.AudioOutputControl
import com.lin0721.linmusic.desktop.ui.components.readTextFromClipboard
import com.lin0721.linmusic.desktop.ui.components.registerClipboardCopyObserver
import com.lin0721.linmusic.desktop.ui.components.uiEnter
import com.lin0721.linmusic.desktop.ui.components.uiExit
import com.lin0721.linmusic.desktop.ui.deeplink.ClipboardLinkBanner
import com.lin0721.linmusic.desktop.ui.login.LoginDialog
import com.lin0721.linmusic.desktop.ui.lyricsview.CHROME_ANIM_MS
import com.lin0721.linmusic.desktop.ui.lyricsview.LyricsViewState
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.recognition.RecognitionDialog
import com.lin0721.linmusic.desktop.ui.search.SearchDropdown
import com.lin0721.linmusic.desktop.ui.share.ShareCardDialog
import com.lin0721.linmusic.desktop.ui.search.SearchDropdownState
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.update.UpdateDialog
import com.lin0721.linmusic.desktop.ui.update.openInBrowser
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ChromeEnter = fadeIn(tween(CHROME_ANIM_MS)) + expandVertically(tween(CHROME_ANIM_MS))
private val ChromeExit = fadeOut(tween(CHROME_ANIM_MS)) + shrinkVertically(tween(CHROME_ANIM_MS))

// AWT 把鼠标侧键报告为第 4、5 键
private const val MOUSE_BUTTON_BACK = 4
private const val MOUSE_BUTTON_FORWARD = 5

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowScope.MelodiaDesktopApp(
    deps: DesktopAppDependencies,
    session: DesktopShellSession,
    windowState: WindowState,
    fullscreen: FullscreenState,
    lyricsView: LyricsViewState,
    searchFocusRequests: Flow<Unit>,
    settingsRequests: Flow<String>,
    loginRequests: Flow<Unit>,
    onEnterMiniPlayer: () -> Unit,
    onClose: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val controller = deps.playbackController
    // 占位播放器没有音量与输出设备能力，界面隐藏对应入口
    val audioOutput: AudioOutputControl? = controller.takeIf { it.unavailableReason == null }
    val state = session.state

    val userProfile by deps.homeViewModel.userProfile.collectAsState()
    val downloadLevel by deps.settingsPreferences.wifiQuality.collectAsState(initial = "standard")
    val defaultPlaylistPrivate by deps.settingsPreferences.defaultPlaylistPrivate.collectAsState(initial = false)
    val nowPlaying by controller.nowPlaying.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val searchDropdown = remember { SearchDropdownState() }

    // 没有曲目时歌词界面无内容可看，自动收起
    LaunchedEffect(nowPlaying == null) { if (nowPlaying == null) lyricsView.close() }
    // 不经记录入口的界面变化（Esc 收起歌词、拖拽改排序等）并入当前步骤，后退时不会被还原回去
    LaunchedEffect(state) {
        combine(
            snapshotFlow { state.liveUi() },
            deps.libraryViewModel.selectedFilter,
            deps.libraryViewModel.sortOrder
        ) { _, _, _ -> }.collect { state.absorbUiChange() }
    }
    LaunchedEffect(state) { deps.desktopPreferences.nowPlayingPanelOpen.collect(state::syncSavedDockOpen) }
    // 登录失效时本地登录态已被清掉，这里提示并直接打开登录框
    LaunchedEffect(state) {
        deps.sessionMonitor.watch {
            state.showMessage("登录已过期，请重新登录")
            state.showLogin = true
        }
    }
    // 播放组件加载失败时启动就提示一次，之后每次尝试播放再提示
    LaunchedEffect(Unit) { controller.unavailableReason?.let { snackbarHostState.showSnackbar(it) } }
    // 桌面歌词控制栏的设置按钮：收起全屏歌词后打开设置页并定位分区
    LaunchedEffect(state) {
        settingsRequests.collect { category ->
            lyricsView.close()
            state.settingsFocus = category
            state.navigate(DesktopRoute.Settings)
        }
    }
    // 迷你播放器里未登录点喜欢：回到主窗口后提示并打开登录框
    LaunchedEffect(state) {
        loginRequests.collect {
            state.showMessage("请先登录账号")
            state.showLogin = true
        }
    }
    LaunchedEffect(state) { appMessages(deps, state).collect { snackbarHostState.showSnackbar(it) } }

    val navigator = desktopNavigator(state, deps, userProfile != null, downloadLevel, defaultPlaylistPrivate)
    val frameHost = session.frameHost
    val liveEntryIds = state.backStack.liveEntryIds
    LaunchedEffect(liveEntryIds) { frameHost.reconcile() }

    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val isFullscreen = fullscreen.isFullscreen

    val pluginLayouts by deps.plugins.layouts.collectAsState()
    val pluginCards = remember(deps) { PluginCardContext(deps.playerCards, deps.pluginPages, DesktopPluginPageUi) }
    CompositionLocalProvider(
        LocalDesktopNavigator provides navigator,
        LocalPlayerLayouts provides pluginLayouts.specs,
        LocalPluginCards provides pluginCards,
        LocalPluginCommands provides deps.pluginCommands,
        LocalSongQualityCache provides deps.songQuality,
        LocalListenTogetherViewModel provides deps.listenTogether
    ) {
        val scope = rememberCoroutineScope()
        var clipboardCheckJob by remember { mutableStateOf<Job?>(null) }
        fun checkClipboardOnFocus() {
            clipboardCheckJob?.cancel()
            clipboardCheckJob = scope.launch {
                if (!deps.settingsPreferences.clipboardLinkEnabled.first()) return@launch
                val text = withContext(Dispatchers.IO) { readTextFromClipboard() }
                if (text.isBlank()) return@launch
                if (deps.songLinkRequests.isRecentlyHandled(text)) return@launch
                val lastHandled = deps.settingsPreferences.lastHandledClipboardLink.first()
                if (text == lastHandled) return@launch
                val direct = NeteaseLink.target(text)
                if (direct != null) {
                    deps.settingsPreferences.setLastHandledClipboardLink(text)
                    deps.songLinkRequests.offerClipboard(ClipboardLink(direct, text))
                    return@launch
                }
                val shortLink = NeteaseLink.shortLink(text) ?: return@launch
                val resolved = NeteaseLink.resolve(shortLink) ?: return@launch
                deps.settingsPreferences.setLastHandledClipboardLink(text)
                deps.songLinkRequests.offerClipboard(ClipboardLink(resolved, text))
            }
        }
        DisposableEffect(deps) {
            registerClipboardCopyObserver { text ->
                if (NeteaseLink.containsRecognizableLink(text)) {
                    deps.songLinkRequests.markSelfHandled(text)
                    scope.launch { deps.settingsPreferences.setLastHandledClipboardLink(text) }
                }
            }
            onDispose { registerClipboardCopyObserver(null) }
        }
        DisposableEffect(window, deps) {
            val listener = object : WindowAdapter() {
                override fun windowGainedFocus(e: WindowEvent?) {
                    checkClipboardOnFocus()
                }
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }
        LaunchedEffect(deps) { checkClipboardOnFocus() }

        LaunchedEffect(deps) {
            deps.songLinkRequests.pendingTarget.filterNotNull().collect { target ->
                deps.songLinkRequests.consume()
                when (target) {
                    is SongLinkTarget.Playlist -> {
                        lyricsView.close()
                        if (target.isAlbum) navigator.openAlbum(target.id, "") else navigator.openPlaylist(target.id, "")
                    }
                    is SongLinkTarget.Artist -> {
                        lyricsView.close()
                        navigator.openArtist(target.id, "")
                    }
                    is SongLinkTarget.Song -> {
                        val track = deps.playbackRepository.getSongDetail(target.id).firstOrNull()?.getOrNull()
                        if (track == null) {
                            state.showMessage("这首歌打不开，可能已下架")
                            return@collect
                        }
                        controller.playLinkTrack(track, "链接")
                    }
                }
            }
        }
        val clipboardLink by deps.songLinkRequests.pendingClipboard.collectAsState()
        var clipboardPreviewState by remember { mutableStateOf<LinkPreviewState>(LinkPreviewState.Loading) }
        LaunchedEffect(clipboardLink) {
            val current = clipboardLink ?: return@LaunchedEffect
            clipboardPreviewState = LinkPreviewState.Loading
            val preview = deps.linkPreviewResolver.resolve(current.target)
            clipboardPreviewState = if (preview != null) LinkPreviewState.Ready(preview) else LinkPreviewState.Failed
        }

        LaunchedEffect(deps) { deps.pluginCommands.notices.messages.collect { navigator.showMessage(it) } }
        LaunchedEffect(deps) {
            deps.pluginCommands.content.opens.collect { state.navigate(DesktopRoute.Playlist(it.id, it.title)) }
        }
        LaunchedEffect(deps) {
            deps.pluginCommands.links.opens.collect { if (!openInBrowser(it)) navigator.showMessage("无法打开浏览器") }
        }
        PluginConfirmDialog(deps.pluginCommands.confirmations)
        Box(
            Modifier.fillMaxSize().skinBackdropRoot(DesktopColors.WindowBackground)
                .onPointerEvent(PointerEventType.Enter) { lyricsView.pointerInWindow = true }
                .onPointerEvent(PointerEventType.Exit) { lyricsView.pointerInWindow = false }
                // 点到搜索下拉之外就收起它；鼠标侧键与浏览器一致：后退键返回，前进键前进；不消费事件，子组件照常响应
                .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { event ->
                    event.changes.firstOrNull()?.position?.let(searchDropdown::onWindowPress)
                    when (event.awtEventOrNull?.button) {
                        MOUSE_BUTTON_BACK -> state.navigateBack()
                        MOUSE_BUTTON_FORWARD -> state.navigateForward()
                    }
                }
                // Esc 先让输入框放弃焦点，窗口级快捷键随即恢复；同时收起剪贴板浮动条
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        searchDropdown.close()
                        focusManager.clearFocus()
                        deps.songLinkRequests.dismissClipboard()
                    }
                    false
                }
        ) {
            Column(Modifier.fillMaxSize()) {
                // 全屏或歌词沉浸态时收起自绘标题栏，Esc 或底栏按钮退出
                AnimatedVisibility(
                    visible = !isFullscreen && !lyricsView.isImmersive,
                    enter = uiEnter(ChromeEnter),
                    exit = uiExit(ChromeExit)
                ) {
                    AppTitleBar(state, deps, windowState, userProfile, searchFocusRequests, searchDropdown, onClose)
                }
                Workspace(state, deps, frameHost, audioOutput, hasNowPlaying = nowPlaying != null, isFullscreen, Modifier.weight(1f))
                AnimatedVisibility(
                    visible = !lyricsView.isImmersive,
                    enter = uiEnter(ChromeEnter),
                    exit = uiExit(ChromeExit)
                ) {
                    AppPlayerBar(state, deps, audioOutput, fullscreen, onEnterMiniPlayer)
                }
            }
            SearchDropdown(searchDropdown, deps.searchViewModel) { keyword ->
                state.navigate(DesktopRoute.Search)
                deps.searchViewModel.searchWithKeyword(keyword)
            }
            ClipboardLinkBanner(
                link = clipboardLink,
                previewState = clipboardPreviewState,
                onPrimaryAction = {
                    val current = deps.songLinkRequests.takeClipboard() ?: return@ClipboardLinkBanner
                    val readyPreview = (clipboardPreviewState as? LinkPreviewState.Ready)?.preview
                    when (val target = current.target) {
                        is SongLinkTarget.Song -> {
                            val readyTrack = readyPreview?.track
                            if (readyTrack != null) {
                                controller.playLinkTrack(readyTrack, "链接")
                            } else {
                                deps.songLinkRequests.request(target)
                            }
                        }
                        is SongLinkTarget.Playlist -> {
                            lyricsView.close()
                            val title = readyPreview?.title.orEmpty()
                            if (target.isAlbum) navigator.openAlbum(target.id, title) else navigator.openPlaylist(target.id, title)
                        }
                        is SongLinkTarget.Artist -> {
                            lyricsView.close()
                            navigator.openArtist(target.id, readyPreview?.title.orEmpty())
                        }
                    }
                },
                onEnqueueNext = {
                    val current = deps.songLinkRequests.takeClipboard() ?: return@ClipboardLinkBanner
                    val songTarget = current.target as? SongLinkTarget.Song ?: return@ClipboardLinkBanner
                    val readyTrack = (clipboardPreviewState as? LinkPreviewState.Ready)?.preview?.track
                    if (readyTrack != null) {
                        controller.enqueueLinkTrackNext(readyTrack)
                        state.showMessage("已添加至下一首播放")
                    } else {
                        scope.launch {
                            val track = deps.playbackRepository.getSongDetail(songTarget.id).firstOrNull()?.getOrNull()
                            if (track != null) {
                                controller.enqueueLinkTrackNext(track)
                                state.showMessage("已添加至下一首播放")
                            } else {
                                state.showMessage("这首歌打不开，可能已下架")
                            }
                        }
                    }
                },
                onDismiss = { deps.songLinkRequests.dismissClipboard() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
            )
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) { data ->
                Snackbar(data, containerColor = DesktopColors.PopupSurface, contentColor = DesktopColors.TextPrimary)
            }
            WindowResizeHandles(enabled = !isMaximized && !isFullscreen)
        }
        // 弹窗内的歌曲菜单依赖导航器，须放在 Provider 内
        if (state.showRecognition) {
            RecognitionDialog(
                recognition = deps.recognition,
                controller = deps.playbackController,
                songActions = deps.songActions,
                onDismiss = { state.showRecognition = false }
            )
        }
        state.shareCardKind?.let { kind ->
            ShareCardDialog(
                initialKind = kind,
                controller = deps.playbackController,
                playerViewModel = deps.playerViewModel,
                onDismiss = { state.shareCardKind = null }
            )
        }
    }

    val availableUpdate by deps.updateChecker.availableUpdate.collectAsState()
    val isUpdateDialogVisible by deps.updateChecker.isDialogVisible.collectAsState()
    availableUpdate?.takeIf { isUpdateDialogVisible }?.let { info ->
        UpdateDialog(
            info = info,
            onDismiss = deps.updateChecker::dismiss,
            onHide = deps.updateChecker::hideDialog,
            onIgnore = deps.updateChecker::ignoreCurrentVersion,
            onMessage = state::showMessage
        )
    }

    if (state.showLogin) {
        LoginDialog(
            viewModel = deps.loginViewModel,
            browserLocator = deps.browserLocator,
            onLoginSuccess = { cookies ->
                deps.homeViewModel.handleLoginSuccess(cookies)
                state.showLogin = false
            },
            onDismiss = { state.showLogin = false }
        )
    }
}
