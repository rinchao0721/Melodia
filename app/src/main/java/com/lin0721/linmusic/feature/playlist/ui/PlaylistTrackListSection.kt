package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

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
    onMoreClick: (Track) -> Unit,
    trackPlayCounts: Map<Long, Int> = emptyMap()
) {
    items(tracks, key = { it.id }) { track ->
        // SongRow 本身不接受外部 modifier，外层套 Box 挂 animateItem：
        // 切排序方式时 key 不变、位置变了，行会顺着新位置滑过去
        Box(modifier = Modifier.animateItem(trackFadeInSpec, trackPlacementSpec, trackFadeOutSpec)) {
            SongRow(
                data = SongRowData(
                    id = track.id,
                    title = track.name,
                    artist = track.ar.joinToString(" • ") { it.name },
                    coverUrl = track.al.picUrl,
                    isVip = track.fee == 1
                ),
                isActive = currentTrackId == track.id.toString(),
                isPlaying = isPlaying,
                onClick = { onPlaySong(track) },
                trailingSlot = {
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
                                onOpenCollectSheet(track.id)
                                onLikeClick(track.id)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "已收藏歌曲",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    MelodiaIconButton(
                        onClick = { onMoreClick(track) },
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
