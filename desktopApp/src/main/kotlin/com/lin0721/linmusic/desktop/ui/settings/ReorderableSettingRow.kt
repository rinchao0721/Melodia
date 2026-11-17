package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.player.QueueReorderState
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

internal val ReorderableSettingRowHeight = 48.dp

// 设置页纵向拖动排序公共行：拖动把手 + 图标/标题 + 右侧操作区，松手后通过 onDragEnd 落盘
@Composable
internal fun LazyItemScope.ReorderableSettingRow(
    reorder: QueueReorderState,
    index: Int,
    title: String,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = ReorderableSettingRowHeight,
    leadingIcon: ImageVector? = null,
    trailing: @Composable RowScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val dragging = reorder.draggedIndex == index
    val settling = reorder.settlingIndex == index
    val offsetY = reorder.offsetOf(index)
    val startDrag by rememberUpdatedState({ reorder.start(index) })
    val drag by rememberUpdatedState({ delta: Float -> reorder.drag(index, delta) })
    val endDrag by rememberUpdatedState({
        reorder.end(index, scope)
        onDragEnd()
    })
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier.fillMaxWidth().height(rowHeight)
            .then(if (dragging || settling) Modifier.zIndex(1f) else if (LocalUiMotion.current.enabled) Modifier.animateItem() else Modifier)
            .graphicsLayer { translationY = offsetY }
            .then(if (dragging) Modifier.themedShadow(8.dp, shape) else Modifier)
            .clip(shape)
            .background(if (dragging) DesktopColors.SurfaceLight else Color.Transparent)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.DragIndicator,
            "拖动排序",
            tint = DesktopColors.TextGray,
            modifier = Modifier.size(24.dp).pointerHoverIcon(PointerIcon.Hand).pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startDrag() },
                    onDrag = { change, amount ->
                        change.consume()
                        drag(amount.y)
                    },
                    onDragEnd = { endDrag() },
                    onDragCancel = { endDrag() }
                )
            }
        )
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                null,
                tint = DesktopColors.TextGray,
                modifier = Modifier.padding(start = 10.dp).size(18.dp)
            )
        }
        Text(
            title,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f).padding(start = 10.dp)
        )
        trailing()
    }
}
