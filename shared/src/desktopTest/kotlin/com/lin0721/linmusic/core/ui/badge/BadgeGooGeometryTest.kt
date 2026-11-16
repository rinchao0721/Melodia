package com.lin0721.linmusic.core.ui.badge

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class BadgeGooGeometryTest {

    @Test
    fun `formatBadgeCount formats zero negative normal and overflow counts`() {
        assertEquals("", formatBadgeCount(-5))
        assertEquals("", formatBadgeCount(0))
        assertEquals("1", formatBadgeCount(1))
        assertEquals("99", formatBadgeCount(99))
        assertEquals("99+", formatBadgeCount(100))
        assertEquals("99+", formatBadgeCount(999))
    }

    @Test
    fun `computeAnchorRadius shrinks linearly and clamps at minRadius`() {
        assertEquals(9f, computeAnchorRadius(baseRadius = 9f, minRadius = 3f, distance = 0f), 1e-4f)
        assertEquals(8f, computeAnchorRadius(baseRadius = 9f, minRadius = 3f, distance = 12f), 1e-4f)
        assertEquals(6f, computeAnchorRadius(baseRadius = 9f, minRadius = 3f, distance = 36f), 1e-4f)
        assertEquals(3f, computeAnchorRadius(baseRadius = 9f, minRadius = 3f, distance = 72f), 1e-4f)
        assertEquals(3f, computeAnchorRadius(baseRadius = 9f, minRadius = 3f, distance = 120f), 1e-4f)
        assertEquals(9f, computeAnchorRadius(baseRadius = 9f, minRadius = 3f, distance = -10f), 1e-4f)
    }

    @Test
    fun `computeBadgeGooGeometry returns null when broken or below minDistance`() {
        assertNull(
            computeBadgeGooGeometry(
                anchorCenter = Offset.Zero,
                dragCenter = Offset(40f, 0f),
                baseAnchorRadius = 9f,
                minAnchorRadius = 3f,
                dragRadius = 9f,
                broken = true,
                minDistance = 2f
            )
        )
        assertNull(
            computeBadgeGooGeometry(
                anchorCenter = Offset.Zero,
                dragCenter = Offset(1.5f, 0f),
                baseAnchorRadius = 9f,
                minAnchorRadius = 3f,
                dragRadius = 9f,
                broken = false,
                minDistance = 2f
            )
        )
    }

    @Test
    fun `computeBadgeGooGeometry computes tangent and midpoint for horizontal drag`() {
        val goo = computeBadgeGooGeometry(
            anchorCenter = Offset(0f, 0f),
            dragCenter = Offset(36f, 0f),
            baseAnchorRadius = 9f,
            minAnchorRadius = 3f,
            dragRadius = 9f,
            broken = false,
            minDistance = 2f
        )
        assertNotNull(goo)
        goo!!
        assertEquals(6f, goo.anchorRadius, 1e-4f)
        assertEquals(Offset(18f, 0f), goo.controlPoint)
        assertOffsetEquals(Offset(0f, 6f), goo.p1)
        assertOffsetEquals(Offset(0f, -6f), goo.p2)
        assertOffsetEquals(Offset(36f, -7.2f), goo.p3)
        assertOffsetEquals(Offset(36f, 7.2f), goo.p4)
    }

    @Test
    fun `computeBadgeGooGeometry computes perpendicular tangent points for diagonal drag`() {
        val anchor = Offset(10f, 20f)
        val drag = Offset(40f, 60f) // dx = 30, dy = 40, dist = 50
        val goo = computeBadgeGooGeometry(
            anchorCenter = anchor,
            dragCenter = drag,
            baseAnchorRadius = 9f,
            minAnchorRadius = 3f,
            dragRadius = 9f,
            broken = false,
            minDistance = 2f
        )!!
        val expectedR0 = 9f - 50f / 12f
        val expectedR1 = 9f * 0.8f
        assertEquals(expectedR0, goo.anchorRadius, 1e-4f)
        assertOffsetEquals(Offset(25f, 40f), goo.controlPoint)

        // p1-anchor 垂直于连线且距离等于 r0
        val d1x = goo.p1.x - anchor.x
        val d1y = goo.p1.y - anchor.y
        assertEquals(expectedR0, hypot(d1x, d1y), 1e-4f)
        assertEquals(0f, d1x * 30f + d1y * 40f, 1e-3f)

        // p3-drag 垂直于连线且距离等于 r1，并与 p2 在同一侧
        val d3x = goo.p3.x - drag.x
        val d3y = goo.p3.y - drag.y
        assertEquals(expectedR1, hypot(d3x, d3y), 1e-4f)
        assertEquals(0f, d3x * 30f + d3y * 40f, 1e-3f)
        val d2x = goo.p2.x - anchor.x
        val d2y = goo.p2.y - anchor.y
        assertTrue(d2x * d3x + d2y * d3y > 0f)
    }

    @Test
    fun `break state stays broken once threshold exceeded and release checks both broken and threshold`() {
        val threshold = 80f
        assertFalse(shouldBreakGoo(80f, threshold))
        assertTrue(shouldBreakGoo(80.1f, threshold))

        // 未断开 -> 超过 80 -> 断开 -> 拉回 30 仍保持断开
        var broken = false
        broken = updateBrokenState(broken, 50f, threshold)
        assertFalse(broken)
        broken = updateBrokenState(broken, 85f, threshold)
        assertTrue(broken)
        broken = updateBrokenState(broken, 30f, threshold)
        assertTrue(broken)

        // 断开且仍超阈值才消除；拉回阈值内松手不消除
        assertFalse(shouldDismissOnRelease(broken = true, distance = 30f, breakThreshold = threshold))
        assertFalse(shouldDismissOnRelease(broken = true, distance = 80f, breakThreshold = threshold))
        assertFalse(shouldDismissOnRelease(broken = false, distance = 90f, breakThreshold = threshold))
        assertTrue(shouldDismissOnRelease(broken = true, distance = 85f, breakThreshold = threshold))
    }

    @Test
    fun `local dismissal hides same or smaller count and restores when count increases or resets`() {
        assertFalse(shouldHideForLocalDismissal(count = 5, dismissedAtCount = null))
        assertTrue(shouldHideForLocalDismissal(count = 5, dismissedAtCount = 5))
        assertTrue(shouldHideForLocalDismissal(count = 3, dismissedAtCount = 5))
        assertFalse(shouldHideForLocalDismissal(count = 6, dismissedAtCount = 5))
        assertTrue(shouldHideForLocalDismissal(count = 0, dismissedAtCount = null))

        assertEquals(5, nextDismissedAtCount(count = 5, currentDismissedAtCount = 5))
        assertNull(nextDismissedAtCount(count = 6, currentDismissedAtCount = 5))
        assertNull(nextDismissedAtCount(count = 0, currentDismissedAtCount = 5))
    }

    private fun assertOffsetEquals(expected: Offset, actual: Offset, delta: Float = 1e-4f) {
        assertEquals(expected.x, actual.x, delta)
        assertEquals(expected.y, actual.y, delta)
    }
}
