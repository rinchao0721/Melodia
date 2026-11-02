package com.lin0721.linmusic.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

// 沉浸区铺底与光斑的目标相对亮度：铺底与白字对比度约 4.9，光斑约 3.9
private const val IMMERSIVE_FILL_LUMINANCE = 0.16f
private const val IMMERSIVE_BLOB_LUMINANCE = 0.22f
private const val IMMERSIVE_MIN_SATURATION = 0.35f
private const val IMMERSIVE_MAX_SATURATION = 0.6f
// 头部浅调目标相对亮度：次要文字 #5E5E5E 在其上对比度约 4.6
private const val HEADER_TINT_LUMINANCE = 0.7f
private const val HEADER_TINT_MAX_SATURATION = 0.6f
private const val ACCENT_TEXT_MIN_CONTRAST = 4.5f
// 浅色歌词页整页淡底与当前行深色调；深色调在头部浅调与淡底上对比度均不低于 4.5
private const val COVER_WASH_LUMINANCE = 0.86f
private const val COVER_WASH_MAX_SATURATION = 0.35f
private const val COVER_DEEP_LUMINANCE = 0.1f
// 低于此饱和度视为灰色封面，不强行提饱和，避免灰图染出色偏
private const val ACHROMATIC_SATURATION = 0.08f
private const val LUMINANCE_SEARCH_STEPS = 20

// hue 取值 0..360，saturation 与 lightness 取值 0..1
data class Hsl(val hue: Float, val saturation: Float, val lightness: Float) {
    fun toColor(): Color = Color.hsl(
        hue = hue.coerceIn(0f, 360f).let { if (it >= 360f) 0f else it },
        saturation = saturation.coerceIn(0f, 1f),
        lightness = lightness.coerceIn(0f, 1f)
    )
}

fun Color.toHsl(): Hsl {
    val maxChannel = max(red, max(green, blue))
    val minChannel = min(red, min(green, blue))
    val delta = maxChannel - minChannel
    val lightness = (maxChannel + minChannel) / 2f
    if (delta == 0f) return Hsl(0f, 0f, lightness)
    val hue = when (maxChannel) {
        red -> 60f * (((green - blue) / delta).mod(6f))
        green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }
    val saturation = delta / (1f - kotlin.math.abs(2f * lightness - 1f))
    return Hsl(hue, saturation.coerceIn(0f, 1f), lightness)
}

// 浅色主题沉浸区（播放页、歌词）铺底：保留色相，按相对亮度定明暗，黄绿与蓝紫在白字下观感一致
fun immersiveMidFill(base: Color): Color = toneAtLuminance(base, IMMERSIVE_FILL_LUMINANCE)

fun immersiveMidBlob(base: Color): Color = toneAtLuminance(base, IMMERSIVE_BLOB_LUMINANCE)

// 浅色主题页面头部底色：同色相浅调，按相对亮度定明暗，各色相浓淡观感一致且托得住次要文字
fun coverHeaderTint(base: Color): Color {
    val hsl = base.toHsl()
    val saturation = if (hsl.saturation < ACHROMATIC_SATURATION) hsl.saturation else min(hsl.saturation, HEADER_TINT_MAX_SATURATION)
    var low = 0f
    var high = 1f
    repeat(LUMINANCE_SEARCH_STEPS) {
        val mid = (low + high) / 2f
        if (Hsl(hsl.hue, saturation, mid).toColor().luminance() < HEADER_TINT_LUMINANCE) low = mid else high = mid
    }
    return Hsl(hsl.hue, saturation, high).toColor()
}

// 浅色歌词页整页铺底：同色相极淡色，压低饱和度避免大面积刺眼
fun coverWashTint(base: Color): Color {
    val hsl = base.toHsl()
    val saturation = if (hsl.saturation < ACHROMATIC_SATURATION) hsl.saturation else min(hsl.saturation, COVER_WASH_MAX_SATURATION)
    return Hsl(hsl.hue, saturation, lightnessAtLuminance(hsl.hue, saturation, COVER_WASH_LUMINANCE)).toColor()
}

// 浅色歌词页当前行：同色相深色调；灰色封面没有可用色相，返回 null 由调用方回退主色
fun coverDeepTone(base: Color): Color? =
    if (base.toHsl().saturation < ACHROMATIC_SATURATION) null else toneAtLuminance(base, COVER_DEEP_LUMINANCE)

// 主色在浅底上作文字时的可读版本：对比度不足 4.5 时保持色相与饱和度，降低明度直到达标
fun accentTextOn(accent: Color, background: Color): Color {
    if (contrastRatio(accent, background) >= ACCENT_TEXT_MIN_CONTRAST) return accent
    val hsl = accent.toHsl()
    var low = 0f
    var high = hsl.lightness
    repeat(LUMINANCE_SEARCH_STEPS) {
        val mid = (low + high) / 2f
        if (contrastRatio(Hsl(hsl.hue, hsl.saturation, mid).toColor(), background) >= ACCENT_TEXT_MIN_CONTRAST) low = mid else high = mid
    }
    return Hsl(hsl.hue, hsl.saturation, low).toColor()
}

private fun toneAtLuminance(base: Color, targetLuminance: Float): Color {
    val hsl = base.toHsl()
    val saturation = if (hsl.saturation < ACHROMATIC_SATURATION) {
        hsl.saturation
    } else {
        hsl.saturation.coerceIn(IMMERSIVE_MIN_SATURATION, IMMERSIVE_MAX_SATURATION)
    }
    return Hsl(hsl.hue, saturation, lightnessAtLuminance(hsl.hue, saturation, targetLuminance)).toColor()
}

// 固定色相与饱和度时相对亮度随 lightness 单调递增，二分即可
private fun lightnessAtLuminance(hue: Float, saturation: Float, targetLuminance: Float): Float {
    var low = 0f
    var high = 1f
    repeat(LUMINANCE_SEARCH_STEPS) {
        val mid = (low + high) / 2f
        if (Hsl(hue, saturation, mid).toColor().luminance() < targetLuminance) low = mid else high = mid
    }
    return (low + high) / 2f
}
