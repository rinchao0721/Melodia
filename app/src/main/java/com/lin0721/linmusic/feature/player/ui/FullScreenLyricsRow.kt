package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    isShareMode: Boolean = false,
    selectedLyricKeys: Set<String> = emptySet(),
    onToggleSelect: ((String) -> Unit)? = null,
    onLyricLongPress: ((String) -> Unit)? = null,
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
        isShareMode -> 1f
        isCurrent -> 1.10f
        isCenterTarget -> 1.04f
        else -> when (distance) {
            1 -> 0.98f
            2 -> 0.94f
            else -> 0.90f
        }
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 280f),
        label = "fs_lyric_scale_$index"
    )

    val mainKey = "${index}_main"
    val secKey = "${index}_sec"
    val bgKey = "${index}_bg"
    val isMainSelected = mainKey in selectedLyricKeys
    val isSecSelected = secKey in selectedLyricKeys
    val isBgSelected = bgKey in selectedLyricKeys
    val hasAnySelected = isMainSelected || isSecSelected || isBgSelected

    val targetAlpha = when {
        isShareMode -> if (hasAnySelected) 1f else 0.45f
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
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 280f),
        label = "fs_lyric_alpha_$index"
    )

    val widthFraction = if (effectiveAlignment == "center") 0.9f else 1f
    val paddingStart = when (effectiveAlignment) {
        "center" -> 24.dp
        "left" -> MelodiaSpacing.md
        else -> 0.dp
    }
    val paddingEnd = when (effectiveAlignment) {
        "center" -> 24.dp
        "right" -> MelodiaSpacing.md
        else -> MelodiaSpacing.md
    }

    val checkProgress by animateFloatAsState(
        targetValue = if (isShareMode) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "fs_lyric_check_$index"
    )

    val rowHighlightAlpha by animateFloatAsState(
        targetValue = if (isShareMode && isMainSelected) 0.12f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "fs_row_bg_$index"
    )

    val secRowHighlightAlpha by animateFloatAsState(
        targetValue = if (isShareMode && isSecSelected) 0.12f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "fs_sec_row_bg_$index"
    )

    val bgRowHighlightAlpha by animateFloatAsState(
        targetValue = if (isShareMode && isBgSelected) 0.12f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "fs_bg_row_bg_$index"
    )

    // 右边距预留恰好等于勾选槽宽度（32dp）：
    // 常态下右侧预留 32dp 确保当前行即使放大 1.10x 也能在换行后完全居于屏内不超界；
    // 分享模式下左侧勾选槽滑入 32dp，右侧边距等量缩减 32dp，可用排版宽度恒定不变，无需重排折行！
    val shareSlotWidth = 32.dp
    val checkSlotWidth = (shareSlotWidth.value * checkProgress).dp
    val dynamicReservedEndMargin = (shareSlotWidth.value * (1f - checkProgress)).dp

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = contentAlignment
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .padding(
                    start = paddingStart,
                    end = paddingEnd + if (effectiveAlignment == "left") dynamicReservedEndMargin else 0.dp
                )
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    alpha = animatedAlpha
                    transformOrigin = targetTransformOrigin
                },
            horizontalAlignment = horizontalAlignment
        ) {
            // 主歌词行：支持平滑推移动画出现勾选框，主副行独立多选
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = rowHighlightAlpha))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (isShareMode) {
                                onToggleSelect?.invoke(mainKey)
                            } else {
                                onClick()
                            }
                        },
                        onLongClick = {
                            if (!isShareMode) {
                                onLyricLongPress?.invoke(mainKey)
                            }
                        }
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                if (checkProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .width(checkSlotWidth)
                            .height(24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (isMainSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "已选择",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer {
                                        alpha = checkProgress.coerceIn(0f, 1f)
                                        scaleX = 0.5f + 0.5f * checkProgress
                                        scaleY = 0.5f + 0.5f * checkProgress
                                    }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (!isShareMode && isCurrent && line.words.isNotEmpty()) {
                        textBounds?.mainLayout = null
                        Box(modifier = reportMainBounds) {
                            KaraokeLyricRow(
                                line = line,
                                currentPositionProvider = currentPositionProvider,
                                inactiveColor = highlightColor.copy(alpha = 0.5f),
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
                            color = if (isShareMode) {
                                if (isMainSelected) Color.White else Color.White.copy(alpha = 0.45f)
                            } else {
                                if (isCurrent) Color.White else highlightColor
                            },
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = textAlign,
                            softWrap = true,
                            onTextLayout = { textBounds?.mainLayout = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(reportMainBounds)
                        )
                    }
                }

                if (effectiveAlignment == "center" && checkProgress > 0f) {
                    Spacer(modifier = Modifier.width(checkSlotWidth))
                }
            }

            val secondaryText = when (secondaryMode) {
                "translation" -> line.translation
                "roma" -> line.roma
                else -> null
            }
            if (secondaryText != null) {
                Spacer(modifier = Modifier.height(spacingBetween))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = secRowHighlightAlpha))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (isShareMode) {
                                    onToggleSelect?.invoke(secKey)
                                } else {
                                    onClick()
                                }
                            },
                            onLongClick = {
                                if (!isShareMode) {
                                    onLyricLongPress?.invoke(secKey)
                                }
                            }
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    if (checkProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .width(checkSlotWidth)
                                .height(20.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (isSecSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "已选择",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .graphicsLayer {
                                            alpha = checkProgress.coerceIn(0f, 1f)
                                            scaleX = 0.5f + 0.5f * checkProgress
                                            scaleY = 0.5f + 0.5f * checkProgress
                                        }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        Text(
                            text = secondaryText,
                            fontSize = translationFontSize,
                            lineHeight = translationLineHeight,
                            color = if (isShareMode) {
                                if (isSecSelected) Color.White else Color.White.copy(alpha = 0.45f)
                            } else {
                                if (isCurrent) Color.White else highlightColor
                            },
                            fontWeight = FontWeight.Normal,
                            textAlign = textAlign,
                            softWrap = true,
                            onTextLayout = { textBounds?.secondaryLayout = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(reportSecondaryBounds)
                        )
                    }

                    if (effectiveAlignment == "center" && checkProgress > 0f) {
                        Spacer(modifier = Modifier.width(checkSlotWidth))
                    }
                }
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
                val bgTextColor = if (isShareMode) {
                    if (isBgSelected) Color.White else Color.White.copy(alpha = 0.45f)
                } else {
                    if (isCurrent) backgroundActiveColor else backgroundInactiveColor
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = bgRowHighlightAlpha))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (isShareMode) {
                                    onToggleSelect?.invoke(bgKey)
                                } else {
                                    onClick()
                                }
                            },
                            onLongClick = {
                                if (!isShareMode) {
                                    onLyricLongPress?.invoke(bgKey)
                                }
                            }
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    if (checkProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .width(checkSlotWidth)
                                .height(20.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (isBgSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "已选择",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .graphicsLayer {
                                            alpha = checkProgress.coerceIn(0f, 1f)
                                            scaleX = 0.5f + 0.5f * checkProgress
                                            scaleY = 0.5f + 0.5f * checkProgress
                                        }
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = when (backgroundTextAlign) {
                            TextAlign.Center -> Alignment.CenterHorizontally
                            TextAlign.End -> Alignment.End
                            else -> Alignment.Start
                        }
                    ) {
                        if (!isShareMode && background.words.isNotEmpty()) {
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
                                color = bgTextColor,
                                fontWeight = FontWeight.Bold,
                                textAlign = backgroundTextAlign,
                                softWrap = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        background.translation?.let {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = it,
                                fontSize = (fontSize - 8).coerceAtLeast(11).sp,
                                color = bgTextColor,
                                textAlign = backgroundTextAlign,
                                softWrap = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        background.roma?.let {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = it,
                                fontSize = (fontSize - 9).coerceAtLeast(11).sp,
                                color = bgTextColor,
                                textAlign = backgroundTextAlign,
                                softWrap = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (effectiveAlignment == "center" && checkProgress > 0f) {
                        Spacer(modifier = Modifier.width(checkSlotWidth))
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
