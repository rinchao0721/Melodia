package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// 全屏歌词页的下拉关闭状态机：顶栏直接拖动与列表边缘的嵌套滚动两条手势路径共用同一位移
class FullScreenLyricsDragState(
    private val scope: CoroutineScope,
    private val lazyListState: LazyListState,
    private val onClose: () -> Unit,
    private val onDragClose: () -> Unit = onClose,
    initialScreenHeightPx: Float = 0f
) {

    var offsetY by mutableFloatStateOf(0f)
        private set

    var viewportHeightPx by mutableFloatStateOf(0f)
        private set

    var screenHeightPx by mutableFloatStateOf(initialScreenHeightPx)
        private set

    // 一次手势是否起始于列表顶部，决定跟手阻尼与是否响应甩动关闭
    private var isGestureStartedAtTop = true
    private var isScrollGestureActive = false
    private var dragReleaseJob: Job? = null

    fun reset() {
        dragReleaseJob?.cancel()
        offsetY = 0f
        isScrollGestureActive = false
        isGestureStartedAtTop = true
    }

    fun onViewportHeightChange(heightPx: Float) {
        viewportHeightPx = heightPx
    }

    fun onScreenHeightChange(heightPx: Float) {
        screenHeightPx = heightPx
    }

    // 顶栏拖动整页跟手，不设阻尼
    fun onHeaderDrag(delta: Float) {
        offsetY = (offsetY + delta).coerceAtLeast(0f)
    }

    fun onHeaderDragStart() {
        isGestureStartedAtTop = true
    }

    // 松手后按位移比例或甩动速度决定关闭还是回弹
    fun handleDragRelease(velocity: Float = 0f) {
        dragReleaseJob?.cancel()
        dragReleaseJob = scope.launch {
            val totalHeight = if (screenHeightPx > 0f) screenHeightPx else 2400f
            val shouldClose = if (isGestureStartedAtTop) {
                offsetY > totalHeight * 0.10f || velocity > 450f
            } else {
                offsetY > totalHeight * 0.20f
            }

            if (offsetY > 0f && shouldClose) {
                animate(
                    initialValue = offsetY,
                    targetValue = totalHeight,
                    initialVelocity = velocity,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { value, _ ->
                    offsetY = value
                }
                onDragClose()
            } else {
                animate(
                    initialValue = offsetY,
                    targetValue = 0f,
                    initialVelocity = velocity,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { value, _ ->
                    offsetY = value.coerceAtLeast(0f)
                }
            }
        }
    }

    // 起始于列表中部的下拉施加 0.3 阻尼，避免误触把整页拽走
    private fun damping(): Float = if (isGestureStartedAtTop) 1.0f else 0.3f

    private fun markGestureStart(source: NestedScrollSource) {
        if (source == NestedScrollSource.UserInput && !isScrollGestureActive) {
            isScrollGestureActive = true
            isGestureStartedAtTop = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
        }
    }

    val nestedScrollConnection = object : NestedScrollConnection {

        // 页面已被拉下时，先用上滑量把页面推回原位，再把余量交给列表
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            markGestureStart(source)

            return if (offsetY > 0f && available.y < 0f) {
                val damping = damping()
                val delta = available.y * damping
                val consumed = delta.coerceAtLeast(-offsetY)
                offsetY += consumed
                Offset(0f, consumed / damping)
            } else {
                Offset.Zero
            }
        }

        // 列表已滚到顶仍继续下拉时，多余位移转为整页下移
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            markGestureStart(source)

            val isAtTop = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            return if (available.y > 0f && isAtTop && source == NestedScrollSource.UserInput) {
                offsetY += available.y * damping()
                Offset(0f, available.y)
            } else {
                Offset.Zero
            }
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            isScrollGestureActive = false
            return if (offsetY > 0f) {
                handleDragRelease(velocity = available.y)
                available
            } else {
                Velocity.Zero
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            isScrollGestureActive = false
            return if (offsetY > 0f) {
                handleDragRelease(velocity = available.y)
                available
            } else {
                Velocity.Zero
            }
        }
    }
}

// 无 key 的 remember：与原实现一致，连接对象只在首次组合时创建并捕获当时的 onClose，
// 后续重组不重建。改为带 key 会让嵌套滚动与顶栏两条路径的回调时机产生差异。
@Composable
fun rememberFullScreenLyricsDragState(
    lazyListState: LazyListState,
    onClose: () -> Unit,
    onDragClose: () -> Unit = onClose,
    screenHeightPx: Float = 0f
): FullScreenLyricsDragState {
    val scope = rememberCoroutineScope()
    val state = remember {
        FullScreenLyricsDragState(
            scope = scope,
            lazyListState = lazyListState,
            onClose = onClose,
            onDragClose = onDragClose,
            initialScreenHeightPx = screenHeightPx
        )
    }
    if (screenHeightPx > 0f && state.screenHeightPx != screenHeightPx) {
        state.onScreenHeightChange(screenHeightPx)
    }
    return state
}
