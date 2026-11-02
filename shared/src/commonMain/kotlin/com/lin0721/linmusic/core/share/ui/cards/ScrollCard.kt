package com.lin0721.linmusic.core.share.ui.cards

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.share.ShareLyricLine
import com.lin0721.linmusic.core.share.ui.CardRoot
import com.lin0721.linmusic.core.share.ui.CardScope
import com.lin0721.linmusic.core.share.ui.LocalShareTypography
import com.lin0721.linmusic.core.share.ui.NeteaseRed
import com.lin0721.linmusic.core.share.ui.Paper
import com.lin0721.linmusic.core.share.ui.Watermark
import kotlin.math.abs

private const val CHAR_HEIGHT = 1.2f
private const val LINE_GAP = 0.75f
private const val WRAP_GAP = 0.3f

// 中文竖排，从右往左，左下角红色印章
@Composable
internal fun ScrollCard(s: CardScope) {
    val fill = s.fill(Paper)
    val on = fill.onColor
    val seal = if (abs(fill.tone.red - NeteaseRed.red) + abs(fill.tone.green - NeteaseRed.green) < 0.3f) on else NeteaseRed
    CardRoot(fill, s.art) {
        Box(Modifier.matchParentSize().padding(14.dp).border(1.dp, on.copy(alpha = 0.18f)))
        Row(Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = s.verticalPadding + 6.dp)) {
            Column(Modifier.fillMaxHeight()) {
                Watermark(s.art, on.copy(alpha = 0.75f))
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.border(1.5.dp, seal).padding(horizontal = 4.dp, vertical = 6.dp)) {
                        VerticalString(s.content.title.take(8), seal, 13f, FontWeight.Bold)
                    }
                    Spacer(Modifier.width(6.dp))
                    VerticalString(s.content.artist.take(12), on.copy(alpha = 0.6f), 11f, FontWeight.Normal)
                }
            }
            Spacer(Modifier.width(18.dp))
            VerticalLyrics(s.content.lines, on, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun VerticalString(text: String, color: Color, sp: Float, weight: FontWeight) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        text.filterNot { it.isWhitespace() }.forEach { ch -> CharCell(ch, color, sp, weight) }
    }
}

// 整体从大到小试排：每列放得下的字数决定列数，总宽不超过可用宽度
@Composable
private fun VerticalLyrics(lines: List<ShareLyricLine>, color: Color, modifier: Modifier) {
    // 竖排没有左右对齐之分，只取字形、字重与字号倍率
    val typography = LocalShareTypography.current
    val family = typography.family ?: FontFamily.Serif
    val weight = typography.weight ?: FontWeight.Bold
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopEnd) {
        val texts = lines.map { it.text.trim() }.filter { it.isNotEmpty() }
        var sp = 30f * typography.scale
        var columns = layoutColumns(texts, perColumn(maxHeight, sp))
        while (sp > 16f && totalWidth(columns, sp) > maxWidth.value) {
            sp -= 1f
            columns = layoutColumns(texts, perColumn(maxHeight, sp))
        }
        Row(horizontalArrangement = Arrangement.End) {
            // 第一句在最右侧
            columns.asReversed().forEachIndexed { i, column ->
                if (i > 0) {
                    val prevStartsLine = columns.asReversed()[i - 1].startsLine
                    Spacer(Modifier.width((sp * if (prevStartsLine) LINE_GAP else WRAP_GAP).dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    column.chars.forEach { ch ->
                        if (ch == ' ') {
                            Spacer(Modifier.height((sp * 0.5f).dp))
                        } else {
                            CharCell(ch, color, sp, weight, family)
                        }
                    }
                }
            }
        }
    }
}

private class VColumn(val chars: List<Char>, val startsLine: Boolean)

private fun perColumn(height: Dp, sp: Float): Int = (height.value / (sp * CHAR_HEIGHT)).toInt().coerceAtLeast(1)

// 连续空白压成一个半字空隙
private fun layoutColumns(texts: List<String>, perColumn: Int): List<VColumn> = texts.flatMap { text ->
    val chars = text.replace(Regex("\\s+"), " ").toList()
    chars.chunked(perColumn).mapIndexed { i, chunk -> VColumn(chunk, startsLine = i == 0) }
}

private fun totalWidth(columns: List<VColumn>, sp: Float): Float {
    if (columns.isEmpty()) return 0f
    val gaps = columns.drop(1).sumOf { (if (it.startsLine) LINE_GAP else WRAP_GAP).toDouble() }.toFloat()
    return sp * (columns.size * 1.1f + gaps)
}

// 固定格高，字形度量差异不影响列高计算
@Composable
private fun CharCell(ch: Char, color: Color, sp: Float, weight: FontWeight, family: FontFamily = FontFamily.Serif) {
    Box(Modifier.size(width = (sp * 1.1f).dp, height = (sp * CHAR_HEIGHT).dp), contentAlignment = Alignment.Center) {
        BasicText(ch.toString(), style = charStyle(color, sp, weight, family), softWrap = false)
    }
}

private fun charStyle(color: Color, sp: Float, weight: FontWeight, family: FontFamily) = TextStyle(
    color = color,
    fontSize = sp.sp,
    fontWeight = weight,
    fontFamily = family,
    textAlign = TextAlign.Center
)
