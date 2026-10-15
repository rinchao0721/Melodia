package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.lyricLineKey
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import kotlinx.coroutines.delay


// 全屏歌词列表区：加载态/空态、当前行自动居中定位、居中行推导与拖动定位覆盖层
@Composable
fun FullScreenLyricsList(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    // 未传集合时沿用单行高亮；显式传空集合表示当前没有需要高亮的行。
    activeIndices: Set<Int>? = null,
    isLoading: Boolean,
    isUserScrolling: Boolean,
    highlightColor: Color,
    currentPositionProvider: () -> Long,
    lazyListState: LazyListState,
    viewportHeightPx: Float,
    onViewportHeightChange: (Float) -> Unit,
    gestureModifier: Modifier,
    modifier: Modifier = Modifier,
    topContentPadding: Dp = 0.dp,
    topFadeInsetPx: Float = 0f,
    bottomFadeInsetPx: Float = 0f,
    fontSize: Int = 22,
    alignment: String = "left",
    secondaryMode: String = "translation",
    lineSpacing: Int = 24,
    secondarySpacing: Int = 6,
    advancedKaraokeEffect: Boolean = true,
    karaokeGlowEffect: Boolean = false,
    isPlaying: Boolean = true,
    isShareMode: Boolean = false,
    selectedLyricKeys: Set<String> = emptySet(),
    onToggleSelect: ((String) -> Unit)? = null,
    onLyricLongPress: ((String) -> Unit)? = null,
    // 以下为宽屏播放器用：关掉居中线与播放胶囊、关掉列表自带拖动（由外层按命中规则接管）、上报每行文字范围
    showSeekGuide: Boolean = true,
    userScrollEnabled: Boolean = true,
    onLineTextBounds: ((index: Int, bounds: Rect) -> Unit)? = null,
    onSeek: (Long) -> Unit,
    onLyricClick: (LyricLine) -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val defaultHalfViewportDp = remember(configuration.screenHeightDp) { (configuration.screenHeightDp / 2).dp }
    val halfViewportPadding = if (viewportHeightPx > 0f) with(density) { (viewportHeightPx / 2f).toDp() } else defaultHalfViewportDp

    LaunchedEffect(currentIndex, isUserScrolling, viewportHeightPx, fontSize, secondaryMode, lineSpacing, secondarySpacing, isShareMode) {
        if (!isShareMode && !isUserScrolling && currentIndex in lyrics.indices && viewportHeightPx > 0f) {
            lazyListState.springScrollToCentre(
                targetIndex = currentIndex,
                viewportHeightPx = viewportHeightPx
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
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .widthIn(max = 760.dp)
        ) {
            // 切换歌词源时保留当前列表，后台完成后再替换，避免移除列表后重新排版。
            if (isLoading && lyrics.isEmpty()) {
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
                visible = !isShareMode && isUserScrolling && showSeekGuide,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.Center)
            )

            LazyColumn(
                state = lazyListState,
                userScrollEnabled = userScrollEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .then(gestureModifier)
                    .onSizeChanged { onViewportHeightChange(it.height.toFloat()) },
                verticalArrangement = Arrangement.spacedBy(lineSpacing.coerceAtLeast(0).dp),
                contentPadding = PaddingValues(
                    top = halfViewportPadding,
                    bottom = halfViewportPadding
                ),
                horizontalAlignment = when (alignment) {
                    "center" -> Alignment.CenterHorizontally
                    "right" -> Alignment.End
                    else -> Alignment.Start
                }
            ) {
                itemsIndexed(items = lyrics, key = ::lyricLineKey) { index, line ->
                    val isCurrent = activeIndices?.contains(index) ?: (index == currentIndex)
                    val isCenterTarget = index == centerLineIndex && isUserScrolling && showSeekGuide
                    val distance = kotlin.math.abs(index - currentIndex).coerceAtMost(5)

                    FullScreenLyricsRow(
                        index = index,
                        line = line,
                        isCurrent = isCurrent,
                        isCenterTarget = isCenterTarget,
                        distance = distance,
                        highlightColor = highlightColor,
                        currentPositionProvider = currentPositionProvider,
                        fontSize = fontSize,
                        alignment = alignment,
                        secondaryMode = secondaryMode,
                        secondarySpacing = secondarySpacing,
                        advancedKaraokeEffect = advancedKaraokeEffect,
                        karaokeGlowEffect = karaokeGlowEffect,
                        isPlaying = isPlaying,
                        isShareMode = isShareMode,
                        selectedLyricKeys = selectedLyricKeys,
                        onToggleSelect = onToggleSelect,
                        onLyricLongPress = onLyricLongPress,
                        onTextBoundsInRoot = onLineTextBounds?.let { report -> { bounds -> report(index, bounds) } },
                        onClick = { onLyricClick(line) }
                    )
                }
            }

            PlayCapsule(
                visible = !isShareMode && isUserScrolling && showSeekGuide && centerLineIndex in lyrics.indices,
                targetLine = lyrics.getOrNull(centerLineIndex),
                onSeek = onSeek,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = MelodiaSpacing.md)
            )
        }
    }
}
}

// 用户滚动时出现的居中虚线基准，标示"松手即跳转"的目标位置
@Composable
private fun CenterTargetLine(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(dampingRatio = 0.85f, stiffness = 300f)),
        exit = fadeOut(tween(180)),
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
        enter = fadeIn(spring(dampingRatio = 0.82f, stiffness = 380f)) +
                scaleIn(
                    initialScale = 0.82f,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)
                ),
        exit = fadeOut(tween(160)) +
                scaleOut(
                    targetScale = 0.85f,
                    animationSpec = tween(160)
                ),
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

// 物理弹簧阻尼居中平滑滚动（关键阻尼比 1.0f 零回弹零抽动，基于实际行高与视口真实中心精准居中）
private suspend fun LazyListState.springScrollToCentre(
    targetIndex: Int,
    viewportHeightPx: Float,
    dampingRatio: Float = Spring.DampingRatioNoBouncy,
    stiffness: Float = 260f
) {
    if (layoutInfo.totalItemsCount == 0 || targetIndex !in 0 until layoutInfo.totalItemsCount) return
    if (viewportHeightPx <= 0f) return

    var visibleItem = layoutInfo.visibleItemsInfo.find { it.index == targetIndex }

    // 如果目标项不在当前可见区域内（例如大跨度进度跳转/切歌），先平滑预对齐至中央附近
    if (visibleItem == null) {
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isNotEmpty()) {
            val firstVisible = visibleItems.first().index
            val lastVisible = visibleItems.last().index
            val approxVisibleCount = (lastVisible - firstVisible + 1).coerceAtLeast(1)
            val approxCenterLead = approxVisibleCount / 2
            val snapIndex = (targetIndex - approxCenterLead).coerceIn(0, layoutInfo.totalItemsCount - 1)
            this.scrollToItem(index = snapIndex, scrollOffset = 0)
        } else {
            this.scrollToItem(index = targetIndex, scrollOffset = 0)
        }
        visibleItem = layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
        if (visibleItem == null) {
            delay(16L)
            visibleItem = layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
        }
        if (visibleItem == null) {
            this.scrollToItem(index = targetIndex, scrollOffset = 0)
            delay(32L)
            visibleItem = layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
        }
    }

    if (visibleItem != null) {
        val activeLayout = this.layoutInfo
        val actualCenter = (activeLayout.viewportStartOffset + activeLayout.viewportEndOffset) / 2f
        val itemCenter = visibleItem.offset + visibleItem.size / 2f
        val deltaToScroll = itemCenter - actualCenter

        if (kotlin.math.abs(deltaToScroll) > 0.5f) {
            var previousValue = 0f
            val anim = Animatable(0f)
            val springSpec = spring<Float>(
                dampingRatio = dampingRatio,
                stiffness = stiffness
            )
            this.scroll {
                anim.animateTo(
                    targetValue = deltaToScroll,
                    animationSpec = springSpec
                ) {
                    val delta = this.value - previousValue
                    scrollBy(delta)
                    previousValue = this.value
                }
            }
        }
    } else {
        this.scrollToItem(index = targetIndex, scrollOffset = 0)
    }
}
