package com.lin0721.linmusic.core.ui.theme.vibrant

// RGB(0..255) 与 HSL(0..1) 互转，公式照抄 node-vibrant 的 @vibrant/color/converter.ts，
// 保证跟上游算法在色彩空间换算上完全对齐

internal fun rgbToHsl(r: Int, g: Int, b: Int): FloatArray {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f
    val max = maxOf(rf, gf, bf)
    val min = minOf(rf, gf, bf)
    var h = 0f
    var s = 0f
    val l = (max + min) / 2f
    if (max != min) {
        val d = max - min
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            rf -> (gf - bf) / d + (if (gf < bf) 6f else 0f)
            gf -> (bf - rf) / d + 2f
            else -> (rf - gf) / d + 4f
        }
        h /= 6f
    }
    return floatArrayOf(h, s, l)
}

internal fun hslToRgb(h: Float, s: Float, l: Float): IntArray {
    fun hue2rgb(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    if (s == 0f) {
        val v = (l * 255f).toInt()
        return intArrayOf(v, v, v)
    }
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    val r = hue2rgb(p, q, h + 1f / 3f)
    val g = hue2rgb(p, q, h)
    val b = hue2rgb(p, q, h - 1f / 3f)
    return intArrayOf((r * 255f).toInt(), (g * 255f).toInt(), (b * 255f).toInt())
}
