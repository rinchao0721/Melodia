package com.lin0721.linmusic.feature.player.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.skin.CoverGroup
import com.lin0721.linmusic.core.ui.skin.isCoverBackdrop
import com.lin0721.linmusic.core.ui.theme.darken
import com.lin0721.linmusic.core.ui.theme.playerBackdrop
import com.lin0721.linmusic.core.ui.theme.rememberImmersiveColors
import com.lin0721.linmusic.core.ui.theme.smoothVerticalGradient
import com.lin0721.linmusic.core.ui.theme.themedCoverBase
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class BackdropMode { Collapsed, Immersive }

// 用原生 Modifier.blur 软化光斑（仅沉浸式光斑游走需要模糊漫反射）
private val BACKDROP_BLUR_RADIUS = 60.dp

// Collapsed 渐隐终点固定，动态改 endY 会让 8-bit 色带边缘每帧爬动
private const val COLLAPSED_GRADIENT_END_DP = 1100f
private const val COLLAPSED_GRADIENT_STEPS = 64
private const val COLLAPSED_FADE_END_FRACTION = 0.8f

// 抖动噪声：白色随机 alpha 平铺，叠在渐变上打散相邻色阶的边界；强度恒定，仅在渐隐终点处蒙版淡出
private const val NOISE_TILE_PX = 128
private const val NOISE_MAX_ALPHA = 4
private const val NOISE_MASK_SOLID_FRACTION = 0.9f
private const val NOISE_SEED = 20261005

// 光斑游走周期十几秒、位移很小，约 20fps 推进肉眼无差别，避免整块模糊层满帧重绘
private const val MESH_TICK_MS = 50L

private fun createNoiseBrush(): ShaderBrush {
    val random = Random(NOISE_SEED)
    val pixels = IntArray(NOISE_TILE_PX * NOISE_TILE_PX) {
        (random.nextInt(NOISE_MAX_ALPHA + 1) shl 24) or 0x00FFFFFF
    }
    val bitmap = Bitmap.createBitmap(pixels, NOISE_TILE_PX, NOISE_TILE_PX, Bitmap.Config.ARGB_8888)
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

// 单一色相的模糊光斑：深色底 + lighten/darken 变体，Immersive 背景与歌词预览卡共用
// fill 必须是明显压暗过的变体，不能直接传未处理的 base——base 现在取自 Vibrant
// lightBlob 为 null 时只画 darkBlob 这一枚
internal fun DrawScope.drawSingleHueMesh(
    fill: Color,
    lightBlob: Color? = null,
    lightCenter: Offset = Offset.Zero,
    lightRadius: Float = 0f,
    darkBlob: Color,
    darkCenter: Offset,
    darkRadius: Float
) {
    drawRect(color = fill)
    if (lightBlob != null) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(lightBlob.copy(alpha = 0.5f), Color.Transparent),
                center = lightCenter,
                radius = lightRadius
            ),
            center = lightCenter,
            radius = lightRadius
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(darkBlob.copy(alpha = 0.6f), Color.Transparent),
            center = darkCenter,
            radius = darkRadius
        ),
        center = darkCenter,
        radius = darkRadius
    )
}

// 光斑游走相位（毫秒）：仅 animate 为真时推进，停下即定格；只应在绘制阶段读取，避免触发重组
@Composable
internal fun rememberMeshPhaseMs(animate: Boolean): LongState {
    val phaseMs = remember { mutableLongStateOf(0L) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        // 帧时钟在页面不可见时挂起，后台不会空转；回到前台的首个间隔封顶，避免光斑跳位
        var last = withFrameMillis { it }
        while (true) {
            delay(MESH_TICK_MS)
            val now = withFrameMillis { it }
            phaseMs.longValue += (now - last).coerceIn(0L, MESH_TICK_MS * 2)
            last = now
        }
    }
    return phaseMs
}

// 往返线性插值：periodMs 为单程时长，与 infiniteRepeatable(tween(periodMs), Reverse) 等效
internal fun meshPingPong(phaseMs: Long, periodMs: Long, from: Float, to: Float): Float {
    val t = (phaseMs % (periodMs * 2)).toFloat() / periodMs
    val fraction = if (t <= 1f) t else 2f - t
    return from + (to - from) * fraction
}

// 全屏播放器背景：Collapsed（Hero）单色平滑渐隐，Immersive（歌词全屏）单色相双光斑游走
// 背景绘制和 content 拆成两个子 Box：仅 Immersive 模糊背景层，content 保持清晰
@Composable
fun PlayerBackdrop(
    base: Color,
    mode: BackdropMode,
    modifier: Modifier = Modifier,
    // 仅 Immersive 生效：为假时光斑定格
    animateMesh: Boolean = false,
    translationYProvider: () -> Float = { 0f },
    content: @Composable BoxScope.() -> Unit = {}
) {
    // 透出皮肤背景图时由外层整页画背景，这里只在歌词全屏层补画一次
    if (isCoverBackdrop(CoverGroup.PLAYER)) {
        Box(modifier.then(if (mode == BackdropMode.Immersive) Modifier.playerBackdrop(Color.Transparent) else Modifier), content = content)
        return
    }
    when (mode) {
        BackdropMode.Collapsed -> {
            val density = LocalDensity.current
            val gradientEndY = with(density) { COLLAPSED_GRADIENT_END_DP.dp.toPx() }

            // base 本身已偏深且明度有上限，深色下直接铺底；浅色下取同色相浅调，两者都渐隐到页面底
            val fillColor = themedCoverBase(base)
            val endColor = Color.Transparent
            val gradientBrush = remember(fillColor, endColor, gradientEndY) {
                Brush.smoothVerticalGradient(
                    from = fillColor,
                    to = endColor,
                    startY = 0f,
                    endY = gradientEndY,
                    fadeEndFraction = COLLAPSED_FADE_END_FRACTION,
                    steps = COLLAPSED_GRADIENT_STEPS
                )
            }
            // 开启抖动打散 8-bit 色阶，消除大面积暗色渐变的条纹
            val ditherPaint = remember { Paint().apply { asFrameworkPaint().isDither = true } }
            val noiseBrush = remember { createNoiseBrush() }
            val noiseMaskBrush = remember(gradientEndY) {
                Brush.verticalGradient(
                    0f to Color.Black,
                    NOISE_MASK_SOLID_FRACTION to Color.Black,
                    1f to Color.Transparent,
                    startY = 0f,
                    endY = gradientEndY * COLLAPSED_FADE_END_FRACTION
                )
            }

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(1200.dp)
                    .graphicsLayer { translationY = translationYProvider() }
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        // 独立离屏层，渐变与噪声的合成结果缓存，不逐帧重绘
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawBehind {
                            drawIntoCanvas { canvas ->
                                gradientBrush.applyTo(size, ditherPaint, 1f)
                                canvas.drawRect(0f, 0f, size.width, size.height, ditherPaint)
                            }
                            drawIntoCanvas { canvas ->
                                canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
                                drawRect(brush = noiseBrush)
                                drawRect(brush = noiseMaskBrush, blendMode = BlendMode.DstIn)
                                canvas.restore()
                            }
                        }
                )
                content()
            }
        }

        BackdropMode.Immersive -> {
            val meshPhaseMs = rememberMeshPhaseMs(animateMesh)
            val immersive = rememberImmersiveColors(base)
            val fillColor = immersive.fill
            val darkBlob = immersive.blob

            Box(modifier = modifier) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(BACKDROP_BLUR_RADIUS)
                        .drawBehind {
                            val baseSize = size.minDimension
                            val phaseMs = meshPhaseMs.longValue
                            val darkCenterX = meshPingPong(phaseMs, 20000L, 1.0f, 1.4f)
                            val darkCenterY = meshPingPong(phaseMs, 16000L, 1.0f, 1.5f)
                            val darkRadiusScale = meshPingPong(phaseMs, 12000L, 0.5f, 0.7f)
                            drawSingleHueMesh(
                                fill = fillColor,
                                darkBlob = darkBlob,
                                darkCenter = Offset(size.width * darkCenterX, size.height * darkCenterY),
                                darkRadius = baseSize * darkRadiusScale
                            )
                        }
                )
                content()
            }
        }
    }
}
