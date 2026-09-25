package com.lin0721.linmusic.feature.home.ui

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import com.lin0721.linmusic.core.ui.theme.LayoutReflowDurationMs

// 首页内容区的 LookaheadScope。平板播放面板开合时列数与卡片尺寸整体切换，
// 卡片先按新排版算出目标位置与尺寸，再从旧位置平滑过去，而不是一帧跳到位
val LocalHomeLookaheadScope = staticCompositionLocalOf<LookaheadScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
private val HomeReflowBoundsTransform = BoundsTransform { _, _ ->
    tween(LayoutReflowDurationMs, easing = FastOutSlowInEasing)
}

// 只对排版变化本身做动画；列表滚动这类父级整体移动不参与（animateBounds 默认忽略参照系移动）
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.homeReflowBounds(): Modifier {
    val scope = LocalHomeLookaheadScope.current ?: return this
    return this.animateBounds(lookaheadScope = scope, boundsTransform = HomeReflowBoundsTransform)
}
