package com.lin0721.linmusic.core.player.floatinglyric

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.player.ui.KaraokeLyricRow
import kotlinx.coroutines.delay

private const val TAG = "FloatingLyricOverlay"

private const val CONTROLS_AUTO_HIDE_MS = 3_000L
private const val SECONDARY_SCALE = 0.7f
private const val LINE_HEIGHT_SCALE = 1.35f

private val PillShape = RoundedCornerShape(16.dp)
private val ControlBarShape = RoundedCornerShape(18.dp)
private val ControlBarBackground = Color(0xCC000000)

class FloatingLyricActions(
    val onPrevious: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onNext: () -> Unit,
    val onToggleLock: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onClose: () -> Unit,
    // 屏幕坐标增量（px），由 Service 换算成窗口位置
    val onMoveBy: (dx: Float, dy: Float) -> Unit,
    val onResizeStart: (LyricResizeHandle) -> Unit,
    // 屏幕坐标横向增量（px）
    val onResizeBy: (LyricResizeHandle, dx: Float) -> Unit,
    val onGestureEnd: () -> Unit
)

@Composable
fun FloatingLyricOverlay(
    frame: FloatingLyricFrame,
    style: FloatingLyricStyle,
    locked: Boolean,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    actions: FloatingLyricActions
) {
    var controlsVisible by remember { mutableStateOf(false) }
    // 每次操作递增，重新计时自动隐藏
    var interaction by remember { mutableIntStateOf(0) }
    LaunchedEffect(locked) { if (locked) controlsVisible = false }
    LaunchedEffect(controlsVisible, interaction) {
        if (controlsVisible) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }
    val touched: () -> Unit = { interaction++ }

    val played = parseColor(style.playedColor, Color.White)
    val unplayed = parseColor(style.unplayedColor, Color.White.copy(alpha = 0.6f))
    val density = LocalDensity.current
    val shadow = style.shadowArgb()?.let { argb ->
        with(density) {
            Shadow(Color(argb.toInt()), Offset(0f, FloatingLyricShadow.OFFSET_Y_DP.dp.toPx()), blurRadius = style.shadowBlurDp.dp.toPx())
        }
    }
    val textAlign = when (style.align) {
        FloatingLyricAlign.START -> TextAlign.Start
        FloatingLyricAlign.CENTER -> TextAlign.Center
        FloatingLyricAlign.END -> TextAlign.End
    }
    val fontWeight = if (style.bold) FontWeight.ExtraBold else FontWeight.Medium
    val lineHeight = (style.fontSizeSp * LINE_HEIGHT_SCALE).sp

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth()
                .clip(PillShape)
                .background(Color.Black.copy(alpha = style.backgroundAlpha / 100f))
        ) {
            // 手势层与拖动柄是兄弟节点：命中拖动柄时手势层收不到事件，调宽不会顺带移动窗口
            Box(
                Modifier.matchParentSize().dragAndTap(
                    onMoveBy = { dx, dy ->
                        touched()
                        actions.onMoveBy(dx, dy)
                    },
                    onGestureEnd = actions.onGestureEnd,
                    onTap = {
                        controlsVisible = !controlsVisible
                        touched()
                    },
                    onDoubleTap = actions.onToggleLock
                )
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                val primary = frame.primary
                if (primary != null && primary.words.isNotEmpty()) {
                    KaraokeLyricRow(
                        line = primary,
                        currentPositionProvider = positionProvider,
                        inactiveColor = unplayed,
                        activeColor = played,
                        fontSize = style.fontSizeSp.sp,
                        lineHeight = lineHeight,
                        textAlign = textAlign,
                        isPlaying = isPlaying,
                        fontWeight = fontWeight,
                        shadow = shadow
                    )
                } else {
                    Text(
                        primary?.text ?: frame.fallback,
                        color = played,
                        fontSize = style.fontSizeSp.sp,
                        lineHeight = lineHeight,
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
            if (controlsVisible) {
                Box(Modifier.matchParentSize()) {
                    ResizeHandle(LyricResizeHandle.LEFT, actions, touched, Modifier.align(Alignment.CenterStart))
                    ResizeHandle(LyricResizeHandle.RIGHT, actions, touched, Modifier.align(Alignment.CenterEnd))
                    ResizeHandle(LyricResizeHandle.CORNER, actions, touched, Modifier.align(Alignment.BottomEnd))
                }
            }
        }
        // 控制栏放在歌词下方，展开时歌词位置不跳动；
        // 窗口高度随内容变化，动画不能改布局尺寸，否则每帧都要重排窗口导致抽动
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 0f)),
            exit = fadeOut() + scaleOut(targetScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 0f))
        ) {
            ControlBar(isPlaying, actions, touched)
        }
    }
}

@Composable
private fun ResizeHandle(handle: LyricResizeHandle, actions: FloatingLyricActions, touched: () -> Unit, modifier: Modifier) {
    val corner = handle == LyricResizeHandle.CORNER
    Box(
        modifier
            .then(if (corner) Modifier.size(28.dp) else Modifier.width(20.dp).fillMaxHeight())
            .dragAndTap(
                onMoveBy = { dx, _ ->
                    touched()
                    actions.onResizeBy(handle, dx)
                },
                onDragStart = { actions.onResizeStart(handle) },
                onGestureEnd = actions.onGestureEnd,
                onTap = touched,
                onDoubleTap = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        if (corner) {
            Icon(
                Icons.Rounded.OpenInFull,
                contentDescription = "缩放歌词",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp).rotate(90f)
            )
        } else {
            Box(Modifier.width(4.dp).height(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))
        }
    }
}

@Composable
private fun ControlBar(isPlaying: Boolean, actions: FloatingLyricActions, touched: () -> Unit) {
    Row(
        Modifier.padding(top = 6.dp).clip(ControlBarShape).background(ControlBarBackground).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BarButton(Icons.Rounded.SkipPrevious, "上一首") {
            touched()
            actions.onPrevious()
        }
        BarButton(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "暂停" else "播放") {
            touched()
            actions.onTogglePlay()
        }
        BarButton(Icons.Rounded.SkipNext, "下一首") {
            touched()
            actions.onNext()
        }
        Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.25f)))
        BarButton(Icons.Rounded.Lock, "锁定歌词", actions.onToggleLock)
        BarButton(Icons.Rounded.Settings, "歌词设置", actions.onOpenSettings)
        BarButton(Icons.Rounded.Close, "关闭悬浮歌词", actions.onClose)
    }
}

@Composable
private fun BarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

// 悬浮窗随手指移动时局部坐标会跟着变，拖动只能用屏幕坐标 rawX/rawY 的增量
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.dragAndTap(
    onMoveBy: (Float, Float) -> Unit,
    onGestureEnd: () -> Unit,
    onDragStart: () -> Unit = {},
    onTap: () -> Unit,
    onDoubleTap: () -> Unit
): Modifier {
    val context = LocalContext.current
    val tracker = remember { DragTapTracker(ViewConfiguration.get(context).scaledTouchSlop.toFloat()) }
    tracker.onMoveBy = onMoveBy
    tracker.onDragStart = onDragStart
    tracker.onGestureEnd = onGestureEnd
    tracker.onTap = onTap
    tracker.onDoubleTap = onDoubleTap
    return pointerInteropFilter { event -> tracker.handle(event) }
}

private class DragTapTracker(private val touchSlop: Float) {
    var onMoveBy: (Float, Float) -> Unit = { _, _ -> }
    var onDragStart: () -> Unit = {}
    var onGestureEnd: () -> Unit = {}
    var onTap: () -> Unit = {}
    var onDoubleTap: () -> Unit = {}

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var lastTapUpTime = 0L

    fun handle(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastX = downX
                lastY = downY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (kotlin.math.abs(event.rawX - downX) > touchSlop || kotlin.math.abs(event.rawY - downY) > touchSlop)) {
                    dragging = true
                    onDragStart()
                }
                if (dragging) {
                    onMoveBy(event.rawX - lastX, event.rawY - lastY)
                }
                lastX = event.rawX
                lastY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    onGestureEnd()
                } else if (event.eventTime - lastTapUpTime <= ViewConfiguration.getDoubleTapTimeout()) {
                    lastTapUpTime = 0L
                    onDoubleTap()
                } else {
                    lastTapUpTime = event.eventTime
                    onTap()
                }
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) onGestureEnd()
                dragging = false
            }
        }
        return true
    }
}

private fun parseColor(hex: String, fallback: Color): Color {
    val argb = FloatingLyricPalette.parseArgb(hex)
    if (argb == null) {
        AppLogger.w(TAG, "悬浮歌词颜色值非法: $hex，使用默认色")
        return fallback
    }
    return Color(argb.toInt())
}
