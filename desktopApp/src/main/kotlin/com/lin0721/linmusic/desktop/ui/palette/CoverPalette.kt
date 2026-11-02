package com.lin0721.linmusic.desktop.ui.palette

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.lerp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.ui.skin.CoverGroup
import com.lin0721.linmusic.core.ui.skin.coverBase
import com.lin0721.linmusic.core.ui.theme.coverHeaderTint
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "CoverPalette"

// 量化参数对齐移动端：64 色量化；解码阶段直接缩到 128 边长，等价于移动端 quality=5 的降采样
private const val COLOR_COUNT = 64
private const val DECODE_SIZE = 128
private const val CACHE_SIZE = 150
private const val HIGHLIGHT_MIX = 0.85f

// 面板着色的唯一契约：base 为封面主色，textHighlight 为其提亮后的文字强调色
data class CoverPalette(val base: Color, val textHighlight: Color)

val FallbackCoverPalette = CoverPalette(FallbackBase, lerp(FallbackBase, Color.White, HIGHLIGHT_MIX))

// 封面色作页面背景：深色主题按比例压暗，浅色主题取同色相浅调，正文沿用主题文字色
@Composable
@ReadOnlyComposable
fun themedCoverBackground(base: Color, darkenFraction: Float): Color =
    if (DesktopColors.IsDark) lerp(base, Color.Black, darkenFraction) else coverHeaderTint(base)

// 页面头部渐变：按皮肤取色区设置换底色；透出背景图时为全透明页面底，渐变自然消失
@Composable
@ReadOnlyComposable
fun headerCoverBackground(base: Color, darkenFraction: Float): Color =
    coverBase(CoverGroup.HEADER, base)?.let { themedCoverBackground(it, darkenFraction) } ?: DesktopColors.PageFill

// 封面色卡片：透出背景图时改铺与外壳同款的半透明面板底
@Composable
@ReadOnlyComposable
fun cardCoverBackground(base: Color, darkenFraction: Float): Color =
    coverBase(CoverGroup.CARDS, base)?.let { themedCoverBackground(it, darkenFraction) } ?: DesktopColors.ShellFill

// 像素为 ARGB_8888 packed int；无可用像素或取色异常时返回兜底色板
fun extractCoverPalette(pixels: IntArray): CoverPalette {
    return try {
        val quantized = medianCutQuantize(pixels, COLOR_COUNT, ::defaultVibrantFilter)
        if (quantized.isEmpty()) return FallbackCoverPalette
        // 整张图都没有色度信号时直接给纯灰，不带任何色调
        val base = if (isGrayscaleSwatches(quantized)) grayscaleBaseColor(quantized) else pickBaseColor(quantized)
        CoverPalette(base, lerp(base, Color.White, HIGHLIGHT_MIX))
    } catch (e: Exception) {
        AppLogger.d(TAG, "取色失败，使用默认色板", e)
        FallbackCoverPalette
    }
}

// 取色专用的独立解码请求，与界面上封面的显示尺寸脱钩，同一封面结果恒定
suspend fun extractCoverPaletteFromUrl(url: String): CoverPalette {
    val canonicalUrl = url.substringBefore("?param=")
    if (canonicalUrl.isBlank()) return FallbackCoverPalette
    CoverPaletteCache.get(canonicalUrl)?.let { return it }
    return try {
        val palette = withContext(Dispatchers.Default) {
            val context = PlatformContext.INSTANCE
            val request = ImageRequest.Builder(context).data(canonicalUrl).size(DECODE_SIZE, DECODE_SIZE).build()
            val image = SingletonImageLoader.get(context).execute(request).image ?: return@withContext null
            val bitmap = image.toBitmap().asComposeImageBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.readPixels(pixels)
            extractCoverPalette(pixels)
        } ?: return FallbackCoverPalette
        CoverPaletteCache.put(canonicalUrl, palette)
        palette
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.d(TAG, "封面解码失败，使用默认色板", e)
        FallbackCoverPalette
    }
}

// 切回已听过的歌时不必重新解码
object CoverPaletteCache {
    private val cache = object : LinkedHashMap<String, CoverPalette>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CoverPalette>): Boolean = size > CACHE_SIZE
    }

    @Synchronized
    fun get(url: String): CoverPalette? = cache[url]

    @Synchronized
    fun put(url: String, palette: CoverPalette) {
        cache[url] = palette
    }
}
