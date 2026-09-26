package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

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
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it / 3 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 3 },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
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
                        tint = Color.White,
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
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.75f),
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
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    MelodiaIconButton(onClick = onTogglePlay) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            // 边缘进度条，跟 mini 栏同一个组件
            MiniPlayerProgress(
                currentPositionProvider = currentPositionProvider,
                duration = duration
            )
        }
    }
}
