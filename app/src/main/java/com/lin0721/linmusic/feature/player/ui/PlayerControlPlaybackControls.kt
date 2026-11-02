package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.border
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.ui.graphics.Color
import com.lin0721.linmusic.core.ui.components.PlayPauseIcon
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.ui.interaction.pressScale
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.outlinedControls
import com.lin0721.linmusic.core.ui.theme.styledButtonShape

// 主控件行：随机/漫游、上一曲、播放暂停、下一曲、循环模式
@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onQueueClick: () -> Unit,
    playMode: PlayMode,
    isRoaming: Boolean = false,
    onDisableRoaming: () -> Unit = {},
    isIntelligence: Boolean = false,
    onDisableIntelligence: () -> Unit = {}
) {
    // 五个键等距排开：模式 / 上一首 / 播放 / 下一首 / 队列。
    // 300dp 宽（大字号/高密度）下要缩小播放键与边距，否则最右侧队列会被挤出屏幕
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.sm)
            .padding(top = MelodiaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 常规播放模式一律白色（模式只靠图标形状区分，避免"某些模式变红、某些不变"的不一致）；
        // 漫游/心动是临时特殊状态，仍用主题色提示
        val specialMode = isRoaming || isIntelligence
        MelodiaIconButton(
            onClick = {
                when {
                    isRoaming -> onDisableRoaming()
                    isIntelligence -> onDisableIntelligence()
                    else -> onCyclePlayMode()
                }
            },
            containerColor = if (specialMode) MelodiaColors.Accent.copy(alpha = 0.16f) else Color.Transparent
        ) {
            Icon(
                imageVector = when {
                    isRoaming -> Icons.Rounded.AllInclusive
                    isIntelligence -> Icons.Rounded.AutoAwesome
                    else -> playMode.modeIcon()
                },
                contentDescription = when {
                    isRoaming -> "关闭相似歌曲漫游"
                    isIntelligence -> "关闭心动模式"
                    else -> playMode.modeLabel()
                },
                tint = if (specialMode) MelodiaColors.Accent else MelodiaColors.TextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }

        val outlined = outlinedControls
        val transportOutline = if (outlined) Modifier.border(1.dp, MelodiaColors.Outline, CircleShape) else Modifier
        MelodiaIconButton(onClick = onPlayPrevious, style = MelodiaPress.Transport, modifier = transportOutline) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首", tint = MelodiaColors.TextPrimary, modifier = Modifier.size(40.dp))
        }
        PlayPauseFab(isPlaying, onTogglePlay, size = 56.dp)
        MelodiaIconButton(onClick = onPlayNext, style = MelodiaPress.Transport, modifier = transportOutline) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", tint = MelodiaColors.TextPrimary, modifier = Modifier.size(40.dp))
        }

        MelodiaIconButton(onClick = onQueueClick) {
            Icon(
                Icons.AutoMirrored.Rounded.PlaylistPlay,
                contentDescription = "播放队列",
                tint = MelodiaColors.TextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

// 大播放键：按下变淡，松手或点击后恢复
@Composable
private fun PlayPauseFab(
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    scale: Float = 1f,
    size: Dp = 72.dp
) {
    val motion = LocalUiMotion.current
    val outlined = outlinedControls
    val playInteraction = remember { MutableInteractionSource() }
    val isPressed by playInteraction.collectIsPressedAsState()
    val buttonAlpha = remember { Animatable(1f) }
    var clickTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            buttonAlpha.animateTo(0.70f, motion.tween(durationMillis = 80))
        } else if (clickTrigger == 0) {
            buttonAlpha.animateTo(1f, motion.tween(durationMillis = 200, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(clickTrigger) {
        if (clickTrigger > 0) {
            buttonAlpha.snapTo(0.70f)
            buttonAlpha.animateTo(1f, motion.tween(durationMillis = 220, easing = FastOutSlowInEasing))
        }
    }

    FloatingActionButton(
        onClick = {
            clickTrigger++
            onTogglePlay()
        },
        containerColor = if (outlined) Color.Transparent else MelodiaColors.TextPrimary,
        shape = styledButtonShape(CircleShape),
        elevation = if (outlined) FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp) else FloatingActionButtonDefaults.elevation(),
        interactionSource = playInteraction,
        modifier = Modifier
            .size(size * scale)
            .then(if (outlined) Modifier.border(2.dp, MelodiaColors.TextPrimary, styledButtonShape(CircleShape)) else Modifier)
            .graphicsLayer {
                alpha = buttonAlpha.value
            }
    ) {
        PlayPauseIcon(isPlaying, if (outlined) MelodiaColors.TextPrimary else MelodiaColors.InverseText, size = size * 0.58f * scale)
    }
}

// 插件布局用的控制行：按给定按钮与顺序排布，播客不显示随机与循环
@Composable
fun PlaybackControlRow(
    buttons: List<String>,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCyclePlayMode: () -> Unit,
    playMode: PlayMode,
    showModeButtons: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    val outlined = outlinedControls
    val transportOutline = if (outlined) Modifier.border(1.dp, MelodiaColors.Outline, CircleShape) else Modifier
    // 默认只有传输三键：居中留白；旧版自定义布局带 4~5 个键时改为两端对齐，避免最右侧按钮被挤出屏幕
    val crowded = buttons.size >= 4
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (crowded) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.spacedBy(28.dp * scale, Alignment.CenterHorizontally)
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        buttons.forEach { button ->
            when (button) {
                "playMode" -> if (showModeButtons) MelodiaIconButton(
                    onClick = onCyclePlayMode,
                    containerColor = if (playMode != PlayMode.LIST_LOOP) MelodiaColors.Accent.copy(alpha = 0.16f) else Color.Transparent
                ) {
                    Icon(
                        playMode.modeIcon(),
                        contentDescription = playMode.modeLabel(),
                        tint = if (playMode != PlayMode.LIST_LOOP) MelodiaColors.Accent else MelodiaColors.TextPrimary,
                        modifier = Modifier.size(28.dp * scale)
                    )
                }
                "shuffle" -> if (showModeButtons) MelodiaIconButton(
                    onClick = onToggleShuffle,
                    containerColor = if (playMode == PlayMode.SHUFFLE) MelodiaColors.Accent.copy(alpha = 0.16f) else Color.Transparent
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = if (playMode == PlayMode.SHUFFLE) "关闭随机播放" else "随机播放",
                        tint = if (playMode == PlayMode.SHUFFLE) MelodiaColors.Accent else MelodiaColors.TextSecondary,
                        modifier = Modifier.size(28.dp * scale)
                    )
                }
                "previous" -> MelodiaIconButton(onClick = onPlayPrevious, style = MelodiaPress.Transport, modifier = transportOutline) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首", tint = MelodiaColors.TextPrimary, modifier = Modifier.size(46.dp * scale))
                }
                "playPause" -> PlayPauseFab(isPlaying, onTogglePlay, scale)
                "next" -> MelodiaIconButton(onClick = onPlayNext, style = MelodiaPress.Transport, modifier = transportOutline) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", tint = MelodiaColors.TextPrimary, modifier = Modifier.size(46.dp * scale))
                }
                "repeat" -> if (showModeButtons) MelodiaIconButton(
                    onClick = onToggleRepeat,
                    containerColor = if (playMode != PlayMode.LIST_LOOP && playMode != PlayMode.SHUFFLE)
                        MelodiaColors.Accent.copy(alpha = 0.16f) else Color.Transparent
                ) {
                    Icon(
                        playMode.modeIcon(),
                        contentDescription = playMode.modeLabel(),
                        tint = if (playMode != PlayMode.LIST_LOOP && playMode != PlayMode.SHUFFLE)
                            MelodiaColors.Accent else MelodiaColors.TextSecondary,
                        modifier = Modifier.size(28.dp * scale)
                    )
                }
            }
        }
        // 合并后的模式按钮占了左侧一席，右侧补等宽占位，播放键才会停在屏幕正中
        if (showModeButtons && "playMode" in buttons) {
            Spacer(Modifier.width(48.dp * scale))
        }
    }
}

// 与图标按钮同宽的占位
private val ModeButtonSlot = 40.dp
