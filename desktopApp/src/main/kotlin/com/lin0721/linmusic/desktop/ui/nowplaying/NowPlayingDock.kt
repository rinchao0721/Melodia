package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.ui.theme.paneBackdrop
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.data.download.DesktopSongDownloader
import com.lin0721.linmusic.desktop.player.AudioOutputControl
import com.lin0721.linmusic.desktop.ui.comments.CommentsHost
import com.lin0721.linmusic.desktop.ui.comments.CommentsPanel
import com.lin0721.linmusic.desktop.ui.comments.asCommentsHost
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.TooltipSide
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.download.DownloadsPanel
import com.lin0721.linmusic.desktop.ui.player.AudioDevicePanel
import com.lin0721.linmusic.desktop.ui.player.PlayQueuePanel
import com.lin0721.linmusic.desktop.ui.shell.PANE_ANIMATION_MS
import com.lin0721.linmusic.desktop.ui.shell.PaneResizeHandle
import com.lin0721.linmusic.desktop.ui.shell.SongActionSources
import com.lin0721.linmusic.desktop.ui.shell.fixedWidthAtStart
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.delay

private const val PEEK_DELAY_MS = 120L
private const val OVERLAY_FADE_MS = 150

// 覆盖在“正在播放”之上的面板，关闭后露出下层的正在播放页
enum class DockOverlay {
    Queue,
    Devices,
    Comments,
    PlaylistComments,
    Downloads
}

// 侧栏宽度状态：width 随动画变化；稳定宽度不含悬停预览，内容区据此排版
@Stable
class NowPlayingDockState internal constructor(
    private val animatedWidth: State<Dp>,
    private val settledWidth: Dp,
    // 展开时的面板宽度，随拖动调整
    val openWidth: Dp,
    internal val peeking: MutableState<Boolean>
) {
    val width: Dp get() = animatedWidth.value

    // 侧栏占用宽度（含间距）与稳定值之差：内容区可见宽度加上它，就是按稳定宽度排版时的宽度
    val widthExtra: Dp get() = occupied(width) - occupied(settledWidth)

    // 间距随宽度从 0 长出，收到 0 后不残留空隙
    internal fun gap(width: Dp): Dp =
        DesktopDimens.PaneGap * (width / DesktopDimens.NowPlayingHandleWidth).coerceIn(0f, 1f)

    // 稳定宽度下侧栏占用的总宽度（含间距）
    val settledOccupied: Dp get() = occupied(settledWidth)

    private fun occupied(width: Dp): Dp = width + gap(width)
}

@Composable
fun rememberNowPlayingDockState(hasTrack: Boolean, open: Boolean, openWidth: Dp, resizing: Boolean): NowPlayingDockState {
    val peeking = remember { mutableStateOf(false) }
    val settled = when {
        !hasTrack -> 0.dp
        open -> openWidth
        else -> DesktopDimens.NowPlayingHandleWidth
    }
    val target = if (peeking.value && hasTrack && !open) DesktopDimens.NowPlayingPeekWidth else settled
    // 拖动调宽时跟手，不走过渡动画
    val spec = if (resizing) snap<Dp>() else uiTween<Dp>(PANE_ANIMATION_MS, easing = FastOutSlowInEasing)
    val width = animateDpAsState(target, spec, label = "nowPlayingDock")
    return NowPlayingDockState(width, settled, openWidth, peeking)
}

// 独立成函数以脱离外层 RowScope，否则 AnimatedVisibility 会解析到被 DSL 作用域屏蔽的扩展版本；
// 不透明底并吞掉点击，避免操作穿透到下层的正在播放页
@Composable
private fun OverlayLayer(visible: Boolean, content: @Composable (Modifier) -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(uiTween(OVERLAY_FADE_MS)),
        exit = fadeOut(uiTween(OVERLAY_FADE_MS))
    ) {
        content(
            Modifier.paneBackdrop(DesktopColors.Pane).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
        )
    }
}

// 右侧“正在播放”栏：无曲目时不存在，关闭后收成右边缘的窄条，悬停预览、点击展开。
// 三种宽度由同一个元素过渡，面板内容始终按完整宽度排版并被裁剪
@Composable
fun NowPlayingDock(
    state: NowPlayingDockState,
    hasTrack: Boolean,
    open: Boolean,
    overlay: DockOverlay?,
    audioOutput: AudioOutputControl?,
    downloader: DesktopSongDownloader,
    onCloseOverlay: () -> Unit,
    onOpenComments: () -> Unit,
    playlistComments: CommentsHost?,
    onOpenLyricsView: () -> Unit,
    onOpenLyricsFullscreen: () -> Unit,
    onOpenChange: (Boolean) -> Unit,
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    songActions: SongActionSources,
    onResizeStart: () -> Unit,
    onResize: (delta: Dp) -> Unit,
    onResizeEnd: () -> Unit
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val collapsed = hasTrack && !open
    val playerCommentsHost = remember(playerViewModel) { playerViewModel.asCommentsHost() }

    // 停留一小段时间才预览，避免鼠标掠过窗口边缘时误触
    LaunchedEffect(hovered, collapsed) {
        if (hovered && collapsed) {
            delay(PEEK_DELAY_MS)
            state.peeking.value = true
        } else {
            state.peeking.value = false
        }
    }

    val handleWidth = DesktopDimens.NowPlayingHandleWidth
    val peekWidth = DesktopDimens.NowPlayingPeekWidth
    val width = state.width
    val contentAlpha = ((width - handleWidth) / (peekWidth - handleWidth)).coerceIn(0f, 1f)

    Row(Modifier.fillMaxHeight()) {
        // 展开态下缝隙即拖动条，向左拖变宽
        PaneResizeHandle(
            width = state.gap(width),
            enabled = hasTrack && open,
            onDragStart = onResizeStart,
            onDrag = onResize,
            onDragEnd = onResizeEnd
        )
        if (width > 0.dp) {
            Box(
                Modifier.width(width).fillMaxHeight()
                    .clip(RoundedCornerShape(DesktopDimens.PaneRadius))
                    .background(DesktopColors.ShellFill)
                    .hoverable(hoverSource)
                    .then(
                        if (collapsed) {
                            Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onOpenChange(true) }
                        } else {
                            Modifier
                        }
                    )
            ) {
                // 面板始终保持组合，收起时移出可视区；展开时无需现场创建内容，避免动画起头卡顿
                Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }
                        .fixedWidthAtStart(state.openWidth, offscreen = contentAlpha == 0f)
                ) {
                    NowPlayingPanel(
                        controller = controller,
                        playerViewModel = playerViewModel,
                        hovered = hovered && !collapsed,
                        onClose = { onOpenChange(false) },
                        onOpenComments = onOpenComments,
                        onOpenLyricsView = onOpenLyricsView,
                        onOpenLyricsFullscreen = onOpenLyricsFullscreen
                    )
                    OverlayLayer(visible = open && overlay == DockOverlay.Comments) { modifier ->
                        CommentsPanel(host = playerCommentsHost, onClose = onCloseOverlay, modifier = modifier)
                    }
                    if (playlistComments != null) {
                        OverlayLayer(visible = open && overlay == DockOverlay.PlaylistComments) { modifier ->
                            CommentsPanel(host = playlistComments, onClose = onCloseOverlay, modifier = modifier)
                        }
                    }
                    OverlayLayer(visible = open && overlay == DockOverlay.Queue) { modifier ->
                        PlayQueuePanel(controller = controller, songActions = songActions, onClose = onCloseOverlay, modifier = modifier)
                    }
                    if (audioOutput != null) {
                        OverlayLayer(visible = open && overlay == DockOverlay.Devices) { modifier ->
                            AudioDevicePanel(control = audioOutput, onClose = onCloseOverlay, modifier = modifier)
                        }
                    }
                    OverlayLayer(visible = open && overlay == DockOverlay.Downloads) { modifier ->
                        DownloadsPanel(downloader = downloader, onClose = onCloseOverlay, modifier = modifier)
                    }
                }
                if (collapsed) {
                    // 收起与预览态下面板内容不响应点击，整块都是展开入口
                    Box(
                        Modifier.fillMaxSize().clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onOpenChange(true) }
                    )
                }
                if (contentAlpha < 1f) {
                    DesktopTooltip(
                        "显示“正在播放”",
                        modifier = Modifier.width(handleWidth).fillMaxHeight(),
                        side = TooltipSide.Left
                    ) {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer { alpha = 1f - contentAlpha },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.ChevronLeft,
                                null,
                                tint = if (hovered) DesktopColors.TextPrimary else DesktopColors.TextGray
                            )
                        }
                    }
                }
            }
        }
    }
}

// 全屏歌词之上浮出的面板：与右侧栏里的覆盖面板内容相同，各自独立组合
@Composable
fun FloatingDockPanel(
    overlay: DockOverlay,
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    songActions: SongActionSources,
    playlistComments: CommentsHost?,
    audioOutput: AudioOutputControl?,
    downloader: DesktopSongDownloader,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(DesktopDimens.PaneRadius)
    val playerCommentsHost = remember(playerViewModel) { playerViewModel.asCommentsHost() }
    Box(modifier.themedShadow(16.dp, shape).clip(shape).paneBackdrop(DesktopColors.Pane)) {
        when (overlay) {
            DockOverlay.Comments -> CommentsPanel(host = playerCommentsHost, onClose = onClose)
            DockOverlay.PlaylistComments -> playlistComments?.let { CommentsPanel(host = it, onClose = onClose) }
            DockOverlay.Queue -> PlayQueuePanel(controller = controller, songActions = songActions, onClose = onClose)
            DockOverlay.Devices -> audioOutput?.let { AudioDevicePanel(control = it, onClose = onClose) }
            DockOverlay.Downloads -> DownloadsPanel(downloader = downloader, onClose = onClose)
        }
    }
}
