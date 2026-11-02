package com.lin0721.linmusic.core.share.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt

// 歌词正文的字体覆盖；各项为 null 时沿用样式自带的设定
@Immutable
data class ShareCardTypography(
    val family: FontFamily? = null,
    val scale: Float = 1f,
    val weight: FontWeight? = null,
    val align: TextAlign? = null
)

data class ShareFontOption(val label: String, val family: FontFamily?)

object ShareCardTypographyOptions {
    // 两端都有的系统字形；桌面端另外追加电脑上安装的字体
    val presetFonts: List<ShareFontOption> = listOf(
        ShareFontOption("默认", null),
        ShareFontOption("黑体", FontFamily.SansSerif),
        ShareFontOption("衬线", FontFamily.Serif),
        ShareFontOption("等宽", FontFamily.Monospace),
        ShareFontOption("手写", FontFamily.Cursive)
    )

    val scales: List<Float> = listOf(0.8f, 0.9f, 1f, 1.1f, 1.2f)

    val weights: List<Pair<String, FontWeight?>> = listOf(
        "默认" to null,
        "常规" to FontWeight.Normal,
        "中等" to FontWeight.Medium,
        "加粗" to FontWeight.Bold,
        "特粗" to FontWeight.Black
    )

    val aligns: List<Pair<String, TextAlign?>> = listOf(
        "默认" to null,
        "左对齐" to TextAlign.Start,
        "居中" to TextAlign.Center,
        "右对齐" to TextAlign.End
    )

    fun scaleLabel(scale: Float): String = "${(scale * 100).roundToInt()}%"
}

internal val LocalShareTypography = staticCompositionLocalOf { ShareCardTypography() }

// 放大时最小字号不跟着放大，长歌词仍能缩下去放得下
internal fun LyricTextSpec.applied(t: ShareCardTypography): LyricTextSpec = copy(
    maxSp = maxSp * t.scale,
    minSp = minOf(minSp, minSp * t.scale),
    weight = t.weight ?: weight,
    family = t.family ?: family,
    align = t.align ?: align
)
