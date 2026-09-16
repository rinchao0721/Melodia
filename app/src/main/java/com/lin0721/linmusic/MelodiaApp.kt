package com.lin0721.linmusic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.ui.components.PlaylistCollectSheet
import com.lin0721.linmusic.core.ui.components.ProfileSidebar
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.update.UpdateManager
import com.lin0721.linmusic.core.update.UpdateUiState
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.settings.ui.UpdateDialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val SidebarWidth = 310.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MelodiaApp() {
    val viewModel: HomeViewModel = koinViewModel()
    val settingsPreferences: SettingsPreferences = koinInject()
    val showCreateEntry by settingsPreferences.showCreateEntry.collectAsStateWithLifecycle(initialValue = true)
    val currentTrack by viewModel.playerManager.currentTrack.collectAsStateWithLifecycle()
    val previousQueueItem by viewModel.playerManager.previousQueueItem.collectAsStateWithLifecycle()
    val nextQueueItem by viewModel.playerManager.nextQueueItem.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsStateWithLifecycle()
    // 迷你播放条按钮专用：弱网缓冲期间也要立刻显示"暂停中"图标，不能等音频真正流出的 isPlaying
    val miniPlayerShowPause by viewModel.playerManager.playWhenReady.collectAsStateWithLifecycle()
    val currentPositionState = viewModel.playerManager.currentPosition.collectAsStateWithLifecycle()
    val currentPositionProvider = { currentPositionState.value }
    val duration by viewModel.playerManager.duration.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val collectState by viewModel.collectState.collectAsStateWithLifecycle()
    val likedSongIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val isMiniPlayerLiked = currentTrack?.mediaId?.toLongOrNull()?.let { it in likedSongIds } ?: false

    val playerSheet = rememberMelodiaPlayerSheetState()
    val navigation = rememberMelodiaNavigationState()
    val sidebar = rememberMelodiaSidebarState(SidebarWidth)

    var showCreateSheet by remember { mutableStateOf(false) }
    // 网页登录界面可见性状态
    var isLoginScreenVisible by remember { mutableStateOf(false) }
    // MV 播放页是否处于全屏态：全屏时隐藏底部导航栏/悬浮播放条，避免盖住视频
    var isMvFullscreen by remember { mutableStateOf(false) }
    // MV 播放页评论区是否展开：展开时临时隐藏悬浮 MiniPlayer，为评论区和输入栏让出空间
    var isMvCommentsOpen by remember { mutableStateOf(false) }
    // 悬浮播放卡片 + 导航栏的实际高度，下发给各页面用作列表底部留白
    var bottomOverlayHeight by remember { mutableStateOf(0.dp) }
    // mini 栏爱心按钮触发的"收藏到歌单"弹层，非 null 时显示
    var miniCollectSongId by remember { mutableStateOf<Long?>(null) }

    val hazeState = remember { HazeState() }
    val density = LocalDensity.current

    val toastMessage = rememberGlobalToastMessage()

    fun handleBack() {
        val shouldReopenPlayer = navigation.navigateBack()
        if (shouldReopenPlayer) {
            playerSheet.animateTo(true, 0f)
        }
    }

    // 系统返回键与侧滑返回拦截：按优先级关闭浮层或返回上一级
    // activeTab != Home 时即使当前 tab 栈深为 1，也需要交给 handleBack() 退回主页 tab，而不是转给系统
    val isAnyOverlayOpen = navigation.isNavigatingFromPlayer || playerSheet.isOpen || sidebar.isOpen ||
            showCreateSheet || navigation.showMusicNewWorks || navigation.canNavigateBack ||
            navigation.activeTab != Screen.Home

    BackHandler(enabled = isAnyOverlayOpen) {
        when {
            navigation.isNavigatingFromPlayer -> handleBack()
            playerSheet.isOpen -> {
                navigation.resetPlayerNavigation()
                playerSheet.animateTo(false, 0f)
            }
            sidebar.isOpen -> sidebar.close()
            showCreateSheet -> showCreateSheet = false
            navigation.showMusicNewWorks -> navigation.updateShowMusicNewWorks(false)
            navigation.canNavigateBack || navigation.activeTab != Screen.Home -> handleBack()
        }
    }

    val isDrawerDraggable = userProfile != null && (sidebar.isOpen || sidebar.isTouchStartingAtEdge)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { playerSheet.onScreenSizeChanged(it.height.toFloat()) }
            .pointerInput(sidebar.isOpen) {
                val edgeWidthPx = with(density) { 32.dp.toPx() }
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.firstOrNull { it.changedToDown() }
                        if (down != null) {
                            sidebar.onPointerDown(down.position.x, edgeWidthPx)
                        }
                        if (event.changes.all { !it.pressed }) {
                            sidebar.onPointerUp()
                        }
                    }
                }
            }
            .anchoredDraggable(
                state = sidebar.draggableState,
                orientation = Orientation.Horizontal,
                enabled = isDrawerDraggable
            )
            .background(BackgroundDark)
    ) {
        // 1. 侧边栏层 (位于最底层或同步移动)
        userProfile?.let { profile ->
            Box(
                modifier = Modifier
                    .width(SidebarWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = sidebar.offsetX - sidebar.widthPx
                        alpha = 0.5f + (0.5f * sidebar.progress)
                    }
            ) {
                ProfileSidebar(
                    userProfile = profile,
                    onLogout = {
                        viewModel.logout()
                        sidebar.close()
                    },
                    onDismiss = { sidebar.close() },
                    onNavigateToProfile = { uid ->
                        sidebar.close()
                        navigation.openProfile(uid)
                    },
                    onNavigateToRecentPlay = { navigation.openRecentPlay() },
                    onNavigateToListenData = { navigation.openListenData() },
                    onNavigateToCloud = { navigation.openCloud() },
                    onNavigateToMessage = { navigation.openMessage() },
                    onNavigateToAccount = { navigation.openAccount() },
                    onNavigateToSettings = { navigation.navigateTo(Screen.Settings) }
                )
            }
        }

        // 2. 主页面内容层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = sidebar.offsetX
                    clip = true
                    shape = RoundedCornerShape((sidebar.progress * 32).dp)
                    shadowElevation = (sidebar.progress * 30f)
                }
                .background(BackgroundDark)
        ) {
            CompositionLocalProvider(
                LocalBottomOverlayInset provides bottomOverlayHeight,
                LocalGlobalOverlayOpen provides (playerSheet.isOpen || sidebar.isOpen || showCreateSheet)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (playerSheet.isOpen) Modifier.haze(hazeState) else Modifier)
                ) {
                    MelodiaNavHost(
                        currentScreen = navigation.currentScreen,
                        homeViewModel = viewModel,
                        homeTab = navigation.homeTab,
                        showMusicNewWorks = navigation.showMusicNewWorks,
                        searchAutoFocus = navigation.searchAutoFocus,
                        onOpenSidebar = { sidebar.open() },
                        onLoginScreenVisibilityChanged = { isLoginScreenVisible = it },
                        onNavigateToPlaylist = { id, isAlbum -> navigation.openPlaylist(id, isAlbum) },
                        onNavigateToArtist = { id -> navigation.openArtist(id) },
                        onNavigateToRadio = { id -> navigation.openRadio(id) },
                        onNavigateToMv = { id, name -> navigation.openMvPlayer(id, name) },
                        onMvFullscreenChanged = { isMvFullscreen = it },
                        onMvCommentsVisibilityChanged = { isMvCommentsOpen = it },
                        onNavigateToPlaylistCategory = { category -> navigation.openPlaylistCategory(category) },
                        onNavigateToProfile = { uid -> navigation.openProfile(uid) },
                        onNavigateToFollowList = { uid, mode -> navigation.openFollowList(uid, mode) },
                        onHomeTabSelected = { navigation.selectHomeTab(it) },
                        onShowMusicNewWorksChanged = { navigation.updateShowMusicNewWorks(it) },
                        onNavigateToSearch = { navigation.openSearch(autoFocus = true) },
                        onBack = { handleBack() }
                    )

                    // 创建菜单遮罩
                    if (showCreateSheet) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                                .pressable(MelodiaPress.None) { showCreateSheet = false }
                        )
                    }

                    // 放置在应用了平移 graphicsLayer 的主 Box 内部的底部
                    MelodiaBottomOverlay(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        currentScreen = navigation.currentScreen,
                        showCreateSheet = showCreateSheet,
                        isLoginScreenVisible = isLoginScreenVisible,
                        isMvFullscreen = isMvFullscreen,
                        isMvCommentsOpen = isMvCommentsOpen,
                        currentTrack = currentTrack,
                        isPlaying = miniPlayerShowPause,
                        currentPositionProvider = currentPositionProvider,
                        duration = duration,
                        hazeState = hazeState,
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onNext = { viewModel.playerManager.playNext() },
                        onMiniPlayerClick = { playerSheet.animateTo(true, 0f) },
                        onMiniPlayerDrag = { delta -> playerSheet.onDrag(delta) },
                        onMiniPlayerDragEnd = { velocity -> playerSheet.onDragEnd(velocity) },
                        previousQueueItem = previousQueueItem,
                        nextQueueItem = nextQueueItem,
                        onMiniPlayerPrevious = { viewModel.playerManager.skipToPrevious() },
                        onCancelPendingSkip = { viewModel.playerManager.cancelPendingSkip() },
                        isMiniPlayerLiked = isMiniPlayerLiked,
                        onMiniPlayerLikeClick = {
                            val songId = currentTrack?.mediaId?.toLongOrNull()
                            if (songId != null) {
                                miniCollectSongId = songId
                                viewModel.prepareCollectDialog(songId)
                            }
                        },
                        onCreateDismiss = { showCreateSheet = false },
                        onNavigate = { navigation.openTab(it) },
                        onCreateClick = { showCreateSheet = !showCreateSheet },
                        showCreateEntry = showCreateEntry,
                        onOverlayHeightChanged = { bottomOverlayHeight = it }
                    )

                    // 侧边栏打开时的遮罩与点击收起事件
                    if (sidebar.progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f * sidebar.progress))
                                .pressable(
                                    style = MelodiaPress.None,
                                    enabled = sidebar.isOpen,
                                    onClick = { sidebar.close() }
                                )
                        )
                    }
                }
            }
        }

        MelodiaFullPlayerOverlay(
            currentTrack = currentTrack,
            screenHeightPx = playerSheet.screenHeightPx,
            isPlayerOpen = playerSheet.isOpen,
            playerOffsetY = playerSheet.offsetY,
            isPlaying = isPlaying,
            currentPositionProvider = currentPositionProvider,
            duration = duration,
            onTogglePlay = { viewModel.togglePlayPause() },
            onSeek = { viewModel.playerManager.seekTo(it) },
            onClose = {
                navigation.resetPlayerNavigation()
                playerSheet.animateTo(false, 0f)
            },
            onDragClose = { offset, velocity ->
                navigation.resetPlayerNavigation()
                playerSheet.animateTo(false, velocity, offset)
            },
            onArtistClick = { artistId ->
                navigation.navigateFromPlayer {
                    navigation.openArtist(artistId)
                }
                playerSheet.animateTo(false, 0f)
            },
            onAlbumClick = { albumId ->
                navigation.navigateFromPlayer {
                    navigation.openPlaylist(albumId, isAlbum = true)
                }
                playerSheet.animateTo(false, 0f)
            },
            onNavigateToProfile = { uid ->
                navigation.navigateFromPlayer {
                    navigation.openProfile(uid)
                }
                playerSheet.animateTo(false, 0f)
            },
            modifier = Modifier.zIndex(1f)
        )

        // 5. 全局自定义 Toast 提示
        MelodiaToastHost(toastMessage = toastMessage)

        // 6. 全局更新弹窗，任意页面均可弹出
        val updateManager: UpdateManager = koinInject()
        val updateState by updateManager.uiState.collectAsStateWithLifecycle()
        val isDialogVisible by updateManager.isDialogVisible.collectAsStateWithLifecycle()
        if (isDialogVisible && updateState !is UpdateUiState.Idle) {
            UpdateDialog(
                state = updateState,
                onDismiss = { updateManager.dismiss() },
                onIgnore = { updateManager.ignoreCurrentVersion() },
                onStartDownload = { updateManager.startDownload() },
                onInstall = { updateManager.retryInstall() }
            )
        }

        // 7. mini 栏爱心按钮触发的"收藏到歌单"弹层，跟全屏播放页共用同一个组件
        miniCollectSongId?.let { songId ->
            PlaylistCollectSheet(
                songId = songId,
                collectState = collectState,
                onDismiss = { miniCollectSongId = null },
                onSaveCollection = { id, items -> viewModel.savePlaylistCollection(id, items) },
                onSaveNewCollection = { name, id -> viewModel.createPlaylistAndAddSong(name, id) }
            )
        }
    }
}

// 订阅全局 Toast 事件，展示 2 秒后自动清空
@Composable
private fun rememberGlobalToastMessage(): String? {
    var message by remember { mutableStateOf<String?>(null) }
    var trigger by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        ToastManager.toastFlow.collect { msg ->
            message = msg
            trigger++
        }
    }

    LaunchedEffect(trigger) {
        if (message != null) {
            kotlinx.coroutines.delay(2000)
            message = null
        }
    }

    return message
}
