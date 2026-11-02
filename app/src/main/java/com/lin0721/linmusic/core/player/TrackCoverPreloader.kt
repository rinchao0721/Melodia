package com.lin0721.linmusic.core.player

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest

// 提前把封面塞进 Coil 缓存，切歌时直接命中避免空白
class TrackCoverPreloader(private val context: Context) {

    fun preload(coverUrl: String) {
        if (coverUrl.isBlank()) return
        val sizedUrl = normalizeFullPlayerCoverUrl(coverUrl)
        val imageRequest = ImageRequest.Builder(context)
            .data(sizedUrl)
            .size(800, 800)
            .build()
        SingletonImageLoader.get(context).enqueue(imageRequest)
    }
}

// 全屏播放器与预取器统一在线封面规格，避免拉取数千像素原图并保证预取命中
internal fun normalizeFullPlayerCoverUrl(url: String): String {
    if (url.isBlank()) return ""
    val stripped = url.replace("?param=300y300", "")
    return if ((stripped.startsWith("http://") || stripped.startsWith("https://")) && !stripped.contains('?')) {
        "$stripped?param=800y800"
    } else {
        stripped
    }
}
