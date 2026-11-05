package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.desktop.data.LibraryMode
import com.lin0721.linmusic.desktop.ui.components.uiSpring
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.nowplaying.NowPlayingDockState
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import kotlinx.coroutines.flow.first

// 略欠阻尼，松手回弹带一点过冲
private const val DRAG_SPRING_DAMPING = 0.8f

// 三栏（音乐库 / 中间内容 / 正在播放）的宽度分配：音乐库展开时吃掉中间区域，中间区域宽度收到 0
@Stable
class WorkspaceLayout internal constructor(
    private val defaultWidth: Dp,
    private val libraryTarget: Dp,
    private val libraryFrom: Dp,
    val expandedWidth: Dp,
    private val libraryWidthState: State<Dp>,
    private val centerGapState: State<Dp>,
    private val dock: NowPlayingDockState
) {
    val libraryWidth: Dp get() = libraryWidthState.value

    // 音乐库与中间区域之间的间距，展开时收到 0
    val centerGap: Dp get() = centerGapState.value

    // 可见宽度加上该差值即中间内容的排版宽度。音乐库伸缩期间按起止两端中音乐库较窄、即内容较宽的一端冻结排版，
    // 多出部分被裁剪，结束后只重排一次；音乐库宽于默认宽度时按默认宽度冻结，展开态可见宽度为 0，内容原样被裁剪，返回后不必重建
    val centerWidthExtra: Dp
        get() {
            val settledLibrary = if (libraryWidth == libraryTarget) libraryTarget else minOf(libraryFrom, libraryTarget)
            // 弹簧过冲时音乐库会短暂窄于两端，冻结宽度不能超过当前宽度，否则内容右侧露空
            val frozenLibrary = minOf(settledLibrary, libraryWidth).coerceAtMost(defaultWidth)
            return (libraryWidth - frozenLibrary) + (centerGap - DesktopDimens.PaneGap) + dock.widthExtra
        }
}

@Composable
fun rememberWorkspaceLayout(
    mode: LibraryMode,
    availableWidth: Dp,
    dock: NowPlayingDockState,
    defaultWidth: Dp,
    stretch: Dp,
    spring: Boolean,
    dragging: Boolean,
    onSpringSettled: () -> Unit
): WorkspaceLayout {
    val expandedWidth = (availableWidth - dock.settledOccupied).coerceAtLeast(0.dp)
    val target = when (mode) {
        LibraryMode.RAIL -> DesktopDimens.LibraryRailWidth + stretch
        LibraryMode.DEFAULT -> defaultWidth + stretch
        LibraryMode.EXPANDED -> expandedWidth
    }
    // 拖动及松手回弹走弹簧，目标逐帧变化时保留速度，不会像 tween 那样每次重新起步
    val spec = if (spring) {
        uiSpring<Dp>(dampingRatio = DRAG_SPRING_DAMPING, stiffness = Spring.StiffnessMedium)
    } else {
        uiTween<Dp>(PANE_ANIMATION_MS, easing = FastOutSlowInEasing)
    }
    val libraryWidth = animateDpAsState(target, spec, label = "libraryWidth")
    // 松手回弹到位后退出弹簧，窗口缩放等非拖动引起的宽度变化仍走常规过渡
    if (spring && !dragging) {
        val currentOnSpringSettled by rememberUpdatedState(onSpringSettled)
        LaunchedEffect(target) {
            snapshotFlow { libraryWidth.value == target }.first { it }
            currentOnSpringSettled()
        }
    }
    // 目标切换那一帧动画尚未推进，此刻的宽度即本次过渡的起点
    val libraryFrom = remember(target) { libraryWidth.value }
    val centerGap = animateDpAsState(
        if (mode == LibraryMode.EXPANDED) 0.dp else DesktopDimens.PaneGap,
        spec,
        label = "centerGap"
    )
    return WorkspaceLayout(defaultWidth, target, libraryFrom, expandedWidth, libraryWidth, centerGap, dock)
}
