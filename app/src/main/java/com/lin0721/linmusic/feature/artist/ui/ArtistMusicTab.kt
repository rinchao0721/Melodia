package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.components.TrackSelectionUi
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 音乐 Tab: 默认呈现热门50首歌曲，搜索框有内容时改为呈现该歌手的搜索结果
fun LazyListScope.artistMusicTab(
    hotSongs: List<Track>,
    likedSongIds: Set<Long>,
    currentTrackId: String?,
    isPlaying: Boolean,
    isLoggedIn: Boolean,
    songSearch: ArtistSongSearchState,
    selectionUi: TrackSelectionUi,
    onPlaySong: (Track, List<Track>) -> Unit,
    onLikeClick: (Long) -> Unit,
    onOpenCollectSheet: (Long) -> Unit,
    onOpenMoreOptions: (Track) -> Unit,
    onRequireLogin: () -> Unit
) {

    val searching = songSearch.active
    val songs = if (searching) songSearch.results else hotSongs

    when {
        searching && songSearch.isLoading -> item(key = "search_loading") {
            CenteredStatus { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        }
        searching && songSearch.errorMessage != null -> item(key = "search_error") {
            CenteredStatus { StatusText(songSearch.errorMessage.orEmpty()) }
        }
        songs.isEmpty() -> item(key = "empty_songs") {
            CenteredStatus { StatusText(if (searching) "没有找到相关歌曲" else "暂无歌曲") }
        }
        else -> {
            val keyPrefix = if (searching) "search" else "hot"
            itemsIndexed(songs, key = { _, track -> "${keyPrefix}_${track.id}" }) { index, track ->
                val selecting = selectionUi.active
                val isActive = track.id.toString() == currentTrackId
                SongRow(
                    data = SongRowData(
                        id = track.id,
                        title = track.name,
                        artist = track.ar.joinToString(" • ") { it.name },
                        coverUrl = track.al.picUrl,
                        isVip = track.fee == 1
                    ),
                    isActive = isActive,
                    isPlaying = isPlaying,
                    index = index + 1,
                    copyable = !selecting,
                    selectionChecked = if (selecting) track.id in selectionUi.selectedIds else null,
                    onClick = { if (selecting) selectionUi.onToggle(track.id) else onPlaySong(track, songs) },
                    trailingSlot = {
                        if (!selecting) {
                            val isLiked = track.id in likedSongIds
                            MelodiaIconButton(
                                onClick = {
                                    if (!isLoggedIn) {
                                        onRequireLogin()
                                    } else {
                                        onOpenCollectSheet(track.id)
                                        onLikeClick(track.id)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "收藏到歌单",
                                    tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            MelodiaIconButton(
                                onClick = { onOpenMoreOptions(track) },
                                onLongClick = { selectionUi.onEnter(track.id) },
                                modifier = Modifier.size(32.dp).padding(end = MelodiaSpacing.xs)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "更多操作",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                )
            }
            if (searching && songSearch.loadingMore) {
                item(key = "search_loading_more") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredStatus(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(MelodiaColors.PageFill),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
}
