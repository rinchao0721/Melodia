package com.lin0721.linmusic.desktop.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.platform.native.UiScale
import com.lin0721.linmusic.desktop.ui.ProvideUiScale
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.desktop.platform.native.DesktopLyricBehavior
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Toolkit

private const val TAG = "DesktopLyric"

// 与 Android 悬浮歌词一致的 70% 透明黑底
private val PillBackground = Color(0xB3000000)

// Android 字号按手机屏幕设定，桌面观看距离更远，统一放大
private const val DESKTOP_TEXT_SCALE = 1.5f

private val PillShape = RoundedCornerShape(15.dp)

private val WindowSize = DpSize(1000.dp, 96.dp)

// 窗口几何按 AWT 像素换算（逻辑尺寸 × 桌面缩放）；内容缩放由 ProvideUiScale 统一注入
private val ScaledWindowSize: DpSize
    get() = DpSize(WindowSize.width * UiScale.current.factor, WindowSize.height * UiScale.current.factor)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopLyricWindow(
    visible: Boolean,
    locked: Boolean,
    playerViewModel: PlayerViewModel,
    controller: PlaybackController,
    settingsPreferences: SettingsPreferences,
    lyricBehavior: DesktopLyricBehavior,
    onHide: () -> Unit
) {
    // 默认放在屏幕下方约五分之四高度处，水平居中
    val windowState = rememberWindowState(
        size = ScaledWindowSize,
        position = remember {
            val screen = Toolkit.getDefaultToolkit().screenSize
            WindowPosition(
                ((screen.width - ScaledWindowSize.width.value) / 2).dp,
                (screen.height * 0.8f - ScaledWindowSize.height.value).dp
            )
        }
    )
    Window(
        onCloseRequest = onHide,
        visible = visible,
        state = windowState,
        title = "Melodia 桌面歌词",
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        focusable = false,
        resizable = false
    ) {
        // 穿透设置可能因等窗口映射而短暂重试，放到 IO 线程执行
        LaunchedEffect(locked, visible) {
            if (visible) withContext(Dispatchers.IO) { lyricBehavior.setClickThrough(window, locked) }
        }

        val nowPlaying by controller.nowPlaying.collectAsState()
        val detailState by playerViewModel.songDetailState.collectAsState()
        val currentIndex by playerViewModel.currentLyricIndex.collectAsState()
        val textSize by settingsPreferences.lyricTextSize.collectAsState(initial = 14)
        val colorHex by settingsPreferences.lyricTextColor.collectAsState(initial = "#FFFFFF")

        val track = nowPlaying
        val text = when {
            track == null -> "Melodia 桌面歌词"
            else -> detailState.lyrics.getOrNull(currentIndex)?.text?.takeIf { it.isNotBlank() }
                ?: "${track.title} - ${track.artist}"
        }

        ProvideUiScale {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WindowDraggableArea(
                    Modifier.clip(PillShape).background(PillBackground)
                        .onPointerEvent(PointerEventType.Press) { event ->
                            // 与 Android 一致：双击关闭
                            if (event.awtEventOrNull?.clickCount == 2) onHide()
                        }
                ) {
                    Text(
                        text,
                        color = parseColor(colorHex),
                        fontSize = (textSize * DESKTOP_TEXT_SCALE).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private fun parseColor(hex: String): Color = try {
    Color(java.awt.Color.decode(hex).rgb)
} catch (e: NumberFormatException) {
    AppLogger.w(TAG, "桌面歌词颜色值非法: $hex，回退白色", e)
    Color.White
}
