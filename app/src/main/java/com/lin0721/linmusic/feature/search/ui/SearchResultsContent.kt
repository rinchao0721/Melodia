package com.lin0721.linmusic.feature.search.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.EntityCoverShape
import com.lin0721.linmusic.core.ui.components.EntityRow
import com.lin0721.linmusic.core.ui.components.EntityRowData
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.FilterChipsRow
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.SearchResultRowSkeleton
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.podcastSubtitle
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.flow.StateFlow

private const val TAB_ENTER_DURATION = 300
private const val TAB_EXIT_DURATION = 150

private fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
internal fun SearchResultsContent(
    searchPlatforms: List<MusicPlatform>,
    selectedPlatform: MusicPlatform,
    selectedType: SearchType,
    resultsByType: Map<SearchType, StateFlow<SearchResultsUiState>>,
    externalResults: Map<MusicPlatform, StateFlow<ExternalSearchUiState>>,
    listStates: Map<SearchType, LazyListState>,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedSongIds: Set<Long>,
    isLoggedIn: Boolean,
    onSelectPlatform: (MusicPlatform) -> Unit,
    onSelectType: (SearchType) -> Unit,
    onSongClick: (Track) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onRadioClick: (Long) -> Unit,
    onProgramClick: (PodcastProgram) -> Unit,
    onExternalSongClick: (ExternalTrack) -> Unit,
    onOpenExternalOptions: (ExternalTrack) -> Unit,
    onLoadMore: () -> Unit,
    onLoadMoreExternal: (MusicPlatform) -> Unit,
    onRetry: () -> Unit,
    onRetryExternal: (MusicPlatform) -> Unit,
    onLikeClick: (Long) -> Unit,
    onOpenMoreOptions: (Track) -> Unit
) {
    val motion = LocalUiMotion.current
    val types = SearchType.entries
    Column(modifier = Modifier.fillMaxSize()) {
        if (searchPlatforms.size > 1) {
            PlatformTabRow(
                platforms = searchPlatforms,
                selected = selectedPlatform,
                externalResults = externalResults,
                onSelect = onSelectPlatform,
                modifier = Modifier.padding(bottom = MelodiaSpacing.xs)
            )
        }
        AnimatedContent(
            targetState = selectedPlatform,
            transitionSpec = {
                fadeIn(motion.tween(TAB_ENTER_DURATION, easing = FastOutSlowInEasing))
                    .togetherWith(fadeOut(motion.tween(TAB_EXIT_DURATION)))
            },
            label = "search_platform_switch"
        ) { platform ->
            if (platform == MusicPlatform.NETEASE) {
                Column(modifier = Modifier.fillMaxSize()) {
                    FilterChipsRow(
                        items = remember { types.map { it.label } },
                        selectedIndex = types.indexOf(selectedType),
                        onSelected = { index -> types.getOrNull(index)?.let(onSelectType) },
                        modifier = Modifier.padding(bottom = MelodiaSpacing.xs)
                    )
                    AnimatedContent(
                        targetState = selectedType,
                        transitionSpec = {
                            fadeIn(motion.tween(TAB_ENTER_DURATION, easing = FastOutSlowInEasing))
                                .togetherWith(fadeOut(motion.tween(TAB_EXIT_DURATION)))
                        },
                        label = "search_type_tab_switch"
                    ) { type ->
                        val resultsState by resultsByType.getValue(type).collectAsStateWithLifecycle()
                        SearchResultsList(
                            state = resultsState,
                            type = type,
                            listState = listStates.getValue(type),
                            currentTrackId = currentTrackId,
                            isPlaying = isPlaying,
                            likedSongIds = likedSongIds,
                            isLoggedIn = isLoggedIn,
                            onSongClick = onSongClick,
                            onAlbumClick = onAlbumClick,
                            onArtistClick = onArtistClick,
                            onPlaylistClick = onPlaylistClick,
                            onRadioClick = onRadioClick,
                            onProgramClick = onProgramClick,
                            onLoadMore = onLoadMore,
                            onRetry = onRetry,
                            onLikeClick = onLikeClick,
                            onOpenMoreOptions = onOpenMoreOptions
                        )
                    }
                }
            } else {
                val resultsStateFlow = externalResults[platform]
                val resultsState = resultsStateFlow?.collectAsStateWithLifecycle()?.value ?: ExternalSearchUiState.Idle
                ExternalSearchResultsList(
                    state = resultsState,
                    platform = platform,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    onSongClick = onExternalSongClick,
                    onOpenOptions = onOpenExternalOptions,
                    onLoadMore = { onLoadMoreExternal(platform) },
                    onRetry = { onRetryExternal(platform) }
                )
            }
        }
    }
}

// 外部平台单曲结果列表
@Composable
private fun ExternalSearchResultsList(
    state: ExternalSearchUiState,
    platform: MusicPlatform,
    currentTrackId: String?,
    isPlaying: Boolean,
    onSongClick: (ExternalTrack) -> Unit,
    onOpenOptions: (ExternalTrack) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        ExternalSearchUiState.Idle, ExternalSearchUiState.Loading -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(6) { SearchResultRowSkeleton() }
            }
        }
        ExternalSearchUiState.Empty -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "在${platform.displayName}未找到相关歌曲"
                )
            }
        }
        is ExternalSearchUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(message = state.message, onRetry = onRetry)
            }
        }
        is ExternalSearchUiState.Success -> {
            val listState = remember(platform) { LazyListState() }
            val shouldLoadMore by remember(state.hasMore, state.isLoadingMore) {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisible >= state.tracks.size - 5 && state.hasMore && !state.isLoadingMore
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) onLoadMore()
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
            ) {
                item(key = "header") {
                    Text(
                        "在 ${platform.displayName} 找到 ${state.tracks.size} 首歌曲",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
                    )
                }

                items(state.tracks, key = { "${it.platform.name}_${it.id}" }) { track ->
                    val isActive = currentTrackId == track.queueSongId.toString()
                    SongRow(
                        data = SongRowData(
                            id = track.queueSongId,
                            title = track.name,
                            artist = if (track.albumName.isNotBlank()) "${track.artists} · ${track.albumName}" else track.artists,
                            coverUrl = track.coverUrl,
                            durationText = if (track.durationMs > 0) formatTrackDuration(track.durationMs) else null,
                            copyArtist = track.artists
                        ),
                        isActive = isActive,
                        isPlaying = isPlaying,
                        copyable = true,
                        onClick = { onSongClick(track) },
                        trailingSlot = {
                            MelodiaIconButton(
                                onClick = { onOpenOptions(track) },
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

                if (state.isLoadingMore) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(MelodiaSpacing.md),
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
    }
}

// 分类型搜索结果列表
@Composable
private fun SearchResultsList(
    state: SearchResultsUiState,
    type: SearchType,
    listState: LazyListState,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedSongIds: Set<Long>,
    isLoggedIn: Boolean,
    onSongClick: (Track) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onRadioClick: (Long) -> Unit,
    onProgramClick: (PodcastProgram) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onLikeClick: (Long) -> Unit,
    onOpenMoreOptions: (Track) -> Unit
) {
    when (state) {
        SearchResultsUiState.Idle, SearchResultsUiState.Loading -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(6) { SearchResultRowSkeleton() }
            }
        }
        SearchResultsUiState.Empty -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "没有找到相关${type.label}"
                )
            }
        }
        is SearchResultsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(message = state.message, onRetry = onRetry)
            }
        }
        is SearchResultsUiState.Success -> {
            val shouldLoadMore by remember(state.hasMore, state.isLoadingMore) {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisible >= state.items.size - 5 && state.hasMore && !state.isLoadingMore
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) onLoadMore()
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
            ) {
                item(key = "header") {
                    Text(
                        "找到 ${state.totalCount} 个${type.label}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
                    )
                }

                items(state.items, key = { it.stableKey }) { item ->
                    when (item) {
                        is SearchResultItem.SongItem -> {
                            val track = item.track
                            val isActive = currentTrackId == track.id.toString()
                            SongRow(
                                data = SongRowData(
                                    id = track.id,
                                    title = track.name,
                                    artist = track.ar.joinToString(" / ") { it.name },
                                    coverUrl = track.al.picUrl,
                                    isVip = track.fee == 1
                                ),
                                isActive = isActive,
                                isPlaying = isPlaying,
                                copyable = true,
                                onClick = { onSongClick(track) },
                                trailingSlot = {
                                    if (isLoggedIn && track.id in likedSongIds) {
                                        MelodiaIconButton(
                                            onClick = { onLikeClick(track.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "收藏到歌单",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    MelodiaIconButton(
                                        onClick = { onOpenMoreOptions(track) },
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
                        is SearchResultItem.AlbumItem -> EntityRow(
                            data = EntityRowData(
                                id = item.album.id,
                                title = item.album.name,
                                subtitle = item.album.artists.joinToString(" / ") { it.name },
                                coverUrl = item.album.picUrl,
                                coverShape = EntityCoverShape.Rounded
                            ),
                            onClick = { onAlbumClick(item.album.id) }
                        )
                        is SearchResultItem.ArtistItem -> EntityRow(
                            data = EntityRowData(
                                id = item.artist.id,
                                title = item.artist.name,
                                coverUrl = item.artist.picUrl,
                                coverShape = EntityCoverShape.Circle
                            ),
                            onClick = { onArtistClick(item.artist.id) }
                        )
                        is SearchResultItem.PlaylistItem -> EntityRow(
                            data = EntityRowData(
                                id = item.playlist.id,
                                title = item.playlist.name,
                                subtitle = listOfNotNull(
                                    item.playlist.creator?.nickname,
                                    if (item.playlist.trackCount > 0) "${item.playlist.trackCount}首" else null
                                ).joinToString(" · "),
                                coverUrl = item.playlist.coverImgUrl,
                                coverShape = EntityCoverShape.Rounded
                            ),
                            onClick = { onPlaylistClick(item.playlist.id) }
                        )
                        is SearchResultItem.RadioItem -> EntityRow(
                            data = EntityRowData(
                                id = item.radio.id,
                                title = item.radio.name,
                                subtitle = listOfNotNull(
                                    item.radio.djName.takeIf { it.isNotBlank() },
                                    item.radio.programCount.takeIf { it > 0 }?.let { "$it 期" }
                                ).joinToString(" · "),
                                coverUrl = item.radio.picUrl,
                                coverShape = EntityCoverShape.Rounded
                            ),
                            onClick = { onRadioClick(item.radio.id) }
                        )
                        is SearchResultItem.ProgramItem -> EntityRow(
                            data = EntityRowData(
                                id = item.program.id,
                                title = item.program.name,
                                subtitle = podcastSubtitle(item.program.radioName, item.program.djName),
                                coverUrl = item.program.coverUrl,
                                coverShape = EntityCoverShape.Rounded
                            ),
                            onClick = { onProgramClick(item.program) }
                        )
                    }
                }

                if (state.isLoadingMore) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(MelodiaSpacing.md),
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
    }
}
