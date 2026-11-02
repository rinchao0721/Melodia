package com.lin0721.linmusic.core.ui.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.theme.MelodiaPalettes
import com.lin0721.linmusic.core.ui.theme.MelodiaStyle
import com.lin0721.linmusic.core.ui.theme.ThemeAccents
import com.lin0721.linmusic.core.ui.theme.ThemeFont
import com.lin0721.linmusic.core.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinParserTest {

    private fun success(text: String): SkinParseResult.Success {
        val result = SkinParser.parse(text)
        assertTrue("应解析成功：$result", result is SkinParseResult.Success)
        return result as SkinParseResult.Success
    }

    private fun failure(text: String): String {
        val result = SkinParser.parse(text)
        assertTrue("应解析失败：$result", result is SkinParseResult.Failure)
        return (result as SkinParseResult.Failure).reason
    }

    @Test
    fun `最小皮肤沿用全部默认值`() {
        val parsed = success("""{"format":1,"id":"plain","name":"素"}""")
        val skin = parsed.skin
        assertEquals(SkinMode.entries.toSet(), skin.modes)
        assertTrue(skin.darkColors.isEmpty() && skin.lightColors.isEmpty())
        assertEquals(MelodiaStyle.Default, skin.style)
        assertNull(skin.background)
        assertTrue(parsed.warnings.isEmpty())
    }

    @Test
    fun `结构错误直接失败`() {
        failure("not json")
        failure("[]")
        failure("""{"id":"a","name":"b"}""")
        failure("""{"format":2,"id":"a","name":"b"}""")
        failure("""{"format":1,"id":"Bad Id","name":"b"}""")
        failure("""{"format":1,"id":"builtin","name":"b"}""")
        failure("""{"format":1,"id":"ok","name":"  "}""")
    }

    @Test
    fun `未知字段被忽略`() {
        val parsed = success("""{"format":1,"id":"a","name":"b","future":{"x":1}}""")
        assertTrue(parsed.warnings.isEmpty())
    }

    @Test
    fun `色值支持六位与八位，无效项记警告后跳过`() {
        val parsed = success(
            """{"format":1,"id":"a","name":"b","colors":{"dark":{
                "pane":"#101820","hover":"#14FFFFFF","textPrimary":"red","sparkle":"#FFFFFF"}}}"""
        )
        val dark = parsed.skin.darkColors
        assertEquals(Color(0xFF101820), dark[PaletteToken.PANE])
        assertEquals(Color(0x14FFFFFF), dark[PaletteToken.HOVER])
        assertFalse(PaletteToken.TEXT_PRIMARY in dark)
        assertEquals(2, parsed.warnings.size)
    }

    @Test
    fun `色值编码往返`() {
        listOf("#101820", "#14FFFFFF", "#000000").forEach { hex ->
            assertEquals(hex, SkinParser.encodeColor(SkinParser.parseColor(hex)!!))
        }
        assertNull(SkinParser.parseColor("#12345"))
    }

    @Test
    fun `样式超出范围收到边界并给出警告`() {
        val parsed = success(
            """{"format":1,"id":"a","name":"b","style":{
                "radius":{"pane":100,"cover":999},
                "font":{"family":"monospace"},
                "border":{"panes":true,"width":5},
                "density":"tiny"}}"""
        )
        val style = parsed.skin.style
        assertEquals(24.dp, style.paneRadius)
        assertEquals(999.dp, style.coverRadius)
        assertEquals(ThemeFont.MONOSPACE, style.font)
        assertEquals(2.dp, style.paneBorder)
        assertEquals(MelodiaStyle.Default.density, style.density)
        assertEquals(3, parsed.warnings.size)
    }

    @Test
    fun `随包字体本期不支持`() {
        val parsed = success("""{"format":1,"id":"a","name":"b","style":{"font":{"family":"assets/x.ttf"}}}""")
        assertEquals(ThemeFont.DEFAULT, parsed.skin.style.font)
        assertEquals(1, parsed.warnings.size)
    }

    @Test
    fun `背景图路径必须在 assets 下`() {
        val bad = success("""{"format":1,"id":"a","name":"b","background":{"image":"../bg.png"}}""")
        assertNull(bad.skin.background)
        val good = success("""{"format":1,"id":"a","name":"b","background":{"image":"assets/bg.png","dim":2}}""")
        assertEquals(0.9f, good.skin.background!!.dim)
    }

    @Test
    fun `按 modes 判断支持的深浅模式`() {
        val skin = success("""{"format":1,"id":"a","name":"b","modes":["light"]}""").skin
        assertFalse(skin.supports(isDark = true))
        assertTrue(skin.supports(isDark = false))
        val both = success("""{"format":1,"id":"a","name":"b","modes":["dark","light"]}""").skin
        assertTrue(both.supports(isDark = true))
        assertTrue(both.supports(isDark = false))
    }

    @Test
    fun `没有皮肤时色板与内置一致`() {
        val palette = resolveSkinPalette(isDark = false, ThemeAccents.DEFAULT_ID, null, skin = null)
        assertEquals(MelodiaPalettes.light.withAccent(ThemeAccents.light.first().color), palette)
    }

    @Test
    fun `皮肤主色生效，用户自定义色优先`() {
        val skin = success(
            """{"format":1,"id":"a","name":"b","colors":{"light":{"accent":"#1767CC","accentContainer":"#22FF0000","pane":"#FAFAF5"}}}"""
        ).skin
        val withSkinAccent = resolveSkinPalette(false, ThemeAccents.DEFAULT_ID, null, skin)
        assertEquals(Color(0xFF1767CC), withSkinAccent.accent)
        assertEquals(Color(0x22FF0000), withSkinAccent.accentContainer)
        assertEquals(Color(0xFFFAFAF5), withSkinAccent.pane)
        assertEquals(MelodiaPalettes.light.textPrimary, withSkinAccent.textPrimary)

        val custom = resolveSkinPalette(false, ThemeAccents.CUSTOM_ID, "#18803A", skin)
        assertEquals(Color(0xFF18803A), custom.accent)
        assertTrue(contrastRatio(custom.accentText, custom.pane) >= 4.5f)
        assertTrue(custom.accentContainer != Color(0x22FF0000))
    }
}
