package com.lin0721.linmusic.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// hue 取值 0..360，saturation 与 value 取值 0..1
data class Hsv(val hue: Float, val saturation: Float, val value: Float) {
    fun toColor(): Color = Color.hsv(
        hue = hue.coerceIn(0f, 360f).let { if (it >= 360f) 0f else it },
        saturation = saturation.coerceIn(0f, 1f),
        value = value.coerceIn(0f, 1f)
    )
}

fun Color.toHsv(): Hsv {
    val maxChannel = max(red, max(green, blue))
    val minChannel = min(red, min(green, blue))
    val delta = maxChannel - minChannel
    val hue = when {
        delta == 0f -> 0f
        maxChannel == red -> 60f * (((green - blue) / delta).mod(6f))
        maxChannel == green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }
    val saturation = if (maxChannel == 0f) 0f else delta / maxChannel
    return Hsv(hue, saturation, maxChannel)
}

// 只接受 #RRGGBB 或 RRGGBB，忽略首尾空白；非法值返回 null
fun parseRgbHex(text: String): Color? {
    val body = text.trim().removePrefix("#")
    if (body.length != 6 || body.any { it.digitToIntOrNull(16) == null }) return null
    val rgb = body.toLong(16)
    return Color(
        red = ((rgb shr 16) and 0xFF).toInt(),
        green = ((rgb shr 8) and 0xFF).toInt(),
        blue = (rgb and 0xFF).toInt()
    )
}

fun Color.toRgbHex(): String {
    fun channel(value: Float) = (value.coerceIn(0f, 1f) * 255f).roundToInt().toString(16).padStart(2, '0')
    return "#" + (channel(red) + channel(green) + channel(blue)).uppercase()
}

// WCAG 对比度，取值 1..21
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

// 主色上的文字色：白、黑中对比度更高者
fun contentColorOn(background: Color): Color =
    if (contrastRatio(Color.White, background) >= contrastRatio(Color.Black, background)) Color.White else Color.Black
