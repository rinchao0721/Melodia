package com.lin0721.linmusic.desktop.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.theme.progressScaled
import com.lin0721.linmusic.desktop.ui.components.TooltipLabel
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlin.math.roundToInt

private val TrackHeight = 4.dp
private val ThumbSize = 12.dp
private val TouchHeight = 16.dp
private val PreviewGap = 8.dp

// 进度与音量共用的细轨道滑块：已播放部分平时为白色，悬停或拖动时变主题色并显示圆形滑块。
// 传入 previewLabel 时，悬停位置上方显示该位置的提示，且轨道上从当前进度到悬停位置铺一段浅色预览
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    previewLabel: ((fraction: Float) -> String)? = null,
    externalActive: Boolean = false
) {
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = enabled && (hovered || dragging || externalActive)

    var widthPx by remember { mutableStateOf(0) }
    var hoverX by remember { mutableStateOf<Float?>(null) }
    val thumbPx = with(density) { ThumbSize.toPx() }
    val hoverFraction = hoverX?.takeIf { widthPx > thumbPx }
        ?.let { ((it - thumbPx / 2) / (widthPx - thumbPx)).coerceIn(0f, 1f) }
    val previewing = !externalActive && previewLabel != null && enabled && hoverFraction != null

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val fraction = value.coerceIn(0f, 1f)
    val trackHeight = progressScaled(TrackHeight)
    val shape = RoundedCornerShape(trackHeight / 2)

    Box(
        modifier = modifier
            .height(TouchHeight)
            .onSizeChanged { widthPx = it.width }
            .hoverable(interaction, enabled = enabled)
            .onPointerEvent(PointerEventType.Enter) { hoverX = it.changes.firstOrNull()?.position?.x }
            .onPointerEvent(PointerEventType.Move) { hoverX = it.changes.firstOrNull()?.position?.x }
            .onPointerEvent(PointerEventType.Exit) { hoverX = null }
            .then(
                if (enabled) {
                    Modifier.pointerInput(widthPx, thumbPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            dragging = true
                            fun calculateFraction(x: Float): Float =
                                if (widthPx > thumbPx) ((x - thumbPx / 2) / (widthPx - thumbPx)).coerceIn(0f, 1f)
                                else if (widthPx > 0) (x / widthPx).coerceIn(0f, 1f)
                                else 0f

                            val initialFraction = calculateFraction(down.position.x)
                            currentOnValueChange(initialFraction)
                            hoverX = down.position.x
                            down.consume()

                            drag(down.id) { change ->
                                currentOnValueChange(calculateFraction(change.position.x))
                                hoverX = change.position.x
                                change.consume()
                            }
                            dragging = false
                            currentOnValueChangeFinished?.invoke()
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // 背景底轨
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(shape)
                .background(DesktopColors.SurfaceLight)
        )

        // 悬停预览轨
        if (previewing && hoverFraction > fraction) {
            Box(
                Modifier
                    .fillMaxWidth(hoverFraction)
                    .height(trackHeight)
                    .clip(shape)
                    .background(DesktopColors.TextGray.copy(alpha = 0.6f))
            )
        }

        // 已播放/激活进度轨
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(trackHeight)
                .clip(shape)
                .background(
                    when {
                        !enabled -> DesktopColors.TextGray
                        active -> DesktopColors.Accent
                        else -> DesktopColors.TextPrimary
                    }
                )
        )

        // 滑块圆点：垂直居中在轨道上
        if (widthPx > thumbPx) {
            val thumbOffsetPx = (fraction * (widthPx - thumbPx)).coerceIn(0f, (widthPx - thumbPx).toFloat())
            val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }
            Box(
                Modifier
                    .offset(x = thumbOffsetDp)
                    .size(ThumbSize)
                    .then(
                        if (active) Modifier.clip(CircleShape).background(DesktopColors.TextPrimary)
                        else Modifier
                    )
            )
        }

        // 悬停气泡提示
        if (previewing) {
            val x = hoverX ?: 0f
            Box(
                Modifier.align(Alignment.TopStart).layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints())
                    val minX = 0
                    val maxX = (widthPx - placeable.width).coerceAtLeast(0)
                    val left = (x.roundToInt() - placeable.width / 2).coerceIn(minX, maxX)
                    layout(0, 0) {
                        placeable.place(left, -placeable.height - PreviewGap.roundToPx())
                    }
                }
            ) {
                TooltipLabel(previewLabel(hoverFraction))
            }
        }
    }
}
