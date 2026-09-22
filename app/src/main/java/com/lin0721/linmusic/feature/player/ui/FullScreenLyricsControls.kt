package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.DragHandleShape
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlin.math.roundToInt

// ────────────────────────────────────────────────────────────────────────────
// 全屏歌词操作工具栏（翻译/罗马音切换、分享、快捷歌词设置）
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun FullScreenLyricsToolbar(
    secondaryMode: String,
    hasTranslation: Boolean,
    hasRoma: Boolean,
    onToggleSecondaryMode: () -> Unit,
    onShareClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：翻译/罗马音多态切换按钮（文A / 音A）
        val hasAnySecondary = hasTranslation || hasRoma
        val (badgeText, badgeSub) = when (secondaryMode) {
            "roma" -> "音" to "A"
            else -> "文" to "A"
        }
        val badgeTint = when {
            !hasAnySecondary -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            secondaryMode == "none" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            else -> Color.White
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .pressable(if (hasAnySecondary) MelodiaPress.Icon else MelodiaPress.None) {
                    if (hasAnySecondary) onToggleSecondaryMode()
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = badgeText,
                    color = badgeTint,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = badgeSub,
                    color = badgeTint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 1.dp, bottom = 1.dp)
                )
            }
        }

        // 右侧操作项：分享与歌词快捷设置
        Row(
            horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MelodiaIconButton(onClick = onShareClick) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "分享歌词",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            MelodiaIconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "歌词设置",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// iOS 胶囊风格歌词字号滑块组件（平滑填充、触感良好、无生硬 steps 点）
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun LyricCapsuleSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 16f..32f,
    modifier: Modifier = Modifier
) {
    var componentWidthPx by remember { mutableFloatStateOf(1f) }
    val progressFraction = remember(value, valueRange) {
        ((value.toFloat() - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    }

    val updateValueFromPosition = rememberUpdatedState { xPx: Float ->
        if (componentWidthPx > 0) {
            val fraction = (xPx / componentWidthPx).coerceIn(0f, 1f)
            val rawValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
            onValueChange(rawValue.roundToInt())
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { componentWidthPx = it.width.toFloat() }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        updateValueFromPosition.value(offset.x)
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        updateValueFromPosition.value(offset.x)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        updateValueFromPosition.value(change.position.x)
                    }
                )
            }
    ) {
        // 激活填充进度
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progressFraction)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )

        // 胶囊两端字号视觉指示符
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "A",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = "A",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// 全屏歌词快捷设置弹窗（字号大小、对齐方式、副文本展示）
// ────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenLyricsSettingsSheet(
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    alignment: String,
    onAlignmentChange: (String) -> Unit,
    secondaryMode: String,
    onSecondaryModeChange: (String) -> Unit,
    hasTranslation: Boolean,
    hasRoma: Boolean,
    advancedKaraokeEffect: Boolean = true,
    onAdvancedKaraokeEffectChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = MelodiaSpacing.xs)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(DragHandleShape)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MelodiaSpacing.lg)
                .padding(bottom = MelodiaSpacing.lg)
        ) {
            Text(
                text = "全屏歌词设置",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = MelodiaSpacing.md)
            )

            // 字号调节（iOS 风格胶囊滑块）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("歌词字号大小", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Text(
                        text = "${fontSize} sp",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LyricCapsuleSlider(
                    value = fontSize,
                    onValueChange = onFontSizeChange,
                    valueRange = 16f..32f
                )
            }

            Spacer(modifier = Modifier.height(MelodiaSpacing.sm))

            // 对齐方式（左对齐 / 居中对齐 / 右对齐）
            Text(
                text = "歌词对齐方式",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            val alignments = listOf(
                "left" to "左对齐",
                "center" to "居中对齐",
                "right" to "右对齐"
            )
            alignments.forEach { (key, label) ->
                val isSelected = alignment == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAlignmentChange(key) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(MelodiaSpacing.sm))

            // 歌词副文本设置（翻译 / 罗马音 / 仅原词 二选一展示）
            Text(
                text = "歌词副文本",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            val secondaryOptions = listOf(
                "translation" to ("中文翻译" to hasTranslation),
                "roma" to ("罗马音" to hasRoma),
                "none" to ("关闭（仅原词）" to true)
            )
            secondaryOptions.forEach { (key, pair) ->
                val (label, isAvailable) = pair
                val isSelected = secondaryMode == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = isAvailable) { onSecondaryModeChange(key) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isAvailable) label else "$label (暂无)",
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isAvailable -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        fontSize = 15.sp
                    )
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(MelodiaSpacing.sm))

            // 逐字歌词流光动效
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAdvancedKaraokeEffectChange(!advancedKaraokeEffect) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "逐字歌词流光动效",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "开启柔和渐变推进边缘",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = advancedKaraokeEffect,
                    onCheckedChange = onAdvancedKaraokeEffectChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// 全屏歌词页的播放控制区（工具栏 + 进度条 + 五键播放控制）
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
    secondaryMode: String,
    hasTranslation: Boolean,
    hasRoma: Boolean,
    onToggleSecondaryMode: () -> Unit,
    onShareLyrics: () -> Unit,
    onLyricsSettingsClick: () -> Unit,
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
            .padding(horizontal = MelodiaSpacing.lg)
            .padding(bottom = 20.dp)
    ) {
        // 1. 顶部插入操作工具栏（翻译/罗马音多态切换、分享、歌词设置）
        FullScreenLyricsToolbar(
            secondaryMode = secondaryMode,
            hasTranslation = hasTranslation,
            hasRoma = hasRoma,
            onToggleSecondaryMode = onToggleSecondaryMode,
            onShareClick = onShareLyrics,
            onSettingsClick = onLyricsSettingsClick
        )

        Spacer(modifier = Modifier.height(MelodiaSpacing.xs))

        // 2. 进度条
        var trackWidthPx by remember { mutableFloatStateOf(0f) }
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(duration) {
                    detectTapGestures(
                        onPress = { offset ->
                            if (trackWidthPx > 0f && duration > 0L) {
                                isSeeking = true
                                val newProgress = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                                seekPosition = newProgress
                                val released = tryAwaitRelease()
                                if (released) {
                                    isSeeking = false
                                    onSeek((seekPosition * duration).toLong())
                                } else {
                                    isSeeking = false
                                }
                            }
                        }
                    )
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (trackWidthPx > 0f && duration > 0L) {
                            isSeeking = true
                            seekPosition = (seekPosition + delta / trackWidthPx).coerceIn(0f, 1f)
                        }
                    },
                    onDragStarted = { isSeeking = true },
                    onDragStopped = {
                        isSeeking = false
                        onSeek((seekPosition * duration).toLong())
                    }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            // 背景底轨
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )
            // 播放高亮条
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White)
            )
            // 进度滑块 Thumb
            val thumbSize = if (isSeeking) 14.dp else 8.dp
            val thumbRadiusPx = with(density) { (thumbSize / 2).toPx() }
            val thumbOffsetPx = (progress.coerceIn(0f, 1f) * trackWidthPx - thumbRadiusPx)
                .coerceIn(0f, (trackWidthPx - thumbRadiusPx * 2).coerceAtLeast(0f))
            val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }
            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetDp)
                    .size(thumbSize)
                    .background(Color.White, CircleShape)
            )
        }

        // 3. 当前播放时间与总时长（两端完全对齐进度条轨道与工具栏两端）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayPosition = if (isSeeking) (seekPosition * duration).toLong() else currentPosition
            Text(formatTime(displayPosition), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(formatTime(duration), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(MelodiaSpacing.sm))

        // 4. 底部播放控制五键（两端严格对齐）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MelodiaSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .pressable(MelodiaPress.Icon) { onToggleShuffle() },
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = null,
                    tint = if (playMode == PlayMode.SHUFFLE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .pressable(MelodiaPress.Icon) { onToggleRepeat() },
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = if (playMode == PlayMode.SINGLE_LOOP) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = null,
                    tint = if (playMode == PlayMode.SINGLE_LOOP) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

