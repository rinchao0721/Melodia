package com.lin0721.linmusic.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeToSkipCoverStateTest {

    private fun stateAt(key: String, previous: String?, next: String?, index: Int): SwipeToSkipCoverState =
        SwipeToSkipCoverState().apply {
            containerWidthPx = 100f
            syncCurrentKey(key, previous, next, SwipeQueueTarget(index, key))
        }

    @Test
    fun `相邻曲目与当前同一首时不冻结`() {
        val state = stateAt("B", "A", "B", 1)

        state.syncCurrentKey("B", "A", "B", SwipeQueueTarget(1, "B"))

        assertFalse(state.isTransitioning)
    }

    @Test
    fun `按钮切歌时队列先行冻结，曲目追上后解冻并给出方向`() {
        val state = stateAt("A", "C", "B", 0)

        state.syncCurrentKey("A", "A", "C", SwipeQueueTarget(1, "B"))
        assertTrue(state.isTransitioning)

        val direction = state.syncCurrentKey("B", "A", "C", SwipeQueueTarget(1, "B"))
        assertEquals(SwipeDirection.NEXT, direction)
        assertFalse(state.isTransitioning)
    }

    @Test
    fun `拖拽切到相邻同一首歌，队列位置变化后解冻并归位`() {
        val state = stateAt("B", "A", "B", 1)
        state.beginTransition()
        // 滑出动画停在终点
        state.offsetX = -100f

        state.syncCurrentKey("B", "B", "A", SwipeQueueTarget(2, "B"))

        assertFalse(state.isTransitioning)
        assertEquals(0f, state.offsetX)
    }

    @Test
    fun `拖拽确认后队列还没移动时保持冻结`() {
        val state = stateAt("B", "A", "B", 1)
        state.beginTransition()

        val direction = state.syncCurrentKey("B", "A", "B", SwipeQueueTarget(1, "B"))

        assertNull(direction)
        assertTrue(state.isTransitioning)
    }

    @Test
    fun `切歌失败队列退回原曲后解冻`() {
        val state = stateAt("A", "C", "B", 0)
        state.syncCurrentKey("A", "A", "C", SwipeQueueTarget(1, "B"))
        assertTrue(state.isTransitioning)

        state.syncCurrentKey("A", "C", "B", SwipeQueueTarget(0, "A"))

        assertFalse(state.isTransitioning)
    }

    @Test
    fun `未提供队列位置时不冻结`() {
        val state = SwipeToSkipCoverState()
        state.syncCurrentKey("A", "B", "A")

        state.syncCurrentKey("A", "B", "A")

        assertFalse(state.isTransitioning)
    }
}
