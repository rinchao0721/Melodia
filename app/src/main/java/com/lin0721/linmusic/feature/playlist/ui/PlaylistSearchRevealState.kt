package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// ────────────────────────────────────────────────────────────────────────────
// 搜索栏下拉展开：橡皮筋阻尼位移 + fling 速度带入弹簧收尾。
// 替代旧版靠拦截/清零 delta 实现吸附的写法（未达阈值时画面零反馈、fling 速度被无差别
// 清零、松手后另起一个不连续的补偿动画），这里全程用同一个 revealPx 实时驱动视觉位移，
// 没有死区，也不丢惯性。写法参照 FullPlayerDragNestedScroll 的 onPre/onPostScroll 分工。
// ────────────────────────────────────────────────────────────────────────────
class SearchBarRevealState internal constructor(
    private val listState: LazyListState,
    private val isDailyRecommend: Boolean,
    private val scope: CoroutineScope,
    private val flingVelocityThresholdPx: Float
) {
    // 搜索栏自身高度，由调用方在 onSizeChanged 中回填
    var searchBarHeightPx by mutableFloatStateOf(0f)

    // 0f 完全隐藏，searchBarHeightPx 完全展开
    var revealPx by mutableFloatStateOf(0f)
        private set

    // 搜索态的离散意图：松手那一刻就确定，不随回弹动画途中的 revealPx 数值抖动
    var isOpen by mutableStateOf(false)
        private set

    private var settleJob: Job? = null

    // 继续下拉：越接近展开上限，同样的拖拽距离换来的位移越小，橡皮筋阻尼只作用于这个方向
    private fun applyDampedOpen(delta: Float) {
        val max = searchBarHeightPx
        if (max <= 0f) return
        val remaining = (max - revealPx).coerceAtLeast(0f)
        val dampingFactor = (remaining / max).coerceIn(0f, 1f)
        revealPx = (revealPx + delta * dampingFactor).coerceIn(0f, max)
    }

    // 收起：不加阻尼，跟手直接响应，否则展开量接近上限时会因为阻尼系数趋近 0 而收不回去
    private fun applyClose(delta: Float) {
        val max = searchBarHeightPx
        if (max <= 0f) return
        revealPx = (revealPx + delta).coerceIn(0f, max)
    }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (isDailyRecommend || isOpen || revealPx <= 0f || available.y >= 0f) return Offset.Zero
            settleJob?.cancel()
            applyClose(available.y)
            return available
        }

        // 列表已经在顶部、自己消费不掉的下拉量，才轮到搜索栏展开
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (isDailyRecommend || isOpen || source != NestedScrollSource.UserInput || available.y <= 0f) {
                return Offset.Zero
            }
            val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            if (!atTop) return Offset.Zero
            settleJob?.cancel()
            applyDampedOpen(available.y)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (isDailyRecommend || isOpen || revealPx <= 0f) return Velocity.Zero
            settle(available.y)
            return available
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (isDailyRecommend || isOpen || revealPx <= 0f) return Velocity.Zero
            settle(available.y)
            return available
        }
    }

    // 搜索返回箭头专用：展开态下唯一允许把它关掉的入口
    fun close() {
        val max = searchBarHeightPx
        if (max <= 0f || !isOpen) return
        isOpen = false
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(
                initialValue = revealPx,
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { value, _ ->
                revealPx = value.coerceIn(0f, max)
            }
        }
    }

    // 松手收尾：位置过半或速度足够快都判定为对应方向，把手指的惯性带进弹簧动画
    private fun settle(velocity: Float) {
        val max = searchBarHeightPx
        if (max <= 0f) return
        val target = when {
            velocity > flingVelocityThresholdPx -> max
            velocity < -flingVelocityThresholdPx -> 0f
            revealPx > max / 2f -> max
            else -> 0f
        }
        isOpen = target > 0f
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(
                initialValue = revealPx,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { value, _ ->
                revealPx = value.coerceIn(0f, max)
            }
        }
    }
}

@Composable
fun rememberSearchBarRevealState(
    listState: LazyListState,
    isDailyRecommend: Boolean
): SearchBarRevealState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val flingVelocityThresholdPx = with(density) { 300.dp.toPx() }
    return remember(listState, isDailyRecommend) {
        SearchBarRevealState(
            listState = listState,
            isDailyRecommend = isDailyRecommend,
            scope = scope,
            flingVelocityThresholdPx = flingVelocityThresholdPx
        )
    }
}
