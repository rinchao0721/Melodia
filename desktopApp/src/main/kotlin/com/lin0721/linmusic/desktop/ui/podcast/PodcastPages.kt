package com.lin0721.linmusic.desktop.ui.podcast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.home.CardWidth
import com.lin0721.linmusic.desktop.ui.home.HomeTabError
import com.lin0721.linmusic.desktop.ui.home.HomeTabLoading
import com.lin0721.linmusic.desktop.ui.home.OutlineChip
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.podcast.domain.formatSubCount
import com.lin0721.linmusic.feature.podcast.ui.PodcastCategoryUiState
import com.lin0721.linmusic.feature.podcast.ui.PodcastCategoryViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastSection
import com.lin0721.linmusic.feature.podcast.ui.PodcastSubscribedSort
import com.lin0721.linmusic.feature.podcast.ui.PodcastSubscribedUiState
import com.lin0721.linmusic.feature.podcast.ui.PodcastSubscribedViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastToplistState
import com.lin0721.linmusic.feature.podcast.ui.PodcastToplistTab
import com.lin0721.linmusic.feature.podcast.ui.PodcastToplistViewModel

// 距离底部还剩几项时加载下一页
private const val GRID_LOAD_MORE_THRESHOLD = 12
private const val LIST_LOAD_MORE_THRESHOLD = 6

@Composable
private fun PodcastPageTitle(text: String) {
    Text(text, color = DesktopColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun LoadingMoreSpinner() {
    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DesktopColors.Accent)
    }
}

// 网格滚到接近底部时触发翻页
@Composable
private fun GridLoadMoreEffect(state: LazyGridState, enabled: Boolean, onLoadMore: () -> Unit) {
    val shouldLoadMore by remember(enabled) {
        derivedStateOf {
            val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            enabled && lastVisible >= state.layoutInfo.totalItemsCount - GRID_LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }
}

@Composable
private fun ListLoadMoreEffect(state: LazyListState, enabled: Boolean, onLoadMore: () -> Unit) {
    val shouldLoadMore by remember(enabled) {
        derivedStateOf {
            val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            enabled && lastVisible >= state.layoutInfo.totalItemsCount - LIST_LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }
}

// 「我的订阅」二级页：订阅的电台铺成网格，可切换排序，有更新的电台带「新」标记
@Composable
fun PodcastSubscribedPage(viewModel: PodcastSubscribedViewModel, modifier: Modifier = Modifier) {
    val navigator = LocalDesktopNavigator.current
    LaunchedEffect(viewModel) { viewModel.loadIfNeeded() }
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        PodcastSubscribedUiState.Loading -> HomeTabLoading(modifier)
        PodcastSubscribedUiState.NotLoggedIn -> PodcastHint(
            text = "登录后查看订阅的电台",
            modifier = modifier,
            actionText = "去登录",
            onAction = navigator.openLogin
        )
        is PodcastSubscribedUiState.Error -> HomeTabError(state.message, viewModel::retry, modifier)
        is PodcastSubscribedUiState.Success -> {
            if (state.radios.isEmpty()) {
                PodcastHint("还没有订阅的电台，在电台详情页点「订阅」，更新会出现在这里", modifier)
                return
            }
            val gridState = rememberLazyGridState()
            GridLoadMoreEffect(gridState, state.hasMore && !state.isLoadingMore, viewModel::loadMore)
            HoverScrollbarBox(gridState) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(CardWidth),
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PodcastPageTitle("我的订阅")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PodcastSubscribedSort.entries.forEach { sort ->
                                    OutlineChip(sort.label, state.sort == sort) { viewModel.setSort(sort) }
                                }
                            }
                        }
                    }
                    itemsIndexed(state.sortedRadios, key = { index, radio -> "${radio.id}_$index" }) { _, radio ->
                        PodcastRadioTile(
                            radio = radio,
                            onClick = { navigator.openRadio(radio.id) },
                            hasUpdate = radio.id in state.updatedRadioIds
                        )
                    }
                    if (state.isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) { LoadingMoreSpinner() }
                    }
                }
            }
        }
    }
}

// 分类二级页：该分类的热门电台，可翻页
@Composable
fun PodcastCategoryPage(
    categoryId: Long,
    name: String,
    viewModel: PodcastCategoryViewModel,
    modifier: Modifier = Modifier
) {
    val navigator = LocalDesktopNavigator.current
    LaunchedEffect(categoryId) { viewModel.load(categoryId) }
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        PodcastCategoryUiState.Loading -> HomeTabLoading(modifier)
        is PodcastCategoryUiState.Error -> HomeTabError(state.message, viewModel::retry, modifier)
        is PodcastCategoryUiState.Success -> {
            val gridState = rememberLazyGridState()
            GridLoadMoreEffect(gridState, state.hasMore && !state.isLoadingMore, viewModel::loadMore)
            HoverScrollbarBox(gridState) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(CardWidth),
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { PodcastPageTitle(name) }
                    if (state.radios.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { PodcastHint("这个分类暂时没有电台") }
                    }
                    itemsIndexed(state.radios, key = { index, radio -> "${radio.id}_$index" }) { _, radio ->
                        PodcastRadioTile(radio = radio, onClick = { navigator.openRadio(radio.id) })
                    }
                    if (state.isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) { LoadingMoreSpinner() }
                    }
                }
            }
        }
    }
}

// 播客榜单二级页：电台榜与节目榜两个分段
@Composable
fun PodcastToplistPage(viewModel: PodcastToplistViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(viewModel) { viewModel.loadIfNeeded() }
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    ListLoadMoreEffect(
        listState,
        enabled = state.tab == PodcastToplistTab.PROGRAM && state.programsHasMore && !state.isLoadingMorePrograms,
        onLoadMore = viewModel::loadMorePrograms
    )

    HoverScrollbarBox(listState) {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "header") {
                Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PodcastPageTitle("播客榜单")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PodcastToplistTab.entries.forEach { tab ->
                            OutlineChip(tab.label, state.tab == tab) { viewModel.selectTab(tab) }
                        }
                    }
                }
            }
            when (state.tab) {
                PodcastToplistTab.RADIO -> radioToplist(state, viewModel)
                PodcastToplistTab.PROGRAM -> programToplist(state, viewModel)
            }
        }
    }
}

private fun LazyListScope.radioToplist(
    state: PodcastToplistState,
    viewModel: PodcastToplistViewModel
) {
    when (val section = state.radios) {
        PodcastSection.Loading -> item(key = "radios_loading") { PodcastInlineLoading() }
        is PodcastSection.Error -> item(key = "radios_error") { PodcastInlineError(section.message, viewModel::retry) }
        is PodcastSection.Success -> itemsIndexed(section.data, key = { index, radio -> "${radio.id}_$index" }) { index, radio ->
            RankedRadioRow(rank = index + 1, radio = radio)
        }
    }
}

private fun LazyListScope.programToplist(
    state: PodcastToplistState,
    viewModel: PodcastToplistViewModel
) {
    when (val section = state.programs) {
        PodcastSection.Loading -> item(key = "programs_loading") { PodcastInlineLoading() }
        is PodcastSection.Error -> item(key = "programs_error") { PodcastInlineError(section.message, viewModel::retry) }
        is PodcastSection.Success -> {
            itemsIndexed(section.data, key = { index, program -> "${program.id}_$index" }) { index, program ->
                PodcastProgramRow(
                    program = program,
                    progress = state.progress[program.songId],
                    onPlay = { viewModel.playProgram(index) },
                    index = index + 1
                )
            }
            if (state.isLoadingMorePrograms) {
                item(key = "loading_more") { LoadingMoreSpinner() }
            }
        }
    }
}

// 电台榜行：名次 · 封面 · 名称与主播 · 期数 · 订阅数。点击进详情页
@Composable
private fun RankedRadioRow(rank: Int, radio: PodcastRadio) {
    val navigator = LocalDesktopNavigator.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable { navigator.openRadio(radio.id) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            color = if (rank <= 3) DesktopColors.AccentText else DesktopColors.TextGray,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp)
        )
        Cover(radio.picUrl, 48.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = radio.name,
                color = DesktopColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (radio.djName.isNotBlank()) {
                Text(radio.djName, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            text = radio.programCount.takeIf { it > 0 }?.let { "$it 期" }.orEmpty(),
            color = DesktopColors.TextGray,
            fontSize = 13.sp,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = formatSubCount(radio.subCount),
            color = DesktopColors.TextGray,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(112.dp)
        )
    }
}
