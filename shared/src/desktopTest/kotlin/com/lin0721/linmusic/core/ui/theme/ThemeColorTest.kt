package com.lin0721.linmusic.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.lin0721.linmusic.core.preferences.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ThemeColorTest {

    @Test
    fun `未知主题模式回退深色`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("sepia"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("system"))
    }

    @Test
    fun `跟随系统时按系统深浅判断`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertEquals(false, ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }

    @Test
    fun `十六进制解析只接受六位色值`() {
        assertEquals(Color(0xFFC20C0C), parseRgbHex("#c20c0c"))
        assertEquals(Color(0xFFC20C0C), parseRgbHex("  C20C0C "))
        assertNull(parseRgbHex("#FFC20C0C"))
        assertNull(parseRgbHex("#C20C0G"))
        assertNull(parseRgbHex(""))
    }

    @Test
    fun `十六进制与 HSV 往返保持色值`() {
        listOf("#C20C0C", "#1DB954", "#3D8BFF", "#000000", "#FFFFFF", "#808080").forEach { hex ->
            val color = parseRgbHex(hex)!!
            assertEquals(hex, color.toRgbHex())
            assertEquals(hex, color.toHsv().toColor().toRgbHex())
        }
    }

    @Test
    fun `HSV 分量换算正确`() {
        val hsv = Color(0xFF00FF00).toHsv()
        assertTrue(abs(hsv.hue - 120f) < 0.01f)
        assertTrue(abs(hsv.saturation - 1f) < 0.001f)
        assertTrue(abs(hsv.value - 1f) < 0.001f)
        assertEquals(0f, Color(0xFF808080).toHsv().saturation)
    }

    @Test
    fun `主色失效时回退各模式默认色`() {
        assertEquals(ThemeAccents.dark.first().color, ThemeAccents.resolve("unknown", null, isDark = true))
        assertEquals(ThemeAccents.light.first().color, ThemeAccents.resolve(ThemeAccents.CUSTOM_ID, "bad", isDark = false))
        assertEquals(ThemeAccents.light.first().color, ThemeAccents.resolve(ThemeAccents.CUSTOM_ID, null, isDark = false))
        assertEquals(ThemeAccents.dark.first().color, ThemeAccents.resolve(ThemeAccents.DYNAMIC_ID, null, isDark = true))
    }

    @Test
    fun `主色按预设与自定义解析`() {
        assertEquals(Color(0xFF1DB954), ThemeAccents.resolve("green", null, isDark = true))
        assertEquals(Color(0xFF123456), ThemeAccents.resolve(ThemeAccents.CUSTOM_ID, "#123456", isDark = true))
        assertEquals(Color(0xFF654321), ThemeAccents.resolve(ThemeAccents.DYNAMIC_ID, null, isDark = false, dynamicColor = Color(0xFF654321)))
    }

    @Test
    fun `主色上文字色按对比度选择`() {
        assertEquals(Color.White, contentColorOn(Color(0xFFC20C0C)))
        assertEquals(Color.Black, contentColorOn(Color(0xFFE0A100)))
        assertTrue(contrastRatio(Color.White, Color.Black) > 20f)
    }

    @Test
    fun `沉浸区浓色铺底不论色相都能托住白字`() {
        listOf(0xFFE0A100, 0xFF1DB954, 0xFF3D8BFF, 0xFFD9703A, 0xFF9B6BFF).forEach { argb ->
            val fill = immersiveMidFill(Color(argb))
            assertTrue(contrastRatio(Color.White, fill) in 4.5f..5.5f)
            assertTrue(abs(fill.toHsl().hue - Color(argb).toHsl().hue) < 2f)
        }
        assertTrue(contrastRatio(Color.White, immersiveMidBlob(Color(0xFFE0A100))) >= 3.5f)
    }

    @Test
    fun `灰色封面不染色`() {
        val grey = Color(0xFF808080)
        assertTrue(immersiveMidFill(grey).toHsl().saturation < 0.01f)
        assertTrue(coverHeaderTint(grey).toHsl().saturation < 0.01f)
    }

    @Test
    fun `头部浅调保留色相且足够浅`() {
        val tint = coverHeaderTint(Color(0xFFD9703A))
        val hsl = tint.toHsl()
        assertTrue(abs(tint.luminance() - 0.7f) < 0.01f)
        assertTrue(abs(hsl.hue - Color(0xFFD9703A).toHsl().hue) < 2f)
        assertTrue(contrastRatio(Color(0xFF141414), tint) > 12f)
    }

    @Test
    fun `浅色主色预设配白字与放在白底上都达标`() {
        ThemeAccents.light.forEach { accent ->
            assertTrue(accent.id, contrastRatio(Color.White, accent.color) >= 4.5f)
            assertEquals(accent.id, accent.color, MelodiaPalettes.light.withAccent(accent.color).accentText)
        }
    }

    @Test
    fun `浅色主色文字色对任意自定义色都达标且保留色相`() {
        val pane = MelodiaPalettes.light.pane
        listOf(0xFFFFEB3B, 0xFF7CFC00, 0xFF00E5FF, 0xFFFF80AB, 0xFFF26B3A, 0xFFFFFFFF, 0xFF101010).forEach { argb ->
            val accent = Color(argb)
            val text = MelodiaPalettes.light.withAccent(accent).accentText
            assertTrue(contrastRatio(text, pane) >= 4.5f)
            if (accent.toHsl().saturation > 0.1f) assertTrue(abs(text.toHsl().hue - accent.toHsl().hue) < 2f)
        }
    }

    @Test
    fun `深色主色文字色保持原值`() {
        val accent = Color(0xFFC20C0C)
        assertEquals(accent, MelodiaPalettes.dark.withAccent(accent).accentText)
    }

    @Test
    fun `浅色预设旧 id 迁移到新预设`() {
        assertEquals(Color(0xFF18803A), ThemeAccents.resolve("forest", null, isDark = false))
        assertEquals(Color(0xFF1767CC), ThemeAccents.resolve("lake", null, isDark = false))
        assertEquals(Color(0xFF6E45D9), ThemeAccents.resolve("indigo", null, isDark = false))
        assertEquals("violet", ThemeAccents.normalizeId("indigo", isDark = false))
        assertEquals("indigo", ThemeAccents.normalizeId("indigo", isDark = true))
    }

    @Test
    fun `文字分级在面板上的对比度达标`() {
        val light = MelodiaPalettes.light
        assertTrue(contrastRatio(light.textPrimary, light.pane) >= 15f)
        assertTrue(contrastRatio(light.textSecondary, light.pane) >= 6f)
        assertTrue(contrastRatio(light.textSecondary, light.windowBackground) >= 4.5f)
        assertTrue(contrastRatio(light.textTertiary, light.pane) >= 4.5f)
        assertTrue(contrastRatio(light.success, light.pane) >= 4.5f)
        assertTrue(contrastRatio(light.error, light.pane) >= 4.5f)
    }

    @Test
    fun `头部浅调上的主文字任意色相都达标`() {
        (0 until 360 step 15).forEach { hue ->
            val tint = coverHeaderTint(Hsl(hue.toFloat(), 0.9f, 0.5f).toColor())
            assertTrue("hue=$hue", contrastRatio(MelodiaPalettes.light.textPrimary, tint) >= 4.5f)
            assertTrue("hue=$hue", contrastRatio(MelodiaPalettes.light.textSecondary, tint) >= 4.5f)
        }
    }

    @Test
    fun `浅色歌词页当前行在淡底与头部浅调上都达标`() {
        (0 until 360 step 15).forEach { hue ->
            val base = Hsl(hue.toFloat(), 0.8f, 0.5f).toColor()
            val deep = coverDeepTone(base)!!
            assertTrue("hue=$hue", contrastRatio(deep, coverWashTint(base)) >= 4.5f)
            assertTrue("hue=$hue", contrastRatio(deep, coverHeaderTint(base)) >= 4.5f)
            assertTrue("hue=$hue", contrastRatio(MelodiaPalettes.light.textPrimary.copy(alpha = 1f), coverWashTint(base)) >= 12f)
        }
        assertNull(coverDeepTone(Color(0xFF808080)))
        assertTrue(coverWashTint(Color(0xFF808080)).toHsl().saturation < 0.01f)
    }
}
