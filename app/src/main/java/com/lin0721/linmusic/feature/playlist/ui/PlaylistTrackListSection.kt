package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.components.TrackSelectionUi
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.uiSpec

// 切歌单排序方式时的行位移动画：与 Library 筛选胶囊的增删动效同一套规格
private val trackPlacementSpec = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)
private val trackFadeInSpec = spring<Float>(stiffness = Spring.StiffnessMedium)
private val trackFadeOutSpec = spring<Float>(stiffness = Spring.StiffnessMedium)

// ────────────────────────────────────────────────
// 歌曲列表：调用方需要按搜索关键字过滤好再传进来
// ────────────────────────────────────────────────
fun LazyListScope.playlistTrackItems(
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedSongIds: Set<Long> = emptySet(),
    isLoggedIn: Boolean = false,
    onPlaySong: (Track) -> Unit,
    onLikeClick: (Long) -> Unit = {},
    onOpenCollectSheet: (Long) -> Unit = {},
    onToggleLike: (Long, Boolean) -> Unit = { _, _ -> },
    onMoreClick: (Track) -> Unit,
    trackPlayCounts: Map<Long, Int> = emptyMap(),
    unplayableIds: Set<Long> = emptySet(),
    selectionUi: TrackSelectionUi = TrackSelectionUi(),
    // 混排进歌单的外部平台歌曲，歌名后显示来源角标
    externalPlatforms: Map<Long, MusicPlatform> = emptyMap(),
    showIndex: Boolean = false
) {
    val activeTrackId = currentTrackId?.toLongOrNull()
    val indexDigits = tracks.size.toString().length
    // 序号与歌手页保持一致：显示的是歌曲在当前列表里的位置（搜索过滤后同样生效）
    itemsIndexed(tracks, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
        val selecting = selectionUi.active
        val platform = externalPlatforms[track.id]
        val rowData = remember(track, platform) {
            SongRowData(
                id = track.id,
                title = track.name,
                artist = track.ar.joinToString(" • ") { it.name },
                coverUrl = track.al.picUrl,
                isVip = track.fee == 1,
                platformTag = platform?.badgeName,
                platformTagColorArgb = platform?.brandColorArgb
            )
        }
        // SongRow 本身不接受外部 modifier，外层套 Box 挂 animateItem：
        // 切排序方式时 key 不变、位置变了，行会顺着新位置滑过去
        Box(modifier = Modifier.animateItem(uiSpec(trackFadeInSpec), uiSpec(trackPlacementSpec), uiSpec(trackFadeOutSpec))) {
            SongRow(
                data = rowData,
                isActive = activeTrackId == track.id,
                isPlaying = isPlaying,
                index = if (showIndex) index + 1 else null,
                indexDigits = indexDigits,
                // 多选态下不可播放的歌曲也能勾选
                enabled = selecting || track.id !in unplayableIds,
                copyable = !selecting,
                selectionChecked = if (selecting) track.id in selectionUi.selectedIds else null,
                onClick = { if (selecting) selectionUi.onToggle(track.id) else onPlaySong(track) },
                trailingSlot = trailing@{
                    if (selecting) return@trailing
                    val playCount = trackPlayCounts[track.id]
                    if (playCount != null) {
                        Text(
                            text = "${playCount}次",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = MelodiaSpacing.xs)
                        )
                    } else if (isLoggedIn && track.id in likedSongIds) {
                        MelodiaIconButton(
                            onClick = {
                                onToggleLike(track.id, false)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = "取消喜欢",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    MelodiaIconButton(
                        onClick = { onMoreClick(track) },
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
            )
        }
    }
}
