package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.lin0721.linmusic.core.ui.theme.floatingOutline
import com.lin0721.linmusic.desktop.data.LibraryMode
import com.lin0721.linmusic.desktop.player.AudioOutputControl
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.home.LocalPaneResizing
import com.lin0721.linmusic.desktop.ui.library.LibraryPane
import com.lin0721.linmusic.desktop.ui.library.LibraryExtrasEntry
import com.lin0721.linmusic.desktop.ui.lyricsview.LyricsViewOverlay
import com.lin0721.linmusic.desktop.ui.navigation.DesktopFrameHost
import com.lin0721.linmusic.desktop.ui.nowplaying.DockOverlay
import com.lin0721.linmusic.desktop.ui.nowplaying.FloatingDockPanel
import com.lin0721.linmusic.desktop.ui.nowplaying.NowPlayingDock
import com.lin0721.linmusic.desktop.ui.nowplaying.rememberNowPlayingDockState
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens

private const val PAGE_ENTER_MS = 220
private const val PAGE_EXIT_MS = 120
private const val PAGE_ENTER_SCALE = 0.98f

// 新页淡入并从略小放大到原尺寸，旧页只淡出；两页同尺寸，不需要尺寸过渡
private val PageTransform = ContentTransform(
    targetContentEnter = fadeIn(tween(PAGE_ENTER_MS, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(PAGE_ENTER_MS, easing = FastOutSlowInEasing), initialScale = PAGE_ENTER_SCALE),
    initialContentExit = fadeOut(tween(PAGE_EXIT_MS)),
    sizeTransform = null
)

// 标题栏与播放栏之间的工作区：音乐库、页面内容、正在播放栏，以及盖在上面的全屏歌词
@Composable
internal fun Workspace(
    state: DesktopAppState,
    deps: DesktopAppDependencies,
    frameHost: DesktopFrameHost,
    audioOutput: AudioOutputControl?,
    hasNowPlaying: Boolean,
    isFullscreen: Boolean,
    modifier: Modifier
) {
    val pane = state.pane
    val lyricsView = state.lyricsView
    val dockOpen = state.dockOpen
    val dockOverlay = state.dockOverlay
    BoxWithConstraints(modifier) {
        val available = maxWidth - DesktopDimens.PaneGap * 2
        // 无曲目时下载面板与歌单评论也要能打开，右侧栏只承载它们
        val hasTrack = hasNowPlaying || dockOverlay == DockOverlay.Downloads || dockOverlay == DockOverlay.PlaylistComments
        val measure = pane.measure(available, hasTrack, dockOpen)
        val dockState = rememberNowPlayingDockState(hasTrack, dockOpen, measure.dockOpenWidth, pane.resizing)
        val workspace = rememberWorkspaceLayout(pane.libraryMode, available, dockState, measure.libraryDefaultWidth, pane.resizing)
        CompositionLocalProvider(
            LocalPaneWidthExtra provides { workspace.centerWidthExtra },
            LocalPaneResizing provides pane.resizing
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = DesktopDimens.PaneGap)) {
                LibraryPane(
                    mode = pane.libraryMode,
                    width = workspace.libraryWidth,
                    defaultWidth = measure.libraryDefaultWidth,
                    expandedWidth = workspace.expandedWidth,
                    viewModel = deps.libraryViewModel,
                    isLoggedIn = LocalDesktopNavigator.current.isLoggedIn,
                    onLoginClick = { state.showLogin = true },
                    onItemClick = state::openLibraryItem,
                    onModeChange = state::setLibraryMode,
                    viewMode = pane.libraryViewMode,
                    onViewModeChange = state::setLibraryViewMode,
                    onFilterToggle = state::toggleLibraryFilter,
                    onSortOrderChange = state::setLibrarySortOrder,
                    extras = LibraryExtrasEntry(
                        local = deps.localMusic,
                        cloud = deps.cloudMusic,
                        selected = state.libraryExtra,
                        onToggle = state::toggleLibraryExtra,
                        onOpenLocal = state::openLocalPlaylist,
                        onOpenCloud = state::openCloud
                    )
                )
                // 缝隙即拖动条：拖窄过阈值吸附成窄条，拖宽只停在上限，不会变成展开态
                PaneResizeHandle(
                    width = workspace.centerGap,
                    enabled = pane.libraryMode != LibraryMode.EXPANDED,
                    onDragStart = { pane.startLibraryDrag(workspace.libraryWidth) },
                    onDrag = { delta -> pane.dragLibrary(delta, measure.libraryMax, state::setLibraryModeByDrag) },
                    onDragEnd = pane::endLibraryDrag
                )
                Pane(Modifier.weight(1f)) {
                    val motion = LocalUiMotion.current
                    // 同一栈帧内的界面步骤不换页，按栈帧 id 判定是否过渡
                    AnimatedContent(
                        targetState = state.backStack.currentEntry,
                        modifier = Modifier.settledLayoutWidth(LocalPaneWidthExtra.current).fillMaxSize(),
                        transitionSpec = { motion.transform(PageTransform) },
                        contentKey = { it.id },
                        label = "page"
                    ) { entry ->
                        frameHost.Provide(entry) {
                            RouteContent(entry, frameHost, deps, state)
                        }
                    }
                }
                NowPlayingDock(
                    state = dockState,
                    hasTrack = hasTrack,
                    open = dockOpen,
                    // 全屏歌词开着时面板改由上层的浮层承载
                    overlay = if (lyricsView.isOpen) null else dockOverlay,
                    audioOutput = audioOutput,
                    downloader = deps.downloader,
                    onCloseOverlay = state::closeOverlay,
                    onOpenComments = { state.toggleOverlay(DockOverlay.Comments) },
                    playlistComments = state.playlistComments,
                    onOpenLyricsView = state::openLyricsView,
                    onOpenLyricsFullscreen = state::openLyricsFullscreen,
                    onOpenChange = state::setDockOpen,
                    controller = deps.playbackController,
                    playerViewModel = deps.playerViewModel,
                    songActions = deps.songActions,
                    onResizeStart = { pane.startDockDrag(dockState.width) },
                    onResize = { delta -> pane.dragDock(delta, measure.dockMax, dockOpen, state::closeDockByDrag) },
                    onResizeEnd = pane::endDockDrag
                )
            }
        }
        // 盖住整个工作区的全屏歌词，收起后原样露出下层
        LyricsViewOverlay(
            state = lyricsView,
            controller = deps.playbackController,
            playerViewModel = deps.playerViewModel,
            settingsPreferences = deps.settingsPreferences,
            desktopPreferences = deps.desktopPreferences,
            isFullscreen = isFullscreen,
            onClose = state::closeLyricsView
        )
        // 全屏歌词盖住了右侧栏，队列、设备等面板在它之上浮出
        if (lyricsView.isOpen && dockOpen && dockOverlay != null) {
            FloatingDockPanel(
                overlay = dockOverlay,
                controller = deps.playbackController,
                playerViewModel = deps.playerViewModel,
                songActions = deps.songActions,
                playlistComments = state.playlistComments,
                audioOutput = audioOutput,
                downloader = deps.downloader,
                onClose = state::closeOverlay,
                modifier = Modifier.align(Alignment.CenterEnd).padding(DesktopDimens.PaneGap)
                    .width(pane.dockWidthPref.coerceIn(DesktopDimens.NowPlayingMinWidth, DesktopDimens.NowPlayingMaxWidth))
                    .fillMaxHeight()
            )
        }
    }
}


@Composable
private fun Pane(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(DesktopDimens.PaneRadius)).background(DesktopColors.ShellFill)
            .floatingOutline(RoundedCornerShape(DesktopDimens.PaneRadius))
    ) {
        content()
    }
}
