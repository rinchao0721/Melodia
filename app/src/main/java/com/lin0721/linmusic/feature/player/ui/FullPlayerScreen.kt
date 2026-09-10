package com.lin0721.linmusic.feature.player.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.FallbackBackdropPalette
import com.lin0721.linmusic.core.ui.theme.PaletteMemoryCache
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

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
    onDragClose: (Float, Float) -> Unit = { _, _ -> }
) {
    if (currentTrack == null) return

    val context = LocalContext.current
    val viewModel: PlayerViewModel = koinViewModel()
    val songDetailState by viewModel.songDetailState.collectAsStateWithLifecycle()
    val songDetail = songDetailState.songDetail
    val currentLyricIndex by viewModel.currentLyricIndex.collectAsStateWithLifecycle()
    val playContext by viewModel.playerManager.playContext.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val activeQuality by viewModel.activeQuality.collectAsStateWithLifecycle()
    val collectState by viewModel.collectState.collectAsStateWithLifecycle()
    val playMode by viewModel.playerManager.playMode.collectAsStateWithLifecycle()
    val queue by viewModel.playerManager.queue.collectAsStateWithLifecycle()
    val currentQueueIndex by viewModel.playerManager.currentIndex.collectAsStateWithLifecycle()
    var showQueueSheet by remember { mutableStateOf(false) }
    var isLyricsFullScreen by remember { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    var showTimerSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var collectSongId by remember { mutableStateOf<Long?>(null) }
    var showOutputDeviceSheet by remember { mutableStateOf(false) }
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

    BackHandler(enabled = showOutputDeviceSheet) {
        showOutputDeviceSheet = false
    }

    // 全屏歌词逐字滚动需要更密的进度回调
    DisposableEffect(isLyricsFullScreen, isPlaying) {
        if (isLyricsFullScreen && isPlaying) {
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
    val coverUrl = currentTrack.mediaMetadata.artworkUri?.toString()
        ?.replace("?param=300y300", "") ?: ""

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
            translationYProvider = { scrollMetrics.backgroundTranslationY }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().haze(hazeState),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottom = 80.dp
            )
        ) {
            fullPlayerPlaybackSection(
                songState = songDetailState,
                colors = colors,
                coverUrl = coverUrl,
                title = title,
                artist = artist,
                playContext = playContext,
                currentLyricIndex = currentLyricIndex,
                isPlaying = isPlaying,
                currentPositionProvider = currentPositionProvider,
                duration = duration,
                playMode = playMode,
                onClose = onClose,
                onPaletteExtracted = {
                    colorPalette = it
                    PaletteMemoryCache.put(currentTrack.mediaId, it)
                },
                onMoreClick = { showMoreOptionsSheet = true },
                onToggleLike = viewModel::toggleLike,
                onArtistClick = {
                    songDetail?.ar?.firstOrNull()?.id?.let { id ->
                        onClose()
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
                connectedDevice = connectedDevice
            )

            fullPlayerInfoSection(
                songState = songDetailState,
                colors = colors,
                commentsState = commentsState,
                currentLyricIndex = currentLyricIndex,
                onOpenFullScreenLyrics = { isLyricsFullScreen = true },
                onCommentsClick = { showCommentsSheet = true },
                onRetryComments = viewModel::retryComments,
                onFollowArtistClick = { viewModel.toggleArtistFollow() },
                onArtistClick = onArtistClick
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
            onArtistClick = {
                songDetail?.ar?.firstOrNull()?.id?.let { id ->
                    onClose()
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
            showCommentsSheet = showCommentsSheet,
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
            commentsState = commentsState,
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
                    onClose()
                    onAlbumClick(albumId)
                } else {
                    ToastManager.showToast("未找到专辑信息")
                }
            },
            onArtistClick = {
                val artistId = songDetail?.ar?.firstOrNull()?.id ?: 0L
                if (artistId > 0L) {
                    onClose()
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
            onSaveCollection = { songId, items -> viewModel.savePlaylistCollection(songId, items) },
            onSaveNewCollection = { name, songId -> viewModel.createPlaylistAndAddSong(name, songId) },
            onCollectDismiss = { collectSongId = null },
            onMoreOptionsDismiss = { showMoreOptionsSheet = false },
            onSetTimer = { minutes ->
                viewModel.setSleepTimer(minutes)
                showTimerSheet = false
            },
            onTimerDismiss = { showTimerSheet = false },
            onLikeComment = viewModel::likeComment,
            onRetryComments = { viewModel.retryComments() },
            onCommentsDismiss = { showCommentsSheet = false },
            onOutputDeviceSelected = { deviceId -> viewModel.playerManager.setPreferredAudioDevice(deviceId) },
            onOutputDeviceDismiss = { showOutputDeviceSheet = false },
            onNavigateToProfile = { uid ->
                onClose()
                onNavigateToProfile(uid)
            }
        )
    }
}
