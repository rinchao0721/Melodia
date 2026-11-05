package com.lin0721.linmusic.core.ui.skin

import androidx.compose.ui.graphics.Color
import com.lin0721.linmusic.core.ui.theme.Hsl
import com.lin0721.linmusic.core.ui.theme.MelodiaStyle
import com.lin0721.linmusic.core.ui.theme.ThemeAccents
import com.lin0721.linmusic.core.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinContrastTest {

    private fun skin(darkColors: Map<PaletteToken, Color> = emptyMap(), lightColors: Map<PaletteToken, Color> = emptyMap()) = Skin(
        id = "t",
        name = "t",
        author = null,
        version = null,
        modes = SkinMode.entries.toSet(),
        darkColors = darkColors,
        lightColors = lightColors,
        background = null,
        style = MelodiaStyle.Default,
        layouts = null
    )

    @Test
    fun `只改无关项时不做修正`() {
        val (_, fixes) = SkinContrast.fix(skin(darkColors = mapOf(PaletteToken.SUCCESS to Color(0xFF00FF00))))
        assertTrue(fixes.isEmpty())
    }

    @Test
    fun `中灰面板上的文字被修正到达标`() {
        val grey = Color(0xFF8A8A8A)
        val (fixed, fixes) = SkinContrast.fix(skin(lightColors = mapOf(PaletteToken.PANE to grey, PaletteToken.WINDOW_BACKGROUND to grey)))
        assertTrue(fixes.isNotEmpty())
        val palette = resolveSkinPalette(false, ThemeAccents.DEFAULT_ID, null, fixed)
        assertTrue(contrastRatio(palette.textPrimary, grey) >= 7f || palette.textPrimary == Color.Black)
        assertTrue(contrastRatio(palette.textSecondary, grey) >= 4.5f)
        assertTrue(contrastRatio(palette.textTertiary, grey) >= 3f)
        fixes.forEach { assertTrue(it.description.startsWith("浅色")) }
    }

    @Test
    fun `皮肤主色上的文字色不足时改用黑白`() {
        val (fixed, fixes) = SkinContrast.fix(
            skin(darkColors = mapOf(PaletteToken.ACCENT to Color(0xFFFFD54F), PaletteToken.ON_ACCENT to Color.White))
        )
        assertEquals(Color.Black, fixed.darkColors[PaletteToken.ON_ACCENT])
        assertTrue(fixes.any { it.token == PaletteToken.ON_ACCENT })
    }

    @Test
    fun `皮肤没写主色时不改主色文字`() {
        val (fixed, _) = SkinContrast.fix(skin(darkColors = mapOf(PaletteToken.PANE to Color(0xFF2A0000))))
        assertTrue(PaletteToken.ACCENT_TEXT !in fixed.darkColors)
    }

    @Test
    fun `任意色相在深浅背景上都能调到门槛`() {
        val backgrounds = listOf(Color(0xFF121212), Color(0xFFFFFFFF), Color(0xFF5A5A5A), Color(0xFFB0B0B0))
        backgrounds.forEach { background ->
            (0 until 24).forEach { step ->
                val foreground = Hsl(step * 15f, 0.7f, 0.5f).toColor()
                val result = SkinContrast.ensureContrast(foreground, listOf(background), 4.5f)
                assertTrue("hue=${step * 15} bg=$background", contrastRatio(result, background) >= 4.5f)
            }
        }
    }
}
