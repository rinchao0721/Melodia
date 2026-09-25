package com.lin0721.linmusic.feature.recognition.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.SurfaceLight
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.feature.recognition.engine.RecognitionSession
import kotlin.math.sqrt

internal const val LEVELS_PER_SECOND = RecognitionSession.LEVEL_COUNT / RecognitionSession.MAX_SECONDS

enum class WaveformHighlight {
    // 聆听中：红框圈出正在匹配的窗口
    Frame,
    // 结果页：命中窗口的柱子染红
    Bars
}

// 麦克风 RMS 多在 0.001~0.1，开方压缩动态范围后再映射到柱高
private fun levelToFraction(level: Float): Float =
    (sqrt(level.coerceAtLeast(0f)) * 3.2f).coerceIn(0.06f, 1f)

@Composable
internal fun RecognitionWaveform(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    highlightStartSecond: Int? = null,
    highlight: WaveformHighlight = WaveformHighlight.Frame,
    showPendingSlots: Boolean = true,
    barColor: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val slots = RecognitionSession.LEVEL_COUNT
        val slotWidth = size.width / slots
        val barWidth = slotWidth * 0.58f
        val radius = CornerRadius(barWidth / 2, barWidth / 2)
        val highlightRange = highlightStartSecond?.let {
            val from = it * LEVELS_PER_SECOND
            from until from + RecognitionSession.WINDOW_SECONDS * LEVELS_PER_SECOND
        }

        if (highlight == WaveformHighlight.Frame && highlightRange != null) {
            val inset = 4.dp.toPx()
            val left = highlightRange.first * slotWidth - inset / 2
            val width = (highlightRange.last + 1 - highlightRange.first) * slotWidth
            val corner = CornerRadius(12.dp.toPx(), 12.dp.toPx())
            drawRoundRect(
                color = NeteaseRed.copy(alpha = 0.16f),
                topLeft = Offset(left, 0f),
                size = Size(width, size.height),
                cornerRadius = corner
            )
            drawRoundRect(
                color = NeteaseRed.copy(alpha = 0.75f),
                topLeft = Offset(left, 0f),
                size = Size(width, size.height),
                cornerRadius = corner,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        val maxBarHeight = size.height * 0.84f
        val pendingHeight = 6.dp.toPx().coerceAtMost(maxBarHeight)
        val lastSlot = if (showPendingSlots) slots else levels.size.coerceAtMost(slots)
        for (index in 0 until lastSlot) {
            val recorded = index < levels.size
            val height = if (recorded) maxBarHeight * levelToFraction(levels[index]) else pendingHeight
            val color = when {
                !recorded -> SurfaceLight
                highlight == WaveformHighlight.Bars && highlightRange != null && index in highlightRange -> NeteaseRed
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

// 0s ~ 8s 刻度，与波形等宽摆放
@Composable
internal fun RecognitionTimeAxis(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        for (second in 0..RecognitionSession.MAX_SECONDS) {
            Text(text = "${second}s", color = TextGray, fontSize = 11.sp)
        }
    }
}
