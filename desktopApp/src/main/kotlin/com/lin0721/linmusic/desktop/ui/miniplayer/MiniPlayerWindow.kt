package com.lin0721.linmusic.desktop.ui.miniplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.ui.skin.Skin
import com.lin0721.linmusic.core.ui.theme.MelodiaPalette
import com.lin0721.linmusic.desktop.app.WindowBounds
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import com.lin0721.linmusic.desktop.data.MiniLyricSettings
import com.lin0721.linmusic.desktop.data.MiniPlayerPosition
import com.lin0721.linmusic.desktop.data.MiniPlayerSize
import com.lin0721.linmusic.desktop.data.MiniPlayerStyle
import com.lin0721.linmusic.desktop.platform.WindowEffects
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.components.LocalWindowEffects
import com.lin0721.linmusic.desktop.ui.components.UiMotion
import com.lin0721.linmusic.desktop.ui.theme.MelodiaDesktopTheme
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Toolkit
import kotlin.math.roundToInt

private const val TAG = "MiniPlayer"
private const val POSITION_SAVE_DEBOUNCE_MS = 500L
private const val SCREEN_MARGIN = 24

// 打开迷你窗时读取的已存状态，首帧即按它绘制
data class MiniPlayerInit(
    val position: MiniPlayerPosition?,
    val style: MiniPlayerStyle,
    val sizes: Map<MiniPlayerStyle, MiniPlayerSize>,
    val alwaysOnTop: Boolean,
    val lyricSettings: MiniLyricSettings
)

@OptIn(FlowPreview::class)
@Composable
fun MiniPlayerWindow(
    init: MiniPlayerInit,
    uiScale: Float,
    palette: MelodiaPalette,
    skin: Skin?,
    uiMotion: UiMotion,
    windowEffects: WindowEffects,
    icon: Painter,
    isLoggedIn: Boolean,
    playerViewModel: PlayerViewModel,
    controller: PlaybackController,
    desktopPreferences: DesktopPreferences,
    persistScope: CoroutineScope,
    onRestore: () -> Unit,
    onOpenLyricSettings: () -> Unit,
    onRequireLogin: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val style by desktopPreferences.miniPlayerStyle.collectAsState(init.style)
    val alwaysOnTop by desktopPreferences.miniPlayerAlwaysOnTop.collectAsState(init.alwaysOnTop)
    val lyricSettings by desktopPreferences.miniLyricSettings.collectAsState(init.lyricSettings)
    val savedSizes by desktopPreferences.miniPlayerSizes.collectAsState(init.sizes)
    val savedSize = savedSizes[style] ?: style.defaultSize
    // 拖动中用临时尺寸，松手写盘后等存储值追上再清掉，避免回跳一帧
    var dragSize by remember { mutableStateOf<MiniPlayerSize?>(null) }
    var resizing by remember { mutableStateOf(false) }
    LaunchedEffect(style) { dragSize = null }
    LaunchedEffect(savedSize) { if (!resizing && dragSize == savedSize) dragSize = null }
    val layoutSize = dragSize ?: savedSize
    val size = windowSizeOf(layoutSize, uiScale)
    val windowState = rememberWindowState(
        size = size,
        position = remember {
            init.position?.let { WindowPosition(it.x.dp, it.y.dp) } ?: defaultPosition(size)
        }
    )
    // 关窗会取消防抖中的写盘，补写一次最终位置；窗口自身的协程此时已取消，借外层作用域
    DisposableEffect(windowState) {
        onDispose {
            positionOf(windowState.position)?.let { position ->
                persistScope.launch { savePosition(desktopPreferences, position) }
            }
        }
    }

    Window(
        onCloseRequest = onRestore,
        state = windowState,
        title = "Melodia 迷你播放器",
        icon = icon,
        undecorated = true,
        transparent = true,
        alwaysOnTop = alwaysOnTop,
        resizable = false
    ) {
        // 换样式只改大小、左上角不动；新尺寸放不下时收回工作区内。拖动中由拖动回调直接设置
        LaunchedEffect(size) {
            if (resizing) return@LaunchedEffect
            windowState.size = size
            val position = windowState.position
            if (!position.isSpecified) return@LaunchedEffect
            val area = window.graphicsConfiguration?.let(::workArea) ?: return@LaunchedEffect
            val fitted = MiniPlayerPosition(position.x.value.roundToInt(), position.y.value.roundToInt())
                .fitInto(size.width.value.roundToInt(), size.height.value.roundToInt(), area)
            windowState.position = WindowPosition(fitted.x.dp, fitted.y.dp)
        }
        LaunchedEffect(windowState) {
            snapshotFlow { windowState.position }
                .drop(1)
                .debounce(POSITION_SAVE_DEBOUNCE_MS)
                .collect { position -> positionOf(position)?.let { savePosition(desktopPreferences, it) } }
        }

        val actions = remember(controller, playerViewModel, isLoggedIn, style) {
            MiniPlayerActions(
                onPrevious = controller::skipToPrevious,
                onTogglePlay = controller::togglePlayPause,
                onNext = controller::playNext,
                onToggleRepeat = controller::toggleRepeat,
                onToggleLike = { if (isLoggedIn) playerViewModel.toggleLike() else onRequireLogin() },
                onSeek = controller::seekTo,
                onSwitchStyle = {
                    scope.launch {
                        try {
                            desktopPreferences.saveMiniPlayerStyle(style.next())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "保存迷你播放器样式失败", e)
                        }
                    }
                },
                onOpenLyricSettings = onOpenLyricSettings,
                onRestore = onRestore
            )
        }
        val currentLayoutSize by rememberUpdatedState(layoutSize)
        val currentSavedSize by rememberUpdatedState(savedSize)
        val resize = remember(windowState, style, uiScale) {
            var start: MiniResizeState? = null
            MiniResizeCallbacks(
                onStart = {
                    val position = windowState.position
                    if (position.isSpecified) {
                        resizing = true
                        start = MiniResizeState(position.x.value, position.y.value, currentLayoutSize)
                    }
                },
                onDrag = { handle, dx, dy ->
                    start?.let { from ->
                        val next = MiniResize.apply(style, handle, from, dx, dy, uiScale)
                        dragSize = next.size
                        windowState.position = WindowPosition(next.x.dp, next.y.dp)
                        windowState.size = windowSizeOf(next.size, uiScale)
                    }
                },
                onEnd = {
                    start = null
                    resizing = false
                    val finalSize = dragSize
                    if (finalSize == null || finalSize == currentSavedSize) {
                        dragSize = null
                    } else {
                        scope.launch {
                            try {
                                desktopPreferences.saveMiniPlayerSize(style, finalSize)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                AppLogger.w(TAG, "保存迷你播放器尺寸失败", e)
                                dragSize = null
                            }
                        }
                    }
                }
            )
        }
        val baseDensity = LocalDensity.current
        val resizeDensity = remember(baseDensity, uiScale) { Density(baseDensity.density * uiScale, baseDensity.fontScale) }
        CompositionLocalProvider(LocalUiMotion provides uiMotion, LocalWindowEffects provides windowEffects) {
            MelodiaDesktopTheme(uiScale = uiScale * layoutSize.scale, palette = palette, skin = skin) {
                MiniPlayerContent(style, lyricSettings, controller, playerViewModel, actions, resize, resizeDensity)
            }
        }
    }
}

private fun positionOf(position: WindowPosition): MiniPlayerPosition? =
    if (position.isSpecified) MiniPlayerPosition(position.x.value.roundToInt(), position.y.value.roundToInt()) else null

private suspend fun savePosition(preferences: DesktopPreferences, position: MiniPlayerPosition) {
    try {
        preferences.saveMiniPlayerPosition(position)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.w(TAG, "保存迷你播放器位置失败", e)
    }
}

private fun windowSizeOf(size: MiniPlayerSize, uiScale: Float): DpSize {
    val factor = size.scale * uiScale
    return DpSize((size.width * factor).dp, (size.height * factor).dp)
}

// 首次打开放在主屏工作区右下角
private fun defaultPosition(size: DpSize): WindowPosition {
    val area = try {
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.let(::workArea)
    } catch (_: HeadlessException) {
        null
    } ?: return WindowPosition.PlatformDefault
    return WindowPosition(
        (area.x + area.width - size.width.value - SCREEN_MARGIN).dp,
        (area.y + area.height - size.height.value - SCREEN_MARGIN).dp
    )
}

// 屏幕扣掉任务栏后的可用区域
private fun workArea(configuration: GraphicsConfiguration): WindowBounds {
    val bounds = configuration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    return WindowBounds(
        bounds.x + insets.left,
        bounds.y + insets.top,
        bounds.width - insets.left - insets.right,
        bounds.height - insets.top - insets.bottom
    )
}
