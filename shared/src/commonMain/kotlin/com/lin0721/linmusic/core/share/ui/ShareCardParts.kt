package com.lin0721.linmusic.core.share.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.share.ShareCardBackground
import com.lin0721.linmusic.core.share.ShareCardContent
import com.lin0721.linmusic.core.share.ShareCardRatio
import com.lin0721.linmusic.core.share.ShareLyricLine

internal val Ink = Color(0xFF231B14)
internal val Paper = Color(0xFFF3EEE3)
internal val NeteaseRed = Color(0xFFE03E3E)
private val FallbackPalette = listOf(Color(0xFF2F5566), Color(0xFF6E8FA3), Color(0xFF14262E))

internal class CardScope(
    val content: ShareCardContent,
    val background: ShareCardBackground,
    val ratio: ShareCardRatio,
    val art: ShareCardArt
) {
    fun paletteColor(index: Int): Color = art.palette.getOrNull(index) ?: FallbackPalette[index % FallbackPalette.size]

    // 封面主色压暗一些，保证白字可读
    val coverBase: Color get() = lerp(paletteColor(0), Color.Black, 0.25f)

    fun fill(auto: Color): CardFill = when (val bg = background) {
        ShareCardBackground.Auto -> CardFill.Flat(auto)
        is ShareCardBackground.Palette -> CardFill.Flat(lerp(paletteColor(bg.index), Color.Black, 0.15f))
        is ShareCardBackground.Solid -> CardFill.Flat(Color(bg.argb))
        ShareCardBackground.BlurCover -> if (art.cover != null) CardFill.Blur else CardFill.Flat(auto)
    }

    val isStory: Boolean get() = ratio == ShareCardRatio.STORY
    val isSquare: Boolean get() = ratio == ShareCardRatio.SQUARE

    // 9:16 上下多留白，内容集中在中段
    val verticalPadding: Dp get() = when (ratio) {
        ShareCardRatio.STORY -> 72.dp
        ShareCardRatio.SQUARE -> 24.dp
        ShareCardRatio.PORTRAIT -> 30.dp
    }
}

internal sealed interface CardFill {
    data class Flat(val color: Color) : CardFill
    data object Blur : CardFill

    // 用于决定文字深浅的代表色
    val tone: Color get() = when (this) {
        is Flat -> color
        Blur -> Color(0xFF202020)
    }

    val onColor: Color get() = tone.onColor()
}

internal fun Color.onColor(): Color = if (luminance() > 0.55f) Ink else Color.White

@Composable
internal fun BoxScope.CardBackdrop(fill: CardFill, cover: androidx.compose.ui.graphics.painter.Painter?) {
    when (fill) {
        is CardFill.Flat -> Box(Modifier.matchParentSize().background(fill.color))
        CardFill.Blur -> {
            Box(Modifier.matchParentSize().background(Color.Black))
            if (cover != null) {
                Image(
                    painter = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // 放大后再模糊，边缘不会露出暗边
                    modifier = Modifier.matchParentSize().graphicsLayer {
                        scaleX = 1.3f
                        scaleY = 1.3f
                    }.blur(40.dp)
                )
            }
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.38f)))
        }
    }
}

@Composable
internal fun CoverArt(art: ShareCardArt, modifier: Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    val cover = art.cover
    if (cover != null) {
        Image(
            painter = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape)
        )
    } else {
        Box(
            modifier.clip(shape).background(
                Brush.linearGradient(listOf(lerp(NeteaseRed, Color.Black, 0.2f), Color(0xFF2A1A1A)))
            ),
            contentAlignment = Alignment.Center
        ) {
            BasicText("♪", style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 28.sp))
        }
    }
}

@Composable
internal fun Watermark(art: ShareCardArt, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = art.logo,
            contentDescription = null,
            modifier = Modifier.size(18.dp).clip(RoundedCornerShape(5.dp))
        )
        Spacer(Modifier.width(6.dp))
        BasicText(
            "Melodia",
            style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.02.em)
        )
    }
}

@Composable
internal fun CardText(
    text: String,
    color: Color,
    size: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    family: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    align: TextAlign = TextAlign.Start
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = family,
            letterSpacing = letterSpacing,
            textAlign = align,
            lineHeight = size * 1.25f
        )
    )
}

internal data class LyricTextSpec(
    val maxSp: Float,
    val minSp: Float,
    val weight: FontWeight = FontWeight.ExtraBold,
    val family: FontFamily? = null,
    val lineHeight: Float = 1.22f,
    val letterSpacing: TextUnit = TextUnit.Unspecified,
    val align: TextAlign = TextAlign.Start,
    val lineGap: Dp = 10.dp
)

// 从大到小逐档试排，取能放进可用高度的最大字号
@Composable
internal fun FittedLyrics(
    lines: List<ShareLyricLine>,
    color: Color,
    baseSpec: LyricTextSpec,
    modifier: Modifier = Modifier,
    translationColor: Color = color.copy(alpha = 0.7f)
) {
    val typography = LocalShareTypography.current
    val spec = remember(baseSpec, typography) { baseSpec.applied(typography) }
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth
        val heightPx = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        val fontSp = remember(lines, widthPx, heightPx, spec) {
            fun mainStyle(sp: Float) = lyricStyle(color, sp, spec)
            fun subStyle(sp: Float) = translationStyle(translationColor, sp, spec)
            fun totalHeight(sp: Float): Int {
                val textConstraints = Constraints(maxWidth = widthPx)
                val gapPx = with(density) { spec.lineGap.roundToPx() }
                val subGapPx = with(density) { 4.dp.roundToPx() }
                return lines.withIndex().sumOf { (i, line) ->
                    var h = measurer.measure(line.text, mainStyle(sp), constraints = textConstraints).size.height
                    line.translation?.let {
                        h += subGapPx + measurer.measure(it, subStyle(sp), constraints = textConstraints).size.height
                    }
                    if (i > 0) h += gapPx
                    h
                }
            }
            var sp = spec.maxSp
            while (sp > spec.minSp && totalHeight(sp) > heightPx) sp -= 1f
            sp
        }
        Column(verticalArrangement = Arrangement.spacedBy(spec.lineGap)) {
            lines.forEach { line ->
                Column {
                    BasicText(line.text, style = lyricStyle(color, fontSp, spec), modifier = Modifier.fillWidthIfAligned(spec.align))
                    line.translation?.let {
                        Spacer(Modifier.height(4.dp))
                        BasicText(
                            it,
                            style = translationStyle(translationColor, fontSp, spec),
                            modifier = Modifier.fillWidthIfAligned(spec.align)
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.fillWidthIfAligned(align: TextAlign): Modifier =
    if (align == TextAlign.Start) this else this.fillMaxWidth()

private fun translationSp(mainSp: Float): Float = (mainSp * 0.52f).coerceAtLeast(12f)

// 测量与绘制共用，换字体后试排结果才准
private fun translationStyle(color: Color, mainSp: Float, spec: LyricTextSpec) = TextStyle(
    color = color,
    fontSize = translationSp(mainSp).sp,
    lineHeight = (translationSp(mainSp) * 1.3f).sp,
    fontWeight = FontWeight.Medium,
    fontFamily = spec.family,
    textAlign = spec.align
)

private fun lyricStyle(color: Color, sp: Float, spec: LyricTextSpec) = TextStyle(
    color = color,
    fontSize = sp.sp,
    lineHeight = (sp * spec.lineHeight).sp,
    fontWeight = spec.weight,
    fontFamily = spec.family,
    letterSpacing = spec.letterSpacing,
    textAlign = spec.align
)

internal fun formatTime(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0) / 1000).toInt()
    val sec = totalSec % 60
    return "${totalSec / 60}:${if (sec < 10) "0" else ""}$sec"
}

@Composable
internal fun CardRoot(
    fill: CardFill,
    art: ShareCardArt,
    shade: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        CardBackdrop(fill, art.cover)
        // 纯色底自上而下轻微压暗，避免平涂
        if (shade && fill is CardFill.Flat && fill.color.luminance() < 0.55f) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.28f)))
                )
            )
        }
        content()
    }
}
