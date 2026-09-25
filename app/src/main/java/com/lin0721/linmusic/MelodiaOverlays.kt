package com.lin0721.linmusic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import com.lin0721.linmusic.core.download.ui.DownloadProgressBanner
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.ui.components.CustomToast
import com.lin0721.linmusic.core.ui.components.MelodiaNavigationBar
import com.lin0721.linmusic.core.ui.components.MiniPlayerCard
import com.lin0721.linmusic.feature.create.ui.CreatePopupMenu
import com.lin0721.linmusic.feature.player.ui.FullPlayerScreen
import dev.chrisbanes.haze.HazeState
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.rememberMelodiaPlayerPanelWidth

// 悬浮播放卡片 + 底部导航栏的实际占用高度，供各页面计算列表底部留白，避免内容被遮挡
val LocalBottomOverlayInset = staticCompositionLocalOf { 0.dp }

// Expanded 下底栏与迷你条共用的固定高度，取迷你条自然高度（上下内边距 10dp×2 + 内容 52dp + 进度条 2dp），
// 也是播放面板向上生长的起始高度。不用 IntrinsicSize：迷你条隐藏后导航栏会回落到自身较矮的高度
internal val ExpandedBottomBarHeight = 74.dp

// 面板让位后 Tab 栏在内容卡片内的最大宽度
private val ExpandedNavigationBarMaxWidth = 560.dp

// 全屏播放器/侧边栏/创建菜单这类全局浮层是否开着。页面内部自己的 BackHandler（收起子菜单、退出搜索态等）
// 需要在全局浮层开着时让位，否则 Compose 按注册顺序分发返回事件时会被内层的局部 BackHandler 抢先吞掉，
// 导致返回键没有先关掉全局浮层，而是直接改动了浮层底下页面的内部状态
val LocalGlobalOverlayOpen = staticCompositionLocalOf { false }

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
    isMvCommentsOpen: Boolean = false,
    // 平板播放面板是否已让位（内容区已收窄、面板常驻右侧）
    isPanelDocked: Boolean = false,
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
    previousQueueItem: QueueItem? = null,
    nextQueueItem: QueueItem? = null,
    onMiniPlayerPrevious: () -> Unit = {},
    onCancelPendingSkip: () -> Boolean = { false },
    isMiniPlayerLiked: Boolean = false,
    onMiniPlayerLikeClick: () -> Unit = {},
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
        val windowSizeClass = LocalMelodiaWindowSizeClass.current
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    onOverlayHeightChanged(with(density) { it.height.toDp() })
                }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 下载进度横幅
                AnimatedVisibility(
                    visible = !isLoginScreenVisible && !isMvFullscreen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DownloadProgressBanner(modifier = Modifier.fillMaxWidth())
                }

                if (windowSizeClass == MelodiaWindowSizeClass.Expanded) {
                    // 平板宽屏：Tab 组件 + 迷你播放组件左右并排，两个独立悬浮卡片，
                    // 都不贴屏幕边缘；系统手势条避让在这一层统一处理一次
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = MelodiaSpacing.sm)
                            .padding(bottom = MelodiaSpacing.sm)
                            .height(ExpandedBottomBarHeight),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        AnimatedVisibility(
                            visible = !isLoginScreenVisible && !isMvFullscreen && !isMvCommentsOpen,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            // 面板收起时 Tab 栏铺满到迷你条前；面板让位后在内容卡片里居中限宽
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                MelodiaNavigationBar(
                                    currentScreen = currentScreen,
                                    onNavigate = onNavigate,
                                    onCreateClick = onCreateClick,
                                    isCreateMenuOpen = showCreateSheet,
                                    showCreateEntry = showCreateEntry,
                                    expanded = true,
                                    modifier = Modifier
                                        .then(
                                            if (isPanelDocked) Modifier.widthIn(max = ExpandedNavigationBarMaxWidth)
                                            else Modifier
                                        )
                                        .fillMaxHeight()
                                )
                            }
                        }

                        // 让位期间迷你条被面板盖住，直接移出、移回，不走退场/入场动画。
                        // 与导航栏的间距放在迷你条自身而非 spacedBy，否则入场/退场首尾会各有一次 8dp 突变
                        if (!isPanelDocked) {
                            AnimatedVisibility(
                                visible = currentTrack != null && !isLoginScreenVisible && !isMvFullscreen && !isMvCommentsOpen,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally(),
                                modifier = Modifier.fillMaxHeight()
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
                                    previousQueueItem = previousQueueItem,
                                    nextQueueItem = nextQueueItem,
                                    onPrevious = onMiniPlayerPrevious,
                                    onCancelPendingSkip = onCancelPendingSkip,
                                    isLiked = isMiniPlayerLiked,
                                    onLikeClick = onMiniPlayerLikeClick,
                                    expanded = true,
                                    // 和右侧展开态面板同宽，两者上下贴齐
                                    modifier = Modifier
                                        .padding(start = MelodiaSpacing.sm)
                                        .width(rememberMelodiaPlayerPanelWidth())
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                } else {
                    // 手机：迷你播放卡在上、导航栏在下，垂直堆叠（现状不变）
                    AnimatedVisibility(
                        visible = currentTrack != null && !isLoginScreenVisible && !isMvFullscreen && !isMvCommentsOpen,
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
                            previousQueueItem = previousQueueItem,
                            nextQueueItem = nextQueueItem,
                            onPrevious = onMiniPlayerPrevious,
                            onCancelPendingSkip = onCancelPendingSkip,
                            isLiked = isMiniPlayerLiked,
                            onLikeClick = onMiniPlayerLikeClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MelodiaSpacing.sm)
                        )
                    }

                    AnimatedVisibility(
                        visible = !isLoginScreenVisible && !isMvFullscreen && !isMvCommentsOpen,
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
    onNavigateToProfile: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 仅在播放器打开或动画进行中时渲染，避免关闭后 nestedScroll 拦截触摸事件
    if (currentTrack != null && screenHeightPx > 0f && isPlayerOpen) {
        Box(
            modifier = modifier
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
                onNavigateToProfile = onNavigateToProfile,
                fitCoverAboveNavigationBar = true
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
