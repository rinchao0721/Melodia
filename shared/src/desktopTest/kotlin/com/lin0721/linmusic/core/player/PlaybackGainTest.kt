package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackGainTest {

    @Test
    fun `默认档位不衰减`() {
        assertEquals(PlaybackGain.MAX_STEP, PlaybackGain.DEFAULT_STEP)
        assertEquals(1f, PlaybackGain.amplitude(PlaybackGain.MAX_STEP), 0f)
        assertEquals(0.0, PlaybackGain.dbForStep(PlaybackGain.MAX_STEP), 1e-9)
        assertEquals("0 dB", PlaybackGain.label(PlaybackGain.MAX_STEP))
    }

    @Test
    fun `最低档静音且越界档位被截断`() {
        assertEquals(0f, PlaybackGain.amplitude(0), 0f)
        assertEquals("静音", PlaybackGain.label(0))
        assertEquals(0f, PlaybackGain.amplitude(-10), 0f)
        assertEquals(1f, PlaybackGain.amplitude(1_000), 0f)
    }

    @Test
    fun `档位单调递增且始终落在 0 到 1 之间`() {
        var previous = -1f
        for (step in 0..PlaybackGain.MAX_STEP) {
            val amplitude = PlaybackGain.amplitude(step)
            assertTrue("step=$step amplitude=$amplitude", amplitude in 0f..1f)
            assertTrue("step=$step 应不小于 previous=$previous", amplitude >= previous)
            previous = amplitude
        }
    }

    @Test
    fun `低端覆盖到负四十 dB 且逐档约零点四 dB`() {
        // 0~-40 dB 分成 100 档，逐档约 0.404 dB，正好补上系统一次音量跳变
        assertEquals(-40.0, PlaybackGain.dbForStep(1), 1e-9)
        assertEquals(-20.0, PlaybackGain.dbForStep(51), 0.3)
        val stepDb = PlaybackGain.dbForStep(99) - PlaybackGain.dbForStep(100)
        assertEquals(0.404, -stepDb, 0.001)
    }

    @Test
    fun `标签保留一位小数`() {
        assertEquals("-40.0 dB", PlaybackGain.label(1))
        assertEquals("-19.8 dB", PlaybackGain.label(51))
        assertEquals("-0.4 dB", PlaybackGain.label(99))
    }

    @Test
    fun `越界档位会被归一化`() {
        assertEquals(0, PlaybackGain.normalizeStep(-1))
        assertEquals(PlaybackGain.MAX_STEP, PlaybackGain.normalizeStep(101))
    }
}
