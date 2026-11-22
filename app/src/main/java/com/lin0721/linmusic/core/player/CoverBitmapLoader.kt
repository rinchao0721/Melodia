package com.lin0721.linmusic.core.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.IOException
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val COVER_SIZE_PX = 512

// 通知大图标与系统媒体面板共用，取缩放图且同一地址只下载一次
@OptIn(UnstableApi::class)
internal class CoverBitmapLoader(
    context: Context,
    private val scope: CoroutineScope
) : BitmapLoader {

    private val appContext = context.applicationContext
    private val lock = Any()
    private var lastUri: String? = null
    private var lastFuture: ListenableFuture<Bitmap>? = null

    override fun supportsMimeType(mimeType: String): Boolean = Util.isBitmapFactorySupportedMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (bitmap != null) future.set(bitmap) else future.setException(IOException("封面数据无法解码"))
            } catch (e: CancellationException) {
                future.cancel(false)
                throw e
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val key = uri.toString()
        synchronized(lock) {
            val cached = lastFuture
            if (cached != null && lastUri == key && !isFailed(cached)) return cached
            val future = SettableFuture.create<Bitmap>()
            lastUri = key
            lastFuture = future
            scope.launch(Dispatchers.IO) { fetch(uri, future) }
            return future
        }
    }

    private suspend fun fetch(uri: Uri, future: SettableFuture<Bitmap>) {
        try {
            val request = ImageRequest.Builder(appContext)
                .data(sizedModel(uri))
                .size(COVER_SIZE_PX, COVER_SIZE_PX)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .allowHardware(false)
                .build()
            val bitmap = SingletonImageLoader.get(appContext).execute(request).image?.toBitmap()
            if (bitmap != null) future.set(bitmap) else future.setException(IOException("封面加载失败 uri=$uri"))
        } catch (e: CancellationException) {
            future.cancel(false)
            throw e
        } catch (e: Exception) {
            future.setException(e)
        }
    }

    // 与全屏播放器、下一首预取同一规格，直接命中磁盘缓存
    private fun sizedModel(uri: Uri): Any {
        val raw = uri.toString()
        val isHttp = raw.startsWith("http://") || raw.startsWith("https://")
        return if (isHttp) normalizeFullPlayerCoverUrl(raw) else uri
    }

    private fun isFailed(future: ListenableFuture<Bitmap>): Boolean {
        if (!future.isDone) return false
        if (future.isCancelled) return true
        return try {
            Futures.getDone(future)
            false
        } catch (e: ExecutionException) {
            true
        }
    }
}
