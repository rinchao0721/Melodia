package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

// 下拉关闭手势与内容列表滚动的联动。
// 手势状态以 MutableState 实例透传，确保与调用方共享同一份状态。
@Composable
fun rememberFullPlayerNestedScrollConnection(
    listState: LazyListState,
    offsetYState: MutableState<Float>,
    isScrollGestureActiveState: MutableState<Boolean>,
    isGestureStartedAtTopState: MutableState<Boolean>,
    onDragRelease: (Float) -> Unit
): NestedScrollConnection {
    var offsetY by offsetYState
    var isScrollGestureActive by isScrollGestureActiveState
    var isGestureStartedAtTop by isGestureStartedAtTopState

    return remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (!isScrollGestureActive) {
                        isScrollGestureActive = true
                        isGestureStartedAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    }
                }

                return if (offsetY > 0f && available.y < 0f) {
                    val damping = if (isGestureStartedAtTop) 1.0f else 0.3f
                    val delta = available.y * damping
                    val consumed = delta.coerceAtLeast(-offsetY)
                    offsetY += consumed
                    Offset(0f, consumed / damping)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (!isScrollGestureActive) {
                        isScrollGestureActive = true
                        isGestureStartedAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    }
                }

                val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                return if (available.y > 0f && isAtTop && source == NestedScrollSource.UserInput) {
                    val damping = if (isGestureStartedAtTop) 1.0f else 0.3f
                    offsetY += available.y * damping
                    Offset(0f, available.y)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                isScrollGestureActive = false
                return if (offsetY > 0f) {
                    onDragRelease(available.y)
                    available
                } else {
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isScrollGestureActive = false
                return if (offsetY > 0f) {
                    onDragRelease(available.y)
                    available
                } else {
                    Velocity.Zero
                }
            }
        }
    }
}
