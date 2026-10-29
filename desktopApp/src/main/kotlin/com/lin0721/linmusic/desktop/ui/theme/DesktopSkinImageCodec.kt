package com.lin0721.linmusic.desktop.ui.theme

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.ui.skin.SkinImageCodec
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "DesktopSkinImageCodec"

class DesktopSkinImageCodec : SkinImageCodec {

    override fun readSize(file: File): IntSize? = try {
        Codec.makeFromData(Data.makeFromBytes(file.readBytes())).let { IntSize(it.width, it.height) }
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    override fun decode(file: File, maxSide: Int): ImageBitmap? = try {
        val image = Image.makeFromEncoded(file.readBytes())
        val scale = minOf(1f, maxSide.toFloat() / max(image.width, image.height))
        if (scale >= 1f) {
            image.toComposeImageBitmap()
        } else {
            val width = (image.width * scale).roundToInt().coerceAtLeast(1)
            val height = (image.height * scale).roundToInt().coerceAtLeast(1)
            val surface = Surface.makeRasterN32Premul(width, height)
            surface.canvas.drawImageRect(
                image,
                Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                Rect.makeWH(width.toFloat(), height.toFloat()),
                FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
                null,
                true
            )
            surface.makeImageSnapshot().toComposeImageBitmap()
        }
    } catch (e: IOException) {
        AppLogger.w(TAG, "读取图片失败: ${file.name}", e)
        null
    } catch (e: IllegalArgumentException) {
        AppLogger.w(TAG, "解码图片失败: ${file.name}", e)
        null
    }

    // ARGB 整数按小端写出即 BGRA 字节序
    override fun fromPixels(width: Int, height: Int, argb: IntArray): ImageBitmap? {
        if (width <= 0 || height <= 0 || argb.size < width * height) return null
        val bytes = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            val color = argb[i]
            bytes[i * 4] = (color and 0xFF).toByte()
            bytes[i * 4 + 1] = ((color shr 8) and 0xFF).toByte()
            bytes[i * 4 + 2] = ((color shr 16) and 0xFF).toByte()
            bytes[i * 4 + 3] = ((color ushr 24) and 0xFF).toByte()
        }
        val bitmap = Bitmap()
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        if (!bitmap.installPixels(info, bytes, width * 4)) return null
        return bitmap.asComposeImageBitmap()
    }
}
