package com.lin0721.linmusic.desktop.ui.lyrics

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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricFrames
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricStyle
import com.lin0721.linmusic.core.player.floatinglyric.LyricResize
import com.lin0721.linmusic.core.player.floatinglyric.LyricResizeHandle
import com.lin0721.linmusic.core.player.floatinglyric.LyricResizeLimits
import com.lin0721.linmusic.core.player.floatinglyric.LyricResizeState
import com.lin0721.linmusic.desktop.data.DesktopLyricBounds
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import com.lin0721.linmusic.desktop.platform.WindowEffects
import com.lin0721.linmusic.desktop.ui.components.LocalWindowEffects
import com.lin0721.linmusic.desktop.ui.lyricsview.rememberLyricClock
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.awt.Toolkit
import kotlin.math.roundToInt

private const val TAG = "DesktopLyric"
private const val BOUNDS_SAVE_DEBOUNCE_MS = 500L

// 首帧前的占位高度，内容测量后按实际高度收紧
private val InitialHeight = 140.dp

@OptIn(FlowPreview::class)
@Composable
fun DesktopLyricWindow(
    locked: Boolean,
    initialBounds: DesktopLyricBounds?,
    initialStyle: FloatingLyricStyle,
    playerViewModel: PlayerViewModel,
    controller: PlaybackController,
    desktopPreferences: DesktopPreferences,
    windowEffects: WindowEffects,
    persistScope: CoroutineScope,
    onLockedChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onHide: () -> Unit
) {
    val initialWidth = (initialBounds?.width ?: DesktopLyricBounds.DEFAULT_WIDTH).dp
    val windowState = rememberWindowState(
        size = DpSize(initialWidth, InitialHeight),
        position = remember { initialBounds?.let { WindowPosition(it.x.dp, it.y.dp) } ?: defaultPosition(initialWidth) }
    )
    // 关窗会取消防抖中的写盘，补写一次最终位置；窗口自身的协程此时已取消，借外层作用域
    DisposableEffect(windowState) {
        onDispose {
            boundsOf(windowState.position, windowState.size.width)?.let { bounds ->
                persistScope.launch { saveBounds(desktopPreferences, bounds) }
            }
        }
    }
    Window(
        onCloseRequest = onHide,
        state = windowState,
        title = "Melodia 桌面歌词",
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        focusable = false,
        resizable = false
    ) {
        // 锁定后鼠标事件穿透到下层窗口；等首帧后原生窗口才就绪
        LaunchedEffect(locked) {
            withFrameNanos { }
            windowEffects.setClickThrough(window, locked)
        }

        // 拖动、调宽停下后再写盘
        LaunchedEffect(windowState) {
            snapshotFlow { windowState.position to windowState.size.width }
                .drop(1)
                .debounce(BOUNDS_SAVE_DEBOUNCE_MS)
                .collect { (position, width) -> boundsOf(position, width)?.let { saveBounds(desktopPreferences, it) } }
        }

        val savedStyle by desktopPreferences.desktopLyricStyle.collectAsState(initialStyle)
        // 右下角缩放过程中先用预览字号，松手写盘后等存储值追上再清掉，避免回跳一帧
        var previewFontSize by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(savedStyle.fontSizeSp) { if (previewFontSize == savedStyle.fontSizeSp) previewFontSize = null }
        val style = previewFontSize?.let { savedStyle.copy(fontSizeSp = it) } ?: savedStyle
        val scope = rememberCoroutineScope()
        val resize = remember(windowState) {
            var start: LyricResizeState? = null
            DesktopLyricResizeCallbacks(
                onStart = {
                    start = LyricResizeState(windowState.position.x.value, windowState.size.width.value, previewFontSize ?: savedStyle.fontSizeSp)
                },
                onDrag = { handle, dx ->
                    start?.let { from ->
                        val next = LyricResize.apply(handle, from, dx.value, ResizeLimits)
                        windowState.position = WindowPosition(next.x.dp, windowState.position.y)
                        windowState.size = DpSize(next.width.dp, windowState.size.height)
                        if (handle == LyricResizeHandle.CORNER) previewFontSize = next.fontSizeSp
                    }
                },
                onEnd = { handle ->
                    start = null
                    val fontSize = previewFontSize
                    if (handle == LyricResizeHandle.CORNER && fontSize != null) {
                        scope.launch { saveFontSize(desktopPreferences, fontSize) }
                    }
                }
            )
        }
        val nowPlaying by controller.nowPlaying.collectAsState()
        val isPlaying by controller.isPlaying.collectAsState()
        val detailState by playerViewModel.songDetailState.collectAsState()
        val currentIndex by playerViewModel.currentLyricIndex.collectAsState()
        val frame = remember(detailState.lyrics, currentIndex, nowPlaying, style.secondary) {
            FloatingLyricFrames.of(detailState.lyrics, currentIndex, nowPlaying, style.secondary)
        }
        val clock = rememberLyricClock(controller)
        val actions = remember(controller, locked) {
            DesktopLyricActions(
                onPrevious = controller::skipToPrevious,
                onTogglePlay = controller::togglePlayPause,
                onNext = controller::playNext,
                onToggleLock = { onLockedChange(!locked) },
                onOpenSettings = onOpenSettings,
                onClose = onHide
            )
        }

        CompositionLocalProvider(LocalWindowEffects provides windowEffects) {
            DesktopLyricContent(
                frame = frame,
                style = style,
                locked = locked,
                isPlaying = isPlaying,
                positionProvider = clock,
                actions = actions,
                resize = resize,
                onHeightMeasured = { height -> resizeHeight(windowState.size, height)?.let { windowState.size = it } }
            )
        }
    }
}

private val ResizeLimits = LyricResizeLimits(
    minWidth = DesktopLyricBounds.MIN_WIDTH.toFloat(),
    maxWidth = DesktopLyricBounds.MAX_WIDTH.toFloat(),
    minFontSp = 16,
    maxFontSp = 72
)

private fun boundsOf(position: WindowPosition, width: Dp): DesktopLyricBounds? =
    if (position.isSpecified) {
        DesktopLyricBounds(position.x.value.roundToInt(), position.y.value.roundToInt(), width.value.roundToInt())
    } else {
        null
    }

private suspend fun saveBounds(preferences: DesktopPreferences, bounds: DesktopLyricBounds) {
    try {
        preferences.saveDesktopLyricBounds(bounds)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.w(TAG, "保存桌面歌词位置失败", e)
    }
}

private suspend fun saveFontSize(preferences: DesktopPreferences, fontSize: Int) {
    try {
        preferences.saveDesktopLyricStyle(preferences.desktopLyricStyle.first().copy(fontSizeSp = fontSize))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.w(TAG, "保存桌面歌词字号失败", e)
    }
}

private fun resizeHeight(current: DpSize, measured: Dp): DpSize? =
    if (measured > 0.dp && measured != current.height) DpSize(current.width, measured) else null

// 默认放在屏幕下方约五分之四高度处，水平居中
private fun defaultPosition(width: Dp): WindowPosition {
    val screen = Toolkit.getDefaultToolkit().screenSize
    return WindowPosition(
        ((screen.width - width.value) / 2).dp,
        (screen.height * 0.8f - InitialHeight.value).dp
    )
}
