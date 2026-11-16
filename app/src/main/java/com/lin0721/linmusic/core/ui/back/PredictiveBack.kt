package com.lin0721.linmusic.core.ui.back

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.UiMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val BACK_DISMISS_MIN_SCALE = 0.92f
private const val BACK_DISMISS_MAX_SHIFT_FRACTION = 0.03f

// 手势进度快照
@Immutable
data class BackGestureProgress(
    val progress: Float,       // 0..1
    val fromLeftEdge: Boolean, // 手势起始边
    val touchY: Float          // 触点纵坐标，像素
)

// 是否启用跟手效果：设置开关开 && 界面动画总开关开 && SDK >= 34
val LocalPredictiveBackEnabled = staticCompositionLocalOf { false }

// 手势进度经过二次减速曲线映射到 0..1，前段跟手敏感、后段阻尼增大
fun decelerateBackProgress(progress: Float): Float {
    val clamped = progress.coerceIn(0f, 1f)
    return 1f - (1f - clamped) * (1f - clamped)
}

internal data class BackDismissTransform(
    val scale: Float,
    val translationXFraction: Float,
    val translationYFraction: Float
)

// 将 0..1 收缩分数与原点映射为缩放比例和相对容器尺寸的位移比例
internal fun computeBackDismissTransform(
    fraction: Float,
    origin: TransformOrigin
): BackDismissTransform {
    val f = fraction.coerceIn(0f, 1f)
    if (f <= 0f) {
        return BackDismissTransform(scale = 1f, translationXFraction = 0f, translationYFraction = 0f)
    }
    val scale = 1f - (1f - BACK_DISMISS_MIN_SCALE) * f
    val dirX = (origin.pivotFractionX - 0.5f) * 2f
    val dirY = (origin.pivotFractionY - 0.5f) * 2f
    return BackDismissTransform(
        scale = scale,
        translationXFraction = BACK_DISMISS_MAX_SHIFT_FRACTION * dirX * f,
        translationYFraction = BACK_DISMISS_MAX_SHIFT_FRACTION * dirY * f
    )
}

// 弹层/浮层跟手退出的收缩状态：仅在绘制阶段读取，提交时不归零以衔接退出动画
@Stable
class BackDismissState(
    private val scope: CoroutineScope,
    private val motion: UiMotion
) {
    var fraction by mutableFloatStateOf(0f)
        private set

    private var cancelJob: Job? = null
    // 递增令牌隔离新旧取消协程，防止旧动画尾部回写新进度
    private var cancelToken = 0

    fun onProgress(progress: Float) {
        ++cancelToken
        cancelJob?.cancel()
        cancelJob = null
        fraction = decelerateBackProgress(progress)
    }

    fun onCancel() {
        val token = ++cancelToken
        cancelJob?.cancel()
        val start = fraction
        if (start <= 0f) {
            fraction = 0f
            cancelJob = null
            return
        }
        cancelJob = scope.launch {
            try {
                animate(
                    initialValue = start,
                    targetValue = 0f,
                    animationSpec = motion.spring()
                ) { value, _ ->
                    if (cancelToken == token) {
                        fraction = value.coerceIn(0f, 1f)
                    }
                }
                if (cancelToken == token) {
                    fraction = 0f
                }
            } finally {
                if (cancelToken == token) {
                    cancelJob = null
                }
            }
        }
    }

    fun onCommit() {
        ++cancelToken
        cancelJob?.cancel()
        cancelJob = null
    }

    fun reset() {
        ++cancelToken
        cancelJob?.cancel()
        cancelJob = null
        if (fraction != 0f) {
            fraction = 0f
        }
    }
}

@Composable
fun rememberBackDismissState(): BackDismissState {
    val scope = rememberCoroutineScope()
    val motion = LocalUiMotion.current
    return remember(scope, motion) { BackDismissState(scope, motion) }
}

@Composable
fun MelodiaDismissBackHandler(
    enabled: Boolean,
    state: BackDismissState,
    onBack: () -> Unit
) {
    val wasEnabled = remember { booleanArrayOf(false) }
    // 在同帧应用阶段同步复位，避免容器重新出现首帧闪出上次提交残留的收缩态
    SideEffect {
        if (enabled && !wasEnabled[0]) {
            state.reset()
        }
        wasEnabled[0] = enabled
    }

    MelodiaBackHandler(
        enabled = enabled,
        onProgress = { event -> state.onProgress(event.progress) },
        onCancel = { state.onCancel() },
        onBack = {
            state.onCommit()
            onBack()
        }
    )
}

fun Modifier.backDismissPreview(
    state: BackDismissState,
    origin: TransformOrigin = TransformOrigin(0.5f, 1f)
): Modifier = graphicsLayer {
    val transform = computeBackDismissTransform(state.fraction, origin)
    scaleX = transform.scale
    scaleY = transform.scale
    translationX = size.width * transform.translationXFraction
    translationY = size.height * transform.translationYFraction
    transformOrigin = origin
}

@Composable
fun MelodiaBackHandler(
    enabled: Boolean = true,
    onProgress: (BackGestureProgress) -> Unit = {},
    onCancel: () -> Unit = {},
    onBack: () -> Unit
) {
    val predictiveEnabled by rememberUpdatedState(LocalPredictiveBackEnabled.current)
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val currentOnBack by rememberUpdatedState(onBack)

    // 始终用 PredictiveBackHandler 注册，避免切换实现导致回调优先级跳到最前
    PredictiveBackHandler(enabled = enabled) { progressFlow ->
        if (!predictiveEnabled) {
            progressFlow.collect {}
            currentOnBack()
            return@PredictiveBackHandler
        }
        try {
            progressFlow.collect { event ->
                currentOnProgress(
                    BackGestureProgress(
                        progress = event.progress,
                        fromLeftEdge = event.swipeEdge == BackEventCompat.EDGE_LEFT,
                        touchY = event.touchY
                    )
                )
            }
            currentOnBack()
        } catch (e: CancellationException) {
            currentOnCancel()
            throw e
        }
    }
}
