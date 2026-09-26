package com.lin0721.linmusic.core.ui.theme

import androidx.compose.ui.graphics.Color

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

val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF282828)
val SurfaceLight = Color(0xFF3E3E3E)
val TextGray = Color(0xFFB3B3B3)

val NeteaseRed = Color(0xFFC20C0C)
val GradientStart = Color(0xFF3a1515)
val BackgroundBlack = Color(0xFF0a0a0a)

// 底栏选中项的药丸指示器底色
val NavPillSelected = Color(0xFF383A4A)

// 筛选药丸未选中态底色。取白 10% 叠在顶栏渐变上的等效不透明色
val FilterPillInactive = Color(0xFF332B2B)

// 全局 Toast 底色
val ToastBackground = Color(0xFF2E2E2E)

// 网页登录容器底色，与网页自身背景一致以避免键盘弹出时闪屏
val WebLoginBackground = Color(0xFFF5F5F7)

// 封面取色未命中缓存时的兜底背景色，迷你播放器与全屏播放器共用以保证配色一致
val FallbackBase = Color(0xFF333333)

// 歌单/歌手页在取色完成前的初始底色
val CoverPlaceholderDark = Color(0xFF2C2C2C)

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

// 已下载标识翠绿色
val DownloadedGreen = Color(0xFF10B981)

// SVIP 标识金色
val SvipGold = Color(0xFFD4A017)

// 首页功能入口的策展渐变。心动模式、音乐漫游这类功能本就没有对应封面，
// 用固定色块表达
val EntryDailyGradient = listOf(Color(0xFFE5484D), Color(0xFF8C1116))
val EntryHotGradient = listOf(Color(0xFFF08C1A), Color(0xFF9C4A05))
val EntryHeartGradient = listOf(Color(0xFFA33FC4), Color(0xFF4B1268))
val EntryRadarGradient = listOf(Color(0xFF2F7FD6), Color(0xFF123F70))
val EntryRoamingGradient = listOf(Color(0xFF3FAE5C), Color(0xFF175C2C))
