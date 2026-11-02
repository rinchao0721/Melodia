package com.lin0721.linmusic.desktop.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.data.local.LOCAL_ALL_TRACKS_ID
import com.lin0721.linmusic.desktop.data.local.LOCAL_DOWNLOADS_ID
import com.lin0721.linmusic.desktop.data.local.LocalPlaylistSummary
import com.lin0721.linmusic.desktop.ui.cloud.uploadFiles
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.local.pickAudioFiles
import com.lin0721.linmusic.desktop.ui.local.pickMusicFolder
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.library.ui.LibraryFilter
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import com.lin0721.linmusic.feature.library.ui.LibrarySortOrder
import com.lin0721.linmusic.feature.library.ui.LibraryUiState
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.Collator
import java.util.Locale

internal const val LOCAL_ITEM_PREFIX = "local:"
internal const val LOCAL_FILTER_LABEL = "本地音乐"

private val collator: Collator = Collator.getInstance(Locale.CHINA)

internal fun LibraryItem.localPlaylistId(): Long? =
    if (id.startsWith(LOCAL_ITEM_PREFIX)) id.removePrefix(LOCAL_ITEM_PREFIX).toLongOrNull() else null

internal val LibraryItem.isLocal: Boolean get() = id.startsWith(LOCAL_ITEM_PREFIX)

internal fun LocalPlaylistSummary.toLibraryItem(): LibraryItem = LibraryItem(
    id = "$LOCAL_ITEM_PREFIX$id",
    title = name,
    subtitle = "$trackCount 首",
    coverUrl = coverUri.orEmpty(),
    type = LibraryItemType.PLAYLIST,
    isPinned = isPinned,
    updateTime = createdAt,
    trackCount = trackCount,
    lastPlayTime = lastPlayedAt,
    isOwnedByMe = true
)

// 「本地音乐」胶囊只显示本地歌单（含全部本地歌曲）；「全部」与「歌单」把本地歌单混排进云端列表
internal fun mergeLibraryItems(
    cloud: List<LibraryItem>,
    local: List<LocalPlaylistSummary>,
    filter: LibraryFilter?,
    localOnly: Boolean,
    query: String,
    sort: LibrarySortOrder
): List<LibraryItem> {
    val localItems = when {
        localOnly -> local
        filter == null || filter == LibraryFilter.PLAYLIST ->
            local.filter { it.id != LOCAL_ALL_TRACKS_ID && (it.id != LOCAL_DOWNLOADS_ID || it.trackCount > 0) }
        else -> emptyList()
    }.map { it.toLibraryItem() }.filter { item ->
        query.isBlank() || item.title.contains(query, ignoreCase = true)
    }
    val cloudItems = if (localOnly) emptyList() else cloud
    val pinned = cloudItems.filter { it.isPinned } + localItems.filter { it.isPinned }.sortedWith(localOrder(sort))
    val cloudUnpinned = cloudItems.filterNot { it.isPinned }
    val localUnpinned = localItems.filterNot { it.isPinned }
    val unpinned = when (sort) {
        LibrarySortOrder.NAME -> (cloudUnpinned + localUnpinned).sortedWith(compareBy(collator) { it.title })
        LibrarySortOrder.RECENTLY_PLAYED -> (cloudUnpinned + localUnpinned.sortedWith(localOrder(sort)))
            .sortedByDescending { it.lastPlayTime }
        LibrarySortOrder.CUSTOM -> cloudUnpinned + localUnpinned.sortedWith(localOrder(sort))
    }
    return pinned + unpinned
}

// 内置歌单（全部、已下载）在前，其余按创建时间
private fun localOrder(sort: LibrarySortOrder): Comparator<LibraryItem> = when (sort) {
    LibrarySortOrder.NAME -> compareBy(collator) { it.title }
    LibrarySortOrder.RECENTLY_PLAYED -> compareByDescending<LibraryItem> { it.lastPlayTime }.thenBy { it.updateTime }
    LibrarySortOrder.CUSTOM -> compareBy<LibraryItem> { (it.localPlaylistId() ?: 0L) > 0L }.thenBy { it.updateTime }
}

@Composable
internal fun rememberLibraryDisplayItems(viewModel: LibraryViewModel, extras: LibraryExtrasEntry, isLoggedIn: Boolean): List<LibraryItem>? {
    val uiState by viewModel.uiState.collectAsState()
    val summaries by extras.local.repository.summaries.collectAsState()
    val filter by viewModel.selectedFilter.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val sort by viewModel.sortOrder.collectAsState()
    val cloudDrive by extras.cloud.library.state.collectAsState()
    val cloudSelected = extras.selected == LibraryExtraFilter.CLOUD
    LaunchedEffect(cloudSelected, isLoggedIn) {
        if (cloudSelected && isLoggedIn) extras.cloud.library.ensureLoaded()
    }
    if (cloudSelected) {
        return remember(cloudDrive, query, isLoggedIn) {
            if (!isLoggedIn) emptyList() else listOf(cloudLibraryItem(cloudDrive)).filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
        }
    }
    // 未登录时只剩本地歌单；云端仍在加载或出错时返回 null，交给原有状态展示
    val cloud = when {
        !isLoggedIn -> emptyList()
        else -> (uiState as? LibraryUiState.Success)?.filteredItems
    }
    val localOnly = extras.selected == LibraryExtraFilter.LOCAL || !isLoggedIn
    return remember(cloud, summaries, filter, query, sort, localOnly) {
        if (cloud == null && !localOnly) null else mergeLibraryItems(cloud.orEmpty(), summaries, filter, localOnly, query, sort)
    }
}

@Composable
internal fun LocalPlaylistBadge(modifier: Modifier = Modifier) {
    Badge(modifier = modifier, containerColor = DesktopColors.SurfaceLight, contentColor = DesktopColors.TextGray) {
        Text("本地歌单", fontSize = 10.sp)
    }
}

@Composable
internal fun LocalLibraryItemMenu(
    expanded: Boolean,
    offset: DpOffset,
    item: LibraryItem,
    extras: LibraryExtrasEntry,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val id = item.localPlaylistId() ?: return
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    val repository = extras.local.repository
    val isUserPlaylist = id > 0L
    DesktopMenu(expanded = expanded, onDismiss = onDismiss, offset = offset) {
        MenuItem("播放", icon = Icons.Rounded.PlayArrow, onClick = {
            onDismiss()
            extras.local.playPlaylist(id, shuffle = false) { navigator.showMessage("歌单里还没有歌曲") }
        })
        MenuItem("全部加入下一首播放", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
            onDismiss()
            extras.local.addPlaylistToPlayNext(id) { count ->
                navigator.showMessage(if (count > 0) "已添加至下一首播放" else "歌单里还没有歌曲")
            }
        })
        MenuItem("全部上传到云盘", icon = Icons.Rounded.CloudUpload, onClick = {
            onDismiss()
            scope.launch {
                val files = repository.tracksOf(id).first().map { File(it.path) }
                uploadFiles(extras.cloud, navigator, files)
            }
        })
        if (isUserPlaylist) {
            MenuItem("添加文件夹", icon = Icons.Rounded.CreateNewFolder, onClick = {
                onDismiss()
                scope.launch { pickMusicFolder()?.let { repository.addFolder(id, it) } }
            })
            MenuItem("添加歌曲文件", icon = Icons.Rounded.AudioFile, onClick = {
                onDismiss()
                scope.launch { pickAudioFiles().takeIf { it.isNotEmpty() }?.let { repository.addFiles(id, it) } }
            })
            MenuItem("重命名", icon = Icons.Rounded.DriveFileRenameOutline, onClick = {
                onDismiss()
                onRename()
            })
        }
        MenuItem(if (item.isPinned) "取消置顶" else "置顶歌单", icon = Icons.Rounded.PushPin, onClick = {
            onDismiss()
            repository.setPinned(id, !item.isPinned)
        })
        MenuItem("重新扫描", icon = Icons.Rounded.Refresh, onClick = {
            onDismiss()
            repository.rescan()
        })
        if (isUserPlaylist) {
            MenuDivider()
            MenuItem("移除歌单", icon = Icons.Rounded.Delete, danger = true, onClick = {
                onDismiss()
                onRemove()
            })
        }
    }
}

internal val LocalBadgeSpacing = 6.dp
