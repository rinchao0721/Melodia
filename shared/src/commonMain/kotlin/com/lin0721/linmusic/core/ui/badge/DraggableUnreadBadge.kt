package com.lin0721.linmusic.core.ui.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlin.math.hypot

// QQ 风格可拖拽消除未读角标；未提供 DragBadgeOverlayHost 或 enabled=false 时退化为静态胶囊角标
@Composable
fun DraggableUnreadBadge(
    count: Int,
    onDismiss: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: TextUnit = 11.sp,
    minWidth: Dp = 18.dp,
    height: Dp = 18.dp,
    horizontalPadding: Dp = if (count > 9) 5.dp else 4.dp,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent
) {
    var dismissedAtCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(count) {
        dismissedAtCount = nextDismissedAtCount(count, dismissedAtCount)
    }

    val text = formatBadgeCount(count)
    if (text.isEmpty() || shouldHideForLocalDismissal(count, dismissedAtCount)) return

    val host = LocalDragBadgeHost.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val textStyle = remember(fontSize, contentColor) {
        TextStyle(
            color = contentColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            lineHeight = fontSize
        )
    }
    val textMeasurer = rememberTextMeasurer()
    val textLayoutResult = remember(text, textStyle, textMeasurer) {
        textMeasurer.measure(text = text, style = textStyle, maxLines = 1)
    }

    var badgeCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var hiddenForToken by remember { mutableStateOf<Any?>(null) }

    DisposableEffect(host) {
        onDispose {
            hiddenForToken?.let { token ->
                host?.cancelActiveDrag(token)
            }
            hiddenForToken = null
        }
    }

    val currentCount by rememberUpdatedState(count)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentContainerColor by rememberUpdatedState(containerColor)
    val currentContentColor by rememberUpdatedState(contentColor)
    val currentBorderColor by rememberUpdatedState(borderColor)
    val currentTextLayout by rememberUpdatedState(textLayoutResult)

    val breakThresholdPx = with(density) { BADGE_BREAK_DISTANCE_DP.dp.toPx() }
    val minAnchorRadiusPx = with(density) { BADGE_MIN_ANCHOR_RADIUS_DP.dp.toPx() }
    val minGooDistancePx = with(density) { BADGE_MIN_GOO_DISTANCE_DP.dp.toPx() }
    val borderWidthPx = with(density) { borderWidth.toPx() }

    val capsuleShape = RoundedCornerShape(999.dp)
    val interactive = enabled && host != null

    val dragModifier = if (interactive) {
        Modifier.pointerInput(host, enabled, density, borderWidthPx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                if (!host.canStartDrag()) return@awaitEachGesture
                val coords = badgeCoords ?: return@awaitEachGesture
                val hostCoords = host.hostCoordinates
                if (!coords.isAttached || hostCoords == null || !hostCoords.isAttached) {
                    return@awaitEachGesture
                }

                // 按下即消费事件，防止触发所在行点击、列表滚动、侧栏滑动或桌面标题栏拖动
                down.consume()

                val badgeCenterInHost = hostCoords.localPositionOf(
                    coords,
                    Offset(coords.size.width / 2f, coords.size.height / 2f)
                )
                val pointerDownInHost = hostCoords.localPositionOf(coords, down.position)
                val touchOffset = badgeCenterInHost - pointerDownInHost

                val dragToken = Any()
                var broken = false
                var latestDragCenter = badgeCenterInHost
                val badgeHeight = coords.size.height.toFloat()
                val baseRadius = badgeHeight / 2f

                host.startDrag(
                    ActiveDragBadge(
                        token = dragToken,
                        anchorCenter = badgeCenterInHost,
                        initialDragCenter = badgeCenterInHost,
                        badgeSize = Size(coords.size.width.toFloat(), badgeHeight),
                        textLayoutResult = currentTextLayout,
                        containerColor = currentContainerColor,
                        contentColor = currentContentColor,
                        borderColor = currentBorderColor,
                        borderWidthPx = borderWidthPx,
                        baseAnchorRadius = baseRadius,
                        minAnchorRadius = minAnchorRadiusPx,
                        dragRadius = baseRadius,
                        minGooDistance = minGooDistancePx
                    )
                )
                hiddenForToken = dragToken

                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()

                        val pointerInHost = if (coords.isAttached && hostCoords.isAttached) {
                            hostCoords.localPositionOf(coords, change.position)
                        } else {
                            pointerDownInHost + (change.position - down.position)
                        }
                        latestDragCenter = pointerInHost + touchOffset
                        val dist = hypot(
                            latestDragCenter.x - badgeCenterInHost.x,
                            latestDragCenter.y - badgeCenterInHost.y
                        )
                        val nowBroken = updateBrokenState(broken, dist, breakThresholdPx)
                        if (nowBroken && !broken) {
                            broken = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        host.updateDrag(dragToken, latestDragCenter, broken)
                    }

                    val finalDist = hypot(
                        latestDragCenter.x - badgeCenterInHost.x,
                        latestDragCenter.y - badgeCenterInHost.y
                    )
                    if (shouldDismissOnRelease(broken, finalDist, breakThresholdPx)) {
                        dismissedAtCount = currentCount
                        hiddenForToken = null
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        host.dismissDrag(dragToken, density.density)
                        currentOnDismiss()
                    } else {
                        host.releaseToSpring(dragToken) {
                            if (hiddenForToken === dragToken) {
                                hiddenForToken = null
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    host.releaseToSpring(dragToken) {
                        if (hiddenForToken === dragToken) {
                            hiddenForToken = null
                        }
                    }
                    throw e
                }
            }
        }
    } else {
        Modifier
    }

    // 传入的 modifier（如 align/padding）放外层，确保定位与点击范围精准落在胶囊自身
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .onGloballyPositioned { badgeCoords = it }
                .then(dragModifier)
                .graphicsLayer { alpha = if (hiddenForToken != null) 0f else 1f }
                .defaultMinSize(minWidth = minWidth, minHeight = height)
                .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, capsuleShape) else Modifier)
                .clip(capsuleShape)
                .background(containerColor)
                .padding(horizontal = horizontalPadding),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = text,
                style = textStyle,
                maxLines = 1
            )
        }
    }
}
