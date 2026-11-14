package com.lin0721.linmusic.core.ui.badge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private const val POP_DURATION_MS = 150f
private const val BURST_DURATION_MS = 500f
private const val BURST_PARTICLE_COUNT = 14
private const val SPRING_DAMPING_RATIO = 0.4f
private const val SPRING_STIFFNESS = 700f

internal class ActiveDragBadge(
    val token: Any,
    val anchorCenter: Offset,
    initialDragCenter: Offset,
    val badgeSize: Size,
    val textLayoutResult: TextLayoutResult,
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val borderWidthPx: Float,
    val baseAnchorRadius: Float,
    val minAnchorRadius: Float,
    val dragRadius: Float,
    val minGooDistance: Float
) {
    var dragCenter by mutableStateOf(initialDragCenter)
    var broken by mutableStateOf(false)
}

internal class SpringBackBadge(
    val anchorCenter: Offset,
    startCenter: Offset,
    val broken: Boolean,
    val badgeSize: Size,
    val textLayoutResult: TextLayoutResult,
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val borderWidthPx: Float,
    val baseAnchorRadius: Float,
    val minAnchorRadius: Float,
    val dragRadius: Float,
    val minGooDistance: Float
) {
    val center: Animatable<Offset, AnimationVector2D> = Animatable(startCenter, Offset.VectorConverter)
}

internal class BurstParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val baseRadius: Float
)

internal class DismissBurstEffect(
    val center: Offset,
    val badgeSize: Size,
    val textLayoutResult: TextLayoutResult,
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val borderWidthPx: Float,
    val density: Float,
    val particles: List<BurstParticle>
) {
    var popScale by mutableFloatStateOf(1f)
    var popAlpha by mutableFloatStateOf(1f)
    var burstLife by mutableFloatStateOf(1f)
    // 仅在 Canvas draw 内读取，让 60fps 粒子帧循环只重绘不重组
    var frameTick by mutableIntStateOf(0)
}

@Stable
class DragBadgeHostState internal constructor(
    private val scope: CoroutineScope
) {
    internal var hostCoordinates: LayoutCoordinates? = null
    internal var activeDrag by mutableStateOf<ActiveDragBadge?>(null)
    internal val springEffects = mutableStateListOf<SpringBackBadge>()
    internal val dismissEffects = mutableStateListOf<DismissBurstEffect>()

    internal fun canStartDrag(): Boolean = activeDrag == null

    internal fun startDrag(badge: ActiveDragBadge) {
        activeDrag = badge
    }

    internal fun updateDrag(token: Any, dragCenter: Offset, broken: Boolean) {
        val current = activeDrag ?: return
        if (current.token !== token) return
        current.dragCenter = dragCenter
        current.broken = broken
    }

    internal fun cancelActiveDrag(token: Any) {
        if (activeDrag?.token === token) {
            activeDrag = null
        }
    }

    internal fun releaseToSpring(token: Any, onFinished: () -> Unit) {
        val current = activeDrag
        if (current == null || current.token !== token) {
            onFinished()
            return
        }
        activeDrag = null
        val start = current.dragCenter
        if ((start - current.anchorCenter).getDistance() < 1f) {
            onFinished()
            return
        }
        val springBadge = SpringBackBadge(
            anchorCenter = current.anchorCenter,
            startCenter = start,
            broken = current.broken,
            badgeSize = current.badgeSize,
            textLayoutResult = current.textLayoutResult,
            containerColor = current.containerColor,
            contentColor = current.contentColor,
            borderColor = current.borderColor,
            borderWidthPx = current.borderWidthPx,
            baseAnchorRadius = current.baseAnchorRadius,
            minAnchorRadius = current.minAnchorRadius,
            dragRadius = current.dragRadius,
            minGooDistance = current.minGooDistance
        )
        springEffects.add(springBadge)
        scope.launch {
            try {
                springBadge.center.animateTo(
                    targetValue = springBadge.anchorCenter,
                    animationSpec = spring(
                        dampingRatio = SPRING_DAMPING_RATIO,
                        stiffness = SPRING_STIFFNESS
                    )
                )
            } finally {
                springEffects.remove(springBadge)
                onFinished()
            }
        }
    }

    internal fun dismissDrag(token: Any, density: Float) {
        val current = activeDrag ?: return
        if (current.token !== token) return
        activeDrag = null
        val center = current.dragCenter
        val particles = List(BURST_PARTICLE_COUNT) {
            val angle = Random.nextFloat() * (2f * PI.toFloat())
            val speed = (2.2f + Random.nextFloat() * 2.4f) * density
            BurstParticle(
                x = center.x,
                y = center.y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                baseRadius = (2.5f + Random.nextFloat() * 2.2f) * density
            )
        }
        val effect = DismissBurstEffect(
            center = center,
            badgeSize = current.badgeSize,
            textLayoutResult = current.textLayoutResult,
            containerColor = current.containerColor,
            contentColor = current.contentColor,
            borderColor = current.borderColor,
            borderWidthPx = current.borderWidthPx,
            density = density,
            particles = particles
        )
        dismissEffects.add(effect)
        scope.launch {
            try {
                val startNanos = withFrameNanos { it }
                var lastNanos = startNanos
                while (true) {
                    val nowNanos = withFrameNanos { it }
                    val elapsedMs = (nowNanos - startNanos) / 1_000_000f
                    val dtFrames = ((nowNanos - lastNanos) / 1_000_000f) / 16.6667f
                    lastNanos = nowNanos

                    val popT = (elapsedMs / POP_DURATION_MS).coerceIn(0f, 1f)
                    effect.popScale = 1f + 0.6f * popT
                    effect.popAlpha = (1f - popT).coerceIn(0f, 1f)
                    effect.burstLife = (1f - elapsedMs / BURST_DURATION_MS).coerceIn(0f, 1f)

                    if (dtFrames > 0f) {
                        val damping = 0.92f.pow(dtFrames)
                        for (particle in effect.particles) {
                            particle.x += particle.vx * dtFrames
                            particle.y += particle.vy * dtFrames
                            particle.vx *= damping
                            particle.vy *= damping
                        }
                    }
                    effect.frameTick++

                    if (elapsedMs >= BURST_DURATION_MS) break
                }
            } finally {
                dismissEffects.remove(effect)
            }
        }
    }
}

val LocalDragBadgeHost = staticCompositionLocalOf<DragBadgeHostState?> { null }

// 全窗口覆盖层：所有可拖未读角标的拉丝、浮动角标、回弹与碎屑统一画在这里，避免被列表或面板裁剪
@Composable
fun DragBadgeOverlayHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { DragBadgeHostState(scope) }
    val gooPath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { state.hostCoordinates = it }
    ) {
        CompositionLocalProvider(LocalDragBadgeHost provides state) {
            content()
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            state.activeDrag?.let { drag ->
                val goo = computeBadgeGooGeometry(
                    anchorCenter = drag.anchorCenter,
                    dragCenter = drag.dragCenter,
                    baseAnchorRadius = drag.baseAnchorRadius,
                    minAnchorRadius = drag.minAnchorRadius,
                    dragRadius = drag.dragRadius,
                    broken = drag.broken,
                    minDistance = drag.minGooDistance
                )
                if (goo != null) {
                    drawBadgeGoo(gooPath, goo, drag.containerColor)
                }
                drawBadgePill(
                    center = drag.dragCenter,
                    badgeSize = drag.badgeSize,
                    textLayoutResult = drag.textLayoutResult,
                    containerColor = drag.containerColor,
                    contentColor = drag.contentColor,
                    borderColor = drag.borderColor,
                    borderWidthPx = drag.borderWidthPx
                )
            }

            for (spring in state.springEffects) {
                val center = spring.center.value
                val goo = computeBadgeGooGeometry(
                    anchorCenter = spring.anchorCenter,
                    dragCenter = center,
                    baseAnchorRadius = spring.baseAnchorRadius,
                    minAnchorRadius = spring.minAnchorRadius,
                    dragRadius = spring.dragRadius,
                    broken = spring.broken,
                    minDistance = spring.minGooDistance
                )
                if (goo != null) {
                    drawBadgeGoo(gooPath, goo, spring.containerColor)
                }
                drawBadgePill(
                    center = center,
                    badgeSize = spring.badgeSize,
                    textLayoutResult = spring.textLayoutResult,
                    containerColor = spring.containerColor,
                    contentColor = spring.contentColor,
                    borderColor = spring.borderColor,
                    borderWidthPx = spring.borderWidthPx
                )
            }

            for (dismiss in state.dismissEffects) {
                @Suppress("UNUSED_VARIABLE")
                val tick = dismiss.frameTick
                if (dismiss.popAlpha > 0f) {
                    drawBadgePill(
                        center = dismiss.center,
                        badgeSize = dismiss.badgeSize,
                        textLayoutResult = dismiss.textLayoutResult,
                        containerColor = dismiss.containerColor,
                        contentColor = dismiss.contentColor,
                        borderColor = dismiss.borderColor,
                        borderWidthPx = dismiss.borderWidthPx,
                        scale = dismiss.popScale,
                        alpha = dismiss.popAlpha
                    )
                }
                val life = dismiss.burstLife
                if (life > 0f) {
                    val particleColor = dismiss.containerColor.copy(alpha = dismiss.containerColor.alpha * life)
                    for (particle in dismiss.particles) {
                        drawCircle(
                            color = particleColor,
                            radius = particle.baseRadius * life + 0.5f * dismiss.density,
                            center = Offset(particle.x, particle.y)
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBadgeGoo(
    path: Path,
    goo: BadgeGooGeometry,
    color: Color
) {
    drawCircle(
        color = color,
        radius = goo.anchorRadius,
        center = goo.anchorCenter
    )
    path.reset()
    path.moveTo(goo.p1.x, goo.p1.y)
    path.quadraticTo(goo.controlPoint.x, goo.controlPoint.y, goo.p4.x, goo.p4.y)
    path.lineTo(goo.p3.x, goo.p3.y)
    path.quadraticTo(goo.controlPoint.x, goo.controlPoint.y, goo.p2.x, goo.p2.y)
    path.close()
    drawPath(path = path, color = color)
}

private fun DrawScope.drawBadgePill(
    center: Offset,
    badgeSize: Size,
    textLayoutResult: TextLayoutResult,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    borderWidthPx: Float,
    scale: Float = 1f,
    alpha: Float = 1f
) {
    val w = badgeSize.width
    val h = badgeSize.height
    val cornerRadius = CornerRadius(h / 2f, h / 2f)
    withTransform({
        translate(center.x, center.y)
        if (scale != 1f) scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawRoundRect(
            color = containerColor.copy(alpha = containerColor.alpha * alpha),
            topLeft = Offset(-w / 2f, -h / 2f),
            size = badgeSize,
            cornerRadius = cornerRadius
        )
        if (borderWidthPx > 0f && borderColor.alpha > 0f) {
            drawRoundRect(
                color = borderColor.copy(alpha = borderColor.alpha * alpha),
                topLeft = Offset(-w / 2f + borderWidthPx / 2f, -h / 2f + borderWidthPx / 2f),
                size = Size((w - borderWidthPx).coerceAtLeast(0f), (h - borderWidthPx).coerceAtLeast(0f)),
                cornerRadius = cornerRadius,
                style = Stroke(width = borderWidthPx)
            )
        }
        drawText(
            textLayoutResult = textLayoutResult,
            color = contentColor,
            topLeft = Offset(-textLayoutResult.size.width / 2f, -textLayoutResult.size.height / 2f),
            alpha = alpha
        )
    }
}
