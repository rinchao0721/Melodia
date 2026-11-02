package com.lin0721.linmusic.desktop.ui.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.ui.theme.paneBackdrop
import com.lin0721.linmusic.desktop.ui.artist.TabBar
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.track.TrackRow
import com.lin0721.linmusic.feature.recent.domain.PlayDayGroup
import com.lin0721.linmusic.feature.recent.domain.RecentAlbum
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist
import com.lin0721.linmusic.feature.recent.domain.RecentSong
import com.lin0721.linmusic.feature.recent.domain.groupByPlayDay
import com.lin0721.linmusic.feature.recent.ui.RecentPlayUiState
import com.lin0721.linmusic.feature.recent.ui.RecentPlayViewModel
import com.lin0721.linmusic.feature.recent.ui.RecentTab

private val ContentPadding = 24.dp
private val MediaCardWidth = 168.dp

@Composable
fun RecentPlayPage(
    viewModel: RecentPlayViewModel,
    controller: PlaybackController,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val tab by viewModel.selectedTab.collectAsState()

    Column(modifier.fillMaxSize()) {
        Text(
            "最近播放",
            color = DesktopColors.TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = ContentPadding, end = ContentPadding, top = 24.dp, bottom = 12.dp)
        )
        TabBar(
            tabs = RecentTab.entries,
            selected = tab,
            label = { it.label },
            onSelect = viewModel::selectTab,
            modifier = Modifier.padding(horizontal = ContentPadding, vertical = 4.dp)
        )
        when (val state = uiState) {
            RecentPlayUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DesktopColors.Accent)
            }
            is RecentPlayUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, color = DesktopColors.TextGray)
                    TextButton(onClick = viewModel::load) { Text("重试", color = DesktopColors.TextPrimary) }
                }
            }
            is RecentPlayUiState.Success -> RecentContent(state, tab, viewModel, controller)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentContent(
    state: RecentPlayUiState.Success,
    tab: RecentTab,
    viewModel: RecentPlayViewModel,
    controller: PlaybackController
) {
    val navigator = LocalDesktopNavigator.current
    val nowPlaying by controller.nowPlaying.collectAsState()
    val songGroups = remember(state.songs) { state.songs.groupByPlayDay { it.playTime } }
    val playlistGroups = remember(state.playlists) { state.playlists.groupByPlayDay { it.playTime } }
    val albumGroups = remember(state.albums) { state.albums.groupByPlayDay { it.playTime } }
    val isEmpty = when (tab) {
        RecentTab.SONG -> state.songs.isEmpty()
        RecentTab.PLAYLIST -> state.playlists.isEmpty()
        RecentTab.ALBUM -> state.albums.isEmpty()
    }
    if (isEmpty) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有${tab.label}播放记录", color = DesktopColors.TextGray, fontSize = 14.sp)
        }
        return
    }

    val listState = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = ((maxWidth - ContentPadding * 2) / (MediaCardWidth + 16.dp)).toInt().coerceAtLeast(2)
        HoverScrollbarBox(listState) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                when (tab) {
                    RecentTab.SONG -> songItems(songGroups, nowPlaying?.songId) { viewModel.playSong(it) }
                    RecentTab.PLAYLIST -> cardItems(
                        groups = playlistGroups,
                        columns = columns,
                        keyOf = RecentPlaylist::id,
                        cover = RecentPlaylist::coverUrl,
                        title = RecentPlaylist::name,
                        subtitle = { "${it.creatorName} · ${it.playedAtText}" },
                        onClick = { navigator.openPlaylist(it.id, it.name) }
                    )
                    RecentTab.ALBUM -> cardItems(
                        groups = albumGroups,
                        columns = columns,
                        keyOf = RecentAlbum::id,
                        cover = RecentAlbum::coverUrl,
                        title = RecentAlbum::name,
                        subtitle = { "${it.artistName} · ${it.playedAtText}" },
                        onClick = { navigator.openAlbum(it.id, it.name) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.songItems(
    groups: List<PlayDayGroup<RecentSong>>,
    currentSongId: Long?,
    onPlay: (RecentSong) -> Unit
) {
    var runningIndex = 0
    groups.forEach { group ->
        val startIndex = runningIndex
        runningIndex += group.items.size
        stickyHeader(key = "songs_header_${group.label}_$startIndex") { DayHeader(group.label) }
        items(group.items.size, key = { "song_${group.items[it].track.id}_${group.items[it].playTime}_${startIndex + it}" }) { offset ->
            val song = group.items[offset]
            TrackRow(
                index = startIndex + offset,
                track = song.track,
                isCurrent = currentSongId == song.track.id,
                onPlay = { onPlay(song) },
                modifier = Modifier.padding(horizontal = 16.dp),
                addedAtText = song.playedAtText
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun <T> LazyListScope.cardItems(
    groups: List<PlayDayGroup<T>>,
    columns: Int,
    keyOf: (T) -> Long,
    cover: (T) -> String,
    title: (T) -> String,
    subtitle: (T) -> String,
    onClick: (T) -> Unit
) {
    groups.forEachIndexed { groupIndex, group ->
        stickyHeader(key = "cards_header_${group.label}_$groupIndex") { DayHeader(group.label) }
        val rows = group.items.chunked(columns)
        items(rows.size, key = { "cards_${groupIndex}_row_${keyOf(rows[it].first())}_$it" }) { rowIndex ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ContentPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rows[rowIndex].forEach { item ->
                    MediaCard(cover(item), title(item), subtitle(item)) { onClick(item) }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    Text(
        label,
        color = DesktopColors.TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().paneBackdrop(DesktopColors.Pane)
            .padding(horizontal = ContentPadding, vertical = 10.dp)
    )
}

@Composable
private fun MediaCard(coverUrl: String, title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.width(MediaCardWidth).clip(RoundedCornerShape(6.dp)).pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick).padding(8.dp)
    ) {
        Cover(coverUrl, MediaCardWidth - 16.dp, shape = RoundedCornerShape(4.dp))
        Text(
            title,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(subtitle, color = DesktopColors.TextGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
