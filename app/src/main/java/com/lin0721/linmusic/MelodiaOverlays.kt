package com.lin0721.linmusic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import com.lin0721.linmusic.core.ui.components.CustomToast
import com.lin0721.linmusic.core.ui.components.MelodiaNavigationBar
import com.lin0721.linmusic.core.ui.components.MiniPlayerCard
import com.lin0721.linmusic.feature.create.ui.CreatePopupMenu
import com.lin0721.linmusic.feature.player.ui.FullPlayerScreen
import dev.chrisbanes.haze.HazeState
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 悬浮播放卡片 + 底部导航栏的实际占用高度，供各页面计算列表底部留白，避免内容被遮挡
val LocalBottomOverlayInset = staticCompositionLocalOf { 0.dp }

// ────────────────────────────────────────────────────────────────────────────
// 底部浮层：创建菜单弹出层 + 悬浮播放卡片 + M3 导航栏
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun MelodiaBottomOverlay(
    modifier: Modifier = Modifier,
    currentScreen: Screen,
    showCreateSheet: Boolean,
    isLoginScreenVisible: Boolean,
    isMvFullscreen: Boolean,
    currentTrack: MediaItem?,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    hazeState: HazeState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    onMiniPlayerDrag: (Float) -> Unit,
    onMiniPlayerDragEnd: (Float) -> Unit,
    onCreateDismiss: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onCreateClick: () -> Unit,
    showCreateEntry: Boolean = true,
    onOverlayHeightChanged: (Dp) -> Unit = {}
) {
    val density = LocalDensity.current
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // 0. 创建菜单弹出层
        AnimatedVisibility(
            visible = showCreateSheet && !isLoginScreenVisible,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = MelodiaSpacing.md)
                    .padding(bottom = 12.dp)
            ) {
                CreatePopupMenu(
                    onDismiss = onCreateDismiss,
                    onLoginRequest = {
                        // TODO: 触发登录流程
                    }
                )
            }
        }

        // 悬浮播放卡片 + 导航栏：实际占用高度上报出去，供页面内容计算底部留白
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    onOverlayHeightChanged(with(density) { it.height.toDp() })
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 浮动播放卡片
            AnimatedVisibility(
                visible = currentTrack != null && !isLoginScreenVisible && !isMvFullscreen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxWidth()
            ) {
                MiniPlayerCard(
                    hazeState = hazeState,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    currentPositionProvider = currentPositionProvider,
                    duration = duration,
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    onClick = onMiniPlayerClick,
                    onDrag = onMiniPlayerDrag,
                    onDragEnd = onMiniPlayerDragEnd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MelodiaSpacing.sm)
                )
            }

            // 2. M3 导航栏 (在非登录状态下显示)
            AnimatedVisibility(
                visible = !isLoginScreenVisible && !isMvFullscreen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                MelodiaNavigationBar(
                    currentScreen = currentScreen,
                    onNavigate = onNavigate,
                    onCreateClick = onCreateClick,
                    isCreateMenuOpen = showCreateSheet,
                    showCreateEntry = showCreateEntry
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// 全屏播放器悬浮层（随 playerOffsetY 拖拽/弹簧动画上下滑动）
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun MelodiaFullPlayerOverlay(
    currentTrack: MediaItem?,
    screenHeightPx: Float,
    isPlayerOpen: Boolean,
    playerOffsetY: Float,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    onDragClose: (Float, Float) -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onNavigateToProfile: (Long) -> Unit = {}
) {
    // 仅在播放器打开或动画进行中时渲染，避免关闭后 nestedScroll 拦截触摸事件
    if (currentTrack != null && screenHeightPx > 0f && isPlayerOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, playerOffsetY.toInt()) }
                .clip(
                    RoundedCornerShape(
                        topStart = if (playerOffsetY > 0f) 24.dp else 0.dp,
                        topEnd = if (playerOffsetY > 0f) 24.dp else 0.dp
                    )
                )
        ) {
            FullPlayerScreen(
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                currentPositionProvider = currentPositionProvider,
                duration = duration,
                onTogglePlay = onTogglePlay,
                onSeek = onSeek,
                onClose = onClose,
                onDragClose = onDragClose,
                isPlayerOpen = isPlayerOpen,
                onArtistClick = onArtistClick,
                onAlbumClick = onAlbumClick,
                onNavigateToProfile = onNavigateToProfile
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// 全局自定义 Toast 提示悬浮层
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun MelodiaToastHost(toastMessage: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(250)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp) // 位于底部浮岛上方
                .zIndex(999f)
        ) {
            toastMessage?.let { msg ->
                CustomToast(message = msg)
            }
        }
    }
}
