package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// ────────────────────────────────────────────────────────────────────────────
// 全屏歌词页的播放控制条（进度条 + 随机/上一首/播放暂停/下一首/循环）
// ────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenControls(
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    playMode: PlayMode,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    val currentPosition = currentPositionProvider()
    val progress = if (duration > 0) {
        if (isSeeking) seekPosition else currentPosition.toFloat() / duration
    } else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MelodiaSpacing.md)
            .padding(bottom = 28.dp)
    ) {
        Slider(
            value = progress.coerceIn(0f, 1f),
            onValueChange = {
                isSeeking = true
                seekPosition = it
            },
            onValueChangeFinished = {
                isSeeking = false
                onSeek((seekPosition * duration).toLong())
            },
            thumb = {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isSeeking) 14.dp else 8.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            },
            track = { sliderState ->
                val fraction = sliderState.value
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color.White)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayPosition = if (isSeeking) (seekPosition * duration).toLong() else currentPosition
            Text(formatTime(displayPosition), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(formatTime(duration), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(MelodiaSpacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MelodiaSpacing.xs, start = MelodiaSpacing.sm, end = MelodiaSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pressable(MelodiaPress.Icon) { onToggleShuffle() }
                    .clip(CircleShape)
                    .padding(start = 0.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = null,
                    tint = if (playMode == PlayMode.SHUFFLE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MelodiaIconButton(onClick = onPlayPrevious, style = MelodiaPress.Transport) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(46.dp)
                    )
                }

                val playInteraction = remember { MutableInteractionSource() }
                val isPressed by playInteraction.collectIsPressedAsState()
                val buttonAlpha = remember { Animatable(1f) }
                var clickTrigger by remember { mutableIntStateOf(0) }

                LaunchedEffect(isPressed) {
                    if (isPressed) {
                        buttonAlpha.animateTo(0.70f, tween(durationMillis = 80))
                    } else if (clickTrigger == 0) {
                        buttonAlpha.animateTo(1f, tween(durationMillis = 200, easing = FastOutSlowInEasing))
                    }
                }

                LaunchedEffect(clickTrigger) {
                    if (clickTrigger > 0) {
                        buttonAlpha.snapTo(0.70f)
                        buttonAlpha.animateTo(1f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
                    }
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            alpha = buttonAlpha.value
                        }
                        .background(Color.White, CircleShape)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null,
                            onClick = {
                                clickTrigger++
                                onTogglePlay()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        tint = Color.Black,
                        modifier = Modifier.size(42.dp),
                        contentDescription = null
                    )
                }

                MelodiaIconButton(onClick = onPlayNext, style = MelodiaPress.Transport) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }


            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pressable(MelodiaPress.Icon) { onToggleRepeat() }
                    .clip(CircleShape),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = if (playMode == PlayMode.SINGLE_LOOP) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = null,
                    tint = if (playMode == PlayMode.SINGLE_LOOP) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
