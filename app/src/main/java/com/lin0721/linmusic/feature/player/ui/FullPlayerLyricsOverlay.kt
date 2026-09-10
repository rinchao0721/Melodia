package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.ui.theme.PlayerBackdropPalette
import dev.chrisbanes.haze.HazeState

// 全屏歌词覆盖层，从底部滑入滑出
@Composable
fun FullPlayerLyricsOverlay(
    visible: Boolean,
    songState: PlayerSongDetailState,
    colors: PlayerBackdropPalette,
    currentLyricIndex: Int,
    title: String,
    artist: String,
    hazeState: HazeState,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    playMode: PlayMode,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onMoreClick: () -> Unit
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
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = if (isDragClosed) {
            ExitTransition.None
        } else {
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        FullScreenLyricsView(
            lyrics = songState.lyrics,
            currentIndex = currentLyricIndex,
            isLoading = songState.isLyricsLoading,
            title = title,
            artist = artist,
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
            duration = duration,
            onTogglePlay = onTogglePlay,
            onPlayNext = onPlayNext,
            onPlayPrevious = onPlayPrevious,
            playMode = playMode,
            onToggleShuffle = onToggleShuffle,
            onToggleRepeat = onToggleRepeat,
            onMoreClick = onMoreClick
        )
    }
}
