package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.core.ui.theme.uiSpring
import com.lin0721.linmusic.core.ui.theme.uiTween

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
    modifier: Modifier = Modifier.fillMaxSize(),
    centerFirstLine: Boolean = false,
    topInsetPx: Float = 0f,
    fontSize: Int = 22,
    alignment: String = "left",
    secondaryMode: String = "translation",
    lineSpacing: Int = 24,
    secondarySpacing: Int = 6,
    advancedKaraokeEffect: Boolean = true,
    karaokeGlowEffect: Boolean = false,
    isPlaying: Boolean = true,
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
    val effectiveTopInsetPx = if (topInsetPx > 0f) topInsetPx else with(density) { EdgeFadeHeight.toPx() }
    val effectiveTopInsetDp = with(density) { effectiveTopInsetPx.toDp() }

    LaunchedEffect(currentIndex, lyrics, isUserScrolling, viewportHeightPx, fontSize, secondaryMode, lineSpacing, secondarySpacing, centerFirstLine, topInsetPx) {
        if (isUserScrolling || viewportHeightPx <= 0f) return@LaunchedEffect

        // 新歌前奏或歌词重载时当前行尚未开始：回到顶部，否则列表停在上一首的滚动位置
        if (currentIndex < 0) {
            if (lyrics.isNotEmpty()) {
                lazyListState.springScrollToCentreClassic(
                    targetIndex = 0,
                    desiredOffsetPx = effectiveTopInsetPx.toInt(),
                    fallbackScrollOffsetPx = 0
                )
            }
            return@LaunchedEffect
        }
        if (currentIndex !in lyrics.indices) return@LaunchedEffect

        if (centerFirstLine) {
            lazyListState.springScrollToCentre(
                targetIndex = currentIndex,
                viewportHeightPx = viewportHeightPx
            )
            return@LaunchedEffect
        }

        // 估算值以默认间距（行距 24dp、副文本距 6dp）为基准，按设置的差值修正
        val itemStridePx = with(density) { (66 + lineSpacing - 24).coerceAtLeast(1).dp.toPx() }
        val linesAboveCentre = (viewportHeightPx / 2 / itemStridePx).toInt()

        if (currentIndex < linesAboveCentre) {
            lazyListState.springScrollToCentreClassic(
                targetIndex = 0,
                desiredOffsetPx = effectiveTopInsetPx.toInt(),
                fallbackScrollOffsetPx = 0
            )
            return@LaunchedEffect
        }

        val current = lyrics[currentIndex]
        val hasSecondary = current.backgroundLine != null || when (secondaryMode) {
            "translation" -> current.translation != null
            "roma" -> current.roma != null
            else -> false
        }
        val itemHeightPx = with(density) {
            (if (hasSecondary) 96 + secondarySpacing - 6 else 54).coerceAtLeast(1).dp.toPx()
        }
        val desiredOffsetPx = ((viewportHeightPx - itemHeightPx) / 2f).toInt()
        lazyListState.springScrollToCentreClassic(
            targetIndex = currentIndex,
            desiredOffsetPx = desiredOffsetPx,
            fallbackScrollOffsetPx = -desiredOffsetPx
        )
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
                visible = isUserScrolling && showSeekGuide,
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
                    .verticalEdgeFade(top = EdgeFadeHeight, bottom = EdgeFadeHeight)
                    .then(gestureModifier)
                    .onSizeChanged { onViewportHeightChange(it.height.toFloat()) },
                verticalArrangement = Arrangement.spacedBy(lineSpacing.coerceAtLeast(0).dp),
                contentPadding = PaddingValues(
                    top = if (centerFirstLine) halfViewportPadding else effectiveTopInsetDp,
                    bottom = if (centerFirstLine) halfViewportPadding else with(density) { (viewportHeightPx / 2f).toDp() }
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
                        onTextBoundsInRoot = onLineTextBounds?.let { report -> { bounds -> report(index, bounds) } },
                        onClick = { onLyricClick(line) }
                    )
                }
            }

            PlayCapsule(
                visible = isUserScrolling && showSeekGuide && centerLineIndex in lyrics.indices,
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
        enter = fadeIn(uiSpring(dampingRatio = 0.85f, stiffness = 300f)),
        exit = fadeOut(uiTween(180)),
        modifier = modifier
    ) {
        val lineColor = MelodiaColors.Overlay.copy(alpha = 0.2f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = lineColor,
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
        enter = fadeIn(uiSpring(dampingRatio = 0.82f, stiffness = 380f)) +
                scaleIn(
                    initialScale = 0.82f,
                    animationSpec = uiSpring(dampingRatio = 0.75f, stiffness = 400f)
                ),
        exit = fadeOut(uiTween(160)) +
                scaleOut(
                    targetScale = 0.85f,
                    animationSpec = uiTween(160)
                ),
        modifier = modifier
    ) {
        if (targetLine != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .pressable(MelodiaPress.Pill) { onSeek(targetLine.timeMs) }
                    .clip(RoundedCornerShape(PillRadius))
                    .background(MelodiaColors.Overlay.copy(alpha = 0.2f))
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

    // 目标行不在可见区时先瞬移到附近，再等一帧布局后精确居中
    if (visibleItem == null) {
        val visibleItems = layoutInfo.visibleItemsInfo
        val lead = if (visibleItems.isNotEmpty()) {
            (visibleItems.last().index - visibleItems.first().index + 1).coerceAtLeast(1) / 2
        } else 0
        scrollToItem(index = (targetIndex - lead).coerceIn(0, layoutInfo.totalItemsCount - 1), scrollOffset = 0)
        withFrameNanos { }
        visibleItem = layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
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
    }
}

// 原样式：物理弹簧阻尼居中平滑滚动，首行吸顶
private suspend fun LazyListState.springScrollToCentreClassic(
    targetIndex: Int,
    desiredOffsetPx: Int,
    fallbackScrollOffsetPx: Int,
    dampingRatio: Float = 0.82f,
    stiffness: Float = 360f
) {
    val layoutInfo = this.layoutInfo
    val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == targetIndex }

    if (visibleItem != null) {
        val currentOffset = visibleItem.offset
        val deltaToScroll = (currentOffset - desiredOffsetPx).toFloat()

        if (kotlin.math.abs(deltaToScroll) > 1f) {
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
            return
        }
    }

    this.animateScrollToItem(index = targetIndex, scrollOffset = fallbackScrollOffsetPx)
}

// 默认歌词视口边缘羽化高度
private val EdgeFadeHeight = 32.dp

// 视口上下边缘渐隐：用 DstIn 把内容按渐变蒙版擦淡
internal fun Modifier.verticalEdgeFade(top: Dp = EdgeFadeHeight, bottom: Dp = EdgeFadeHeight): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val height = size.height
        if (height <= 0f) return@drawWithContent
        val topFraction = (top.toPx() / height).coerceIn(0f, 0.5f)
        val bottomFraction = (bottom.toPx() / height).coerceIn(0f, 0.5f)
        val stops = buildList {
            add(0f to if (topFraction > 0f) Color.Transparent else Color.Black)
            if (topFraction > 0f) add(topFraction to Color.Black)
            add(1f - bottomFraction to Color.Black)
            add(1f to if (bottomFraction > 0f) Color.Transparent else Color.Black)
        }
        drawRect(Brush.verticalGradient(colorStops = stops.toTypedArray()), blendMode = BlendMode.DstIn)
    }
