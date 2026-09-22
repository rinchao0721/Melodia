package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.MediaItem
import com.lin0721.linmusic.core.ui.theme.rememberMelodiaPlayerPanelWidth

// Expanded 断点下的播放器展开态：不整屏覆盖，铺在一个固定宽度的常驻侧栏里，与内容区
// 左右并排（容器结构见 MelodiaApp.kt）。内容原样复用 FullPlayerScreen，onClose/onDragClose
// 改成收起面板回迷你播放条，不走手机端"关闭播放器+可能重新拉起"那套导航记账机制
@Composable
fun PlayerDockPanel(
    currentTrack: MediaItem?,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    onDragClose: (Float, Float) -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onNavigateToProfile: (Long) -> Unit
) {
    if (currentTrack == null) return

    val panelWidth = rememberMelodiaPlayerPanelWidth()

    Box(
        modifier = Modifier
            .width(panelWidth)
            .fillMaxHeight()
    ) {
        FullPlayerScreen(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            currentPositionProvider = currentPositionProvider,
            duration = duration,
            onTogglePlay = onTogglePlay,
            onSeek = onSeek,
            onClose = onClose,
            isPlayerOpen = true,
            onArtistClick = onArtistClick,
            onAlbumClick = onAlbumClick,
            onNavigateToProfile = onNavigateToProfile,
            onDragClose = onDragClose
        )
    }
}
