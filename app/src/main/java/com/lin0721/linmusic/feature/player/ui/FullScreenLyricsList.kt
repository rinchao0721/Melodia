package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.lyricLineKey
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius

// 全屏歌词列表区：加载态/空态、当前行自动居中定位、居中行推导与拖动定位覆盖层
@Composable
fun ColumnScope.FullScreenLyricsList(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    isLoading: Boolean,
    isUserScrolling: Boolean,
    highlightColor: Color,
    currentPositionProvider: () -> Long,
    lazyListState: LazyListState,
    viewportHeightPx: Float,
    onViewportHeightChange: (Float) -> Unit,
    gestureModifier: Modifier,
    onSeek: (Long) -> Unit,
    onLyricClick: (LyricLine) -> Unit
) {
    val density = LocalDensity.current

    LaunchedEffect(currentIndex, isUserScrolling, viewportHeightPx) {
        if (!isUserScrolling && currentIndex in lyrics.indices && viewportHeightPx > 0f) {
            val itemStridePx = with(density) { 66.dp.toPx() }
            val linesAboveCentre = (viewportHeightPx / 2 / itemStridePx).toInt()

            if (currentIndex < linesAboveCentre) {
                lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
                return@LaunchedEffect
            }

            val hasTranslation = lyrics[currentIndex].translation != null
            val itemHeightPx = with(density) {
                if (hasTranslation) 96.dp.toPx() else 54.dp.toPx()
            }
            val centreOffsetPx = -((viewportHeightPx - itemHeightPx) / 2f).toInt()
            lazyListState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = centreOffsetPx
            )
        }
    }

    val centerLineIndex by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf -1
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
            var minDistance = Float.MAX_VALUE
            var closestIndex = -1
            for (item in visibleItems) {
                val itemCenter = item.offset + item.size / 2f
                val distance = kotlin.math.abs(itemCenter - viewportCenter)
                if (distance < minDistance) {
                    minDistance = distance
                    closestIndex = item.index
                }
            }
            closestIndex
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp).align(Alignment.Center)
            )
        } else if (lyrics.isEmpty()) {
            Text(
                text = "暂无歌词",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            CenterTargetLine(
                visible = isUserScrolling,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.Center)
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(gestureModifier)
                    .onSizeChanged { onViewportHeightChange(it.height.toFloat()) },
                verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.lg),
                contentPadding = PaddingValues(
                    top = 0.dp,
                    bottom = with(density) { (viewportHeightPx / 2f).toDp() }
                ),
                horizontalAlignment = Alignment.Start
            ) {
                itemsIndexed(items = lyrics, key = ::lyricLineKey) { index, line ->
                    val isCurrent = index == currentIndex
                    val isCenterTarget = index == centerLineIndex && isUserScrolling
                    val distance = kotlin.math.abs(index - currentIndex).coerceAtMost(5)

                    FullScreenLyricsRow(
                        index = index,
                        line = line,
                        isCurrent = isCurrent,
                        isCenterTarget = isCenterTarget,
                        distance = distance,
                        highlightColor = highlightColor,
                        currentPositionProvider = currentPositionProvider,
                        onClick = { onLyricClick(line) }
                    )
                }
            }

            PlayCapsule(
                visible = isUserScrolling && centerLineIndex in lyrics.indices,
                targetLine = lyrics.getOrNull(centerLineIndex),
                onSeek = onSeek,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = MelodiaSpacing.md)
            )
        }
    }
}

// 用户滚动时出现的居中虚线基准，标示"松手即跳转"的目标位置
@Composable
private fun CenterTargetLine(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f),
                strokeWidth = 1f
            )
        }
    }
}

// 居中虚线右侧的跳转胶囊，显示目标行时间并点击定位播放
@Composable
private fun PlayCapsule(
    visible: Boolean,
    targetLine: LyricLine?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        if (targetLine != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .pressable(MelodiaPress.Pill) { onSeek(targetLine.timeMs) }
                    .clip(RoundedCornerShape(PillRadius))
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(horizontal = 14.dp, vertical = MelodiaSpacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "跳转到此处播放",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(MelodiaSpacing.xs))
                Text(
                    text = formatTime(targetLine.timeMs),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
