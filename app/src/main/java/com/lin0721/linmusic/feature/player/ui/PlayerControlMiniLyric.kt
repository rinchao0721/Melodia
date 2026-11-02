package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 控件区上方的单行歌词，无歌词时退化为加载动画
@Composable
fun MiniLyricLine(
    lyrics: List<LyricLine>,
    currentLyricIndex: Int,
    isPlaying: Boolean,
    onClick: (() -> Unit)? = null
) {
    val isPureMusic = lyrics.size == 1 && lyrics[0].text == "纯音乐"
    val currentText = when {
        isPureMusic -> "纯音乐"
        lyrics.isNotEmpty() && currentLyricIndex in lyrics.indices -> lyrics[currentLyricIndex].text
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.lg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedContent(
            targetState = currentText,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "mini_lyric"
        ) { text ->
            if (text.isEmpty()) {
                LoadingDotsAnimation(
                    color = MelodiaColors.TextPrimary.copy(alpha = 0.35f),
                    isPlaying = isPlaying
                )
            } else {
                Text(
                    text = text,
                    color = MelodiaColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// 三点依次呼吸的等待动画，暂停时收回到最小态
@Composable
fun LoadingDotsAnimation(
    color: Color,
    isPlaying: Boolean,
    dotSize: androidx.compose.ui.unit.Dp = 8.dp,
    spacing: androidx.compose.ui.unit.Dp = 6.dp
) {
    val dot1Scale = remember { Animatable(0.3f) }
    val dot2Scale = remember { Animatable(0.3f) }
    val dot3Scale = remember { Animatable(0.3f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            launch {
                dot1Scale.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            launch {
                delay(150)
                dot2Scale.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            launch {
                delay(300)
                dot3Scale.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
        } else {
            launch { dot1Scale.animateTo(0.3f, tween(200)) }
            launch { dot2Scale.animateTo(0.3f, tween(200)) }
            launch { dot3Scale.animateTo(0.3f, tween(200)) }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    scaleX = dot1Scale.value
                    scaleY = dot1Scale.value
                    alpha = dot1Scale.value
                }
                .background(color, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    scaleX = dot2Scale.value
                    scaleY = dot2Scale.value
                    alpha = dot2Scale.value
                }
                .background(color, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    scaleX = dot3Scale.value
                    scaleY = dot3Scale.value
                    alpha = dot3Scale.value
                }
                .background(color, CircleShape)
        )
    }
}
