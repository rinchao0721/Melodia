package com.lin0721.linmusic.desktop.ui.palette

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private const val SHARE_COLOR_COUNT = 64
private const val MIN_ACCENT_CHROMA = 0.06f
// 色相按 0..1 计，约 30 度
private const val MIN_ACCENT_HUE_GAP = 30f / 360f

// 分享卡片用的三色：主色、与主色色相拉开的次色、最深色；与移动端 extractShareColorsFromUrl 同一规则
fun extractShareColors(pixels: IntArray): List<Color> {
    val quantized = medianCutQuantize(pixels, SHARE_COLOR_COUNT, ::defaultVibrantFilter)
    if (quantized.isEmpty() || isGrayscaleSwatches(quantized)) return shareColorsFrom(FallbackBase)
    val base = pickBaseColor(quantized)
    val baseHue = VibrantSwatch(base, 1).hsl[0]
    val usable = quantized.filter { it.hsl[2] in 0.12f..0.85f && it.chroma >= MIN_ACCENT_CHROMA }
    val accent = usable
        .filter { hueDistance(it.hsl[0], baseHue) > MIN_ACCENT_HUE_GAP }
        .maxByOrNull { it.population }?.rgb
        ?: lerp(base, Color.White, 0.35f)
    val deep = usable.minByOrNull { it.hsl[2] }?.rgb?.let { lerp(it, Color.Black, 0.2f) }
        ?: lerp(base, Color.Black, 0.45f)
    return listOf(base, accent, deep)
}

fun shareColorsFrom(base: Color): List<Color> =
    listOf(base, lerp(base, Color.White, 0.35f), lerp(base, Color.Black, 0.45f))
