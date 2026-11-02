package com.lin0721.linmusic.core.share.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.share.ui.CardFill
import com.lin0721.linmusic.core.share.ui.CardRoot
import com.lin0721.linmusic.core.share.ui.CardScope
import com.lin0721.linmusic.core.share.ui.CardText
import com.lin0721.linmusic.core.share.ui.CoverArt
import com.lin0721.linmusic.core.share.ui.NeteaseRed
import com.lin0721.linmusic.core.share.ui.Watermark

// 大封面 + 歌名歌手
@Composable
internal fun CoverCard(s: CardScope) {
    val fill = s.fill(s.coverBase)
    val on = fill.onColor
    CardRoot(fill, s.art, shade = true) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = s.verticalPadding)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CoverArt(s.art, Modifier.aspectRatio(1f), RoundedCornerShape(18.dp))
            }
            Spacer(Modifier.height(20.dp))
            CardText(s.content.title, on, 26.sp, weight = FontWeight.ExtraBold, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            CardText(s.content.artist, on.copy(alpha = 0.7f), 16.sp)
            Spacer(Modifier.height(20.dp))
            Watermark(s.art, on.copy(alpha = 0.9f))
        }
    }
}

// 封套里抽出半张唱片
@Composable
internal fun VinylCard(s: CardScope) {
    val fill = s.fill(Color(0xFF111111))
    val on = fill.onColor
    CardRoot(fill, s.art) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = s.verticalPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(NeteaseRed))
                Spacer(Modifier.width(8.dp))
                CardText("正在播放", on.copy(alpha = 0.7f), 12.sp, letterSpacing = 0.3.em)
            }
            Spacer(Modifier.height(18.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // 唱片露出部分 + 封套总宽不超过可用宽度
                val sleeve = minOf(maxWidth * 0.68f, maxHeight)
                Box(
                    Modifier.align(Alignment.CenterStart)
                        .offset(x = sleeve * 0.42f)
                        .size(sleeve * 0.96f)
                ) {
                    VinylDisc(s, fill)
                }
                CoverArt(s.art, Modifier.align(Alignment.CenterStart).size(sleeve), RoundedCornerShape(4.dp))
            }
            Spacer(Modifier.height(22.dp))
            CardText(s.content.title, on, 30.sp, weight = FontWeight.Black, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            CardText(s.content.artist, on.copy(alpha = 0.65f), 16.sp)
            Spacer(Modifier.height(20.dp))
            Watermark(s.art, on.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun VinylDisc(s: CardScope, fill: CardFill) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(Color(0xFF0A0A0A), r)
            var groove = r * 0.97f
            while (groove > r * 0.4f) {
                drawCircle(Color.White.copy(alpha = 0.05f), groove, style = Stroke(width = 1.dp.toPx()))
                groove -= r * 0.035f
            }
            drawCircle(
                Brush.sweepGradient(
                    listOf(
                        Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent,
                        Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent
                    ),
                    center
                ),
                r
            )
        }
        Box(Modifier.fillMaxSize(0.36f), contentAlignment = Alignment.Center) {
            CoverArt(s.art, Modifier.fillMaxSize(), CircleShape)
            Box(Modifier.size(6.dp).clip(CircleShape).background(fill.tone))
        }
    }
}
