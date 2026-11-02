package com.lin0721.linmusic.core.share.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.share.LyricShareSelection
import com.lin0721.linmusic.core.share.ShareCardBackground
import com.lin0721.linmusic.core.share.ShareCardContent
import com.lin0721.linmusic.core.share.ShareCardRatio
import com.lin0721.linmusic.core.share.ShareCardStyle
import com.lin0721.linmusic.core.share.ui.cards.AuroraCard
import com.lin0721.linmusic.core.share.ui.cards.ClassicCard
import com.lin0721.linmusic.core.share.ui.cards.CoverCard
import com.lin0721.linmusic.core.share.ui.cards.PosterCard
import com.lin0721.linmusic.core.share.ui.cards.ScrollCard
import com.lin0721.linmusic.core.share.ui.cards.SnapshotCard
import com.lin0721.linmusic.core.share.ui.cards.VinylCard

// 卡片设计稿宽度；导出时按此宽度等比放大
internal const val CARD_BASE_WIDTH_DP = 360f

const val SHARE_CARD_EXPORT_WIDTH_PX = 1080

// 封面与 Logo 由平台加载，取色结果由平台计算，按主色、亮色、暗色排列
class ShareCardArt(
    val cover: Painter?,
    val logo: Painter,
    val palette: List<Color>
)

// 卡片按 360dp 设计稿排版，再整体缩放到实际宽度，预览与导出排版一致
@Composable
fun ShareCard(
    content: ShareCardContent,
    style: ShareCardStyle,
    background: ShareCardBackground,
    ratio: ShareCardRatio,
    art: ShareCardArt,
    modifier: Modifier = Modifier,
    typography: ShareCardTypography = ShareCardTypography()
) {
    BoxWithConstraints(modifier) {
        val scale = constraints.maxWidth / CARD_BASE_WIDTH_DP
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f), LocalShareTypography provides typography) {
            Box(Modifier.size(CARD_BASE_WIDTH_DP.dp, (CARD_BASE_WIDTH_DP / ratio.aspect).dp)) {
                val scope = CardScope(content, background, ratio, art)
                when (style) {
                    ShareCardStyle.COVER -> CoverCard(scope)
                    ShareCardStyle.VINYL -> VinylCard(scope)
                    ShareCardStyle.CLASSIC -> ClassicCard(scope)
                    ShareCardStyle.AURORA -> AuroraCard(scope)
                    ShareCardStyle.POSTER -> PosterCard(scope)
                    ShareCardStyle.SNAPSHOT -> SnapshotCard(scope)
                    ShareCardStyle.SCROLL ->
                        if (LyricShareSelection.isMostlyCjk(content.lines)) ScrollCard(scope) else ClassicCard(scope, paper = true)
                }
            }
        }
    }
}

@Stable
class ShareCardCapture internal constructor(internal val layer: GraphicsLayer) {
    // 等下一帧录制完成再取图
    suspend fun capture(): ImageBitmap {
        withFrameNanos { }
        withFrameNanos { }
        return layer.toImageBitmap()
    }
}

@Composable
fun rememberShareCardCapture(): ShareCardCapture {
    val layer = rememberGraphicsLayer()
    return remember(layer) { ShareCardCapture(layer) }
}

// 不可见的导出面：按固定像素宽度排版并录制，不占父布局空间
@Composable
fun ShareCardExportSurface(
    capture: ShareCardCapture,
    content: ShareCardContent,
    style: ShareCardStyle,
    background: ShareCardBackground,
    ratio: ShareCardRatio,
    art: ShareCardArt,
    typography: ShareCardTypography = ShareCardTypography(),
    widthPx: Int = SHARE_CARD_EXPORT_WIDTH_PX
) {
    val heightPx = (widthPx / ratio.aspect).toInt()
    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
        Box(
            Modifier
                .layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
                    layout(0, 0) { placeable.place(0, 0) }
                }
                .drawWithContent {
                    capture.layer.record { this@drawWithContent.drawContent() }
                }
        ) {
            ShareCard(content, style, background, ratio, art, typography = typography)
        }
    }
}
