package com.lin0721.linmusic.feature.localmusic.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicUiState
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicViewModel

// 本地音乐详情页（专辑、歌手、文件夹）顶部的多选入口与多选态操作，与"全部歌曲"页的图标行为一致：
// 未多选时只有"多选"；多选后已选歌曲可加入本地歌单，删除/退出共用最后一个按钮
@Composable
fun RowScope.LocalSelectionActions(
    state: LocalMusicUiState.Success,
    tracks: List<LocalTrack>,
    viewModel: LocalMusicViewModel,
    onRequestDelete: () -> Unit
) {
    if (!state.isSelectionMode) {
        MelodiaIconButton(onClick = { viewModel.toggleSelectionMode() }) {
            Icon(Icons.Rounded.Checklist, contentDescription = "多选", tint = MelodiaColors.TextPrimary)
        }
        return
    }
    if (state.selectedUris.isNotEmpty()) {
        MelodiaIconButton(onClick = {
            viewModel.openPlaylistPicker(tracks.filter { it.uri.toString() in state.selectedUris })
        }) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "加入本地歌单", tint = MelodiaColors.TextPrimary)
        }
    }
    MelodiaIconButton(onClick = {
        if (state.selectedUris.isEmpty()) viewModel.exitSelectionMode() else onRequestDelete()
    }) {
        if (state.selectedUris.isEmpty()) {
            Icon(Icons.Rounded.Close, contentDescription = "退出多选", tint = MelodiaColors.TextPrimary)
        } else {
            Icon(Icons.Rounded.Delete, contentDescription = "删除选中", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun LocalBatchDeleteDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量处理", fontWeight = FontWeight.Bold) },
        text = { Text("确定要处理选中的 $count 首歌曲吗？普通本地文件会被永久删除，导入的歌曲将被移出列表。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确定", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
