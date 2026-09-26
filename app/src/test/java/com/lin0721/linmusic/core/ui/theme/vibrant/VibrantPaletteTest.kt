package com.lin0721.linmusic.core.ui.theme.vibrant

import com.lin0721.linmusic.core.ui.theme.FallbackBase
import com.lin0721.linmusic.core.ui.theme.isGrayscaleSwatches
import com.lin0721.linmusic.core.ui.theme.pickBaseColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrantPaletteTest {

    private fun argb(r: Int, g: Int, b: Int, a: Int = 255): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `rgb与hsl互转不失真`() {
        val (h, s, l) = rgbToHsl(255, 0, 0)
        assertEquals(0f, h, 0.001f)
        assertEquals(1f, s, 0.001f)
        assertEquals(0.5f, l, 0.001f)

        val gray = rgbToHsl(128, 128, 128)
        assertEquals(0f, gray[1], 0.001f)

        val rgb = hslToRgb(h, s, l)
        assertEquals(255, rgb[0])
        assertEquals(0, rgb[1])
        assertEquals(0, rgb[2])
    }

    @Test
    fun `默认filter排除纯白与透明但保留纯黑`() {
        assertTrue(defaultVibrantFilter(0, 0, 0, 255))
        assertTrue(!defaultVibrantFilter(255, 255, 255, 255))
        assertTrue(!defaultVibrantFilter(0, 0, 0, 10))
        assertTrue(defaultVibrantFilter(200, 100, 50, 255))
    }

    @Test
    fun `双色图像量化出两个代表色且population之和等于像素总数`() {
        val pixels = IntArray(200) { i -> if (i < 120) argb(220, 20, 20) else argb(20, 20, 220) }

        val swatches = medianCutQuantize(pixels, 64, ::defaultVibrantFilter)
        assertTrue(swatches.isNotEmpty())
        assertEquals(200, swatches.sumOf { it.population })

        val reddish = swatches.maxByOrNull { it.rgb.red }
        assertNotNull(reddish)
        assertTrue(reddish!!.rgb.red > reddish.rgb.blue)
    }

    @Test
    fun `纯灰图像量化后饱和度接近0`() {
        val pixels = IntArray(64) { argb(128, 128, 128) }
        val swatches = medianCutQuantize(pixels, 64, ::defaultVibrantFilter)

        assertTrue(swatches.isNotEmpty())
        assertTrue(swatches.all { it.hsl[1] < 0.05f })
    }

    @Test
    fun `全部像素被filter排除时返回空色板`() {
        val pixels = IntArray(16) { argb(255, 255, 255) }
        val swatches = medianCutQuantize(pixels, 64, ::defaultVibrantFilter)
        assertTrue(swatches.isEmpty())
    }

    private fun hex(rgb: Int, population: Int) = VibrantSwatch(
        androidx.compose.ui.graphics.Color(0xFF000000.toInt() or rgb),
        population,
    )

    private fun hslOf(color: androidx.compose.ui.graphics.Color) =
        rgbToHsl((color.red * 255f).toInt(), (color.green * 255f).toInt(), (color.blue * 255f).toInt())

    private fun chromaOf(color: androidx.compose.ui.graphics.Color) =
        maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)

    private fun assertSameHue(expected: VibrantSwatch, actual: androidx.compose.ui.graphics.Color) {
        assertEquals(expected.hsl[0], hslOf(actual)[0], 0.01f)
    }

    @Test
    fun `大面积色系胜过小面积高饱和点缀`() {
        val largeBrown = hex(0x966F41, 300)
        val tinyVividPink = hex(0xDC6F93, 10)
        assertSameHue(largeBrown, pickBaseColor(listOf(largeBrown, tinyVividPink)))
    }

    @Test
    fun `胜出色系内偏向挑选深色的颜色`() {
        // CLANNAD 实测：红色头发与肤色同属暖色组，面积更大的浅肤色不应胜过深红
        val peach = hex(0xF0B08D, 81)
        val red = hex(0x9E4848, 61)
        val coral = hex(0xCA6556, 26)
        assertSameHue(red, pickBaseColor(listOf(peach, red, coral)))
    }

    @Test
    fun `肉眼近白的淡色区域面积占优时base只带轻微色调`() {
        // AIR 实测：淡灰蓝羽翼色度低于中性阈值，面积约为金发色系的 1.7 倍
        val paleBlue = hex(0xCCD4EC, 100)
        val paleBlue2 = hex(0xBFC8DE, 45)
        val gold = hex(0xC28548, 84)
        val base = pickBaseColor(listOf(paleBlue, paleBlue2, gold))
        assertEquals(0.06f, chromaOf(base), 0.01f)
        assertTrue("应带淡蓝色调，实际 rgb=(${base.red},${base.green},${base.blue})", base.blue > base.red)
    }

    @Test
    fun `灰白面积仅略大于彩色时仍取彩色`() {
        // 红白对半封面实测：灰白 0.37 对红色 0.32
        val gray = hex(0xC4C4C4, 37)
        val red = hex(0xA02D26, 32)
        assertSameHue(red, pickBaseColor(listOf(gray, red)))
    }

    @Test
    fun `纯灰中性组输出无色相的灰`() {
        val base = pickBaseColor(listOf(hex(0x808080, 100), hex(0xC28548, 10)))
        assertEquals(base.red, base.green, 0.002f)
        assertEquals(base.green, base.blue, 0.002f)
    }

    @Test
    fun `高饱和浅色压到区间内且色相不变不会变艳`() {
        // 風の夢实测：淡粉底色压明度时不能被放大成大红
        val lightPink = hex(0xF4ACAC, 100)
        val base = pickBaseColor(listOf(lightPink))
        assertEquals(0.5f, hslOf(base)[2], 0.01f)
        assertTrue(chromaOf(base) <= 0.30f + 0.01f)
        assertSameHue(lightPink, base)
    }

    @Test
    fun `低饱和暗色提到区间内且色相不变`() {
        val darkBrown = hex(0x391F19, 100)
        val base = pickBaseColor(listOf(darkBrown))
        assertEquals(0.2f, hslOf(base)[2], 0.01f)
        assertTrue(chromaOf(base) >= 0.15f - 0.01f)
        assertSameHue(darkBrown, base)
    }

    @Test
    fun `暗色低色度候选不被误判为中性且纯黑不参与分组`() {
        // 暗色封面实测：暗褐色度只有 0.11，按绝对色度门槛会被当成灰
        val darkBrown = hex(0x2F2614, 100)
        val gray = hex(0x808080, 60)
        val black = hex(0x040404, 1000)
        assertSameHue(darkBrown, pickBaseColor(listOf(darkBrown, gray, black)))
    }

    @Test
    fun `纸白不参与分组`() {
        val paper = hex(0xFCFCF4, 1000)
        val red = hex(0x9E4848, 10)
        assertSameHue(red, pickBaseColor(listOf(paper, red)))
    }

    @Test
    fun `无候选时base回退到FallbackBase`() {
        assertEquals(FallbackBase, pickBaseColor(emptyList()))
        assertEquals(FallbackBase, pickBaseColor(listOf(hex(0xFCFCFC, 100))))
    }

    @Test
    fun `近乎纯白的封面不会被HSL饱和度放大误判成彩色`() {
        // 复现的场景：肉眼是纯白封面，量化后落在两个相邻的 5-bit 桶里，
        // 桶中心还原出的 swatch 是 (244,252,252) 和 (252,252,252)——色度只有约 0.03，
        // 但 HSL 饱和度会被放大到 0.5 以上，足以被旧的饱和度阈值(0.08)误判成"有色"。
        // r 分量特意控制在 250 以内，避免撞上默认 filter 的纯白排除线（r>250&&g>250&&b>250）
        val pixels = IntArray(300) { i -> if (i < 150) argb(240, 248, 248) else argb(248, 248, 248) }
        val swatches = medianCutQuantize(pixels, 64, ::defaultVibrantFilter)
        assertEquals(2, swatches.size)
        assertTrue("这组 swatch 的 HSL 饱和度应该被放大到明显偏高", swatches.any { it.hsl[1] > 0.3f })

        // 色度判定不应该被上面那种放大误导，仍然正确识别为灰阶信号
        assertTrue(isGrayscaleSwatches(swatches))

        val base = pickBaseColor(swatches)
        assertEquals(base.red, base.green, 0.001f)
        assertEquals(base.green, base.blue, 0.001f)
    }

    @Test
    fun `色度判定不受HSL饱和度放大影响`() {
        // 构造一个 HSL 饱和度会被放大到很高、但绝对色度很小的近白色 swatch
        val nearWhite = VibrantSwatch(androidx.compose.ui.graphics.Color(253 / 255f, 255 / 255f, 254 / 255f), 100)
        assertTrue(nearWhite.hsl[1] > 0.3f)
        assertTrue(nearWhite.chroma < 0.06f)
        assertTrue(isGrayscaleSwatches(listOf(nearWhite)))
    }
}
