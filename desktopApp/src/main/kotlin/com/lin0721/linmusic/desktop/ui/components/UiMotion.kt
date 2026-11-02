package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationConstants
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.spring as composeSpring
import androidx.compose.animation.core.tween as composeTween

// 界面过渡动画开关：关闭后过渡直接落到终点。歌词逐字高亮、歌词滚动与常驻的加载动画不走这里
@Stable
class UiMotion(initialEnabled: Boolean = true) {
    var enabled by mutableStateOf(initialEnabled)

    fun <T> tween(
        durationMillis: Int = AnimationConstants.DefaultDurationMillis,
        delayMillis: Int = 0,
        easing: Easing = FastOutSlowInEasing
    ): FiniteAnimationSpec<T> = if (enabled) composeTween(durationMillis, delayMillis, easing) else snap()

    fun <T> spring(
        dampingRatio: Float = Spring.DampingRatioNoBouncy,
        stiffness: Float = Spring.StiffnessMedium,
        visibilityThreshold: T? = null
    ): FiniteAnimationSpec<T> = if (enabled) composeSpring(dampingRatio, stiffness, visibilityThreshold) else snap()

    fun enter(transition: EnterTransition): EnterTransition = if (enabled) transition else EnterTransition.None

    fun exit(transition: ExitTransition): ExitTransition = if (enabled) transition else ExitTransition.None

    // 关闭时连尺寸过渡一并去掉，ContentTransform 默认的 SizeTransform 自带弹簧动画
    fun transform(transform: ContentTransform): ContentTransform =
        if (enabled) transform else ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)

    suspend fun scrollToItem(state: LazyListState, index: Int, scrollOffset: Int = 0) {
        if (enabled) state.animateScrollToItem(index, scrollOffset) else state.scrollToItem(index, scrollOffset)
    }

    suspend fun scrollToItem(state: LazyGridState, index: Int, scrollOffset: Int = 0) {
        if (enabled) state.animateScrollToItem(index, scrollOffset) else state.scrollToItem(index, scrollOffset)
    }
}

val LocalUiMotion = staticCompositionLocalOf { UiMotion() }

@Composable
@ReadOnlyComposable
fun <T> uiTween(
    durationMillis: Int = AnimationConstants.DefaultDurationMillis,
    delayMillis: Int = 0,
    easing: Easing = FastOutSlowInEasing
): FiniteAnimationSpec<T> = LocalUiMotion.current.tween(durationMillis, delayMillis, easing)

@Composable
@ReadOnlyComposable
fun <T> uiSpring(
    dampingRatio: Float = Spring.DampingRatioNoBouncy,
    stiffness: Float = Spring.StiffnessMedium,
    visibilityThreshold: T? = null
): FiniteAnimationSpec<T> = LocalUiMotion.current.spring(dampingRatio, stiffness, visibilityThreshold)

@Composable
@ReadOnlyComposable
fun uiEnter(transition: EnterTransition): EnterTransition = LocalUiMotion.current.enter(transition)

@Composable
@ReadOnlyComposable
fun uiExit(transition: ExitTransition): ExitTransition = LocalUiMotion.current.exit(transition)
