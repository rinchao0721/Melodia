package com.lin0721.linmusic.core.ui.theme.vibrant

import androidx.compose.ui.graphics.Color

// 对应 node-vibrant 的 Swatch：一个量化后的代表色 + 该色在采样像素里的占比
data class VibrantSwatch(val rgb: Color, val population: Int) {
    val hsl: FloatArray by lazy {
        rgbToHsl((rgb.red * 255f).toInt(), (rgb.green * 255f).toInt(), (rgb.blue * 255f).toInt())
    }

    // 绝对色度：RGB 分量最大最小值之差（0..1），不受明度影响。
    // HSL 饱和度在明度趋近 0 或 1 时会被分母 (1-|2L-1|) 放大——纯白封面上几级压缩噪声
    // 就能被算成接近 1 的"高饱和度"，色度没有这个问题，更适合做灰阶判定
    val chroma: Float by lazy {
        maxOf(rgb.red, rgb.green, rgb.blue) - minOf(rgb.red, rgb.green, rgb.blue)
    }
}
