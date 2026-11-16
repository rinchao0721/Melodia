package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.desktop.data.LibraryShortcutLayout
import com.lin0721.linmusic.desktop.data.LibraryShortcutSetting
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.library.icon
import com.lin0721.linmusic.desktop.ui.player.rememberQueueReorderState
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private val ShortcutEditorRowHeight = 48.dp

// 音乐库快捷入口编辑：拖动把手调整顺序，开关控制显隐；松手后写入偏好
@Composable
internal fun LibraryShortcutEditor(
    layout: List<LibraryShortcutSetting>,
    onChange: (List<LibraryShortcutSetting>) -> Unit
) {
    var working by remember(layout) { mutableStateOf(layout) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentLayout by rememberUpdatedState(layout)
    val currentOnChange by rememberUpdatedState(onChange)
    val reorder = rememberQueueReorderState(
        listState = listState,
        upcomingStart = 0,
        queueSize = working.size,
        upcomingFirstLazyIndex = 0,
        onMove = { from, to -> working = working.toMutableList().apply { add(to, removeAt(from)) } }
    )
    val shape = RoundedCornerShape(8.dp)

    LazyColumn(
        state = listState,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxWidth().height(ShortcutEditorRowHeight * working.size)
    ) {
        itemsIndexed(working, key = { _, setting -> setting.item.key }) { index, setting ->
            val dragging = reorder.draggedIndex == index
            val settling = reorder.settlingIndex == index
            val offsetY = reorder.offsetOf(index)
            val startDrag by rememberUpdatedState({ reorder.start(index) })
            val drag by rememberUpdatedState({ delta: Float -> reorder.drag(index, delta) })
            val endDrag by rememberUpdatedState({
                reorder.end(index, scope)
                currentOnChange(LibraryShortcutLayout.normalize(working))
            })
            Row(
                Modifier.fillMaxWidth().height(ShortcutEditorRowHeight)
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
                Icon(
                    setting.item.icon(),
                    null,
                    tint = DesktopColors.TextGray,
                    modifier = Modifier.padding(start = 10.dp).size(18.dp)
                )
                Text(
                    setting.item.title,
                    color = DesktopColors.TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
                SettingSwitch(setting.visible) { visible ->
                    currentOnChange(LibraryShortcutLayout.setVisible(currentLayout, setting.item, visible))
                }
            }
        }
    }
}
