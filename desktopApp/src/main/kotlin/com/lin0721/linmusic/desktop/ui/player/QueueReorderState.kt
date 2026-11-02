package com.lin0721.linmusic.desktop.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.components.UiMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

// 播放队列拖拽排序状态，算法移植自移动端 PlayQueueSheet；只有“接下来播放”段可拖，
// 下标均为队列下标，与 LazyColumn 的内部下标靠 upcomingFirstLazyIndex 换算
@Stable
class QueueReorderState internal constructor(
    private val listState: LazyListState,
    private val motion: UiMotion,
    private val fallbackItemHeightPx: Float,
    private val topEdgePx: Float,
    private val bottomEdgePx: Float
) {
    var draggedIndex by mutableIntStateOf(-1)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set
    var settlingIndex by mutableIntStateOf(-1)
        private set
    val settleOffset = Animatable(0f)

    val isDragging: Boolean get() = draggedIndex >= 0

    private var upcomingStart = 0
    private var queueLastIndex = -1
    private var upcomingFirstLazyIndex = 0
    private var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }

    internal fun update(upcomingStart: Int, queueSize: Int, upcomingFirstLazyIndex: Int, onMove: (Int, Int) -> Unit) {
        this.upcomingStart = upcomingStart
        this.queueLastIndex = queueSize - 1
        this.upcomingFirstLazyIndex = upcomingFirstLazyIndex
        this.onMove = onMove
    }

    fun offsetOf(queueIndex: Int): Float = when {
        queueIndex == draggedIndex -> dragOffset
        queueIndex == settlingIndex -> settleOffset.value
        else -> 0f
    }

    // 同一时刻只允许一行处于拖拽态，避免并发手势同时驱动换位
    fun start(queueIndex: Int) {
        if (draggedIndex >= 0) return
        settlingIndex = -1
        draggedIndex = queueIndex
        dragOffset = 0f
    }

    fun drag(queueIndex: Int, deltaY: Float) {
        if (draggedIndex != queueIndex) return
        dragOffset = clampDragOffset(dragOffset + deltaY)
        advanceSwaps()
    }

    fun end(queueIndex: Int, scope: CoroutineScope) {
        if (draggedIndex != queueIndex) return
        val released = dragOffset
        draggedIndex = -1
        dragOffset = 0f
        if (released == 0f) return
        settlingIndex = queueIndex
        scope.launch {
            settleOffset.snapTo(released)
            settleOffset.animateTo(0f, motion.spring(stiffness = Spring.StiffnessMediumLow))
            settlingIndex = -1
        }
    }

    // 拖到列表边缘时自动滚屏并逐渐加速，按帧驱动，速度以 px/秒 表达再乘真实帧间隔
    suspend fun runEdgeScroll() {
        var edgeDurationMs = 0f
        var lastFrameNanos = 0L
        while (draggedIndex >= 0) {
            val frameNanos = withFrameNanos { it }
            val deltaMs = if (lastFrameNanos == 0L) {
                16f
            } else {
                ((frameNanos - lastFrameNanos) / 1_000_000f).coerceIn(1f, 64f)
            }
            lastFrameNanos = frameNanos

            val layoutInfo = listState.layoutInfo
            val draggingItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == lazyIndexOf(draggedIndex) }
            if (draggingItem == null) {
                edgeDurationMs = 0f
                continue
            }

            val currentTop = draggingItem.offset + dragOffset
            val currentBottom = currentTop + draggingItem.size
            val viewportEnd = layoutInfo.viewportEndOffset

            val isNearTop = currentTop < topEdgePx && draggedIndex > upcomingStart
            val isNearBottom = currentBottom > (viewportEnd - bottomEdgePx) && draggedIndex < queueLastIndex
            if (!isNearTop && !isNearBottom) {
                edgeDurationMs = 0f
                continue
            }

            edgeDurationMs += deltaMs
            val timeMultiplier = 1f + (edgeDurationMs / 600f).coerceAtMost(2.5f)
            val depthRatio = if (isNearTop) {
                ((topEdgePx - currentTop) / topEdgePx).coerceIn(0f, 1f)
            } else {
                ((currentBottom - (viewportEnd - bottomEdgePx)) / bottomEdgePx).coerceIn(0f, 1f)
            }
            val speedPxPerSec = (depthRatio * 1000f + 300f) * timeMultiplier
            val stepPx = speedPxPerSec * deltaMs / 1000f

            val scrolled = listState.scrollBy(if (isNearTop) -stepPx else stepPx)
            dragOffset = clampDragOffset(dragOffset + scrolled)
            advanceSwaps()
        }
    }

    private fun lazyIndexOf(queueIndex: Int): Int = upcomingFirstLazyIndex + (queueIndex - upcomingStart)

    private fun queueIndexOf(lazyIndex: Int): Int = upcomingStart + (lazyIndex - upcomingFirstLazyIndex)

    private fun draggedItemHeight(): Float =
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == lazyIndexOf(draggedIndex) }
            ?.size?.toFloat() ?: fallbackItemHeightPx

    // 滚出组合范围的行会被销毁并杀死其手势协程，不能把 draggedIndex 换到尚未组合出来的位置
    private fun maxComposedQueueIndex(): Int =
        listState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index }
            ?.let { queueIndexOf(it) } ?: queueLastIndex

    private fun minComposedQueueIndex(): Int =
        listState.layoutInfo.visibleItemsInfo.minOfOrNull { it.index }
            ?.let { queueIndexOf(it) } ?: upcomingStart

    // 上限取一整行高，与换位阈值一致：更小会在触边瞬间夹断 dragOffset 产生可见回跳
    private fun clampDragOffset(offset: Float): Float {
        val itemHeight = draggedItemHeight()
        return when {
            (draggedIndex <= upcomingStart || draggedIndex <= minComposedQueueIndex()) && offset < 0f ->
                max(offset, -itemHeight)
            (draggedIndex >= queueLastIndex || draggedIndex >= maxComposedQueueIndex()) && offset > 0f ->
                min(offset, itemHeight)
            else -> offset
        }
    }

    // 换位只是交换相邻两行内容，滚动位置本应原地不动；LazyColumn 却会让视口锚点跟着 key 跑到新下标，
    // 把被拖行顶出组合范围，因此涉及锚点时按原下标重新钉一次
    private fun neutralizeAnchorShift(from: Int, to: Int) {
        val anchor = listState.firstVisibleItemIndex
        if (lazyIndexOf(from) != anchor && lazyIndexOf(to) != anchor) return
        listState.requestScrollToItem(anchor, listState.firstVisibleItemScrollOffset)
    }

    // dragOffset 每越过一整行就与相邻行换位并扣掉一行高，使被拖行的视觉位置在换位前后保持连续
    private fun advanceSwaps() {
        val itemHeight = draggedItemHeight()
        while (dragOffset > itemHeight && draggedIndex < queueLastIndex && draggedIndex < maxComposedQueueIndex()) {
            neutralizeAnchorShift(draggedIndex, draggedIndex + 1)
            onMove(draggedIndex, draggedIndex + 1)
            draggedIndex += 1
            dragOffset = clampDragOffset(dragOffset - itemHeight)
        }
        while (dragOffset < -itemHeight && draggedIndex > upcomingStart && draggedIndex > minComposedQueueIndex()) {
            neutralizeAnchorShift(draggedIndex, draggedIndex - 1)
            onMove(draggedIndex, draggedIndex - 1)
            draggedIndex -= 1
            dragOffset = clampDragOffset(dragOffset + itemHeight)
        }
    }
}

private val FallbackItemHeight = 56.dp
private val TopEdgeThreshold = 80.dp
private val BottomEdgeThreshold = 100.dp

@Composable
fun rememberQueueReorderState(
    listState: LazyListState,
    upcomingStart: Int,
    queueSize: Int,
    upcomingFirstLazyIndex: Int,
    onMove: (from: Int, to: Int) -> Unit
): QueueReorderState {
    val density = LocalDensity.current
    val motion = LocalUiMotion.current
    val state = remember(listState, density, motion) {
        with(density) {
            QueueReorderState(listState, motion, FallbackItemHeight.toPx(), TopEdgeThreshold.toPx(), BottomEdgeThreshold.toPx())
        }
    }
    state.update(upcomingStart, queueSize, upcomingFirstLazyIndex, onMove)
    return state
}
