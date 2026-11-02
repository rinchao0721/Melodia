package com.lin0721.linmusic.core.ui.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.PressDownSpec
import com.lin0721.linmusic.core.ui.theme.PressStyle
import com.lin0721.linmusic.core.ui.theme.PressUpSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 全局兜底按压反馈：轻点缩一下，按住过长按阈值再把底色熄灭成纯黑。

class MelodiaPressIndication(
    private val pressedScale: Float,
    private val dimAlpha: Float
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressHighlightNode(interactionSource, pressedScale, dimAlpha)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is MelodiaPressIndication &&
                other.pressedScale == pressedScale &&
                other.dimAlpha == dimAlpha)

    override fun hashCode(): Int = 31 * pressedScale.hashCode() + dimAlpha.hashCode()

    companion object {
        // 注入 LocalIndication 的兜底实例，取列表行档位
        val Default = MelodiaPressIndication(MelodiaPress.Row.scale, MelodiaPress.Row.highlightAlpha)
    }
}

private class PressHighlightNode(
    private val interactionSource: InteractionSource,
    private val pressedScale: Float,
    private val dimAlpha: Float
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private val scale = Animatable(1f)
    private val dim = Animatable(0f)
    private var scaleJob: Job? = null
    private var dimJob: Job? = null

    override fun onAttach() {
        val longPressDelayMs = currentValueOf(LocalViewConfiguration).longPressTimeoutMillis
        val motion = currentValueOf(LocalUiMotion)
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        scaleJob?.cancel()
                        scaleJob = coroutineScope.launch { scale.animateTo(pressedScale, motion.spec(PressDownSpec)) }
                        dimJob?.cancel()
                        // 缩放立刻响应，压暗要按住过了系统长按阈值才出现
                        dimJob = coroutineScope.launch {
                            delay(longPressDelayMs)
                            dim.animateTo(dimAlpha, motion.spec(PressDownSpec))
                        }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        // 取消尚未到点的延时，抬手立刻回落
                        scaleJob?.cancel()
                        scaleJob = coroutineScope.launch { scale.animateTo(1f, motion.spec(PressUpSpec)) }
                        dimJob?.cancel()
                        dimJob = coroutineScope.launch { dim.animateTo(0f, motion.spec(PressUpSpec)) }
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val currentScale = scale.value
        val currentDim = dim.value
        scale(currentScale, currentScale) {
            // 黑层画在 drawContent 之前：盖住的是上游的底色，文字图标随后画在黑底上照常可见
            if (currentDim > 0f) drawRect(color = Color.Black, alpha = currentDim)
            this@draw.drawContent()
        }
    }
}

// 按压缩放 + 高光 + 点击的一体化封装。
//
// 必须放在 clip / background 之前，否则底色不参与缩放。传入 shape 后本 modifier
// 自带裁剪，调用点原有的 clip 应当去掉，避免重复建层：
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(
    style: PressStyle = MelodiaPress.Row,
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    role: Role? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = rememberPressIndication(style)
    return this
        .pressDecoration(style, shape, interactionSource)
        .then(
            if (onLongClick == null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    enabled = enabled,
                    role = role,
                    onClick = onClick
                )
            } else {
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    enabled = enabled,
                    role = role,
                    onLongClick = onLongClick,
                    onClick = onClick
                )
            }
        )
}

// 仅做缩放，给自带 onClick、插不进 clickable 的组件用（Surface / Button / combinedClickable），
// 调用点需把同一个 interactionSource 一并交给该组件
@Composable
fun Modifier.pressScale(
    style: PressStyle,
    interactionSource: InteractionSource
): Modifier = pressDecoration(style, RectangleShape, interactionSource)

@Composable
private fun Modifier.pressDecoration(
    style: PressStyle,
    shape: Shape,
    interactionSource: InteractionSource
): Modifier {
    val motion = LocalUiMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed, style.scale) {
        scale.animateTo(
            targetValue = if (pressed) style.scale else 1f,
            animationSpec = if (pressed) motion.spec(PressDownSpec) else motion.spec(PressUpSpec)
        )
    }
    val needsClip = shape !== RectangleShape
    return if (style.scale == 1f && !needsClip) {
        this
    } else {
        // 在 layer 内读 Animatable，按帧只走绘制，不触发重组
        graphicsLayer {
            val current = scale.value
            scaleX = current
            scaleY = current
            this.shape = shape
            clip = needsClip
        }
    }
}

// pressable 自己用 graphicsLayer 缩放，这里的 indication 必须把缩放关掉，否则缩两次
@Composable
private fun rememberPressIndication(style: PressStyle): Indication? =
    remember(style.highlightAlpha) {
        if (style.highlightAlpha > 0f) {
            MelodiaPressIndication(pressedScale = 1f, dimAlpha = style.highlightAlpha)
        } else {
            null
        }
    }
