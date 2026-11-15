package com.lin0721.linmusic

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.lin0721.linmusic.core.ui.back.decelerateBackProgress
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.UiMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal const val PLAYER_BACK_MAX_OFFSET_FRACTION = 0.08f

// 根据手势进度计算全屏播放页返回时的跟手下移像素
internal fun computePlayerSheetBackOffset(progress: Float, screenHeightPx: Float): Float {
    if (screenHeightPx <= 0f) return 0f
    return (screenHeightPx * PLAYER_BACK_MAX_OFFSET_FRACTION * decelerateBackProgress(progress))
        .coerceIn(0f, screenHeightPx)
}

// 全屏播放器的展开/收起状态机：统一管理弹簧动画与拖拽跟手位移
class MelodiaPlayerSheetState(private val scope: CoroutineScope, private val motion: UiMotion) {

    var isOpen by mutableStateOf(false)
        private set

    // 初始化为极大值，屏幕尺寸测量前播放器始终在屏幕外不可见
    var offsetY by mutableStateOf(10000f)
        private set

    var screenHeightPx by mutableStateOf(0f)
        private set

    // 预见式返回手势驱动的 0..1 缩小进度，供覆盖层在绘制阶段读取
    var backShrinkFraction by mutableFloatStateOf(0f)
        private set

    // 不用 mutableStateOf，避免写入时触发不必要的 recomposition
    private val springJobRef = arrayOfNulls<Job>(1)
    // 标记是否已进入关闭动画，避免在收起尾声被残留返回进度打断
    private var isClosing = false

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
            isClosing = false
            backShrinkFraction = 0f
            // 先让它在屏幕外完成首次组合，再起弹簧；上滑展开时 onDrag 已经挂载，无需等待
            val needsMount = !isOpen
            isOpen = true
            springJobRef[0] = scope.launch {
                if (needsMount) {
                    withFrameNanos { }
                    withFrameNanos { }
                }
                animate(
                    initialValue = offsetY,
                    targetValue = 0f,
                    initialVelocity = velocity,
                    animationSpec = motion.spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { value, _ -> offsetY = value.coerceIn(0f, screenHeightPx) }
            }
        } else {
            isClosing = true
            if (!initialOffset.isNaN()) {
                offsetY = initialOffset
                backShrinkFraction = 0f
            }
            val startOffsetY = offsetY
            val startShrink = backShrinkFraction
            val remainingTotal = (screenHeightPx - startOffsetY).coerceAtLeast(1f)
            springJobRef[0] = scope.launch {
                animate(
                    initialValue = startOffsetY,
                    targetValue = screenHeightPx,
                    initialVelocity = velocity,
                    animationSpec = motion.spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { value, _ ->
                    val clampedOffset = value.coerceIn(0f, screenHeightPx)
                    offsetY = clampedOffset
                    if (startShrink > 0f) {
                        val remainingFraction = ((screenHeightPx - clampedOffset) / remainingTotal).coerceIn(0f, 1f)
                        backShrinkFraction = startShrink * remainingFraction
                    }
                }
                offsetY = screenHeightPx
                backShrinkFraction = 0f
                isOpen = false
                isClosing = false
            }
        }
    }

    // 系统返回手势跟手：取消进行中的弹簧，同步推进下移与缩小比例
    fun onBackProgress(progress: Float) {
        if (!isOpen || isClosing) return
        springJobRef[0]?.cancel()
        backShrinkFraction = decelerateBackProgress(progress)
        offsetY = computePlayerSheetBackOffset(progress, screenHeightPx)
    }

    // 返回手势取消：使用现有弹簧规格将位移与缩小比例一并弹回 0
    fun onBackCancel() {
        if (!isOpen || isClosing) return
        springJobRef[0]?.cancel()
        val startOffsetY = offsetY
        val startShrink = backShrinkFraction
        if (startOffsetY == 0f && startShrink == 0f) return
        springJobRef[0] = scope.launch {
            animate(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = motion.spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) { factor, _ ->
                val clamped = factor.coerceIn(0f, 1f)
                offsetY = (startOffsetY * clamped).coerceIn(0f, screenHeightPx)
                backShrinkFraction = (startShrink * clamped).coerceIn(0f, 1f)
            }
            offsetY = 0f
            backShrinkFraction = 0f
        }
    }

    // 悬浮舱上拉跟手：取消进行中的弹簧动画，位移直接跟随手指
    fun onDrag(delta: Float) {
        springJobRef[0]?.cancel()
        isClosing = false
        backShrinkFraction = 0f
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
    val motion = LocalUiMotion.current
    val state = remember(scope, motion) { MelodiaPlayerSheetState(scope, motion) }
    LaunchedEffect(state.screenHeightPx) {
        state.settleToBottomIfClosed()
    }
    return state
}
