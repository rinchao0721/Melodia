package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.LoginBottomSheet
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.WebViewLoginScreen
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.comment.ui.CommentsBottomSheet
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.DraggableSongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import java.util.Collections
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: Long,
    isAlbum: Boolean = false,
    viewModel: PlaylistViewModel = koinViewModel(),
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit
) {
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTrack by viewModel.playerManager.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsStateWithLifecycle()
    val playMode by viewModel.playerManager.playMode.collectAsStateWithLifecycle()
    val playContext by viewModel.playerManager.playContext.collectAsStateWithLifecycle()
    val likedSongIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val collectState by viewModel.collectState.collectAsStateWithLifecycle()
    val userProfile  by viewModel.userProfile.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val historyRecommendState by viewModel.historyRecommendState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val addMusicSearchQuery by viewModel.addMusicSearchQuery.collectAsStateWithLifecycle()
    val addMusicSearchState by viewModel.addMusicSearchState.collectAsStateWithLifecycle()

    var showLoginSheet by remember { mutableStateOf(false) }
    var showWebViewLogin by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showMoreMenuSheet by remember { mutableStateOf(false) }
    var showImportTargetSheet by remember { mutableStateOf(false) }
    var showEditInfoDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddMusicSheet by remember { mutableStateOf(false) }
    var isReorderMode by remember { mutableStateOf(false) }
    var isSavingOrder by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    val reorderedTracks = remember { mutableStateListOf<Track>() }
    var selectedHistoryDate by remember { mutableStateOf("今天") }

    // reorderedTracks 是引用不变的 SnapshotStateList，remember(keys) 感知不到其内容变化，必须用 derivedStateOf 才能正确响应拖拽
    val isOrderChanged by remember {
        derivedStateOf {
            val current = (uiState as? PlaylistUiState.Success)?.playlist?.tracks.orEmpty()
            reorderedTracks.isNotEmpty() && reorderedTracks != current
        }
    }

    BackHandler(enabled = isReorderMode) {
        if (!isSavingOrder) {
            if (isOrderChanged) {
                showDiscardConfirmDialog = true
            } else {
                isReorderMode = false
            }
        }
    }

    LaunchedEffect(historyRecommendState.selectedDate) {
        selectedHistoryDate = historyRecommendState.selectedDate ?: "今天"
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { com.lin0721.linmusic.core.ui.components.ToastManager.showToast(it) }
    }
    LaunchedEffect(playlistId, isAlbum) {
        viewModel.loadPlaylist(playlistId, isAlbum)
        if (playlistId == -1L) {
            viewModel.loadHistoryDates()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = uiState) {
            is PlaylistUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            is PlaylistUiState.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加载失败", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(MelodiaSpacing.sm))
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Spacer(Modifier.height(MelodiaSpacing.md))
                        MelodiaButton(onClick = { viewModel.loadPlaylist(playlistId, isAlbum) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Text("重试", color = MaterialTheme.colorScheme.onPrimary)
                        }
                        MelodiaTextButton(onClick = onBack) { Text("返回", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            is PlaylistUiState.Success -> {
                val profile = userProfile
                val isOwnedPlaylist = profile != null &&
                    state.playlist.id > 0L &&
                    state.playlist.id != profile.uid &&
                    state.playlist.creator?.userId == profile.uid
                val isShuffleActive = playMode == PlayMode.SHUFFLE
                // 当前播放队列的来源是否就是这个歌单（playContext 存的是歌单名）
                val isThisPlaylistContext = playContext == state.playlist.name
                val isCurrentlyPlayingThis = isThisPlaylistContext && isPlaying

                if (isReorderMode) {
                    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val overlayHeight = 56.dp + statusBarHeight
                    val density = LocalDensity.current
                    val haptic = LocalHapticFeedback.current
                    val scope = rememberCoroutineScope()
                    val reorderListState = rememberLazyListState()

                    // 所有行结构一致，行高只随字体缩放整体变化，取任一已布局项的实测高度即可。
                    // 固定 60dp（44dp 封面 + 上下各 8dp）只作首帧兜底：字体放大后真实行高会超过它，
                    // 用估算值做位移补偿会让拖拽行每交换一次就相对手指漂移一点，长距离拖拽后明显脱手
                    val fallbackItemHeightPx = with(density) { 60.dp.toPx() }
                    var itemHeightPx by remember { mutableFloatStateOf(fallbackItemHeightPx) }
                    LaunchedEffect(reorderListState) {
                        snapshotFlow { reorderListState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0 }
                            .collect { if (it > 0) itemHeightPx = it.toFloat() }
                    }

                    val topEdgeThreshold = with(density) { 80.dp.toPx() }
                    val bottomEdgeThreshold = with(density) { 100.dp.toPx() }
                    val bottomOverlayInsetPx = with(density) { LocalBottomOverlayInset.current.toPx() }

                    var draggedIndex by remember { mutableIntStateOf(-1) }
                    var dragOffset by remember { mutableFloatStateOf(0f) }
                    val isDraggingActive = draggedIndex >= 0

                    // 松手后把残留偏移动画收回 0 的落位态：直接归零会让拖拽行连同阴影一起硬切回槽位
                    var settlingIndex by remember { mutableIntStateOf(-1) }
                    val settleOffset = remember { Animatable(0f) }

                    // LazyColumn 只保留视口附近一小段 index 范围的组合状态，滚出这个范围的行会被直接销毁（连带杀死其
                    // pointerInput 手势协程）。swap 只根据 dragOffset/原始触摸位移推进 draggedIndex，本身并不关心列表
                    // 实际滚到了哪——必须拿这两个边界卡住，禁止把 draggedIndex 换到当前还没被组合出来的位置，否则拖拽
                    // 行迟早会被换到视口之外而断触。取不到布局信息时回退到列表自身边界，不能回退成 draggedIndex，
                    // 那会让上下两个方向同时被判定为越界而彻底钳死
                    fun maxComposedIndex(): Int =
                        reorderListState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: reorderedTracks.lastIndex
                    fun minComposedIndex(): Int =
                        reorderListState.layoutInfo.visibleItemsInfo.minOfOrNull { it.index } ?: 0

                    // 越界/待滚屏钳制：手指拖拽和自动滚屏两条路径都要走这里，避免各自实现不一致导致跳变。
                    // 不仅在首尾边界钳制，目标位置还没被组合出来（自动滚屏没跟上）时也要同样钳制——
                    // 否则 swap 被composed-range 挡住后 dragOffset 会无限增长，拖拽行会视觉上盖住好几行而不是贴着手指等滚屏赶上来。
                    // 回弹上限与下面的交换阈值同取 itemHeightPx：上限更小会在触边瞬间把 dragOffset 夹断产生可见回跳。
                    // 两处都直接读 itemHeightPx 而不另存 val，因为滚屏协程只在拖拽起止时重启，捕获的快照不会跟随实测行高更新
                    fun clampDragOffset(offset: Float): Float = when {
                        (draggedIndex <= 0 || draggedIndex <= minComposedIndex()) && offset < 0f -> max(offset, -itemHeightPx)
                        (draggedIndex >= reorderedTracks.lastIndex || draggedIndex >= maxComposedIndex()) && offset > 0f -> min(offset, itemHeightPx)
                        else -> offset
                    }

                    // 手势与自动滚屏共用的交换推进：dragOffset 每越过一整行就与相邻行交换并扣掉一行高，
                    // 使拖拽行的视觉位置在交换前后保持连续。阈值取整行高等价于拖拽行中心越过相邻行中心才交换，
                    // 与 ItemTouchHelper 判定一致；取半行高会让交换后的 dragOffset 正好落在反向交换的临界点上，
                    // 手指抖几像素就来回换
                    // LazyColumn 记的是视口首项的 key，重排后会让锚点跟着这个 key 跑到新 index，视口便整体
                    // 跳一行，把拖拽行顶出组合范围直接销毁掉，手势协程随之被杀而断触。
                    // 换位只是交换相邻两行内容，列表长度和行高都没变，滚动位置本就该原地不动，
                    // 因此换位涉及锚点时按原 index 重新钉一次（requestScrollToItem 只认 index、不再跟 key）
                    fun neutralizeAnchorShift(from: Int, to: Int) {
                        val anchor = reorderListState.firstVisibleItemIndex
                        if (from != anchor && to != anchor) return
                        reorderListState.requestScrollToItem(anchor, reorderListState.firstVisibleItemScrollOffset)
                    }

                    fun advanceSwaps() {
                        var swapped = false
                        while (dragOffset > itemHeightPx && draggedIndex < reorderedTracks.lastIndex && draggedIndex < maxComposedIndex()) {
                            neutralizeAnchorShift(draggedIndex, draggedIndex + 1)
                            Collections.swap(reorderedTracks, draggedIndex, draggedIndex + 1)
                            draggedIndex += 1
                            dragOffset = clampDragOffset(dragOffset - itemHeightPx)
                            swapped = true
                        }
                        while (dragOffset < -itemHeightPx && draggedIndex > 0 && draggedIndex > minComposedIndex()) {
                            neutralizeAnchorShift(draggedIndex, draggedIndex - 1)
                            Collections.swap(reorderedTracks, draggedIndex, draggedIndex - 1)
                            draggedIndex -= 1
                            dragOffset = clampDragOffset(dragOffset + itemHeightPx)
                            swapped = true
                        }
                        if (swapped) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }

                    // 拖拽到达屏幕边缘时的自动平滑滚屏协程（支持边缘停留渐进加速）。
                    // 按帧回调驱动而非 delay(16)：滚动速度以 px/秒 表达再乘真实帧间隔，
                    // 掉帧或非 60Hz 屏幕上速度和加速曲线都不会跟着变形
                    LaunchedEffect(isDraggingActive) {
                        if (!isDraggingActive) return@LaunchedEffect
                        var edgeDurationMs = 0f
                        var lastFrameNanos = 0L
                        while (isActive && draggedIndex >= 0) {
                            val frameNanos = withFrameNanos { it }
                            val deltaMs = if (lastFrameNanos == 0L) {
                                16f
                            } else {
                                ((frameNanos - lastFrameNanos) / 1_000_000f).coerceIn(1f, 64f)
                            }
                            lastFrameNanos = frameNanos

                            val layoutInfo = reorderListState.layoutInfo
                            val draggingItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggedIndex }
                            if (draggingItem == null) {
                                edgeDurationMs = 0f
                                continue
                            }

                            val currentTop = draggingItem.offset + dragOffset
                            val currentBottom = currentTop + draggingItem.size
                            val effectiveViewportEnd = layoutInfo.viewportEndOffset - bottomOverlayInsetPx

                            val isNearTop = currentTop < topEdgeThreshold && draggedIndex > 0
                            val isNearBottom = currentBottom > (effectiveViewportEnd - bottomEdgeThreshold) &&
                                draggedIndex < reorderedTracks.lastIndex
                            if (!isNearTop && !isNearBottom) {
                                edgeDurationMs = 0f
                                continue
                            }

                            edgeDurationMs += deltaMs
                            // 加速系数：边缘停留 1.5s 内从 1.0 渐进提升到 3.5 倍
                            val timeMultiplier = 1f + (edgeDurationMs / 600f).coerceAtMost(2.5f)
                            val depthRatio = if (isNearTop) {
                                ((topEdgeThreshold - currentTop) / topEdgeThreshold).coerceIn(0f, 1f)
                            } else {
                                ((currentBottom - (effectiveViewportEnd - bottomEdgeThreshold)) / bottomEdgeThreshold).coerceIn(0f, 1f)
                            }
                            val speedPxPerSec = (depthRatio * 1000f + 300f) * timeMultiplier
                            val stepPx = speedPxPerSec * deltaMs / 1000f

                            val scrolled = reorderListState.scrollBy(if (isNearTop) -stepPx else stepPx)
                            dragOffset = clampDragOffset(dragOffset + scrolled)
                            advanceSwaps()
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        PlaylistReorderTopBar(
                            overlayHeight = overlayHeight,
                            statusBarHeight = statusBarHeight,
                            isSaving = isSavingOrder,
                            onCancel = {
                                if (!isSavingOrder) {
                                    if (isOrderChanged) {
                                        showDiscardConfirmDialog = true
                                    } else {
                                        isReorderMode = false
                                    }
                                }
                            },
                            onDone = {
                                if (!isOrderChanged) {
                                    isReorderMode = false
                                } else {
                                    isSavingOrder = true
                                    // 提交的是此刻的快照，进入保存态后行的手势会被卸载（enabled=false），
                                    // 这里一并收掉可能残留的拖拽态，避免快照与用户后续动作不一致
                                    draggedIndex = -1
                                    dragOffset = 0f
                                    settlingIndex = -1
                                    viewModel.updateTrackOrder(state.playlist.id, reorderedTracks.toList()) { success ->
                                        isSavingOrder = false
                                        if (success) {
                                            isReorderMode = false
                                        }
                                    }
                                }
                            }
                        )

                        LazyColumn(
                            state = reorderListState,
                            // 拖拽时滚动完全交给自动滚屏协程接管，避免和列表自身的滚动手势抢占同一个 scroll mutex 导致断触
                            userScrollEnabled = !isDraggingActive,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = overlayHeight),
                            contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
                        ) {
                            itemsIndexed(
                                items = reorderedTracks,
                                key = { _, item -> item.id }
                            ) { idx, item ->
                                val isDragging = draggedIndex == idx
                                val isSettling = settlingIndex == idx
                                Box(
                                    // 抬起中的行自己负责位移，交给 animateItem 会和手动 translationY 打架
                                    modifier = if (isDragging || isSettling) Modifier.zIndex(1f) else Modifier.animateItem()
                                ) {
                                    DraggableSongRow(
                                        data = SongRowData(
                                            id = item.id,
                                            title = item.name,
                                            artist = item.ar.joinToString("/") { it.name },
                                            coverUrl = item.al.picUrl
                                        ),
                                        isCurrent = currentTrack?.mediaId == item.id.toString(),
                                        isPlaying = isPlaying,
                                        isPlayed = false,
                                        isDragging = isDragging,
                                        dragOffsetY = when {
                                            isDragging -> dragOffset
                                            isSettling -> settleOffset.value
                                            else -> 0f
                                        },
                                        enabled = !isSavingOrder,
                                        onClick = {},
                                        onDragStart = {
                                            // 同一时刻只允许一行处于拖拽态。多指分别长按两行时后者会覆盖 draggedIndex，
                                            // 而前一条手势协程仍然存活并继续推送位移，两条手势会同时驱动交换把列表搅乱
                                            if (draggedIndex < 0) {
                                                settlingIndex = -1
                                                draggedIndex = idx
                                                dragOffset = 0f
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        },
                                        onDrag = { delta ->
                                            // 交换后本行 idx 与 draggedIndex 同步变化，这个判等对拖拽行恒真，
                                            // 只会滤掉未被受理的那条并发手势
                                            if (draggedIndex == idx) {
                                                dragOffset = clampDragOffset(dragOffset + delta)
                                                advanceSwaps()
                                            }
                                        },
                                        onDragEnd = {
                                            if (draggedIndex == idx) {
                                                val releasedOffset = dragOffset
                                                draggedIndex = -1
                                                dragOffset = 0f
                                                if (releasedOffset != 0f) {
                                                    settlingIndex = idx
                                                    scope.launch {
                                                        settleOffset.snapTo(releasedOffset)
                                                        settleOffset.animateTo(
                                                            targetValue = 0f,
                                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                        )
                                                        settlingIndex = -1
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    PlaylistContent(
                        playlist       = state.playlist,
                        canRemoveFromPlaylist = isOwnedPlaylist,
                        onRemoveFromPlaylist = { songId ->
                            viewModel.removeTrackFromPlaylist(state.playlist.id, songId)
                        },
                        onAddMusicClick = { showAddMusicSheet = true },
                        onEditOrderClick = {
                            reorderedTracks.clear()
                            reorderedTracks.addAll(state.playlist.tracks)
                            isReorderMode = true
                        },
                        onEditInfoClick = { showEditInfoDialog = true },
                        currentTrackId = currentTrack?.mediaId,
                    isPlaying      = isPlaying,
                    likedSongIds   = likedSongIds,
                    collectState   = collectState,
                    isLoggedIn     = userProfile != null,
                    recommendedSongs = state.recommendedSongs,
                    onBack         = onBack,
                    onArtistClick  = onArtistClick,
                    onAlbumClick   = onAlbumClick,
                    onToggleLike   = viewModel::toggleLikeSong,
                    onPlaySong     = { track ->
                        viewModel.playSongInList(track, state.playlist.tracks)
                    },
                    onAddToPlayNext = { track ->
                        viewModel.addTrackToPlayNext(track)
                    },
                    onPlayAll = {
                        if (isThisPlaylistContext) {
                            // 已经是当前播放队列，播放键只做暂停/继续切换
                            viewModel.playerManager.togglePlayPause()
                        } else {
                            val tracks = state.playlist.tracks
                            if (tracks.isNotEmpty()) {
                                // 随机开关是全局播放模式，起播时按当前开关状态决定顺序播放还是打乱播放
                                val ordered = if (isShuffleActive) tracks.shuffled() else tracks
                                viewModel.playSongInList(ordered.first(), ordered)
                            }
                        }
                    },
                    isShuffleActive = isShuffleActive,
                    isCurrentlyPlayingThis = isCurrentlyPlayingThis,
                    onShuffleToggle = {
                        // 纯开关，不直接触发播放；复用全局播放模式，跟全屏播放器的随机按钮保持一致
                        viewModel.playerManager.toggleShuffle()
                    },
                    onLikeClick = { songId ->
                        viewModel.prepareCollectDialog(songId)
                    },
                    onSaveCollection = { songId, items ->
                        viewModel.savePlaylistCollection(songId, items)
                    },
                    onSaveNewCollection = { name, songId ->
                        viewModel.createPlaylistAndAddSong(name, songId)
                    },
                    onRequireLogin = {
                        showLoginSheet = true
                    },
                    onRefreshRecommendations = {
                        viewModel.refreshRecommendations()
                    },
                    onAddRecommendSong = { track ->
                        viewModel.addRecommendSongToPlaylist(state.playlist.id, track)
                    },
                    isSubscribed = state.isSubscribed,
                    onSubscribeClick = {
                        if (userProfile == null) {
                            showLoginSheet = true
                        } else {
                            viewModel.toggleSubscribePlaylist()
                        }
                    },
                    onCommentsClick = {
                        showCommentsSheet = true
                        viewModel.loadPlaylistComments(playlistId)
                    },
                    onMoreClick = {
                        showMoreMenuSheet = true
                    },
                    onHistoryClick = {},
                    historyDates = historyRecommendState.dates,
                    historySongsLoading = historyRecommendState.songsLoading,
                    showHistoryDatePicker = true,
                    selectedHistoryDate = selectedHistoryDate,
                    onSelectedHistoryDateChange = { selectedHistoryDate = it },
                    onLoadHistoryDetail = { viewModel.loadHistoryDetail(it) },
                    onLoadDailyRecommend = { viewModel.loadPlaylist(-1L) }
                )
            }
        }
    }

    if (showLoginSheet) {
            LoginBottomSheet(
                onDismiss = { showLoginSheet = false },
                onWebLogin = {
                    showLoginSheet = false
                    showWebViewLogin = true
                }
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

        if (showCommentsSheet) {
            CommentsBottomSheet(
                commentsState = commentsState,
                onLikeComment = viewModel::likeComment,
                onDismiss = { showCommentsSheet = false },
                onRetry = { viewModel.loadPlaylistComments(playlistId) }
            )
        }



        val successState = uiState as? PlaylistUiState.Success
        if (showMoreMenuSheet && successState != null) {
            val playlist = successState.playlist
            ModalBottomSheet(
                onDismissRequest = { showMoreMenuSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = BottomSheetShape,
                dragHandle = { MelodiaDragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = MelodiaSpacing.lg, vertical = MelodiaSpacing.md)
                ) {
                    Text(
                        text = "歌单操作",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = MelodiaSpacing.md)
                    )

                    val firstArtist = playlist.tracks.firstOrNull()?.ar?.firstOrNull()
                    val artistName = firstArtist?.name ?: "未知歌手"
                    val resourceLabel = if (isAlbum) "专辑" else "歌单"

                    val isManageable = !isAlbum && userProfile != null &&
                        playlist.creator?.userId == userProfile?.uid &&
                        playlist.id > 0L &&
                        playlist.id != userProfile?.uid

                    val menuItems = buildList {
                        add(
                            PlaylistMenuItem(
                                icon = if (successState.isSubscribed) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                title = if (successState.isSubscribed) "取消收藏$resourceLabel" else "收藏$resourceLabel"
                            ) {
                                showMoreMenuSheet = false
                                if (userProfile == null) {
                                    showLoginSheet = true
                                } else {
                                    viewModel.toggleSubscribePlaylist()
                                }
                            }
                        )
                        add(
                            PlaylistMenuItem(
                                icon = Icons.Default.Person,
                                title = "跳转至艺人"
                            ) {
                                showMoreMenuSheet = false
                                if (firstArtist != null) {
                                    onArtistClick(firstArtist.id)
                                } else {
                                    com.lin0721.linmusic.core.ui.components.ToastManager.showToast("未找到关联艺人信息")
                                }
                            }
                        )
                        add(
                            PlaylistMenuItem(
                                icon = Icons.AutoMirrored.Filled.QueueMusic,
                                title = "加入播放队列"
                            ) {
                                showMoreMenuSheet = false
                                viewModel.addTracksToPlayNext(playlist.tracks)
                            }
                        )
                        add(
                            PlaylistMenuItem(
                                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                                title = "添加到歌单"
                            ) {
                                showMoreMenuSheet = false
                                if (userProfile == null) {
                                    showLoginSheet = true
                                } else {
                                    viewModel.prepareImportTargets(playlist.id)
                                    showImportTargetSheet = true
                                }
                            }
                        )
                        if (isManageable) {
                            add(
                                PlaylistMenuItem(
                                    icon = Icons.Default.Edit,
                                    title = "编辑歌单信息"
                                ) {
                                    showMoreMenuSheet = false
                                    showEditInfoDialog = true
                                }
                            )
                            add(
                                PlaylistMenuItem(
                                    icon = Icons.Default.Delete,
                                    title = "删除歌单",
                                    isDestructive = true
                                ) {
                                    showMoreMenuSheet = false
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(menuItems, key = { it.title }) { item ->
                            val iconColor = if (item.isDestructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color.White.copy(alpha = 0.8f)
                            }
                            val titleColor = if (item.isDestructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = item.onClick)
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = iconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(MelodiaSpacing.md))
                                Text(
                                    text = item.title,
                                    color = titleColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showImportTargetSheet) {
            PlaylistImportTargetSheet(
                importState = importState,
                onDismiss = { showImportTargetSheet = false },
                onSelectTarget = { targetId ->
                    viewModel.importAllTracksTo(targetId)
                    showImportTargetSheet = false
                },
                onCreateAndImport = { name ->
                    viewModel.createPlaylistAndImportAll(name)
                    showImportTargetSheet = false
                }
            )
        }

        if (showAddMusicSheet && successState != null) {
            val playlist = successState.playlist
            AddMusicToPlaylistSheet(
                searchQuery = addMusicSearchQuery,
                searchState = addMusicSearchState,
                existingTrackIds = remember(playlist.tracks) {
                    playlist.tracks.map { it.id }.toSet()
                },
                onQueryChange = viewModel::updateAddMusicSearchQuery,
                onClearQuery = viewModel::clearAddMusicSearch,
                onAddTrack = { track, onComplete ->
                    viewModel.addTrackToPlaylist(playlist.id, track) { onComplete() }
                },
                onDismiss = {
                    showAddMusicSheet = false
                    viewModel.clearAddMusicSearch()
                }
            )
        }

        if (showEditInfoDialog && successState != null) {
            val playlist = successState.playlist
            EditPlaylistInfoDialog(
                initialName = playlist.name,
                initialDescription = playlist.description.orEmpty(),
                onDismiss = { showEditInfoDialog = false },
                onConfirm = { newName, newDesc ->
                    viewModel.updatePlaylistInfo(
                        id = playlist.id,
                        originalName = playlist.name,
                        newName = newName,
                        originalDesc = playlist.description,
                        newDesc = newDesc
                    ) { success ->
                        if (success) {
                            showEditInfoDialog = false
                        }
                    }
                }
            )
        }

        if (showDeleteConfirmDialog && successState != null) {
            val playlist = successState.playlist
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = {
                    Text(
                        text = "删除歌单",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "确定要删除歌单「${playlist.name}」吗？删除后将无法恢复。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    MelodiaTextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.deletePlaylist(playlist.id) {
                                onBack()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("删除", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    MelodiaTextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            )
        }

        if (showDiscardConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirmDialog = false },
                title = {
                    Text(
                        text = "放弃修改？",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "当前调整的歌曲顺序尚未保存，确定要放弃修改吗？",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    MelodiaTextButton(
                        onClick = {
                            showDiscardConfirmDialog = false
                            isReorderMode = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("放弃", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    MelodiaTextButton(onClick = { showDiscardConfirmDialog = false }) {
                        Text("继续调整", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            )
        }

    }
}

private data class PlaylistMenuItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)
