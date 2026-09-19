package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.domain.LyricLine

private const val TAG = "KaraokeLyricRow"

// 逐字词的物理渲染坐标缓存，避免每帧重复调用 getBoundingBox 的 JNI 开销
private class WordLayout(
    val startMs: Long,
    val endMs: Long,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val lineIndex: Int
)

private class LineLayout(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float
)

private class LyricLayoutInfo(
    val wordLayouts: List<WordLayout>,
    val lineLayouts: List<LineLayout>
)

// ────────────────────────────────────────────────────────────────────────────
// 逐字高亮（卡拉OK式）歌词行
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun KaraokeLyricRow(
    line: LyricLine,
    currentPositionProvider: () -> Long,
    inactiveColor: Color,
    activeColor: Color,
    fontSize: TextUnit = 22.sp,
    textAlign: TextAlign = TextAlign.Start
) {
    var textLayoutResult by remember(line) { mutableStateOf<TextLayoutResult?>(null) }
    val currentPositionProviderState = rememberUpdatedState(currentPositionProvider)

    // 在排版结果解析后，仅计算并缓存一次每个字词与行的物理渲染坐标，彻底避免每帧重复调用 getBoundingBox 的 JNI 开销
    val lyricLayoutInfo = remember(line, textLayoutResult) {
        val layout = textLayoutResult
        if (layout == null) null else {
            val textLength = line.text.length
            var currentSearchIndex = 0
            val wordRanges = line.words.map { word ->
                val startIndex = line.text.indexOf(word.text, currentSearchIndex)
                if (startIndex != -1) {
                    currentSearchIndex = startIndex + word.text.length
                    startIndex until currentSearchIndex
                } else {
                    val start = currentSearchIndex
                    currentSearchIndex = (currentSearchIndex + word.text.length).coerceAtMost(textLength)
                    start until currentSearchIndex
                }
            }

            val wordLayouts = line.words.mapIndexed { i, word ->
                val range = wordRanges[i]
                val lineIndex = layout.getLineForOffset(range.first)
                val lineTop = layout.getLineTop(lineIndex)
                val lineBottom = layout.getLineBottom(lineIndex)

                val wordLeft = try {
                    layout.getBoundingBox(range.first).left
                } catch (e: Exception) {
                    AppLogger.d(TAG, "歌词字符定位 getBoundingBox 失败，回退 getHorizontalPosition", e)
                    layout.getHorizontalPosition(range.first, true)
                }
                val wordRight = try {
                    layout.getBoundingBox(range.last).right
                } catch (e: Exception) {
                    AppLogger.d(TAG, "歌词字符定位 getBoundingBox 失败，回退 getHorizontalPosition", e)
                    layout.getHorizontalPosition(range.last + 1, true)
                }

                WordLayout(
                    startMs = word.startOffsetMs,
                    endMs = word.startOffsetMs + word.durationMs,
                    left = wordLeft,
                    right = wordRight,
                    top = lineTop,
                    bottom = lineBottom,
                    lineIndex = lineIndex
                )
            }

            val lineLayouts = (0 until layout.lineCount).map { lineIndex ->
                LineLayout(
                    left = layout.getLineLeft(lineIndex),
                    right = layout.getLineRight(lineIndex),
                    top = layout.getLineTop(lineIndex),
                    bottom = layout.getLineBottom(lineIndex)
                )
            }

            LyricLayoutInfo(wordLayouts, lineLayouts)
        }
    }

    val boxAlignment = when (textAlign) {
        TextAlign.Center -> Alignment.Center
        TextAlign.End -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = boxAlignment
    ) {
        // 底层灰色（未激活）歌词
        Text(
            text = line.text,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            color = inactiveColor,
            textAlign = textAlign,
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier.fillMaxWidth()
        )

        // 顶层高亮（已激活）歌词，通过 Path 对每一行分别建立独立的裁剪矩形，防止单行歌词折行时产生漏光和干扰
        Text(
            text = line.text,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            color = activeColor,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val info = lyricLayoutInfo
                    if (info == null) {
                        alpha = 0f
                    } else {
                        alpha = 1f
                        clip = true
                        shape = object : Shape {
                            override fun createOutline(
                                size: Size,
                                layoutDirection: LayoutDirection,
                                density: Density
                            ): Outline {
                                val path = androidx.compose.ui.graphics.Path()
                                val relativeProgress = currentPositionProviderState.value() - line.timeMs

                                info.lineLayouts.forEachIndexed { lineIndex, lineLayout ->
                                    val lastWordOnLine = info.wordLayouts.lastOrNull { it.lineIndex == lineIndex }
                                    var maxRight = lineLayout.left

                                    if (lastWordOnLine != null && relativeProgress >= lastWordOnLine.endMs) {
                                        // 整行已唱完，直接拉满高亮
                                        maxRight = lineLayout.right
                                    } else {
                                        info.wordLayouts.forEach { word ->
                                            if (word.lineIndex == lineIndex) {
                                                if (relativeProgress >= word.endMs) {
                                                    maxRight = maxRight.coerceAtLeast(word.right)
                                                } else if (relativeProgress in word.startMs..word.endMs) {
                                                    // 在当前唱到的字词内进行线性像素高亮插值
                                                    val ratio = (relativeProgress - word.startMs).toFloat() / (word.endMs - word.startMs)
                                                    val currentWordRight = word.left + (word.right - word.left) * ratio
                                                    maxRight = maxRight.coerceAtLeast(currentWordRight)
                                                }
                                            }
                                        }
                                    }

                                    // 如果当前行存在已播放高亮范围，则将其生成裁剪矩形加入到 Path 中
                                    if (maxRight > lineLayout.left) {
                                        path.addRect(
                                            Rect(
                                                left = lineLayout.left,
                                                top = lineLayout.top,
                                                right = maxRight.coerceIn(lineLayout.left, lineLayout.right),
                                                bottom = lineLayout.bottom
                                            )
                                        )
                                    }
                                }

                                return Outline.Generic(path)
                            }
                        }
                    }
                }
        )
    }
}
