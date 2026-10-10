package com.lin0721.linmusic.desktop.platform.native.linux.sni

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

// 解码带 alpha 的 PNG，生成 SNI 的 IconPixmap（ARGB32 大端，行优先）。
// 与 XEmbed 托盘不同，SNI 的 pixmap 支持逐像素透明，图标圆角外不会再被填白。
internal fun iconPixmapsFromPng(png: ByteArray, size: Int = 256): Array<IconPixmap> {
    val source = ImageIO.read(ByteArrayInputStream(png)) ?: return emptyArray()
    val image = if (source.width == size && source.height == size) {
        source
    } else {
        BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).also { target ->
            val graphics = target.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.drawImage(source, 0, 0, size, size, null)
            graphics.dispose()
        }
    }

    val pixels = ByteArray(size * size * 4)
    var index = 0
    for (y in 0 until size) {
        for (x in 0 until size) {
            val argb = image.getRGB(x, y)
            pixels[index++] = ((argb ushr 24) and 0xFF).toByte()
            pixels[index++] = ((argb ushr 16) and 0xFF).toByte()
            pixels[index++] = ((argb ushr 8) and 0xFF).toByte()
            pixels[index++] = (argb and 0xFF).toByte()
        }
    }
    return arrayOf(IconPixmap(size, size, pixels))
}
