package com.lin0721.linmusic.desktop.platform.windows.taskbar

import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

private const val PLACEHOLDER_SIZE = 512
private const val PLACEHOLDER_ICON_RATIO = 0.5
private val PlaceholderBackground = Color(0xFF141414.toInt(), true)
private val FrameBackground = Color(0xFF000000.toInt(), true)

private fun BufferedImage.graphicsForScaling() = createGraphics().apply {
    setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
}

// 按较短边居中裁成正方形后缩放到 size；不足 size 的源图同样放大，保证位图尺寸与请求一致
internal fun scaleToSquare(source: BufferedImage, size: Int): BufferedImage {
    val target = size.coerceAtLeast(1)
    val side = minOf(source.width, source.height).coerceAtLeast(1)
    val sx = (source.width - side) / 2
    val sy = (source.height - side) / 2
    val result = BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB)
    val g = result.graphicsForScaling()
    try {
        g.color = FrameBackground
        g.fillRect(0, 0, target, target)
        g.drawImage(source, 0, 0, target, target, sx, sy, sx + side, sy + side, null)
    } finally {
        g.dispose()
    }
    return result
}

// 封面缺失时的占位图：深色底 + 居中的应用图标；icon 为空时只有底色
internal fun placeholderCover(icon: BufferedImage?, size: Int = PLACEHOLDER_SIZE): BufferedImage {
    val result = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = result.graphicsForScaling()
    try {
        g.color = PlaceholderBackground
        g.fillRect(0, 0, size, size)
        if (icon != null) {
            val side = (size * PLACEHOLDER_ICON_RATIO).toInt()
            val offset = (size - side) / 2
            g.drawImage(icon, offset, offset, side, side, null)
        }
    } finally {
        g.dispose()
    }
    return result
}

// DIB 按 BGRA 小端存放，ARGB 的 Int 直接写入即可；强制不透明，避免预乘与否的歧义
internal fun opaqueArgbPixels(image: BufferedImage): IntArray {
    val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    for (i in pixels.indices) pixels[i] = pixels[i] or 0xFF000000.toInt()
    return pixels
}

// 工具栏图标：纯色形状，边缘半透明，按预乘 alpha 输出，供 GDI 图标使用
internal fun renderThumbIcon(icon: ThumbIcon, size: Int, lightTheme: Boolean): IntArray {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.color = if (lightTheme) Color(0x20, 0x20, 0x20) else Color.WHITE
        drawGlyph(g, icon, size.toDouble())
    } finally {
        g.dispose()
    }
    val pixels = image.getRGB(0, 0, size, size, null, 0, size)
    for (i in pixels.indices) pixels[i] = premultiply(pixels[i])
    return pixels
}

private fun premultiply(argb: Int): Int {
    val a = argb ushr 24
    if (a == 0xFF) return argb
    val r = ((argb shr 16) and 0xFF) * a / 255
    val g = ((argb shr 8) and 0xFF) * a / 255
    val b = (argb and 0xFF) * a / 255
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

private fun drawGlyph(g: java.awt.Graphics2D, icon: ThumbIcon, s: Double) {
    val pad = s * 0.2
    val inner = s - pad * 2
    val barWidth = inner * 0.18
    when (icon) {
        ThumbIcon.PLAY -> g.fill(triangle(pad + inner * 0.1, pad, inner * 0.9, inner, pointRight = true))
        ThumbIcon.PAUSE -> {
            g.fill(Rectangle2D.Double(pad + inner * 0.15, pad, inner * 0.25, inner))
            g.fill(Rectangle2D.Double(pad + inner * 0.6, pad, inner * 0.25, inner))
        }
        ThumbIcon.NEXT -> {
            g.fill(triangle(pad, pad, inner - barWidth, inner, pointRight = true))
            g.fill(Rectangle2D.Double(pad + inner - barWidth, pad, barWidth, inner))
        }
        ThumbIcon.PREVIOUS -> {
            g.fill(Rectangle2D.Double(pad, pad, barWidth, inner))
            g.fill(triangle(pad + barWidth, pad, inner - barWidth, inner, pointRight = false))
        }
    }
}

private fun triangle(x: Double, y: Double, w: Double, h: Double, pointRight: Boolean): Path2D.Double =
    Path2D.Double().apply {
        if (pointRight) {
            moveTo(x, y)
            lineTo(x + w, y + h / 2)
            lineTo(x, y + h)
        } else {
            moveTo(x + w, y)
            lineTo(x, y + h / 2)
            lineTo(x + w, y + h)
        }
        closePath()
    }
