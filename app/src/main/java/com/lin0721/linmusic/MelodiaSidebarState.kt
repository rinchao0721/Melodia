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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class AppSidebarState {
    Closed, Open
}

// 侧边栏推拉状态机：锚点、边缘手势判定与展开进度
@OptIn(ExperimentalFoundationApi::class)
class MelodiaSidebarState(
    private val scope: CoroutineScope,
    val widthPx: Float,
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
    }

    fun onPointerUp() {
        isTouchStartingAtEdge = false
    }

    fun open() {
        scope.launch { draggableState.animateTo(AppSidebarState.Open) }
    }

    fun close() {
        scope.launch { draggableState.animateTo(AppSidebarState.Closed) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberMelodiaSidebarState(width: androidx.compose.ui.unit.Dp = 310.dp): MelodiaSidebarState {
    val motion = LocalUiMotion.current
    val scope = rememberCoroutineScope()
    val density: Density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }

    val draggableState = remember {
        AnchoredDraggableState(
            initialValue = AppSidebarState.Closed,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
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

    return remember(scope, widthPx, draggableState) {
        MelodiaSidebarState(scope, widthPx, draggableState)
    }
}
