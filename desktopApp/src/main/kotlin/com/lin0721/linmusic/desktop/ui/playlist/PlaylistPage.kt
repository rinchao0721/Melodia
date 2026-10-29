package com.lin0721.linmusic.desktop.ui.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.model.isLikedSongsPlaylist
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.ui.theme.paneBackdrop
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.desktop.ui.comments.asCommentsHost
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.components.SubMenuItem
import com.lin0721.linmusic.desktop.ui.components.uiSpring
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.library.CreatePlaylistDialog
import com.lin0721.linmusic.desktop.ui.library.EMPTY_NAME_MESSAGE
import com.lin0721.linmusic.desktop.ui.library.LibraryPopupMenu
import com.lin0721.linmusic.desktop.ui.library.LibrarySearchInput
import com.lin0721.linmusic.desktop.ui.library.MenuHeader
import com.lin0721.linmusic.desktop.ui.library.MenuOption
import com.lin0721.linmusic.desktop.ui.library.SEARCH_ANIMATION_MS
import com.lin0721.linmusic.desktop.ui.palette.FallbackCoverPalette
import com.lin0721.linmusic.desktop.ui.palette.extractCoverPaletteFromUrl
import com.lin0721.linmusic.desktop.ui.palette.headerCoverBackground
import com.lin0721.linmusic.desktop.ui.player.rememberQueueReorderState
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.track.ADDED_COLUMN_WIDTH
import com.lin0721.linmusic.desktop.ui.track.ImportSubmenuContent
import com.lin0721.linmusic.desktop.ui.track.TrackReorder
import com.lin0721.linmusic.desktop.ui.track.TrackRowSelection
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.desktop.ui.track.TrackSelectionBar
import com.lin0721.linmusic.desktop.ui.track.TrackRow
import com.lin0721.linmusic.desktop.ui.track.rememberTrackActions
import com.lin0721.linmusic.feature.playlist.presentation.PlaylistSortKey
import com.lin0721.linmusic.feature.playlist.presentation.PlaylistSortOrder
import com.lin0721.linmusic.feature.playlist.presentation.filterTracks
import com.lin0721.linmusic.feature.playlist.presentation.formatAddedDate
import com.lin0721.linmusic.feature.playlist.presentation.formatTotalDuration
import com.lin0721.linmusic.feature.playlist.presentation.sortTracks
import com.lin0721.linmusic.feature.playlist.ui.PlaylistImportState
import com.lin0721.linmusic.feature.playlist.ui.PlaylistUiState
import com.lin0721.linmusic.feature.playlist.ui.PlaylistViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.Collections

// 距离列表底部还剩几项时加载下一页曲目
private const val TRACK_LOAD_MORE_THRESHOLD = 10
private const val HERO_DARKEN_FRACTION = 0.35f
private const val PRIVATE_PLAYLIST = 10

// PlaylistViewModel 约定的特殊歌单 id：每日推荐
internal const val DAILY_RECOMMEND_ID = -1L
internal const val DAILY_RECOMMEND_NAME = "每日推荐"

// 表头项与吸顶列头占用的列表下标
private const val LEADING_LIST_ITEMS = 2

// 吸顶标题横栏滑入滑出与播放按钮展开的时长
private const val STICKY_BAR_ANIMATION_MS = 200

private val HeaderPadding = 24.dp
private val ActionBarVerticalPadding = 16.dp
private val PlayButtonSize = 56.dp
private val StickyBarHeight = 64.dp
private val StickyPlayButtonSize = 48.dp
private val ControlHeight = 36.dp
private val SearchWidth = 240.dp
private val SortMenuWidth = 200.dp

@Composable
fun PlaylistPage(
    playlistId: Long,
    isAlbum: Boolean,
    viewModel: PlaylistViewModel,
    controller: PlaybackController,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(playlistId, isAlbum) {
        viewModel.loadPlaylistIfNeeded(playlistId, isAlbum)
    }
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        PlaylistUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DesktopColors.Accent)
        }
        is PlaylistUiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = DesktopColors.TextGray)
                TextButton(onClick = { viewModel.loadPlaylist(playlistId, isAlbum) }) {
                    Text("重试", color = DesktopColors.TextPrimary)
                }
            }
        }
        is PlaylistUiState.Success -> {
            // 单例 ViewModel 在切换歌单的瞬间仍持有上一个歌单，避免闪现旧内容
            if (state.playlist.id != playlistId && !isAlbum) {
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DesktopColors.Accent)
                }
            } else {
                PlaylistContent(state, isAlbum, viewModel, controller, modifier)
            }
        }
    }
}

private class PlaylistDialogState {
    var showEdit by mutableStateOf(false)
    var showAddSongs by mutableStateOf(false)
    var showDelete by mutableStateOf(false)
    var showCreateImport by mutableStateOf(false)
    var showBatchCreate by mutableStateOf(false)
    var showBatchRemove by mutableStateOf(false)
}

@Composable
private fun rememberPlaylistDialogState(playlistId: Long): PlaylistDialogState =
    remember(playlistId) { PlaylistDialogState() }

@Composable
private fun PlaylistDialogsHost(
    playlist: PlaylistDetail,
    viewModel: PlaylistViewModel,
    dialogState: PlaylistDialogState
) {
    val navigator = LocalDesktopNavigator.current
    if (dialogState.showEdit) {
        val isSavingInfo by viewModel.isSavingInfo.collectAsState()
        EditPlaylistDialog(
            initialName = playlist.name,
            initialDescription = playlist.description.orEmpty(),
            coverUrl = playlist.coverImgUrl,
            isSaving = isSavingInfo,
            onDismiss = { dialogState.showEdit = false },
            onConfirm = { name, description, coverBytes ->
                viewModel.updatePlaylistInfo(
                    playlist.id, playlist.name, name, playlist.description, description, coverBytes
                ) { success -> if (success) dialogState.showEdit = false }
            }
        )
    }
    if (dialogState.showAddSongs) {
        AddSongsDialog(
            playlistId = playlist.id,
            existingIds = remember(playlist.tracks) { playlist.tracks.mapTo(HashSet()) { it.id } },
            viewModel = viewModel,
            onDismiss = { dialogState.showAddSongs = false }
        )
    }
    if (dialogState.showCreateImport) {
        CreatePlaylistDialog(
            onDismiss = { dialogState.showCreateImport = false },
            onCreate = { name, isPrivate ->
                dialogState.showCreateImport = false
                viewModel.createPlaylistAndImportAll(name, isPrivate)
            },
            onEmptyName = { navigator.showMessage(EMPTY_NAME_MESSAGE) }
        )
    }
    if (dialogState.showBatchCreate) {
        CreatePlaylistDialog(
            onDismiss = { dialogState.showBatchCreate = false },
            onCreate = { name, isPrivate ->
                dialogState.showBatchCreate = false
                viewModel.batchCreatePlaylistAndAdd(name, isPrivate)
            },
            onEmptyName = { navigator.showMessage(EMPTY_NAME_MESSAGE) }
        )
    }
    if (dialogState.showBatchRemove) {
        val selection by viewModel.selection.collectAsState()
        DesktopDialog(
            title = "从歌单中删除",
            onDismiss = { dialogState.showBatchRemove = false },
            width = 340.dp,
            actions = {
                DialogButton("取消", { dialogState.showBatchRemove = false })
                DialogButton("删除", {
                    dialogState.showBatchRemove = false
                    viewModel.batchRemove()
                }, danger = true)
            }
        ) {
            Text("确定要从歌单中删除选中的 ${selection.count} 首歌曲吗？", color = DesktopColors.TextGray, fontSize = 13.sp)
        }
    }
    if (dialogState.showDelete) {
        DesktopDialog(
            title = "删除歌单",
            onDismiss = { dialogState.showDelete = false },
            width = 340.dp,
            actions = {
                DialogButton("取消", { dialogState.showDelete = false })
                DialogButton("删除", {
                    dialogState.showDelete = false
                    viewModel.deletePlaylist(playlist.id) { navigator.goBack() }
                }, danger = true)
            }
        ) {
            Text("确定要删除歌单「${playlist.name}」吗？", color = DesktopColors.TextGray, fontSize = 13.sp)
        }
    }
}

private class PlaylistPlaybackState(
    val isShuffleActive: Boolean,
    val isCurrentlyPlayingThis: Boolean,
    val onPlayClick: () -> Unit,
    val playFrom: (Track) -> Unit
)

@Composable
private fun rememberPlaylistPlaybackState(
    playlist: PlaylistDetail,
    viewModel: PlaylistViewModel,
    controller: PlaybackController,
    unplayableIds: Set<Long>,
    customView: Boolean,
    query: String,
    order: PlaylistSortOrder,
    addedAt: Map<Long, Long>
): PlaylistPlaybackState {
    val playMode by controller.playMode.collectAsState()
    val isShuffleActive = playMode == PlayMode.SHUFFLE
    val isPlaying by controller.playWhenReady.collectAsState()
    val playContext by controller.playContext.collectAsState()
    val playSource by controller.playSource.collectAsState()
    val isThisPlaylistContext = playSource?.id == playlist.id || (playlist.name.isNotBlank() && playContext == playlist.name)
    val isCurrentlyPlayingThis = isThisPlaylistContext && isPlaying

    val viewOf = remember(query, order, addedAt) {
        { all: List<Track> -> sortTracks(filterTracks(all, query), order, addedAt) }
    }
    val playFrom: (Track) -> Unit = remember(customView, isShuffleActive, viewOf, viewModel) {
        { track ->
            if (customView && !isShuffleActive) {
                viewModel.playTrackInPlaylist(track)
            } else {
                viewModel.ensureAllTracksLoaded { all ->
                    val list = viewOf(all)
                    val ordered = if (isShuffleActive) {
                        val remaining = list.filter { it.id != track.id }.shuffled()
                        listOf(track) + remaining
                    } else {
                        list
                    }
                    viewModel.playSongInList(track, ordered)
                }
            }
        }
    }
    val playDisplayed: (Boolean) -> Unit = remember(customView, viewOf, unplayableIds, viewModel) {
        { shuffle ->
            if (customView) {
                viewModel.playAll(shuffle)
            } else {
                viewModel.ensureAllTracksLoaded { all ->
                    val ordered = viewOf(all).let { if (shuffle) it.shuffled() else it }
                    ordered.firstOrNull { it.id !in unplayableIds }?.let { viewModel.playSongInList(it, ordered) }
                }
            }
        }
    }
    val onPlayClick: () -> Unit = remember(isThisPlaylistContext, isShuffleActive, controller, playDisplayed) {
        {
            if (isThisPlaylistContext) {
                controller.togglePlayPause()
            } else {
                playDisplayed(isShuffleActive)
            }
        }
    }

    return remember(isShuffleActive, isCurrentlyPlayingThis, onPlayClick, playFrom) {
        PlaylistPlaybackState(
            isShuffleActive = isShuffleActive,
            isCurrentlyPlayingThis = isCurrentlyPlayingThis,
            onPlayClick = onPlayClick,
            playFrom = playFrom
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistContent(
    state: PlaylistUiState.Success,
    isAlbum: Boolean,
    viewModel: PlaylistViewModel,
    controller: PlaybackController,
    modifier: Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val playlist = state.playlist
    val profile by viewModel.userProfile.collectAsState()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val likedSongIds by viewModel.likedSongIds.collectAsState()
    val unplayableIds by viewModel.unplayableIds.collectAsState()
    val collectState by viewModel.collectState.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val externalTracks by viewModel.externalTracks.collectAsState()

    val uid = profile?.uid
    val isLikedView = uid != null && !isAlbum && isLikedSongsPlaylist(playlist.name, playlist.id, uid)
    val isOwned = uid != null && !isAlbum && playlist.id > 0 && playlist.creator?.userId == uid && !isLikedView
    val canSubscribe = playlist.id > 0 && !isOwned && !isLikedView

    val removeTrack: ((Track) -> Unit)? = if (isOwned) {
        fun(track: Track) = viewModel.removeTrackFromPlaylist(playlist.id, track.id)
    } else {
        null
    }
    val actions = rememberTrackActions(
        likedSongIds = likedSongIds,
        collectState = collectState,
        onToggleLike = viewModel::toggleLikeSong,
        onPlayNext = viewModel::addTrackToPlayNext,
        onPrepareCollect = viewModel::prepareCollectDialog,
        onSaveCollect = viewModel::savePlaylistCollection,
        onCreateAndAdd = viewModel::createPlaylistAndAddSong,
        onRemove = removeTrack,
        onReduceRecommend = if (playlist.id == DAILY_RECOMMEND_ID && navigator.isLoggedIn) {
            { track -> viewModel.reduceDailyRecommend(track.id) }
        } else {
            null
        }
    )

    val commentsHost = remember(viewModel) { viewModel.asCommentsHost() }
    val commentsOpen = navigator.isCommentsPanelOpen(commentsHost)
    val dialogState = rememberPlaylistDialogState(playlist.id)
    var query by remember(playlist.id) { mutableStateOf("") }
    var order by remember(playlist.id) { mutableStateOf(PlaylistSortOrder()) }
    val addedAt = remember(playlist.trackIds) { playlist.trackIds.associate { it.id to it.at } }
    val hasDates = remember(addedAt) { addedAt.values.any { it > 0 } }
    val customView = order.key == PlaylistSortKey.CUSTOM && query.isBlank()
    val displayed = remember(playlist.tracks, query, order, addedAt) {
        sortTracks(filterTracks(playlist.tracks, query), order, addedAt)
    }

    val playbackState = rememberPlaylistPlaybackState(
        playlist = playlist,
        viewModel = viewModel,
        controller = controller,
        unplayableIds = unplayableIds,
        customView = customView,
        query = query,
        order = order,
        addedAt = addedAt
    )

    // 排序与搜索针对全部曲目，自建歌单的拖动调序按全量提交，都需要把没加载的补齐
    LaunchedEffect(customView, isOwned, playlist.id, state.hasMoreTracks) {
        if ((!customView || isOwned) && state.hasMoreTracks && !state.isLoadingMoreTracks) viewModel.ensureAllTracksLoaded { }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draft by remember(playlist.id) { mutableStateOf<List<Track>?>(null) }
    var savingOrder by remember(playlist.id) { mutableStateOf(false) }
    val canReorder = isOwned && customView && !state.hasMoreTracks && !state.isLoadingMoreTracks && !savingOrder
    val shown = draft ?: displayed
    val reorder = rememberQueueReorderState(
        listState = listState,
        upcomingStart = 0,
        queueSize = shown.size,
        upcomingFirstLazyIndex = LEADING_LIST_ITEMS,
        onMove = { from, to -> draft = (draft ?: displayed).toMutableList().also { Collections.swap(it, from, to) } }
    )
    LaunchedEffect(reorder.isDragging) {
        if (reorder.isDragging) reorder.runEdgeScroll()
    }
    val commitOrder = {
        val reordered = draft
        if (reordered != null && reordered != playlist.tracks) {
            savingOrder = true
            viewModel.updateTrackOrder(playlist.id, reordered) {
                savingOrder = false
                draft = null
            }
        } else {
            draft = null
        }
    }
    // 同一首歌不会重复入歌单，但按出现次数区分以防服务端数据异常
    val rowKeys = remember(shown) {
        val seen = HashMap<Long, Int>()
        shown.map { track ->
            val occurrence = seen.getOrElse(track.id) { 0 }
            seen[track.id] = occurrence + 1
            "track_${track.id}_$occurrence"
        }
    }

    val count = when {
        playlist.trackIds.isNotEmpty() -> playlist.trackIds.size
        playlist.trackCount > 0 -> playlist.trackCount
        else -> playlist.tracks.size
    }
    val totalMs = if (state.hasMoreTracks) null else playlist.tracks.sumOf { it.dt }

    var heroBase by remember(playlist.id) { mutableStateOf(FallbackCoverPalette.base) }
    LaunchedEffect(playlist.coverImgUrl) {
        if (playlist.coverImgUrl.isNotBlank()) heroBase = extractCoverPaletteFromUrl(playlist.coverImgUrl).base
    }
    val heroColor by animateColorAsState(headerCoverBackground(heroBase, HERO_DARKEN_FRACTION), uiSpring(), label = "playlistHero")

    val density = LocalDensity.current
    val barHeightPx = with(density) { StickyBarHeight.roundToPx() }
    val playButtonBottomPx = with(density) { (ActionBarVerticalPadding + PlayButtonSize).roundToPx() }
    // 恢复页面现场时列表直接停在中间，大标题不会再测一次，高度要随现场保存
    var heroHeightPx by rememberSaveable(playlist.id) { mutableIntStateOf(0) }
    var headerHeightPx by rememberSaveable(playlist.id) { mutableIntStateOf(0) }
    // 大标题整体滑到横栏之下时出现歌单名，操作栏的播放按钮滑到横栏之下时再出现播放按钮
    val showTitleBar by remember(barHeightPx) {
        derivedStateOf { heroHeightPx > 0 && listState.scrolledPast(heroHeightPx - barHeightPx) }
    }
    val showPlayButton by remember(barHeightPx, playButtonBottomPx) {
        derivedStateOf { heroHeightPx > 0 && listState.scrolledPast(heroHeightPx + playButtonBottomPx - barHeightPx) }
    }

    val shouldLoadMore by remember(state) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasMoreTracks && !state.isLoadingMoreTracks &&
                lastVisible >= listState.layoutInfo.totalItemsCount - TRACK_LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreTracks()
    }

    HoverScrollbarBox(listState) {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                Column(
                    Modifier.fillMaxWidth().onSizeChanged { headerHeightPx = it.height }
                        .background(Brush.verticalGradient(listOf(heroColor, DesktopColors.PageFill)))
                ) {
                    Box(Modifier.onSizeChanged { heroHeightPx = it.height }) {
                        PlaylistHero(playlist, isAlbum, count, totalMs)
                    }
                    PlaylistActionBar(
                        isAlbum = isAlbum,
                        canSubscribe = canSubscribe,
                        isSubscribed = state.isSubscribed,
                        query = query,
                        order = order,
                        showAdded = hasDates,
                        canDelete = isOwned,
                        onDelete = { dialogState.showDelete = true },
                        showComments = playlist.id > 0,
                        commentsOpen = commentsOpen,
                        onToggleComments = {
                            if (!commentsOpen) viewModel.loadPlaylistComments(playlist.id)
                            navigator.toggleCommentsPanel(commentsHost)
                        },
                        isPlaying = playbackState.isCurrentlyPlayingThis,
                        onPlay = playbackState.onPlayClick,
                        isShuffleActive = playbackState.isShuffleActive,
                        onToggleShuffle = controller::toggleShuffle,
                        onToggleSubscribe = {
                            if (navigator.isLoggedIn) viewModel.toggleSubscribePlaylist() else navigator.showMessage("请先登录账号")
                        },
                        onDownload = { viewModel.downloadPlaylist(playlist.id, playlist.name, navigator.downloadLevel) },
                        onPlayNextAll = viewModel::addAllTracksToPlayNext,
                        importState = importState,
                        onPrepareImport = { viewModel.prepareImportTargets(playlist.id) },
                        onImportTo = { target -> viewModel.importAllTracksTo(target.id) },
                        onCreateImport = { dialogState.showCreateImport = true },
                        canCopyLink = playlist.id > 0,
                        onCopyLink = {
                            navigator.showMessage(if (copyPlaylistLink(isAlbum, playlist.id)) "已复制链接" else "复制失败")
                        },
                        onQueryChange = { query = it },
                        onOrderChange = { order = it },
                        onEnterSelection = { viewModel.enterSelection() }
                    )
                    if (isOwned) {
                        Row(
                            Modifier.padding(start = HeaderPadding, end = HeaderPadding, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PlaylistPill(Icons.Rounded.Add, "添加") { dialogState.showAddSongs = true }
                            PlaylistPill(Icons.Rounded.Edit, "名称和详情") { dialogState.showEdit = true }
                        }
                    }
                }
            }
            stickyHeader(key = "columns") {
                // 吸顶后让到标题横栏之下，位移由表头项的滚动位置推出
                Box(
                    Modifier.graphicsLayer {
                        if (headerHeightPx > 0) {
                            val naturalY = if (listState.firstVisibleItemIndex > 0) 0 else headerHeightPx - listState.firstVisibleItemScrollOffset
                            translationY = (barHeightPx - naturalY).coerceIn(0, barHeightPx).toFloat()
                        }
                    }
                ) {
                    Column(Modifier.paneBackdrop(DesktopColors.Pane)) {
                        if (selection.active) {
                            val shownIds = shown.map { it.id }
                            TrackSelectionBar(
                                count = selection.count,
                                allSelected = shownIds.isNotEmpty() && selection.selectedIds.containsAll(shownIds),
                                importState = importState,
                                canRemove = isOwned,
                                onToggleAll = { viewModel.toggleSelectAll(shownIds) },
                                onPlayNext = viewModel::batchPlayNext,
                                onPrepareImport = { viewModel.prepareImportTargets(playlist.id) },
                                onImportTo = { target -> viewModel.batchAddToPlaylist(target.id) },
                                onCreateImport = { dialogState.showBatchCreate = true },
                                onLike = viewModel::batchLike,
                                onDownload = { viewModel.batchDownload(navigator.downloadLevel) },
                                onRemove = { dialogState.showBatchRemove = true },
                                onExit = viewModel::exitSelection
                            )
                        }
                        TrackColumnHeader(order, hasDates) { order = order.toggled(it) }
                    }
                }
            }
            itemsIndexed(shown, key = { index, _ -> rowKeys[index] }) { index, track ->
                val dragging = reorder.draggedIndex == index
                val settling = reorder.settlingIndex == index
                TrackRow(
                    index = index,
                    track = track,
                    isCurrent = nowPlaying?.songId == track.id,
                    onPlay = { playbackState.playFrom(track) },
                    actions = actions,
                    // 多选态下不可播放的歌曲也能勾选
                    enabled = selection.active || track.id !in unplayableIds,
                    selection = if (selection.active) {
                        TrackRowSelection(
                            checked = track.id in selection.selectedIds,
                            onToggle = { viewModel.toggleSelected(track.id) }
                        )
                    } else {
                        null
                    },
                    // 抬起中的行自己负责位移，交给 animateItem 会和手动位移打架
                    modifier = Modifier.padding(horizontal = 16.dp)
                        .then(if (dragging || settling) Modifier.zIndex(1f) else if (LocalUiMotion.current.enabled) Modifier.animateItem() else Modifier),
                    addedAtText = if (hasDates) formatAddedDate(addedAt[track.id] ?: 0L) else null,
                    sourcePlatform = externalTracks[track.id]?.platform,
                    // 外部歌曲的位置跟随加入时间，不参与拖动调序
                    reorder = if (!selection.active && track.id > 0L && (canReorder || dragging)) {
                        TrackReorder(
                            dragging = dragging,
                            offsetY = reorder.offsetOf(index),
                            onDragStart = { reorder.start(index) },
                            onDrag = { delta -> reorder.drag(index, delta) },
                            onDragEnd = {
                                reorder.end(index, scope)
                                commitOrder()
                            }
                        )
                    } else {
                        null
                    }
                )
            }
            if (shown.isEmpty() && query.isNotBlank() && !state.isLoadingMoreTracks) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("未找到匹配的歌曲", color = DesktopColors.TextGray, fontSize = 14.sp)
                    }
                }
            }
            if (isOwned && query.isBlank() && state.recommendedSongs.isNotEmpty()) {
                item(key = "recommend_header") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = HeaderPadding, end = HeaderPadding, top = 32.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("推荐歌曲", color = DesktopColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("根据歌单里的歌挑选", color = DesktopColors.TextGray, fontSize = 13.sp)
                        }
                        TextButton(onClick = viewModel::refreshRecommendations) {
                            Text("换一批", color = DesktopColors.TextGray, fontSize = 13.sp)
                        }
                    }
                }
                itemsIndexed(state.recommendedSongs, key = { _, track -> "recommend_${track.id}" }) { index, track ->
                    TrackRow(
                        index = index,
                        track = track,
                        isCurrent = nowPlaying?.songId == track.id,
                        onPlay = { viewModel.playRecommendedTrack(track) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        trailingAction = {
                            DesktopTooltip("添加到歌单") {
                                IconButton(
                                    onClick = { viewModel.addRecommendSongToPlaylist(playlist.id, track) },
                                    modifier = Modifier.padding(start = 4.dp).size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, "添加到歌单", tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    )
                }
            }
            if (state.isLoadingMoreTracks) {
                item(key = "loading_more") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DesktopColors.Accent)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            AnimatedVisibility(
                visible = showTitleBar,
                enter = fadeIn(uiTween(STICKY_BAR_ANIMATION_MS)) + slideInVertically(uiTween(STICKY_BAR_ANIMATION_MS)) { -it / 2 },
                exit = fadeOut(uiTween(STICKY_BAR_ANIMATION_MS)) + slideOutVertically(uiTween(STICKY_BAR_ANIMATION_MS)) { -it / 2 }
            ) {
                PlaylistStickyBar(playlist.name, heroColor, showPlayButton, playbackState.isCurrentlyPlayingThis, listState, playbackState.onPlayClick)
            }
        }
    }

    PlaylistDialogsHost(
        playlist = playlist,
        viewModel = viewModel,
        dialogState = dialogState
    )
}

@Composable
private fun PlaylistHero(playlist: PlaylistDetail, isAlbum: Boolean, count: Int, totalMs: Long?) {
    val typeLabel = when {
        playlist.id == DAILY_RECOMMEND_ID -> "为你推荐"
        isAlbum -> "专辑"
        playlist.privacy == PRIVATE_PLAYLIST -> "隐私歌单"
        else -> "公开歌单"
    }
    val navigator = LocalDesktopNavigator.current
    val owner = if (isAlbum) playlist.artists.joinToString(" / ") { it.name } else playlist.creator?.nickname.orEmpty()
    val avatarUrl = if (isAlbum) null else playlist.creator?.avatarUrl
    val creatorId = playlist.creator?.userId?.takeIf { !isAlbum && it > 0 }
    val summary = buildString {
        append("$count 首歌曲")
        if (totalMs != null) append("，${formatTotalDuration(totalMs)}")
    }
    Row(Modifier.fillMaxWidth().padding(HeaderPadding), verticalAlignment = Alignment.Bottom) {
        Cover(playlist.coverImgUrl, 200.dp, shape = RoundedCornerShape(6.dp))
        Column(Modifier.weight(1f).padding(start = HeaderPadding)) {
            Text(typeLabel, color = DesktopColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                playlist.name,
                color = DesktopColors.TextPrimary,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(4.dp)).then(
                        if (creatorId != null) {
                            Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { navigator.openProfile(creatorId) }
                        } else {
                            Modifier
                        }
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        Cover(avatarUrl, 24.dp, shape = CircleShape, modifier = Modifier.padding(end = 8.dp))
                    }
                    if (owner.isNotBlank()) {
                        Text(owner, color = DesktopColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (owner.isNotBlank()) Text(" · ", color = DesktopColors.TextGray, fontSize = 14.sp)
                Text(summary, color = DesktopColors.TextGray, fontSize = 14.sp)
            }
        }
    }
}

// 表头项已滚出视野，或滚动距离达到阈值
internal fun LazyListState.scrolledPast(thresholdPx: Int): Boolean =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset >= thresholdPx

@Composable
internal fun PlaylistStickyBar(
    name: String,
    color: Color,
    showPlay: Boolean,
    isPlaying: Boolean,
    listState: LazyListState,
    onPlay: () -> Unit
) {
    Row(
        // 横栏盖住列表顶部，滚轮事件转给列表
        Modifier.fillMaxWidth().height(StickyBarHeight).background(color)
            .scrollable(listState, Orientation.Vertical)
            .padding(horizontal = HeaderPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = showPlay,
            enter = fadeIn(uiTween(STICKY_BAR_ANIMATION_MS)) + expandHorizontally(uiTween(STICKY_BAR_ANIMATION_MS)),
            exit = fadeOut(uiTween(STICKY_BAR_ANIMATION_MS)) + shrinkHorizontally(uiTween(STICKY_BAR_ANIMATION_MS))
        ) {
            PlayCircleButton(StickyPlayButtonSize, 28.dp, isPlaying, onPlay, Modifier.padding(end = 12.dp))
        }
        Text(
            name,
            color = DesktopColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun PlayCircleButton(
    size: Dp,
    iconSize: Dp,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DesktopTooltip(if (isPlaying) "暂停" else "播放全部") {
        Box(
            modifier.size(size).clip(CircleShape).background(DesktopColors.Accent)
                .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (isPlaying) "暂停" else "播放全部",
                tint = DesktopColors.TextPrimary,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun PlaylistActionBar(
    isAlbum: Boolean,
    canSubscribe: Boolean,
    isSubscribed: Boolean,
    query: String,
    order: PlaylistSortOrder,
    showAdded: Boolean,
    canDelete: Boolean,
    onDelete: () -> Unit,
    showComments: Boolean,
    commentsOpen: Boolean,
    onToggleComments: () -> Unit,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    isShuffleActive: Boolean,
    onToggleShuffle: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onDownload: () -> Unit,
    onPlayNextAll: () -> Unit,
    importState: PlaylistImportState,
    onPrepareImport: () -> Unit,
    onImportTo: (UserPlaylist) -> Unit,
    onCreateImport: () -> Unit,
    canCopyLink: Boolean,
    onCopyLink: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOrderChange: (PlaylistSortOrder) -> Unit,
    onEnterSelection: () -> Unit
) {
    val resourceLabel = if (isAlbum) "专辑" else "歌单"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = HeaderPadding, vertical = ActionBarVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayCircleButton(PlayButtonSize, 32.dp, isPlaying, onPlay)
        ActionIcon(
            Icons.Rounded.Shuffle,
            if (isShuffleActive) "随机播放已开启" else "随机播放已关闭",
            tint = if (isShuffleActive) DesktopColors.Accent else DesktopColors.TextGray,
            onClick = onToggleShuffle
        )
        if (canSubscribe) {
            ActionIcon(
                if (isSubscribed) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (isSubscribed) "取消收藏$resourceLabel" else "收藏$resourceLabel",
                tint = if (isSubscribed) DesktopColors.Accent else DesktopColors.TextGray,
                onClick = onToggleSubscribe
            )
        }
        if (showComments) {
            ActionIcon(
                Icons.Rounded.ChatBubbleOutline,
                "${resourceLabel}评论",
                tint = if (commentsOpen) DesktopColors.TextPrimary else DesktopColors.TextGray,
                onClick = onToggleComments
            )
        }
        ActionIcon(Icons.Rounded.Download, "下载$resourceLabel", onClick = onDownload)
        PlaylistMoreMenu(
            resourceLabel, canDelete, canCopyLink, onPlayNextAll,
            importState, onPrepareImport, onImportTo, onCreateImport, onCopyLink, onDelete
        )
        Spacer(Modifier.weight(1f))
        ActionIcon(Icons.Rounded.Checklist, "多选", onClick = onEnterSelection)
        PlaylistSearchBox(query, onQueryChange)
        PlaylistSortMenu(order, showAdded) { key ->
            onOrderChange(if (key == PlaylistSortKey.CUSTOM) PlaylistSortOrder() else PlaylistSortOrder(key, true))
        }
    }
}

@Composable
internal fun ActionIcon(
    icon: ImageVector,
    description: String,
    tint: Color = DesktopColors.TextGray,
    onClick: () -> Unit
) {
    DesktopTooltip(description) {
        IconButton(onClick = onClick) {
            Icon(icon, description, tint = tint, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun PlaylistMoreMenu(
    resourceLabel: String,
    canDelete: Boolean,
    canCopyLink: Boolean,
    onPlayNextAll: () -> Unit,
    importState: PlaylistImportState,
    onPrepareImport: () -> Unit,
    onImportTo: (UserPlaylist) -> Unit,
    onCreateImport: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        ActionIcon(Icons.Rounded.MoreHoriz, "更多") { expanded = true }
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }) {
            MenuItem("全部加入下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
                expanded = false
                onPlayNextAll()
            })
            if (navigator.isLoggedIn) {
                SubMenuItem("添加到歌单", icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onOpen = onPrepareImport) {
                    ImportSubmenuContent(importState, onImportTo = {
                        expanded = false
                        onImportTo(it)
                    }, onCreate = {
                        expanded = false
                        onCreateImport()
                    })
                }
            } else {
                MenuItem("添加到歌单", icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = {
                    expanded = false
                    navigator.showMessage("请先登录账号")
                })
            }
            if (canCopyLink) {
                MenuItem("复制${resourceLabel}链接", icon = Icons.Rounded.Link, onClick = {
                    expanded = false
                    onCopyLink()
                })
            }
            if (canDelete) {
                MenuDivider()
                MenuItem("删除歌单", icon = Icons.Rounded.Delete, danger = true, onClick = {
                    expanded = false
                    onDelete()
                })
            }
        }
    }
}

@Composable
internal fun PlaylistPill(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(DesktopColors.Surface)
            .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = DesktopColors.TextPrimary, modifier = Modifier.size(18.dp))
        Text(
            text,
            color = DesktopColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

// 点图标展开成输入框，失焦且为空时收回；Esc 清空并收回
@Composable
internal fun PlaylistSearchBox(query: String, onQueryChange: (String) -> Unit) {
    var searching by remember { mutableStateOf(false) }
    var focusedOnce by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val width by animateDpAsState(
        if (searching) SearchWidth else ControlHeight,
        uiTween(SEARCH_ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "playlistSearchWidth"
    )
    val background by animateColorAsState(
        if (searching) DesktopColors.Surface else DesktopColors.Surface.copy(alpha = 0f),
        uiTween(SEARCH_ANIMATION_MS),
        label = "playlistSearchBackground"
    )
    LaunchedEffect(searching) {
        if (searching) {
            focusedOnce = false
            runCatching { focusRequester.requestFocus() }
        }
    }
    Box(
        Modifier.width(width).height(ControlHeight).clip(RoundedCornerShape(ControlHeight / 2)).background(background)
            .then(if (searching) Modifier else Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { searching = true })
    ) {
        if (searching) {
            LibrarySearchInput(
                query = query,
                startPadding = 10.dp,
                showClear = true,
                onQueryChange = onQueryChange,
                onClear = {
                    onQueryChange("")
                    searching = false
                },
                focusRequester = focusRequester,
                onFocusChange = { focused ->
                    if (focused) focusedOnce = true else if (focusedOnce && query.isEmpty()) searching = false
                },
                placeholder = "在歌单内搜索"
            )
        } else {
            DesktopTooltip("在歌单内搜索", modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Search, "搜索", tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
internal fun PlaylistSortMenu(order: PlaylistSortOrder, showAdded: Boolean, onSelect: (PlaylistSortKey) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box(Modifier.onSizeChanged { anchorWidth = with(density) { it.width.toDp() } }) {
        Row(
            Modifier.height(ControlHeight).clip(RoundedCornerShape(ControlHeight / 2)).pointerHoverIcon(PointerIcon.Hand)
                .clickable { expanded = true }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(order.key.label, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, null, tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
        }
        LibraryPopupMenu(expanded, { expanded = false }, anchorWidth, SortMenuWidth) {
            MenuHeader("排序依据")
            PlaylistSortKey.entries.filter { it != PlaylistSortKey.ADDED || showAdded }.forEach { key ->
                MenuOption(key.label, selected = key == order.key) {
                    expanded = false
                    onSelect(key)
                }
            }
        }
    }
}

// 列宽与 TrackRow 逐项对齐：序号 32、封面槽 56、标题 0.45、专辑 0.35、[添加日期]、红心槽 32、时长 48、更多槽 36
@Composable
internal fun TrackColumnHeader(order: PlaylistSortOrder, showAdded: Boolean, onSort: (PlaylistSortKey) -> Unit) {
    Column(Modifier.fillMaxWidth().paneBackdrop(DesktopColors.Pane)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#",
                color = DesktopColors.TextGray,
                fontSize = 13.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.width(32.dp).pointerHoverIcon(PointerIcon.Hand).clickable { onSort(PlaylistSortKey.CUSTOM) }
            )
            Spacer(Modifier.width(56.dp))
            HeaderCell("标题", PlaylistSortKey.TITLE, order, Modifier.weight(0.45f).padding(start = 12.dp), onSort)
            HeaderCell("专辑", PlaylistSortKey.ALBUM, order, Modifier.weight(0.35f).padding(horizontal = 12.dp), onSort)
            if (showAdded) HeaderCell("添加日期", PlaylistSortKey.ADDED, order, Modifier.width(ADDED_COLUMN_WIDTH), onSort)
            Spacer(Modifier.width(32.dp))
            Row(
                Modifier.width(48.dp).pointerHoverIcon(PointerIcon.Hand).clickable { onSort(PlaylistSortKey.DURATION) },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SortArrow(order, PlaylistSortKey.DURATION)
                Icon(
                    Icons.Rounded.AccessTime,
                    "时长",
                    tint = if (order.key == PlaylistSortKey.DURATION) DesktopColors.TextPrimary else DesktopColors.TextGray,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(36.dp))
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = DesktopColors.SurfaceLight.copy(alpha = 0.6f))
    }
}

@Composable
private fun HeaderCell(
    label: String,
    key: PlaylistSortKey,
    order: PlaylistSortOrder,
    modifier: Modifier,
    onSort: (PlaylistSortKey) -> Unit
) {
    val active = order.key == key
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(4.dp)).pointerHoverIcon(PointerIcon.Hand).clickable { onSort(key) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = if (active) DesktopColors.TextPrimary else DesktopColors.TextGray,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false
            )
            SortArrow(order, key)
        }
    }
}

@Composable
private fun SortArrow(order: PlaylistSortOrder, key: PlaylistSortKey) {
    if (order.key != key) return
    Icon(
        if (order.ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
        if (order.ascending) "升序" else "降序",
        tint = DesktopColors.TextPrimary,
        modifier = Modifier.padding(horizontal = 2.dp).size(14.dp)
    )
}

private fun copyPlaylistLink(isAlbum: Boolean, id: Long): Boolean {
    val path = if (isAlbum) "album" else "playlist"
    return runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection("https://music.163.com/$path?id=$id"), null)
    }.isSuccess
}
