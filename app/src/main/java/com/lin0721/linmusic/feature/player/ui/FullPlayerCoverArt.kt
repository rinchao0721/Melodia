package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.SwipeToSkipCover
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaPlayerPanelAtStart
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

private val SidebarToggleGap = 4.dp

// 封面区：播放来源标题栏 + 方形封面
@Composable
fun FullPlayerCoverArt(
    coverUrl: String,
    playContext: String?,
    podcastRadioName: String = "",
    onClose: () -> Unit,
    onMoreClick: () -> Unit = {},
    previousCoverUrl: String? = null,
    nextCoverUrl: String? = null,
    onSwipeToPrevious: () -> Unit = {},
    onSwipeToNext: () -> Unit = {},
    currentKey: Any,
    previousKey: Any? = null,
    nextKey: Any? = null,
    // 在默认左右留白之外再内缩的距离，封面随之居中缩小
    coverExtraInset: Dp = 0.dp,
    // 封面默认基础水平边距，默认 MelodiaSpacing.lg (24dp)，关闭小歌词时可自适应缩窄以放大封面
    baseCoverPadding: Dp = MelodiaSpacing.lg,
    // 平板常驻面板用：侧栏与全屏互切，为 null 时不显示该按钮
    onToggleSidebarFullscreen: (() -> Unit)? = null,
    isSidebarFullscreen: Boolean = false,
    onPlaylistClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onCancelSwipe: () -> Boolean = { false }
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = MelodiaSpacing.md, bottom = MelodiaSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FullPlayerSourceBar(
            playContext = playContext,
            podcastRadioName = podcastRadioName,
            onClose = onClose,
            onMoreClick = onMoreClick,
            onToggleSidebarFullscreen = onToggleSidebarFullscreen,
            isSidebarFullscreen = isSidebarFullscreen,
            onPlaylistClick = onPlaylistClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.lg)
                .padding(bottom = 44.dp)
        )

        SwipeToSkipCover(
            coverUrl = coverUrl,
            previousCoverUrl = previousCoverUrl,
            nextCoverUrl = nextCoverUrl,
            onConfirmPrevious = onSwipeToPrevious,
            onConfirmNext = onSwipeToNext,
            currentKey = currentKey,
            previousKey = previousKey,
            nextKey = nextKey,
            contentPadding = baseCoverPadding + coverExtraInset,
            contentScale = ContentScale.Crop,
            shape = RoundedCornerShape(RadiusCompact),
            elevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
            onCancelPending = onCancelSwipe
        )
    }
}

// 播放来源标题栏：侧栏/全屏切换、收起、播放来源、全屏歌词、更多
@Composable
fun FullPlayerSourceBar(
    playContext: String?,
    onClose: () -> Unit,
    onMoreClick: () -> Unit,
    onToggleSidebarFullscreen: (() -> Unit)?,
    isSidebarFullscreen: Boolean,
    onFullscreenLyricsClick: (() -> Unit)? = null,
    podcastRadioName: String = "",
    onPlaylistClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val motion = LocalUiMotion.current
    val isPanelAtStart = LocalMelodiaPlayerPanelAtStart.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onToggleSidebarFullscreen != null) {
                MelodiaIconButton(
                    onClick = onToggleSidebarFullscreen,
                    modifier = Modifier
                        .size(32.dp)
                        .offset(x = (-4).dp)
                ) {
                    // 图标里的侧栏画在右侧，面板停靠左侧时水平翻转
                    Icon(
                        if (isSidebarFullscreen) Icons.Filled.ViewSidebar else Icons.Outlined.ViewSidebar,
                        contentDescription = if (isSidebarFullscreen) "收回侧栏" else "展开全屏",
                        tint = MelodiaColors.TextPrimary,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { scaleX = if (isPanelAtStart) -1f else 1f }
                    )
                }
                Spacer(modifier = Modifier.width(SidebarToggleGap))
            }
            MelodiaIconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .offset(x = (-4).dp)
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MelodiaColors.TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        AnimatedContent(
            targetState = playContext,
            transitionSpec = {
                (fadeIn(animationSpec = motion.tween(260)) +
                        slideInVertically(animationSpec = motion.tween(260)) { height -> height / 3 })
                    .togetherWith(
                        fadeOut(animationSpec = motion.tween(200)) +
                                slideOutVertically(animationSpec = motion.tween(200)) { height -> -height / 3 }
                    ).using(
                        SizeTransform(
                            clip = false,
                            sizeAnimationSpec = { _, _ -> motion.tween(280) }
                        )
                    )
            },
            label = "play_source_transition",
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f)
        ) { context ->
            val (sourceText, detailText) = when (context) {
                null -> "NOW PLAYING" to null
                "搜索" -> "播放自" to "搜索"
                "每日推荐" -> "播放自" to "每日推荐"
                "历史日推" -> "播放自" to "历史日推"
                "intelligence" -> "播放自" to "心动模式"
                PlaybackController.CONTEXT_PODCAST -> "播放自播客" to podcastRadioName.ifBlank { "播客" }
                else -> "播放自歌单" to context
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = if (onPlaylistClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onPlaylistClick)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                } else Modifier
            ) {
                Text(
                    text = sourceText,
                    color = MelodiaColors.TextPrimary.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (detailText != null) {
                    Text(
                        text = "“$detailText”",
                        color = MelodiaColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onFullscreenLyricsClick != null) {
                MelodiaIconButton(
                    onClick = onFullscreenLyricsClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Fullscreen,
                        contentDescription = "全屏歌词",
                        tint = MelodiaColors.TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(SidebarToggleGap))
            } else if (onToggleSidebarFullscreen != null) {
                // 左侧有切换按钮且右侧无全屏按钮时补等宽留白，播放来源标题保持居中
                Spacer(modifier = Modifier.width(32.dp + SidebarToggleGap))
            }
            MelodiaIconButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .size(32.dp)
                    .offset(x = 4.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = MelodiaColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
