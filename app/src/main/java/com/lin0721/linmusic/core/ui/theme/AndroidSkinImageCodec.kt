package com.lin0721.linmusic.core.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.ui.skin.SkinImageCodec
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "AndroidSkinImageCodec"

class AndroidSkinImageCodec : SkinImageCodec {

    override fun readSize(file: File): IntSize? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        return if (options.outWidth > 0 && options.outHeight > 0) IntSize(options.outWidth, options.outHeight) else null
    }

    override fun decode(file: File, maxSide: Int): ImageBitmap? {
        val size = readSize(file) ?: return null
        val longest = max(size.width, size.height)
        // 先按 2 的幂粗采样，再精确缩放到目标尺寸
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = try {
            BitmapFactory.decodeFile(file.path, options)
        } catch (e: OutOfMemoryError) {
            AppLogger.w(TAG, "解码图片内存不足: ${file.name}", e)
            null
        } ?: return null
        val scale = maxSide.toFloat() / max(decoded.width, decoded.height)
        if (scale >= 1f) return decoded.asImageBitmap()
        val width = (decoded.width * scale).roundToInt().coerceAtLeast(1)
        val height = (decoded.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled.asImageBitmap()
    }

    override fun fromPixels(width: Int, height: Int, argb: IntArray): ImageBitmap? {
        if (width <= 0 || height <= 0 || argb.size < width * height) return null
        return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
