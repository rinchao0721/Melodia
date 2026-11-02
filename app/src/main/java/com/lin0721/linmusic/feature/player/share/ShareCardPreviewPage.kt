package com.lin0721.linmusic.feature.player.share

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.share.ShareCardBackground
import com.lin0721.linmusic.core.share.ShareCardContent
import com.lin0721.linmusic.core.share.ShareCardKind
import com.lin0721.linmusic.core.share.ShareCardRatio
import com.lin0721.linmusic.core.share.ShareCardStyle
import com.lin0721.linmusic.core.share.ui.ShareCard
import com.lin0721.linmusic.core.share.ui.ShareCardArt
import com.lin0721.linmusic.core.share.ui.ShareCardTypography
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaOutlinedButton
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import kotlin.math.absoluteValue

private val PillShape = RoundedCornerShape(PillRadius)
private val Ink = Color(0xFF1A1A1A)

@Composable
internal fun ShareCardPreviewPage(
    content: ShareCardContent,
    art: ShareCardArt,
    kind: ShareCardKind,
    hasLyrics: Boolean,
    styleIndex: Int,
    background: ShareCardBackground,
    ratio: ShareCardRatio,
    typography: ShareCardTypography,
    blurSupported: Boolean,
    exporting: Boolean,
    songLink: String?,
    onKindChange: (ShareCardKind) -> Unit,
    onStyleIndexChange: (Int) -> Unit,
    onBackgroundChange: (ShareCardBackground) -> Unit,
    onRatioChange: (ShareCardRatio) -> Unit,
    onTypographyChange: (ShareCardTypography) -> Unit,
    onEditLyrics: (() -> Unit)?,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val styles = ShareCardStyle.of(kind)
    var fontSheetOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize()) {
        key(kind) {
            val pagerState = rememberPagerState(initialPage = styleIndex.coerceIn(0, styles.lastIndex)) { styles.size }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect(onStyleIndexChange)
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 44.dp),
                pageSpacing = 14.dp
            ) { page ->
                val offset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction).absoluteValue
                Column(
                    Modifier.fillMaxSize().graphicsLayer {
                        val scale = 1f - 0.08f * offset.coerceIn(0f, 1f)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.4f * offset.coerceIn(0f, 1f)
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ShareCard(
                            content = content,
                            style = styles[page],
                            background = background,
                            ratio = ratio,
                            art = art,
                            modifier = Modifier.aspectRatio(ratio.aspect).clip(RoundedCornerShape(16.dp)),
                            typography = typography
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(styles[page].label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(MelodiaSpacing.md))
        if (hasLyrics) {
            KindToggle(kind, onKindChange, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(MelodiaSpacing.md))
        }
        BackgroundRow(art, background, blurSupported, onBackgroundChange)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = MelodiaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            songLink?.let { link ->
                OptionChip("复制链接", Icons.Rounded.Link, false) {
                    clipboard.setText(AnnotatedString(link))
                    ToastManager.showToast("已复制链接")
                }
            }
            ShareCardRatio.entries.forEach { option ->
                OptionChip(option.label, null, option == ratio) { onRatioChange(option) }
            }
            if (kind == ShareCardKind.LYRIC) {
                OptionChip("歌词字体", Icons.Rounded.TextFields, typography != ShareCardTypography()) { fontSheetOpen = true }
                onEditLyrics?.let { OptionChip("编辑歌词", Icons.Rounded.Edit, false, it) }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(MelodiaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MelodiaOutlinedButton(
                onClick = onSave,
                enabled = !exporting,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("保存图片", fontWeight = FontWeight.Bold)
            }
            MelodiaButton(
                onClick = onShare,
                enabled = !exporting,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)
            ) {
                if (exporting) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Ink, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("分享", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (fontSheetOpen) {
        ShareFontSheet(typography, onTypographyChange) { fontSheetOpen = false }
    }
}

@Composable
private fun KindToggle(kind: ShareCardKind, onChange: (ShareCardKind) -> Unit, modifier: Modifier) {
    Row(
        modifier.width(260.dp).height(40.dp).clip(PillShape).background(Color.White.copy(alpha = 0.12f))
    ) {
        ShareCardKind.entries.forEach { option ->
            val selected = option == kind
            val bg by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "kindBg")
            val fg by animateColorAsState(if (selected) Ink else Color.White, label = "kindFg")
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .pressable(MelodiaPress.Pill, PillShape) { onChange(option) }
                    .background(bg, PillShape),
                contentAlignment = Alignment.Center
            ) {
                Text(option.label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BackgroundRow(
    art: ShareCardArt,
    current: ShareCardBackground,
    blurSupported: Boolean,
    onChange: (ShareCardBackground) -> Unit
) {
    val base = art.palette.firstOrNull() ?: Color(0xFF2F5566)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = MelodiaSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Swatch(current == ShareCardBackground.Auto, { onChange(ShareCardBackground.Auto) }) {
            Box(Modifier.fillMaxSize().background(lerp(base, Color.Black, 0.25f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = "自动", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        repeat(ShareCardBackground.PALETTE_SIZE) { i ->
            val option = ShareCardBackground.Palette(i)
            val color = art.palette.getOrNull(i) ?: return@repeat
            Swatch(current == option, { onChange(option) }) {
                Box(Modifier.fillMaxSize().background(lerp(color, Color.Black, 0.15f)))
            }
        }
        ShareCardBackground.presets.forEach { option ->
            Swatch(current == option, { onChange(option) }) {
                Box(Modifier.fillMaxSize().background(Color(option.argb)))
            }
        }
        val cover = art.cover
        if (blurSupported && cover != null) {
            Swatch(current == ShareCardBackground.BlurCover, { onChange(ShareCardBackground.BlurCover) }) {
                Image(
                    painter = cover,
                    contentDescription = "模糊封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(4.dp)
                )
            }
        }
    }
}

@Composable
private fun Swatch(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val ring by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = 0.2f), label = "swatchRing")
    Box(
        Modifier.size(40.dp)
            .pressable(MelodiaPress.Pill, CircleShape, onClick = onClick)
            .border(if (selected) 2.5.dp else 1.dp, ring, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
    ) {
        content()
    }
}

@Composable
internal fun OptionChip(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = 0.12f), label = "chipBg")
    val fg by animateColorAsState(if (selected) Ink else Color.White, label = "chipFg")
    Row(
        Modifier.height(36.dp)
            .pressable(MelodiaPress.Pill, PillShape, onClick = onClick)
            .background(bg, PillShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
