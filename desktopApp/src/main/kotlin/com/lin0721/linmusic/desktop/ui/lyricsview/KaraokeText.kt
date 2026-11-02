package com.lin0721.linmusic.desktop.ui.lyricsview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.domain.LyricLine
import kotlin.math.PI
import kotlin.math.sin

private const val TAG = "KaraokeText"

// 扫色分界处的羽化带总宽度：过大像光晕、过小退化成硬竖线，16dp 约一个字宽，观感最轻微
private val KaraokeEdgeFeather: Dp = 16.dp

// 逐字高亮（卡拉 OK 式）歌词行：灰色底层 + 白色上层，上层按已唱范围逐帧裁剪并在分界处羽化。
// 进度只在绘制阶段读取，不触发重组；每帧重绘由帧心跳驱动
@Composable
fun KaraokeText(
    line: LyricLine,
    positionProvider: () -> Long,
    inactiveColor: Color,
    activeColor: Color,
    fontSize: TextUnit,
    // 行高必须大于字形实际纵向跨度，让相邻视觉行的行盒互不重叠：裁剪矩形与羽化带都按行盒作用，
    // 行盒一旦重叠，已播行的白字会染到未播行的升部，且矩形裁剪无法在重叠像素上同时满足两行
    lineHeight: TextUnit,
    textAlign: TextAlign,
    isPlaying: Boolean,
    // 关闭流光只去掉柔边羽化，仍按逐字时间裁剪并逐帧推进
    advancedEffect: Boolean = true,
    // 演唱中的字词叠加呼吸高亮微光
    glowEffect: Boolean = false,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    // 只加在底层未播文字上，已播层裁剪覆盖在其上，阴影不会叠两遍
    shadow: Shadow? = null
) {
    var textLayoutResult by remember(line) { mutableStateOf<TextLayoutResult?>(null) }
    val positionState = rememberUpdatedState(positionProvider)
    // 裁剪路径复用：矩形每帧重建，Path 背后是原生对象，每帧新建会持续制造 GC 压力
    val clipPath = remember { Path() }
    // 逐帧心跳：写入每帧都不同的帧时间戳，供绘制阶段读取以换取每帧重绘
    var frameTick by remember(line) { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying, line) {
        if (isPlaying) {
            // withFrameNanos 在协程取消时抛出 CancellationException，循环自然退出
            while (true) {
                withFrameNanos { frameNano -> frameTick = frameNano }
            }
        }
    }

    // 排版结果出来后只算一次每个字词与行的物理坐标，避免每帧重复定位字符
    val layoutInfo = remember(line, textLayoutResult) { textLayoutResult?.let { buildLayoutInfo(line, it) } }

    val contentAlignment = when (textAlign) {
        TextAlign.End -> Alignment.CenterEnd
        TextAlign.Center -> Alignment.Center
        else -> Alignment.CenterStart
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = contentAlignment) {
        Text(
            line.text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            color = inactiveColor,
            textAlign = textAlign,
            style = LocalTextStyle.current.copy(shadow = shadow),
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            line.text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            color = activeColor,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth()
                .graphicsLayer {
                    // 排版结果出来之前先不显示，避免闪一帧整行高亮；
                    // 离屏合成让羽化的 DstIn 只作用于本层文字，不擦除底层灰字与背景
                    alpha = if (layoutInfo != null) 1f else 0f
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    val info = layoutInfo
                    if (info == null) {
                        drawContent()
                        return@drawWithContent
                    }
                    // 读取帧心跳是本行按屏幕刷新率重绘的唯一依据
                    @Suppress("UNUSED_VARIABLE")
                    val frameTickRead = frameTick
                    val featherHalfPx = if (advancedEffect) (KaraokeEdgeFeather / 2).toPx() else 0f
                    val relativeProgress = positionState.value() - line.timeMs
                    val spans = computePlayedSpans(info, relativeProgress, featherHalfPx)
                    clipPath.rewind()
                    info.lineLayouts.forEachIndexed { lineIndex, lineLayout ->
                        val clipRight = spans[lineIndex].clipRight
                        if (clipRight > lineLayout.left) {
                            clipPath.addRect(
                                Rect(lineLayout.left, lineLayout.top, clipRight.coerceIn(lineLayout.left, lineLayout.right), lineLayout.bottom)
                            )
                        }
                    }
                    clipPath(clipPath) {
                        this@drawWithContent.drawContent()
                        val featherIndex = if (featherHalfPx > 0f) spans.indexOfFirst { it.featherCenterX != null } else -1
                        if (featherIndex != -1) {
                            val lineLayout = info.lineLayouts[featherIndex]
                            val centerX = spans[featherIndex].featherCenterX!!
                            val left = (centerX - featherHalfPx).coerceAtLeast(lineLayout.left)
                            val right = (centerX + featherHalfPx).coerceAtMost(lineLayout.right)
                            // 遮罩横向覆盖整行：渐变右侧必须透明，窄矩形的 DstIn 会让未覆盖的裁剪区域残留白色细线；
                            // 纵向严格限制在当前行盒内
                            if (right > left) {
                                drawRect(
                                    brush = Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = left, endX = right),
                                    topLeft = Offset(lineLayout.left, lineLayout.top),
                                    size = Size(lineLayout.right - lineLayout.left, lineLayout.bottom - lineLayout.top),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                        if (glowEffect) drawBreathingGlow(info, relativeProgress)
                    }
                }
        )
    }
}

// 演唱中的字词按进度叠一层随正弦脉动的白色微光
private fun DrawScope.drawBreathingGlow(info: LyricLayoutInfo, relativeProgress: Long) {
    info.wordLayouts.forEach { word ->
        if (relativeProgress !in word.startMs..word.endMs) return@forEach
        val duration = (word.endMs - word.startMs).coerceAtLeast(1)
        val pulse = sin((relativeProgress - word.startMs).toFloat() / duration * PI).toFloat()
        if (pulse > 0f) {
            drawRect(
                color = Color.White.copy(alpha = 0.22f * pulse),
                topLeft = Offset(word.left, word.top),
                size = Size((word.right - word.left).coerceAtLeast(0f), (word.bottom - word.top).coerceAtLeast(0f)),
                blendMode = BlendMode.Plus
            )
        }
    }
}

// 把每个计时词映射到文本偏移，再取各字符的包围盒；词文本在行内找不到时按顺序顺延，保证后续词不错位
private fun buildLayoutInfo(line: LyricLine, layout: TextLayoutResult): LyricLayoutInfo {
    val textLength = line.text.length
    var searchIndex = 0
    val wordRanges = line.words.map { word ->
        val startIndex = line.text.indexOf(word.text, searchIndex)
        if (startIndex != -1) {
            searchIndex = startIndex + word.text.length
            startIndex until searchIndex
        } else {
            val start = searchIndex
            searchIndex = (searchIndex + word.text.length).coerceAtMost(textLength)
            start until searchIndex
        }
    }
    val lineLayouts = (0 until layout.lineCount).map { index ->
        LineLayout(layout.getLineLeft(index), layout.getLineRight(index), layout.getLineTop(index), layout.getLineBottom(index))
    }
    val wordLayouts = line.words.flatMapIndexed { i, word ->
        val boxes = wordRanges[i].mapNotNull { offset ->
            val character = line.text[offset]
            val isTrailingSurrogate = Character.isLowSurrogate(character) && offset > 0 && Character.isHighSurrogate(line.text[offset - 1])
            if (character.isWhitespace() || isTrailingSurrogate) {
                null
            } else {
                val visualLine = layout.getLineForOffset(offset)
                try {
                    val bounds = layout.getBoundingBox(offset)
                    KaraokeCharacterBox(visualLine, bounds.left, bounds.right)
                } catch (e: Exception) {
                    AppLogger.d(TAG, "歌词字符定位失败，回退到水平位置", e)
                    KaraokeCharacterBox(
                        visualLine,
                        layout.getHorizontalPosition(offset, true),
                        layout.getHorizontalPosition(offset + 1, true)
                    )
                }
            }
        }
        splitTimedWordAcrossLines(word.startOffsetMs, word.durationMs, boxes).map { segment ->
            val visualLine = lineLayouts[segment.lineIndex]
            WordLayout(
                startMs = segment.startMs,
                endMs = segment.endMs,
                left = segment.left.coerceIn(visualLine.left, visualLine.right),
                right = segment.right.coerceIn(visualLine.left, visualLine.right),
                top = visualLine.top,
                bottom = visualLine.bottom,
                lineIndex = segment.lineIndex
            )
        }
    }
    return LyricLayoutInfo(wordLayouts, lineLayouts)
}
