package com.lin0721.linmusic.desktop.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.sizedCoverUrl
import com.lin0721.linmusic.desktop.ui.home.CardTile
import com.lin0721.linmusic.desktop.ui.home.CardWidth
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.search.domain.HotSearch
import com.lin0721.linmusic.feature.search.domain.PlaylistTag
import com.lin0721.linmusic.feature.search.ui.DiscoveryUiState
import com.lin0721.linmusic.feature.search.ui.PlaylistCategoryUiState
import com.lin0721.linmusic.feature.search.ui.PlaylistCategoryViewModel
import com.lin0721.linmusic.feature.search.ui.SearchViewModel

// 分类卡片底色轮换，无封面时也能区分
private val TagColors = listOf(
    Color(0xFF8C1932), Color(0xFF1E3264), Color(0xFF477D95), Color(0xFFAF2896),
    Color(0xFF148A08), Color(0xFFE8115B), Color(0xFF503750), Color(0xFFBA5D07)
)

private const val HOT_SEARCH_SHOWN = 10
private const val CATEGORY_LOAD_MORE_THRESHOLD = 6

// 浏览页：热搜榜 + 歌单分类
@Composable
fun BrowsePage(
    viewModel: SearchViewModel,
    onHotSearchClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.discoveryState.collectAsState()
    val discoveryGridState = rememberLazyGridState()
    when (val s = state) {
        DiscoveryUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DesktopColors.Accent)
        }
        is DiscoveryUiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, color = DesktopColors.TextGray)
                TextButton(onClick = viewModel::retryDiscovery) { Text("重试", color = DesktopColors.TextPrimary) }
            }
        }
        is DiscoveryUiState.Success -> HoverScrollbarBox(discoveryGridState) {
            LazyVerticalGrid(
                state = discoveryGridState,
                columns = GridCells.Adaptive(180.dp),
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (s.hotSearches.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { PageTitle("热搜榜") }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HotSearchColumns(s.hotSearches.take(HOT_SEARCH_SHOWN), onHotSearchClick)
                    }
                }
                if (s.playlistTags.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { PageTitle("歌单分类") }
                    itemsIndexed(s.playlistTags, key = { _, tag -> tag.name }) { index, tag ->
                        TagTile(tag, TagColors[index % TagColors.size]) { onCategoryClick(tag.name) }
                    }
                }
            }
        }
    }
}

// 两列排布，左列 1-5、右列 6-10
@Composable
private fun HotSearchColumns(items: List<HotSearch>, onClick: (String) -> Unit) {
    val half = (items.size + 1) / 2
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        listOf(items.take(half), items.drop(half)).forEachIndexed { column, list ->
            Column(Modifier.weight(1f)) {
                list.forEachIndexed { i, hot ->
                    val rank = column * half + i + 1
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { onClick(hot.keyword) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$rank",
                            color = if (rank <= 3) DesktopColors.AccentText else DesktopColors.TextGray,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(28.dp)
                        )
                        Text(
                            hot.keyword,
                            color = DesktopColors.TextPrimary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagTile(tag: PlaylistTag, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(8.dp)).background(color).clickable(onClick = onClick)
    ) {
        if (tag.coverUrl.isNotBlank()) {
            AsyncImage(
                model = sizedCoverUrl(tag.coverUrl, 200),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // 封面斜放在右下角，作为分类卡片的装饰
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp)
                    .width(72.dp).aspectRatio(1f).clip(RoundedCornerShape(4.dp))
            )
        }
        Text(
            tag.name,
            color = DesktopColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(14.dp)
        )
    }
}

// 分类歌单列表页
@Composable
fun PlaylistCategoryPage(
    category: String,
    viewModel: PlaylistCategoryViewModel,
    onPlaylistClick: (id: Long, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(category) { viewModel.load(category) }
    val state by viewModel.uiState.collectAsState()
    when (val s = state) {
        PlaylistCategoryUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DesktopColors.Accent)
        }
        PlaylistCategoryUiState.Empty -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("这个分类下暂时没有歌单", color = DesktopColors.TextGray)
        }
        is PlaylistCategoryUiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, color = DesktopColors.TextGray)
                TextButton(onClick = viewModel::retry) { Text("重试", color = DesktopColors.TextPrimary) }
            }
        }
        is PlaylistCategoryUiState.Success -> {
            val gridState = rememberLazyGridState()
            val shouldLoadMore by remember(s) {
                derivedStateOf {
                    val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    s.hasMore && !s.isLoadingMore &&
                        lastVisible >= gridState.layoutInfo.totalItemsCount - CATEGORY_LOAD_MORE_THRESHOLD
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) viewModel.loadMore()
            }
            HoverScrollbarBox(gridState) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(CardWidth),
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { PageTitle(category) }
                    items(s.playlists, key = { it.id }) { playlist ->
                        CardTile(playlist.coverImgUrl, playlist.name, playlist.creator?.nickname.orEmpty()) {
                            onPlaylistClick(playlist.id, playlist.name)
                        }
                    }
                    if (s.isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = DesktopColors.Accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageTitle(text: String) {
    Text(text, color = DesktopColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
}
