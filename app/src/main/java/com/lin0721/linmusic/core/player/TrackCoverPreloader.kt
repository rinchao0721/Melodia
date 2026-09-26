package com.lin0721.linmusic.core.player

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest

// 提前把封面塞进 Coil 缓存，切歌时直接命中避免空白
class TrackCoverPreloader(private val context: Context) {

    fun preload(coverUrl: String) {
        if (coverUrl.isBlank()) return
        val imageRequest = ImageRequest.Builder(context)
            .data(coverUrl)
            .build()
        SingletonImageLoader.get(context).enqueue(imageRequest)
    }
}
