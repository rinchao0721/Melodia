package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectSheet
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.ui.theme.FallbackBase
import kotlin.math.max

// TopBar 操作区高度（不含状态栏）
private val TOP_BAR_HEIGHT = 56.dp
// 封面固定尺寸
private val COVER_MAX_SIZE = 260.dp

// ────────────────────────────────────────────────────────────────────────────
// 主内容：折叠状态计算 + 各区块装配
// ────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistContent(
    playlist: PlaylistDetail,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedSongIds: Set<Long>,
    collectState: PlaylistCollectState,
    isLoggedIn: Boolean,
    recommendedSongs: List<Track>,
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onToggleLike: (Long, Boolean) -> Unit,
    onPlaySong: (Track) -> Unit,
    onAddToPlayNext: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleToggle: () -> Unit,
    isShuffleActive: Boolean,
    isCurrentlyPlayingThis: Boolean,
    onLikeClick: (Long) -> Unit,
    onSaveCollection: (Long, List<PlaylistCollectItem>) -> Unit,
    onSaveNewCollection: (String, Long) -> Unit,
    onRequireLogin: () -> Unit,
    onRefreshRecommendations: () -> Unit,
    onAddRecommendSong: (Track) -> Unit,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onMoreClick: () -> Unit,
    isLikedSongsPlaylistView: Boolean = false,
    onShareClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    historyDates: List<String> = emptyList(),
    historySongsLoading: Boolean = false,
    showHistoryDatePicker: Boolean = false,
    selectedHistoryDate: String = "今天",
    onSelectedHistoryDateChange: (String) -> Unit = {},
    onLoadHistoryDetail: (String) -> Unit = {},
    onLoadDailyRecommend: () -> Unit = {},
    canRemoveFromPlaylist: Boolean = false,
    onRemoveFromPlaylist: (Long) -> Unit = {},
    onAddMusicClick: () -> Unit = {},
    onEditOrderClick: () -> Unit = {},
    onEditInfoClick: () -> Unit = {}
) {
    val density = LocalDensity.current

    val isDailyRecommend = playlist.id == -1L || playlist.id == -2L
    // 搜索栏统一为 item 0，无需按是否每日推荐区分初始位置
    val listState = rememberLazyListState()
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(PlaylistSortOption.DEFAULT) }
    var showSortSheet by remember { mutableStateOf(false) }
    val sortedTracks = remember(playlist.tracks, sortOption) { sortOption.sort(playlist.tracks) }

    // 从封面提取的主色调，默认为深灰色
    var dominantColor by remember { mutableStateOf(FallbackBase) }

    // 获取系统状态栏高度
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Overlay 总高度：状态栏 + 操作区(56dp)
    val overlayHeight = TOP_BAR_HEIGHT + statusBarHeight

    // 折叠进度 0f→1f（从封面完整显示到完全折叠）；header 恒为 item 0，两种歌单共用同一套计算
    val collapseThresholdPx = with(density) { 300.dp.toPx() }
    val progress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                (listState.firstVisibleItemScrollOffset / collapseThresholdPx).coerceIn(0f, 1f)
            } else {
                1f
            }
        }
    }

    val coverSize: Dp     = COVER_MAX_SIZE
    // 封面透明度：滚动一开始就同步淡出，65% 处完全消失
    val coverAlpha        = 1f - (progress / 0.65f).coerceIn(0f, 1f)

    // 播放按钮停靠位：按钮中心正好卡在顶栏下边缘
    val dockedYPx = with(density) { (overlayHeight - 28.dp).toPx() }
    // 按钮在完全未滚动时的基准 Y（由 PlaylistHeaderItem 里那个透明占位按钮上报，
    // 只在滚到顶部时才接受更新，滚动过程中不重新测量）
    // 用listState.firstVisibleItemScrollOffset 做数学换算得到实时位置
    // 已知问题：切换播放/暂停图标会让按钮跳一下，根因还没查清楚，先放着
    var playButtonBaselineYPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }

    var collectSongId by remember { mutableStateOf<Long?>(null) }
    var activeSongMoreOptions by remember { mutableStateOf<Track?>(null) }

    val searchBarRevealState = rememberSearchBarRevealState(
        listState        = listState,
        isDailyRecommend = isDailyRecommend
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // ── 1. 滚动内容：整体随 revealPx 下移，让出顶部空间给搜索栏 ───────────
        LazyColumn(
            state          = listState,
            contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp),
            modifier       = Modifier
                .graphicsLayer { translationY = searchBarRevealState.revealPx }
                .nestedScroll(searchBarRevealState.connection)
        ) {
            // Item 0：全出血 Hero
            item(key = "header") {
                PlaylistHeaderItem(
                    playlist            = playlist,
                    coverSize           = coverSize,
                    coverAlpha          = coverAlpha,
                    progress            = progress,
                    statusBarHeight     = statusBarHeight,
                    dominantColor       = dominantColor,
                    onColorCalculated   = { dominantColor = it },
                    onPlayAll              = onPlayAll,
                    onShuffleToggle        = onShuffleToggle,
                    isShuffleActive        = isShuffleActive,
                    isCurrentlyPlayingThis = isCurrentlyPlayingThis,
                    isSubscribed        = isSubscribed,
                    onSubscribeClick    = onSubscribeClick,
                    onCommentsClick     = onCommentsClick,
                    onMoreClick         = onMoreClick,
                    isLikedSongsPlaylistView = isLikedSongsPlaylistView,
                    onShareClick        = onShareClick,
                    onDownloadClick     = onDownloadClick,
                    onHistoryClick      = onHistoryClick,
                    selectedHistoryDate = selectedHistoryDate,
                    onPlayButtonPositioned = { y ->
                        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                            playButtonBaselineYPx = y
                        }
                    }
                )
            }

            // 自建歌单快捷操作小胶囊行
            if (canRemoveFromPlaylist && !isDailyRecommend) {
                item(key = "action_pills") {
                    PlaylistActionPillsRow(
                        isOwnedPlaylist = canRemoveFromPlaylist,
                        isTracksEmpty = playlist.tracks.isEmpty(),
                        currentSortOption = sortOption,
                        onAddMusicClick = onAddMusicClick,
                        onEditOrderClick = {
                            // 拖拽排的是歌单真实顺序。停留在本地排序视图时进入重排，列表会突然跳回默认序，
                            // 保存后回来又被排序规则盖住看不到结果，因此进入前先把视图切回默认
                            sortOption = PlaylistSortOption.DEFAULT
                            onEditOrderClick()
                        },
                        onSortClick = { showSortSheet = true },
                        onEditInfoClick = onEditInfoClick
                    )
                }
            }

            if (playlist.id == -1L && showHistoryDatePicker) {
                item(key = "history_date_picker") {
                    PlaylistHistoryDateRow(
                        historyDates                = historyDates,
                        selectedHistoryDate         = selectedHistoryDate,
                        onSelectedHistoryDateChange = onSelectedHistoryDateChange,
                        onLoadHistoryDetail         = onLoadHistoryDetail,
                        onLoadDailyRecommend        = onLoadDailyRecommend
                    )
                }
            }

            if (playlist.id == -2L) {
                item(key = "user_record_filter") {
                    PlaylistRecordFilterRow(
                        selectedHistoryDate         = selectedHistoryDate,
                        onSelectedHistoryDateChange = onSelectedHistoryDateChange,
                        onLoadHistoryDetail         = onLoadHistoryDetail
                    )
                }
            }

            if (historySongsLoading) {
                item(key = "history_songs_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                playlistTrackItems(
                    tracks             = sortedTracks,
                    searchQuery        = searchQuery,
                    currentTrackId     = currentTrackId,
                    isPlaying          = isPlaying,
                    likedSongIds       = likedSongIds,
                    isLoggedIn         = isLoggedIn,
                    onPlaySong         = onPlaySong,
                    onLikeClick        = onLikeClick,
                    onOpenCollectSheet = { collectSongId = it },
                    onMoreClick        = { activeSongMoreOptions = it }
                )
            }

            // 推荐歌曲板块
            if (recommendedSongs.isNotEmpty()) {
                playlistRecommendItems(
                    recommendedSongs         = recommendedSongs,
                    currentTrackId           = currentTrackId,
                    isPlaying                = isPlaying,
                    onRefreshRecommendations = onRefreshRecommendations,
                    onPlaySong               = onPlaySong,
                    onAddRecommendSong       = onAddRecommendSong
                )
            }
        }

        // ── 2. 搜索栏浮层：隐藏时整体位移到屏幕外上方，随 revealPx 跟手展开 ─────
        if (!isDailyRecommend) {
            SearchBarItem(
                query              = searchQuery,
                onQueryChange      = { searchQuery = it },
                topPadding         = overlayHeight,
                backgroundColor    = dominantColor,
                sortOption         = sortOption,
                onSortOptionChange = { sortOption = it },
                modifier           = Modifier
                    .onSizeChanged { searchBarRevealState.searchBarHeightPx = it.height.toFloat() }
                    .graphicsLayer {
                        translationY = searchBarRevealState.revealPx - searchBarRevealState.searchBarHeightPx
                    }
            )
        }

        // ── 3. 固定 Overlay ───────────────────────────────────────────────
        PlaylistTopBar(
            title           = playlist.name,
            progress        = progress,
            overlayHeight   = overlayHeight,
            statusBarHeight = statusBarHeight,
            dominantColor   = dominantColor,
            onBack          = onBack
        )

        // 播放按钮跟手滑动、到位后锁停
        if (!isDailyRecommend) {
            PlaylistDockedPlayButton(
                dockedOffsetYProvider = {
                    val naturalY = if (listState.firstVisibleItemIndex == 0) {
                        playButtonBaselineYPx - listState.firstVisibleItemScrollOffset
                    } else {
                        -Float.MAX_VALUE
                    }
                    max(naturalY, dockedYPx)
                },
                isCurrentlyPlayingThis = isCurrentlyPlayingThis,
                onPlayAll              = onPlayAll
            )
        }
    }

    if (collectSongId != null) {
        PlaylistCollectSheet(
            songId = collectSongId!!,
            collectState = collectState,
            onDismiss = { collectSongId = null },
            onSaveCollection = onSaveCollection,
            onSaveNewCollection = onSaveNewCollection
        )
    }

    if (activeSongMoreOptions != null) {
        val track = activeSongMoreOptions!!
        PlaylistSongOptionsSheet(
            track = track,
            isLiked = track.id in likedSongIds,
            isLoggedIn = isLoggedIn,
            onDismiss = { activeSongMoreOptions = null },
            onAddToPlayNext = onAddToPlayNext,
            onToggleLike = onToggleLike,
            onCollectClick = { songId ->
                collectSongId = songId
                onLikeClick(songId)
            },
            onArtistClick = onArtistClick,
            onAlbumClick = onAlbumClick,
            onRequireLogin = onRequireLogin,
            canRemoveFromPlaylist = canRemoveFromPlaylist,
            onRemoveFromPlaylist = onRemoveFromPlaylist
        )
    }

    if (showSortSheet) {
        PlaylistSortSheet(
            sortOption = sortOption,
            onSortOptionChange = {
                sortOption = it
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }
}

