package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.domain.LyricLine
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ────────────────────────────────────────────────────────────────────────────
// 全屏歌词页：承载下拉关闭手势与滚动跟随状态，装配顶栏、歌词列表与播放控制
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun FullScreenLyricsView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    isLoading: Boolean,
    title: String,
    artist: String,
    base: Color,
    highlightColor: Color,
    onSeek: (Long) -> Unit,
    hazeState: HazeState,
    onClose: () -> Unit,
    onDragClose: () -> Unit = onClose,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    playMode: PlayMode,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onMoreClick: () -> Unit
) {
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var timerJob by remember { mutableStateOf<Job?>(null) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }

    val dragState = rememberFullScreenLyricsDragState(
        lazyListState = lazyListState,
        onClose = onClose,
        onDragClose = onDragClose,
        screenHeightPx = screenHeightPx
    )

    LaunchedEffect(Unit) {
        dragState.reset()
    }

    DisposableEffect(Unit) {
        onDispose {
            dragState.reset()
        }
    }

    // 歌词页手势拖动的纯 UI 交互态，不涉及业务数据，只在本组件内部使用
    var isUserScrolling by remember { mutableStateOf(false) }

    val isPlayingState = rememberUpdatedState(isPlaying)

    // 拖动/点击跳转播放进度后，同时结束用户滚动态，恢复自动跟随当前歌词行
    val handleSeek: (Long) -> Unit = { timeMs ->
        isUserScrolling = false
        onSeek(timeMs)
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            if (isUserScrolling) {
                isUserScrolling = false
            }
        } else {
            timerJob?.cancel()
        }
    }

    val gestureModifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press) {
                    timerJob?.cancel()
                    isUserScrolling = true
                } else if (event.type == PointerEventType.Release) {
                    timerJob?.cancel()
                    if (isPlayingState.value) {
                        timerJob = scope.launch {
                            delay(5000)
                            isUserScrolling = false
                        }
                    }
                }
            }
        }
    }

    val topCornerRadius by remember {
        derivedStateOf {
            if (dragState.offsetY > 0f) 24.dp else 0.dp
        }
    }

    PlayerBackdrop(
        base = base,
        mode = BackdropMode.Immersive,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { dragState.onScreenHeightChange(it.height.toFloat()) }
            .nestedScroll(dragState.nestedScrollConnection)
            .graphicsLayer {
                translationY = dragState.offsetY
            }
            .clip(RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius))
            // 拦截全屏歌词页空白处点击，防止手势穿透到底层播放器
            .pointerInput(Unit) {
                detectTapGestures { }
            }
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeChild(state = hazeState, style = HazeStyle(blurRadius = 40.dp, noiseFactor = 0.02f))
                .statusBarsPadding()
        ) {
            FullScreenLyricsHeader(
                title = title,
                artist = artist,
                onClose = onClose,
                onMoreClick = onMoreClick,
                onDragDelta = { delta -> dragState.onHeaderDrag(delta) },
                onDragStart = { dragState.onHeaderDragStart() },
                onDragRelease = { velocity -> dragState.handleDragRelease(velocity = velocity) }
            )

            FullScreenLyricsList(
                lyrics = lyrics,
                currentIndex = currentIndex,
                isLoading = isLoading,
                isUserScrolling = isUserScrolling,
                highlightColor = highlightColor,
                currentPositionProvider = currentPositionProvider,
                lazyListState = lazyListState,
                viewportHeightPx = dragState.viewportHeightPx,
                onViewportHeightChange = { height -> dragState.onViewportHeightChange(height) },
                gestureModifier = gestureModifier,
                onSeek = handleSeek,
                onLyricClick = { line ->
                    timerJob?.cancel()
                    handleSeek(line.timeMs)
                }
            )

            FullScreenControls(
                isPlaying = isPlaying,
                currentPositionProvider = currentPositionProvider,
                duration = duration,
                onSeek = handleSeek,
                onTogglePlay = onTogglePlay,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                playMode = playMode,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat
            )
        }
    }
}
