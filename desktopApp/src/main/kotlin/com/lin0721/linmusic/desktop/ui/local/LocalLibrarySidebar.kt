package com.lin0721.linmusic.desktop.ui.local

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.core.localmusic.domain.LocalPlaylist
import com.lin0721.linmusic.core.ui.icons.Album
import com.lin0721.linmusic.core.ui.icons.Download
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.MoreHoriz
import com.lin0721.linmusic.core.ui.icons.MusicNote
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlin.math.roundToInt

private const val PLAYLIST_ROW_HEIGHT_DP = 40

@Composable
fun LocalLibrarySidebar(
    viewModel: LocalLibraryDesktopViewModel,
    onSelectNav: (LocalLibraryNav) -> Unit,
    onOpenPlaylist: (id: Long, title: String) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {}
) {
    val selectedNav by viewModel.selectedNav.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingPlaylist by remember { mutableStateOf<LocalPlaylist?>(null) }
    var deletingPlaylist by remember { mutableStateOf<LocalPlaylist?>(null) }

    Column(modifier.fillMaxSize().padding(top = 12.dp)) {
        Text(
            text = "本地曲库",
            color = DesktopColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            LocalLibraryNav.entries.forEach { nav ->
                LocalNavRow(
                    icon = navIcon(nav),
                    label = nav.label,
                    selected = selectedNav == nav,
                    onClick = {
                        viewModel.selectNav(nav)
                        onSelectNav(nav)
                    }
                )
            }
        }

        HorizontalDivider(
            color = DesktopColors.Divider.copy(alpha = 0.45f),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "歌单",
                color = DesktopColors.TextGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { showCreateDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = DesktopColors.TextGray, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        LocalPlaylistReorderList(
            playlists = playlists,
            onOpen = { playlist -> onOpenPlaylist(playlist.id, playlist.name) },
            onRename = { renamingPlaylist = it },
            onTogglePin = { viewModel.setPinned(it.id, !it.isPinned) },
            onDelete = { deletingPlaylist = it },
            onReorder = viewModel::reorderPlaylists,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        footer()
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建本地歌单") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("输入歌单名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.createPlaylist(name)
                    showCreateDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            }
        )
    }

    renamingPlaylist?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renamingPlaylist = null },
            title = { Text("重命名歌单") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("输入新名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.renamePlaylist(target.id, name)
                    renamingPlaylist = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renamingPlaylist = null }) { Text("取消") }
            }
        )
    }

    deletingPlaylist?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingPlaylist = null },
            title = { Text("删除歌单") },
            text = { Text("确定删除本地歌单「${target.name}」吗？（不会删除磁盘文件）") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(target.id)
                    deletingPlaylist = null
                }) { Text("删除", color = Color(0xFFE5534B)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingPlaylist = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun LocalNavRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) DesktopColors.Hover else Color.Transparent
    val tint = if (selected) DesktopColors.Accent else DesktopColors.TextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun LocalPlaylistReorderList(
    playlists: List<LocalPlaylist>,
    onOpen: (LocalPlaylist) -> Unit,
    onRename: (LocalPlaylist) -> Unit,
    onTogglePin: (LocalPlaylist) -> Unit,
    onDelete: (LocalPlaylist) -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { PLAYLIST_ROW_HEIGHT_DP.dp.toPx() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var draggingIndex by remember { mutableIntStateOf(-1) }

    if (playlists.isEmpty()) {
        Box(modifier.padding(16.dp), contentAlignment = Alignment.TopCenter) {
            Text("暂无本地歌单，点击右上角 + 新建", color = DesktopColors.TextGray, fontSize = 12.sp)
        }
        return
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(playlists, key = { _, item -> item.id }) { index, playlist ->
            val isDragging = draggingId == playlist.id
            val hover = remember { MutableInteractionSource() }
            val hovered by hover.collectIsHoveredAsState()
            var menuOpen by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PLAYLIST_ROW_HEIGHT_DP.dp)
                    .zIndex(if (isDragging) 1f else 0f)
                    .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDragging || hovered) DesktopColors.Hover else Color.Transparent)
                    .hoverable(hover)
                    .clickable { onOpen(playlist) }
                    .pointerInput(playlists.map { it.id }) {
                        detectDragGestures(
                            onDragStart = {
                                draggingId = playlist.id
                                draggingIndex = index
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y
                            },
                            onDragEnd = {
                                val startIdx = draggingIndex
                                val delta = (dragOffsetY / rowHeightPx).roundToInt()
                                if (startIdx in playlists.indices && delta != 0) {
                                    val targetIdx = (startIdx + delta).coerceIn(0, playlists.lastIndex)
                                    if (targetIdx != startIdx) {
                                        val mutable = playlists.map { it.id }.toMutableList()
                                        val moved = mutable.removeAt(startIdx)
                                        mutable.add(targetIdx, moved)
                                        onReorder(mutable)
                                    }
                                }
                                draggingId = null
                                draggingIndex = -1
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                draggingId = null
                                draggingIndex = -1
                                dragOffsetY = 0f
                            }
                        )
                    }
                    .pointerInput(playlist.id) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.button == PointerButton.Secondary) {
                                    menuOpen = true
                                }
                            }
                        }
                    }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MelodiaIcons.MusicNote,
                        contentDescription = null,
                        tint = if (playlist.isPinned) DesktopColors.Accent else DesktopColors.TextGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            color = DesktopColors.TextPrimary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${playlist.tracks.size}",
                        color = DesktopColors.TextGray,
                        fontSize = 11.sp
                    )
                    HoverReveal(revealed = hovered || menuOpen) {
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { menuOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = MelodiaIcons.MoreHoriz,
                                contentDescription = "更多",
                                tint = DesktopColors.TextGray,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (playlist.isPinned) "取消置顶" else "置顶歌单") },
                        onClick = {
                            menuOpen = false
                            onTogglePin(playlist)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            menuOpen = false
                            onRename(playlist)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除歌单", color = Color(0xFFE5534B)) },
                        onClick = {
                            menuOpen = false
                            onDelete(playlist)
                        }
                    )
                }
            }
        }
    }
}

private fun navIcon(nav: LocalLibraryNav): ImageVector = when (nav) {
    LocalLibraryNav.SONGS -> MelodiaIcons.MusicNote
    LocalLibraryNav.ALBUMS -> MelodiaIcons.Album
    LocalLibraryNav.ARTISTS -> MelodiaIcons.MusicNote
    LocalLibraryNav.FOLDERS -> MelodiaIcons.MusicNote
    LocalLibraryNav.DOWNLOADS -> MelodiaIcons.Download
}
