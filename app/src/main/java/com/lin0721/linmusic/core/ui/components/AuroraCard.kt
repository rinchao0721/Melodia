package com.lin0721.linmusic.core.ui.components

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion

/**
 * 极光卡片三色组合：低饱和实色底 + 同色系深浅光团。
 */
data class AuroraPalette(
    val base: Color,
    val light: Color,
    val dark: Color
)

fun PersonalFmMode.toAuroraPalette(): AuroraPalette = AuroraPalette(
    base = Color(baseColor),
    light = Color(lightColor),
    dark = Color(darkColor)
)

/**
 * 极光卡片背景：低饱和实色底叠加同色系慢速漂移光团。
 * 卡片不可见或关闭系统过渡动画时停止动画；API < 31 使用无模糊多层径向渐变降级。
 */
@Composable
fun AuroraCardBackground(
    palette: AuroraPalette,
    modifier: Modifier = Modifier
) {
    val motionEnabled = LocalUiMotion.current.enabled
    val lifecycleOwner = LocalLifecycleOwner.current
    var isStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isOnScreen by remember { mutableStateOf(true) }
    val shouldAnimate = motionEnabled && isStarted && isOnScreen

    val phasePrimary: State<Float>
    val phaseSecondary: State<Float>
    if (shouldAnimate) {
        val transition = rememberInfiniteTransition(label = "aurora_drift")
        phasePrimary = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 9000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "aurora_blob_1"
        )
        phaseSecondary = transition.animateFloat(
            initialValue = 0.65f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 10500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "aurora_blob_2"
        )
    } else {
        phasePrimary = remember { mutableFloatStateOf(0f) }
        phaseSecondary = remember { mutableFloatStateOf(0.5f) }
    }

    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                isOnScreen = coordinates.isAttached && !coordinates.boundsInWindow().isEmpty
            }
            .background(palette.base)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (supportsBlur) {
                        Modifier.blur(22.dp, BlurredEdgeTreatment.Rectangle)
                    } else {
                        Modifier
                    }
                )
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    if (w <= 0f || h <= 0f) return@drawBehind
                    val p1 = phasePrimary.value
                    val p2 = phaseSecondary.value

                    val driftX1 = 18.dp.toPx() * p1
                    val driftY1 = (-12).dp.toPx() * p1
                    val scale1 = 1f + 0.15f * p1

                    val driftX2 = (-16).dp.toPx() * p2
                    val driftY2 = 10.dp.toPx() * p2
                    val scale2 = 1f + 0.12f * p2

                    drawAuroraBlob(
                        color = palette.light,
                        center = Offset(w * 0.18f + driftX1, h * 0.10f + driftY1),
                        radius = h * 0.45f * scale1,
                        blurred = supportsBlur
                    )
                    drawAuroraBlob(
                        color = palette.dark,
                        center = Offset(w * 0.78f + driftX2, h * 0.85f + driftY2),
                        radius = h * 0.40f * scale2,
                        blurred = supportsBlur
                    )
                }
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAuroraBlob(
    color: Color,
    center: Offset,
    radius: Float,
    blurred: Boolean
) {
    if (blurred) {
        drawCircle(
            color = color.copy(alpha = 0.88f),
            center = center,
            radius = radius
        )
    } else {
        // API < 31 无原生模糊，用多层渐隐径向渐变模拟柔和光团
        val softRadius = radius * 1.75f
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to color.copy(alpha = 0.78f),
                0.45f to color.copy(alpha = 0.42f),
                1.0f to Color.Transparent,
                center = center,
                radius = softRadius
            ),
            center = center,
            radius = softRadius
        )
    }
}

/**
 * 播放页模式胶囊左侧的极光小圆点。
 */
@Composable
fun PersonalFmModeOrb(
    mode: PersonalFmMode,
    modifier: Modifier = Modifier
) {
    val palette = remember(mode) { mode.toAuroraPalette() }
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(CircleShape)
            .drawBehind {
                drawRect(palette.base)
                val r = size.minDimension * 0.78f
                val lightCenter = Offset(size.width * 0.25f, size.height * 0.25f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.light.copy(alpha = 0.92f), Color.Transparent),
                        center = lightCenter,
                        radius = r
                    ),
                    center = lightCenter,
                    radius = r
                )
                val darkCenter = Offset(size.width * 0.80f, size.height * 0.80f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.dark.copy(alpha = 0.92f), Color.Transparent),
                        center = darkCenter,
                        radius = r
                    ),
                    center = darkCenter,
                    radius = r
                )
            }
    )
}
