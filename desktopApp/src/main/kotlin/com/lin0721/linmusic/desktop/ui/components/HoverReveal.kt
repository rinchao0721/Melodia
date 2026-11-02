package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

private const val REVEAL_DURATION_MS = 150
private const val HIDDEN_SCALE = 0.85f

// 桌面端悬停才出现的小按钮统一用它显隐：淡入淡出并轻微缩放。
// 容器侧用 Modifier.hoverable(source) + source.collectIsHoveredAsState() 得到 revealed；
// 按钮带弹出菜单时，菜单展开期间也应保持 revealed，否则锚点消失会连带关闭菜单。
// reserveSpace 为 false 时隐藏态不占位，显示时宽度展开并把相邻内容挤开
@Composable
fun HoverReveal(
    revealed: Boolean,
    modifier: Modifier = Modifier,
    reserveSpace: Boolean = true,
    content: @Composable () -> Unit
) {
    if (reserveSpace) {
        val progress by animateFloatAsState(if (revealed) 1f else 0f, uiTween(REVEAL_DURATION_MS), label = "hoverReveal")
        Box(
            modifier.graphicsLayer {
                alpha = progress
                val scale = HIDDEN_SCALE + (1f - HIDDEN_SCALE) * progress
                scaleX = scale
                scaleY = scale
            }
        ) { content() }
    } else {
        AnimatedVisibility(
            visible = revealed,
            modifier = modifier,
            enter = expandHorizontally(uiTween(REVEAL_DURATION_MS)) + fadeIn(uiTween(REVEAL_DURATION_MS)),
            exit = shrinkHorizontally(uiTween(REVEAL_DURATION_MS)) + fadeOut(uiTween(REVEAL_DURATION_MS))
        ) { content() }
    }
}
