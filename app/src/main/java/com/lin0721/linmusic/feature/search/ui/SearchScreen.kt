package com.lin0721.linmusic.feature.search.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.lin0721.linmusic.core.ui.components.CoverPlaceholder
import com.lin0721.linmusic.core.ui.components.LoginBottomSheet
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectSheet
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.ui.components.WebViewLoginScreen
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.EntityCoverShape
import com.lin0721.linmusic.core.ui.components.EntityRow
import com.lin0721.linmusic.core.ui.components.EntityRowData
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.DiscoverySectionSkeleton
import com.lin0721.linmusic.core.ui.components.SearchResultRowSkeleton
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.core.ui.theme.ScreenSlideDurationMs
import com.lin0721.linmusic.feature.playlist.ui.PlaylistSongOptionsSheet
import com.lin0721.linmusic.feature.search.domain.HotSearch
import com.lin0721.linmusic.feature.search.domain.PlaylistTag
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchSuggestion
import com.lin0721.linmusic.feature.search.domain.SearchType
import org.koin.androidx.compose.koinViewModel

private val tagFallbackColors = listOf(
    Color(0xFFE13300),
    Color(0xFF1E3264),
    Color(0xFF148A08),
    Color(0xFF8D67AB),
    Color(0xFFE8115B),
    Color(0xFF537AA1),
    Color(0xFFE91429),
    Color(0xFF477D95),
    Color(0xFF7D4B32),
    Color(0xFF1A6B52),
)

private const val ANIM_DURATION = 300
private const val ANIM_EXIT_DURATION = 150

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = koinViewModel(),
    autoFocus: Boolean = false,
    onOpenSidebar: () -> Unit = {},
    onPlaylistClick: (id: Long, isAlbum: Boolean) -> Unit = { _, _ -> },
    onArtistClick: (id: Long) -> Unit = {},
    onPlaylistCategoryClick: (category: String) -> Unit = {}
) {
    val discoveryState by viewModel.discoveryState.collectAsStateWithLifecycle()
    val inputState by viewModel.inputState.collectAsStateWithLifecycle()
    val isSearchActive by viewModel.isSearchActive.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val currentTrack by viewModel.playerManager.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val likedSongIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val collectState by viewModel.collectState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // 每个 Tab 各自持有滚动位置，切换 Tab 时不丢失浏览进度
    val resultListStates = remember { SearchType.entries.associateWith { LazyListState() } }

    var optionsTrack by remember { mutableStateOf<Track?>(null) }
    var collectSongId by remember { mutableStateOf<Long?>(null) }
    var showLoginSheet by remember { mutableStateOf(false) }
    var showWebViewLogin by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(autoFocus) {
        if (autoFocus) viewModel.activateSearch()
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) focusRequester.requestFocus()
    }

    // 搜索态是页面内部状态，不在导航栈里；系统返回手势要先退出搜索态回到发现页，
    // 而不是直接被外层全局 BackHandler 接住退回首页
    BackHandler(enabled = isSearchActive) {
        viewModel.deactivateSearch()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        focusManager.clearFocus()
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (userProfile != null) {
                AsyncImage(
                    model = "${userProfile!!.avatarUrl}?param=200y200",
                    contentDescription = "打开侧边栏",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .pressable(MelodiaPress.Icon) { onOpenSidebar() }
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    Icons.Rounded.AccountCircle,
                    contentDescription = "打开侧边栏",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(36.dp)
                        .pressable(MelodiaPress.Icon) {
                            ToastManager.showToast("请先在主页登录以显示侧边栏哦！")
                        }
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "搜索",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            MelodiaIconButton(onClick = { /* 听歌识曲，暂不实现 */ }) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = "听歌识曲",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 网易返回的真实推荐词，仅用于兜底触发搜索；未成功加载时为 null，不能拿占位文案当关键词去搜
        val remoteDefaultKeyword = (discoveryState as? DiscoveryUiState.Success)?.defaultKeyword
        val defaultKeywordText = remoteDefaultKeyword ?: "搜索你想听的"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.md)
                .padding(bottom = MelodiaSpacing.sm)
                .height(36.dp)
                .clip(RoundedCornerShape(PillRadius))
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (!isSearchActive) Modifier.clickable { viewModel.activateSearch() }
                    else Modifier
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            if (isSearchActive) {
                BasicTextField(
                    value = inputState.query,
                    onValueChange = { viewModel.updateQuery(it) },
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        // 联想词下拉开着时按回车/搜索键，视为采用第一条联想词（自动填充）；
                        // 输入框还空着（没打字）时，退到搜索框占位显示的推荐词，对齐点这两处的直觉预期
                        val keyword = when {
                            inputState.isSuggesting && inputState.suggestions.isNotEmpty() -> inputState.suggestions.first().keyword
                            inputState.query.isNotBlank() -> inputState.query
                            else -> remoteDefaultKeyword
                        }
                        if (!keyword.isNullOrBlank()) viewModel.searchWithKeyword(keyword)
                        focusManager.clearFocus()
                    }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (inputState.query.isEmpty()) {
                                Text(defaultKeywordText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )
                AnimatedVisibility(
                    visible = inputState.query.isNotEmpty(),
                    enter = fadeIn(tween(ANIM_EXIT_DURATION)),
                    exit = fadeOut(tween(ANIM_EXIT_DURATION))
                ) {
                    MelodiaIconButton(onClick = { viewModel.updateQuery("") }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "清空搜索框",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = defaultKeywordText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val showResults = isSearchActive && inputState.query.isNotBlank()
            AnimatedContent(
                targetState = showResults,
                transitionSpec = {
                    fadeIn(tween(ANIM_DURATION, easing = FastOutSlowInEasing))
                        .togetherWith(fadeOut(tween(ANIM_EXIT_DURATION)))
                },
                label = "discovery_results_switch"
            ) { resultsVisible ->
                if (resultsVisible) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SearchTypeTabRow(selectedType = selectedType, onSelect = { viewModel.selectType(it) })
                        AnimatedContent(
                            targetState = selectedType,
                            transitionSpec = {
                                fadeIn(tween(ANIM_DURATION, easing = FastOutSlowInEasing))
                                    .togetherWith(fadeOut(tween(ANIM_EXIT_DURATION)))
                            },
                            label = "search_type_tab_switch"
                        ) { type ->
                            val resultsState by viewModel.resultsByType.getValue(type).collectAsStateWithLifecycle()
                            SearchResultsList(
                                state = resultsState,
                                type = type,
                                listState = resultListStates.getValue(type),
                                currentTrackId = currentTrack?.mediaId,
                                isPlaying = isPlaying,
                                likedSongIds = likedSongIds,
                                isLoggedIn = userProfile != null,
                                onSongClick = { viewModel.playSong(it) },
                                onAlbumClick = { id -> onPlaylistClick(id, true) },
                                onArtistClick = onArtistClick,
                                onPlaylistClick = { id -> onPlaylistClick(id, false) },
                                onLoadMore = { viewModel.loadMore() },
                                onRetry = { viewModel.retrySearch() },
                                onLikeClick = { songId ->
                                    collectSongId = songId
                                    viewModel.prepareCollectDialog(songId)
                                },
                                onOpenMoreOptions = { track ->
                                    if (userProfile == null) showLoginSheet = true else optionsTrack = track
                                }
                            )
                        }
                    }
                } else {
                    DiscoveryContent(
                        state = discoveryState,
                        history = history,
                        onHistoryClick = { viewModel.searchWithKeyword(it) },
                        onClearHistory = { viewModel.clearHistory() },
                        onHotSearchClick = { viewModel.searchWithKeyword(it) },
                        onPlaylistTagClick = onPlaylistCategoryClick,
                        onRetry = { viewModel.retryDiscovery() }
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isSearchActive && inputState.isSuggesting && inputState.suggestions.isNotEmpty(),
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(ANIM_EXIT_DURATION)) + shrinkVertically(tween(ANIM_EXIT_DURATION))
            ) {
                SuggestionDropdown(
                    suggestions = inputState.suggestions,
                    onClick = { viewModel.searchWithKeyword(it) }
                )
            }
        }

        optionsTrack?.let { track ->
            PlaylistSongOptionsSheet(
                track = track,
                isLiked = track.id in likedSongIds,
                isLoggedIn = userProfile != null,
                onDismiss = { optionsTrack = null },
                onAddToPlayNext = { viewModel.addTrackToPlayNext(it) },
                onToggleLike = { songId, like -> viewModel.toggleLikeSong(songId, like) },
                onCollectClick = { songId ->
                    collectSongId = songId
                    viewModel.prepareCollectDialog(songId)
                },
                onArtistClick = onArtistClick,
                onAlbumClick = { id -> onPlaylistClick(id, true) },
                onRequireLogin = { showLoginSheet = true }
            )
        }

        collectSongId?.let { songId ->
            PlaylistCollectSheet(
                songId = songId,
                collectState = collectState,
                onDismiss = { collectSongId = null },
                onSaveCollection = { id, items -> viewModel.savePlaylistCollection(id, items) },
                onSaveNewCollection = { name, id -> viewModel.createPlaylistAndAddSong(name, id) }
            )
        }

        if (showLoginSheet) {
            LoginBottomSheet(
                onDismiss = { showLoginSheet = false },
                onWebLogin = {
                    showLoginSheet = false
                    showWebViewLogin = true
                },
                onLoginSuccess = { cookies ->
                    showLoginSheet = false
                    viewModel.handleLoginSuccess(cookies)
                }
            )
        }

        AnimatedVisibility(
            visible = showWebViewLogin,
            enter = slideInVertically(tween(ScreenSlideDurationMs)) { it } + fadeIn(tween(ScreenSlideDurationMs)),
            exit = slideOutVertically(tween(ScreenSlideDurationMs)) { it } + fadeOut(tween(ScreenSlideDurationMs))
        ) {
            WebViewLoginScreen(
                onClose = { showWebViewLogin = false },
                onLoginSuccess = { cookies ->
                    showWebViewLogin = false
                    viewModel.handleLoginSuccess(cookies)
                }
            )
        }
    }
}

@Composable
private fun SearchTypeTabRow(selectedType: SearchType, onSelect: (SearchType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md)
            .padding(bottom = MelodiaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
    ) {
        SearchType.entries.forEach { type ->
            val isSelected = type == selectedType
            Box(
                modifier = Modifier
                    .pressable(MelodiaPress.Pill) { onSelect(type) }
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .semantics {
                        role = Role.Tab
                        selected = isSelected
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = type.label,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun SuggestionDropdown(
    suggestions: List<SearchSuggestion>,
    onClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        suggestions.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(suggestion.keyword) }
                    .padding(horizontal = MelodiaSpacing.md, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = suggestion.keyword,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val SearchResultItem.stableKey: String
    get() = when (this) {
        is SearchResultItem.SongItem -> "song_${track.id}"
        is SearchResultItem.AlbumItem -> "album_${album.id}"
        is SearchResultItem.ArtistItem -> "artist_${artist.id}"
        is SearchResultItem.PlaylistItem -> "playlist_${playlist.id}"
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
                                onClick = { onSongClick(track) },
                                trailingSlot = {
                                    if (isLoggedIn && track.id in likedSongIds) {
                                        MelodiaIconButton(
                                            onClick = { onLikeClick(track.id) },
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

@Composable
private fun DiscoveryContent(
    state: DiscoveryUiState,
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onHotSearchClick: (String) -> Unit,
    onPlaylistTagClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        DiscoveryUiState.Loading -> {
            Column(modifier = Modifier.fillMaxSize().padding(top = MelodiaSpacing.md)) {
                DiscoverySectionSkeleton()
                Spacer(Modifier.height(MelodiaSpacing.lg))
                DiscoverySectionSkeleton()
            }
        }
        is DiscoveryUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(message = state.message, onRetry = onRetry)
            }
        }
        is DiscoveryUiState.Success -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm),
                contentPadding = PaddingValues(
                    start = MelodiaSpacing.md,
                    end = MelodiaSpacing.md,
                    top = MelodiaSpacing.sm,
                    bottom = LocalBottomOverlayInset.current + 16.dp
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                if (history.isNotEmpty()) {
                    item(key = "history_header", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MelodiaSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "搜索历史",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            MelodiaIconButton(onClick = onClearHistory) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "清除搜索历史",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    items(history, key = { "history_$it" }) { keyword ->
                        HistoryChip(keyword = keyword, onClick = { onHistoryClick(keyword) })
                    }
                }

                if (state.hotSearches.isNotEmpty()) {
                    item(key = "hot_header", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader("热搜榜")
                    }
                    val topHot = state.hotSearches.take(10)
                    itemsIndexed(topHot, key = { index, _ -> "hot_$index" }) { index, item ->
                        HotSearchCompactItem(
                            rank = index + 1,
                            item = item,
                            onClick = { onHotSearchClick(item.keyword) }
                        )
                    }
                }

                if (state.playlistTags.isNotEmpty()) {
                    item(key = "tags_header", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader("精品歌单")
                    }
                    itemsIndexed(state.playlistTags, key = { index, _ -> "tag_$index" }) { index, tag ->
                        PlaylistTagCard(
                            tag = tag,
                            fallbackColor = tagFallbackColors[index % tagFallbackColors.size],
                            onClick = { onPlaylistTagClick(tag.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryChip(keyword: String, onClick: () -> Unit) {
    Text(
        text = keyword,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = MelodiaSpacing.sm)
    )
}

@Composable
private fun PlaylistTagCard(
    tag: PlaylistTag,
    fallbackColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .pressable(MelodiaPress.Card, onClick = onClick)
            .clip(RoundedCornerShape(RadiusCompact))
            .background(if (tag.coverUrl.isBlank()) fallbackColor else MaterialTheme.colorScheme.surface)
    ) {
        if (tag.coverUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = tag.coverUrl,
                contentDescription = tag.name,
                contentScale = ContentScale.Crop,
                loading = { CoverPlaceholder() },
                error = { CoverPlaceholder() },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )
        }

        Text(
            text = tag.name,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HotSearchCompactItem(
    rank: Int,
    item: HotSearch,
    onClick: () -> Unit
) {
    val isTop3 = rank <= 3

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            color = if (isTop3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isTop3) FontWeight.Bold else FontWeight.Normal,
            fontSize = 15.sp,
            modifier = Modifier.width(20.dp)
        )
        Text(
            text = item.keyword,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isTop3) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!item.iconUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(4.dp))
            AsyncImage(
                model = item.iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(12.dp).widthIn(max = 32.dp)
            )
        }
    }
}
