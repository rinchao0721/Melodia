package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 缩放/透明度动画值只在 graphicsLayer 块内读取，变化时仅刷新绘制阶段
@Composable
fun FullScreenLyricsRow(
    index: Int,
    line: LyricLine,
    isCurrent: Boolean,
    isCenterTarget: Boolean,
    distance: Int,
    highlightColor: Color,
    currentPositionProvider: () -> Long,
    fontSize: Int = 22,
    alignment: String = "left",
    secondaryMode: String = "translation",
    onClick: () -> Unit
) {
    val textAlign = when (alignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.End
        else -> TextAlign.Start
    }
    val horizontalAlignment = when (alignment) {
        "center" -> Alignment.CenterHorizontally
        "right" -> Alignment.End
        else -> Alignment.Start
    }
    val targetTransformOrigin = when (alignment) {
        "center" -> TransformOrigin(0.5f, 0.5f)
        "right" -> TransformOrigin(1f, 0.5f)
        else -> TransformOrigin(0f, 0.5f)
    }
    val mainFontSize = fontSize.sp
    val translationFontSize = (fontSize - 5).coerceAtLeast(12).sp

    val targetScale = if (isCurrent) 1.15f
                      else if (isCenterTarget) 1.05f
                      else (1f - distance * 0.05f).coerceAtLeast(0.82f)
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "fs_lyric_scale_$index"
    )

    val targetAlpha = if (isCurrent) 1f else if (isCenterTarget) 0.95f else 0.85f
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(250),
        label = "fs_lyric_alpha_$index"
    )

    val widthFraction = if (alignment == "center") 0.9f else 0.85f
    val paddingStart = when (alignment) {
        "center" -> 24.dp
        "left" -> MelodiaSpacing.md
        else -> 0.dp
    }
    val paddingEnd = when (alignment) {
        "center" -> 24.dp
        "right" -> MelodiaSpacing.md
        else -> 0.dp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .padding(start = paddingStart, end = paddingEnd)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = animatedAlpha
                transformOrigin = targetTransformOrigin
            }
            .pressable(MelodiaPress.None) {
                onClick()
            },
        horizontalAlignment = horizontalAlignment
    ) {
        if (isCurrent && line.words.isNotEmpty()) {
            KaraokeLyricRow(
                line = line,
                currentPositionProvider = currentPositionProvider,
                inactiveColor = highlightColor.copy(alpha = 0.5f),
                activeColor = Color.White,
                fontSize = mainFontSize,
                textAlign = textAlign
             )
        } else {
            Text(
                text = line.text,
                fontSize = mainFontSize,
                color = if (isCurrent) Color.White else highlightColor,
                fontWeight = FontWeight.ExtraBold,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
        val secondaryText = when (secondaryMode) {
            "translation" -> line.translation
            "roma" -> line.roma
            else -> null
        }
        if (secondaryText != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = secondaryText,
                fontSize = translationFontSize,
                color = if (isCurrent) Color.White else highlightColor,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
