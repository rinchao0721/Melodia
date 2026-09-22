package com.lin0721.linmusic.core.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.ui.theme.vibrant.VibrantPalette
import com.lin0721.linmusic.core.ui.theme.vibrant.VibrantSwatch
import com.lin0721.linmusic.core.ui.theme.vibrant.defaultVibrantFilter
import com.lin0721.linmusic.core.ui.theme.vibrant.generateVibrantPalette
import com.lin0721.linmusic.core.ui.theme.vibrant.medianCutQuantize

private const val TAG = "ColorExtraction"

// 全屏播放器背景色系统的唯一契约：swatches 是 node-vibrant 算法的完整 6 色板，
// base/textHighlight 是从 swatches 派生出的、播放器 UI 直接消费的两个值
data class PlayerBackdropPalette(
    val swatches: VibrantPalette,
    val base: Color,
    val textHighlight: Color,
)

// candidate swatch 色度均低于此值时，视为黑白/灰阶封面，不编造色相。
// 用绝对色度（RGB 最大最小值之差）而不是 HSL 饱和度：HSL 饱和度在明度趋近 0 或 1 时
// 会被放大，纯白封面上几级压缩噪声就能算出接近 1 的"高饱和度"，色度没有这个问题
private const val MEANINGFUL_CHROMA = 0.06f

// 缓存未命中或取色异常时的兜底色板，迷你播放器与全屏播放器共用以保证配色一致
private val EmptyVibrantPalette = VibrantPalette(null, null, null, null, null, null)
val FallbackBackdropPalette = PlayerBackdropPalette(
    swatches = EmptyVibrantPalette,
    base = FallbackBase,
    textHighlight = lerp(FallbackBase, Color.White, 0.85f),
)

// 取色算法对齐 node-vibrant 默认配置：64 色量化、quality=5 等比例降采样
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
        val drawable = context.imageLoader.execute(request).drawable ?: return FallbackBackdropPalette
        extractBackdropPalette(drawable)
    } catch (e: Exception) {
        AppLogger.d(TAG, "取色请求失败，使用默认深灰色板", e)
        FallbackBackdropPalette
    }
}

suspend fun extractBaseColorFromUrl(context: Context, url: String): Color {
    return extractBackdropPaletteFromUrl(context, url).base
}

// 内部核心逻辑：解码好的位图 → 按 quality 等比例降采样 → node-vibrant 量化+生成 6 色板 → 挑 base。
// 只应由上面的 *FromUrl 系列调用，不要在业务代码里直接复用某个显示用 AsyncImage 的解码结果——
// 那正是取色不一致的根源
private fun extractBackdropPalette(drawable: android.graphics.drawable.Drawable): PlayerBackdropPalette {
    return try {
        val bitmap = drawable.toBitmap()
        val scaled = scaleDownByQuality(bitmap, VIBRANT_QUALITY)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)

        val quantized = medianCutQuantize(pixels, VIBRANT_COLOR_COUNT, ::defaultVibrantFilter)
        if (quantized.isEmpty()) return FallbackBackdropPalette

        // 灰阶判定放在 generator 之前：整张图都没有色度信号时，不让 generator 去矬子里拔将军——
        // 它内部按 HSL 饱和度匹配区间，同一张纯白封面照样会被放大误导，选出一个"看起来鲜艳"的候选
        if (isGrayscaleSwatches(quantized)) {
            val base = grayscaleBaseColor(quantized)
            return PlayerBackdropPalette(
                swatches = EmptyVibrantPalette,
                base = base,
                textHighlight = lerp(base, Color.White, 0.85f),
            )
        }

        val palette = generateVibrantPalette(quantized)
        val base = pickBaseColor(palette)
        PlayerBackdropPalette(
            swatches = palette,
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

// base 明度下限——DarkVibrant/DarkMuted 的目标明度只有 0.26，黑白基调的封面（比如乐队
// 黑白硬照）落到这两个分类时，只要还有一丝色度、没触发整图灰阶判定，就会原样把这个很暗的
// 颜色当 base，肉眼看基本就是纯黑。
private const val MIN_BASE_LIGHTNESS = 0.2f

// 提亮用线性 RGB 朝白混合，不走 HSL 色相重建——色度很低的候选，色相在极低色度下数值本身
// 就不稳定，重建色相反而会把这点数值噪声放大成一个跟原图无关的颜色（比如黑白封面提亮后发蓝）
private const val LOW_LIGHTNESS_LIFT_RATIO = 0.25f

// node-vibrant 本身只产出 6 个平行分类，不产出"主色"，base 的优先级是 Melodia 自己定义的：
// 鲜艳系优先于柔和系，同一系里正常明度优先于亮/暗变体
internal fun pickBaseColor(palette: VibrantPalette): Color {
    val candidates = listOfNotNull(
        palette.vibrant, palette.lightVibrant, palette.darkVibrant,
        palette.muted, palette.lightMuted, palette.darkMuted,
    )
    if (candidates.isEmpty()) return FallbackBase

    // 整图色度检查通过了才会走到这，但 generator 内部按 HSL 饱和度匹配区间，
    // 仍可能选出一个色度依然很低的候选（同样的放大问题）——这里再用色度兜一层底
    if (isGrayscaleSwatches(candidates)) {
        return grayscaleBaseColor(candidates)
    }

    val chosen = candidates.first()
    return if (chosen.hsl[2] < MIN_BASE_LIGHTNESS) {
        lerp(chosen.rgb, Color.White, LOW_LIGHTNESS_LIFT_RATIO)
    } else {
        chosen.rgb
    }
}

object PaletteMemoryCache {
    private val cache = android.util.LruCache<String, PlayerBackdropPalette>(150)

    fun get(mediaId: String): PlayerBackdropPalette? = cache.get(mediaId)

    fun put(mediaId: String, palette: PlayerBackdropPalette) {
        cache.put(mediaId, palette)
    }
}
