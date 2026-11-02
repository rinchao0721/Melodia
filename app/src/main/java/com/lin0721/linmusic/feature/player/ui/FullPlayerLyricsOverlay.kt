package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.ui.theme.ImmersiveTheme
import com.lin0721.linmusic.core.ui.theme.PlayerBackdropPalette
import com.lin0721.linmusic.core.ui.theme.uiSpring
import dev.chrisbanes.haze.HazeState

// 全屏歌词是否完整盖住下层播放页：进场动画结束且未下拉。只应在绘制阶段读取，下层据此跳过整页绘制
@Stable
class LyricsOverlayCoverage {
    internal var coveringProvider by mutableStateOf<() -> Boolean>({ false })
    val isCovering: Boolean get() = coveringProvider()
}

// 全屏歌词覆盖层，从底部滑入滑出
@Composable
fun FullPlayerLyricsOverlay(
    visible: Boolean,
    coverage: LyricsOverlayCoverage,
    songState: PlayerSongDetailState,
    colors: PlayerBackdropPalette,
    currentLyricIndex: Int,
    activeLyricIndices: Set<Int> = emptySet(),
    title: String,
    artist: String,
    coverUrl: String,
    songLink: String?,
    hazeState: HazeState,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    lyricPositionProvider: () -> Long = currentPositionProvider,
    duration: Long,
    playMode: PlayMode,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onMoreClick: () -> Unit,
    onControlsVisibilityChange: (Boolean) -> Unit = {}
) {
    var isDragClosed by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            isDragClosed = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = uiSpring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = if (isDragClosed) {
            ExitTransition.None
        } else {
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = uiSpring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        val visibilityTransition = transition
        // 全屏歌词是沉浸场景，浅色主题下仍按深色取色、中调铺底
        ImmersiveTheme {
            FullScreenLyricsView(
                coverage = coverage,
                isFullyEntered = {
                    visibilityTransition.currentState == EnterExitState.Visible &&
                        visibilityTransition.targetState == EnterExitState.Visible
                },
                lyrics = songState.lyrics,
                currentIndex = currentLyricIndex,
                activeIndices = activeLyricIndices,
                isLoading = songState.isLyricsLoading,
                title = title,
                artist = artist,
                coverUrl = coverUrl,
                songLink = songLink,
                base = colors.base,
                highlightColor = colors.textHighlight,
                onSeek = onSeek,
                hazeState = hazeState,
                onClose = onClose,
                onDragClose = {
                    isDragClosed = true
                    onClose()
                },
                isPlaying = isPlaying,
                currentPositionProvider = currentPositionProvider,
                lyricPositionProvider = lyricPositionProvider,
                duration = duration,
                onTogglePlay = onTogglePlay,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                playMode = playMode,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onMoreClick = onMoreClick,
                onControlsVisibilityChange = onControlsVisibilityChange
            )
        }
    }
}
