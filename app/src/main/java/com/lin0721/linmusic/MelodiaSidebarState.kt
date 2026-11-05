package com.lin0721.linmusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AppSidebarState {
    Closed, Open
}

// 侧边栏推拉状态机：锚点、边缘手势判定与展开进度
@OptIn(ExperimentalFoundationApi::class)
class MelodiaSidebarState(
    private val scope: CoroutineScope,
    val widthPx: Float,
    private val velocityThresholdPx: Float,
    val draggableState: AnchoredDraggableState<AppSidebarState>
) {

    // 边缘滑动判断：避免抽屉打开手势与列表左右滑动冲突
    var isTouchStartingAtEdge by mutableStateOf(false)
        private set

    val isOpen: Boolean get() = draggableState.currentValue == AppSidebarState.Open

    // 0f 完全收起，1f 完全展开；测量完成前 offset 为 NaN
    val progress: Float
        get() {
            val offset = draggableState.offset
            return if (offset.isNaN()) 0f else (offset / widthPx).coerceIn(0f, 1f)
        }

    val offsetX: Float
        get() = draggableState.offset.let { if (it.isNaN()) 0f else it }

    // 侧边栏已打开时允许在任意位置向左滑动关闭；关闭时仅允许在左边缘向右拉出
    fun onPointerDown(positionX: Float, edgeWidthPx: Float) {
        isTouchStartingAtEdge = isOpen || positionX < edgeWidthPx
        isHandoffRequested = false
        isHandoffActive = false
    }

    fun onPointerUp() {
        isTouchStartingAtEdge = false
        if (isHandoffActive) settle(handoffVelocity.calculateVelocity().x)
        isHandoffRequested = false
        isHandoffActive = false
    }

    // 主页横向列表滚到最左仍在右滑时，由根布局接管手势拉出侧栏。
    // 列表位于随侧栏平移的内容层里，不能直接把它的滚动量转给侧栏，否则手指与内容同步移动，局部位移趋近于零
    var isHomeHandoffEnabled = false
    private var isHandoffRequested = false
    private var isHandoffActive = false
    private val handoffVelocity = VelocityTracker()

    val homeHandoffConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (isHomeHandoffEnabled && source == NestedScrollSource.UserInput && available.x > 0f && !isOpen) {
                isHandoffRequested = true
            }
            return Offset.Zero
        }
    }

    // 根布局 Initial 阶段逐事件调用，坐标系不随内容层平移；接管后消费事件，子列表随之放弃拖动
    fun onRootPointerChange(change: PointerInputChange) {
        if (!isHandoffRequested) return
        if (!isHandoffActive) {
            isHandoffActive = true
            handoffVelocity.resetTracking()
        }
        handoffVelocity.addPosition(change.uptimeMillis, change.position)
        draggableState.dispatchRawDelta(change.position.x - change.previousPosition.x)
        change.consume()
    }

    private fun settle(velocityX: Float) {
        val target = when {
            velocityX > velocityThresholdPx -> AppSidebarState.Open
            velocityX < -velocityThresholdPx -> AppSidebarState.Closed
            progress >= 0.5f -> AppSidebarState.Open
            else -> AppSidebarState.Closed
        }
        scope.launch { draggableState.animateTo(target) }
    }

    fun open() {
        scope.launch { draggableState.animateTo(AppSidebarState.Open) }
    }

    fun close() {
        scope.launch { draggableState.animateTo(AppSidebarState.Closed) }
    }

    // 收起后再执行，避免与页面切换动画叠在一起；弹簧尾段较长，接近收起即执行。被手势打断时不执行
    fun closeThen(action: () -> Unit) {
        scope.launch {
            var done = false
            val watcher = launch {
                snapshotFlow { progress }.first { it <= ActionProgressThreshold }
                done = true
                action()
            }
            draggableState.animateTo(AppSidebarState.Closed)
            if (!done) {
                watcher.cancel()
                action()
            }
        }
    }

    private companion object {
        const val ActionProgressThreshold = 0.1f
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberMelodiaSidebarState(width: androidx.compose.ui.unit.Dp = 310.dp): MelodiaSidebarState {
    val motion = LocalUiMotion.current
    val scope = rememberCoroutineScope()
    val density: Density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }
    val velocityThresholdPx = with(density) { 100.dp.toPx() }

    val draggableState = remember {
        AnchoredDraggableState(
            initialValue = AppSidebarState.Closed,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { velocityThresholdPx },
            snapAnimationSpec = motion.spring(stiffness = Spring.StiffnessMediumLow),
            decayAnimationSpec = exponentialDecay()
        )
    }

    LaunchedEffect(widthPx) {
        draggableState.updateAnchors(
            DraggableAnchors {
                AppSidebarState.Closed at 0f
                AppSidebarState.Open at widthPx
            }
        )
    }

    return remember(scope, widthPx, velocityThresholdPx, draggableState) {
        MelodiaSidebarState(scope, widthPx, velocityThresholdPx, draggableState)
    }
}
