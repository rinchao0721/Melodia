package com.lin0721.linmusic.feature.home.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.ScreenSlideDurationMs
import com.lin0721.linmusic.core.ui.theme.uiTween
import com.lin0721.linmusic.feature.music.ui.MusicContent
import com.lin0721.linmusic.feature.music.ui.MusicViewModel
import com.lin0721.linmusic.feature.newworks.ui.NewWorksFeedActions
import com.lin0721.linmusic.feature.newworks.ui.NewWorksFeedContent
import com.lin0721.linmusic.feature.newworks.ui.NewWorksViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastHomeActions
import com.lin0721.linmusic.feature.podcast.ui.PodcastHomeContent
import com.lin0721.linmusic.feature.podcast.ui.PodcastHomeViewModel
import org.koin.androidx.compose.koinViewModel

internal const val TAB_ALL = 0
internal const val TAB_MUSIC = 1
internal const val TAB_PODCAST = 2

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    musicViewModel: MusicViewModel = koinViewModel(),
    podcastViewModel: PodcastHomeViewModel = koinViewModel(),
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
    onPodcastSubscribedClick: () -> Unit = {},
    onPodcastToplistClick: () -> Unit = {},
    onPodcastCategoryClick: (id: Long, name: String) -> Unit = { _, _ -> },
    onStyleClick: (id: Long, name: String) -> Unit = { _, _ -> },
    onSearchClick: () -> Unit = {},
    onOpenSidebar: () -> Unit = {},
    onLoginScreenVisibilityChanged: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val musicUiState by musicViewModel.uiState.collectAsStateWithLifecycle()
    val podcastState by podcastViewModel.state.collectAsStateWithLifecycle()
    val newWorksUiState by newWorksViewModel.uiState.collectAsStateWithLifecycle()
    val newWorksCollectState by newWorksViewModel.collectState.collectAsStateWithLifecycle()
    val newWorksImportState by newWorksViewModel.importState.collectAsStateWithLifecycle()
    val newWorksStatus by newWorksViewModel.releaseStatus.collectAsStateWithLifecycle()

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

    LaunchedEffect(newWorksViewModel) {
        newWorksViewModel.toastEvent.collect { message ->
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
            .background(MelodiaColors.PageFill)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏跨 tab 只渲染一次，切 tab 时不会被重建，FilterPills 的展开动画状态才能保留
            HomeSharedHeader(
                userProfile = userProfile,
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                secondarySelected = showNewWorksFeed,
                onSecondarySelected = { onShowNewWorksFeedChanged(true) },
                onAvatarClick = onAvatarClick
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    selectedTab == TAB_MUSIC && showNewWorksFeed -> NewWorksFeedContent(
                        uiState = newWorksUiState,
                        collectState = newWorksCollectState,
                        importState = newWorksImportState,
                        status = newWorksStatus,
                        actions = remember(newWorksViewModel, onPlaylistClick) {
                            NewWorksFeedActions(
                                onAlbumClick = { id -> onPlaylistClick(id, true) },
                                onTogglePlay = newWorksViewModel::togglePlayRelease,
                                onToggleLibrary = newWorksViewModel::toggleInLibrary,
                                onAddToPlayNext = newWorksViewModel::addToPlayNext,
                                onPrepareCollect = newWorksViewModel::prepareCollectDialog,
                                onSaveCollection = newWorksViewModel::savePlaylistCollection,
                                onSaveNewCollection = newWorksViewModel::createPlaylistAndAddSong,
                                onPrepareImportTargets = newWorksViewModel::prepareImportTargets,
                                onAddToPlaylist = newWorksViewModel::addToPlaylist,
                                onCreatePlaylistAndAdd = newWorksViewModel::createPlaylistAndAdd,
                                onRetry = newWorksViewModel::load,
                                onLoadMore = newWorksViewModel::loadMore
                            )
                        }
                    )

                    selectedTab == TAB_MUSIC -> MusicContent(
                        uiState = musicUiState,
                        onStyleClick = onStyleClick,
                        onRetry = { musicViewModel.loadStyles() }
                    )

                    selectedTab == TAB_PODCAST -> PodcastHomeContent(
                        state = podcastState,
                        actions = remember(podcastViewModel, onRadioClick, onPodcastSubscribedClick, onPodcastToplistClick, onPodcastCategoryClick) {
                            PodcastHomeActions(
                                onFilterSelect = podcastViewModel::selectFilter,
                                onResume = podcastViewModel::resume,
                                onPickClick = podcastViewModel::playPicks,
                                onRadioClick = { onRadioClick(it.id) },
                                onOpenSubscribed = onPodcastSubscribedClick,
                                onOpenToplist = onPodcastToplistClick,
                                onOpenCategory = onPodcastCategoryClick,
                                onLoginClick = { showLoginSheet = true },
                                onRetryAll = podcastViewModel::refresh,
                                onRetrySubscribed = podcastViewModel::retrySubscribed,
                                onRetryPicks = podcastViewModel::retryPicks,
                                onRetryCategoryGroups = podcastViewModel::retryCategoryGroups,
                                onRetryToplist = podcastViewModel::retryToplistRadios,
                                onRetryCategoryRadios = podcastViewModel::retryCategoryRadios
                            )
                        }
                    )

                    else -> HomeContent(
                        uiState = uiState,
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshHomeData() },
                        onPlaylistClick = onPlaylistClick,
                        onSongClick = { shelfTitle, songs, song -> viewModel.playShelfSong(shelfTitle, songs, song) },
                        onVoiceClick = { shelfTitle, voices, voice -> viewModel.playShelfVoice(shelfTitle, voices, voice) },
                        onRetry = { viewModel.loadHomeData() },
                        onLoadMore = { viewModel.loadMoreShelves() },
                        onIntelligenceClick = { viewModel.startIntelligenceMode() },
                        onRoamingClick = { viewModel.startRoaming() },
                        onDailyPlay = { viewModel.playDailySong() }
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
                onLoginSuccess = { cookies ->
                    showLoginSheet = false
                    viewModel.handleLoginSuccess(cookies)
                }
            )
        }

        AnimatedVisibility(
            visible = showWebViewLogin,
            enter = slideInVertically(uiTween(ScreenSlideDurationMs)) { it } + fadeIn(uiTween(ScreenSlideDurationMs)),
            exit = slideOutVertically(uiTween(ScreenSlideDurationMs)) { it } + fadeOut(uiTween(ScreenSlideDurationMs))
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
