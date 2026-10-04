package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectSheet
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.ui.theme.CoverPlaceholderDark
import com.lin0721.linmusic.feature.playlist.ui.PlaylistSongOptionsSheet

// 歌手详情页骨架：持有折叠进度与弹层状态，按 Tab 装配各内容区块
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistContent(
    artist: ArtistDetailInfo,
    pageState: ArtistPageState,
    isFollowed: Boolean,
    fansCount: Long,
    topSongs: List<Track>,
    albums: List<ArtistAlbum>,
    albumsHasMore: Boolean,
    albumsLoadingMore: Boolean,
    allSongs: List<Track>,
    allSongsLoadingMore: Boolean,
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
    onLoadAllSongsIfNeeded: () -> Unit,
    onLoadMoreAllSongs: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onMusicSubTabSelected: (Int) -> Unit,
    onScrollPositionChanged: (Int, Int) -> Unit
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = pageState.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = pageState.firstVisibleItemScrollOffset
    )

    var showMoreMenuSheet by remember { mutableStateOf(false) }

    var dominantColor by remember { mutableStateOf(CoverPlaceholderDark) }

    // 背景折叠临界点
    val collapseThresholdPx = with(density) { 320.dp.toPx() }
    val progress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseThresholdPx).coerceIn(0f, 1f)
        }
    }

    // 头图 + 操作栏合计高度：与 ArtistBackdrop 的图片+融合色层总高度一致
    val headerHeightPx = with(density) { 310.dp.toPx() }
    val actionBarHeightPx = with(density) { 80.dp.toPx() }
    val backdropCoverThresholdPx = headerHeightPx + actionBarHeightPx
    // 已滚动越过头图区域的像素数：用于驱动下方不透明背板，彻底盖住固定不动的背景图，避免歌曲行等透明区域漏色
    val backdropCoveredPx by remember {
        derivedStateOf {
            val index = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset.toFloat()
            when {
                index <= 0 -> offset
                index == 1 -> headerHeightPx + offset
                else -> backdropCoverThresholdPx
            }.coerceIn(0f, backdropCoverThresholdPx)
        }
    }

    var collectSongId by remember { mutableStateOf<Long?>(null) }
    var showBioDialog by remember { mutableStateOf(false) }
    var optionsTrack by remember { mutableStateOf<Track?>(null) }

    DisposableEffect(artist.id, listState) {
        onDispose {
            onScrollPositionChanged(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    // 首次切到「全部歌曲」子 Tab 时触发加载
    LaunchedEffect(pageState.musicSubTab) {
        if (pageState.musicSubTab == 1) onLoadAllSongsIfNeeded()
    }

    // 滚动到底部附近，按当前 Tab 触发对应区块的分页加载
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 5
        }
    }
    LaunchedEffect(shouldLoadMore, pageState.selectedTab, pageState.musicSubTab) {
        if (!shouldLoadMore) return@LaunchedEffect
        when (pageState.selectedTab) {
            0 -> if (pageState.musicSubTab == 1) onLoadMoreAllSongs()
            1 -> onLoadMoreAlbums()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        ArtistBackdrop(
            artist = artist,
            dominantColor = dominantColor,
            onDominantColorChange = { dominantColor = it }
        )

        // 不透明背板：随头图区滚动距离同步上移，跟随内容边缘彻底盖住背景图，防止歌曲行等透明区域漏出封面
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = backdropCoverThresholdPx - backdropCoveredPx }
                .background(MaterialTheme.colorScheme.background)
        )

        // 滚动视口列表
        @OptIn(ExperimentalFoundationApi::class)
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
            ) {
                item(key = "header_placeholder") {
                    ArtistNameSection(artist = artist, progress = progress, tintColor = dominantColor)
                }

                item(key = "action_bar") {
                    ArtistActionBar(
                        artist = artist,
                        fansCount = fansCount,
                        isFollowed = isFollowed,
                        onFollowClick = onFollowClick,
                        onMoreClick = { showMoreMenuSheet = true },
                        onPlayAll = {
                            val queue = if (pageState.musicSubTab == 1 && allSongs.isNotEmpty()) allSongs else topSongs
                            queue.firstOrNull()?.let { onPlaySong(it, queue) }
                        }
                    )
                }

                item(key = "tab_bar") {
                    ArtistTabBar(
                        selectedTab = pageState.selectedTab,
                        onTabSelected = onTabSelected
                    )
                }

                // 根据选中的 Tab 呈现对应内容
                when (pageState.selectedTab) {
                    0 -> artistMusicTab(
                        hotSongs = topSongs,
                        allSongs = allSongs,
                        musicSubTab = pageState.musicSubTab,
                        onMusicSubTabSelected = onMusicSubTabSelected,
                        allSongsLoadingMore = allSongsLoadingMore,
                        likedSongIds = likedSongIds,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        isLoggedIn = isLoggedIn,
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

        ArtistTopBarOverlay(
            artistName = artist.name,
            progress = progress,
            dominantColor = dominantColor,
            onBack = onBack
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
