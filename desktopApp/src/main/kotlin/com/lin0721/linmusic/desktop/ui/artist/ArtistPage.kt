package com.lin0721.linmusic.desktop.ui.artist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.format.formatCompactCount
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.ui.theme.paneBackdrop
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.uiSpring
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.palette.FallbackCoverPalette
import com.lin0721.linmusic.desktop.ui.palette.extractCoverPaletteFromUrl
import com.lin0721.linmusic.desktop.ui.palette.themedCoverBackground
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.library.CreatePlaylistDialog
import com.lin0721.linmusic.desktop.ui.library.EMPTY_NAME_MESSAGE
import com.lin0721.linmusic.desktop.ui.library.LibrarySearchInput
import com.lin0721.linmusic.feature.playlist.presentation.TrackSelection
import com.lin0721.linmusic.desktop.ui.track.TrackActions
import com.lin0721.linmusic.desktop.ui.track.TrackRowSelection
import com.lin0721.linmusic.desktop.ui.track.TrackSelectionBar
import com.lin0721.linmusic.desktop.ui.track.TrackRow
import com.lin0721.linmusic.desktop.ui.track.rememberTrackActions
import com.lin0721.linmusic.feature.artist.ui.ArtistUiState
import com.lin0721.linmusic.feature.artist.ui.ArtistViewModel
import java.time.Instant
import java.time.ZoneId

// 距离列表底部还剩几项时加载下一页
private const val LOAD_MORE_THRESHOLD = 6
private const val HERO_DARKEN_FRACTION = 0.35f
private const val STICKY_BAR_ANIMATION_MS = 200

private val BackdropHeight = 300.dp
private val AlbumCardWidth = 168.dp
private val ContentPadding = 24.dp
private val SongSearchWidth = 240.dp
private val SongSearchHeight = 36.dp
private val ActionBarVerticalPadding = 16.dp
private val PlayButtonSize = 56.dp
private val StickyBarHeight = 64.dp
private val StickyPlayButtonSize = 48.dp

private enum class ArtistTab(val label: String) { SONGS("歌曲"), ALBUMS("专辑"), ABOUT("简介") }

@Composable
fun ArtistPage(
    artistId: Long,
    viewModel: ArtistViewModel,
    controller: PlaybackController,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(artistId) {
        viewModel.loadArtistDataIfNeeded(artistId)
    }
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        ArtistUiState.Loading -> Loading(modifier)
        is ArtistUiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = DesktopColors.TextGray)
                TextButton(onClick = { viewModel.loadArtistData(artistId) }) {
                    Text("重试", color = DesktopColors.TextPrimary)
                }
            }
        }
        is ArtistUiState.Success -> {
            // 单例 ViewModel 在切换歌手的瞬间仍持有上一位歌手，避免闪现旧内容
            if (state.artist.id != artistId) {
                Loading(modifier)
                return
            }
            ArtistContent(state, viewModel, controller, modifier)
        }
    }
}

@Composable
private fun Loading(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DesktopColors.Accent)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistContent(
    state: ArtistUiState.Success,
    viewModel: ArtistViewModel,
    controller: PlaybackController,
    modifier: Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val nowPlaying by controller.nowPlaying.collectAsState()
    val blockedIds by viewModel.blockedArtistIds.collectAsState()
    val likedSongIds by viewModel.likedSongIds.collectAsState()
    val collectState by viewModel.collectState.collectAsState()
    val songSearch by viewModel.songSearch.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val importState by viewModel.importState.collectAsState()
    var showBatchCreate by remember(state.artist.id) { mutableStateOf(false) }
    var tab by rememberSaveable(state.artist.id) { mutableStateOf(ArtistTab.SONGS) }
    val songsShown = if (songSearch.active) songSearch.results else state.topSongs

    val onToggleFollow = {
        if (navigator.isLoggedIn) viewModel.toggleFollow(state.artist.id) else navigator.showMessage("请先登录账号")
    }
    val onPlayHot = {
        state.topSongs.firstOrNull()?.let { viewModel.playSongInList(it, state.topSongs) } ?: Unit
    }

    val actions = rememberTrackActions(
        likedSongIds = likedSongIds,
        collectState = collectState,
        onToggleLike = viewModel::toggleLikeSong,
        onPlayNext = viewModel::addTrackToPlayNext,
        onPrepareCollect = viewModel::prepareCollectDialog,
        onSaveCollect = viewModel::savePlaylistCollection,
        onCreateAndAdd = viewModel::createPlaylistAndAddSong
    )

    val backdropUrl = remember(state.artist) { state.artist.cover.ifBlank { state.artist.avatar } }
    var heroBase by remember(state.artist.id) { mutableStateOf(FallbackCoverPalette.base) }
    LaunchedEffect(backdropUrl) {
        if (backdropUrl.isNotBlank()) heroBase = extractCoverPaletteFromUrl(backdropUrl).base
    }
    val heroColor by animateColorAsState(themedCoverBackground(heroBase, HERO_DARKEN_FRACTION), uiSpring(), label = "artistHero")

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val barHeightPx = with(density) { StickyBarHeight.roundToPx() }
    val playButtonBottomPx = with(density) { (ActionBarVerticalPadding + PlayButtonSize).roundToPx() }
    // 恢复页面现场时列表直接停在中间，大标题不会再测一次，高度要随现场保存
    var backdropHeightPx by rememberSaveable(state.artist.id) { mutableIntStateOf(0) }
    var headerHeightPx by rememberSaveable(state.artist.id) { mutableIntStateOf(0) }

    val showTitleBar by remember(barHeightPx) {
        derivedStateOf { backdropHeightPx > 0 && listState.scrolledPast(backdropHeightPx - barHeightPx) }
    }
    val showPlayButton by remember(barHeightPx, playButtonBottomPx) {
        derivedStateOf { backdropHeightPx > 0 && listState.scrolledPast(backdropHeightPx + playButtonBottomPx - barHeightPx) }
    }

    val shouldLoadMore by remember(state, tab) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore, tab) {
        if (!shouldLoadMore) return@LaunchedEffect
        when (tab) {
            ArtistTab.ALBUMS -> viewModel.loadMoreAlbums()
            ArtistTab.SONGS -> if (songSearch.active) viewModel.loadMoreSongSearch()
            else -> Unit
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = ((maxWidth - ContentPadding * 2) / (AlbumCardWidth + 16.dp)).toInt().coerceAtLeast(2)
        HoverScrollbarBox(listState) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "header") {
                    ArtistHeader(
                        state = state,
                        isBlocked = state.artist.id in blockedIds,
                        onPlayHot = onPlayHot,
                        onToggleFollow = onToggleFollow,
                        onToggleBlock = { viewModel.toggleBlockArtist(state.artist.id) },
                        onHeaderHeightChanged = { headerHeightPx = it },
                        onBackdropHeightChanged = { backdropHeightPx = it }
                    )
                }
                stickyHeader(key = "tabs") {
                    Box(
                        Modifier.fillMaxWidth()
                            .graphicsLayer {
                                if (headerHeightPx > 0) {
                                    val naturalY = if (listState.firstVisibleItemIndex > 0) 0 else headerHeightPx - listState.firstVisibleItemScrollOffset
                                    translationY = (barHeightPx - naturalY).coerceIn(0, barHeightPx).toFloat()
                                }
                            }
                            .paneBackdrop(DesktopColors.Pane)
                            .padding(horizontal = ContentPadding, vertical = 8.dp)
                    ) {
                        TabBar(ArtistTab.entries, tab, { it.label }, { tab = it })
                    }
                }
                when (tab) {
                    ArtistTab.SONGS -> {
                        item(key = "songs_toolbar") {
                            if (selection.active) {
                                val shownIds = songsShown.map { it.id }
                                TrackSelectionBar(
                                    count = selection.count,
                                    allSelected = shownIds.isNotEmpty() && selection.selectedIds.containsAll(shownIds),
                                    importState = importState,
                                    canRemove = false,
                                    onToggleAll = { viewModel.toggleSelectAll(shownIds) },
                                    onPlayNext = viewModel::batchPlayNext,
                                    onPrepareImport = viewModel::prepareImportTargets,
                                    onImportTo = { target -> viewModel.batchAddToPlaylist(target.id) },
                                    onCreateImport = { showBatchCreate = true },
                                    onLike = viewModel::batchLike,
                                    onDownload = { viewModel.batchDownload(navigator.downloadLevel) },
                                    onRemove = {},
                                    onExit = viewModel::exitSelection,
                                    modifier = Modifier.padding(horizontal = ContentPadding - 16.dp)
                                )
                            } else {
                                SongsToolbar(
                                    query = songSearch.query,
                                    onQueryChange = viewModel::updateSongSearchQuery,
                                    onEnterSelection = { viewModel.enterSelection() }
                                )
                            }
                        }
                        when {
                            songSearch.active && songSearch.isLoading -> loadingItem("songs_search_loading")
                            songSearch.active && songSearch.errorMessage != null ->
                                emptyItem("songs_search_error", songSearch.errorMessage.orEmpty())
                            songsShown.isEmpty() -> emptyItem("songs_empty", if (songSearch.active) "没有找到相关歌曲" else "暂无歌曲")
                            else -> trackItems(songsShown, nowPlaying?.songId, actions, selection, viewModel::toggleSelected) {
                                viewModel.playSongInList(it, songsShown)
                            }
                        }
                        if (songSearch.active && songSearch.loadingMore) loadingItem("songs_search_more")
                    }
                    ArtistTab.ALBUMS -> {
                        val rows = state.albums.chunked(columns)
                        items(rows.size, key = { "album_row_$it" }) { rowIndex ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = ContentPadding, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                rows[rowIndex].forEach { album ->
                                    AlbumCard(album) { navigator.openAlbum(album.id, album.name) }
                                }
                            }
                        }
                        if (state.albumsLoadingMore) loadingItem("albums_loading")
                        if (state.albums.isEmpty()) emptyItem("albums_empty", "暂无专辑")
                    }
                    ArtistTab.ABOUT -> {
                        item(key = "about") {
                            AboutSection(state.artist.briefDesc, state.similarArtists) { navigator.openArtist(it.id, it.name) }
                        }
                    }
                }
            }
            if (showBatchCreate) {
                CreatePlaylistDialog(
                    onDismiss = { showBatchCreate = false },
                    onCreate = { name, isPrivate ->
                        showBatchCreate = false
                        viewModel.batchCreatePlaylistAndAdd(name, isPrivate)
                    },
                    onEmptyName = { navigator.showMessage(EMPTY_NAME_MESSAGE) }
                )
            }
            Box(Modifier.fillMaxWidth().clipToBounds()) {
                AnimatedVisibility(
                    visible = showTitleBar,
                    enter = fadeIn(uiTween(STICKY_BAR_ANIMATION_MS)) + slideInVertically(uiTween(STICKY_BAR_ANIMATION_MS)) { -it / 2 },
                    exit = fadeOut(uiTween(STICKY_BAR_ANIMATION_MS)) + slideOutVertically(uiTween(STICKY_BAR_ANIMATION_MS)) { -it / 2 }
                ) {
                    ArtistStickyBar(
                        name = state.artist.name,
                        color = heroColor,
                        showPlay = showPlayButton,
                        isFollowed = state.isFollowed,
                        listState = listState,
                        onPlay = onPlayHot,
                        onToggleFollow = onToggleFollow
                    )
                }
            }
        }
    }
}

private fun LazyListScope.trackItems(
    tracks: List<Track>,
    currentSongId: Long?,
    actions: TrackActions,
    selection: TrackSelection,
    onToggleSelected: (Long) -> Unit,
    onPlay: (Track) -> Unit
) {
    itemsIndexed(tracks, key = { index, track -> "track_${track.id}_$index" }) { index, track ->
        TrackRow(
            index = index,
            track = track,
            isCurrent = currentSongId == track.id,
            onPlay = { onPlay(track) },
            actions = actions,
            modifier = Modifier.padding(horizontal = 16.dp),
            selection = if (selection.active) {
                TrackRowSelection(
                    checked = selection.isSelected(track.id),
                    onToggle = { onToggleSelected(track.id) }
                )
            } else {
                null
            }
        )
    }
}

// 歌曲 Tab 顶部：歌手歌曲搜索框与多选入口
@Composable
private fun SongsToolbar(query: String, onQueryChange: (String) -> Unit, onEnterSelection: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ContentPadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(SongSearchWidth).height(SongSearchHeight)
                .clip(RoundedCornerShape(SongSearchHeight / 2)).background(DesktopColors.Surface)
        ) {
            LibrarySearchInput(
                query = query,
                startPadding = 12.dp,
                showClear = query.isNotEmpty(),
                onQueryChange = onQueryChange,
                onClear = { onQueryChange("") },
                placeholder = "搜索歌手的歌曲"
            )
        }
        Spacer(Modifier.weight(1f))
        DesktopTooltip("多选") {
            IconButton(onClick = onEnterSelection) {
                Icon(Icons.Rounded.Checklist, "多选", tint = DesktopColors.TextGray, modifier = Modifier.size(24.dp))
            }
        }
    }
}

private fun LazyListScope.loadingItem(key: String) {
    item(key = key) {
        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DesktopColors.Accent)
        }
    }
}

private fun LazyListScope.emptyItem(key: String, text: String) {
    item(key = key) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(text, color = DesktopColors.TextGray, fontSize = 14.sp)
        }
    }
}

private fun LazyListState.scrolledPast(thresholdPx: Int): Boolean =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset >= thresholdPx

@Composable
private fun ArtistStickyBar(
    name: String,
    color: Color,
    showPlay: Boolean,
    isFollowed: Boolean,
    listState: LazyListState,
    onPlay: () -> Unit,
    onToggleFollow: () -> Unit
) {
    Row(
        // 横栏盖住列表顶部，滚轮事件转给列表
        Modifier.fillMaxWidth().height(StickyBarHeight).background(color)
            .scrollable(listState, Orientation.Vertical)
            .padding(horizontal = ContentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = showPlay,
            enter = fadeIn(uiTween(STICKY_BAR_ANIMATION_MS)) + expandHorizontally(uiTween(STICKY_BAR_ANIMATION_MS)),
            exit = fadeOut(uiTween(STICKY_BAR_ANIMATION_MS)) + shrinkHorizontally(uiTween(STICKY_BAR_ANIMATION_MS))
        ) {
            PlayCircleButton(StickyPlayButtonSize, 28.dp, onPlay, Modifier.padding(end = 12.dp))
        }
        Text(
            name,
            color = DesktopColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(16.dp))
        HeaderButton(if (isFollowed) "已关注" else "关注", onToggleFollow)
    }
}

@Composable
private fun PlayCircleButton(size: Dp, iconSize: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DesktopTooltip("播放热门歌曲") {
        Box(
            modifier.size(size).clip(CircleShape).background(DesktopColors.Accent)
                .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PlayArrow, "播放热门歌曲", tint = DesktopColors.TextPrimary, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
private fun ArtistHeader(
    state: ArtistUiState.Success,
    isBlocked: Boolean,
    onPlayHot: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleBlock: () -> Unit,
    onHeaderHeightChanged: (Int) -> Unit,
    onBackdropHeightChanged: (Int) -> Unit
) {
    val artist = state.artist
    Column(Modifier.fillMaxWidth().onSizeChanged { onHeaderHeightChanged(it.height) }) {
        Box(
            Modifier.fillMaxWidth().height(BackdropHeight)
                .onSizeChanged { onBackdropHeightChanged(it.height) }
        ) {
            val backdrop = artist.cover.ifBlank { artist.avatar }
            if (backdrop.isNotBlank()) {
                AsyncImage(
                    model = if (backdrop.startsWith("http") && !backdrop.contains("?")) "$backdrop?param=1200y600" else backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, DesktopColors.PageFill.let { it.copy(alpha = it.alpha * 0.6f) }, DesktopColors.PageFill))
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(ContentPadding)) {
                Text(
                    artist.name,
                    color = DesktopColors.TextPrimary,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = listOfNotNull(
                    artist.trans?.takeIf { it.isNotBlank() },
                    artist.alias?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.joinToString(" / "),
                    state.fansCount.takeIf { it > 0 }?.let { "${formatCompactCount(it)} 粉丝" }
                ).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = DesktopColors.TextGray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(
            Modifier.padding(horizontal = ContentPadding, vertical = ActionBarVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(PlayButtonSize).clip(CircleShape).background(DesktopColors.Accent)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(enabled = state.topSongs.isNotEmpty(), onClick = onPlayHot),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, "播放热门歌曲", tint = DesktopColors.TextPrimary, modifier = Modifier.size(32.dp))
            }
            HeaderButton(if (state.isFollowed) "已关注" else "关注", onToggleFollow)
            HeaderButton(if (isBlocked) "取消屏蔽" else "屏蔽", onToggleBlock)
        }
    }
}

@Composable
private fun HeaderButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DesktopColors.TextPrimary)
    ) {
        Text(text, fontSize = 14.sp)
    }
}

@Composable
internal fun <T> TabBar(
    tabs: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEach { tab ->
            val active = tab == selected
            Box(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (active) DesktopColors.TextPrimary else DesktopColors.Surface)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 4.dp else 6.dp)
            ) {
                Text(
                    label(tab),
                    color = if (active) DesktopColors.Pane else DesktopColors.TextPrimary,
                    fontSize = if (small) 13.sp else 14.sp
                )
            }
        }
    }
}

@Composable
private fun AlbumCard(album: ArtistAlbum, onClick: () -> Unit) {
    Column(
        Modifier.width(AlbumCardWidth).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(8.dp)
    ) {
        Cover(album.picUrl, AlbumCardWidth - 16.dp, shape = RoundedCornerShape(4.dp))
        Text(
            album.name,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        val year = album.publishTime.takeIf { it > 0 }?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year.toString()
        }
        Text(
            listOfNotNull(year, album.size.takeIf { it > 0 }?.let { "$it 首" }).joinToString(" · "),
            color = DesktopColors.TextGray,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun AboutSection(briefDesc: String, similar: List<ArtistInfo>, onArtistClick: (ArtistInfo) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = ContentPadding, vertical = 8.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DesktopColors.CardSurface).padding(20.dp)
        ) {
            Text("简介", color = DesktopColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                briefDesc.trim().ifBlank { "暂无艺人简介信息" },
                color = DesktopColors.TextGray,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (similar.isNotEmpty()) {
            Text(
                "相似歌手",
                color = DesktopColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(similar, key = { it.id }) { artist ->
                    SimilarArtistCard(artist, 128.dp) { onArtistClick(artist) }
                }
            }
        }
    }
}

@Composable
private fun SimilarArtistCard(artist: ArtistInfo, size: Dp, onClick: () -> Unit) {
    Column(
        Modifier.width(size + 16.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Cover(artist.avatarUrl, size, shape = CircleShape)
        Text(
            artist.name,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
