package com.lin0721.linmusic.core.ui.theme.vibrant

import com.lin0721.linmusic.core.ui.theme.FallbackBase
import com.lin0721.linmusic.core.ui.theme.isGrayscaleSwatches
import com.lin0721.linmusic.core.ui.theme.pickBaseColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `generator对双色图像只标记真实命中的分类不合成假色`() {
        val pixels = IntArray(400) { i -> if (i < 300) argb(230, 30, 30) else argb(30, 30, 230) }
        val swatches = medianCutQuantize(pixels, 64, ::defaultVibrantFilter)

        val palette = generateVibrantPalette(swatches)
        assertNotNull(palette.vibrant)
        // 两个候选饱和度都超过 Muted 上限、明度都落不进 Light/Dark 区间：
        // 不再借用 Vibrant 的色相合成假的 Muted/LightVibrant/DarkVibrant，如实保持 null
        assertNull(palette.muted)
        assertNull(palette.lightVibrant)
        assertNull(palette.darkVibrant)
    }

    @Test
    fun `base优先取Vibrant分类`() {
        val vibrant = VibrantSwatch(androidx.compose.ui.graphics.Color(0.9f, 0.1f, 0.1f), 100)
        val muted = VibrantSwatch(androidx.compose.ui.graphics.Color(0.4f, 0.35f, 0.3f), 50)
        val palette = VibrantPalette(
            vibrant = vibrant, lightVibrant = null, darkVibrant = null,
            muted = muted, lightMuted = null, darkMuted = null,
        )
        assertEquals(vibrant.rgb, pickBaseColor(palette))
    }

    @Test
    fun `六个候选饱和度都过低时base退化为中性灰`() {
        fun lowSaturation(l: Float) = VibrantSwatch(
            androidx.compose.ui.graphics.Color(l, l, l), 10,
        )
        val palette = VibrantPalette(
            vibrant = lowSaturation(0.5f), lightVibrant = null, darkVibrant = null,
            muted = lowSaturation(0.5f), lightMuted = null, darkMuted = null,
        )
        val base = pickBaseColor(palette)
        assertEquals(base.red, base.green, 0.001f)
        assertEquals(base.green, base.blue, 0.001f)
    }

    @Test
    fun `空色板时base回退到FallbackBase`() {
        val empty = VibrantPalette(null, null, null, null, null, null)
        assertEquals(FallbackBase, pickBaseColor(empty))
    }

    @Test
    fun `候选明度过低时自动提亮且不改变色相方向`() {
        // 黑白基调封面常见的暗色候选：有色度（不会触发整图灰阶判定），但明度很低，
        // 原样使用肉眼看基本就是纯黑
        val darkRed = VibrantSwatch(androidx.compose.ui.graphics.Color(40 / 255f, 10 / 255f, 10 / 255f), 100)
        val palette = VibrantPalette(
            vibrant = darkRed, lightVibrant = null, darkVibrant = null,
            muted = null, lightMuted = null, darkMuted = null,
        )
        val base = pickBaseColor(palette)
        val hsl = rgbToHsl((base.red * 255f).toInt(), (base.green * 255f).toInt(), (base.blue * 255f).toInt())

        assertTrue("提亮后明度应该比原来的 0.098 明显更高", hsl[2] > 0.098f + 0.1f)
        // 用线性 RGB 朝白混合而不是重建 HSL 色相：验证红色分量依然是三个通道里最高的，
        // 没有在提亮过程中变成别的颜色（这正是之前用 hslToRgb 重建色相导致"变蓝"的回归点）
        assertTrue("提亮后应该还是偏红，不能变成别的颜色，实际 rgb=(${base.red},${base.green},${base.blue})",
            base.red > base.green && base.red > base.blue)
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

        val palette = generateVibrantPalette(swatches)
        val base = pickBaseColor(palette)
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
