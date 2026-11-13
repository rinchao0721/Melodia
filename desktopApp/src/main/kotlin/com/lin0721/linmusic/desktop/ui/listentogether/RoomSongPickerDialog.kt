package com.lin0721.linmusic.desktop.ui.listentogether

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.listentogether.ui.RoomSongPickerViewModel

private val ListHeight = 420.dp

// 一起听点歌对话框：未输入时列出我喜欢的音乐，输入后搜索，点击行加入房间队列
@Composable
fun RoomSongPickerDialog(viewModel: RoomSongPickerViewModel, onDismiss: () -> Unit) {
    val navigator = LocalDesktopNavigator.current
    val state by viewModel.state.collectAsState()
    val roomSongIds by viewModel.roomSongIds.collectAsState()

    LaunchedEffect(viewModel) { viewModel.open() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { navigator.showMessage(it) } }

    DesktopDialog(
        title = "添加歌曲到一起听",
        onDismiss = onDismiss,
        width = 460.dp,
        actions = { DialogButton("完成", onDismiss, primary = true) }
    ) {
        DialogTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = "搜索歌曲、歌手",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text(state.title, color = DesktopColors.TextGray, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(ListHeight)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), color = DesktopColors.Accent, strokeWidth = 2.dp)
                state.error != null -> Text(state.error.orEmpty(), color = DesktopColors.TextGray, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
                state.tracks.isEmpty() -> Text(
                    if (state.query.isBlank()) "暂无喜欢的音乐，试试搜索" else "没有找到相关歌曲",
                    color = DesktopColors.TextGray,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.tracks, key = { it.songId }) { track ->
                        PickerRow(
                            track = track,
                            added = track.songId in state.added || track.songId in roomSongIds,
                            onAdd = { viewModel.add(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(track: QueueItem, added: Boolean, onAdd: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .hoverable(interaction)
            .background(if (hovered && !added) DesktopColors.Hover else Color.Transparent)
            .then(if (added) Modifier else Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onAdd))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(track.coverUrl, 36.dp, shape = RoundedCornerShape(4.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = DesktopColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = DesktopColors.TextGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            if (added) Icons.Rounded.Check else Icons.Rounded.Add,
            if (added) "已添加" else "添加到一起听队列",
            tint = if (added) DesktopColors.Success else DesktopColors.TextGray,
            modifier = Modifier.size(20.dp)
        )
    }
}
