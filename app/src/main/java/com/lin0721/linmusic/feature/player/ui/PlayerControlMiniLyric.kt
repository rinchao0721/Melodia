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
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.unit.Dp
import com.lin0721.linmusic.core.plugin.LayoutAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

private const val MINI_LYRIC_DIM_ALPHA = 0.45f

// 小歌词展示内容：text 为空表示加载中；dimmed 表示前奏占位与无歌词提示，低透明度显示
internal data class MiniLyricContent(val text: String?, val dimmed: Boolean = false)

// 加载中显示动画；有歌词但尚未唱到第一行（前奏）显示首行；加载完仍无歌词显示「暂无歌词」
internal fun resolveMiniLyricContent(
    lyrics: List<LyricLine>,
    currentLyricIndex: Int,
    isLoading: Boolean
): MiniLyricContent = when {
    lyrics.size == 1 && lyrics[0].text == "纯音乐" -> MiniLyricContent("纯音乐")
    isLoading && lyrics.isEmpty() -> MiniLyricContent(null)
    lyrics.isEmpty() -> MiniLyricContent("暂无歌词", dimmed = true)
    currentLyricIndex in lyrics.indices -> MiniLyricContent(lyrics[currentLyricIndex].text)
    else -> MiniLyricContent(lyrics[0].text, dimmed = true)
}

// 控件区上方的单行歌词，歌词加载中退化为加载动画
@Composable
fun MiniLyricLine(
    lyrics: List<LyricLine>,
    currentLyricIndex: Int,
    isPlaying: Boolean,
    isLoading: Boolean = true,
    onClick: (() -> Unit)? = null,
    horizontalPadding: Dp = MelodiaSpacing.lg,
    textAlign: LayoutAlign? = null,
    // 放不下时横向滚动，否则截断加省略号
    marquee: Boolean = false
) {
    val content = resolveMiniLyricContent(lyrics, currentLyricIndex, isLoading)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        contentAlignment = when (textAlign) {
            LayoutAlign.CENTER -> Alignment.Center
            LayoutAlign.END -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "mini_lyric"
        ) { target ->
            val text = target.text
            if (text == null) {
                LoadingDotsAnimation(
                    color = MelodiaColors.TextPrimary.copy(alpha = 0.35f),
                    isPlaying = isPlaying
                )
            } else {
                Text(
                    text = text,
                    color = if (target.dimmed) {
                        MelodiaColors.TextPrimary.copy(alpha = MINI_LYRIC_DIM_ALPHA)
                    } else {
                        MelodiaColors.TextPrimary
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = if (marquee) Modifier.basicMarquee() else Modifier
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
