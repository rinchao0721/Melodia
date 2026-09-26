package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.SurfaceLight
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlin.math.abs

// 副歌圆点的吸附半径
private val ChorusSnapRadius = 12.dp
private val ChorusDotSize = 6.dp

// 进度条与两端时间：拖动期间用本地进度接管，松手才真正 seek；
// 副歌圆点播放越过后隐藏，拖动或点按靠近时吸附并震动一次
@Composable
fun ProgressSection(
    currentPositionProvider: () -> Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    chorusStartMs: Long? = null
) {
    var isSeeking by remember { mutableStateOf(false) }
    // 拖动增量累加在 raw 上，seekPosition 是吸附后的值
    var rawSeekPosition by remember { mutableFloatStateOf(0f) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var isSnappedToChorus by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val chorusFraction = if (chorusStartMs != null && duration > 0L && chorusStartMs in 1 until duration) {
        chorusStartMs.toFloat() / duration
    } else null

    val currentPosition = currentPositionProvider()
    val progress = if (duration > 0) {
        if (isSeeking) seekPosition else (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    } else 0f

    val thumbSize by animateDpAsState(
        targetValue = if (isSeeking) 14.dp else 8.dp,
        animationSpec = tween(150),
        label = "thumb_size"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.lg)
            .padding(top = MelodiaSpacing.sm)
    ) {
        var trackWidthPx by remember { mutableFloatStateOf(0f) }
        val density = LocalDensity.current
        val snapRadiusPx = with(density) { ChorusSnapRadius.toPx() }

        fun updateSeek(raw: Float) {
            rawSeekPosition = raw.coerceIn(0f, 1f)
            val snapped = chorusFraction != null && trackWidthPx > 0f &&
                abs(rawSeekPosition - chorusFraction) * trackWidthPx <= snapRadiusPx
            if (snapped && !isSnappedToChorus) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            isSnappedToChorus = snapped
            seekPosition = if (snapped && chorusFraction != null) chorusFraction else rawSeekPosition
        }

        fun endSeek(commit: Boolean) {
            isSeeking = false
            isSnappedToChorus = false
            if (commit) onSeek((seekPosition * duration).toLong())
        }

        // 自定义全宽进度条（两端 0 缩进，精准对齐外边缘与时间）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(duration, chorusFraction) {
                    detectTapGestures(
                        onPress = { offset ->
                            if (trackWidthPx > 0f && duration > 0L) {
                                isSeeking = true
                                updateSeek(offset.x / trackWidthPx)
                                endSeek(commit = tryAwaitRelease())
                            }
                        }
                    )
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (trackWidthPx > 0f && duration > 0L) {
                            isSeeking = true
                            updateSeek(rawSeekPosition + delta / trackWidthPx)
                        }
                    },
                    onDragStarted = {
                        isSeeking = true
                    },
                    onDragStopped = {
                        endSeek(commit = true)
                    }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            // 背景轨道（满宽 4dp）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceLight)
            )

            // 已播放进度轨道（满宽比例）
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )

            if (trackWidthPx > 0f && chorusFraction != null && progress < chorusFraction) {
                val dotOffsetDp = with(density) {
                    (chorusFraction * trackWidthPx - ChorusDotSize.toPx() / 2f).coerceIn(
                        0f,
                        trackWidthPx - ChorusDotSize.toPx()
                    ).toDp()
                }
                Box(
                    modifier = Modifier
                        .offset(x = dotOffsetDp)
                        .size(ChorusDotSize)
                        .background(Color.White, CircleShape)
                )
            }

            // 滑块圆点（居中跟随当前进度）
            if (trackWidthPx > 0f) {
                val thumbOffsetDp = with(density) {
                    (progress * trackWidthPx - thumbSize.toPx() / 2f).coerceIn(
                        0f,
                        trackWidthPx - thumbSize.toPx()
                    ).toDp()
                }
                Box(
                    modifier = Modifier
                        .offset(x = thumbOffsetDp)
                        .size(thumbSize)
                        .background(Color.White, CircleShape)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayPosition = if (isSeeking) (seekPosition * duration).toLong() else currentPosition
            Text(formatTime(displayPosition), color = TextGray, fontSize = 13.sp)
            Text(formatTime(duration), color = TextGray, fontSize = 13.sp)
        }
    }
}
