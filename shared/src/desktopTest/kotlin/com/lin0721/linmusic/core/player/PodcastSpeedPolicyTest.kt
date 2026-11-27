package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastSpeedPolicyTest {

    @Test
    fun `clamps values below min and above max`() {
        assertEquals(0.5f, PodcastSpeedPolicy.normalize(0.1f))
        assertEquals(0.5f, PodcastSpeedPolicy.normalize(0.5f))
        assertEquals(3.0f, PodcastSpeedPolicy.normalize(3.0f))
        assertEquals(3.0f, PodcastSpeedPolicy.normalize(4.5f))
    }

    @Test
    fun `rounds arbitrary floats to nearest 0_1 step`() {
        assertEquals(1.2f, PodcastSpeedPolicy.normalize(1.24f))
        assertEquals(1.3f, PodcastSpeedPolicy.normalize(1.26f))
        assertEquals(1.0f, PodcastSpeedPolicy.normalize(Float.NaN))
    }

    @Test
    fun `indexOf and speedAt roundtrip across all 26 steps`() {
        assertEquals(26, PodcastSpeedPolicy.STEP_COUNT)
        for (index in 0 until PodcastSpeedPolicy.STEP_COUNT) {
            val speed = PodcastSpeedPolicy.speedAt(index)
            assertEquals(index, PodcastSpeedPolicy.indexOf(speed))
        }
    }

    @Test
    fun `formats speed with single decimal place and x suffix`() {
        assertEquals("0.5x", PodcastSpeedPolicy.format(0.5f))
        assertEquals("1.0x", PodcastSpeedPolicy.format(1.0f))
        assertEquals("1.5x", PodcastSpeedPolicy.format(1.52f))
        assertEquals("3.0x", PodcastSpeedPolicy.format(3.4f))
    }
}
