package com.lin0721.linmusic.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.platform.toDisplayTrack
import com.lin0721.linmusic.core.platform.repository.UnifiedRank
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.icons.Leaderboard
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Play
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.feature.home.data.DailySong
import com.lin0721.linmusic.feature.home.domain.HomeCard
import kotlinx.coroutines.flow.distinctUntilChanged

private const val RECENT_PLAY_KEY = "recent_play"
private const val FOR_YOU_KEY = "for_you"

// 首页信息流骨架：单个 LazyColumn 承载服务端下发的货架序列，顶栏由 HomeScreen 统一渲染。
// 货架与最近播放的网格用 FlowRow 而非懒加载网格：嵌进 LazyColumn 会因无界高度约束崩溃；
// 所有卡片同处一个父级，列数变化时卡片保持身份，才能从旧位置平滑过渡到新排版
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onPlaylistClick: (Long, Boolean) -> Unit,
    onSongClick: (shelfTitle: String, List<HomeCard.Song>, HomeCard.Song) -> Unit,
    onVoiceClick: (shelfTitle: String, List<HomeCard.Voice>, HomeCard.Voice) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPersonalFmClick: () -> Unit,
    onRoamingClick: () -> Unit,
    onDailyPlay: () -> Unit,
    personalFmMode: PersonalFmMode,
    fmCoverUrls: List<String>,
    isFmPlaying: Boolean,
    roamingCoverUrl: String,
    hotTopCoverUrl: String,
    isRoamingPlaying: Boolean,
    showRecentPlay: Boolean,
    showForYou: Boolean,
    currentSongId: Long? = null,
    isPlaying: Boolean = false,
    onPlaylistEntityClick: (String, Boolean) -> Unit = { _, _ -> },
    onKugouDailyOpen: () -> Unit = {},
    onKugouDiscoverTrackClick: (Int) -> Unit = {},
    onKugouRankOpen: (UnifiedRank) -> Unit = {},
    onKugouRankPlay: (UnifiedRank) -> Unit = {},
    onCloudClick: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val feed = (uiState as? HomeUiState.Success)?.data

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LookaheadScope {
            CompositionLocalProvider(LocalHomeLookaheadScope provides this) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
                ) {
                    when (uiState) {
                        is HomeUiState.Loading -> item { LoadingIndicator() }

                        is HomeUiState.Error -> item {
                            ErrorContent(message = uiState.message, onRetry = onRetry)
                        }

                        is HomeUiState.Success -> {
                            val data = uiState.data

                            if (data.browsePlatform == MusicPlatform.KUGOU) {
                                item(key = "kugou_quick_actions") {
                                    KugouQuickActionsRow(
                                        isFmPlaying = isFmPlaying,
                                        onDailyClick = onKugouDailyOpen,
                                        onFmClick = onPersonalFmClick,
                                        onRankClick = {
                                            data.kugouToplists.firstOrNull()?.let(onKugouRankOpen)
                                        },
                                        onCloudClick = onCloudClick
                                    )
                                }

                                if (data.kugouDailySongs.isNotEmpty()) {
                                    item(key = "kugou_daily_spotlight") {
                                        val dailyPreview = remember(data.kugouDailySongs) {
                                            data.kugouDailySongs.map { daily ->
                                                val t = daily.track.toDisplayTrack()
                                                DailySong(id = t.id, name = t.name, ar = t.ar, al = t.al)
                                            }
                                        }
                                        Column(Modifier.padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)) {
                                            KugouSectionTitle("每日推荐")
                                            Spacer(Modifier.height(MelodiaSpacing.sm))
                                            DailySpotlightCard(
                                                songs = dailyPreview,
                                                onOpen = onKugouDailyOpen,
                                                onPlay = onDailyPlay,
                                                minHeight = 132.dp
                                            )
                                        }
                                    }
                                }

                                if (data.kugouDiscoverTracks.isNotEmpty()) {
                                    item(key = "kugou_discover_header") {
                                        Column(Modifier.padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)) {
                                            KugouSectionTitle("发现推荐")
                                        }
                                    }
                                    val shownDiscover = data.kugouDiscoverTracks.take(12)
                                    itemsIndexed(shownDiscover, key = { index, t -> "kugou_discover_${t.id.canonical}_$index" }) { index, unified ->
                                        val track = unified.toDisplayTrack()
                                        SongRow(
                                            data = SongRowData(
                                                id = track.id,
                                                title = track.name,
                                                artist = track.ar.joinToString(" / ") { it.name },
                                                coverUrl = track.al.picUrl
                                            ),
                                            isActive = currentSongId == track.id,
                                            isPlaying = isPlaying,
                                            compact = true,
                                            onClick = { onKugouDiscoverTrackClick(index) }
                                        )
                                    }
                                }

                                if (data.kugouPlaylists.isNotEmpty()) {
                                    item(key = "kugou_playlists_shelf") {
                                        Column(Modifier.padding(vertical = MelodiaSpacing.sm)) {
                                            Box(Modifier.padding(horizontal = MelodiaSpacing.md)) {
                                                KugouSectionTitle("推荐歌单")
                                            }
                                            Spacer(Modifier.height(MelodiaSpacing.sm))
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = MelodiaSpacing.md),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                items(data.kugouPlaylists, key = { it.id.canonical }) { playlist ->
                                                    Column(
                                                        modifier = Modifier
                                                            .width(132.dp)
                                                            .clip(RoundedCornerShape(RadiusCompact))
                                                            .clickable { onPlaylistEntityClick(playlist.id.canonical, false) }
                                                    ) {
                                                        AsyncImage(
                                                            model = playlist.coverUrl,
                                                            contentDescription = playlist.name,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier
                                                                .size(132.dp)
                                                                .clip(RoundedCornerShape(RadiusCompact))
                                                                .background(MaterialTheme.colorScheme.surface)
                                                        )
                                                        Spacer(Modifier.height(6.dp))
                                                        Text(
                                                            text = playlist.name,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontSize = 13.sp,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (data.kugouToplists.isNotEmpty()) {
                                    item(key = "kugou_toplists_shelf") {
                                        Column(Modifier.padding(vertical = MelodiaSpacing.sm)) {
                                            Box(Modifier.padding(horizontal = MelodiaSpacing.md)) {
                                                KugouSectionTitle("酷狗排行榜")
                                            }
                                            Spacer(Modifier.height(MelodiaSpacing.sm))
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = MelodiaSpacing.md),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                items(data.kugouToplists, key = { it.id }) { rank ->
                                                    Column(
                                                        modifier = Modifier
                                                            .width(148.dp)
                                                            .clip(RoundedCornerShape(RadiusCompact))
                                                            .background(MaterialTheme.colorScheme.surface)
                                                            .clickable { onKugouRankOpen(rank) }
                                                            .padding(10.dp)
                                                    ) {
                                                        Box {
                                                            AsyncImage(
                                                                model = rank.coverUrl,
                                                                contentDescription = rank.name,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier
                                                                    .size(128.dp)
                                                                    .clip(RoundedCornerShape(RadiusCompact))
                                                            )
                                                            MelodiaIconButton(
                                                                onClick = { onKugouRankPlay(rank) },
                                                                modifier = Modifier
                                                                    .align(Alignment.BottomEnd)
                                                                    .padding(4.dp)
                                                                    .size(30.dp)
                                                                    .clip(RoundedCornerShape(15.dp))
                                                                    .background(MaterialTheme.colorScheme.primary)
                                                            ) {
                                                                Icon(
                                                                    MelodiaIcons.Play,
                                                                    contentDescription = "播放${rank.name}",
                                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                        Spacer(Modifier.height(6.dp))
                                                        Text(
                                                            text = rank.name,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (rank.updateFrequency.isNotBlank()) {
                                                            Text(
                                                                text = rank.updateFrequency,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                fontSize = 11.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                            // 区块高度随列数变化时，下方区块平滑让位；只做位移，不给新增区块加淡入淡出
                            if (showRecentPlay) {
                                item(key = RECENT_PLAY_KEY) {
                                    RecentPlaySection(
                                        items = data.recentPlaylists,
                                        onClick = { item -> onPlaylistClick(item.id, false) },
                                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                                    )
                                }
                            }

                            item(key = FOR_YOU_KEY) {
                                HomeHighlightsSection(
                                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                    dailySongs = data.dailySongs,
                                    toplists = data.toplistItems,
                                    recommendPlaylists = data.recommendPlaylists,
                                    personalFmMode = personalFmMode,
                                    fmCoverUrls = fmCoverUrls,
                                    isFmPlaying = isFmPlaying,
                                    roamingCoverUrl = roamingCoverUrl,
                                    hotTopCoverUrl = hotTopCoverUrl,
                                    isRoamingPlaying = isRoamingPlaying,
                                    onDailyOpen = { onPlaylistClick(-1L, false) },
                                    onDailyPlay = onDailyPlay,
                                    onHotlistClick = { onPlaylistClick(it, false) },
                                    onPersonalFmClick = onPersonalFmClick,
                                    onRadarClick = { onPlaylistClick(it, false) },
                                    onRoamingClick = onRoamingClick,
                                    showForYou = showForYou
                                )
                            }

                            items(
                                items = data.shelves,
                                key = { it.blockCode }
                            ) { shelf ->
                                HomeShelfSection(
                                    shelf = shelf,
                                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                    onCardClick = { card ->
                                        when (card) {
                                            is HomeCard.Playlist -> onPlaylistClick(card.id, false)
                                            is HomeCard.Album -> onPlaylistClick(card.id, true)
                                            // 同货架的其余歌曲/单集卡片一起入队，播完才能自动接续
                                            is HomeCard.Song -> onSongClick(shelf.title, shelf.cards.filterIsInstance<HomeCard.Song>(), card)
                                            is HomeCard.Voice -> onVoiceClick(shelf.title, shelf.cards.filterIsInstance<HomeCard.Voice>(), card)
                                        }
                                    }
                                )
                            }

                            if (data.isLoadingMore) {
                                item { HomeShelfLoadingMore() }
                            }
                            }
                        }
                    }
                }
            }
        }
    }

    // 翻页条件只能进 LaunchedEffect 的 key，不能塞进 snapshotFlow 的闭包——
    // uiState 是入参而非 State，闭包会一直捕获创建时那一份，翻页将永远触发不了。
    val canLoadMore = feed != null && feed.browsePlatform == MusicPlatform.NETEASE && feed.hasMore && !feed.isLoadingMore

    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            // 距底部两个区块时预取
            lastVisible >= info.totalItemsCount - 2
        }
            .distinctUntilChanged()
            .collect { if (it) onLoadMore() }
    }
}

@Composable
private fun KugouSectionTitle(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun KugouQuickActionsRow(
    isFmPlaying: Boolean,
    onDailyClick: () -> Unit,
    onFmClick: () -> Unit,
    onRankClick: () -> Unit,
    onCloudClick: () -> Unit
) {
    val items = listOf(
        Triple(MelodiaIcons.Calendar, "每日推荐", onDailyClick),
        Triple(MelodiaIcons.Radio, if (isFmPlaying) "FM 播放中" else "私人 FM", onFmClick),
        Triple(MelodiaIcons.Leaderboard, "排行榜", onRankClick),
        Triple(MelodiaIcons.Cloud, "酷狗云盘", onCloudClick)
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.second }) { (icon, label, onClick) ->
            KugouQuickActionChip(
                icon = icon,
                label = label,
                active = label.startsWith("FM") && isFmPlaying,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun KugouQuickActionChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val bg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
