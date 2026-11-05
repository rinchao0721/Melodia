package com.lin0721.linmusic.desktop.ui.theme

import androidx.compose.ui.graphics.toArgb
import com.lin0721.linmusic.core.ui.theme.MelodiaPalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowBorderTest {

    private fun channels(rgb: Int) = listOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)

    @Test
    fun accentColorIsUsedAsIs() {
        assertEquals(0x0078D4, WindowBorder.opaqueRgb(0x0078D4, focused = true))
    }

    @Test
    fun neutralLineIsOpaqueRgbWithoutAlpha() {
        val rgb = WindowBorder.opaqueRgb(null, focused = true)
        assertEquals(0, rgb ushr 24)
    }

    // 失焦时系统会把边框压暗，中性线需预先调亮作补偿
    @Test
    fun unfocusedNeutralLineIsBrighter() {
        val focused = WindowBorder.opaqueRgb(null, focused = true)
        val unfocused = WindowBorder.opaqueRgb(null, focused = false)

        assertNotEquals(focused, unfocused)
        channels(focused).zip(channels(unfocused)).forEach { (f, u) -> assertTrue(u >= f) }
    }

    // 浅色主题下中性线比窗口底色更深
    @Test
    fun lightNeutralLineIsDarkerThanWindow() {
        val rgb = WindowBorder.opaqueRgb(null, focused = true, palette = MelodiaPalettes.light)
        val window = MelodiaPalettes.light.windowBackground.toArgb() and 0xFFFFFF
        channels(rgb).zip(channels(window)).forEach { (line, bg) -> assertTrue(line < bg) }
    }
}
