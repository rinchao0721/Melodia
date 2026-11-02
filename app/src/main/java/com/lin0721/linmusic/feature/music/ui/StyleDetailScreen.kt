package com.lin0721.linmusic.feature.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.smoothVerticalGradient
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.home.ui.ErrorContent
import com.lin0721.linmusic.feature.music.domain.StyleSort
import org.koin.androidx.compose.koinViewModel

// 距离列表底部还剩几项时加载下一页曲目
private const val SONG_LOAD_MORE_THRESHOLD = 8

// 曲风详情页：通栏头图 → 播放与二级标签 → 歌单 / 专辑 / 歌手横滑 → 可翻页曲目列表
@Composable
fun StyleDetailScreen(
    tagId: Long,
    name: String,
    viewModel: StyleDetailViewModel = koinViewModel(),
    onBack: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()

    LaunchedEffect(tagId) {
        viewModel.load(tagId)
    }
    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    Box(modifier = Modifier.fillMaxSize().background(MelodiaColors.PageFill)) {
        when (val state = uiState) {
            StyleDetailUiState.Loading -> PlaceholderPage(name) { MusicSectionLoading() }
            is StyleDetailUiState.Error -> PlaceholderPage(name) {
                ErrorContent(message = state.message, onRetry = { viewModel.retry() })
            }
            is StyleDetailUiState.Success -> {
                // ViewModel 在切换曲风的瞬间仍持有上一个曲风，避免闪现旧内容
                if (state.data.tagId != tagId) {
                    PlaceholderPage(name) { MusicSectionLoading() }
                } else {
                    StyleDetailList(
                        data = state.data,
                        fallbackName = name,
                        nowPlayingSongId = nowPlaying?.songId,
                        viewModel = viewModel,
                        onPlaylistClick = onPlaylistClick,
                        onAlbumClick = onAlbumClick,
                        onArtistClick = onArtistClick
                    )
                }
            }
        }

        MelodiaIconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(MelodiaSpacing.xs)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
    }
}

@Composable
private fun PlaceholderPage(name: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        MusicStyleHeroPlaceholder(name)
        content()
    }
}

@Composable
private fun StyleDetailList(
    data: StyleDetailData,
    fallbackName: String,
    nowPlayingSongId: Long?,
    viewModel: StyleDetailViewModel,
    onPlaylistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit
) {
    val accent = data.head?.colorHex.toStyleColor()
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(data) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            data.hasMoreSongs && !data.isLoadingMore && !data.isContentLoading && !data.isSongsLoading &&
                lastVisible >= listState.layoutInfo.totalItemsCount - SONG_LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreSongs()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
    ) {
        item(key = "hero") { MusicStyleHero(data.head, fallbackName, accent) }
        item(key = "actions") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.smoothVerticalGradient(from = accent.copy(alpha = 0.32f), to = Color.Transparent))
                    .padding(top = 16.dp, bottom = 4.dp)
            ) {
                ActionRow(
                    data = data,
                    onPlayAll = { viewModel.playSongAt(0) },
                    onPlayFavourite = { viewModel.playFavourite() }
                )
                if (data.children.isNotEmpty()) {
                    Box(modifier = Modifier.padding(top = 14.dp)) {
                        MusicSubStyleChips(
                            children = data.children,
                            selectedChildId = data.selectedChildId,
                            onSelect = { viewModel.selectChild(it) }
                        )
                    }
                }
            }
        }

        if (data.isContentLoading) {
            item(key = "content_loading") { MusicSectionLoading() }
            return@LazyColumn
        }

        if (data.playlists.isNotEmpty()) {
            item(key = "playlists") {
                Column {
                    MusicSectionTitle("热门歌单")
                    MusicPlaylistRow(playlists = data.playlists, onClick = { onPlaylistClick(it.id) })
                }
            }
        }
        if (data.albums.isNotEmpty()) {
            item(key = "albums") {
                Column {
                    MusicSectionTitle("热门专辑")
                    MusicAlbumRow(albums = data.albums, onClick = { onAlbumClick(it.id) })
                }
            }
        }
        if (data.artists.isNotEmpty()) {
            item(key = "artists") {
                Column {
                    MusicSectionTitle("代表歌手")
                    MusicArtistRow(artists = data.artists, onClick = { onArtistClick(it.id) })
                }
            }
        }

        item(key = "songs_title") {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.weight(1f)) { MusicSectionTitle("歌曲") }
                SortMenu(
                    sort = data.sort,
                    onSelect = { viewModel.selectSort(it) },
                    modifier = Modifier.padding(end = MusicEdgePadding - 8.dp, bottom = 4.dp)
                )
            }
        }
        if (data.isSongsLoading) {
            item(key = "songs_loading") { MusicSectionLoading() }
        } else {
            itemsIndexed(data.songs, key = { index, track -> "${track.id}_$index" }) { index, track ->
                MusicSongRow(
                    index = index,
                    track = track,
                    isCurrent = nowPlayingSongId == track.id,
                    onClick = { viewModel.playSongAt(index) }
                )
            }
            if (data.isLoadingMore) {
                item(key = "loading_more") {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(data: StyleDetailData, onPlayAll: () -> Unit, onPlayFavourite: () -> Unit) {
    val canPlay = data.songs.isNotEmpty()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MusicEdgePadding),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .pressable(MelodiaPress.Icon, enabled = canPlay) { onPlayAll() }
                .clip(CircleShape)
                .background(if (canPlay) MaterialTheme.colorScheme.primary else MelodiaColors.Selected),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "播放全部",
                tint = MelodiaColors.OnAccent,
                modifier = Modifier.size(26.dp)
            )
        }
        data.head?.favouriteSong?.let { track ->
            MusicFavouriteSongCard(track = track, onClick = onPlayFavourite, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SortMenu(sort: StyleSort, onSelect: (StyleSort) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .pressable(MelodiaPress.Pill) { expanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = sort.label(), color = MelodiaColors.TextSecondary, fontSize = 12.sp)
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "排序方式",
                tint = MelodiaColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StyleSort.entries.forEach { option ->
                val selected = option == sort
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label(),
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

private fun StyleSort.label(): String = when (this) {
    StyleSort.Hot -> "热门"
    StyleSort.Latest -> "最新"
}
