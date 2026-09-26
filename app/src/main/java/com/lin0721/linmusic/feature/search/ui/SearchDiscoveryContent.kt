package com.lin0721.linmusic.feature.search.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.AdaptiveContentWidth
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.DiscoverySectionSkeleton
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.FallbackBase
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.darken
import com.lin0721.linmusic.core.ui.theme.extractBaseColorFromUrl
import com.lin0721.linmusic.feature.search.domain.HotSearch
import com.lin0721.linmusic.feature.search.domain.PlaylistTag
import java.util.Locale

private val tagFallbackColors = listOf(
    Color(0xFF1E3264),
    Color(0xFF8D67AB),
    Color(0xFF148A08),
    Color(0xFFE13300),
    Color(0xFF477D95),
    Color(0xFFE8115B),
    Color(0xFF537AA1),
    Color(0xFF7D4B32),
    Color(0xFF1A6B52),
    Color(0xFFE91429),
)

private const val HOT_LIST_SIZE = 10
private val TagSmallHeight = 72.dp

@Composable
internal fun SearchDiscoveryContent(
    state: DiscoveryUiState,
    featuredTrack: Track?,
    onHotSearchClick: (String) -> Unit,
    onPlayFeatured: () -> Unit,
    onPlaylistTagClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        DiscoveryUiState.Loading -> {
            Column(modifier = Modifier.fillMaxSize().padding(top = MelodiaSpacing.md)) {
                DiscoverySectionSkeleton()
                Spacer(Modifier.height(MelodiaSpacing.lg))
                DiscoverySectionSkeleton()
            }
        }
        is DiscoveryUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(message = state.message, onRetry = onRetry)
            }
        }
        is DiscoveryUiState.Success -> {
            val hotSearches = state.hotSearches.take(HOT_LIST_SIZE)
            val tagGroups = state.playlistTags.chunked(3)
            AdaptiveContentWidth {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = MelodiaSpacing.md,
                        end = MelodiaSpacing.md,
                        top = MelodiaSpacing.sm,
                        bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    hotSearches.firstOrNull()?.let { top ->
                        item(key = "hot_featured") {
                            FeaturedHotCard(
                                hot = top,
                                track = featuredTrack,
                                onClick = { onHotSearchClick(top.keyword) },
                                onPlay = onPlayFeatured
                            )
                        }
                    }

                    if (hotSearches.size > 1) {
                        item(key = "hot_header") { SectionHeader("热搜榜") }
                        itemsIndexed(hotSearches.drop(1), key = { index, _ -> "hot_${index + 2}" }) { index, hot ->
                            HotSearchRow(
                                rank = index + 2,
                                item = hot,
                                onClick = { onHotSearchClick(hot.keyword) }
                            )
                        }
                    }

                    if (tagGroups.isNotEmpty()) {
                        item(key = "tags_header") { SectionHeader("精品歌单") }
                        itemsIndexed(tagGroups, key = { index, _ -> "tag_group_$index" }) { groupIndex, group ->
                            PlaylistTagGroup(
                                tags = group,
                                groupIndex = groupIndex,
                                onClick = onPlaylistTagClick,
                                modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = MelodiaSpacing.lg, bottom = MelodiaSpacing.sm)
    )
}

// 底色取首条单曲封面主色
@Composable
private fun FeaturedHotCard(
    hot: HotSearch,
    track: Track?,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    val coverUrl = track?.al?.picUrl?.takeIf { it.isNotBlank() }
    var baseColor by remember { mutableStateOf(FallbackBase) }
    LaunchedEffect(coverUrl) {
        baseColor = if (coverUrl != null) extractBaseColorFromUrl(context, coverUrl) else FallbackBase
    }
    val animatedBase by animateColorAsState(baseColor, tween(500), label = "featured_hot_base")
    val secondaryText = Color.White.copy(alpha = 0.72f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(MelodiaPress.Card, onClick = onClick)
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.horizontalGradient(listOf(animatedBase, animatedBase.darken(0.45f))))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(RadiusCompact)),
            contentAlignment = Alignment.Center
        ) {
            if (coverUrl != null) {
                SubcomposeAsyncImage(
                    model = "$coverUrl?param=200y200",
                    contentDescription = hot.keyword,
                    contentScale = ContentScale.Crop,
                    loading = { CoverPlaceholder() },
                    error = { CoverPlaceholder() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Whatshot,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listOfNotNull("热搜 No.1", formatHotScore(hot.score)).joinToString(" · "),
                color = secondaryText,
                fontSize = 12.sp
            )
            Text(
                text = hot.keyword,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            if (hot.description.isNotBlank()) {
                Text(
                    text = hot.description,
                    color = secondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (track != null) {
            Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
            MelodiaIconButton(
                onClick = onPlay,
                containerColor = Color.White,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "播放 ${track.name}",
                    tint = animatedBase.darken(0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun HotSearchRow(
    rank: Int,
    item: HotSearch,
    onClick: () -> Unit
) {
    val isTop3 = rank <= 3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(MelodiaPress.Row, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            color = if (isTop3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isTop3) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp,
            modifier = Modifier.width(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.keyword,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isTop3) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!item.iconUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    AsyncImage(
                        model = item.iconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(13.dp).widthIn(max = 32.dp)
                    )
                }
            }
            if (isTop3 && item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        formatHotScore(item.score)?.let { score ->
            Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
            Text(
                text = score,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

// 每组三张：一大两小，大卡左右交替摆放；末组不足三张时均分一行
@Composable
private fun PlaylistTagGroup(
    tags: List<PlaylistTag>,
    groupIndex: Int,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MelodiaSpacing.sm
    val bigHeight: Dp = TagSmallHeight * 2 + spacing
    val colorOffset = groupIndex * 3

    if (tags.size < 3) {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            tags.forEachIndexed { index, tag ->
                PlaylistTagCard(
                    tag = tag,
                    fallbackColor = tagFallbackColors[(colorOffset + index) % tagFallbackColors.size],
                    onClick = { onClick(tag.name) },
                    modifier = Modifier.weight(1f).height(TagSmallHeight + 24.dp)
                )
            }
        }
        return
    }

    val bigOnLeft = groupIndex % 2 == 0
    Row(
        modifier = modifier.fillMaxWidth().height(bigHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        val big = @Composable { m: Modifier ->
            PlaylistTagCard(
                tag = tags[0],
                fallbackColor = tagFallbackColors[colorOffset % tagFallbackColors.size],
                onClick = { onClick(tags[0].name) },
                large = true,
                modifier = m
            )
        }
        val smalls = @Composable { m: Modifier ->
            Column(modifier = m, verticalArrangement = Arrangement.spacedBy(spacing)) {
                for (i in 1..2) {
                    PlaylistTagCard(
                        tag = tags[i],
                        fallbackColor = tagFallbackColors[(colorOffset + i) % tagFallbackColors.size],
                        onClick = { onClick(tags[i].name) },
                        modifier = Modifier.fillMaxWidth().height(TagSmallHeight)
                    )
                }
            }
        }
        if (bigOnLeft) {
            big(Modifier.weight(1.3f).fillMaxHeight())
            smalls(Modifier.weight(1f).fillMaxHeight())
        } else {
            smalls(Modifier.weight(1f).fillMaxHeight())
            big(Modifier.weight(1.3f).fillMaxHeight())
        }
    }
}

@Composable
private fun PlaylistTagCard(
    tag: PlaylistTag,
    fallbackColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    Box(
        modifier = modifier
            .pressable(MelodiaPress.Card, onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(if (tag.coverUrl.isBlank()) fallbackColor else MaterialTheme.colorScheme.surface)
    ) {
        if (tag.coverUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = tag.coverUrl,
                contentDescription = tag.name,
                contentScale = ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                        )
                    )
            )
        }
        Text(
            text = tag.name,
            color = Color.White,
            fontSize = if (large) 20.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        )
    }
}

// 热度按万为单位展示，接口缺省为 0 时不显示
private fun formatHotScore(score: Int): String? = when {
    score <= 0 -> null
    score >= 10_000 -> {
        val wan = score / 10_000.0
        if (wan >= 100) "${wan.toInt()}万" else String.format(Locale.ROOT, "%.1f万", wan).replace(".0万", "万")
    }
    else -> score.toString()
}
