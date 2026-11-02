package com.lin0721.linmusic.core.share.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.share.ShareCardBackground
import com.lin0721.linmusic.core.share.ui.CardFill
import com.lin0721.linmusic.core.share.ui.CardRoot
import com.lin0721.linmusic.core.share.ui.CardScope
import com.lin0721.linmusic.core.share.ui.CardText
import com.lin0721.linmusic.core.share.ui.CoverArt
import com.lin0721.linmusic.core.share.ui.FittedLyrics
import com.lin0721.linmusic.core.share.ui.LyricTextSpec
import com.lin0721.linmusic.core.share.ui.NeteaseRed
import com.lin0721.linmusic.core.share.ui.Paper
import com.lin0721.linmusic.core.share.ui.Watermark
import com.lin0721.linmusic.core.share.ui.formatTime
import kotlin.math.abs
import kotlin.math.max

// Spotify 式：小封面标题 + 大字歌词；paper 为竖排书笺遇到非中文歌词时的横排版本
@Composable
internal fun ClassicCard(s: CardScope, paper: Boolean = false) {
    val fill = s.fill(if (paper) Paper else s.coverBase)
    val on = fill.onColor
    val family = if (paper) FontFamily.Serif else null
    CardRoot(fill, s.art, shade = !paper) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = s.verticalPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(s.art, Modifier.size(44.dp), RoundedCornerShape(8.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    CardText(s.content.title, on, 15.sp, weight = FontWeight.Bold, family = family)
                    CardText("歌曲 · ${s.content.artist}", on.copy(alpha = 0.7f), 13.sp, family = family)
                }
            }
            Spacer(Modifier.height(26.dp))
            FittedLyrics(
                lines = s.content.lines,
                color = on,
                baseSpec = LyricTextSpec(maxSp = if (s.isStory) 34f else 30f, minSp = 15f, family = family),
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Watermark(s.art, on.copy(alpha = 0.9f))
        }
    }
}

// 封面三色光斑叠底
@Composable
internal fun AuroraCard(s: CardScope) {
    val fill = s.fill(lerp(s.paletteColor(0), Color.Black, 0.55f))
    val on = fill.onColor
    val blobs = if (s.background == ShareCardBackground.Auto) {
        listOf(s.paletteColor(0), s.paletteColor(1), s.paletteColor(2))
    } else {
        val tone = fill.tone
        listOf(lerp(tone, Color.White, 0.3f), tone, lerp(tone, Color.Black, 0.4f))
    }
    CardRoot(fill, s.art) {
        if (fill is CardFill.Flat) {
            Canvas(Modifier.matchParentSize()) {
                blob(blobs[0], Offset(size.width * 0.08f, size.height * 0.06f), size.width * 0.95f)
                blob(blobs[1], Offset(size.width * 1.0f, size.height * 0.48f), size.width * 0.8f)
                blob(blobs[2], Offset(size.width * 0.25f, size.height * 1.02f), size.width * 0.95f)
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                        center = center,
                        radius = max(size.width, size.height) * 0.78f
                    )
                )
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = s.verticalPadding)) {
            Watermark(s.art, on.copy(alpha = 0.9f))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                FittedLyrics(
                    lines = s.content.lines,
                    color = on,
                    baseSpec = LyricTextSpec(
                        maxSp = if (s.isStory) 36f else 32f,
                        minSp = 15f,
                        weight = FontWeight.Bold,
                        letterSpacing = (-0.01).em,
                        lineGap = 12.dp
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                )
            }
            Box(Modifier.width(28.dp).height(2.dp).background(on.copy(alpha = 0.8f)))
            Spacer(Modifier.height(10.dp))
            CardText(s.content.title, on, 15.sp, weight = FontWeight.Bold)
            CardText(s.content.artist, on.copy(alpha = 0.7f), 13.sp)
        }
    }
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float) {
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0f)), center = center, radius = radius),
        radius = radius,
        center = center
    )
}

// 瑞士海报风：贴边巨字 + 网易红竖线
@Composable
internal fun PosterCard(s: CardScope) {
    val fill = s.fill(Color(0xFF0E0E0E))
    val on = fill.onColor
    val accent = if (isNear(fill.tone, NeteaseRed)) on else NeteaseRed
    CardRoot(fill, s.art) {
        Box(Modifier.fillMaxHeight().width(10.dp).background(accent))
        Column(Modifier.fillMaxSize().padding(start = 34.dp, end = 24.dp, top = s.verticalPadding, bottom = s.verticalPadding)) {
            CardText(
                s.content.title.uppercase(),
                accent,
                12.sp,
                weight = FontWeight.Bold,
                letterSpacing = 0.25.em
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                FittedLyrics(
                    lines = s.content.lines,
                    color = on,
                    baseSpec = LyricTextSpec(
                        maxSp = if (s.isStory) 64f else 56f,
                        minSp = 20f,
                        weight = FontWeight.Black,
                        lineHeight = 1.08f,
                        letterSpacing = (-0.02).em,
                        lineGap = 8.dp
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    translationColor = on.copy(alpha = 0.55f)
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                CoverArt(s.art, Modifier.size(52.dp), RoundedCornerShape(2.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    CardText(s.content.title, on, 15.sp, weight = FontWeight.Bold)
                    CardText(s.content.artist, on.copy(alpha = 0.6f), 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                Watermark(s.art, on.copy(alpha = 0.9f))
            }
        }
    }
}

private fun isNear(a: Color, b: Color): Boolean =
    abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) < 0.3f

// 模拟歌词页：选中段高亮，前后各一句淡出
@Composable
internal fun SnapshotCard(s: CardScope) {
    val fill = s.fill(s.coverBase)
    val on = fill.onColor
    val dim = on.copy(alpha = 0.32f)
    CardRoot(fill, s.art, shade = true) {
        Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = s.verticalPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(s.art, Modifier.size(48.dp), RoundedCornerShape(10.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    CardText(s.content.title, on, 16.sp, weight = FontWeight.Bold)
                    CardText(s.content.artist, on.copy(alpha = 0.7f), 13.sp)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 18.dp),
                verticalArrangement = Arrangement.Center
            ) {
                s.content.lineBefore?.let {
                    CardText(it, dim, 17.sp, weight = FontWeight.Bold, maxLines = 2)
                    Spacer(Modifier.height(14.dp))
                }
                FittedLyrics(
                    lines = s.content.lines,
                    color = on,
                    baseSpec = LyricTextSpec(maxSp = 28f, minSp = 15f, weight = FontWeight.Bold),
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                )
                s.content.lineAfter?.let {
                    Spacer(Modifier.height(14.dp))
                    CardText(it, dim, 17.sp, weight = FontWeight.Bold, maxLines = 2)
                }
            }
            val fraction = if (s.content.durationMs > 0) {
                (s.content.positionMs.toFloat() / s.content.durationMs).coerceIn(0f, 1f)
            } else {
                0.35f
            }
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(on.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(on))
            }
            Spacer(Modifier.height(6.dp))
            Row {
                CardText(formatTime(s.content.positionMs), on.copy(alpha = 0.6f), 11.sp)
                Spacer(Modifier.weight(1f))
                if (s.content.durationMs > 0) CardText(formatTime(s.content.durationMs), on.copy(alpha = 0.6f), 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            PlaybackGlyphs(on, fill.tone, Modifier.align(Alignment.CenterHorizontally).width(180.dp).height(40.dp))
            Spacer(Modifier.height(14.dp))
            Watermark(s.art, on.copy(alpha = 0.85f), Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

// 上一首、暂停、下一首
@Composable
private fun PlaybackGlyphs(color: Color, bg: Color, modifier: Modifier) {
    Canvas(modifier) {
        val cy = size.height / 2
        val unit = size.height * 0.22f
        fun skip(cx: Float, forward: Boolean) {
            val dir = if (forward) 1f else -1f
            val path = Path().apply {
                moveTo(cx - unit * dir, cy - unit)
                lineTo(cx + unit * 0.6f * dir, cy)
                lineTo(cx - unit * dir, cy + unit)
                close()
            }
            drawPath(path, color)
            drawRect(
                color,
                topLeft = Offset(if (forward) cx + unit * 0.6f else cx - unit * 0.6f - unit * 0.3f, cy - unit),
                size = androidx.compose.ui.geometry.Size(unit * 0.3f, unit * 2)
            )
        }
        skip(size.width * 0.18f, forward = false)
        skip(size.width * 0.82f, forward = true)
        drawCircle(color, size.height / 2, Offset(size.width / 2, cy))
        val barW = unit * 0.45f
        drawRect(bg, Offset(size.width / 2 - unit * 0.55f, cy - unit * 0.9f), androidx.compose.ui.geometry.Size(barW, unit * 1.8f))
        drawRect(bg, Offset(size.width / 2 + unit * 0.55f - barW, cy - unit * 0.9f), androidx.compose.ui.geometry.Size(barW, unit * 1.8f))
    }
}
