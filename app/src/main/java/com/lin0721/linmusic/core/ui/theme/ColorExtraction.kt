package com.lin0721.linmusic.core.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.ui.theme.vibrant.VibrantSwatch
import com.lin0721.linmusic.core.ui.theme.vibrant.defaultVibrantFilter
import com.lin0721.linmusic.core.ui.theme.vibrant.hslToRgb
import com.lin0721.linmusic.core.ui.theme.vibrant.medianCutQuantize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val TAG = "ColorExtraction"

// 全屏播放器背景色系统的唯一契约：base/textHighlight 是播放器 UI 直接消费的两个值
data class PlayerBackdropPalette(
    val base: Color,
    val textHighlight: Color,
)

// candidate swatch 色度均低于此值时，视为黑白/灰阶封面，不编造色相。
// 用绝对色度（RGB 最大最小值之差）而不是 HSL 饱和度：HSL 饱和度在明度趋近 0 或 1 时
// 会被放大，纯白封面上几级压缩噪声就能算出接近 1 的"高饱和度"，色度没有这个问题
private const val MEANINGFUL_CHROMA = 0.06f

// 缓存未命中或取色异常时的兜底色板，迷你播放器与全屏播放器共用以保证配色一致
val FallbackBackdropPalette = PlayerBackdropPalette(
    base = FallbackBase,
    textHighlight = lerp(FallbackBase, Color.White, 0.85f),
)

// 量化参数对齐 node-vibrant 默认配置：64 色量化、quality=5 等比例降采样
private const val VIBRANT_COLOR_COUNT = 64
private const val VIBRANT_QUALITY = 5

// 取色专用的解码尺寸上限（最长边），复用 ArtistBackdrop 已在用的 640 规格，保持原图宽高比解码
private const val DECODE_MAX_DIMENSION = 640

// 取色专用的独立解码请求，跟各处 UI 实际显示尺寸完全脱钩。
suspend fun extractBackdropPaletteFromUrl(context: Context, url: String): PlayerBackdropPalette {
    val canonicalUrl = url.substringBefore("?param=")
    if (canonicalUrl.isBlank()) return FallbackBackdropPalette
    return try {
        val request = ImageRequest.Builder(context)
            .data(canonicalUrl)
            .size(DECODE_MAX_DIMENSION, DECODE_MAX_DIMENSION)
            .allowHardware(false)
            .build()
        val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return FallbackBackdropPalette
        extractBackdropPalette(bitmap)
    } catch (e: Exception) {
        AppLogger.d(TAG, "取色请求失败，使用默认深灰色板", e)
        FallbackBackdropPalette
    }
}

suspend fun extractBaseColorFromUrl(context: Context, url: String): Color {
    return extractBackdropPaletteFromUrl(context, url).base
}

// 内部核心逻辑：解码好的位图 → 按 quality 等比例降采样 → 中位切分量化 → 挑 base。
// 只应由上面的 *FromUrl 系列调用，不要在业务代码里直接复用某个显示用 AsyncImage 的解码结果——
// 那正是取色不一致的根源
private fun extractBackdropPalette(bitmap: Bitmap): PlayerBackdropPalette {
    return try {
        val scaled = scaleDownByQuality(bitmap, VIBRANT_QUALITY)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)

        val quantized = medianCutQuantize(pixels, VIBRANT_COLOR_COUNT, ::defaultVibrantFilter)
        if (quantized.isEmpty()) return FallbackBackdropPalette

        // 整张图都没有色度信号时直接给纯灰，不带任何色调
        if (isGrayscaleSwatches(quantized)) {
            val base = grayscaleBaseColor(quantized)
            return PlayerBackdropPalette(
                base = base,
                textHighlight = lerp(base, Color.White, 0.85f),
            )
        }

        val base = pickBaseColor(quantized)
        PlayerBackdropPalette(
            base = base,
            textHighlight = lerp(base, Color.White, 0.85f),
        )
    } catch (e: Exception) {
        AppLogger.d(TAG, "取色失败，使用默认深灰色板", e)
        FallbackBackdropPalette
    }
}

// 对应 node-vibrant 的 Image.scaleDown：按 1/quality 等比例缩放两条边，不改变宽高比
private fun scaleDownByQuality(bitmap: Bitmap, quality: Int): Bitmap {
    val ratio = 1f / quality
    val targetWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
    val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

// 整图色度检查用 quantized swatch 列表；此处用同一个阈值判断某个 swatch 列表是不是"没有色度信号"
internal fun isGrayscaleSwatches(swatches: List<VibrantSwatch>): Boolean =
    swatches.none { it.chroma >= MEANINGFUL_CHROMA }

// 灰阶封面的兜底色：取采样占比最高（最能代表整张图基调）的候选定明度，
// 收敛到一个不会在暗色播放器背景上太扎眼、也不会糊成一片黑的范围
internal fun grayscaleBaseColor(swatches: List<VibrantSwatch>): Color {
    val dominant = swatches.maxByOrNull { it.population } ?: return FallbackBase
    val lightness = dominant.hsl[2].coerceIn(0.15f, 0.35f)
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(0f, 0f, lightness)))
}

// 纸白与纯黑：封面留白/高光与暗部，不参与任何分组
private const val PAPER_WHITE_LIGHTNESS = 0.9f
private const val INK_BLACK_LIGHTNESS = 0.1f

// 低于此色度的候选肉眼看就是白/灰（如 AIR 翅膀的淡灰蓝），归入中性组。
// 暗色的色度上限天然随明度收窄（深藏青只有 0.09），明度低于 NEUTRAL_FULL_LIGHTNESS 时门槛按比例降低
private const val NEUTRAL_CHROMA = 0.13f
private const val NEUTRAL_FULL_LIGHTNESS = 0.5f

// 彩色比灰白更抢眼，中性组面积须达到最大彩色组的这个倍数才胜出。
// 实测封面比值在 1.45 与 1.71（AIR）之间有明显断层
private const val NEUTRAL_WIN_RATIO = 1.6f

// 色相分组半径，hsl[0] 取值 0..1
private const val HUE_GROUP_RADIUS = 20f / 360f

// 组内代表色偏向深色的程度，深色更适合做深色背景
private const val DARK_PREFERENCE_POWER = 2

// 最终 base 只保留色相，色度/明度统一钳到固定区间（区间取自 Spotify 实测背景色），
// 避免高饱和封面过艳、低饱和封面发灰
internal const val MIN_BASE_CHROMA = 0.15f
private const val MAX_BASE_CHROMA = 0.30f
private const val MIN_BASE_LIGHTNESS = 0.2f
private const val MAX_BASE_LIGHTNESS = 0.5f

// 中性组胜出时只带一点封面整体色调
internal const val NEUTRAL_BASE_CHROMA = 0.06f

// 规则对齐 Spotify 实测效果：面积最大的色系胜出（中性组按 NEUTRAL_WIN_RATIO 折算后与各彩色色相组比面积），
// 胜出组里挑一个偏深的真实颜色取色相
internal fun pickBaseColor(swatches: List<VibrantSwatch>): Color {
    val candidates = swatches.filter {
        it.population > 0 && it.hsl[2] >= INK_BLACK_LIGHTNESS && it.hsl[2] < PAPER_WHITE_LIGHTNESS
    }
    if (candidates.isEmpty()) return FallbackBase

    val (neutral, chromatic) = candidates.partition {
        it.chroma < NEUTRAL_CHROMA * min(1f, it.hsl[2] / NEUTRAL_FULL_LIGHTNESS)
    }
    val neutralArea = neutral.sumOf { it.population }

    var bestGroup = emptyList<VibrantSwatch>()
    var bestArea = 0
    for (seed in chromatic) {
        val group = chromatic.filter { hueDistance(seed.hsl[0], it.hsl[0]) <= HUE_GROUP_RADIUS }
        val area = group.sumOf { it.population }
        if (area > bestArea) {
            bestGroup = group
            bestArea = area
        }
    }

    if (bestGroup.isEmpty() || neutralArea >= bestArea * NEUTRAL_WIN_RATIO) {
        val lightness = neutral.sumOf { it.hsl[2].toDouble() * it.population }.toFloat() / neutralArea
        val hue = weightedMeanHue(neutral)
            ?: return lightness.coerceIn(MIN_BASE_LIGHTNESS, MAX_BASE_LIGHTNESS).let { Color(it, it, it) }
        return normalizedColor(hue, NEUTRAL_BASE_CHROMA, lightness)
    }

    val chosen = bestGroup.maxBy { swatch ->
        var darkness = 1f
        repeat(DARK_PREFERENCE_POWER) { darkness *= 1f - swatch.hsl[2] }
        swatch.population * darkness
    }
    return normalizedColor(
        chosen.hsl[0],
        chosen.chroma.coerceIn(MIN_BASE_CHROMA, MAX_BASE_CHROMA),
        chosen.hsl[2],
    )
}

// 在 HSL 空间按目标色度反推饱和度：直接改明度而保持饱和度不变，会让压暗的浅色反而变艳
private fun normalizedColor(hue: Float, chroma: Float, lightness: Float): Color {
    val l = lightness.coerceIn(MIN_BASE_LIGHTNESS, MAX_BASE_LIGHTNESS)
    val s = (chroma / (1f - abs(2f * l - 1f))).coerceIn(0f, 1f)
    val (r, g, b) = hslToRgb(hue, s, l)
    return Color(r, g, b)
}

// 色相是环形量，按面积×色度加权求向量平均；纯灰候选色相无意义，权重自然为 0
private fun weightedMeanHue(swatches: List<VibrantSwatch>): Float? {
    var x = 0.0
    var y = 0.0
    for (swatch in swatches) {
        val weight = swatch.population.toDouble() * swatch.chroma
        val angle = swatch.hsl[0] * 2.0 * PI
        x += weight * cos(angle)
        y += weight * sin(angle)
    }
    if (x == 0.0 && y == 0.0) return null
    val hue = (atan2(y, x) / (2.0 * PI)).toFloat()
    return if (hue < 0f) hue + 1f else hue
}

private fun hueDistance(a: Float, b: Float): Float {
    val d = abs(a - b) % 1f
    return min(d, 1f - d)
}

object PaletteMemoryCache {
    private val cache = android.util.LruCache<String, PlayerBackdropPalette>(150)

    fun get(mediaId: String): PlayerBackdropPalette? = cache.get(mediaId)

    fun put(mediaId: String, palette: PlayerBackdropPalette) {
        cache.put(mediaId, palette)
    }
}
