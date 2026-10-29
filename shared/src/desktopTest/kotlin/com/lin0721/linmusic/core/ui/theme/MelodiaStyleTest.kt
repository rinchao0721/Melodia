package com.lin0721.linmusic.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class MelodiaStyleTest {

    @Test
    fun `默认样式各倍数为 1`() {
        assertEquals(1f, MelodiaStyle.Default.densityScale)
        assertEquals(1f, MelodiaStyle.Default.progressScale)
    }

    @Test
    fun `密度与粗进度条倍数`() {
        assertEquals(0.85f, MelodiaStyle(density = ThemeDensity.COMPACT).densityScale)
        assertEquals(1.15f, MelodiaStyle(density = ThemeDensity.COMFORTABLE).densityScale)
        assertEquals(2f, MelodiaStyle(progress = ThemeProgressStyle.THICK).progressScale)
    }
}
