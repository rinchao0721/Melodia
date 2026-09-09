package com.lin0721.linmusic.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.library.ui.LibraryGridItem
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import com.lin0721.linmusic.feature.profile.domain.ProfilePlaylistInfo

@Composable
fun ProfilePlaylistTab(
    playlists: List<ProfilePlaylistInfo>,
    isLoading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onPlaylistClick: (id: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playlists.isEmpty()) {
        if (isLoading) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "暂无歌单"
                )
            }
        }
        return
    }

    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember(hasMore, isLoading, playlists.size) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            playlists.isNotEmpty() && lastVisible >= playlists.size - 3 && hasMore && !isLoading
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        contentPadding = PaddingValues(
            start = MelodiaSpacing.md,
            end = MelodiaSpacing.md,
            top = MelodiaSpacing.sm,
            bottom = LocalBottomOverlayInset.current + 16.dp
        ),
        modifier = modifier.fillMaxSize()
    ) {
        items(playlists, key = { it.id }) { playlist ->
            val libraryItem = LibraryItem(
                id = playlist.id.toString(),
                title = playlist.name,
                subtitle = if (playlist.trackCount > 0) "${playlist.trackCount}首" else playlist.creatorName,
                coverUrl = playlist.coverImgUrl,
                type = LibraryItemType.PLAYLIST,
                isPinned = false,
                playCount = playlist.playCount,
                isLikedSongs = false
            )
            LibraryGridItem(
                item = libraryItem,
                onClick = { onPlaylistClick(playlist.id) }
            )
        }

        if (isLoading) {
            item(key = "loading_more", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MelodiaSpacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
