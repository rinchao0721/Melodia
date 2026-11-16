package com.lin0721.linmusic.core.ui.badge

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

internal const val BADGE_BREAK_DISTANCE_DP = 80f
internal const val BADGE_MIN_ANCHOR_RADIUS_DP = 3f
internal const val BADGE_MIN_GOO_DISTANCE_DP = 2f
internal const val BADGE_ANCHOR_SHRINK_RATIO = 12f
internal const val BADGE_DRAG_ATTACH_FACTOR = 0.8f

// 拉丝两端的切点与二次贝塞尔控制点：p1-p4、p2-p3 分别位于连线两侧
data class BadgeGooGeometry(
    val anchorCenter: Offset,
    val anchorRadius: Float,
    val dragCenter: Offset,
    val controlPoint: Offset,
    val p1: Offset,
    val p2: Offset,
    val p3: Offset,
    val p4: Offset
)

fun formatBadgeCount(count: Int): String = when {
    count <= 0 -> ""
    count > 99 -> "99+"
    else -> count.toString()
}

fun computeAnchorRadius(
    baseRadius: Float,
    minRadius: Float,
    distance: Float,
    shrinkPerDistance: Float = BADGE_ANCHOR_SHRINK_RATIO
): Float {
    if (baseRadius <= 0f) return 0f
    val floor = minRadius.coerceIn(0f, baseRadius)
    return (baseRadius - distance.coerceAtLeast(0f) / shrinkPerDistance).coerceAtLeast(floor)
}

fun shouldBreakGoo(distance: Float, breakThreshold: Float): Boolean =
    distance > breakThreshold

fun updateBrokenState(
    alreadyBroken: Boolean,
    distance: Float,
    breakThreshold: Float
): Boolean = alreadyBroken || shouldBreakGoo(distance, breakThreshold)

fun shouldDismissOnRelease(
    broken: Boolean,
    distance: Float,
    breakThreshold: Float
): Boolean = broken && distance > breakThreshold

fun shouldHideForLocalDismissal(count: Int, dismissedAtCount: Int?): Boolean {
    if (count <= 0) return true
    if (dismissedAtCount == null) return false
    return count <= dismissedAtCount
}

fun nextDismissedAtCount(count: Int, currentDismissedAtCount: Int?): Int? = when {
    count <= 0 -> null
    currentDismissedAtCount != null && count > currentDismissedAtCount -> null
    else -> currentDismissedAtCount
}

fun computeBadgeGooGeometry(
    anchorCenter: Offset,
    dragCenter: Offset,
    baseAnchorRadius: Float,
    minAnchorRadius: Float,
    dragRadius: Float,
    broken: Boolean,
    minDistance: Float,
    shrinkPerDistance: Float = BADGE_ANCHOR_SHRINK_RATIO,
    dragAttachFactor: Float = BADGE_DRAG_ATTACH_FACTOR
): BadgeGooGeometry? {
    if (broken) return null
    val dx = dragCenter.x - anchorCenter.x
    val dy = dragCenter.y - anchorCenter.y
    val distance = hypot(dx, dy)
    if (distance < minDistance || distance <= 0f) return null

    val r0 = computeAnchorRadius(baseAnchorRadius, minAnchorRadius, distance, shrinkPerDistance)
    val r1 = dragRadius * dragAttachFactor
    val nx = dx / distance
    val ny = dy / distance

    val p1 = Offset(anchorCenter.x - r0 * ny, anchorCenter.y + r0 * nx)
    val p2 = Offset(anchorCenter.x + r0 * ny, anchorCenter.y - r0 * nx)
    val p3 = Offset(dragCenter.x + r1 * ny, dragCenter.y - r1 * nx)
    val p4 = Offset(dragCenter.x - r1 * ny, dragCenter.y + r1 * nx)
    val controlPoint = Offset((anchorCenter.x + dragCenter.x) * 0.5f, (anchorCenter.y + dragCenter.y) * 0.5f)

    return BadgeGooGeometry(
        anchorCenter = anchorCenter,
        anchorRadius = r0,
        dragCenter = dragCenter,
        controlPoint = controlPoint,
        p1 = p1,
        p2 = p2,
        p3 = p3,
        p4 = p4
    )
}
