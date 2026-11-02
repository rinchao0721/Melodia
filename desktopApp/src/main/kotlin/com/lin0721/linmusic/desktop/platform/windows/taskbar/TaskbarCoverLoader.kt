package com.lin0721.linmusic.desktop.platform.windows.taskbar

import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage

private const val TAG = "TaskbarCoverLoader"

// 缩略图最大约 200 像素，高分屏下按 2.5 倍留余量
internal const val TASKBAR_COVER_SIZE_PX = 512

// 经 Coil 取封面（复用磁盘缓存），失败返回 null
internal object TaskbarCoverLoader {

    suspend fun load(url: String): BufferedImage? = try {
        withContext(Dispatchers.Default) {
            val context = PlatformContext.INSTANCE
            val request = ImageRequest.Builder(context).data(url).size(TASKBAR_COVER_SIZE_PX, TASKBAR_COVER_SIZE_PX).build()
            val image = SingletonImageLoader.get(context).execute(request).image ?: return@withContext null
            val bitmap = image.toBitmap().asComposeImageBitmap()
            if (bitmap.width <= 0 || bitmap.height <= 0) return@withContext null
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.readPixels(pixels)
            BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB).apply {
                setRGB(0, 0, bitmap.width, bitmap.height, pixels, 0, bitmap.width)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.d(TAG, "封面解码失败", e)
        null
    }
}
