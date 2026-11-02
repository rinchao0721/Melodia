package com.lin0721.linmusic.core.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos

// 平滑抗色阶断层的垂直渐变
// 采用余弦缓动曲线采样，首尾斜率平滑过渡；透明渐变保留原色相避免发灰
fun Brush.Companion.smoothVerticalGradient(
    from: Color,
    to: Color,
    startY: Float = 0f,
    endY: Float = Float.POSITIVE_INFINITY,
    fadeEndFraction: Float = 1f,
    steps: Int = 24
): Brush {
    val effectiveTo = if (to == Color.Transparent) from.copy(alpha = 0f) else to
    val effectiveFrom = if (from == Color.Transparent) effectiveTo.copy(alpha = 0f) else from
    val clampedFadeEnd = fadeEndFraction.coerceIn(0f, 1f)

    val stops = Array(if (clampedFadeEnd < 1f) steps + 1 else steps) { i ->
        if (i < steps) {
            val t = i.toFloat() / (steps - 1)
            val factor = ((1.0 - cos(t * PI)) / 2.0).toFloat()
            val color = lerp(effectiveFrom, effectiveTo, factor)
            (t * clampedFadeEnd) to color
        } else {
            1f to effectiveTo
        }
    }

    return verticalGradient(
        colorStops = stops,
        startY = startY,
        endY = endY
    )
}

// HSV 明度按比例偏移，供取色/背景渲染各处按需现算深浅变体，不落地成持久字段。
// 用比例缩放而不是加减法再 coerceIn 夹断：暗色/灰阶封面明度本就 ≤ 0.35，
// 加减法一夹断就是绝对纯黑，色相全丢；比例缩放无论原明度多低都只是"更暗"，不会砸成死黑
fun Color.lighten(amount: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    hsv[2] = (hsv[2] + (1f - hsv[2]) * amount).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

fun Color.darken(amount: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    hsv[2] = (hsv[2] * (1f - amount)).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// HSV 饱和度按比例往纯色方向推；原色饱和度为 0（灰阶/黑白）时色相无意义，直接跳过，
// 否则会凭空编出一个色相方向的颜色
fun Color.saturate(amount: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    if (hsv[1] <= 0f) return this
    hsv[1] = (hsv[1] + (1f - hsv[1]) * amount).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// 只给有色 base 提饱和度：中性 base 只带 NEUTRAL_BASE_CHROMA 的轻微色调，提饱和度会放大成明显偏色；
// 两个色度档位之间线性过渡，切歌渐变的中间色不跳变
fun Color.saturateIfChromatic(amount: Float): Color {
    val chroma = maxOf(red, green, blue) - minOf(red, green, blue)
    val weight = ((chroma - NEUTRAL_BASE_CHROMA) / (MIN_BASE_CHROMA - NEUTRAL_BASE_CHROMA)).coerceIn(0f, 1f)
    return saturate(amount * weight)
}

val GradientStart = Color(0xFF3a1515)

// 全局 Toast 底色
val ToastBackground = Color(0xFF2E2E2E)

// 网页登录容器底色，与网页自身背景一致以避免键盘弹出时闪屏
val WebLoginBackground = Color(0xFFF5F5F7)

// 封面取色未命中缓存时的兜底背景色，迷你播放器与全屏播放器共用以保证配色一致
val FallbackBase = Color(0xFF333333)

// 定时关闭中的剩余时间告警色
val TimerWarningRed = Color(0xFFFF5252)

// 音乐库卡片的装饰渐变：紫罗兰 → 紫 → 粉
val LibraryVioletGradient = listOf(
    Color(0xFF6366F1),
    Color(0xFFA855F7),
    Color(0xFFEC4899)
)

// 音乐库卡片的装饰渐变：蓝 → 翠绿
val LibraryBlueGreenGradient = listOf(
    Color(0xFF3B82F6),
    Color(0xFF10B981)
)

// 首页功能入口的策展渐变。心动模式、音乐漫游这类功能本就没有对应封面，
// 用固定色块表达
val EntryHotGradient = listOf(Color(0xFFF08C1A), Color(0xFF9C4A05))
val EntryHeartGradient = listOf(Color(0xFFA33FC4), Color(0xFF4B1268))
val EntryRadarGradient = listOf(Color(0xFF2F7FD6), Color(0xFF123F70))
val EntryRoamingGradient = listOf(Color(0xFF3FAE5C), Color(0xFF175C2C))
