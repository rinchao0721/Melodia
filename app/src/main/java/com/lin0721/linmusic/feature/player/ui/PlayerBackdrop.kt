package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.theme.darken
import com.lin0721.linmusic.core.ui.theme.saturate

enum class BackdropMode { Collapsed, Immersive }

// 用原生 Modifier.blur 软化渐变/光斑
private val BACKDROP_BLUR_RADIUS = 60.dp

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

// 全屏播放器背景：Collapsed（Hero）单色 alpha 渐隐，Immersive（歌词全屏）单色相双光斑游走，两者都做模糊处理
// 背景绘制和 content 拆成两个子 Box：只模糊背景层，content（Immersive 态下是实际歌词内容）保持清晰
@Composable
fun PlayerBackdrop(
    base: Color,
    mode: BackdropMode,
    modifier: Modifier = Modifier,
    translationYProvider: () -> Float = { 0f },
    content: @Composable BoxScope.() -> Unit = {}
) {
    when (mode) {
        BackdropMode.Collapsed -> {
            val density = LocalDensity.current
            val infiniteTransition = rememberInfiniteTransition(label = "bg_breathe")
            val gradientEndYDp by infiniteTransition.animateFloat(
                initialValue = 1050f,
                targetValue = 1150f,
                animationSpec = infiniteRepeatable(
                    animation = tween(8000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "gradient_end"
            )
            val gradientEndY = with(density) { gradientEndYDp.dp.toPx() }

            // 跟歌单页顶栏/搜索栏/大封面渐变同一套处理：base 直接用太亮，先压暗一档
            val fillColor = remember(base) { base.darken(0.35f) }

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(1200.dp)
                    .graphicsLayer { translationY = translationYProvider() }
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(BACKDROP_BLUR_RADIUS)
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to fillColor,
                                    0.2f to fillColor.copy(alpha = 0.75f),
                                    0.45f to fillColor.copy(alpha = 0.45f),
                                    0.62f to fillColor.copy(alpha = 0.08f),
                                    0.76f to Color.Transparent,
                                    startY = 0f,
                                    endY = gradientEndY
                                )
                            )
                        }
                )
                content()
            }
        }

        BackdropMode.Immersive -> {
            val infiniteTransition = rememberInfiniteTransition(label = "fluid_mesh_fullscreen")

            val darkCenterX by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.4f,
                animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse),
                label = "dark_x"
            )
            val darkCenterY by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
                label = "dark_y"
            )
            val darkRadiusScale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse),
                label = "dark_radius"
            )

            val vividBase = remember(base) { base.saturate(0.6f) }
            val fillColor = remember(vividBase) { vividBase.darken(0.35f) }
            val darkBlob = remember(vividBase) { vividBase.darken(0.15f) }

            Box(modifier = modifier) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(BACKDROP_BLUR_RADIUS)
                        .drawBehind {
                            val baseSize = size.minDimension
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
