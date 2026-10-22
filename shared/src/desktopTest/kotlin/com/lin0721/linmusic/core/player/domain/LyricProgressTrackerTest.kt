package com.lin0721.linmusic.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricProgressTrackerTest {
    @Test fun nextLineDoesNotFlashOffWhenSamplesStraddleBoundary() {
        // 同时覆盖普通 LRC 与逐字歌词，边界位置不应影响结果。
        for (boundary in listOf(4000L, 40_000L)) {
            for (timed in listOf(false, true)) {
                val lines = listOf(
                    LyricLine(boundary - 2000, 2000, "previous"),
                    LyricLine(boundary, 2000, "next", words =
                        if (timed) listOf(WordInfo("next", 0, 2000)) else emptyList())
                )
                val tracker = LyricProgressTracker()
                tracker.update(lines, boundary - 50)
                for (offset in listOf(1L, -2L, 0L, -1L, 50L)) {
                    val state = tracker.update(lines, boundary + offset)
                    assertEquals("boundary=$boundary timed=$timed offset=$offset", setOf(1), state.displayIndices)
                    assertEquals(1, state.primaryIndex)
                }
            }
        }
    }

    @Test fun smallExplicitBackwardSeekStillReactivatesPreviousLine() {
        val lines = listOf(LyricLine(1000, 1000, "one"), LyricLine(2000, 1000, "two"))
        val tracker = LyricProgressTracker()
        tracker.update(lines, 2010)
        assertEquals(setOf(0), tracker.update(lines, 1990, isSeek = true).displayIndices)
        assertEquals(setOf(1), tracker.update(lines, 2050).displayIndices)
    }

    @Test fun externalSeekAndResetDiscardPreviousPosition() {
        val lines = listOf(LyricLine(1000, 1000, "one"), LyricLine(2000, 1000, "two"))
        val tracker = LyricProgressTracker()
        tracker.update(lines, 2500)
        assertEquals(setOf(0), tracker.update(lines, 1500).displayIndices)
        tracker.update(lines, 2500)
        tracker.reset()
        assertEquals(setOf(0), tracker.update(lines, 1990).displayIndices)
    }

    @Test fun overlappingLinesAndGapsKeepTimelineBehavior() {
        val lines = listOf(LyricLine(1000, 2000, "one"), LyricLine(2000, 2000, "two"))
        val tracker = LyricProgressTracker()
        tracker.update(lines, 1990)
        assertEquals(setOf(0, 1), tracker.update(lines, 2010).displayIndices)
        assertEquals(setOf(0, 1), tracker.update(lines, 1999).displayIndices)
        assertEquals(setOf(0, 1), tracker.update(lines, 3010).displayIndices)
        assertEquals(emptySet<Int>(), tracker.update(lines, 4010).displayIndices)
        assertEquals(emptySet<Int>(), tracker.update(lines, 3999).displayIndices)
    }
}
