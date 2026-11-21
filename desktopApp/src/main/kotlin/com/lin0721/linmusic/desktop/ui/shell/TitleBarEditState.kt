package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.lin0721.linmusic.desktop.data.TitleBarButton
import com.lin0721.linmusic.desktop.data.TitleBarLayout
import com.lin0721.linmusic.desktop.data.TitleBarLayoutCodec
import com.lin0721.linmusic.desktop.data.TitleBarSlot

// 标题栏编辑模式拖拽状态：根据按钮与槽位 boundsInRoot 做横向命中测试，松手后提交布局
@Stable
internal class TitleBarEditState(initialLayout: TitleBarLayout) {
    private var committedLayout: TitleBarLayout = initialLayout

    var workingLayout by mutableStateOf(initialLayout)
        private set

    var draggedButton by mutableStateOf<TitleBarButton?>(null)
        private set

    var dragDelta by mutableStateOf(Offset.Zero)
        private set

    var initialCenterInRoot by mutableStateOf(Offset.Zero)
        private set

    var titleBarTopLeft by mutableStateOf(Offset.Zero)

    val slotBounds = mutableStateMapOf<TitleBarSlot, Rect>()
    val buttonBounds = mutableStateMapOf<TitleBarButton, Rect>()

    val isDragging: Boolean get() = draggedButton != null

    val currentDragCenterInTitleBar: Offset
        get() = initialCenterInRoot + dragDelta - titleBarTopLeft

    fun syncCommitted(layout: TitleBarLayout) {
        committedLayout = layout
        if (draggedButton == null) {
            workingLayout = layout
        }
    }

    fun buttonAt(rootPos: Offset): TitleBarButton? {
        val visible = workingLayout.centerStart + workingLayout.centerEnd + workingLayout.trailing
        return visible.firstOrNull { buttonBounds[it]?.contains(rootPos) == true }
    }

    fun startDrag(button: TitleBarButton): Boolean {
        val bounds = buttonBounds[button] ?: return false
        draggedButton = button
        initialCenterInRoot = bounds.center
        dragDelta = Offset.Zero
        return true
    }

    fun dragBy(delta: Offset) {
        val button = draggedButton ?: return
        dragDelta += delta
        val dragCenterX = initialCenterInRoot.x + dragDelta.x
        val (slot, index) = resolveTitleBarDropTarget(
            layout = workingLayout,
            dragged = button,
            dragCenterX = dragCenterX,
            slotBounds = slotBounds,
            buttonBounds = buttonBounds
        )
        val next = TitleBarLayoutCodec.move(workingLayout, button, slot, index)
        if (next != workingLayout) {
            workingLayout = next
        }
    }

    fun endDrag(onCommit: (TitleBarLayout) -> Unit) {
        if (draggedButton == null) return
        val finalLayout = workingLayout
        draggedButton = null
        dragDelta = Offset.Zero
        if (finalLayout != committedLayout) {
            committedLayout = finalLayout
            onCommit(finalLayout)
        }
    }

    fun cancelDrag() {
        draggedButton = null
        dragDelta = Offset.Zero
        workingLayout = committedLayout
    }
}

@Composable
internal fun rememberTitleBarEditState(layout: TitleBarLayout): TitleBarEditState {
    val state = remember { TitleBarEditState(layout) }
    state.syncCommitted(layout)
    return state
}

// 按三个槽的水平中点划分归属，再统计目标槽内中心点在指针左侧的按钮数作为插入下标
internal fun resolveTitleBarDropTarget(
    layout: TitleBarLayout,
    dragged: TitleBarButton,
    dragCenterX: Float,
    slotBounds: Map<TitleBarSlot, Rect>,
    buttonBounds: Map<TitleBarButton, Rect>
): Pair<TitleBarSlot, Int> {
    val cs = slotBounds[TitleBarSlot.CENTER_START]
    val ce = slotBounds[TitleBarSlot.CENTER_END]
    val tr = slotBounds[TitleBarSlot.TRAILING]

    val csCeBoundary = if (cs != null && ce != null) (cs.right + ce.left) / 2f else null
    val ceTrBoundary = if (ce != null && tr != null) (ce.right + tr.left) / 2f else null

    val targetSlot = when {
        csCeBoundary != null && dragCenterX < csCeBoundary -> TitleBarSlot.CENTER_START
        ceTrBoundary != null && dragCenterX < ceTrBoundary -> TitleBarSlot.CENTER_END
        ceTrBoundary != null && dragCenterX >= ceTrBoundary -> TitleBarSlot.TRAILING
        cs != null && dragCenterX <= cs.right -> TitleBarSlot.CENTER_START
        tr != null && dragCenterX >= tr.left -> TitleBarSlot.TRAILING
        ce != null -> TitleBarSlot.CENTER_END
        else -> layout.slotOf(dragged) ?: TitleBarSlot.TRAILING
    }

    val others = layout.buttonsIn(targetSlot).filter { it != dragged }
    val index = others.count { other ->
        val bounds = buttonBounds[other]
        bounds != null && bounds.center.x < dragCenterX
    }
    return targetSlot to index
}
