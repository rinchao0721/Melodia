package com.lin0721.linmusic.core.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache

// 透明封面检测与状态缓存
object CoverContourShadowHelper {
    private const val CACHE_SIZE = 32

    private val transparencyCache = LruCache<String, Boolean>(CACHE_SIZE)

    fun isTransparent(url: String): Boolean? {
        if (url.isBlank()) return null
        return synchronized(transparencyCache) { transparencyCache.get(url) }
    }

    fun markTransparency(url: String, isTransparent: Boolean) {
        if (url.isBlank()) return
        synchronized(transparencyCache) { transparencyCache.put(url, isTransparent) }
    }

    // 采样边缘判定是否为透明封面
    fun detectIsTransparentCover(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return false

        val samplePoints = listOf(
            Pair(width * 0.02f, height * 0.02f),
            Pair(width * 0.98f, height * 0.02f),
            Pair(width * 0.02f, height * 0.98f),
            Pair(width * 0.98f, height * 0.98f),
            Pair(width * 0.05f, height * 0.05f),
            Pair(width * 0.95f, height * 0.05f),
            Pair(width * 0.05f, height * 0.95f),
            Pair(width * 0.95f, height * 0.95f)
        )

        var transparentCount = 0
        for ((px, py) in samplePoints) {
            val x = px.toInt().coerceIn(0, width - 1)
            val y = py.toInt().coerceIn(0, height - 1)
            val pixel = bitmap.getPixel(x, y)
            val alpha = Color.alpha(pixel)
            if (alpha < 180) {
                transparentCount++
            }
        }
        return transparentCount >= 4
    }
}
