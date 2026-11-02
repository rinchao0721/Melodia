package com.lin0721.linmusic.desktop.ui.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private const val TAG = "ShareCardImageIO"

internal object ShareCardImageIO {
    suspend fun loadImage(url: String, size: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            val context = PlatformContext.INSTANCE
            val request = ImageRequest.Builder(context).data(url.substringBefore("?param=")).size(size, size).build()
            SingletonImageLoader.get(context).execute(request).image?.toBitmap()?.asComposeImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "加载分享封面失败", e)
            null
        }
    }

    suspend fun savePng(image: ImageBitmap, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ImageIO.write(image.toAwtImage(), "png", file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存分享图片失败", e)
            false
        }
    }

    // 剪贴板图片不支持透明通道，先画到不透明位图上
    fun copyToClipboard(image: ImageBitmap): Boolean = try {
        val source = image.toAwtImage()
        val opaque = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        opaque.createGraphics().apply {
            drawImage(source, 0, 0, null)
            dispose()
        }
        Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageSelection(opaque), null)
        true
    } catch (e: Exception) {
        AppLogger.e(TAG, "复制分享图片失败", e)
        false
    }

    fun safeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]+"), "_").trim().take(60).ifBlank { "Melodia" }
}

private class ImageSelection(private val image: Image) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
        return image
    }
}
