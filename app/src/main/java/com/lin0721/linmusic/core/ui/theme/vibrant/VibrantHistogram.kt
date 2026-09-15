package com.lin0721.linmusic.core.ui.theme.vibrant

// 5-bit 精度直方图，对应 vibrant-image/src/histogram.ts 的 sigBits=5 量化精度。
// pixels 是 ARGB_8888 packed int 数组（bitmap.getPixels 的原始输出）
internal class VibrantHistogram(
    pixels: IntArray,
    filter: (r: Int, g: Int, b: Int, a: Int) -> Boolean,
) {
    companion object {
        const val SIG_BITS = 5
        const val RSHIFT = 8 - SIG_BITS
    }

    val hist = IntArray(1 shl (3 * SIG_BITS))
    var rMin = Int.MAX_VALUE
        private set
    var rMax = 0
        private set
    var gMin = Int.MAX_VALUE
        private set
    var gMax = 0
        private set
    var bMin = Int.MAX_VALUE
        private set
    var bMax = 0
        private set

    init {
        for (pixel in pixels) {
            val a = (pixel ushr 24) and 0xFF
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF
            if (!filter(r, g, b, a)) continue

            val rq = r ushr RSHIFT
            val gq = g ushr RSHIFT
            val bq = b ushr RSHIFT
            hist[colorIndex(rq, gq, bq)]++

            if (rq > rMax) rMax = rq
            if (rq < rMin) rMin = rq
            if (gq > gMax) gMax = gq
            if (gq < gMin) gMin = gq
            if (bq > bMax) bMax = bq
            if (bq < bMin) bMin = bq
        }
    }

    // 没有任何像素通过 filter 时 rMin 停在初始值，永远 > rMax，用来判断"空直方图"
    fun isEmpty(): Boolean = rMin > rMax

    fun colorIndex(r: Int, g: Int, b: Int): Int = (r shl (2 * SIG_BITS)) + (g shl SIG_BITS) + b
}
