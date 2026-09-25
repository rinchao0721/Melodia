package com.lin0721.linmusic.feature.player.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.lin0721.linmusic.core.comment.domain.CommentFloorState
import com.lin0721.linmusic.core.comment.ui.CommentFloorScreen
import com.lin0721.linmusic.core.comment.ui.CommentFullScreen
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.ScreenSlideDurationMs
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import com.lin0721.linmusic.core.download.ui.DownloadQualityPickerSheet
import com.lin0721.linmusic.core.player.rememberQueueItemCoverUrl
import com.lin0721.linmusic.core.ui.components.SwipeToSkipCover
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.FallbackBackdropPalette
import com.lin0721.linmusic.core.ui.theme.MelodiaOrientationClass
import com.lin0721.linmusic.core.ui.theme.PaletteMemoryCache
import com.lin0721.linmusic.core.ui.theme.PanelReflowFadeFromAlpha
import com.lin0721.linmusic.core.ui.theme.PanelReflowFadeSpec
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.extractBackdropPaletteFromUrl
import com.lin0721.linmusic.core.ui.theme.melodiaNavigationBarBottomPadding
import com.lin0721.linmusic.core.ui.theme.melodiaStatusBarTopPadding
import com.lin0721.linmusic.core.ui.theme.rememberMelodiaOrientationClass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

// 侧栏→全屏铺开到这个进度时竖排列表淡出完毕，宽屏两栏从这里开始淡入
private const val WideCrossfadeSplit = 0.6f

// 竖屏全屏时内容列占卡片宽度的比例
private const val PortraitFullscreenColumnFraction = 0.62f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    currentTrack: MediaItem?,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    isPlayerOpen: Boolean,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onNavigateToProfile: (Long) -> Unit = {},
    onDragClose: (Float, Float) -> Unit = { _, _ -> },
    // 手机全屏：封面按可用高度收缩，快捷操作行需让开导航栏
    fitCoverAboveNavigationBar: Boolean = false,
    // 以下为平板常驻面板用，手机全屏保持默认值
    // 封面按可用高度收缩，保证首屏完整显示到快捷操作行
    fitCoverToViewport: Boolean = false,
    // 播放内容列的最大宽度，超出部分只铺背景、内容列居中
    contentMaxWidth: Dp = Dp.Unspecified,
    onToggleSidebarFullscreen: (() -> Unit)? = null,
    isSidebarFullscreen: Boolean = false,
    // 侧栏→全屏的铺开进度（0 侧栏、1 全屏）与全屏时的卡片宽度。横屏全屏换成宽屏两栏排版，
    // 铺开过程中两套排版各按最终宽度排一次、交叉淡入淡出；竖屏全屏沿用竖排列表，按比例放宽并居中
    sidebarFullscreenProgress: (() -> Float)? = null,
    fullscreenContentWidth: Dp = Dp.Unspecified
) {
    if (currentTrack == null) return

    val context = LocalContext.current
    val viewModel: PlayerViewModel = koinViewModel()
    val songDetailState by viewModel.songDetailState.collectAsStateWithLifecycle()
    val songDetail = songDetailState.songDetail
    // 大播放按钮专用：弱网缓冲期间也要立刻显示"暂停中"图标，不能等音频真正流出的 isPlaying；
    // 歌词区/顶栏/队列等其他地方仍按严格的 isPlaying 判断，不受影响
    val playWhenReady by viewModel.playerManager.playWhenReady.collectAsStateWithLifecycle()
    val currentLyricIndex by viewModel.currentLyricIndex.collectAsStateWithLifecycle()
    val playContext by viewModel.playerManager.playContext.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val activeQuality by viewModel.activeQuality.collectAsStateWithLifecycle()
    val collectState by viewModel.collectState.collectAsStateWithLifecycle()
    val playMode by viewModel.playerManager.playMode.collectAsStateWithLifecycle()
    val queue by viewModel.playerManager.queue.collectAsStateWithLifecycle()
    val currentQueueIndex by viewModel.playerManager.currentIndex.collectAsStateWithLifecycle()
    val currentQueueItem by viewModel.playerManager.currentQueueItem.collectAsStateWithLifecycle()
    val previousQueueItem by viewModel.playerManager.previousQueueItem.collectAsStateWithLifecycle()
    val nextQueueItem by viewModel.playerManager.nextQueueItem.collectAsStateWithLifecycle()
    var showQueueSheet by remember { mutableStateOf(false) }
    var isLyricsFullScreen by remember { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    var showTimerSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showCommentFloor by remember { mutableStateOf(false) }
    val composerState by viewModel.composerState.collectAsStateWithLifecycle()
    val floorState by viewModel.floorState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    var collectSongId by remember { mutableStateOf<Long?>(null) }
    var showOutputDeviceSheet by remember { mutableStateOf(false) }
    var showDownloadQualitySheet by remember { mutableStateOf(false) }
    var showCardEditorSheet by remember { mutableStateOf(false) }
    val cardLayout by viewModel.fullPlayerCardLayout.collectAsStateWithLifecycle()
    val connectedDevice = rememberCurrentOutputDevice()

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { message ->
            ToastManager.showToast(message)
        }
    }

    BackHandler(enabled = showQueueSheet) {
        showQueueSheet = false
    }

    BackHandler(enabled = isLyricsFullScreen) {
        isLyricsFullScreen = false
    }

    BackHandler(enabled = showMoreOptionsSheet) {
        showMoreOptionsSheet = false
    }

    BackHandler(enabled = showTimerSheet) {
        showTimerSheet = false
    }

    BackHandler(enabled = showCommentsSheet) {
        showCommentsSheet = false
    }

    BackHandler(enabled = showCommentFloor) {
        showCommentFloor = false
    }

    BackHandler(enabled = showOutputDeviceSheet) {
        showOutputDeviceSheet = false
    }

    BackHandler(enabled = showCardEditorSheet) {
        showCardEditorSheet = false
    }

    BackHandler(enabled = showDownloadQualitySheet) {
        showDownloadQualitySheet = false
    }

    // 宽屏两栏的歌词区当前是否在组合中（滑到卡片区后移出）
    var isWideLyricsVisible by remember { mutableStateOf(false) }

    // 全屏歌词与宽屏歌词区的逐字滚动都需要更密的进度回调
    DisposableEffect(isLyricsFullScreen || isWideLyricsVisible, isPlaying) {
        if ((isLyricsFullScreen || isWideLyricsVisible) && isPlaying) {
            viewModel.playerManager.setPositionUpdateInterval(50L)
        } else {
            viewModel.playerManager.setPositionUpdateInterval(1000L)
        }
        onDispose {
            viewModel.playerManager.setPositionUpdateInterval(1000L)
        }
    }

    var colorPalette by remember(currentTrack.mediaId) {
        mutableStateOf(
            PaletteMemoryCache.get(currentTrack.mediaId) ?: FallbackBackdropPalette
        )
    }
    val colors = rememberFullPlayerColors(colorPalette)

    val title = currentTrack.mediaMetadata.title?.toString() ?: ""
    val artist = currentTrack.mediaMetadata.artist?.toString() ?: ""
    val rawCoverUrl = currentTrack.mediaMetadata.artworkUri?.toString()
        ?.replace("?param=300y300", "").orEmpty()
    val fallbackCoverUrl = currentQueueItem?.let {
        rememberQueueItemCoverUrl(it.coverUrl, it.songId, it.localUri).replace("?param=300y300", "")
    }.orEmpty()
    val coverUrl = rawCoverUrl.ifBlank { fallbackCoverUrl }
    // 预览封面解析
    val previousCoverUrl = previousQueueItem?.let {
        rememberQueueItemCoverUrl(it.coverUrl, it.songId, it.localUri).replace("?param=300y300", "")
    }
    val nextCoverUrl = nextQueueItem?.let {
        rememberQueueItemCoverUrl(it.coverUrl, it.songId, it.localUri).replace("?param=300y300", "")
    }

    // 取色跟封面显示解码完全脱钩，单独发一次固定尺寸的请求；放在宿主里，竖排与宽屏两栏排版共用一份结果
    LaunchedEffect(coverUrl) {
        if (coverUrl.isNotEmpty()) {
            val palette = extractBackdropPaletteFromUrl(context, coverUrl)
            colorPalette = palette
            PaletteMemoryCache.put(currentTrack.mediaId, palette)
        }
    }

    // 歌名/歌手要跟封面一起冻结：队列已经先切过去、currentTrack 还没跟上时，
    // 直接用实时值会让封面下方的文字在滑动/切歌过程中先于封面硬跳
    val previousKeyStr = previousQueueItem?.songId?.toString()
    val nextKeyStr = nextQueueItem?.songId?.toString()
    var displayedTitle by remember { mutableStateOf(title) }
    var displayedArtist by remember { mutableStateOf(artist) }
    if ((previousKeyStr != null && previousKeyStr == currentTrack.mediaId) ||
        (nextKeyStr != null && nextKeyStr == currentTrack.mediaId)
    ) {
        // 队列已经先切过去，曲目数据还没跟上，先保留原文字，等 currentTrack 落地后再刷新
    } else {
        displayedTitle = title
        displayedArtist = artist
    }

    fun shareCurrentSong() {
        val shareText = "《$title》- $artist https://music.163.com/song?id=${currentTrack.mediaId}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "分享歌曲"))
    }

    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val scrollMetrics = rememberFullPlayerScrollMetrics(listState)
    val density = LocalDensity.current
    val navigationBarBottomPadding = melodiaNavigationBarBottomPadding()
    val coverFitInsetPx = rememberCoverFitInsetPx(
        listState = listState,
        enabled = fitCoverToViewport || fitCoverAboveNavigationBar,
        bottomReservePx = if (fitCoverAboveNavigationBar) with(density) { navigationBarBottomPadding.toPx() } else 0f
    )
    val coverExtraInset = with(density) { coverFitInsetPx.toDp() }
    val allCardsHidden = cardLayout.none { it.visible }
    val fillTail = MelodiaSpacing.md + navigationBarBottomPadding
    val fillGap = rememberFullPlayerFillGap(
        listState = listState,
        enabled = allCardsHidden,
        gapCount = FullPlayerFillGapCount,
        contentKeys = FullPlayerPlaybackItemKeys,
        bottomReserve = fillTail
    )

    val isLandscape = rememberMelodiaOrientationClass() == MelodiaOrientationClass.Landscape
    val fullscreenProgress: () -> Float = { sidebarFullscreenProgress?.invoke()?.coerceIn(0f, 1f) ?: 0f }
    val canUseWideLayout = sidebarFullscreenProgress != null && isLandscape && fullscreenContentWidth.isSpecified
    val showColumnLayout by remember(canUseWideLayout) {
        derivedStateOf { !canUseWideLayout || fullscreenProgress() < WideCrossfadeSplit }
    }
    val showWideLayout by remember(canUseWideLayout) {
        derivedStateOf { canUseWideLayout && fullscreenProgress() > WideCrossfadeSplit }
    }
    // 竖屏全屏：铺开到位后竖排列表放宽到卡片宽度的一定比例并居中，这一帧重排用一次轻微淡入盖住
    val fullscreenColumnMaxWidth = if (fullscreenContentWidth.isSpecified) {
        fullscreenContentWidth * PortraitFullscreenColumnFraction
    } else {
        Dp.Unspecified
    }
    val isColumnFullscreenWidth by remember(sidebarFullscreenProgress, isLandscape) {
        derivedStateOf { sidebarFullscreenProgress != null && !isLandscape && fullscreenProgress() >= 1f }
    }
    val columnReflowAlpha = remember { Animatable(1f) }
    var hasSettledColumnWidth by remember { mutableStateOf(false) }
    LaunchedEffect(isColumnFullscreenWidth) {
        if (hasSettledColumnWidth) {
            columnReflowAlpha.snapTo(PanelReflowFadeFromAlpha)
            columnReflowAlpha.animateTo(1f, PanelReflowFadeSpec)
        }
        hasSettledColumnWidth = true
    }

    // 手势状态同时被内联逻辑和嵌套滚动连接读写，持有 MutableState 本体便于透传
    val offsetYState = remember { mutableStateOf(0f) }
    var offsetY by offsetYState
    val isScrollGestureActiveState = remember { mutableStateOf(false) }
    var isScrollGestureActive by isScrollGestureActiveState
    val isGestureStartedAtTopState = remember { mutableStateOf(true) }
    var isGestureStartedAtTop by isGestureStartedAtTopState

    LaunchedEffect(isPlayerOpen) {
        if (isPlayerOpen) {
            offsetY = 0f
            isScrollGestureActive = false
            isGestureStartedAtTop = true
            isLyricsFullScreen = false
        }
    }

    var screenHeightPx by remember { mutableStateOf(0f) }
    val topCornerRadius by remember {
        derivedStateOf {
            if (offsetY > 0f) 24.dp else 0.dp
        }
    }
    val coroutineScope = rememberCoroutineScope()
    var dragReleaseJob by remember { mutableStateOf<Job?>(null) }

    // 松手后决定关闭播放器还是回弹，从列表中途开始的手势要求更严
    fun handleDragRelease(velocity: Float = 0f) {
        dragReleaseJob?.cancel()
        dragReleaseJob = coroutineScope.launch {
            val shouldClose = if (isGestureStartedAtTop) {
                offsetY > screenHeightPx * 0.10f || velocity > 450f
            } else {
                offsetY > screenHeightPx * 0.20f
            }

            if (offsetY > 0f && shouldClose) {
                val finalOffset = offsetY
                offsetY = 0f
                onDragClose(finalOffset, velocity)
            } else {
                animate(
                    initialValue = offsetY,
                    targetValue = 0f,
                    initialVelocity = velocity,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { value, _ ->
                    offsetY = value.coerceAtLeast(0f)
                }
            }
        }
    }

    val nestedScrollConnection = rememberFullPlayerNestedScrollConnection(
        listState = listState,
        offsetYState = offsetYState,
        isScrollGestureActiveState = isScrollGestureActiveState,
        isGestureStartedAtTopState = isGestureStartedAtTopState,
        onDragRelease = { handleDragRelease(velocity = it) }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenHeightPx = it.height.toFloat() }
            .nestedScroll(nestedScrollConnection)
            .graphicsLayer {
                translationY = offsetY
            }
            .clip(RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius))
            .background(MaterialTheme.colorScheme.background)
    ) {
        PlayerBackdrop(
            base = colors.base,
            mode = BackdropMode.Collapsed,
            translationYProvider = { if (showColumnLayout) scrollMetrics.backgroundTranslationY else 0f }
        )

        // 竖排列表：手机、侧栏与竖屏全屏。横屏铺开时贴右固定不动、随进度淡出，让位给宽屏两栏
        if (showColumnLayout) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val crossfade = if (canUseWideLayout) {
                            (1f - fullscreenProgress() / WideCrossfadeSplit).coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                        alpha = crossfade * columnReflowAlpha.value
                    }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .align(if (canUseWideLayout) Alignment.TopEnd else Alignment.TopCenter)
                        .fillMaxHeight()
                        .widthIn(max = if (isColumnFullscreenWidth) fullscreenColumnMaxWidth else contentMaxWidth)
                        .fillMaxWidth()
                        .haze(hazeState),
                    contentPadding = PaddingValues(
                        top = melodiaStatusBarTopPadding(),
                        bottom = (if (allCardsHidden) MelodiaSpacing.md else 80.dp) + melodiaNavigationBarBottomPadding()
                    )
                ) {
                    fullPlayerPlaybackSection(
                        songState = songDetailState,
                        colors = colors,
                        coverUrl = coverUrl,
                        previousCoverUrl = previousCoverUrl,
                        nextCoverUrl = nextCoverUrl,
                        onSwipeToPrevious = viewModel.playerManager::skipToPrevious,
                        onCancelSwipe = viewModel.playerManager::cancelPendingSkip,
                        currentKey = currentTrack.mediaId,
                        previousKey = previousQueueItem?.songId?.toString(),
                        nextKey = nextQueueItem?.songId?.toString(),
                        coverExtraInset = coverExtraInset,
                        onToggleSidebarFullscreen = onToggleSidebarFullscreen,
                        isSidebarFullscreen = isSidebarFullscreen,
                        title = displayedTitle,
                        artist = displayedArtist,
                        playContext = playContext,
                        currentLyricIndex = currentLyricIndex,
                        isPlaying = isPlaying,
                        playWhenReady = playWhenReady,
                        currentPositionProvider = currentPositionProvider,
                        duration = duration,
                        playMode = playMode,
                        onClose = onClose,
                        onMoreClick = { showMoreOptionsSheet = true },
                        onToggleLike = viewModel::toggleLike,
                        onArtistClick = {
                            songDetail?.ar?.firstOrNull()?.id?.let { id ->
                                onArtistClick(id)
                            }
                        },
                        onSeek = onSeek,
                        onTogglePlay = onTogglePlay,
                        onPlayNext = viewModel.playerManager::playNext,
                        onPlayPrevious = viewModel.playerManager::playPrevious,
                        onToggleShuffle = viewModel.playerManager::toggleShuffle,
                        onToggleRepeat = viewModel.playerManager::toggleRepeat,
                        onDisableRoaming = { viewModel.playerManager.disableRoaming() },
                        onDisableIntelligence = { viewModel.playerManager.disableIntelligence() },
                        onOutputDeviceClick = { showOutputDeviceSheet = true },
                        onQueueClick = { showQueueSheet = true },
                        onShareClick = { shareCurrentSong() },
                        connectedDevice = connectedDevice,
                        fillGap = if (allCardsHidden) fillGap else null,
                        fillTail = fillTail
                    )

                    fullPlayerInfoSection(
                        songState = songDetailState,
                        colors = colors,
                        commentsState = commentsState,
                        currentLyricIndex = currentLyricIndex,
                        cardLayout = cardLayout,
                        onOpenFullScreenLyrics = { isLyricsFullScreen = true },
                        onCommentsClick = { showCommentsSheet = true },
                        onRetryComments = viewModel::retryComments,
                        onFollowArtistClick = { artistId -> viewModel.toggleArtistFollow(artistId) },
                        onArtistClick = onArtistClick,
                        onEditCardsClick = { showCardEditorSheet = true },
                        onAlbumClick = onAlbumClick,
                        onSelectArtist = { index -> viewModel.selectArtist(index) }
                    )
                }

                FullPlayerTopBar(
                    onClose = onClose,
                    title = title,
                    artist = artist,
                    showTitle = scrollMetrics.showTitleInBar,
                    isPlaying = isPlaying,
                    onTogglePlay = onTogglePlay,
                    isLiked = songDetailState.isLiked,
                    onToggleLike = viewModel::toggleLike,
                    backgroundColor = colors.base,
                    currentPositionProvider = currentPositionProvider,
                    duration = duration,
                    onArtistClick = {
                        songDetail?.ar?.firstOrNull()?.id?.let { id ->
                            onArtistClick(id)
                        }
                    },
                    modifier = Modifier.draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            offsetY = (offsetY + delta).coerceAtLeast(0f)
                        },
                        onDragStarted = {
                            isGestureStartedAtTop = true
                        },
                        onDragStopped = { velocity ->
                            handleDragRelease(velocity = velocity)
                        }
                    )
                )
            }
        }

        // 宽屏两栏：按全屏最终宽度排一次、贴右放置，铺开过程中卡片左边缘逐步露出，同时淡入
        if (showWideLayout) {
            FullPlayerWideLayout(
                sourceBar = { barModifier ->
                    FullPlayerSourceBar(
                        playContext = playContext,
                        onClose = onClose,
                        onMoreClick = { showMoreOptionsSheet = true },
                        onToggleSidebarFullscreen = onToggleSidebarFullscreen,
                        isSidebarFullscreen = isSidebarFullscreen,
                        modifier = barModifier
                    )
                },
                cover = { coverModifier ->
                    SwipeToSkipCover(
                        coverUrl = coverUrl,
                        previousCoverUrl = previousCoverUrl,
                        nextCoverUrl = nextCoverUrl,
                        onConfirmPrevious = viewModel.playerManager::skipToPrevious,
                        onConfirmNext = viewModel.playerManager::playNext,
                        currentKey = currentTrack.mediaId,
                        previousKey = previousQueueItem?.songId?.toString(),
                        nextKey = nextQueueItem?.songId?.toString(),
                        contentScale = ContentScale.Crop,
                        shape = RoundedCornerShape(RadiusCompact),
                        elevation = 24.dp,
                        modifier = coverModifier,
                        onCancelPending = viewModel.playerManager::cancelPendingSkip
                    )
                },
                playbackControls = {
                    SongInfo(
                        title = displayedTitle,
                        artist = displayedArtist,
                        isLiked = songDetailState.isLiked,
                        onToggleLike = viewModel::toggleLike,
                        onArtistClick = {
                            songDetail?.ar?.firstOrNull()?.id?.let { id -> onArtistClick(id) }
                        }
                    )
                    ProgressSection(
                        currentPositionProvider = currentPositionProvider,
                        duration = if (duration > 0L) duration else (songDetail?.dt ?: 0L),
                        onSeek = onSeek
                    )
                    PlaybackControls(
                        isPlaying = playWhenReady,
                        onTogglePlay = onTogglePlay,
                        onPlayNext = viewModel.playerManager::playNext,
                        onPlayPrevious = viewModel.playerManager::playPrevious,
                        onToggleShuffle = viewModel.playerManager::toggleShuffle,
                        onToggleRepeat = viewModel.playerManager::toggleRepeat,
                        playMode = playMode,
                        isRoaming = playContext == "similar_roaming",
                        onDisableRoaming = { viewModel.playerManager.disableRoaming() },
                        isIntelligence = playContext == "intelligence",
                        onDisableIntelligence = { viewModel.playerManager.disableIntelligence() }
                    )
                    ActionButtons(
                        onOutputDeviceClick = { showOutputDeviceSheet = true },
                        onQueueClick = { showQueueSheet = true },
                        onShareClick = { shareCurrentSong() },
                        connectedDevice = connectedDevice
                    )
                },
                lyrics = songDetailState.lyrics,
                isLyricsLoading = songDetailState.isLyricsLoading,
                currentLyricIndex = currentLyricIndex,
                highlightColor = colors.textHighlight,
                currentPositionProvider = currentPositionProvider,
                isPlaying = isPlaying,
                onLyricClick = { line -> viewModel.seekToTime(line.timeMs) },
                onLyricsVisibleChange = { isWideLyricsVisible = it },
                infoCards = {
                    fullPlayerInfoGrid(
                        songState = songDetailState,
                        colors = colors,
                        commentsState = commentsState,
                        cardLayout = cardLayout,
                        onCommentsClick = { showCommentsSheet = true },
                        onRetryComments = viewModel::retryComments,
                        onFollowArtistClick = { artistId -> viewModel.toggleArtistFollow(artistId) },
                        onArtistClick = onArtistClick,
                        onAlbumClick = onAlbumClick,
                        onSelectArtist = { index -> viewModel.selectArtist(index) },
                        onEditCardsClick = { showCardEditorSheet = true }
                    )
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .wrapContentWidth(align = Alignment.End, unbounded = true)
                    .width(fullscreenContentWidth)
                    .padding(
                        top = melodiaStatusBarTopPadding(),
                        bottom = melodiaNavigationBarBottomPadding()
                    )
                    .graphicsLayer {
                        alpha = ((fullscreenProgress() - WideCrossfadeSplit) / (1f - WideCrossfadeSplit))
                            .coerceIn(0f, 1f)
                    }
            )
        }

        FullPlayerLyricsOverlay(
            visible = isLyricsFullScreen,
            songState = songDetailState,
            colors = colors,
            currentLyricIndex = currentLyricIndex,
            title = title,
            artist = artist,
            hazeState = hazeState,
            isPlaying = isPlaying,
            currentPositionProvider = currentPositionProvider,
            duration = duration,
            playMode = playMode,
            onSeek = { timeMs ->
                viewModel.seekToTime(timeMs)
            },
            onClose = { isLyricsFullScreen = false },
            onTogglePlay = onTogglePlay,
            onPlayNext = viewModel.playerManager::playNext,
            onPlayPrevious = viewModel.playerManager::playPrevious,
            onToggleShuffle = viewModel.playerManager::toggleShuffle,
            onToggleRepeat = viewModel.playerManager::toggleRepeat,
            onMoreClick = { showMoreOptionsSheet = true }
        )

        FullPlayerSheets(
            songState = songDetailState,
            showQueueSheet = showQueueSheet,
            showMoreOptionsSheet = showMoreOptionsSheet,
            collectSongId = collectSongId,
            collectState = collectState,
            showTimerSheet = showTimerSheet,
            showOutputDeviceSheet = showOutputDeviceSheet,
            queue = queue,
            currentQueueIndex = currentQueueIndex,
            playMode = playMode,
            playContext = playContext,
            isPlaying = isPlaying,
            title = title,
            artist = artist,
            coverUrl = coverUrl,
            sleepTimerRemaining = sleepTimerRemaining,
            activeQuality = activeQuality,
            onPlayAtIndex = { viewModel.playerManager.playAtIndex(it) },
            onRemoveAtIndex = { viewModel.playerManager.removeFromQueue(it) },
            onMoveQueueItem = { from, to -> viewModel.playerManager.moveInQueue(from, to) },
            onToggleShuffle = viewModel.playerManager::toggleShuffle,
            onClearQueue = {
                viewModel.clearQueue()
                showQueueSheet = false
            },
            onDisableRoaming = { viewModel.playerManager.disableRoaming() },
            onDisableIntelligence = { viewModel.playerManager.disableIntelligence() },
            onQueueDismiss = { showQueueSheet = false },
            onToggleLike = viewModel::toggleLike,
            onAlbumClick = {
                val albumId = songDetail?.al?.id ?: 0L
                if (albumId > 0L) {
                    showMoreOptionsSheet = false
                    onAlbumClick(albumId)
                } else {
                    ToastManager.showToast("未找到专辑信息")
                }
            },
            onArtistClick = {
                val artistId = songDetail?.ar?.firstOrNull()?.id ?: 0L
                if (artistId > 0L) {
                    showMoreOptionsSheet = false
                    onArtistClick(artistId)
                } else {
                    ToastManager.showToast("未找到歌手信息")
                }
            },
            onShowTimerClick = {
                showTimerSheet = true
            },
            onQualitySelected = viewModel::updateQuality,
            onToggleIntelligence = { checked ->
                if (checked) {
                    val songId = currentTrack.mediaId.toLongOrNull() ?: 0L
                    viewModel.startIntelligenceMode(songId, title, artist, coverUrl)
                } else {
                    viewModel.playerManager.disableIntelligence()
                }
            },
            onStartSimilarRoaming = {
                val songId = currentTrack.mediaId.toLongOrNull() ?: 0L
                viewModel.startSimilarSongsRoaming(songId, title, artist, coverUrl)
            },
            onInsertSimilarSongs = {
                val songId = currentTrack.mediaId.toLongOrNull() ?: 0L
                viewModel.insertSimilarSongs(songId)
            },
            onCollectClick = {
                val songId = currentTrack.mediaId.toLongOrNull()
                if (songId != null) {
                    collectSongId = songId
                    viewModel.prepareCollectDialog(songId)
                }
            },
            onShareClick = { shareCurrentSong() },
            onDownloadClick = { showDownloadQualitySheet = true },
            onSaveCollection = { songId, items -> viewModel.savePlaylistCollection(songId, items) },
            onSaveNewCollection = { name, songId -> viewModel.createPlaylistAndAddSong(name, songId) },
            onCollectDismiss = { collectSongId = null },
            onMoreOptionsDismiss = { showMoreOptionsSheet = false },
            onSetTimer = { minutes ->
                viewModel.setSleepTimer(minutes)
                showTimerSheet = false
            },
            onTimerDismiss = { showTimerSheet = false },
            onOutputDeviceSelected = { deviceId -> viewModel.playerManager.setPreferredAudioDevice(deviceId) },
            onOutputDeviceDismiss = { showOutputDeviceSheet = false }
        )

        AnimatedVisibility(
            visible = showCommentsSheet,
            enter = slideInVertically(tween(ScreenSlideDurationMs)) { it } + fadeIn(tween(ScreenSlideDurationMs)),
            exit = slideOutVertically(tween(ScreenSlideDurationMs)) { it } + fadeOut(tween(ScreenSlideDurationMs))
        ) {
            CommentFullScreen(
                commentsState = commentsState,
                currentUserId = userProfile?.uid,
                composerState = composerState,
                onBack = { showCommentsSheet = false },
                onSortChange = viewModel::changeCommentSort,
                onLikeComment = viewModel::likeComment,
                onUserClick = { uid ->
                    showCommentsSheet = false
                    onNavigateToProfile(uid)
                },
                onExpandFloor = { comment ->
                    showCommentFloor = true
                    viewModel.openCommentFloor(comment)
                },
                onDeleteClick = { comment -> viewModel.deleteCommentItem(comment) },
                onSubmitComment = { content, target ->
                    if (target != null) {
                        viewModel.submitCommentReply(target.commentId, content)
                    } else {
                        viewModel.submitComment(content)
                    }
                },
                onRequireLogin = { ToastManager.showToast("请先登录账号") },
                onLoadMore = viewModel::loadMoreComments,
                onRetry = { viewModel.retryComments() }
            )
        }

        AnimatedVisibility(
            visible = showCommentFloor,
            enter = slideInVertically(tween(ScreenSlideDurationMs)) { it } + fadeIn(tween(ScreenSlideDurationMs)),
            exit = slideOutVertically(tween(ScreenSlideDurationMs)) { it } + fadeOut(tween(ScreenSlideDurationMs))
        ) {
            CommentFloorScreen(
                floorState = floorState,
                currentUserId = userProfile?.uid,
                composerState = composerState,
                onBack = { showCommentFloor = false },
                onLoadMore = viewModel::loadMoreCommentFloor,
                onSubmitReply = { parentCommentId, content ->
                    viewModel.submitCommentReply(parentCommentId, content)
                },
                onRequireLogin = { ToastManager.showToast("请先登录账号") },
                onDeleteClick = { comment -> viewModel.deleteCommentItem(comment) },
                onLikeClick = { comment -> viewModel.likeComment(comment) },
                onRetry = { (floorState as? CommentFloorState.Success)?.ownerComment?.let(viewModel::openCommentFloor) },
                onUserClick = onNavigateToProfile
            )
        }

        if (showDownloadQualitySheet) {
            DownloadQualityPickerSheet(
                songId = currentTrack.mediaId.toLongOrNull(),
                maxDownloadLevel = songDetail?.privilege?.dlLevel,
                onQualitySelected = { level ->
                    val songId = currentTrack.mediaId.toLongOrNull() ?: 0L
                    val artistNames = songDetail?.ar?.joinToString("/") { it.name }?.takeIf { it.isNotBlank() } ?: artist
                    val albumName = songDetail?.al?.name ?: ""
                    val albumYear = com.lin0721.linmusic.core.download.yearFromEpochMillis(songDetail?.publishTime ?: 0)
                    viewModel.downloadCurrentSong(songId, title, artistNames, albumName, coverUrl, albumYear, level)
                    showDownloadQualitySheet = false
                },
                onDismiss = { showDownloadQualitySheet = false }
            )
        }

        if (showCardEditorSheet) {
            FullPlayerCardEditorSheet(
                layout = cardLayout,
                onLayoutChange = viewModel::saveFullPlayerCardLayout,
                onDismiss = { showCardEditorSheet = false }
            )
        }
    }
}
