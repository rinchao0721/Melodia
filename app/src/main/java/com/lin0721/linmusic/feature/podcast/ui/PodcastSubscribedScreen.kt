package com.lin0721.linmusic.feature.podcast.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.theme.rememberMelodiaGridColumns
import com.lin0721.linmusic.feature.home.ui.ErrorContent
import com.lin0721.linmusic.feature.home.ui.LoadingIndicator
import org.koin.androidx.compose.koinViewModel
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Headphones

// 「我的订阅」二级页：订阅的电台铺成网格，可切换排序，有更新的电台带「新」标记
@Composable
fun PodcastSubscribedScreen(
    onBack: () -> Unit,
    onRadioClick: (Long) -> Unit,
    viewModel: PodcastSubscribedViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.loadIfNeeded() }

    SecondaryScreenScaffold(title = "我的订阅", onBack = onBack, wideLayout = true) {
        when (val state = uiState) {
            PodcastSubscribedUiState.Loading -> LoadingIndicator()
            PodcastSubscribedUiState.NotLoggedIn -> EmptyState(
                icon = MelodiaIcons.Headphones,
                title = "登录后查看订阅的电台"
            )
            is PodcastSubscribedUiState.Error -> ErrorContent(message = state.message, onRetry = viewModel::retry)
            is PodcastSubscribedUiState.Success -> SubscribedContent(
                state = state,
                onSortSelect = viewModel::setSort,
                onLoadMore = viewModel::loadMore,
                onRadioClick = onRadioClick
            )
        }
    }
}

@Composable
private fun SubscribedContent(
    state: PodcastSubscribedUiState.Success,
    onSortSelect: (PodcastSubscribedSort) -> Unit,
    onLoadMore: () -> Unit,
    onRadioClick: (Long) -> Unit
) {
    val columns = rememberMelodiaGridColumns(compact = 3, expandedPortrait = 5, expandedLandscape = 7)
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, enabled = state.hasMore && !state.isLoadingMore, onLoadMore = onLoadMore)

    if (state.radios.isEmpty()) {
        EmptyState(
            icon = MelodiaIcons.Headphones,
            title = "还没有订阅的电台",
            subtitle = "在电台详情页点「订阅」，更新会出现在这里"
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PodcastEdgePadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        PodcastSubscribedSort.entries.forEach { sort ->
            PodcastFilterChip(text = sort.label, selected = state.sort == sort) { onSortSelect(sort) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
    ) {
        podcastGridItems(state.sortedRadios, columns, "subscribed") { radio, modifier ->
            PodcastRadioCard(
                radio = radio,
                onClick = { onRadioClick(radio.id) },
                modifier = modifier,
                hasUpdate = radio.id in state.updatedRadioIds
            )
        }
        if (state.isLoadingMore) {
            item(key = "loading_more") {
                Box(modifier = Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
