package com.lin0721.linmusic.feature.search.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalGlobalOverlayOpen
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.LoginBottomSheet
import com.lin0721.linmusic.core.ui.components.PlaylistCollectSheet
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.components.WebViewLoginScreen
import com.lin0721.linmusic.core.ui.theme.ScreenSlideDurationMs
import com.lin0721.linmusic.feature.playlist.ui.PlaylistSongOptionsSheet
import com.lin0721.linmusic.feature.search.domain.SearchSuggestion
import com.lin0721.linmusic.feature.search.domain.SearchType
import org.koin.androidx.compose.koinViewModel

private const val ANIM_DURATION = 250
private const val ANIM_EXIT_DURATION = 150

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = koinViewModel(),
    autoFocus: Boolean = false,
    onOpenSidebar: () -> Unit = {},
    onPlaylistClick: (id: Long, isAlbum: Boolean) -> Unit = { _, _ -> },
    onArtistClick: (id: Long) -> Unit = {},
    onPlaylistCategoryClick: (category: String) -> Unit = {},
    onOpenRecognition: () -> Unit = {}
) {
    val discoveryState by viewModel.discoveryState.collectAsStateWithLifecycle()
    val featuredTrack by viewModel.featuredTrack.collectAsStateWithLifecycle()
    val inputState by viewModel.inputState.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val currentTrack by viewModel.playerManager.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val likedSongIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val collectState by viewModel.collectState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // 每个 Tab 各自持有滚动位置，切换 Tab 时不丢失浏览进度
    val resultListStates = remember { SearchType.entries.associateWith { LazyListState() } }

    var optionsTrack by remember { mutableStateOf<Track?>(null) }
    var collectSongId by remember { mutableStateOf<Long?>(null) }
    var showLoginSheet by remember { mutableStateOf(false) }
    var showWebViewLogin by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    LaunchedEffect(autoFocus) {
        if (autoFocus) viewModel.activateSearch()
    }

    LaunchedEffect(mode) {
        when (mode) {
            SearchMode.Typing -> focusRequester.requestFocus()
            SearchMode.Results, SearchMode.Discovery -> focusManager.clearFocus()
        }
    }

    // 搜索态是页面内部状态，不在导航栈里；返回手势先回发现页，而不是被外层全局 BackHandler 接住退回首页。
    // 全屏播放器/侧边栏/创建菜单开着时要让位，否则会抢先吞掉本该用来关浮层的返回事件
    BackHandler(enabled = mode != SearchMode.Discovery && !LocalGlobalOverlayOpen.current) {
        viewModel.cancelSearch()
    }

    // 网易返回的真实推荐词，仅用于兜底触发搜索；未成功加载时为 null，不能拿占位文案当关键词去搜
    val remoteDefaultKeyword = (discoveryState as? DiscoveryUiState.Success)?.defaultKeyword
    val placeholder = remoteDefaultKeyword ?: "搜索你想听的"

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
        SearchTopBar(
            mode = mode,
            query = inputState.query,
            placeholder = placeholder,
            avatarUrl = userProfile?.avatarUrl,
            focusRequester = focusRequester,
            onQueryChange = viewModel::updateQuery,
            onSubmit = {
                // 联想已加载时回车采用第一条联想关键词；输入框为空时退到占位显示的推荐词
                val keyword = inputState.currentSuggestions.firstOrNull { it is SearchSuggestion.Keyword }?.text
                    ?: inputState.query.takeIf { it.isNotBlank() }
                    ?: remoteDefaultKeyword
                if (!keyword.isNullOrBlank()) viewModel.searchWithKeyword(keyword)
            },
            onActivate = viewModel::activateSearch,
            onFieldFocused = viewModel::editQuery,
            onCancel = viewModel::cancelSearch,
            onOpenSidebar = onOpenSidebar,
            onAvatarClickLoggedOut = { ToastManager.showToast("请先在主页登录以显示侧边栏哦！") },
            onOpenRecognition = onOpenRecognition
        )

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    fadeIn(tween(ANIM_DURATION, easing = FastOutSlowInEasing))
                        .togetherWith(fadeOut(tween(ANIM_EXIT_DURATION)))
                },
                label = "search_mode_switch"
            ) { targetMode ->
                when (targetMode) {
                    SearchMode.Discovery -> SearchDiscoveryContent(
                        state = discoveryState,
                        featuredTrack = featuredTrack,
                        onHotSearchClick = viewModel::searchWithKeyword,
                        onPlayFeatured = viewModel::playFeaturedTrack,
                        onPlaylistTagClick = onPlaylistCategoryClick,
                        onRetry = viewModel::retryDiscovery
                    )
                    SearchMode.Typing -> SearchTypingPanel(
                        query = inputState.query,
                        suggestions = inputState.currentSuggestions,
                        history = history,
                        onSubmitKeyword = viewModel::searchWithKeyword,
                        onFillQuery = viewModel::updateQuery,
                        onArtistClick = onArtistClick,
                        onAlbumClick = { id -> onPlaylistClick(id, true) },
                        onRemoveHistory = viewModel::removeHistory,
                        onClearHistory = viewModel::clearHistory
                    )
                    SearchMode.Results -> SearchResultsContent(
                        selectedType = selectedType,
                        resultsByType = viewModel.resultsByType,
                        listStates = resultListStates,
                        currentTrackId = currentTrack?.mediaId,
                        isPlaying = isPlaying,
                        likedSongIds = likedSongIds,
                        isLoggedIn = userProfile != null,
                        onSelectType = viewModel::selectType,
                        onSongClick = viewModel::playSong,
                        onAlbumClick = { id -> onPlaylistClick(id, true) },
                        onArtistClick = onArtistClick,
                        onPlaylistClick = { id -> onPlaylistClick(id, false) },
                        onLoadMore = viewModel::loadMore,
                        onRetry = viewModel::retrySearch,
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
