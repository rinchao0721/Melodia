package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.media3.common.MediaItem
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaSystemBarsConsumed
import com.lin0721.linmusic.core.ui.theme.rememberMelodiaPlayerPanelWidth

// Expanded 断点下的播放器展开态：默认铺在固定宽度的常驻侧栏卡片里与内容区左右并排，
// 也可切换为铺满全屏的卡片（容器结构见 MelodiaApp.kt）。内容原样复用 FullPlayerScreen，onClose/onDragClose
// 改成收起面板回迷你播放条，不走手机端"关闭播放器+可能重新拉起"那套导航记账机制。
// 卡片本身已避开状态栏与手势条，内部不再为系统栏留白
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
    onNavigateToProfile: (Long) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    // 侧栏→全屏的铺开进度（0~1）与全屏卡片的最终宽度
    fullscreenProgress: () -> Float,
    fullscreenWidth: Dp
) {
    if (currentTrack == null) return

    val panelWidth = rememberMelodiaPlayerPanelWidth()

    // 宽度由外层容器决定（侧栏宽或全屏宽）；侧栏态内容列按侧栏宽度排版，全屏排版的切换由 FullPlayerScreen 按铺开进度处理
    Box(
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.statusBars.union(WindowInsets.navigationBars))
    ) {
        CompositionLocalProvider(LocalMelodiaSystemBarsConsumed provides true) {
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
                onDragClose = onDragClose,
                fitCoverToViewport = true,
                contentMaxWidth = panelWidth,
                onToggleSidebarFullscreen = onToggleFullscreen,
                isSidebarFullscreen = isFullscreen,
                sidebarFullscreenProgress = fullscreenProgress,
                fullscreenContentWidth = fullscreenWidth
            )
        }
    }
}
