package com.lin0721.linmusic.core.ui.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.lin0721.linmusic.core.log.AppLogger
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "SkinImages"

// 图片解码由平台实现；失败一律返回 null，不抛出
interface SkinImageCodec {
    // 只读文件头取尺寸
    fun readSize(file: File): IntSize?

    // 解码并缩放到最长边不超过 maxSide
    fun decode(file: File, maxSide: Int): ImageBitmap?

    fun fromPixels(width: Int, height: Int, argb: IntArray): ImageBitmap?

    fun decodePixels(file: File, maxSide: Int): SkinPixels? {
        val image = decode(file, maxSide) ?: return null
        val argb = IntArray(image.width * image.height)
        image.readPixels(argb)
        return SkinPixels(image.width, image.height, argb)
    }
}

// argb 按行排列，长度为 width * height
class SkinPixels(val width: Int, val height: Int, val argb: IntArray)

object SkinImages {
    const val MAX_IMAGE_SIDE = 4096

    private const val SHARP_MAX_SIDE = 2560
    private const val BLURRED_MAX_SIDE = 480
    private const val AVERAGE_SAMPLE_SIDE = 32

    // 皮肤 blur 按约 1600px 宽的窗口给出，缩小解码后按比例换算半径
    private const val BLUR_REFERENCE_SIDE = 1600f

    fun averageColor(file: File, codec: SkinImageCodec): Color? {
        val pixels = codec.decodePixels(file, AVERAGE_SAMPLE_SIDE)?.argb ?: return null
        if (pixels.isEmpty()) return null
        var red = 0L
        var green = 0L
        var blue = 0L
        pixels.forEach { argb ->
            red += (argb shr 16) and 0xFF
            green += (argb shr 8) and 0xFF
            blue += argb and 0xFF
        }
        val count = pixels.size
        return Color(red = (red / count).toInt(), green = (green / count).toInt(), blue = (blue / count).toInt())
    }

    // 背景图：需要模糊时先缩小解码再做三次盒式模糊，放大绘制时自然平滑
    fun loadBackdrop(skin: Skin, codec: SkinImageCodec): ImageBitmap? {
        val background = skin.background ?: return null
        val file = skin.assetFile(background.image) ?: return null
        return try {
            if (background.blur <= 0f) {
                codec.decode(file, SHARP_MAX_SIDE)
            } else {
                val small = codec.decodePixels(file, BLURRED_MAX_SIDE) ?: return null
                val scale = max(small.width, small.height) / BLUR_REFERENCE_SIDE
                val radius = max(1, (background.blur * scale).roundToInt())
                repeat(3) { boxBlur(small.argb, small.width, small.height, radius) }
                codec.fromPixels(small.width, small.height, small.argb)
            }
        } catch (e: OutOfMemoryError) {
            AppLogger.w(TAG, "背景图内存不足: ${skin.id}", e)
            null
        }
    }

    // 横纵两趟滑动窗口均值，边缘按就近像素延伸
    fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int) {
        if (width <= 0 || height <= 0 || radius <= 0 || pixels.size < width * height) return
        val buffer = IntArray(max(width, height))
        for (y in 0 until height) blurLine(pixels, y * width, 1, width, radius, buffer)
        for (x in 0 until width) blurLine(pixels, x, width, height, radius, buffer)
    }

    private fun blurLine(pixels: IntArray, start: Int, step: Int, length: Int, radius: Int, buffer: IntArray) {
        val window = radius * 2 + 1
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        fun at(index: Int): Int = pixels[start + index.coerceIn(0, length - 1) * step]
        for (i in -radius..radius) {
            val c = at(i)
            a += (c ushr 24) and 0xFF
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        for (i in 0 until length) {
            buffer[i] = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val outgoing = at(i - radius)
            val incoming = at(i + radius + 1)
            a += ((incoming ushr 24) and 0xFF) - ((outgoing ushr 24) and 0xFF)
            r += ((incoming shr 16) and 0xFF) - ((outgoing shr 16) and 0xFF)
            g += ((incoming shr 8) and 0xFF) - ((outgoing shr 8) and 0xFF)
            b += (incoming and 0xFF) - (outgoing and 0xFF)
        }
        for (i in 0 until length) pixels[start + i * step] = buffer[i]
    }
}
