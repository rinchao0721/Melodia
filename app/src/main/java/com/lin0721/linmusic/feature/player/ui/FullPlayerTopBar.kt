package com.lin0721.linmusic.feature.player.ui

import com.lin0721.linmusic.core.ui.components.PlayPauseIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.MiniPlayerProgress
import com.lin0721.linmusic.core.ui.skin.CoverGroup
import com.lin0721.linmusic.core.ui.skin.isCoverBackdrop
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.playerBackdrop
import com.lin0721.linmusic.core.ui.theme.uiTween

// 播放页顶栏，仅在封面滚出视野后显示歌名与快捷操作
@Composable
fun FullPlayerTopBar(
    onClose: () -> Unit,
    title: String = "",
    artist: String = "",
    showTitle: Boolean = false,
    isPlaying: Boolean = false,
    onTogglePlay: () -> Unit = {},
    isLiked: Boolean = false,
    onToggleLike: () -> Unit = {},
    backgroundColor: Color = Color.Transparent,
    currentPositionProvider: () -> Long = { 0L },
    duration: Long = 0L,
    onArtistClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 与全屏背景顶部同色纯色铺底，不做光斑/模糊
    AnimatedVisibility(
        visible = showTitle,
        enter = fadeIn(uiTween(220)) + slideInVertically(uiTween(220)) { -it / 3 },
        exit = fadeOut(uiTween(180)) + slideOutVertically(uiTween(180)) { -it / 3 },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isCoverBackdrop(CoverGroup.PLAYER)) Modifier.playerBackdrop(backgroundColor) else Modifier.background(backgroundColor))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = MelodiaSpacing.sm, vertical = MelodiaSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MelodiaIconButton(onClick = onClose) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MelodiaColors.TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = title,
                        color = MelodiaColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = artist,
                        color = MelodiaColors.TextPrimary.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onArtistClick != null) Modifier.clickable(onClick = onArtistClick) else Modifier
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
                ) {
                    MelodiaIconButton(onClick = onToggleLike) {
                        Icon(
                            if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = MelodiaColors.TextPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    MelodiaIconButton(onClick = onTogglePlay, style = MelodiaPress.Transport) {
                        PlayPauseIcon(isPlaying, MelodiaColors.TextPrimary)
                    }
                }
            }
            // 边缘进度条，跟 mini 栏同一个组件
            MiniPlayerProgress(
                currentPositionProvider = currentPositionProvider,
                duration = duration,
                // 浅色收起顶栏是浅底，轨道改用描边色
                trackColor = if (MelodiaColors.IsDark) Color.White.copy(alpha = 0.18f) else MelodiaColors.Outline
            )
        }
    }
}
