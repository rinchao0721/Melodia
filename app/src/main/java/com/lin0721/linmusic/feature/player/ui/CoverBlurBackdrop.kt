package com.lin0721.linmusic.feature.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.plugin.LayoutBackdrop
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val TAG = "CoverBlurBackdrop"

// 背景只需要低分辨率封面，模糊后细节不可见
private const val BACKDROP_DECODE_EDGE = 256

// Android 12 以下没有 RenderEffect，先缩到这个长边再放大，用双线性插值近似模糊
private const val SOFT_BLUR_EDGE = 24

private const val CROSSFADE_MS = 800

// 漂移的最大旋转角（弧度），其余余量留给平移
private const val MAX_DRIFT_ROTATION = 0.07f

private class CoverFrame(val image: ImageBitmap)

// 原生模糊封面背景：封面铺满放大，可选漂移，切歌时新旧封面交叉淡入淡出
@Composable
fun CoverBlurBackdrop(
    coverUrl: String,
    style: LayoutBackdrop,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val motion = LocalUiMotion.current
    val canonical = remember(coverUrl) { coverUrl.substringBefore("?param=") }
    val softBlur = Build.VERSION.SDK_INT < Build.VERSION_CODES.S && style.blur > 0f
    var shown by remember { mutableStateOf<CoverFrame?>(null) }
    var outgoing by remember { mutableStateOf<CoverFrame?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(canonical, softBlur) {
        val next = withContext(Dispatchers.Default) {
            if (canonical.isBlank()) return@withContext null
            loadCoverBitmap(context, canonical, BACKDROP_DECODE_EDGE)?.let { bitmap ->
                if (softBlur) scaledToEdge(bitmap, SOFT_BLUR_EDGE) else bitmap
            }?.asImageBitmap()
        }
        if (next == null && shown == null) return@LaunchedEffect
        outgoing = shown
        shown = next?.let(::CoverFrame)
        progress.snapTo(0f)
        progress.animateTo(1f, motion.tween(CROSSFADE_MS))
        outgoing = null
    }

    val drift = style.drift && playing && motion.enabled
    val phaseMs = rememberMeshPhaseMs(drift)
    val dim = if (MelodiaColors.IsDark) style.dimDark else style.dimLight
    val density = LocalDensity.current.density
    val blurPx = style.blur * density
    val saturationFilter = remember(style.saturation) {
        if (style.saturation == 1f) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(style.saturation) })
    }
    val current = shown
    val previous = outgoing

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        previous?.let { frame ->
            CoverLayer(frame, style, blurPx, softBlur, saturationFilter, { phaseMs.longValue }) {
                if (current == null) 1f - progress.value else 1f
            }
        }
        current?.let { frame ->
            CoverLayer(frame, style, blurPx, softBlur, saturationFilter, { phaseMs.longValue }) { progress.value }
        }
        if (dim > 0f) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = dim)))
        }
    }
}

@Composable
private fun CoverLayer(
    frame: CoverFrame,
    style: LayoutBackdrop,
    blurPx: Float,
    softBlur: Boolean,
    saturationFilter: ColorFilter?,
    phaseMs: () -> Long,
    alphaProvider: () -> Float
) {
    // 放大倍数留出的余量一半给旋转、一半给平移，保证漂移时边缘不露底
    val margin = (style.scale - 1f).coerceAtLeast(0f)
    val translationFraction = margin / 4f
    Image(
        bitmap = frame.image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = if (softBlur) FilterQuality.Low else FilterQuality.Medium,
        colorFilter = saturationFilter,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = alphaProvider().coerceIn(0f, 1f)
                scaleX = style.scale
                scaleY = style.scale
                if (style.drift && margin > 0f) {
                    val phase = phaseMs()
                    val longEdge = maxOf(size.width, size.height)
                    val shortEdge = minOf(size.width, size.height).coerceAtLeast(1f)
                    val maxRotation = (margin / 2f / (longEdge / shortEdge)).coerceAtMost(MAX_DRIFT_ROTATION)
                    rotationZ = Math.toDegrees(meshPingPong(phase, 24000L, -maxRotation, maxRotation).toDouble()).toFloat()
                    translationX = meshPingPong(phase, 31000L, -translationFraction, translationFraction) * size.width
                    translationY = meshPingPong(phase, 27000L, -translationFraction, translationFraction) * size.height
                }
                if (!softBlur && blurPx > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                }
            }
    )
}

// 解码封面到不超过 maxEdge 的位图；失败返回 null，不抛出（取消除外）
internal suspend fun loadCoverBitmap(context: Context, canonicalUrl: String, maxEdge: Int): Bitmap? {
    return try {
        val request = ImageRequest.Builder(context)
            .data(canonicalUrl)
            .size(maxEdge, maxEdge)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
        context.imageLoader.execute(request).image?.toBitmap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.d(TAG, "封面解码失败", e)
        null
    }
}

private fun scaledToEdge(bitmap: Bitmap, edge: Int): Bitmap {
    val longEdge = maxOf(bitmap.width, bitmap.height)
    if (longEdge <= edge) return bitmap
    val ratio = edge.toFloat() / longEdge
    val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
    val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}

// 画布 state.cover：长边 128px 的 JPEG data URI
class CanvasCoverImage(val src: String, val width: Int, val height: Int)

private const val CANVAS_COVER_EDGE = 128
private const val CANVAS_COVER_QUALITY = 80

// 同一首歌只编码一次
private val canvasCoverCache = android.util.LruCache<String, CanvasCoverImage>(6)

internal suspend fun loadCanvasCover(context: Context, coverUrl: String): CanvasCoverImage? {
    val canonical = coverUrl.substringBefore("?param=")
    if (canonical.isBlank()) return null
    canvasCoverCache.get(canonical)?.let { return it }
    return withContext(Dispatchers.Default) {
        try {
            val source = loadCoverBitmap(context, canonical, CANVAS_COVER_EDGE * 2) ?: return@withContext null
            val scaled = scaledToEdge(source, CANVAS_COVER_EDGE)
            val bytes = ByteArrayOutputStream().use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, CANVAS_COVER_QUALITY, out)) return@withContext null
                out.toByteArray()
            }
            val image = CanvasCoverImage(
                src = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP),
                width = scaled.width,
                height = scaled.height
            )
            canvasCoverCache.put(canonical, image)
            image
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.d(TAG, "画布封面编码失败", e)
            null
        }
    }
}
