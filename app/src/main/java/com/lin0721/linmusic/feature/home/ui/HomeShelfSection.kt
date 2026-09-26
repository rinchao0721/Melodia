package com.lin0721.linmusic.feature.home.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.LayoutReflowDurationMs
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaOrientationClass
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaOrientationClass
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.feature.home.domain.HomeCard
import com.lin0721.linmusic.feature.home.domain.HomeShelf

// 网格列数与网格/横向滚动切换阈值按断点取值：
// 手机 2 列 3 行不变；平板竖屏 4 列 2 行；平板横屏可用宽度更富余，6 列 2 行
private const val GRID_COLUMNS_COMPACT = 2
private const val GRID_COLUMNS_EXPANDED_PORTRAIT = 4
private const val GRID_COLUMNS_EXPANDED_LANDSCAPE = 6
private const val GRID_MAX_ROWS_COMPACT = 3
private const val GRID_MAX_ROWS_EXPANDED = 2

// 封面解码尺寸固定，与链接请求的 400y400 对齐，不随卡片宽度变化
private const val SHELF_COVER_DECODE_SIZE_PX = 400

private val GridGap = 12.dp
private val GridRowGap = 14.dp
private val HorizontalCardWidthCompact = 150.dp
private val HorizontalCardWidthExpanded = 200.dp

internal val HomeEdgePadding = 20.dp

@Composable
private fun rememberShelfGridColumns(): Int {
    val windowSizeClass = LocalMelodiaWindowSizeClass.current
    val orientationClass = LocalMelodiaOrientationClass.current
    return when {
        windowSizeClass == MelodiaWindowSizeClass.Expanded && orientationClass == MelodiaOrientationClass.Landscape ->
            GRID_COLUMNS_EXPANDED_LANDSCAPE
        windowSizeClass == MelodiaWindowSizeClass.Expanded -> GRID_COLUMNS_EXPANDED_PORTRAIT
        else -> GRID_COLUMNS_COMPACT
    }
}

// 一个货架：标题 + 卡片区。卡片少走网格，多则横向滚动。
// 网格用 FlowRow 而非懒加载网格——垂直懒加载容器嵌进外层 LazyColumn 会因无界高度约束崩溃；
// 所有卡片同处一个父级，列数变化时卡片保持身份，才能平滑过渡到新位置。
// 横向 LazyRow 方向不同，宽度有界，可以安全嵌套。
@Composable
fun HomeShelfSection(
    shelf: HomeShelf,
    onCardClick: (HomeCard) -> Unit,
    modifier: Modifier = Modifier
) {
    val windowSizeClass = LocalMelodiaWindowSizeClass.current
    val columns = rememberShelfGridColumns()
    val maxRows = if (windowSizeClass == MelodiaWindowSizeClass.Expanded) GRID_MAX_ROWS_EXPANDED else GRID_MAX_ROWS_COMPACT
    val gridMaxCards = columns * maxRows
    // 平板播放面板开合时断点会切换，横向卡片宽度平滑过渡而非一帧跳变
    val horizontalCardWidth by animateDpAsState(
        targetValue = if (windowSizeClass == MelodiaWindowSizeClass.Expanded) HorizontalCardWidthExpanded else HorizontalCardWidthCompact,
        animationSpec = tween(LayoutReflowDurationMs, easing = FastOutSlowInEasing),
        label = "shelf_horizontal_card_width"
    )

    Column(modifier = modifier.fillMaxWidth().padding(top = MelodiaSpacing.lg)) {
        Text(
            text = shelf.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = HomeEdgePadding, end = HomeEdgePadding, bottom = 13.dp)
        )

        if (shelf.cards.size <= gridMaxCards) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HomeEdgePadding)
                    .padding(bottom = GridRowGap),
                horizontalArrangement = Arrangement.spacedBy(GridGap),
                verticalArrangement = Arrangement.spacedBy(GridRowGap),
                maxItemsInEachRow = columns
            ) {
                shelf.cards.forEachIndexed { index, card ->
                    // 服务端会把同一资源投放到多个位次，key 必须带类型与下标才不会撞
                    key("${card::class.simpleName}_${card.id}_$index") {
                        HomeShelfCard(
                            card = card,
                            rank = (index + 1).takeIf { shelf.showRank },
                            modifier = Modifier
                                .weight(1f)
                                .homeReflowBounds(),
                            onClick = { onCardClick(card) }
                        )
                    }
                }
                // 不满一整行时补等宽占位，避免最后一张/几张被拉伸
                repeat((columns - shelf.cards.size % columns) % columns) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = HomeEdgePadding),
                horizontalArrangement = Arrangement.spacedBy(GridGap)
            ) {
                // 服务端会把同一资源投放到多个位次，key 必须带类型与下标才不会撞
                itemsIndexed(
                    items = shelf.cards,
                    key = { index, card -> "${card::class.simpleName}_${card.id}_$index" }
                ) { index, card ->
                    HomeShelfCard(
                        card = card,
                        rank = (index + 1).takeIf { shelf.showRank },
                        modifier = Modifier.width(horizontalCardWidth),
                        onClick = { onCardClick(card) }
                    )
                }
            }
        }
    }
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
            // 平板让位切换密度后卡片尺寸会变，固定解码尺寸才能继续命中内存缓存，不闪占位图
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

            rank?.let {
                Text(
                    text = it.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

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
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Text(
            text = card.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp)
        )

        if (card.caption.isNotBlank()) {
            Text(
                text = card.caption,
                color = TextGray,
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
