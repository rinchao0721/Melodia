package com.lin0721.linmusic.desktop.platform.windows.taskbar

import com.sun.jna.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

class ThumbnailImagesTest {

    private fun solid(width: Int, height: Int, color: Color): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
            val g = createGraphics()
            g.color = color
            g.fillRect(0, 0, width, height)
            g.dispose()
        }

    @Test
    fun scaleToSquareProducesRequestedSize() {
        val scaled = scaleToSquare(solid(300, 200, Color.RED), 64)
        assertEquals(64, scaled.width)
        assertEquals(64, scaled.height)
        assertEquals(Color.RED.rgb, scaled.getRGB(32, 32))
    }

    // 非正方形源图按较短边居中裁切，不应出现黑边
    @Test
    fun scaleToSquareCropsInsteadOfLetterboxing() {
        val scaled = scaleToSquare(solid(400, 100, Color.GREEN), 40)
        assertEquals(Color.GREEN.rgb, scaled.getRGB(0, 20))
        assertEquals(Color.GREEN.rgb, scaled.getRGB(39, 20))
    }

    @Test
    fun scaleToSquareClampsNonPositiveSize() {
        val scaled = scaleToSquare(solid(10, 10, Color.BLUE), 0)
        assertEquals(1, scaled.width)
        assertEquals(1, scaled.height)
    }

    @Test
    fun placeholderWorksWithoutIcon() {
        val image = placeholderCover(null, 32)
        assertEquals(32, image.width)
        assertEquals(0xFF, image.getRGB(0, 0) ushr 24)
    }

    @Test
    fun placeholderDrawsIconInCenter() {
        val image = placeholderCover(solid(8, 8, Color.WHITE), 64)
        assertEquals(Color.WHITE.rgb, image.getRGB(32, 32))
        assertNotEquals(Color.WHITE.rgb, image.getRGB(2, 2))
    }

    @Test
    fun opaquePixelsForceFullAlpha() {
        val image = BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0x00112233)
        image.setRGB(1, 0, 0x80445566.toInt())
        val pixels = opaqueArgbPixels(image)
        assertEquals(0xFF112233.toInt(), pixels[0])
        assertEquals(0xFF445566.toInt(), pixels[1])
    }

    // 图标字形应有像素，且深浅主题颜色不同
    @Test
    fun iconsDrawVisiblePixelsInThemeColor() {
        for (icon in ThumbIcon.entries) {
            val dark = renderThumbIcon(icon, 24, lightTheme = false)
            val light = renderThumbIcon(icon, 24, lightTheme = true)
            assertEquals(24 * 24, dark.size)
            assertTrue("$icon 在深色主题下应有可见像素", dark.any { it ushr 24 != 0 })
            val opaqueDark = dark.first { it ushr 24 == 0xFF }
            val opaqueLight = light.first { it ushr 24 == 0xFF }
            assertEquals(0xFFFFFFFF.toInt(), opaqueDark)
            assertEquals(0xFF202020.toInt(), opaqueLight)
        }
    }

    // 预乘后颜色分量不得超过 alpha
    @Test
    fun iconPixelsArePremultiplied() {
        val pixels = renderThumbIcon(ThumbIcon.PLAY, 32, lightTheme = false)
        for (p in pixels) {
            val alpha = p ushr 24
            assertTrue(((p shr 16) and 0xFF) <= alpha)
            assertTrue(((p shr 8) and 0xFF) <= alpha)
            assertTrue((p and 0xFF) <= alpha)
        }
    }

    @Test
    fun createEmptyDibCreatesValidPointer() {
        assumeTrue("Requires Windows GDI", Platform.isWindows())
        val dib = GdiBitmaps.createEmptyDib()
        assertTrue(dib != null)
        dib?.let { GdiBitmaps.delete(it) }
    }
}
