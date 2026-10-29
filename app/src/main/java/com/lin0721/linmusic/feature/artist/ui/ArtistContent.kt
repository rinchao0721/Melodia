package com.lin0721.linmusic.feature.artist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectSheet
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.ui.components.SelectionAction
import com.lin0721.linmusic.core.ui.components.TrackSelectionOverlay
import com.lin0721.linmusic.core.ui.components.rememberSelectionBottomPadding
import com.lin0721.linmusic.core.ui.components.TrackSelectionUi
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.melodiaStatusBarTopPadding
import com.lin0721.linmusic.core.ui.theme.paneBackdrop
import com.lin0721.linmusic.core.ui.theme.themedCoverBase
import com.lin0721.linmusic.core.ui.theme.uiEnter
import com.lin0721.linmusic.core.ui.theme.uiExit
import com.lin0721.linmusic.feature.playlist.ui.SearchBarItem
import com.lin0721.linmusic.feature.playlist.ui.rememberSearchBarRevealState
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.feature.playlist.ui.PlaylistSongOptionsSheet

// 搜索展开、头图收起后第 0 项保留的最小高度：保证列表位置可恢复，也用于背板对齐
private val CollapsedHeaderMinHeight = 1.dp
private val PlateSafetyOverlap = 3.dp

// 歌手详情页骨架：持有折叠进度与弹层状态，按 Tab 装配各内容区块
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistContent(
    artist: ArtistDetailInfo,
    isFollowed: Boolean,
    fansCount: Long,
    topSongs: List<Track>,
    albums: List<ArtistAlbum>,
    albumsHasMore: Boolean,
    albumsLoadingMore: Boolean,
    similarArtists: List<ArtistInfo>,
    likedSongIds: Set<Long>,
    blockedArtistIds: Set<Long>,
    currentTrackId: String?,
    isPlaying: Boolean,
    collectState: PlaylistCollectState,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onFollowClick: () -> Unit,
    onBlockClick: () -> Unit,
    onPlaySong: (Track, List<Track>) -> Unit,
    onLikeClick: (Long) -> Unit,
    onToggleLike: (Long, Boolean) -> Unit,
    onAddToPlayNext: (Track) -> Unit,
    onSaveCollection: (Long, List<PlaylistCollectItem>) -> Unit,
    onSaveNewCollection: (String, Long) -> Unit,
    onRequireLogin: () -> Unit,
    onLoadMoreAlbums: () -> Unit,
    songSearch: ArtistSongSearchState,
    onSongSearchQueryChange: (String) -> Unit,
    onLoadMoreSongSearch: () -> Unit,
    selectionUi: TrackSelectionUi,
    selectionActions: List<SelectionAction>,
    onToggleSelectAll: (List<Long>) -> Unit,
    onExitSelection: () -> Unit
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    // 与歌单页同一套搜索栏下拉展开：手势下拉或右上角按钮都走它，展开期间头部整体收起
    val searchBarRevealState = rememberSearchBarRevealState(listState = listState, isDailyRecommend = false)
    val searchOpen = searchBarRevealState.isOpen
    val overlayHeight = 56.dp + melodiaStatusBarTopPadding()

    var showMoreMenuSheet by remember { mutableStateOf(false) }

    val coverPlaceholder = MelodiaColors.CoverPlaceholder
    var dominantColor by remember { mutableStateOf(coverPlaceholder) }

    // 背景折叠临界点
    val collapseThresholdPx = with(density) { 320.dp.toPx() }
    val progress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseThresholdPx).coerceIn(0f, 1f)
        }
    }

    // 不透明背板上沿：取 Tab 栏的实际位置，随头图展开/收起/滚动同步移动，彻底盖住固定不动的背景图，
    // 避免歌曲行等透明区域漏出封面。Tab 栏滚出视口时背板铺满；首帧布局未出时按设计高度（头图 + 操作栏）兜底
    val fallbackPlateTopPx = with(density) { (310.dp + 80.dp).toPx() }
    // 背板上沿向上多盖：折叠头部的最小高度加几 dp 余量，吸收像素取整误差，否则与搜索栏底边之间会漏出一条缝
    // （多盖的这一截在搜索栏浮层之下，被不透明的搜索栏遮住）
    val plateOverlapPx = with(density) { (CollapsedHeaderMinHeight + PlateSafetyOverlap).toPx() }
    val plateTopPx: () -> Float = {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) {
            fallbackPlateTopPx
        } else {
            val tabBar = visible.firstOrNull { it.key == "tab_bar" }
            if (tabBar == null) 0f else (tabBar.offset + searchBarRevealState.revealPx - plateOverlapPx).coerceAtLeast(0f)
        }
    }

    var collectSongId by remember { mutableStateOf<Long?>(null) }
    var showBioDialog by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var optionsTrack by remember { mutableStateOf<Track?>(null) }

    // 滚动到底部附近，按当前 Tab 触发对应区块的分页加载
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 5
        }
    }
    LaunchedEffect(shouldLoadMore, selectedTab) {
        if (!shouldLoadMore) return@LaunchedEffect
        when (selectedTab) {
            0 -> if (songSearch.active) onLoadMoreSongSearch()
            1 -> onLoadMoreAlbums()
        }
    }

    // 搜索栏收起（返回键、顶栏按钮、手势）后清掉关键词，列表回到热门歌曲
    LaunchedEffect(searchOpen) {
        if (!searchOpen) onSongSearchQueryChange("")
    }
    // 搜索展开中拦截系统返回：先关搜索，不直接退出歌手页
    BackHandler(enabled = searchOpen) {
        searchBarRevealState.close()
    }
    BackHandler(enabled = selectionUi.active) {
        onExitSelection()
    }

    Box(modifier = Modifier.fillMaxSize()) {

        ArtistBackdrop(
            artist = artist,
            dominantColor = dominantColor,
            onDominantColorChange = { dominantColor = it }
        )

        // 不透明背板：上沿跟随 Tab 栏位置
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = plateTopPx() }
                .paneBackdrop(MaterialTheme.colorScheme.background)
        )

        // 滚动视口列表
        @OptIn(ExperimentalFoundationApi::class)
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = searchBarRevealState.revealPx }
                    .nestedScroll(searchBarRevealState.connection),
                contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp + rememberSelectionBottomPadding(selectionUi.active))
            ) {
                // 头图、操作栏、Tab 栏在搜索展开意图确定后淡出收起，把空间让给搜索结果
                item(key = "header_placeholder") {
                    // 收起时第 0 项也保留 1dp：高度为 0 的项会被列表算作已滚过，导致关闭搜索后无法回到顶部
                    Box(modifier = Modifier.heightIn(min = CollapsedHeaderMinHeight)) {
                        AnimatedVisibility(
                            visible = !searchOpen,
                            enter = uiEnter(fadeIn() + expandVertically(expandFrom = Alignment.Top)),
                            exit = uiExit(fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top))
                        ) {
                            ArtistNameSection(artist = artist, progress = progress, tintColor = dominantColor)
                        }
                    }
                }

                item(key = "action_bar") {
                    AnimatedVisibility(
                        visible = !searchOpen,
                        enter = uiEnter(fadeIn() + expandVertically(expandFrom = Alignment.Top)),
                        exit = uiExit(fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top))
                    ) {
                        ArtistActionBar(
                            artist = artist,
                            fansCount = fansCount,
                            isFollowed = isFollowed,
                            onFollowClick = onFollowClick,
                            onMoreClick = { showMoreMenuSheet = true },
                            onPlayAll = {
                                topSongs.firstOrNull()?.let { onPlaySong(it, topSongs) }
                            }
                        )
                    }
                }

                item(key = "tab_bar") {
                    AnimatedVisibility(
                        visible = !searchOpen,
                        enter = uiEnter(fadeIn() + expandVertically(expandFrom = Alignment.Top)),
                        exit = uiExit(fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top))
                    ) {
                        ArtistTabBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )
                    }
                }

                // 根据选中的 Tab 呈现对应内容
                when (selectedTab) {
                    0 -> artistMusicTab(
                        hotSongs = topSongs,
                        likedSongIds = likedSongIds,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        isLoggedIn = isLoggedIn,
                        songSearch = songSearch,
                        selectionUi = selectionUi,
                        onPlaySong = onPlaySong,
                        onLikeClick = onLikeClick,
                        onOpenCollectSheet = { collectSongId = it },
                        onOpenMoreOptions = { track ->
                            if (!isLoggedIn) onRequireLogin() else optionsTrack = track
                        },
                        onRequireLogin = onRequireLogin
                    )
                    1 -> artistAlbumTab(
                        albums = albums,
                        loadingMore = albumsLoadingMore,
                        onAlbumClick = onAlbumClick
                    )
                    2 -> artistAboutTab(
                        artist = artist,
                        similarArtists = similarArtists,
                        onShowBioDialog = { showBioDialog = true },
                        onArtistClick = onArtistClick
                    )
                }
            }
        }

        // 搜索栏浮层，随 revealPx 跟手展开
        SearchBarItem(
            query = songSearch.query,
            onQueryChange = onSongSearchQueryChange,
            topPadding = overlayHeight,
            backgroundColor = dominantColor,
            placeholder = "搜索歌手的歌曲",
            requestFocus = searchOpen,
            modifier = Modifier
                .onSizeChanged { searchBarRevealState.searchBarHeightPx = it.height.toFloat() }
                .graphicsLayer {
                    translationY = searchBarRevealState.revealPx - searchBarRevealState.searchBarHeightPx
                }
        )

        ArtistTopBarOverlay(
            artistName = artist.name,
            progress = if (searchOpen) 1f else progress,
            dominantColor = dominantColor,
            onBack = { if (searchOpen) searchBarRevealState.close() else onBack() },
            showSearch = selectedTab == 0,
            searchOpen = searchOpen,
            onSearchClick = { if (searchOpen) searchBarRevealState.close() else searchBarRevealState.open() }
        )

        // 全选范围为当前列表（搜索中为搜索结果）
        TrackSelectionOverlay(
            selectionUi = selectionUi,
            actions = selectionActions,
            visibleIds = (if (songSearch.active) songSearch.results else topSongs).map { it.id },
            headerColor = themedCoverBase(dominantColor, 0.35f),
            onToggleSelectAll = onToggleSelectAll,
            onExit = onExitSelection
        )

        // 详情 Bottom Sheet (批量收藏)
        if (collectSongId != null) {
            PlaylistCollectSheet(
                songId = collectSongId!!,
                collectState = collectState,
                onDismiss = { collectSongId = null },
                onSaveCollection = onSaveCollection,
                onSaveNewCollection = onSaveNewCollection,
                confirmButtonShape = RoundedCornerShape(12.dp)
            )
        }

        // 歌曲「更多操作」Bottom Sheet（下一首播放/喜欢/收藏到歌单/专辑；歌手详情页隐藏「歌手」项）
        optionsTrack?.let { track ->
            PlaylistSongOptionsSheet(
                track = track,
                isLiked = track.id in likedSongIds,
                isLoggedIn = isLoggedIn,
                onDismiss = { optionsTrack = null },
                onAddToPlayNext = onAddToPlayNext,
                onToggleLike = onToggleLike,
                onCollectClick = { songId ->
                    collectSongId = songId
                    onLikeClick(songId)
                },
                onArtistClick = onArtistClick,
                onAlbumClick = onAlbumClick,
                onRequireLogin = onRequireLogin,
                showArtistOption = false
            )
        }

        // 歌手简介 Dialog
        if (showBioDialog) {
            ArtistBioDialog(
                artist = artist,
                onDismiss = { showBioDialog = false }
            )
        }

        // 更多操作 Bottom Sheet
        if (showMoreMenuSheet) {
            ArtistMoreMenuSheet(
                isBlocked = blockedArtistIds.contains(artist.id),
                onBlockClick = onBlockClick,
                onDismiss = { showMoreMenuSheet = false }
            )
        }
    }
}
