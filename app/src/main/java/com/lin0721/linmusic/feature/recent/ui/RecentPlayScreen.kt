package com.lin0721.linmusic.feature.recent.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.EntityCoverShape
import com.lin0721.linmusic.core.ui.components.EntityRow
import com.lin0721.linmusic.core.ui.components.EntityRowData
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.FilterChipsRow
import com.lin0721.linmusic.core.ui.components.SearchResultRowSkeleton
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.recent.domain.RecentSong
import com.lin0721.linmusic.feature.recent.domain.groupByPlayDay
import org.koin.androidx.compose.koinViewModel

private const val SKELETON_ROW_COUNT = 8

@Composable
fun RecentPlayScreen(
    viewModel: RecentPlayViewModel = koinViewModel(),
    onBack: () -> Unit,
    onPlaylistClick: (id: Long) -> Unit,
    onAlbumClick: (id: Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.playerManager.nowPlaying.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerManager.playWhenReady.collectAsStateWithLifecycle()

    SecondaryScreenScaffold(title = "最近播放", onBack = onBack) {
        FilterChipsRow(
            items = RecentTab.entries.map { it.label },
            selectedIndex = selectedTab.ordinal,
            onSelected = { index -> viewModel.selectTab(RecentTab.entries[index]) }
        )

        when (val state = uiState) {
            RecentPlayUiState.Loading -> {
                Column(modifier = Modifier.padding(top = MelodiaSpacing.sm)) {
                    repeat(SKELETON_ROW_COUNT) { SearchResultRowSkeleton() }
                }
            }

            is RecentPlayUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(message = state.message, onRetry = { viewModel.load() })
                }
            }

            is RecentPlayUiState.Success -> {
                RecentPlayList(
                    state = state,
                    tab = selectedTab,
                    currentTrackId = nowPlaying?.songId?.toString(),
                    isPlaying = isPlaying,
                    onSongClick = { viewModel.playSong(it) },
                    onPlaylistClick = onPlaylistClick,
                    onAlbumClick = onAlbumClick
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentPlayList(
    state: RecentPlayUiState.Success,
    tab: RecentTab,
    currentTrackId: String?,
    isPlaying: Boolean,
    onSongClick: (RecentSong) -> Unit,
    onPlaylistClick: (id: Long) -> Unit,
    onAlbumClick: (id: Long) -> Unit
) {
    val isEmpty = when (tab) {
        RecentTab.SONG -> state.songs.isEmpty()
        RecentTab.PLAYLIST -> state.playlists.isEmpty()
        RecentTab.ALBUM -> state.albums.isEmpty()
    }

    if (isEmpty) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.History,
                title = "还没有${tab.label}播放记录"
            )
        }
        return
    }

    // 按天分区块，切 Tab 才需重算；服务端已按时间降序返回，分组不改变顺序
    val songGroups = remember(state.songs) { state.songs.groupByPlayDay { it.playTime } }
    val playlistGroups = remember(state.playlists) { state.playlists.groupByPlayDay { it.playTime } }
    val albumGroups = remember(state.albums) { state.albums.groupByPlayDay { it.playTime } }

    LazyColumn(
        contentPadding = PaddingValues(
            top = MelodiaSpacing.sm,
            bottom = LocalBottomOverlayInset.current + 16.dp
        )
    ) {
        when (tab) {
            RecentTab.SONG -> {
                songGroups.forEach { group ->
                    stickyHeader(key = "header_${group.label}") { DayHeader(group.label) }
                    items(group.items, key = { "${group.label}_${it.track.id}" }) { song ->
                        val track = song.track
                        SongRow(
                            data = SongRowData(
                                id = track.id,
                                title = track.name,
                                artist = track.ar.joinToString(" / ") { it.name },
                                coverUrl = track.al.picUrl,
                                durationText = song.playedAtText.ifBlank { null }
                            ),
                            isActive = currentTrackId == track.id.toString(),
                            isPlaying = isPlaying,
                            onClick = { onSongClick(song) }
                        )
                    }
                }
            }

            RecentTab.PLAYLIST -> {
                playlistGroups.forEach { group ->
                    stickyHeader(key = "header_${group.label}") { DayHeader(group.label) }
                    items(group.items, key = { "${group.label}_${it.id}" }) { playlist ->
                        EntityRow(
                            data = EntityRowData(
                                id = playlist.id,
                                title = playlist.name,
                                subtitle = buildSubtitle(playlist.creatorName, playlist.playedAtText),
                                coverUrl = playlist.coverUrl,
                                coverShape = EntityCoverShape.Rounded
                            ),
                            onClick = { onPlaylistClick(playlist.id) }
                        )
                    }
                }
            }

            RecentTab.ALBUM -> {
                albumGroups.forEach { group ->
                    stickyHeader(key = "header_${group.label}") { DayHeader(group.label) }
                    items(group.items, key = { "${group.label}_${it.id}" }) { album ->
                        EntityRow(
                            data = EntityRowData(
                                id = album.id,
                                title = album.name,
                                subtitle = buildSubtitle(album.artistName, album.playedAtText),
                                coverUrl = album.coverUrl,
                                coverShape = EntityCoverShape.Rounded
                            ),
                            onClick = { onAlbumClick(album.id) }
                        )
                    }
                }
            }
        }
    }
}

// 分区头：吸顶显示，需不透明底色遮住下方滚过的内容
@Composable
private fun DayHeader(label: String) {
    Text(
        text = label,
        color = MelodiaColors.TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .background(MelodiaColors.PageFill)
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
    )
}

// 副标题拼接：任一段缺失时不留下孤零零的分隔符
private fun buildSubtitle(primary: String, playedAtText: String): String =
    listOf(primary, playedAtText).filter { it.isNotBlank() }.joinToString(" · ")
