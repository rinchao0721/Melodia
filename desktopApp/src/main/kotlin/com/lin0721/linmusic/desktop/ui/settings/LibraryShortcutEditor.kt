package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lin0721.linmusic.desktop.data.LibraryShortcutLayout
import com.lin0721.linmusic.desktop.data.LibraryShortcutSetting
import com.lin0721.linmusic.desktop.ui.library.icon
import com.lin0721.linmusic.desktop.ui.player.rememberQueueReorderState

// 音乐库快捷入口编辑：拖动把手调整顺序，开关控制显隐；松手后写入偏好
@Composable
internal fun LibraryShortcutEditor(
    layout: List<LibraryShortcutSetting>,
    onChange: (List<LibraryShortcutSetting>) -> Unit
) {
    var working by remember(layout) { mutableStateOf(layout) }
    val listState = rememberLazyListState()
    val currentLayout by rememberUpdatedState(layout)
    val currentOnChange by rememberUpdatedState(onChange)
    val reorder = rememberQueueReorderState(
        listState = listState,
        upcomingStart = 0,
        queueSize = working.size,
        upcomingFirstLazyIndex = 0,
        onMove = { from, to -> working = working.toMutableList().apply { add(to, removeAt(from)) } }
    )

    LazyColumn(
        state = listState,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxWidth().height(ReorderableSettingRowHeight * working.size)
    ) {
        itemsIndexed(working, key = { _, setting -> setting.item.key }) { index, setting ->
            ReorderableSettingRow(
                reorder = reorder,
                index = index,
                title = setting.item.title,
                leadingIcon = setting.item.icon(),
                onDragEnd = { currentOnChange(LibraryShortcutLayout.normalize(working)) }
            ) {
                SettingSwitch(setting.visible) { visible ->
                    currentOnChange(LibraryShortcutLayout.setVisible(currentLayout, setting.item, visible))
                }
            }
        }
    }
}
