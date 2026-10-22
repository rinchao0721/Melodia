package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.domain.LyricAlignment
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 歌词单行：按距当前行的远近做缩放与透明度递减，当前行走逐字流光，可附带译文/罗马音与背景和声
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
    secondarySpacing: Int = 6,
    advancedKaraokeEffect: Boolean = true,
    karaokeGlowEffect: Boolean = false,
    isPlaying: Boolean = true,
    // 宽屏按下点命中判定用：上报本行文字实际占据的范围（根坐标），离开组合时上报 Rect.Zero
    onTextBoundsInRoot: ((Rect) -> Unit)? = null,
    onClick: () -> Unit
) {
    val textBounds = if (onTextBoundsInRoot != null) remember { LyricTextBounds() } else null
    if (onTextBoundsInRoot != null) {
        DisposableEffect(Unit) {
            onDispose { onTextBoundsInRoot(Rect.Zero) }
        }
    }
    val reportMainBounds: Modifier = if (textBounds != null && onTextBoundsInRoot != null) {
        Modifier.onGloballyPositioned { coords ->
            textBounds.main = textBounds.mainLayout?.let { inkBoundsInRoot(it, coords) } ?: coords.boundsInRoot()
            onTextBoundsInRoot(textBounds.union())
        }
    } else Modifier
    val reportSecondaryBounds: Modifier = if (textBounds != null && onTextBoundsInRoot != null) {
        Modifier.onGloballyPositioned { coords ->
            textBounds.secondary = textBounds.secondaryLayout?.let { inkBoundsInRoot(it, coords) }
            onTextBoundsInRoot(textBounds.union())
        }
    } else Modifier

    // 右对齐时交换主声部和对唱声部的位置；居中时两者都居中。
    val effectiveAlignment = when {
        alignment == "center" -> "center"
        alignment == "right" && line.alignment == LyricAlignment.END -> "left"
        line.alignment == LyricAlignment.END -> "right"
        else -> alignment
    }
    val textAlign = when (effectiveAlignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.End
        else -> TextAlign.Start
    }
    // 缩放不改变布局，所以得靠外层 Box 把定宽列推到正确的一侧
    val contentAlignment = when (effectiveAlignment) {
        "center" -> Alignment.Center
        "right" -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }
    val horizontalAlignment = when (effectiveAlignment) {
        "center" -> Alignment.CenterHorizontally
        "right" -> Alignment.End
        else -> Alignment.Start
    }
    val targetTransformOrigin = when (effectiveAlignment) {
        "center" -> TransformOrigin(0.5f, 0.5f)
        "right" -> TransformOrigin(1f, 0.5f)
        else -> TransformOrigin(0f, 0.5f)
    }

    val mainFontSize = fontSize.sp
    val translationFontSize = (fontSize - 5).coerceAtLeast(12).sp
    val mainLineHeight = (fontSize * 1.35f).sp
    val translationLineHeight = (translationFontSize.value * 1.35f).sp
    val spacingBetween = secondarySpacing.coerceAtLeast(0).dp
    val backgroundFontSize = (fontSize - 5).coerceAtLeast(12).sp
    val backgroundLineHeight = (backgroundFontSize.value * 1.4f).sp

    val targetScale = when {
        isCurrent -> 1.12f
        isCenterTarget -> 1.04f
        else -> when (distance) {
            1 -> 0.98f
            2 -> 0.94f
            else -> 0.90f
        }
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 320f),
        label = "fs_lyric_scale_$index"
    )

    val targetAlpha = when {
        isCurrent -> 1f
        isCenterTarget -> 0.95f
        else -> when (distance) {
            1 -> 0.65f
            2 -> 0.45f
            else -> 0.28f
        }
    }
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "fs_lyric_alpha_$index"
    )

    val widthFraction = if (effectiveAlignment == "center") 0.9f else 0.85f
    val paddingStart = when (effectiveAlignment) {
        "center" -> 24.dp
        "left" -> MelodiaSpacing.md
        else -> 0.dp
    }
    val paddingEnd = when (effectiveAlignment) {
        "center" -> 24.dp
        "right" -> MelodiaSpacing.md
        else -> 0.dp
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = contentAlignment
    ) {
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
            if (line.words.isNotEmpty()) {
                // 保留逐字组件和排版缓存，换句只切换高亮层，避免重建时闪烁。
                // 逐字高亮行拿不到排版结果，命中范围退化为整行
                textBounds?.mainLayout = null
                Box(modifier = reportMainBounds) {
                    KaraokeLyricRow(
                        line = line,
                        currentPositionProvider = currentPositionProvider,
                        inactiveColor = highlightColor,
                        activeColor = Color.White,
                        fontSize = mainFontSize,
                        lineHeight = mainLineHeight,
                        textAlign = textAlign,
                        advancedEffect = advancedKaraokeEffect,
                        glowEffect = karaokeGlowEffect,
                        isPlaying = isPlaying,
                        isActive = isCurrent
                    )
                }
            } else {
                Text(
                    text = line.text,
                    fontSize = mainFontSize,
                    lineHeight = mainLineHeight,
                    color = if (isCurrent) Color.White else highlightColor,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = textAlign,
                    onTextLayout = { textBounds?.mainLayout = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(reportMainBounds)
                )
            }

            val secondaryText = when (secondaryMode) {
                "translation" -> line.translation
                "roma" -> line.roma
                else -> null
            }
            if (secondaryText != null) {
                Spacer(modifier = Modifier.height(spacingBetween))
                Text(
                    text = secondaryText,
                    fontSize = translationFontSize,
                    lineHeight = translationLineHeight,
                    color = if (isCurrent) Color.White else highlightColor,
                    textAlign = textAlign,
                    onTextLayout = { textBounds?.secondaryLayout = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(reportSecondaryBounds)
                )
            } else {
                textBounds?.secondary = null
            }

            // AMLL TTML 的背景和声行（m:role="x-bg"）：字号更小、颜色更淡，
            // 整体缩进到 90% 宽并按自身对齐方式摆放，避免和主声部抢视觉重心。
            line.backgroundLine?.let { background ->
                // 未激活和本行激活但和声尚未开唱时使用同一底色，避免状态切换变色。
                val backgroundInactiveColor = Color.White.copy(alpha = 0.22f)
                val backgroundActiveColor = Color.White.copy(alpha = 0.82f)
                val backgroundTextAlign = textAlign
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalAlignment = when (backgroundTextAlign) {
                        TextAlign.Center -> Alignment.CenterHorizontally
                        TextAlign.End -> Alignment.End
                        else -> Alignment.Start
                    }
                ) {
                    if (background.words.isNotEmpty()) {
                        KaraokeLyricRow(
                            line = background,
                            currentPositionProvider = currentPositionProvider,
                            inactiveColor = backgroundInactiveColor,
                            activeColor = backgroundActiveColor,
                            fontSize = backgroundFontSize,
                            lineHeight = backgroundLineHeight,
                            textAlign = backgroundTextAlign,
                            advancedEffect = advancedKaraokeEffect,
                            glowEffect = karaokeGlowEffect,
                            isPlaying = isPlaying,
                            fontWeight = FontWeight.Bold,
                            isActive = isCurrent
                        )
                    } else {
                        Text(
                            text = background.text,
                            fontSize = backgroundFontSize,
                            lineHeight = backgroundLineHeight,
                            color = if (isCurrent) backgroundActiveColor else backgroundInactiveColor,
                            fontWeight = FontWeight.Bold,
                            textAlign = backgroundTextAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    background.translation?.let {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = it,
                            fontSize = (fontSize - 8).coerceAtLeast(11).sp,
                            color = if (isCurrent) backgroundActiveColor else backgroundInactiveColor,
                            textAlign = backgroundTextAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    background.roma?.let {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = it,
                            fontSize = (fontSize - 9).coerceAtLeast(11).sp,
                            color = if (isCurrent) backgroundActiveColor else backgroundInactiveColor,
                            textAlign = backgroundTextAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// 同一行的正文与翻译各自的排版结果和实际文字范围，上报时取并集
private class LyricTextBounds {
    var mainLayout: TextLayoutResult? = null
    var secondaryLayout: TextLayoutResult? = null
    var main: Rect? = null
    var secondary: Rect? = null

    fun union(): Rect {
        val a = main
        val b = secondary
        return when {
            a != null && b != null -> Rect(
                left = minOf(a.left, b.left),
                top = minOf(a.top, b.top),
                right = maxOf(a.right, b.right),
                bottom = maxOf(a.bottom, b.bottom)
            )
            else -> a ?: b ?: Rect.Zero
        }
    }
}

// 按每一行的左右边界取文字实际占据的范围（而非铺满整行的控件宽度），再换算到根坐标
private fun inkBoundsInRoot(layout: TextLayoutResult, coords: LayoutCoordinates): Rect {
    if (layout.lineCount == 0) return Rect.Zero
    var left = Float.MAX_VALUE
    var right = 0f
    for (line in 0 until layout.lineCount) {
        left = minOf(left, layout.getLineLeft(line))
        right = maxOf(right, layout.getLineRight(line))
    }
    return Rect(
        topLeft = coords.localToRoot(Offset(left, 0f)),
        bottomRight = coords.localToRoot(Offset(right, layout.size.height.toFloat()))
    )
}
