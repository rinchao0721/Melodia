package com.lin0721.linmusic.feature.home.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.ui.components.LoginBottomSheet
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.components.WebViewLoginScreen
import com.lin0721.linmusic.feature.music.ui.MusicContent
import com.lin0721.linmusic.feature.music.ui.MusicViewModel
import com.lin0721.linmusic.feature.newworks.ui.NewWorksFeedContent
import com.lin0721.linmusic.feature.newworks.ui.NewWorksViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastContent
import com.lin0721.linmusic.feature.podcast.ui.PodcastViewModel
import org.koin.androidx.compose.koinViewModel

private const val TAB_ALL = 0
private const val TAB_MUSIC = 1
private const val TAB_PODCAST = 2

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    musicViewModel: MusicViewModel = koinViewModel(),
    podcastViewModel: PodcastViewModel = koinViewModel(),
    newWorksViewModel: NewWorksViewModel = koinViewModel(),
    selectedTab: Int = TAB_ALL,
    onTabSelected: (Int) -> Unit = {},
    // 音乐 tab「最新」二级药丸的选中态：由 MelodiaNavigationState 持有，
    // 避免从新作 feed 点进详情页再返回时（HomeScreen 被销毁重建）状态丢失回到曲风浏览
    showNewWorksFeed: Boolean = false,
    onShowNewWorksFeedChanged: (Boolean) -> Unit = {},
    onPlaylistClick: (Long, Boolean) -> Unit = { _, _ -> },
    onArtistClick: (Long) -> Unit = {},
    onRadioClick: (Long) -> Unit = {},
    onMvClick: (Long, String) -> Unit = { _, _ -> },
    onSearchClick: () -> Unit = {},
    onOpenSidebar: () -> Unit = {},
    onLoginScreenVisibilityChanged: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val musicUiState by musicViewModel.uiState.collectAsStateWithLifecycle()
    val podcastUiState by podcastViewModel.uiState.collectAsStateWithLifecycle()
    val newWorksUiState by newWorksViewModel.uiState.collectAsStateWithLifecycle()

    var showLoginSheet by remember { mutableStateOf(false) }
    var showWebViewLogin by remember { mutableStateOf(false) }

    // 监听网页登录界面可见性变化，并通知上层以隐藏悬浮底栏
    LaunchedEffect(showWebViewLogin) {
        onLoginScreenVisibilityChanged(showWebViewLogin)
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { message ->
            ToastManager.showToast(message)
        }
    }

    LaunchedEffect(musicViewModel) {
        musicViewModel.toastEvent.collect { message ->
            ToastManager.showToast(message)
        }
    }

    // 各 tab 的数据都等真正切过去才拉，避免拖慢「全部」的首屏
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            TAB_MUSIC -> musicViewModel.loadIfNeeded()
            TAB_PODCAST -> podcastViewModel.loadIfNeeded()
        }
    }

    LaunchedEffect(showNewWorksFeed) {
        if (showNewWorksFeed) newWorksViewModel.loadIfNeeded()
    }

    val onAvatarClick: () -> Unit = {
        if (userProfile != null) {
            onOpenSidebar()
        } else {
            showLoginSheet = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏跨 tab 只渲染一次，切 tab 时不会被重建，FilterPills 的展开动画状态才能保留
            HomeSharedHeader(
                userProfile = userProfile,
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                secondarySelected = showNewWorksFeed,
                onSecondarySelected = { onShowNewWorksFeedChanged(true) },
                onAvatarClick = onAvatarClick,
                onSearchClick = onSearchClick
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    selectedTab == TAB_MUSIC && showNewWorksFeed -> NewWorksFeedContent(
                        uiState = newWorksUiState,
                        onMvClick = onMvClick,
                        onAlbumClick = { id -> onPlaylistClick(id, true) },
                        onSongPlay = { newWorksViewModel.playRelease(it) },
                        onRetry = { newWorksViewModel.load() },
                        onLoadMore = { newWorksViewModel.loadMore() }
                    )

                    selectedTab == TAB_MUSIC -> MusicContent(
                        uiState = musicUiState,
                        onStyleSelect = { musicViewModel.selectStyle(it) },
                        onChildStyleSelect = { musicViewModel.selectChildStyle(it) },
                        onPlaylistClick = { onPlaylistClick(it.id, false) },
                        onArtistClick = { onArtistClick(it.id) },
                        onPlaySongAt = { musicViewModel.playSongAt(it) },
                        onPlayFavourite = {
                            (musicUiState as? com.lin0721.linmusic.feature.music.ui.MusicUiState.Success)
                                ?.data?.content?.head?.favouriteSong
                                ?.let { musicViewModel.playFavouriteSong(it) }
                        },
                        onRetry = { musicViewModel.loadStyles() }
                    )

                    selectedTab == TAB_PODCAST -> PodcastContent(
                        uiState = podcastUiState,
                        onCategorySelect = { podcastViewModel.selectCategory(it) },
                        onProgramClick = { podcastViewModel.playProgramAt(it) },
                        onRadioClick = { onRadioClick(it.id) },
                        onRetry = { podcastViewModel.loadFeed() }
                    )

                    else -> HomeContent(
                        uiState = uiState,
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshHomeData() },
                        onPlaylistClick = onPlaylistClick,
                        onSongClick = { song -> viewModel.playShelfSong(song) },
                        onVoiceClick = { voice -> viewModel.playShelfVoice(voice) },
                        onRetry = { viewModel.loadHomeData() },
                        onLoadMore = { viewModel.loadMoreShelves() },
                        onIntelligenceClick = { viewModel.startIntelligenceMode() },
                        onRoamingClick = { viewModel.startRoaming() }
                    )
                }
            }
        }

        // 登录相关的弹窗保持在最顶层
        if (showLoginSheet) {
            LoginBottomSheet(
                onDismiss = { showLoginSheet = false },
                onWebLogin = {
                    showLoginSheet = false
                    showWebViewLogin = true
                },
            )
        }

        if (showWebViewLogin) {
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
