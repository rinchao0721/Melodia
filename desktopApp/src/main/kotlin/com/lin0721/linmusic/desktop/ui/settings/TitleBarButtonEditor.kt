package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.data.TitleBarButton
import com.lin0721.linmusic.desktop.data.TitleBarLayout
import com.lin0721.linmusic.desktop.data.TitleBarLayoutCodec
import com.lin0721.linmusic.desktop.data.TitleBarSlot
import com.lin0721.linmusic.desktop.ui.icons.DownloadIcons
import com.lin0721.linmusic.desktop.ui.player.rememberQueueReorderState
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

internal fun TitleBarButton.icon(): ImageVector = when (this) {
    TitleBarButton.HOME -> Icons.Rounded.Home
    TitleBarButton.RECOGNITION -> Icons.Rounded.GraphicEq
    TitleBarButton.MESSAGES -> Icons.Rounded.MailOutline
    TitleBarButton.DOWNLOADS -> DownloadIcons.Download
    TitleBarButton.PERSONAL_FM -> Icons.Rounded.Radio
    TitleBarButton.DAILY_RECOMMEND -> Icons.Rounded.Today
    TitleBarButton.SETTINGS -> Icons.Rounded.Settings
    TitleBarButton.LOCAL_MUSIC -> Icons.Rounded.AudioFile
    TitleBarButton.RECENT_PLAY -> Icons.Rounded.History
}

// 标题栏按钮编辑器：按中左/中右/右侧分组拖动排序，用下拉切换所在槽，开关控制显隐
@Composable
internal fun TitleBarButtonEditor(
    layout: TitleBarLayout,
    onChange: (TitleBarLayout) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        TitleBarSlot.entries.forEach { slot ->
            TitleBarSlotSection(
                slot = slot,
                buttons = layout.buttonsIn(slot),
                layout = layout,
                onChange = onChange
            )
        }
        val hidden = layout.hiddenButtons
        Text(
            "已隐藏",
            color = DesktopColors.TextGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )
        if (hidden.isEmpty()) {
            Text(
                "全部按钮均已显示",
                color = DesktopColors.TextGray.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
            )
        } else {
            hidden.forEach { button ->
                Row(
                    Modifier.fillMaxWidth().height(ReorderableSettingRowHeight).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.size(24.dp))
                    Icon(
                        button.icon(),
                        null,
                        tint = DesktopColors.TextGray,
                        modifier = Modifier.padding(start = 10.dp).size(18.dp)
                    )
                    Text(
                        button.title,
                        color = DesktopColors.TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f).padding(start = 10.dp)
                    )
                    SettingSwitch(false) { visible ->
                        onChange(TitleBarLayoutCodec.setVisible(layout, button, visible))
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleBarSlotSection(
    slot: TitleBarSlot,
    buttons: List<TitleBarButton>,
    layout: TitleBarLayout,
    onChange: (TitleBarLayout) -> Unit
) {
    Text(
        slot.label,
        color = DesktopColors.TextGray,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
    if (buttons.isEmpty()) {
        Text(
            "暂无按钮",
            color = DesktopColors.TextGray.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        )
        return
    }
    var working by remember(buttons) { mutableStateOf(buttons) }
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
        itemsIndexed(working, key = { _, button -> button.key }) { index, button ->
            ReorderableSettingRow(
                reorder = reorder,
                index = index,
                title = button.title,
                leadingIcon = button.icon(),
                onDragEnd = {
                    val updated = replaceSlot(currentLayout, slot, working)
                    if (updated != currentLayout) currentOnChange(updated)
                }
            ) {
                SettingsDropdown(
                    options = TitleBarSlot.entries,
                    current = slot,
                    label = { it.label },
                    menuWidth = 132.dp,
                    onSelect = { target ->
                        if (target != slot) {
                            reorder.cancel()
                            currentOnChange(TitleBarLayoutCodec.move(currentLayout, button, target))
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                SettingSwitch(true) { visible ->
                    reorder.cancel()
                    currentOnChange(TitleBarLayoutCodec.setVisible(currentLayout, button, visible))
                }
            }
        }
    }
}

private fun replaceSlot(
    layout: TitleBarLayout,
    slot: TitleBarSlot,
    buttons: List<TitleBarButton>
): TitleBarLayout = TitleBarLayoutCodec.normalize(
    when (slot) {
        TitleBarSlot.CENTER_START -> layout.copy(centerStart = buttons)
        TitleBarSlot.CENTER_END -> layout.copy(centerEnd = buttons)
        TitleBarSlot.TRAILING -> layout.copy(trailing = buttons)
    }
)
