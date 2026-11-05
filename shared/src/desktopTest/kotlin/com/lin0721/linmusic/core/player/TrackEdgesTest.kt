package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackEdgesTest {

    private fun windows(vararg db: Double, fromMs: Long = 0L) =
        db.mapIndexed { i, v -> LoudnessWindow(fromMs + i * TrackEdges.WINDOW_MS, v) }

    private fun repeat(db: Double, count: Int) = DoubleArray(count) { db }

    @Test
    fun `满幅正弦约为负3dBFS，零信号为负无穷`() {
        // 满幅正弦均方值为 0.5
        assertEquals(-3.01, TrackEdges.dbfs(0.5 * 100, 100), 0.01)
        assertTrue(TrackEdges.dbfs(0.0, 100).isInfinite())
        assertTrue(TrackEdges.dbfs(1.0, 0).isInfinite())
    }

    @Test
    fun `尾部在绝对阈值以下的静音被跳过`() {
        val w = windows(*repeat(-20.0, 20), *repeat(-80.0, 30), fromMs = 200_000L)
        // 平滑会把有声段向后带出两个窗口
        assertEquals(202_200L, TrackEdges.effectiveEndMs(w))
    }

    @Test
    fun `比峰值低30dB以上的尾奏视为已结束`() {
        // 底噪 -42dBFS 高于绝对阈值，但远低于 -6dBFS 的主体
        val w = windows(*repeat(-6.0, 30), *repeat(-42.0, 50))
        assertEquals(3_200L, TrackEdges.effectiveEndMs(w))
    }

    @Test
    fun `安静的歌整体响度低也不被误判为结束`() {
        val w = windows(*repeat(-36.0, 40), *repeat(-90.0, 10))
        assertEquals(4_200L, TrackEdges.effectiveEndMs(w))
    }

    @Test
    fun `有效开头为第一个有声窗口并前留一个窗口`() {
        assertEquals(200L, TrackEdges.effectiveStartMs(windows(-90.0, -70.0, -50.0, -30.0, -10.0)))
        assertEquals(0L, TrackEdges.effectiveStartMs(windows(-10.0)))
    }

    @Test
    fun `响度只统计有声窗口`() {
        assertEquals(-12.0, TrackEdges.audibleLoudnessDb(windows(-90.0, -12.0, -80.0, -12.0))!!, 0.01)
        // 功率平均：-10 与 -20 的均值约为 -12.6
        assertEquals(-12.6, TrackEdges.audibleLoudnessDb(windows(-10.0, -20.0))!!, 0.05)
    }

    @Test
    fun `全程静音时无结果`() {
        val w = windows(-80.0, -70.0, Double.NEGATIVE_INFINITY)
        assertNull(TrackEdges.effectiveEndMs(w))
        assertNull(TrackEdges.effectiveStartMs(w))
        assertNull(TrackEdges.audibleLoudnessDb(w))
        assertNull(TrackEdges.effectiveEndMs(emptyList()))
    }
}
