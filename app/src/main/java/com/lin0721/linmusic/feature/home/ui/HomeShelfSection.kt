package com.lin0721.linmusic.feature.home.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.LayoutReflowDurationMs
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaOrientationClass
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaOrientationClass
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.uiTween
import com.lin0721.linmusic.feature.home.domain.HomeCard
import com.lin0721.linmusic.feature.home.domain.HomeShelf

// 每屏可见卡片数，带小数是为了让最右一张露出一截，提示还能横滑
private const val VISIBLE_CARDS_COMPACT = 2.3f
private const val VISIBLE_CARDS_EXPANDED_PORTRAIT = 4.3f
private const val VISIBLE_CARDS_EXPANDED_LANDSCAPE = 6.3f

// 封面解码尺寸固定，与链接请求的 400y400 对齐，不随卡片宽度变化
private const val SHELF_COVER_DECODE_SIZE_PX = 400

private val MinCardWidth = 96.dp

internal val HomeCardGap = 12.dp
internal val HomeEdgePadding = 20.dp

// 区块之间的统一间距，由各区块自己在顶部留出
internal val HomeSectionSpacing = MelodiaSpacing.lg

// 首页横滑卡片宽度：按容器宽度与断点反推，保证每屏露出固定张数。
// 平板播放面板开合时容器宽度会突变，宽度平滑过渡而非一帧跳变
@Composable
internal fun rememberHomeCardWidth(containerWidth: Dp): Dp {
    val windowSizeClass = LocalMelodiaWindowSizeClass.current
    val orientationClass = LocalMelodiaOrientationClass.current
    val visibleCards = when {
        windowSizeClass == MelodiaWindowSizeClass.Expanded && orientationClass == MelodiaOrientationClass.Landscape ->
            VISIBLE_CARDS_EXPANDED_LANDSCAPE
        windowSizeClass == MelodiaWindowSizeClass.Expanded -> VISIBLE_CARDS_EXPANDED_PORTRAIT
        else -> VISIBLE_CARDS_COMPACT
    }
    val target = ((containerWidth - HomeEdgePadding - HomeCardGap * visibleCards.toInt()) / visibleCards)
        .coerceAtLeast(MinCardWidth)
    val width by animateDpAsState(
        targetValue = target,
        animationSpec = uiTween(LayoutReflowDurationMs, easing = FastOutSlowInEasing),
        label = "home_card_width"
    )
    return width
}

@Composable
internal fun HomeSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(bottom = 12.dp)
    )
}

// 一个货架：标题 + 横滑卡片。横向 LazyRow 宽度有界，可以安全嵌进外层 LazyColumn
@Composable
fun HomeShelfSection(
    shelf: HomeShelf,
    onCardClick: (HomeCard) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(top = HomeSectionSpacing)) {
        val cardWidth = rememberHomeCardWidth(maxWidth)
        Column(modifier = Modifier.fillMaxWidth()) {
            HomeSectionTitle(
                text = shelf.title,
                modifier = Modifier.padding(horizontal = HomeEdgePadding)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = HomeEdgePadding),
                horizontalArrangement = Arrangement.spacedBy(HomeCardGap)
            ) {
                // 服务端会把同一资源投放到多个位次，key 必须带类型与下标才不会撞
                itemsIndexed(
                    items = shelf.cards,
                    key = { index, card -> "${card::class.simpleName}_${card.id}_$index" }
                ) { index, card ->
                    HomeShelfCard(
                        card = card,
                        rank = (index + 1).takeIf { shelf.showRank },
                        modifier = Modifier.width(cardWidth),
                        onClick = { onCardClick(card) }
                    )
                }
            }
        }
    }
}

private fun HomeCard.typeLabel(): String = when (this) {
    is HomeCard.Playlist -> "歌单"
    is HomeCard.Album -> "专辑"
    is HomeCard.Song -> "单曲"
    is HomeCard.Voice -> "播客"
}

@Composable
private fun HomeShelfCard(
    card: HomeCard,
    rank: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(modifier = modifier.pressable(MelodiaPress.Card) { onClick() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(RadiusCompact))
        ) {
            // 卡宽随容器变化，固定解码尺寸才能继续命中内存缓存，不闪占位图
            val context = LocalContext.current
            val coverRequest = remember(card.coverUrl) {
                ImageRequest.Builder(context)
                    .data(card.coverUrl.withCoverParam("400y400"))
                    .size(SHELF_COVER_DECODE_SIZE_PX)
                    .build()
            }
            SubcomposeAsyncImage(
                model = coverRequest,
                contentDescription = card.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentScale = ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() }
            )

            // 歌曲与播客单集点一下直接播放，给个播放符号说清楚；歌单与专辑是进详情页，不加
            if (card is HomeCard.Song || card is HomeCard.Voice) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MelodiaColors.OnAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Text(
            text = rank?.let { "${card.typeLabel()} · $it" } ?: card.typeLabel(),
            color = MelodiaColors.TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 7.dp)
        )

        Text(
            text = card.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp)
        )

        if (card.caption.isNotBlank()) {
            Text(
                text = card.caption,
                color = MelodiaColors.TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// 翻页加载中的占位，服务端翻完两页后不再出现
@Composable
fun HomeShelfLoadingMore() {
    Box(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
