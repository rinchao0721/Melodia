package com.lin0721.linmusic.feature.newworks.ui

import com.lin0721.linmusic.core.ui.components.PlayPauseIcon
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectSheet
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.ui.components.shimmerBackground
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.skin.CoverGroup
import com.lin0721.linmusic.core.ui.skin.coverBase
import com.lin0721.linmusic.core.ui.theme.FallbackBase
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.extractBaseColorFromUrl
import com.lin0721.linmusic.core.ui.theme.immersiveMidFill
import com.lin0721.linmusic.core.ui.theme.rememberMelodiaGridColumns
import com.lin0721.linmusic.core.ui.theme.uiSpring
import com.lin0721.linmusic.feature.newworks.domain.NewWorksRelease
import com.lin0721.linmusic.feature.newworks.domain.formatRelativeTime
import com.lin0721.linmusic.feature.newworks.domain.trackSummary
import com.lin0721.linmusic.feature.playlist.ui.PlaylistImportState
import com.lin0721.linmusic.feature.playlist.ui.PlaylistImportTargetSheet

private val CardShape = RoundedCornerShape(12.dp)
private val CoverSize = 88.dp
private val PlayButtonSize = 48.dp

// 取色后向黑色压暗的比例，保证白字在任意封面主色上都可读
private const val CARD_COLOR_DARKEN = 0.3f

class NewWorksFeedActions(
    val onAlbumClick: (Long) -> Unit,
    val onTogglePlay: (NewWorksRelease) -> Unit,
    val onToggleLibrary: (NewWorksRelease) -> Unit,
    val onAddToPlayNext: (NewWorksRelease) -> Unit,
    val onPrepareCollect: (songId: Long, onReady: () -> Unit) -> Unit,
    val onSaveCollection: (songId: Long, items: List<PlaylistCollectItem>) -> Unit,
    val onSaveNewCollection: (name: String, songId: Long) -> Unit,
    val onPrepareImportTargets: (onReady: () -> Unit) -> Unit,
    val onAddToPlaylist: (NewWorksRelease, playlistId: Long) -> Unit,
    val onCreatePlaylistAndAdd: (NewWorksRelease, name: String) -> Unit,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit
)

// 首页音乐 tab「最新」二级药丸的内容区：标题 + 整宽新发布卡片列表。
// 不带自己的顶栏/返回箭头——嵌在 HomeSharedHeader 下方，随药丸原地切换而非跳转新页面。
@Composable
fun NewWorksFeedContent(
    uiState: NewWorksUiState,
    collectState: PlaylistCollectState,
    importState: PlaylistImportState,
    status: NewWorksReleaseStatus,
    actions: NewWorksFeedActions
) {
    when (uiState) {
        NewWorksUiState.Loading -> NewWorksFeedSkeleton()

        is NewWorksUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorState(message = uiState.message, onRetry = actions.onRetry)
        }

        is NewWorksUiState.Success -> {
            if (uiState.releases.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Rounded.NewReleases,
                        title = "还没有关注歌手的新作",
                        subtitle = "多关注几位歌手，新歌会出现在这里"
                    )
                }
                return
            }
            NewWorksFeedList(uiState, collectState, importState, status, actions)
        }
    }
}

@Composable
private fun NewWorksFeedList(
    state: NewWorksUiState.Success,
    collectState: PlaylistCollectState,
    importState: PlaylistImportState,
    status: NewWorksReleaseStatus,
    actions: NewWorksFeedActions
) {
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember(state.releases.size, state.hasMore, state.isLoadingMore) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= gridState.layoutInfo.totalItemsCount - 2 && state.hasMore && !state.isLoadingMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) actions.onLoadMore()
    }

    var collectSongId by remember { mutableStateOf<Long?>(null) }
    var optionsRelease by remember { mutableStateOf<NewWorksRelease?>(null) }
    var importRelease by remember { mutableStateOf<NewWorksRelease?>(null) }

    val columns = rememberMelodiaGridColumns(compact = 1, expandedPortrait = 2, expandedLandscape = 3)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        contentPadding = PaddingValues(
            start = MelodiaSpacing.md,
            end = MelodiaSpacing.md,
            top = MelodiaSpacing.sm,
            bottom = LocalBottomOverlayInset.current + 16.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "最新发布",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(vertical = MelodiaSpacing.sm)
            )
        }

        items(state.releases, key = { "${it.isAlbum}_${it.id}" }) { release ->
            ReleaseFeedCard(
                release = release,
                playing = status.isReleasePlaying(release),
                onClick = { if (release.isAlbum) actions.onAlbumClick(release.id) else actions.onTogglePlay(release) },
                onPlay = { actions.onTogglePlay(release) },
                onAdd = {
                    if (release.isAlbum) {
                        actions.onPrepareImportTargets { importRelease = release }
                    } else {
                        actions.onPrepareCollect(release.id) { collectSongId = release.id }
                    }
                },
                onMore = { optionsRelease = release }
            )
        }

        if (state.isLoadingMore) {
            item(key = "loading_more", span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = MelodiaSpacing.md), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }

    collectSongId?.let { songId ->
        PlaylistCollectSheet(
            songId = songId,
            collectState = collectState,
            onDismiss = { collectSongId = null },
            onSaveCollection = { id, items ->
                actions.onSaveCollection(id, items)
                collectSongId = null
            },
            onSaveNewCollection = { name, id ->
                actions.onSaveNewCollection(name, id)
                collectSongId = null
            }
        )
    }

    optionsRelease?.let { release ->
        NewWorksReleaseOptionsSheet(
            release = release,
            inLibrary = status.isInLibrary(release),
            onDismiss = { optionsRelease = null },
            onAddToLibrary = { actions.onToggleLibrary(release) },
            onAddToPlayNext = { actions.onAddToPlayNext(release) },
            onAddToPlaylist = {
                actions.onPrepareImportTargets { importRelease = release }
            }
        )
    }

    importRelease?.let { release ->
        PlaylistImportTargetSheet(
            importState = importState,
            onDismiss = { importRelease = null },
            onSelectTarget = { playlistId ->
                actions.onAddToPlaylist(release, playlistId)
                importRelease = null
            },
            onCreateAndImport = { name ->
                actions.onCreatePlaylistAndAdd(release, name)
                importRelease = null
            }
        )
    }
}

@Composable
private fun ReleaseFeedCard(
    release: NewWorksRelease,
    playing: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onAdd: () -> Unit,
    onMore: () -> Unit
) {
    val context = LocalContext.current
    val baseColor by produceState(initialValue = FallbackBase, release.coverUrl) {
        value = extractBaseColorFromUrl(context, release.coverUrl)
    }
    val isDark = MelodiaColors.IsDark
    val cardColor by animateColorAsState(
        coverBase(CoverGroup.CARDS, baseColor)?.let { if (isDark) lerp(it, Color.Black, CARD_COLOR_DARKEN) else immersiveMidFill(it) }
            ?: MelodiaColors.ShellFill,
        animationSpec = uiSpring(),
        label = "releaseCardColor"
    )
    val timeText = remember(release.publishTime) { formatRelativeTime(release.publishTime, System.currentTimeMillis()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .pressable(MelodiaPress.Card, CardShape, onClick = onClick)
            .background(cardColor)
            .padding(MelodiaSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            SubcomposeAsyncImage(
                model = "${release.coverUrl}?param=300y300",
                contentDescription = release.title,
                contentScale = ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() },
                modifier = Modifier.size(CoverSize).clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(MelodiaSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = release.artistName,
                    color = MelodiaColors.OnCoverCard,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(MelodiaSpacing.xs))
                Text(
                    text = listOf(release.title, timeText).filter { it.isNotBlank() }.joinToString(" • "),
                    color = MelodiaColors.OnCoverCard.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MelodiaIconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = MelodiaColors.OnCoverCard)
            }
        }

        Spacer(Modifier.height(MelodiaSpacing.md))
        Text(
            text = release.trackSummary(),
            color = MelodiaColors.OnCoverCard.copy(alpha = 0.85f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(MelodiaSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MelodiaIconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Rounded.AddCircleOutline,
                    contentDescription = "添加到歌单",
                    tint = MelodiaColors.OnCoverCard,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(MelodiaSpacing.xs))
            MelodiaIconButton(
                onClick = onPlay,
                style = MelodiaPress.Transport,
                containerColor = MelodiaColors.OnCoverCard,
                modifier = Modifier.size(PlayButtonSize).clip(CircleShape)
            ) {
                PlayPauseIcon(
                    isPlaying = playing,
                    tint = MelodiaColors.OnCoverCardInverse,
                    size = 30.dp,
                    contentDescription = if (playing) "暂停" else "播放"
                )
            }
        }
    }
}

@Composable
private fun NewWorksFeedSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(MelodiaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(188.dp)
                    .shimmerBackground(CardShape)
            )
        }
    }
}
