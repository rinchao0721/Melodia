package com.lin0721.linmusic.desktop.ui.settings

import com.lin0721.linmusic.core.preferences.PlayerCardEntry
import com.lin0721.linmusic.core.preferences.PlayerCardEntries
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.nowplaying.SupportedInfoCards
import com.lin0721.linmusic.desktop.ui.player.rememberQueueReorderState
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private val EditorRowHeight = 48.dp

// 插件卡片都能编辑，内置卡片只列出桌面端已实现的
internal fun isEditableCard(entry: PlayerCardEntry): Boolean = entry.card == null || entry.card in SupportedInfoCards

// 正在播放面板的信息卡编辑：拖动把手调整顺序，开关控制显隐；松手后才写入偏好
@Composable
internal fun CardLayoutEditor(layout: List<PlayerCardEntry>, onChange: (List<PlayerCardEntry>) -> Unit) {
    // 拖动期间只改本地顺序，避免每次换位都落盘
    var working by remember(layout) { mutableStateOf(layout.filter(::isEditableCard)) }
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
        modifier = Modifier.fillMaxWidth().height(EditorRowHeight * working.size)
    ) {
        itemsIndexed(working, key = { _, setting -> setting.key }) { index, setting ->
            val dragging = reorder.draggedIndex == index
            val settling = reorder.settlingIndex == index
            val offsetY = reorder.offsetOf(index)
            val startDrag by rememberUpdatedState({ reorder.start(index) })
            val drag by rememberUpdatedState({ delta: Float -> reorder.drag(index, delta) })
            val endDrag by rememberUpdatedState({
                reorder.end(index, scope)
                currentOnChange(PlayerCardEntries.mergeOrdered(currentLayout, working, ::isEditableCard))
            })
            Row(
                Modifier.fillMaxWidth().height(EditorRowHeight)
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
                Text(
                    setting.title,
                    color = DesktopColors.TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
                SettingSwitch(setting.visible) { visible -> currentOnChange(PlayerCardEntries.setVisible(currentLayout, setting.key, visible)) }
            }
        }
    }
}
