package com.lin0721.linmusic

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// 全屏播放器的展开/收起状态机：统一管理弹簧动画与拖拽跟手位移
class MelodiaPlayerSheetState(private val scope: CoroutineScope) {

    var isOpen by mutableStateOf(false)
        private set

    // 初始化为极大值，屏幕尺寸测量前播放器始终在屏幕外不可见
    var offsetY by mutableStateOf(10000f)
        private set

    var screenHeightPx by mutableStateOf(0f)
        private set

    // 不用 mutableStateOf，避免写入时触发不必要的 recomposition
    private val springJobRef = arrayOfNulls<Job>(1)

    fun onScreenSizeChanged(heightPx: Float) {
        screenHeightPx = heightPx
    }

    // 屏幕高度首次测量完成后把播放器初始化到屏幕底部
    internal fun settleToBottomIfClosed() {
        if (screenHeightPx > 0f && !isOpen) {
            offsetY = screenHeightPx
        }
    }

    fun animateTo(open: Boolean, velocity: Float, initialOffset: Float = Float.NaN) {
        springJobRef[0]?.cancel()
        if (open) {
            isOpen = true
            springJobRef[0] = scope.launch {
                animate(
                    initialValue = offsetY,
                    targetValue = 0f,
                    initialVelocity = velocity,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { value, _ -> offsetY = value.coerceIn(0f, screenHeightPx) }
            }
        } else {
            if (!initialOffset.isNaN()) {
                offsetY = initialOffset
            }
            springJobRef[0] = scope.launch {
                animate(
                    initialValue = offsetY,
                    targetValue = screenHeightPx,
                    initialVelocity = velocity,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { value, _ -> offsetY = value.coerceIn(0f, screenHeightPx) }
                offsetY = screenHeightPx
                isOpen = false
            }
        }
    }

    // 悬浮舱上拉跟手：取消进行中的弹簧动画，位移直接跟随手指
    fun onDrag(delta: Float) {
        springJobRef[0]?.cancel()
        if (!isOpen) isOpen = true
        offsetY = (offsetY + delta).coerceIn(0f, screenHeightPx)
    }

    // 松手后按位移比例或甩动速度决定展开还是回落
    fun onDragEnd(velocity: Float) {
        val shouldOpen = offsetY < screenHeightPx * 0.80f || velocity < -1000f
        animateTo(shouldOpen, velocity)
    }
}

@Composable
fun rememberMelodiaPlayerSheetState(): MelodiaPlayerSheetState {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { MelodiaPlayerSheetState(scope) }
    LaunchedEffect(state.screenHeightPx) {
        state.settleToBottomIfClosed()
    }
    return state
}
