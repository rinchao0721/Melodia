package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackEdgesTest {

    private fun windows(vararg db: Double, fromMs: Long = 0L) =
        db.mapIndexed { i, v -> LoudnessWindow(fromMs + i * TrackEdges.WINDOW_MS, v) }

    @Test
    fun `满幅正弦约为负3dBFS，零信号为负无穷`() {
        // 满幅正弦均方值为 0.5
        assertEquals(-3.01, TrackEdges.dbfs(0.5 * 100, 100), 0.01)
        assertTrue(TrackEdges.dbfs(0.0, 100).isInfinite())
        assertTrue(TrackEdges.dbfs(1.0, 0).isInfinite())
    }

    @Test
    fun `有效结尾为最后一个有声窗口末尾`() {
        val w = windows(-20.0, -30.0, -44.0, -60.0, -80.0, fromMs = 200_000L)
        assertEquals(200_300L, TrackEdges.effectiveEndMs(w))
    }

    @Test
    fun `有效开头为第一个有声窗口并前留一个窗口`() {
        val w = windows(-90.0, -70.0, -50.0, -30.0, -10.0)
        assertEquals(200L, TrackEdges.effectiveStartMs(w))
        assertEquals(0L, TrackEdges.effectiveStartMs(windows(-10.0)))
    }

    @Test
    fun `全程静音时无结果`() {
        val w = windows(-80.0, -70.0, Double.NEGATIVE_INFINITY)
        assertNull(TrackEdges.effectiveEndMs(w))
        assertNull(TrackEdges.effectiveStartMs(w))
    }
}
