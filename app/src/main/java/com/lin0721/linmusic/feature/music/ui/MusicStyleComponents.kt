package com.lin0721.linmusic.feature.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.smoothVerticalGradient
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.feature.music.domain.MusicStyle
import com.lin0721.linmusic.feature.music.domain.StyleHead

internal val MusicEdgePadding = 20.dp

// 服务端没给配色的曲风统一退回中性灰
private val FallbackStyleColor = Color(0xFF4A4A4A)

// 头图高度不含状态栏，容得下大标题加两行简介
private val StyleHeroHeight = 230.dp

// 六位 hex 转 Color，脏值退回中性灰
internal fun String?.toStyleColor(): Color {
    val hex = this ?: return FallbackStyleColor
    return runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrDefault(FallbackStyleColor)
}

// 古典等曲风配色近黑（111111），直接铺在深色背景上看不出色块边界
internal fun Color.liftedForTile(): Color = if (luminance() < 0.015f) lerp(this, Color.White, 0.12f) else this

// 二级曲风子筛选。服务端不给二级配色，故一律用描边胶囊
@Composable
fun MusicSubStyleChips(
    children: List<MusicStyle>,
    selectedChildId: Long?,
    onSelect: (Long?) -> Unit
) {
    if (children.isEmpty()) return

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = MusicEdgePadding),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item(key = "all") {
            SubStyleChip(text = "全部", selected = selectedChildId == null, onClick = { onSelect(null) })
        }
        items(children, key = { it.id }) { child ->
            SubStyleChip(
                text = child.name,
                selected = selectedChildId == child.id,
                onClick = { onSelect(child.id) }
            )
        }
    }
}

@Composable
private fun SubStyleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .pressable(MelodiaPress.Pill) { onClick() }
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(MelodiaColors.TextPrimary)
                else Modifier.border(1.dp, MelodiaColors.Overlay.copy(alpha = 0.18f), CircleShape)
            )
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = if (selected) MelodiaColors.InverseText else MelodiaColors.PillContent,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// 曲风详情头图：通栏色块铺到状态栏下，封面淡化叠加，英文名作水印
@Composable
fun MusicStyleHero(head: StyleHead?, fallbackName: String, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.smoothVerticalGradient(from = accent, to = accent.copy(alpha = 0.55f)))
    ) {
        head?.coverUrl?.let {
            SubcomposeAsyncImage(
                model = it,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.35f,
                loading = {},
                error = {}
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(StyleHeroHeight)
        ) {
            head?.enName?.takeIf { it.isNotBlank() }?.let { enName ->
                Text(
                    text = enName,
                    color = Color.White.copy(alpha = 0.15f),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = MusicEdgePadding, vertical = 8.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = MusicEdgePadding, vertical = 16.dp)
            ) {
                Text(
                    text = "曲风",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = head?.name ?: fallbackName,
                    color = Color.White,
                    fontSize = 38.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = head?.desc?.takeIf { it.isNotBlank() } ?: head?.realStatsOrEmpty().orEmpty()
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

// 带 + 的是服务端封顶值（999999+ / 1000+），各曲风都一样，展示了等于没说
private fun StyleHead.realStatsOrEmpty(): String = listOfNotNull(
    songNum.takeIf { it.isNotBlank() && !it.endsWith("+") }?.let { "$it 首歌" },
    artistNum.takeIf { it.isNotBlank() && !it.endsWith("+") }?.let { "$it 位歌手" }
).joinToString(" · ")

// 「你在此曲风最爱」，未登录时上游不会传入。
// 标签固定用品牌色而非曲风色：colorDeep 是给深底白字用的底色，当前景必然读不清
// （流行 23303B、古典 111111 在深色卡上几乎不可见）。
@Composable
fun MusicFavouriteSongCard(track: Track, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubcomposeAsyncImage(
            model = track.al.picUrl.withStyleCoverParam("120y120"),
            contentDescription = track.name,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(RadiusCompact)),
            contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() }
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "你在此曲风最爱",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 3.dp)
                )
            }
            Text(
                text = "${track.name} · ${track.ar.joinToString("/") { it.name }}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

// 头图区域的色块占位，加载中与错误态共用，保证返回键始终落在色块上
@Composable
fun MusicStyleHeroPlaceholder(fallbackName: String) {
    MusicStyleHero(head = null, fallbackName = fallbackName, accent = FallbackStyleColor)
}
