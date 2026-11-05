package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lin0721.linmusic.core.ui.theme.themedShadow
import kotlinx.coroutines.delay
import kotlin.math.max

enum class TooltipSide { Top, Bottom, Left, Right }

private const val SHOW_DELAY_MS = 400L
private const val FADE_MS = 150
private val AnchorGap = 2.dp
private val ShadowRoom = 6.dp
private val SlideDistance = 4.dp
private val WindowMargin = 4.dp

// 悬停提示：停留片刻后贴着锚点出现在指定一侧，不跟随鼠标；点击或移出即消失
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopTooltip(
    text: String,
    modifier: Modifier = Modifier,
    side: TooltipSide = TooltipSide.Top,
    content: @Composable () -> Unit
) {
    DesktopTooltip(
        tooltipContent = { TooltipLabel(text) },
        modifier = modifier,
        side = side,
        content = content
    )
}

// 悬停提示：停留片刻后贴着锚点出现在指定一侧，不跟随鼠标；点击或移出即消失
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopTooltip(
    tooltipContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    side: TooltipSide = TooltipSide.Top,
    content: @Composable () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(hovered, dismissed) {
        if (hovered && !dismissed) {
            delay(SHOW_DELAY_MS)
            visible = true
        } else {
            visible = false
        }
    }
    Box(
        modifier
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) {
                hovered = false
                dismissed = false
            }
            .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { dismissed = true }
    ) {
        content()
        if (visible) TooltipPopup(tooltipContent, side)
    }
}

@Composable
private fun TooltipPopup(tooltipContent: @Composable () -> Unit, side: TooltipSide) {
    val density = LocalDensity.current
    val provider = remember(side, density) {
        with(density) { TooltipPositionProvider(side, AnchorGap.roundToPx(), WindowMargin.roundToPx()) }
    }
    val motion = LocalUiMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, motion.tween(FADE_MS)) }
    val slidePx = with(density) { SlideDistance.toPx() }
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false)) {
        // 外圈留白给阴影，避免被弹层边界裁掉
        Box(
            Modifier.padding(ShadowRoom).graphicsLayer {
                alpha = progress.value
                // 从远离锚点的一侧滑入
                val offset = (1f - progress.value) * slidePx
                when (side) {
                    TooltipSide.Top -> translationY = offset
                    TooltipSide.Bottom -> translationY = -offset
                    TooltipSide.Left -> translationX = offset
                    TooltipSide.Right -> translationX = -offset
                }
            }
        ) {
            Box(Modifier.themedShadow(4.dp, RoundedCornerShape(6.dp))) { tooltipContent() }
        }
    }
}

// 居中贴边放置，超出窗口时往里收
private class TooltipPositionProvider(
    private val side: TooltipSide,
    private val gapPx: Int,
    private val marginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val width = popupContentSize.width
        val height = popupContentSize.height
        val x = when (side) {
            TooltipSide.Top, TooltipSide.Bottom -> anchorBounds.center.x - width / 2
            TooltipSide.Left -> anchorBounds.left - width - gapPx
            TooltipSide.Right -> anchorBounds.right + gapPx
        }
        val y = when (side) {
            TooltipSide.Top -> anchorBounds.top - height - gapPx
            TooltipSide.Bottom -> anchorBounds.bottom + gapPx
            TooltipSide.Left, TooltipSide.Right -> anchorBounds.center.y - height / 2
        }
        return IntOffset(
            x.coerceIn(marginPx, max(marginPx, windowSize.width - width - marginPx)),
            y.coerceIn(marginPx, max(marginPx, windowSize.height - height - marginPx))
        )
    }
}
