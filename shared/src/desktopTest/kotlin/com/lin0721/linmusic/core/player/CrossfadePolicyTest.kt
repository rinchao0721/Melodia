package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CrossfadePolicyTest {

    @Test
    fun `时长按步进取整并截断到上下限`() {
        assertEquals(3000, CrossfadePolicy.normalizeDurationMs(3000))
        assertEquals(3000, CrossfadePolicy.normalizeDurationMs(3200))
        assertEquals(3500, CrossfadePolicy.normalizeDurationMs(3300))
        assertEquals(500, CrossfadePolicy.normalizeDurationMs(0))
        assertEquals(12_000, CrossfadePolicy.normalizeDurationMs(60_000))
    }

    @Test
    fun `实际淡化时长不超过有效时长一半`() {
        assertEquals(3000L, CrossfadePolicy.autoFadeMs(3000, 200_000L))
        assertEquals(2000L, CrossfadePolicy.autoFadeMs(12_000, 4000L))
        assertEquals(0L, CrossfadePolicy.autoFadeMs(3000, 0L))
    }

    @Test
    fun `触发点为有效结尾减淡化时长`() {
        assertEquals(197_000L, CrossfadePolicy.triggerPositionMs(200_000L, 3000L))
        assertEquals(0L, CrossfadePolicy.triggerPositionMs(1000L, 3000L))
    }

    @Test
    fun `只有自动切歌才淡化`() {
        assertEquals(3000L, CrossfadePolicy.fadeMsFor(autoTransition = true, enabled = true, isPlaying = true, startPositionMs = 0L, autoFadeMs = 3000L))
        assertEquals(0L, CrossfadePolicy.fadeMsFor(autoTransition = false, enabled = true, isPlaying = true, startPositionMs = 0L, autoFadeMs = 3000L))
    }

    @Test
    fun `关闭或未播放或断点续播时不淡化`() {
        assertEquals(0L, CrossfadePolicy.fadeMsFor(autoTransition = true, enabled = false, isPlaying = true, startPositionMs = 0L, autoFadeMs = 3000L))
        assertEquals(0L, CrossfadePolicy.fadeMsFor(autoTransition = true, enabled = true, isPlaying = false, startPositionMs = 0L, autoFadeMs = 3000L))
        assertEquals(0L, CrossfadePolicy.fadeMsFor(autoTransition = true, enabled = true, isPlaying = true, startPositionMs = 3000L, autoFadeMs = 3000L))
    }

    @Test
    fun `开启交叉时提前预取`() {
        assertEquals(8000L, CrossfadePolicy.prefetchWindowMs(enabled = false))
        assertEquals(CrossfadePolicy.ANALYSIS_WINDOW_MS, CrossfadePolicy.prefetchWindowMs(enabled = true))
    }

    @Test
    fun `等功率曲线端点与中点`() {
        assertEquals(1f, CrossfadePolicy.fadeOutGain(0f, 1f), 1e-4f)
        assertEquals(0f, CrossfadePolicy.fadeOutGain(1f, 1f), 1e-4f)
        assertEquals(0f, CrossfadePolicy.fadeInGain(0f), 1e-4f)
        assertEquals(1f, CrossfadePolicy.fadeInGain(1f), 1e-4f)
        val mid = CrossfadePolicy.fadeOutGain(0.5f, 1f)
        val midIn = CrossfadePolicy.fadeInGain(0.5f)
        assertEquals(1f, mid * mid + midIn * midIn, 1e-4f)
        assertEquals(0.25f, CrossfadePolicy.fadeOutGain(0f, 0.25f), 1e-4f)
    }
}
