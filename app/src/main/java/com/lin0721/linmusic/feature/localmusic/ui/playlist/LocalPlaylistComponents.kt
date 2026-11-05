package com.lin0721.linmusic.feature.localmusic.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.CreatePlaylistDialog
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.feature.localmusic.domain.LocalPlaylist
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalCover

private const val MOSAIC_SIZE = 4

// 满 4 首拼 2×2，不足时用第一首封面
@Composable
fun LocalPlaylistCover(tracks: List<LocalTrack>, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(if (size >= 80.dp) 8.dp else RadiusCompact)
    when {
        tracks.isEmpty() -> Box(
            modifier = modifier.size(size).clip(shape).background(MelodiaColors.SurfaceLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.4f)
            )
        }
        tracks.size < MOSAIC_SIZE -> LocalCover(sourceUri = tracks.first().uri, size = size, modifier = modifier, shape = shape)
        else -> Column(modifier = modifier.size(size).clip(shape)) {
            val half = size / 2
            tracks.take(MOSAIC_SIZE).chunked(2).forEach { pair ->
                Row {
                    pair.forEach { track ->
                        LocalCover(sourceUri = track.uri, size = half, shape = RoundedCornerShape(0.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NewPlaylistTile(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MelodiaColors.Overlay.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.35f)
        )
    }
}

// "加入本地歌单"弹层：单曲点一下切换加入/移出，多首只追加
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistPickerSheet(
    tracks: List<LocalTrack>,
    playlists: List<LocalPlaylist>,
    onDismiss: () -> Unit,
    onCreate: (name: String) -> Unit,
    onToggleSingle: (LocalPlaylist, LocalTrack) -> Unit,
    onAddBatch: (LocalPlaylist) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    val trackUris = remember(tracks) { tracks.mapTo(HashSet()) { it.uri.toString() } }
    val single = tracks.singleOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MelodiaColors.Background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(modifier = Modifier.padding(bottom = MelodiaSpacing.xl)) {
            Text(
                text = if (single != null) "「${single.title}」加入本地歌单" else "${tracks.size} 首加入本地歌单",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
            )
            LazyColumn {
                item(key = "create") {
                    PickerRow(
                        leading = { NewPlaylistTile(size = 44.dp) },
                        title = "新建歌单",
                        subtitle = null,
                        onClick = { showCreateDialog = true }
                    )
                }
                items(playlists, key = { it.id }) { playlist ->
                    val allContained = playlist.tracks.count { it.uri.toString() in trackUris } == tracks.size
                    PickerRow(
                        leading = { LocalPlaylistCover(tracks = playlist.tracks, size = 44.dp) },
                        title = playlist.name,
                        subtitle = "${playlist.tracks.size} 首" + if (allContained) " · 已包含" else "",
                        trailingChecked = allContained,
                        onClick = {
                            if (single != null) onToggleSingle(playlist, single) else onAddBatch(playlist)
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = onCreate,
            title = "新建本地歌单",
            confirmText = "创建并加入"
        )
    }
}

@Composable
private fun PickerRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailingChecked: Boolean? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(MelodiaPress.Row, onClick = onClick)
            .padding(horizontal = MelodiaSpacing.md, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(MelodiaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        if (trailingChecked != null) {
            Icon(
                imageVector = if (trailingChecked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (trailingChecked) "已包含" else "未包含",
                tint = if (trailingChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
