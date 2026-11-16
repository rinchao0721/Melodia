package com.lin0721.linmusic.core.ui.back

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.theme.LocalSkinBackdrop
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.paneBackdrop
import kotlinx.coroutines.flow.collectLatest

const val GESTURE_STAGE_END_FRACTION = 0.4f
const val PREDICTIVE_BACK_DURATION_MS = 350
const val PREDICTIVE_BACK_CANCEL_DURATION_MS = 200

enum class BackTransitionPhase {
    Idle,
    Seeking,
    Committing,
    Canceling
}

data class BackPageTransform(
    val scale: Float,
    val cornerDp: Float,
    val translationXFraction: Float,
    val alpha: Float
)

// 手势进度经过减速曲线后映射到过渡的前 0.4 区间，留出 0.4..1 给提交收尾
fun gestureToTransitionFraction(progress: Float): Float =
    decelerateBackProgress(progress) * GESTURE_STAGE_END_FRACTION

// 将 0..1 的线性过渡分数映射为当前页的缩放、圆角、横向位移比例与透明度
fun computeBackPageTransform(
    fraction: Float,
    fromLeftEdge: Boolean
): BackPageTransform {
    val f = fraction.coerceIn(0f, 1f)
    if (f <= 0f) {
        return BackPageTransform(scale = 1f, cornerDp = 0f, translationXFraction = 0f, alpha = 1f)
    }
    val stage1 = (f / GESTURE_STAGE_END_FRACTION).coerceIn(0f, 1f)
    val stage2 = ((f - GESTURE_STAGE_END_FRACTION) / (1f - GESTURE_STAGE_END_FRACTION)).coerceIn(0f, 1f)
    val stage2Eased = 1f - (1f - stage2) * (1f - stage2)
    val scale = if (f <= GESTURE_STAGE_END_FRACTION) {
        1f - 0.1f * stage1
    } else {
        0.9f - 0.05f * stage2Eased
    }
    val cornerDp = 28f * stage1
    val direction = if (fromLeftEdge) 1f else -1f
    val translationXFraction = 0.05f * stage1 * direction
    val alpha = 1f - stage2
    return BackPageTransform(
        scale = scale,
        cornerDp = cornerDp,
        translationXFraction = translationXFraction,
        alpha = alpha
    )
}

@Stable
class BackTransitionGestureState<T> {
    var origin: T? by mutableStateOf(null)
        private set
    var target: T? by mutableStateOf(null)
        private set
    // 区分“未开始”与“目标状态本身为 null”（如设置页返回主菜单）
    var hasSegment: Boolean by mutableStateOf(false)
        private set
    var progress: Float by mutableFloatStateOf(0f)
        private set
    var fromLeftEdge: Boolean by mutableStateOf(false)
        private set
    var phase: BackTransitionPhase by mutableStateOf(BackTransitionPhase.Idle)
        private set
    // 实时同步给绘制层读取的 0..1 过渡分数，避免等待 Transition 子动画重组与 seekAnimations 之间的一帧延迟导致闪烁
    var visualFraction: Float by mutableFloatStateOf(0f)
        private set

    // 连续触发预见式返回时递减目标层 zIndex，防止上一页沿用旧的 -1f 盖住新页面
    private var gestureSequence = 0

    fun begin(origin: T, target: T, fromLeftEdge: Boolean, progress: Float) {
        gestureSequence += 1
        val clamped = progress.coerceIn(0f, 1f)
        this.origin = origin
        this.target = target
        this.hasSegment = true
        this.fromLeftEdge = fromLeftEdge
        this.progress = clamped
        this.visualFraction = gestureToTransitionFraction(clamped)
        this.phase = BackTransitionPhase.Seeking
    }

    fun update(progress: Float, fromLeftEdge: Boolean) {
        if (phase != BackTransitionPhase.Seeking) return
        val clamped = progress.coerceIn(0f, 1f)
        this.progress = clamped
        this.visualFraction = gestureToTransitionFraction(clamped)
        this.fromLeftEdge = fromLeftEdge
    }

    internal fun updateVisualFraction(fraction: Float) {
        this.visualFraction = fraction.coerceIn(0f, 1f)
    }

    fun cancel() {
        if (phase != BackTransitionPhase.Seeking) return
        phase = BackTransitionPhase.Canceling
    }

    fun commit() {
        if (phase != BackTransitionPhase.Seeking) return
        phase = BackTransitionPhase.Committing
    }

    fun abandon() {
        hasSegment = false
        origin = null
        target = null
        progress = 0f
        visualFraction = 0f
        fromLeftEdge = false
        phase = BackTransitionPhase.Idle
    }

    internal fun clearIfCommit(expectedTarget: T) {
        if (phase != BackTransitionPhase.Committing || !hasSegment) return
        if (this.target != expectedTarget) return
        abandon()
    }

    internal fun clearIfCancel(expectedOrigin: T) {
        if (phase != BackTransitionPhase.Canceling || !hasSegment) return
        if (this.origin != expectedOrigin) return
        abandon()
    }

    fun targetZIndex(): Float = -gestureSequence.coerceAtLeast(1).toFloat()

    fun isPredictiveSegment(initial: T, dest: T): Boolean {
        if (!hasSegment) return false
        val o = origin
        val t = target
        return when (phase) {
            BackTransitionPhase.Idle -> false
            BackTransitionPhase.Seeking, BackTransitionPhase.Committing -> initial == o && dest == t
            // 回弹沿原线段倒放，落定那一下线段会翻转成 目标页 → 起始页
            BackTransitionPhase.Canceling -> (initial == o && dest == t) || (initial == t && dest == o)
        }
    }
}

private sealed interface BackTransitionIntent<out T> {
    data class Animate<T>(val target: T) : BackTransitionIntent<T>
    data class Seek<T>(val target: T) : BackTransitionIntent<T>
    data class Commit<T>(val target: T, val lastProgress: Float) : BackTransitionIntent<T>
    data class Cancel<T>(val origin: T) : BackTransitionIntent<T>
    data class AbandonAndAnimate<T>(val target: T) : BackTransitionIntent<T>
}

@Composable
fun <T> rememberPredictiveBackTransition(
    currentState: T,
    gestureState: BackTransitionGestureState<T>,
    label: String
): Transition<T> {
    val motion = LocalUiMotion.current
    val latestMotion by rememberUpdatedState(motion)
    val transitionState = remember { SeekableTransitionState(currentState) }
    val transition = rememberTransition(transitionState, label = label)
    val latestCurrentState by rememberUpdatedState(currentState)

    // 为父 Transition 提供时长基准，使 SeekableTransitionState.totalDurationNanos > 0
    transition.animateFloat(
        transitionSpec = {
            motion.tween(durationMillis = PREDICTIVE_BACK_DURATION_MS, easing = LinearEasing)
        },
        label = "predictive_back_clock"
    ) { state ->
        state.hashCode().toFloat()
    }

    // 单一协程串行驱动 seekTo 与 animateTo，避免多协程竞争 SeekableTransitionState 内部互斥锁
    LaunchedEffect(gestureState, transitionState) {
        snapshotFlow {
            val state = latestCurrentState
            when (gestureState.phase) {
                BackTransitionPhase.Seeking -> {
                    if (gestureState.hasSegment && gestureState.origin == state) {
                        @Suppress("UNCHECKED_CAST")
                        BackTransitionIntent.Seek(gestureState.target as T)
                    } else {
                        BackTransitionIntent.AbandonAndAnimate(state)
                    }
                }
                BackTransitionPhase.Canceling -> {
                    if (gestureState.hasSegment && gestureState.origin == state) {
                        BackTransitionIntent.Cancel(state)
                    } else {
                        BackTransitionIntent.AbandonAndAnimate(state)
                    }
                }
                BackTransitionPhase.Committing -> {
                    // onBack 中调用 commit() 时重组尚未发生，latestCurrentState 仍为 origin，下一帧重组后才更新为 target
                    if (gestureState.hasSegment && (state == gestureState.target || state == gestureState.origin)) {
                        @Suppress("UNCHECKED_CAST")
                        BackTransitionIntent.Commit(gestureState.target as T, gestureState.progress)
                    } else {
                        BackTransitionIntent.AbandonAndAnimate(state)
                    }
                }
                BackTransitionPhase.Idle -> BackTransitionIntent.Animate(state)
            }
        }.collectLatest { intent ->
            when (intent) {
                is BackTransitionIntent.Seek -> {
                    @Suppress("UNCHECKED_CAST")
                    val originState = gestureState.origin as T
                    if (transitionState.currentState != originState && transitionState.targetState != intent.target) {
                        transitionState.snapTo(originState)
                    }
                    // 在同一协程内消费 progress 更新，避免首帧等待目标页重组时被 collectLatest 打断
                    snapshotFlow { gestureState.progress }.collect { progress ->
                        val f = gestureToTransitionFraction(progress)
                        gestureState.updateVisualFraction(f)
                        // 极微小的正数使 SeekableTransitionState 始终进入 origin -> target 线段并组合目标页
                        transitionState.seekTo(
                            fraction = f.coerceAtLeast(0.001f),
                            targetState = intent.target
                        )
                    }
                }
                is BackTransitionIntent.Commit -> {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val originState = gestureState.origin as T
                        if (transitionState.currentState != originState && transitionState.targetState != intent.target) {
                            transitionState.snapTo(originState)
                        }
                        val startFraction = maxOf(
                            gestureState.visualFraction,
                            gestureToTransitionFraction(intent.lastProgress)
                        )
                        gestureState.updateVisualFraction(startFraction)
                        // 确保目标页已在原线段 (origin -> target) 上完成组合并置于底层，随后推进 visualFraction 到 1f 再 snapTo
                        if (transitionState.targetState != intent.target) {
                            transitionState.seekTo(
                                fraction = startFraction.coerceAtLeast(0.001f),
                                targetState = intent.target
                            )
                        }
                        val remainingMs = ((1f - startFraction) * PREDICTIVE_BACK_DURATION_MS).toInt().coerceAtLeast(1)
                        val commitAnim = TargetBasedAnimation(
                            animationSpec = latestMotion.tween(durationMillis = remainingMs, easing = LinearEasing),
                            typeConverter = Float.VectorConverter,
                            initialValue = startFraction,
                            targetValue = 1f
                        )
                        val startNanos = withFrameNanos { it }
                        do {
                            val playTimeNanos = withFrameNanos { it } - startNanos
                            val f = commitAnim.getValueFromNanos(playTimeNanos).coerceIn(startFraction, 1f)
                            gestureState.updateVisualFraction(f)
                        } while (!commitAnim.isFinishedFromNanos(playTimeNanos))
                        gestureState.updateVisualFraction(1f)
                        transitionState.snapTo(intent.target)
                        // 等待一帧让 AnimatedContent 完成重组并移除退场页后，再重置手势状态，防止退场页在销毁前闪回 alpha=1
                        withFrameNanos { }
                    } finally {
                        gestureState.clearIfCommit(intent.target)
                    }
                }
                is BackTransitionIntent.Cancel -> {
                    try {
                        // 沿原线段把分数拖回 0 再落定；animateTo(currentState) 会被当成一次反向的新过渡，回弹不连贯
                        val startFraction = gestureState.visualFraction
                        if (transitionState.targetState != intent.origin || startFraction > 0f) {
                            val rewind = TargetBasedAnimation(
                                animationSpec = latestMotion.tween(PREDICTIVE_BACK_CANCEL_DURATION_MS),
                                typeConverter = Float.VectorConverter,
                                initialValue = startFraction,
                                targetValue = 0f
                            )
                            val startNanos = withFrameNanos { it }
                            do {
                                val playTimeNanos = withFrameNanos { it } - startNanos
                                val f = rewind.getValueFromNanos(playTimeNanos).coerceIn(0f, 1f)
                                gestureState.updateVisualFraction(f)
                            } while (!rewind.isFinishedFromNanos(playTimeNanos))
                        }
                        gestureState.updateVisualFraction(0f)
                        transitionState.snapTo(intent.origin)
                        withFrameNanos { }
                    } finally {
                        gestureState.clearIfCancel(intent.origin)
                    }
                }
                is BackTransitionIntent.AbandonAndAnimate -> {
                    gestureState.abandon()
                    transitionState.animateTo(intent.target)
                }
                is BackTransitionIntent.Animate -> {
                    transitionState.animateTo(intent.target)
                }
            }
        }
    }

    return transition
}

fun <T> AnimatedContentTransitionScope<T>.predictiveContentTransform(
    gestureState: BackTransitionGestureState<T>,
    nonPredictive: AnimatedContentTransitionScope<T>.() -> ContentTransform
): ContentTransform = if (
    gestureState.isPredictiveSegment(initialState, targetState) ||
    (gestureState.hasSegment && gestureState.phase != BackTransitionPhase.Idle &&
        ((initialState == gestureState.origin && targetState == gestureState.origin) ||
            (initialState == gestureState.target && targetState == gestureState.target)))
) {
    ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = ExitTransition.KeepUntilTransitionsFinished,
        targetContentZIndex = gestureState.targetZIndex(),
        sizeTransform = SizeTransform(clip = false)
    )
} else {
    nonPredictive()
}

@Composable
fun <T> AnimatedVisibilityScope.PredictiveBackTransitionContainer(
    parentTransition: Transition<T>,
    itemState: T,
    gestureState: BackTransitionGestureState<T>,
    containerColor: Color = Color.Unspecified,
    content: @Composable () -> Unit
) {
    // 直接读取 gestureState 判断当前是否处于预见式手势周期，不依赖 parentTransition.segment（后者需等协程 seekTo 后才更新，会晚一帧）
    val isPredictive = gestureState.hasSegment && gestureState.phase != BackTransitionPhase.Idle &&
        (itemState == gestureState.origin || itemState == gestureState.target)
    val isForeground = isPredictive && itemState == gestureState.origin
    val isBackground = isPredictive && itemState == gestureState.target
    val scrimColor = MelodiaColors.Scrim
    // 有皮肤背景图时 paneBackdrop 已含外壳底，无背景图时只铺了窗口底色
    val hasSkinBackdrop = LocalSkinBackdrop.current != null
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val fraction = if (isForeground) gestureState.visualFraction else 0f
                if (fraction > 0f) {
                    val transform = computeBackPageTransform(fraction, gestureState.fromLeftEdge)
                    scaleX = transform.scale
                    scaleY = transform.scale
                    translationX = size.width * transform.translationXFraction
                    if (transform.cornerDp > 0f) {
                        shape = RoundedCornerShape(transform.cornerDp.dp)
                        clip = true
                    } else {
                        clip = false
                    }
                    alpha = transform.alpha
                } else {
                    scaleX = 1f
                    scaleY = 1f
                    translationX = 0f
                    clip = false
                    alpha = 1f
                }
            }
            // 页面底色平时由根布局统一铺，页面自身是透明的；缩小的当前页要自带一份外壳底，否则会透出上一页
            .then(
                if (isForeground) {
                    Modifier
                        .paneBackdrop(MelodiaColors.Background)
                        .then(if (hasSkinBackdrop) Modifier else Modifier.background(MelodiaColors.ShellFill))
                } else {
                    Modifier
                }
            )
            .then(
                if (containerColor != Color.Unspecified && isPredictive) Modifier.background(containerColor) else Modifier
            )
            .drawWithContent {
                drawContent()
                val scrimAlpha = if (isBackground) (1f - gestureState.visualFraction) * 0.2f else 0f
                if (scrimAlpha > 0f) {
                    drawRect(scrimColor.copy(alpha = scrimAlpha))
                }
            }
    ) {
        content()
    }
}

@Composable
fun <T> MelodiaTransitionBackHandler(
    enabled: Boolean,
    currentState: T,
    targetState: T,
    gestureState: BackTransitionGestureState<T>,
    onBack: () -> Unit
) {
    val predictiveEnabled by rememberUpdatedState(LocalPredictiveBackEnabled.current)
    val latestCurrent by rememberUpdatedState(currentState)
    val latestTarget by rememberUpdatedState(targetState)
    val latestOnBack by rememberUpdatedState(onBack)
    val session = remember { object { var started = false } }

    MelodiaBackHandler(
        enabled = enabled,
        onProgress = { progress ->
            if (!session.started) {
                session.started = true
                gestureState.begin(
                    origin = latestCurrent,
                    target = latestTarget,
                    fromLeftEdge = progress.fromLeftEdge,
                    progress = progress.progress
                )
            } else {
                gestureState.update(
                    progress = progress.progress,
                    fromLeftEdge = progress.fromLeftEdge
                )
            }
        },
        onCancel = {
            session.started = false
            gestureState.cancel()
        },
        onBack = {
            val wasStarted = session.started
            session.started = false
            if (wasStarted) {
                gestureState.commit()
            } else if (predictiveEnabled) {
                // 极快轻扫时系统可能跳过 onBackProgressed 直接触发 onBackPressed，补走预见式退场以防回退到原动画
                gestureState.begin(
                    origin = latestCurrent,
                    target = latestTarget,
                    fromLeftEdge = true,
                    progress = 0f
                )
                gestureState.commit()
            }
            latestOnBack()
        }
    )
}
