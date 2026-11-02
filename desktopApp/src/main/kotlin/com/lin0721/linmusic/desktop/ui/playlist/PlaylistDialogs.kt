package com.lin0721.linmusic.desktop.ui.playlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.desktop.data.CoverImage
import com.lin0721.linmusic.desktop.data.chooseImageFile
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogLabel
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.playlist.ui.AddMusicSearchState
import com.lin0721.linmusic.feature.playlist.ui.PlaylistImportState
import com.lin0721.linmusic.feature.playlist.ui.PlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

private const val MAX_NAME_LENGTH = 40
private const val MAX_DESC_LENGTH = 1000
private const val SEARCH_RESULT_MAX_HEIGHT = 360

@Composable
internal fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DesktopColors.TextPrimary,
    unfocusedBorderColor = DesktopColors.SurfaceLight,
    cursorColor = DesktopColors.TextPrimary,
    focusedTextColor = DesktopColors.TextPrimary,
    unfocusedTextColor = DesktopColors.TextPrimary
)

@Composable
internal fun EditPlaylistDialog(
    initialName: String,
    initialDescription: String,
    coverUrl: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, coverBytes: ByteArray?) -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var coverBytes by remember { mutableStateOf<ByteArray?>(null) }
    var coverPreview by remember { mutableStateOf<ImageBitmap?>(null) }

    val changed = name.trim() != initialName.trim() || description.trim() != initialDescription.trim() || coverBytes != null
    val canSubmit = name.isNotBlank() && changed && !isSaving

    val pickCover: () -> Unit = {
        scope.launch {
            val file = chooseImageFile("选择歌单封面") ?: return@launch
            val prepared = withContext(Dispatchers.IO) { CoverImage.prepareJpeg(file) }
            prepared.onSuccess { bytes ->
                coverBytes = bytes
                coverPreview = withContext(Dispatchers.IO) { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }
            }.onFailure { navigator.showMessage(it.message ?: "无法处理该图片") }
        }
    }

    DesktopDialog(
        title = "名称和详情",
        onDismiss = { if (!isSaving) onDismiss() },
        width = 480.dp,
        actions = {
            DialogButton("取消", onDismiss, enabled = !isSaving)
            DialogButton(
                "保存",
                { onConfirm(name.trim(), description.trim(), coverBytes) },
                primary = true,
                enabled = canSubmit,
                loading = isSaving
            )
        }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(120.dp).clip(RoundedCornerShape(6.dp)).pointerHoverIcon(PointerIcon.Hand)
                    .clickable(enabled = !isSaving, onClick = pickCover)
            ) {
                val preview = coverPreview
                if (preview != null) {
                    Image(preview, "新封面", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Cover(coverUrl, 120.dp, shape = RoundedCornerShape(6.dp))
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Edit, "更换封面", tint = DesktopColors.TextPrimary, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                DialogLabel("名称  ${name.length}/$MAX_NAME_LENGTH")
                DialogTextField(value = name, onValueChange = { name = it.take(MAX_NAME_LENGTH) }, enabled = !isSaving)
                DialogLabel("简介", Modifier.padding(top = 10.dp))
                DialogTextField(
                    value = description,
                    onValueChange = { description = it.take(MAX_DESC_LENGTH) },
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isSaving
                )
            }
        }
    }
}

@Composable
internal fun AddSongsDialog(
    playlistId: Long,
    existingIds: Set<Long>,
    viewModel: PlaylistViewModel,
    onDismiss: () -> Unit
) {
    val query by viewModel.addMusicSearchQuery.collectAsState()
    val searchState by viewModel.addMusicSearchState.collectAsState()
    var adding by remember { mutableStateOf(emptySet<Long>()) }
    DisposableEffect(Unit) { onDispose { viewModel.clearAddMusicSearch() } }

    DesktopDialog(
        title = "添加歌曲",
        onDismiss = onDismiss,
        width = 440.dp,
        actions = { DialogButton("完成", onDismiss, primary = true) }
    ) {
        DialogTextField(
            value = query,
            onValueChange = viewModel::updateAddMusicSearchQuery,
            placeholder = "搜索歌曲、歌手"
        )
        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = SEARCH_RESULT_MAX_HEIGHT.dp).padding(top = 8.dp)) {
            when (val state = searchState) {
                AddMusicSearchState.Idle -> CenteredHint("输入关键词搜索要添加的歌曲")
                AddMusicSearchState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = DesktopColors.Accent)
                }
                is AddMusicSearchState.Error -> CenteredHint(state.message)
                is AddMusicSearchState.Success -> if (state.tracks.isEmpty()) {
                    CenteredHint("没有找到相关歌曲")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.tracks, key = { it.id }) { track ->
                            AddSongRow(
                                track = track,
                                added = track.id in existingIds,
                                busy = track.id in adding,
                                onAdd = {
                                    adding = adding + track.id
                                    viewModel.addTrackToPlaylist(playlistId, track) { adding = adding - track.id }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ImportToPlaylistDialog(
    state: PlaylistImportState,
    onPick: (UserPlaylist) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    DesktopDialog(
        title = "添加到歌单",
        onDismiss = onDismiss,
        width = 380.dp,
        actions = { DialogButton("取消", onDismiss) }
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = SEARCH_RESULT_MAX_HEIGHT.dp)) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = DesktopColors.Accent)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "create") {
                        ImportTargetRow(onClick = onCreate) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(DesktopColors.Surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Add, null, tint = DesktopColors.TextPrimary, modifier = Modifier.size(22.dp))
                            }
                            Text(
                                "新建歌单",
                                color = DesktopColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                    items(state.items, key = { it.id }) { playlist ->
                        ImportTargetRow(onClick = { onPick(playlist) }) {
                            Cover(playlist.coverImgUrl, 40.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    playlist.name,
                                    color = DesktopColors.TextPrimary,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("${playlist.trackCount} 首", color = DesktopColors.TextGray, fontSize = 12.sp)
                            }
                        }
                    }
                    if (state.items.isEmpty()) {
                        item(key = "empty") {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("还没有自建歌单", color = DesktopColors.TextGray, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportTargetRow(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = DesktopColors.TextGray, fontSize = 13.sp)
    }
}

@Composable
private fun AddSongRow(track: Track, added: Boolean, busy: Boolean, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(track.al.picUrl, 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.name, color = DesktopColors.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                track.ar.joinToString(" / ") { it.name },
                color = DesktopColors.TextGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onAdd, enabled = !added && !busy, modifier = Modifier.size(36.dp)) {
            when {
                added -> Icon(Icons.Rounded.Check, "已在歌单中", tint = DesktopColors.Accent, modifier = Modifier.size(20.dp))
                busy -> CircularProgressIndicator(Modifier.size(16.dp), color = DesktopColors.TextGray, strokeWidth = 2.dp)
                else -> Icon(Icons.Rounded.Add, "添加到歌单", tint = DesktopColors.TextPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
