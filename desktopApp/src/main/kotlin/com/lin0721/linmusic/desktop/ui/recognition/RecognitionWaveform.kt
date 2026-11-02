package com.lin0721.linmusic.desktop.ui.recognition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.recognition.engine.RecognitionSession
import kotlin.math.sqrt

private const val LEVELS_PER_SECOND = RecognitionSession.LEVEL_COUNT / RecognitionSession.MAX_SECONDS

internal enum class WaveformHighlight {
    // 聆听中：红框圈出正在匹配的窗口
    Frame,
    // 结果：命中窗口的柱子染红
    Bars
}

// RMS 多在 0.001~0.1，开方压缩动态范围后再映射到柱高；0 表示尚未录到
private fun levelToFraction(level: Float): Float =
    (sqrt(level.coerceAtLeast(0f)) * 3.2f).coerceIn(0.06f, 1f)

// 8 秒录音按固定格数绘制；新录到的柱子从占位高度长出，匹配框随窗口平滑滑动
@Composable
internal fun RecognitionWaveform(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    highlightStartSecond: Int? = null,
    highlight: WaveformHighlight = WaveformHighlight.Frame,
    barColor: Color = DesktopColors.TextPrimary
) {
    val slots = RecognitionSession.LEVEL_COUNT
    val heights: List<State<Float>> = List(slots) { index ->
        animateFloatAsState(
            targetValue = levels.getOrNull(index)?.let(::levelToFraction) ?: 0f,
            animationSpec = uiTween(180),
            label = "waveformBar"
        )
    }
    // 匹配框消失时停在原位淡出，不跳回起点
    val lastStart = remember { FloatArray(1) }
    highlightStartSecond?.let { lastStart[0] = it.toFloat() }
    val frameStart by animateFloatAsState(lastStart[0], uiTween(260), label = "waveformFrameStart")
    val frameAlpha by animateFloatAsState(if (highlightStartSecond != null) 1f else 0f, uiTween(200), label = "waveformFrameAlpha")
    val highlightRange = highlightStartSecond?.let {
        val from = it * LEVELS_PER_SECOND
        from until from + RecognitionSession.WINDOW_SECONDS * LEVELS_PER_SECOND
    }

    val accent = DesktopColors.Accent
    val pendingColor = DesktopColors.SurfaceLight
    Canvas(modifier) {
        val slotWidth = size.width / slots
        val barWidth = slotWidth * 0.58f
        val radius = CornerRadius(barWidth / 2, barWidth / 2)

        if (highlight == WaveformHighlight.Frame && frameAlpha > 0f) {
            val left = frameStart * LEVELS_PER_SECOND * slotWidth
            val width = RecognitionSession.WINDOW_SECONDS * LEVELS_PER_SECOND * slotWidth
            val corner = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            drawRoundRect(
                color = accent.copy(alpha = 0.16f * frameAlpha),
                topLeft = Offset(left, 0f),
                size = Size(width, size.height),
                cornerRadius = corner
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.75f * frameAlpha),
                topLeft = Offset(left, 0f),
                size = Size(width, size.height),
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        val maxBarHeight = size.height * 0.84f
        val pendingHeight = 4.dp.toPx().coerceAtMost(maxBarHeight)
        for (index in 0 until slots) {
            val recorded = index < levels.size
            val height = maxOf(pendingHeight, maxBarHeight * heights[index].value)
            val color = when {
                !recorded -> pendingColor
                highlight == WaveformHighlight.Bars && highlightRange != null && index in highlightRange -> accent
                else -> barColor
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(index * slotWidth + (slotWidth - barWidth) / 2, (size.height - height) / 2),
                size = Size(barWidth, height),
                cornerRadius = radius
            )
        }
    }
}

// 0s ~ 8s 刻度，与波形等宽
@Composable
internal fun RecognitionTimeAxis(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (second in 0..RecognitionSession.MAX_SECONDS) {
            Text("${second}s", color = DesktopColors.TextGray, fontSize = 11.sp)
        }
    }
}
