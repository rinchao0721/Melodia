package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private const val FADE_MS = 150

private val HoverScrollbarStyle: ScrollbarStyle
    @Composable @ReadOnlyComposable get() = ScrollbarStyle(
        minimalHeight = 32.dp,
        thickness = 8.dp,
        shape = RoundedCornerShape(4.dp),
        hoverDurationMillis = FADE_MS,
        // 黑色滑块在白底上更显眼，浅色下压低一档
        unhoverColor = DesktopColors.Overlay.copy(alpha = if (DesktopColors.IsDark) 0.3f else 0.24f),
        hoverColor = DesktopColors.Overlay.copy(alpha = if (DesktopColors.IsDark) 0.5f else 0.4f)
    )

// 竖向滚动区域的悬停滚动条：浮在右侧边缘、不占布局宽度，鼠标进入区域才淡入；内容未溢出时不显示
@Composable
fun HoverScrollbarBox(state: LazyListState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ScrollbarHost(state, rememberScrollbarAdapter(state), modifier, content)
}

@Composable
fun HoverScrollbarBox(state: LazyGridState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ScrollbarHost(state, rememberScrollbarAdapter(state), modifier, content)
}

@Composable
fun HoverScrollbarBox(state: ScrollState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ScrollbarHost(state, rememberScrollbarAdapter(state), modifier, content)
}

@Composable
private fun ScrollbarHost(
    state: ScrollableState,
    adapter: ScrollbarAdapter,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val hoverSource = remember { MutableInteractionSource() }
    val barSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    // 拖动滑块时指针可能移出区域，期间保持显示
    val dragging by barSource.collectIsDraggedAsState()
    val overflowing = state.canScrollForward || state.canScrollBackward
    val alpha by animateFloatAsState(if (overflowing && (hovered || dragging)) 1f else 0f, uiTween(FADE_MS), label = "scrollbarAlpha")

    Box(modifier.hoverable(hoverSource)) {
        content()
        if (alpha > 0f) {
            // 滚动条放进与内容等大的层里再撑满，否则它在高度不受限的父级中会把整块撑到最大高度
            Box(Modifier.matchParentSize()) {
                VerticalScrollbar(
                    adapter = adapter,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().graphicsLayer { this.alpha = alpha },
                    style = HoverScrollbarStyle,
                    interactionSource = barSource
                )
            }
        }
    }
}
