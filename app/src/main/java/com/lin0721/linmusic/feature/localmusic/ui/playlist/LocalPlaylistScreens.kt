package com.lin0721.linmusic.feature.localmusic.ui.playlist

import com.lin0721.linmusic.core.ui.components.rememberDragReorderState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.CreatePlaylistDialog
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.localmusic.domain.LocalPlaylist
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.ui.LocalLibraryStateGate
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicNavigation
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicViewModel
import com.lin0721.linmusic.feature.localmusic.ui.LocalTrackActionsHost
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalCover
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalHeroHeader
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalPlayShuffleButtons
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalTrackRow
import org.koin.androidx.compose.koinViewModel

private val PlaylistHeroCoverSize = 132.dp

@Composable
fun LocalPlaylistsScreen(
    navigation: LocalMusicNavigation,
    viewModel: LocalMusicViewModel = koinViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    SecondaryScreenScaffold(
        title = "本地歌单",
        onBack = navigation.onBack,
        actions = {
            MelodiaIconButton(onClick = { showCreate = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "新建歌单", tint = MelodiaColors.TextPrimary)
            }
        }
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LocalLibraryStateGate(viewModel) {
                if (playlists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(
                                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                title = "建一个自己的歌单",
                                subtitle = "在歌曲的更多菜单里也能直接加入歌单。"
                            )
                            MelodiaButton(onClick = { showCreate = true }) {
                                Text("新建歌单", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    return@LocalLibraryStateGate
                }
                LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable(MelodiaPress.Row) { navigation.openPlaylist(playlist.id) }
                                .padding(horizontal = MelodiaSpacing.md, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LocalPlaylistCover(tracks = playlist.tracks, size = 52.dp)
                            Spacer(Modifier.width(MelodiaSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${playlist.tracks.size} 首",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onCreate = { viewModel.createPlaylist(it) },
            title = "新建本地歌单",
            confirmText = "创建"
        )
    }

    LocalTrackActionsHost(viewModel = viewModel, navigation = navigation)
}

@Composable
fun LocalPlaylistScreen(
    playlistId: Long,
    navigation: LocalMusicNavigation,
    viewModel: LocalMusicViewModel = koinViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playingMediaId by viewModel.playingMediaId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(enabled = editing) { editing = false }

    Box(modifier = Modifier.fillMaxSize().background(MelodiaColors.PageFill)) {
        LocalLibraryStateGate(viewModel) {
            when {
                playlist == null -> MissingPlaylist(onBack = navigation.onBack)
                editing -> EditPlaylistContent(
                    playlist = playlist,
                    onCancel = { editing = false },
                    onDone = { ordered ->
                        viewModel.savePlaylistTracks(playlist.id, ordered)
                        editing = false
                    }
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md)) {
                    item(key = "hero") {
                        LocalHeroHeader(
                            coverSourceUri = playlist.tracks.firstOrNull()?.uri,
                            onBack = navigation.onBack,
                            actions = {
                                if (playlist.tracks.isNotEmpty()) {
                                    MelodiaIconButton(onClick = { editing = true }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "编辑歌单", tint = MelodiaColors.TextPrimary)
                                    }
                                }
                                Box {
                                    MelodiaIconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = MelodiaColors.TextPrimary)
                                    }
                                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                        DropdownMenuItem(text = { Text("重命名") }, onClick = {
                                            showMenu = false
                                            showRename = true
                                        })
                                        DropdownMenuItem(text = { Text("删除歌单", color = MaterialTheme.colorScheme.error) }, onClick = {
                                            showMenu = false
                                            confirmDelete = true
                                        })
                                    }
                                }
                            }
                        ) {
                            PlaylistHeroInfo(playlist)
                            if (playlist.tracks.isNotEmpty()) {
                                Spacer(Modifier.height(MelodiaSpacing.md))
                                LocalPlayShuffleButtons(
                                    onPlay = { viewModel.playTracks(playlist.tracks) },
                                    onShuffle = { viewModel.playTracks(playlist.tracks, shuffle = true) }
                                )
                            }
                        }
                    }
                    if (playlist.tracks.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                title = "歌单里还没有歌",
                                subtitle = "在歌曲的更多菜单里选择「加入本地歌单」。",
                                modifier = Modifier.padding(top = MelodiaSpacing.xl)
                            )
                        }
                    }
                    items(playlist.tracks, key = { "track_${it.uri}" }) { track ->
                        LocalTrackRow(
                            track = track,
                            subtitle = track.artist,
                            playingMediaId = playingMediaId,
                            isPlaying = isPlaying,
                            onClick = { viewModel.playTracks(playlist.tracks, start = track) },
                            onMoreClick = { coverUrl ->
                                viewModel.openTrackMenu(track, playlist.tracks, coverUrl, playlistId = playlist.id)
                            }
                        )
                    }
                }
            }
        }
    }

    LocalTrackActionsHost(viewModel = viewModel, navigation = navigation)

    if (showRename && playlist != null) {
        CreatePlaylistDialog(
            onDismiss = { showRename = false },
            onCreate = { viewModel.renamePlaylist(playlist.id, it) },
            title = "重命名歌单",
            confirmText = "保存",
            initialName = playlist.name
        )
    }

    if (confirmDelete && playlist != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除歌单", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除「${playlist.name}」吗？歌曲文件不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deletePlaylist(playlist)
                    navigation.onBack()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PlaylistHeroInfo(playlist: LocalPlaylist) {
    Row(verticalAlignment = Alignment.Bottom) {
        LocalPlaylistCover(tracks = playlist.tracks, size = PlaylistHeroCoverSize)
        Spacer(Modifier.width(MelodiaSpacing.md))
        Column {
            Text(
                text = playlist.name,
                color = MelodiaColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            val minutes = playlist.tracks.sumOf { it.durationMs } / 60_000
            Text(
                text = listOfNotNull("本地歌单", "${playlist.tracks.size} 首", minutes.takeIf { it > 0 }?.let { "$it 分钟" })
                    .joinToString(" · "),
                color = MelodiaColors.TextPrimary.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = MelodiaSpacing.xs)
            )
        }
    }
}

// 改动只存在本地副本，"完成"才写库
@Composable
private fun EditPlaylistContent(
    playlist: LocalPlaylist,
    onCancel: () -> Unit,
    onDone: (List<LocalTrack>) -> Unit
) {
    var working by remember(playlist.id) { mutableStateOf(playlist.tracks) }
    val listState = rememberLazyListState()
    val reorder = rememberDragReorderState(listState) { from, to ->
        working = working.toMutableList().apply { add(to, removeAt(from)) }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MelodiaSpacing.xs, vertical = MelodiaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MelodiaIconButton(onClick = onCancel) {
                Icon(Icons.Rounded.Close, contentDescription = "放弃修改", tint = MelodiaColors.TextPrimary)
            }
            Text(
                text = "编辑歌单",
                color = MelodiaColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = MelodiaSpacing.xs)
            )
            MelodiaTextButton(onClick = { onDone(working) }) {
                Text("完成", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text = "按住右侧把手拖动排序",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.xs)
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md),
            modifier = Modifier.weight(1f)
        ) {
            items(working, key = { it.uri.toString() }) { track ->
                val key = track.uri.toString()
                val dragging = reorder.draggingKey == key
                Row(
                    modifier = Modifier
                        .then(if (dragging) Modifier.zIndex(1f) else if (LocalUiMotion.current.enabled) Modifier.animateItem() else Modifier)
                        .graphicsLayer { translationY = if (dragging) reorder.dragOffset else 0f }
                        .fillMaxWidth()
                        .background(if (dragging) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .padding(start = MelodiaSpacing.xs, end = MelodiaSpacing.xs, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MelodiaIconButton(onClick = { working = working.filterNot { it.uri == track.uri } }) {
                        Icon(
                            Icons.Rounded.RemoveCircleOutline,
                            contentDescription = "移出 ${track.title}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    LocalCover(sourceUri = track.uri, size = 44.dp, shape = RoundedCornerShape(6.dp))
                    Spacer(Modifier.width(MelodiaSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .pointerInput(key) {
                                detectDragGestures(
                                    onDragStart = { reorder.start(key) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        reorder.drag(amount.y)
                                    },
                                    onDragEnd = { reorder.end() },
                                    onDragCancel = { reorder.end() }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.DragHandle, contentDescription = "拖动排序", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingPlaylist(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        MelodiaIconButton(onClick = onBack, modifier = Modifier.padding(MelodiaSpacing.xs)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MelodiaColors.TextPrimary)
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(icon = Icons.AutoMirrored.Rounded.QueueMusic, title = "歌单不存在或已删除")
        }
    }
}
