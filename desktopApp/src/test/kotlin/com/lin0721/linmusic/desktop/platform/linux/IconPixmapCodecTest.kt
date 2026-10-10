package com.lin0721.linmusic.desktop.platform.linux

import com.lin0721.linmusic.desktop.platform.native.linux.sni.IconPixmap
import com.lin0721.linmusic.desktop.platform.native.linux.sni.SniToolTip
import com.lin0721.linmusic.desktop.platform.native.linux.sni.iconPixmapsFromPng
import org.freedesktop.dbus.Marshalling
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

// SNI 图标编码的单元测试：托盘宿主按 a(iiay) 解析 pixmap，
// 像素须为 ARGB 大端、行优先，且保留 alpha（XEmbed 托盘丢掉的圆角透明靠它恢复）。
class IconPixmapCodecTest {

    @Test
    fun `保留源尺寸并生成 ARGB 大端像素`() {
        val png = pngOf(8) { image ->
            image.setRGB(0, 0, 0x00000000)          // 圆角外：全透明
            image.setRGB(7, 0, 0xFF000000.toInt())  // 不透明黑
            image.setRGB(0, 7, 0x80FF0000.toInt())  // 半透明红
            image.setRGB(7, 7, 0xFFFFFFFF.toInt())  // 不透明白
            image.setRGB(3, 4, 0xFF123456.toInt())
        }

        val pixmaps = iconPixmapsFromPng(png, size = 8)

        assertEquals(1, pixmaps.size)
        val pixmap = pixmaps.first()
        assertEquals(8, pixmap.width)
        assertEquals(8, pixmap.height)
        assertEquals(8 * 8 * 4, pixmap.pixels.size)
        assertEquals(listOf(0x00, 0x00, 0x00, 0x00), pixmap.pixelAt(0, 0))
        assertEquals(listOf(0xFF, 0x00, 0x00, 0x00), pixmap.pixelAt(7, 0))
        assertEquals(listOf(0x80, 0xFF, 0x00, 0x00), pixmap.pixelAt(0, 7))
        assertEquals(listOf(0xFF, 0xFF, 0xFF, 0xFF), pixmap.pixelAt(7, 7))
        assertEquals(listOf(0xFF, 0x12, 0x34, 0x56), pixmap.pixelAt(3, 4))
    }

    @Test
    fun `尺寸不符时缩放为目标尺寸`() {
        val png = pngOf(64) { it.setRGB(0, 0, 0x00000000) }

        val pixmap = iconPixmapsFromPng(png, size = 32).single()

        assertEquals(32, pixmap.width)
        assertEquals(32, pixmap.height)
        assertEquals(32 * 32 * 4, pixmap.pixels.size)
    }

    @Test
    fun `无法解码时返回空数组`() {
        assertEquals(0, iconPixmapsFromPng(byteArrayOf(1, 2, 3)).size)
    }

    @Test
    fun `结构体字段带 Position 且推导出 SNI 签名`() {
        // dbus-java 5 靠 @Position 收集结构体字段，缺注解会得到空参数数组，
        // 线路上表现为 Properties.Get 抛 IndexOutOfBounds（托盘宿主随后丢弃图标）
        val pixmap = IconPixmap(2, 2, ByteArray(16))
        assertEquals(3, pixmap.parameters.size)
        assertEquals(2, pixmap.parameters[0])
        assertEquals("a(iiay)", Marshalling.getDBusType(Array<IconPixmap>::class.java).single())

        val toolTip = SniToolTip("", arrayOf(pixmap), "Melodia", "提示")
        assertEquals(4, toolTip.parameters.size)
        assertEquals("(sa(iiay)ss)", Marshalling.getDBusType(SniToolTip::class.java).single())
    }

    private fun pngOf(size: Int, paint: (BufferedImage) -> Unit): ByteArray {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        paint(image)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
}

private fun IconPixmap.pixelAt(x: Int, y: Int): List<Int> {
    val index = (y * width + x) * 4
    return (0..3).map { pixels[index + it].toInt() and 0xFF }
}
