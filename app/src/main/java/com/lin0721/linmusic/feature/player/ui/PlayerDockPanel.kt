package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem

// 平板常驻播放面板宽度：竖屏 340dp / 横屏 400dp（设计文档 6.3 节建议值，真机验证后可再调）
private val PanelWidthPortrait = 340.dp
private val PanelWidthLandscape = 400.dp

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

    val configuration = LocalConfiguration.current
    val panelWidth = if (configuration.screenWidthDp < configuration.screenHeightDp) {
        PanelWidthPortrait
    } else {
        PanelWidthLandscape
    }

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
