package com.lin0721.linmusic.desktop.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowScope
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricAlign
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricFrame
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricPalette
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricShadow
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricStyle
import com.lin0721.linmusic.core.player.floatinglyric.LyricResizeHandle
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.components.LocalWindowEffects
import com.lin0721.linmusic.desktop.ui.components.PlayPauseIcon
import com.lin0721.linmusic.desktop.ui.components.PointerTravel
import com.lin0721.linmusic.desktop.ui.components.onDoubleClick
import com.lin0721.linmusic.desktop.ui.lyricsview.KaraokeText
import com.lin0721.linmusic.desktop.ui.shell.WindowDragArea
import kotlinx.coroutines.delay
import java.awt.Cursor

private const val TAG = "DesktopLyricContent"

// 鼠标在控制栏与歌词条之间的透明缝隙穿过时不立即收起
private const val HOVER_EXIT_DELAY_MS = 600L

private const val SECONDARY_SCALE = 0.6f
private const val LINE_HEIGHT_SCALE = 1.35f

// 未锁定时背景至少保留极低不透明度，否则纯文字状态下字间空隙抓不住鼠标
private const val MIN_INTERACTIVE_ALPHA = 0.01f
private const val HOVER_BACKGROUND_ALPHA = 0.35f

private val PillShape = RoundedCornerShape(15.dp)
private val ControlBarShape = RoundedCornerShape(16.dp)
private val ControlBarBackground = Color(0xB3000000)
private val ResizeHandleWidth = 12.dp

class DesktopLyricActions(
    val onPrevious: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onNext: () -> Unit,
    val onToggleLock: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onClose: () -> Unit
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowScope.DesktopLyricContent(
    frame: FloatingLyricFrame,
    style: FloatingLyricStyle,
    locked: Boolean,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    actions: DesktopLyricActions,
    resize: DesktopLyricResizeCallbacks,
    onHeightMeasured: (Dp) -> Unit
) {
    val density = LocalDensity.current
    var pointerInside by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    LaunchedEffect(pointerInside) {
        if (pointerInside) {
            hovered = true
        } else {
            delay(HOVER_EXIT_DELAY_MS)
            hovered = false
        }
    }
    val interactive = hovered && !locked

    val played = parseColor(style.playedColor, Color.White)
    val unplayed = parseColor(style.unplayedColor, Color.White.copy(alpha = 0.6f))
    val shadow = style.shadowArgb()?.let { argb ->
        with(density) {
            Shadow(Color(argb.toInt()), Offset(0f, FloatingLyricShadow.OFFSET_Y_DP.dp.toPx()), blurRadius = style.shadowBlurDp.dp.toPx())
        }
    }
    val textAlign = style.align.toTextAlign()
    val fontSize = style.fontSizeSp.sp
    val fontWeight = if (style.bold) FontWeight.ExtraBold else FontWeight.Medium
    val backgroundAlpha = when {
        locked -> style.backgroundAlpha / 100f
        interactive -> maxOf(style.backgroundAlpha / 100f, HOVER_BACKGROUND_ALPHA)
        else -> maxOf(style.backgroundAlpha / 100f, MIN_INTERACTIVE_ALPHA)
    }

    Column(
        Modifier.fillMaxWidth()
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            .onSizeChanged { onHeightMeasured(with(density) { it.height.toDp() }) }
            .onPointerEvent(PointerEventType.Enter) { pointerInside = true }
            .onPointerEvent(PointerEventType.Exit) { pointerInside = false },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HoverReveal(revealed = interactive) {
            ControlBar(isPlaying, actions)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().clip(PillShape).background(Color.Black.copy(alpha = backgroundAlpha))) {
            WindowDragArea(Modifier.fillMaxWidth().onDoubleClick(actions.onToggleLock)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    val primary = frame.primary
                    if (primary != null && primary.words.isNotEmpty()) {
                        KaraokeText(
                            line = primary,
                            positionProvider = positionProvider,
                            inactiveColor = unplayed,
                            activeColor = played,
                            fontSize = fontSize,
                            lineHeight = (style.fontSizeSp * LINE_HEIGHT_SCALE).sp,
                            textAlign = textAlign,
                            isPlaying = isPlaying,
                            fontWeight = fontWeight,
                            shadow = shadow
                        )
                    } else {
                        Text(
                            primary?.text ?: frame.fallback,
                            color = played,
                            fontSize = fontSize,
                            lineHeight = (style.fontSizeSp * LINE_HEIGHT_SCALE).sp,
                            fontWeight = fontWeight,
                            textAlign = textAlign,
                            style = LocalTextStyle.current.copy(shadow = shadow),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    frame.secondary?.let { secondary ->
                        val secondarySize = style.fontSizeSp * SECONDARY_SCALE
                        Text(
                            secondary,
                            color = unplayed,
                            fontSize = secondarySize.sp,
                            lineHeight = (secondarySize * LINE_HEIGHT_SCALE).sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = LocalTextStyle.current.copy(shadow = shadow),
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        )
                    }
                }
            }
            if (!locked) {
                HoverReveal(revealed = interactive, modifier = Modifier.matchParentSize()) {
                    Box(Modifier.fillMaxSize()) {
                        EdgeHandle(LyricResizeHandle.LEFT, resize, Modifier.align(Alignment.CenterStart))
                        EdgeHandle(LyricResizeHandle.RIGHT, resize, Modifier.align(Alignment.CenterEnd))
                        CornerHandle(resize, Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
        }
    }
}

class DesktopLyricResizeCallbacks(
    val onStart: (LyricResizeHandle) -> Unit,
    // 相对按下位置的累计横向位移
    val onDrag: (LyricResizeHandle, Dp) -> Unit,
    val onEnd: (LyricResizeHandle) -> Unit
)

@Composable
private fun EdgeHandle(handle: LyricResizeHandle, resize: DesktopLyricResizeCallbacks, modifier: Modifier) {
    Box(
        modifier.width(ResizeHandleWidth).fillMaxHeight().resizeDrag(handle, Cursor.E_RESIZE_CURSOR, resize),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.width(4.dp).height(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
    }
}

@Composable
private fun CornerHandle(resize: DesktopLyricResizeCallbacks, modifier: Modifier) {
    Box(
        modifier.size(ResizeHandleWidth * 2).resizeDrag(LyricResizeHandle.CORNER, Cursor.SE_RESIZE_CURSOR, resize),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.OpenInFull,
            contentDescription = "缩放歌词",
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(12.dp).rotate(90f)
        )
    }
}

// 拖左边时窗口原点跟着移动，局部坐标会漂；改用屏幕坐标算累计位移
@Composable
private fun Modifier.resizeDrag(handle: LyricResizeHandle, cursor: Int, resize: DesktopLyricResizeCallbacks): Modifier {
    val callbacks by rememberUpdatedState(resize)
    val effects = LocalWindowEffects.current
    return pointerHoverIcon(PointerIcon(Cursor(cursor))).pointerInput(handle, effects) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false).consume()
            val travel = PointerTravel.start(effects) ?: return@awaitEachGesture
            callbacks.onStart(handle)
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
                val delta = travel.delta() ?: continue
                callbacks.onDrag(handle, delta.x.dp)
            }
            callbacks.onEnd(handle)
        }
    }
}

@Composable
private fun ControlBar(isPlaying: Boolean, actions: DesktopLyricActions) {
    Row(
        Modifier.clip(ControlBarShape).background(ControlBarBackground).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BarButton(Icons.Rounded.SkipPrevious, "上一首", actions.onPrevious)
        BarButton("播放/暂停", actions.onTogglePlay) { tint ->
            PlayPauseIcon(isPlaying, tint, size = 20.dp)
        }
        BarButton(Icons.Rounded.SkipNext, "下一首", actions.onNext)
        Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.25f)))
        BarButton(Icons.Rounded.Lock, "锁定歌词", actions.onToggleLock)
        BarButton(Icons.Rounded.Settings, "歌词设置", actions.onOpenSettings)
        BarButton(Icons.Rounded.Close, "关闭桌面歌词", actions.onClose)
    }
}

@Composable
private fun BarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    BarButton(description, onClick) { tint ->
        Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun BarButton(description: String, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    IconButton(onClick = onClick, interactionSource = interaction, modifier = Modifier.size(32.dp)) {
        content(if (hovered) Color.White else Color.White.copy(alpha = 0.75f))
    }
}

private fun FloatingLyricAlign.toTextAlign(): TextAlign = when (this) {
    FloatingLyricAlign.START -> TextAlign.Start
    FloatingLyricAlign.CENTER -> TextAlign.Center
    FloatingLyricAlign.END -> TextAlign.End
}

private fun parseColor(hex: String, fallback: Color): Color {
    val argb = FloatingLyricPalette.parseArgb(hex)
    if (argb == null) {
        AppLogger.w(TAG, "桌面歌词颜色值非法: $hex，使用默认色")
        return fallback
    }
    return Color(argb.toInt())
}
