package com.lin0721.linmusic.feature.player.ui

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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.ui.theme.MelodiaColors

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

internal data class KaraokeCharacterBox(val lineIndex: Int, val left: Float, val right: Float)

internal data class KaraokeWordSegment(
    val lineIndex: Int,
    val left: Float,
    val right: Float,
    val startMs: Long,
    val endMs: Long
)

// 一个计时词可能横跨自动换行。按视觉行分段，时间按各段可见字形宽度分配；
// 空格由调用处过滤，避免换行空格的坐标被当成上一行的终点。
internal fun splitTimedWordAcrossLines(
    startMs: Long,
    durationMs: Long,
    characters: List<KaraokeCharacterBox>
): List<KaraokeWordSegment> {
    if (characters.isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<KaraokeCharacterBox>>()
    characters.forEach { character ->
        if (groups.lastOrNull()?.lastOrNull()?.lineIndex == character.lineIndex) {
            groups.last().add(character)
        } else {
            groups.add(mutableListOf(character))
        }
    }
    val widths = groups.map { group -> group.sumOf { (it.right - it.left).coerceAtLeast(1f).toDouble() } }
    val totalWidth = widths.sum()
    var elapsedWidth = 0.0
    return groups.mapIndexed { index, group ->
        val start = startMs + (durationMs * elapsedWidth / totalWidth).toLong()
        elapsedWidth += widths[index]
        val end = if (index == groups.lastIndex) startMs + durationMs
                  else startMs + (durationMs * elapsedWidth / totalWidth).toLong()
        KaraokeWordSegment(
            lineIndex = group.first().lineIndex,
            left = group.minOf { it.left },
            right = group.maxOf { it.right },
            startMs = start,
            endMs = end
        )
    }
}

// 扫色分界处的羽化带总宽度：过大像光晕、过小退化成硬竖线，16.dp 约一个字宽，观感最轻微
private val KaraokeEdgeFeather: Dp = 16.dp

// 每一视觉行的已播范围：clipRight 是实色裁剪右缘（当前演唱行已外扩半个羽化带），
// featherCenterX 非空表示该行是正在演唱的行，渐变中心应对齐到此处
private data class PlayedSpan(
    val clipRight: Float,
    val featherCenterX: Float?
)

// 按播放进度推导每一视觉行的已播右缘与羽化中心。纯计算、无状态读取，可在绘制阶段每帧调用
private fun computePlayedSpans(
    info: LyricLayoutInfo,
    relativeProgress: Long,
    featherHalfPx: Float
): List<PlayedSpan> {
    return info.lineLayouts.mapIndexed { lineIndex, lineLayout ->
        val lastWordOnLine = info.wordLayouts.lastOrNull { it.lineIndex == lineIndex }
        if (lastWordOnLine != null && relativeProgress >= lastWordOnLine.endMs) {
            // 整行已唱完，直接拉满高亮，不需要羽化
            PlayedSpan(clipRight = lineLayout.right, featherCenterX = null)
        } else {
            var maxRight = lineLayout.left
            var hasActiveWord = false
            info.wordLayouts.forEach { word ->
                if (word.lineIndex == lineIndex) {
                    if (relativeProgress >= word.endMs) {
                        maxRight = maxRight.coerceAtLeast(word.right)
                    } else if (relativeProgress in word.startMs..word.endMs) {
                        // 在当前唱到的字词内进行线性像素高亮插值
                        val ratio = if (word.endMs > word.startMs) {
                            (relativeProgress - word.startMs).toFloat() / (word.endMs - word.startMs)
                        } else 1f
                        val edge = word.left + (word.right - word.left) * ratio
                        maxRight = maxRight.coerceAtLeast(edge)
                        hasActiveWord = true
                    }
                }
            }
            if (hasActiveWord) {
                // 已唱完的字形可能比当前字词的插值边缘更靠右（例如重叠的标点时间戳）。
                // 羽化始终跟随真正的高亮右缘，避免渐变之后残留一条实色竖线。
                PlayedSpan(
                    clipRight = (maxRight + featherHalfPx).coerceAtMost(lineLayout.right),
                    featherCenterX = maxRight
                )
            } else {
                PlayedSpan(clipRight = maxRight, featherCenterX = null)
            }
        }
    }
}

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
    // 行高必须大于字形实际纵向跨度（升部+降部），让相邻视觉行的行盒互不重叠：
    // 裁剪矩形与羽化带都是按行盒高度作用的，一旦行盒重叠，已播行白字会染到未播行升部、
    // 羽化带会擦掉已播行降部，且矩形裁剪无法在重叠像素上同时满足两行，只能从源头避开
    lineHeight: TextUnit = (fontSize.value * 1.35f).sp,
    textAlign: TextAlign = TextAlign.Start,
    // 关闭流光只移除柔边羽化，仍按逐字时间裁剪并逐帧推进。
    advancedEffect: Boolean = true,
    // 字词呼吸光晕微光脉冲
    glowEffect: Boolean = false,
    // 每帧在绘制阶段读播放器时钟；暂停和缓冲时由播放器本身冻结进度。
    isPlaying: Boolean = true,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    isActive: Boolean = true,
    featherWidth: Dp = KaraokeEdgeFeather,
    // 只加在底层未播文字上，已播层裁剪覆盖在其上，阴影不会叠两遍
    shadow: Shadow? = null
) {
    var textLayoutResult by remember(line) { mutableStateOf<TextLayoutResult?>(null) }
    val currentPositionProviderState = rememberUpdatedState(currentPositionProvider)
    // 裁剪路径复用：裁剪矩形每帧都要重建，Path 背后是原生对象且带 finalizer，
    // 每帧新建会持续制造 GC 压力，在满帧率下尤其容易造成掉帧毛刺
    val reusableClipPath = remember { Path() }
    // 逐帧心跳：写一个每帧都不同的帧时间戳，供绘制阶段读取以换取每帧重绘。
    // 没有它的话，重绘只能被 50ms 一次的播放器进度轮询触发，逐字扫色会掉到 20Hz。
    var frameTick by remember(line) { mutableLongStateOf(0L) }

    // 加色光晕只在深色底上可见，浅色底上会把文字冲淡
    val glowVisible = glowEffect && MelodiaColors.IsDark
    LaunchedEffect(isPlaying, line, isActive, advancedEffect, glowEffect) {
        if (isPlaying && isActive) {
            // 用 while(true) 而非 kotlinx.coroutines.isActive：后者会被同名参数 isActive 遮蔽。
            // withFrameNanos 在协程取消时会抛出 CancellationException，循环自然退出。
            while (true) {
                withFrameNanos { frameNano -> frameTick = frameNano }
            }
        }
    }

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

            val lineLayouts = (0 until layout.lineCount).map { lineIndex ->
                LineLayout(
                    left = layout.getLineLeft(lineIndex),
                    right = layout.getLineRight(lineIndex),
                    top = layout.getLineTop(lineIndex),
                    bottom = layout.getLineBottom(lineIndex)
                )
            }

            val wordLayouts = line.words.flatMapIndexed { i, word ->
                val characterBoxes = wordRanges[i].mapNotNull { offset ->
                    val character = line.text[offset]
                    if (character.isWhitespace() ||
                        (Character.isLowSurrogate(character) && offset > 0 && Character.isHighSurrogate(line.text[offset - 1]))) {
                        null
                    } else {
                        val visualLine = layout.getLineForOffset(offset)
                        try {
                            val bounds = layout.getBoundingBox(offset)
                            KaraokeCharacterBox(visualLine, bounds.left, bounds.right)
                        } catch (e: Exception) {
                            AppLogger.d(TAG, "歌词字符定位 getBoundingBox 失败，回退 getHorizontalPosition", e)
                            KaraokeCharacterBox(
                                visualLine,
                                layout.getHorizontalPosition(offset, true),
                                layout.getHorizontalPosition(offset + 1, true)
                            )
                        }
                    }
                }
                splitTimedWordAcrossLines(word.startOffsetMs, word.durationMs, characterBoxes).map { segment ->
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

            LyricLayoutInfo(wordLayouts, lineLayouts)
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        // 底层灰色（未激活）歌词
        Text(
            text = line.text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            color = inactiveColor,
            textAlign = textAlign,
            style = LocalTextStyle.current.copy(shadow = shadow),
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier.fillMaxWidth()
        )

        // 顶层高亮（已激活）歌词：
        // ·实色部分沿用 Path 硬裁剪（GPU clip，不重绘文本），仅当前演唱行的裁剪右缘外扩半个羽化带
        // ·柔边用 DstIn 横向渐变把羽化带压成 1→0 透明，消除已播/未播之间的硬竖线
        // 进度只在绘制阶段读取，不触发重组；裁剪与羽化共用同一次区间推导。
        // 通过 Path 对每一行分别建立独立的裁剪矩形，防止单行歌词折行时产生漏光和干扰
        Text(
            text = line.text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            color = activeColor,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val ready = lyricLayoutInfo != null && isActive
                    // 走逐字裁剪时，排版结果出来之前先不显示，避免闪一帧整行高亮
                    alpha = if (ready) 1f else 0f
                    // 仅在激活且启用 DstIn 柔边遮罩时开启离屏合成，避免非激活行或纯硬裁剪分配离屏层
                    compositingStrategy = if (ready && advancedEffect && featherWidth > 0.dp) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                }
                .drawWithContent {
                    val info = lyricLayoutInfo
                    if (info != null && isActive) {
                        // 读取逐帧心跳：这一步是本行能按屏幕刷新率重绘的唯一依据。
                        // 不读它，绘制阶段只会在 50ms 一次的播放器进度变化时失效，逐字扫色看起来就是掉帧。
                        @Suppress("UNUSED_VARIABLE")
                        val frameTickRead = frameTick
                        val featherHalfPx = if (advancedEffect) (featherWidth / 2).toPx() else 0f
                        val relativeProgress = currentPositionProviderState.value() - line.timeMs
                        val spans = computePlayedSpans(info, relativeProgress, featherHalfPx)
                        val path = reusableClipPath
                        path.rewind()
                        info.lineLayouts.forEachIndexed { lineIndex, lineLayout ->
                            val clipRight = spans[lineIndex].clipRight
                            if (clipRight > lineLayout.left) {
                                path.addRect(
                                    Rect(
                                        left = lineLayout.left,
                                        top = lineLayout.top,
                                        right = clipRight.coerceIn(lineLayout.left, lineLayout.right),
                                        bottom = lineLayout.bottom
                                    )
                                )
                            }
                        }
                        clipPath(path) {
                            this@drawWithContent.drawContent()
                            if (featherHalfPx > 0f) {
                                val featherIndex = spans.indexOfFirst { it.featherCenterX != null }
                                if (featherIndex != -1) {
                                    val lineLayout = info.lineLayouts[featherIndex]
                                    val centerX = spans[featherIndex].featherCenterX!!
                                    val left = (centerX - featherHalfPx).coerceAtLeast(lineLayout.left)
                                    val right = (centerX + featherHalfPx).coerceAtMost(lineLayout.right)
                                    // 遮罩横向覆盖整行：渐变右侧必须透明，窄矩形 DstIn 会让未覆盖的
                                    // 裁剪区域保留白色细线。纵向仍严格限制在当前行盒内。
                                    if (right > left) {
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                0f to Color.Black,
                                                1f to Color.Transparent,
                                                startX = left,
                                                endX = right
                                            ),
                                            topLeft = Offset(lineLayout.left, lineLayout.top),
                                            size = Size(lineLayout.right - lineLayout.left, lineLayout.bottom - lineLayout.top),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                                }
                            }
                            if (glowVisible) {
                                // 绘制原版演唱字词的呼吸光晕微光脉冲
                                info.wordLayouts.forEach { word ->
                                    if (relativeProgress in word.startMs..word.endMs) {
                                        val wordDuration = (word.endMs - word.startMs).coerceAtLeast(1)
                                        val ratio = (relativeProgress - word.startMs).toFloat() / wordDuration
                                        val pulse = kotlin.math.sin(ratio * kotlin.math.PI).toFloat()
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
                            }
                        }
                    }
                }
        )
    }
}
