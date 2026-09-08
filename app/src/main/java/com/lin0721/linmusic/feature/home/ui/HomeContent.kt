package com.lin0721.linmusic.feature.home.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.feature.home.domain.HomeCard
import kotlinx.coroutines.flow.distinctUntilChanged

// 首页信息流骨架：单个 LazyColumn 承载服务端下发的货架序列，顶栏由 HomeScreen 统一渲染。
// 货架内部两列是手写 Row，不能换成懒加载网格，嵌进 LazyColumn 会因无界高度约束崩溃。
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
    onIntelligenceClick: () -> Unit,
    onRoamingClick: () -> Unit
) {
    val listState = rememberLazyListState()
    val feed = (uiState as? HomeUiState.Success)?.data

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
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

                    item {
                        RecentPlaySection(
                            items = data.recentPlaylists,
                            onClick = { item -> onPlaylistClick(item.id, false) }
                        )
                    }

                    item {
                        ForYouSection(
                            dailySongs = data.dailySongs,
                            toplists = data.toplistItems,
                            recommendPlaylists = data.recommendPlaylists,
                            onDailyRecommendClick = { onPlaylistClick(-1L, false) },
                            onHotlistClick = { onPlaylistClick(it, false) },
                            onIntelligenceClick = onIntelligenceClick,
                            onRadarClick = { onPlaylistClick(it, false) },
                            onRoamingClick = onRoamingClick
                        )
                    }

                    items(
                        items = data.shelves,
                        key = { it.blockCode }
                    ) { shelf ->
                        HomeShelfSection(
                            shelf = shelf,
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

    // 翻页条件只能进 LaunchedEffect 的 key，不能塞进 snapshotFlow 的闭包——
    // uiState 是入参而非 State，闭包会一直捕获创建时那一份，翻页将永远触发不了。
    val canLoadMore = feed != null && feed.hasMore && !feed.isLoadingMore

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
