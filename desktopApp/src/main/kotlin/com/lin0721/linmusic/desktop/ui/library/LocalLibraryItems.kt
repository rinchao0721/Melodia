package com.lin0721.linmusic.desktop.ui.library

import com.lin0721.linmusic.core.localmusic.domain.LocalLibraryIndex
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.ui.icons.Edit
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
import com.lin0721.linmusic.desktop.ui.local.LocalLibraryDesktopViewModel
import com.lin0721.linmusic.desktop.ui.local.LocalLibraryFilter
import com.lin0721.linmusic.desktop.ui.local.LocalLibraryNav
import com.lin0721.linmusic.desktop.ui.local.LocalLibrarySortOrder
import com.lin0721.linmusic.desktop.ui.local.pickAudioFiles
import com.lin0721.linmusic.desktop.ui.local.pickMusicFolder
import com.lin0721.linmusic.desktop.ui.shell.DesktopNavigator
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
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.MusicNote
import com.lin0721.linmusic.core.ui.icons.Play
import com.lin0721.linmusic.core.ui.icons.Queue
import com.lin0721.linmusic.core.ui.icons.Delete
import com.lin0721.linmusic.core.ui.icons.PushPin
import com.lin0721.linmusic.core.ui.icons.Refresh
import com.lin0721.linmusic.core.ui.icons.Upload
import com.lin0721.linmusic.core.ui.icons.NewFolder

internal const val LOCAL_ITEM_PREFIX = "local:"
internal const val LOCAL_ALBUM_PREFIX = "local_album:"
internal const val LOCAL_ARTIST_PREFIX = "local_artist:"
internal const val LOCAL_FOLDER_PREFIX = "local_folder:"
internal const val LOCAL_FILTER_LABEL = "本地音乐"

private val collator: Collator = Collator.getInstance(Locale.CHINA)

internal fun LibraryItem.localPlaylistId(): Long? =
    if (id.startsWith(LOCAL_ITEM_PREFIX)) id.removePrefix(LOCAL_ITEM_PREFIX).toLongOrNull() else null

internal fun LibraryItem.localAlbumKey(): String? =
    if (id.startsWith(LOCAL_ALBUM_PREFIX)) id.removePrefix(LOCAL_ALBUM_PREFIX) else null

internal fun LibraryItem.localArtistName(): String? =
    if (id.startsWith(LOCAL_ARTIST_PREFIX)) id.removePrefix(LOCAL_ARTIST_PREFIX) else null

internal fun LibraryItem.localFolderPath(): String? =
    if (id.startsWith(LOCAL_FOLDER_PREFIX)) id.removePrefix(LOCAL_FOLDER_PREFIX) else null

internal val LibraryItem.isLocal: Boolean get() = id.startsWith(LOCAL_ITEM_PREFIX)

internal val LibraryItem.isLocalCollection: Boolean
    get() = id.startsWith(LOCAL_ALBUM_PREFIX) || id.startsWith(LOCAL_ARTIST_PREFIX) || id.startsWith(LOCAL_FOLDER_PREFIX)

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

private fun firstTrackCover(tracks: List<LocalTrack>, coverForTrack: (LocalTrack) -> String?): String =
    tracks.firstNotNullOfOrNull(coverForTrack).orEmpty()

// 纯本地模式下构造侧栏列表：顶部固定「全部歌曲」「已下载」不受筛选影响，其余按筛选/搜索/排序组合
internal fun buildLocalModeLibraryItems(
    summaries: List<LocalPlaylistSummary>,
    index: LocalLibraryIndex,
    downloadTrackCount: Int,
    downloadCoverUri: String?,
    coverForTrack: (LocalTrack) -> String?,
    filter: LocalLibraryFilter?,
    query: String,
    sort: LocalLibrarySortOrder
): List<LibraryItem> {
    val allSummary = summaries.firstOrNull { it.id == LOCAL_ALL_TRACKS_ID }
    val dlSummary = summaries.firstOrNull { it.id == LOCAL_DOWNLOADS_ID }
    val allCount = allSummary?.trackCount ?: index.tracks.size
    val dlCount = dlSummary?.trackCount ?: downloadTrackCount

    val fixedPinned = listOf(
        LibraryItem(
            id = "$LOCAL_ITEM_PREFIX$LOCAL_ALL_TRACKS_ID",
            title = "全部歌曲",
            subtitle = "$allCount 首",
            coverUrl = allSummary?.coverUri ?: firstTrackCover(index.tracks, coverForTrack),
            type = LibraryItemType.PLAYLIST,
            isPinned = true,
            trackCount = allCount,
            isOwnedByMe = true
        ),
        LibraryItem(
            id = "$LOCAL_ITEM_PREFIX$LOCAL_DOWNLOADS_ID",
            title = "已下载",
            subtitle = "$dlCount 首",
            coverUrl = dlSummary?.coverUri ?: downloadCoverUri.orEmpty(),
            type = LibraryItemType.PLAYLIST,
            isPinned = true,
            trackCount = dlCount,
            isOwnedByMe = true
        )
    ).filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }

    val userPlaylists = if (filter == null || filter == LocalLibraryFilter.PLAYLIST) {
        summaries.filter { it.id > 0L }.map { it.toLibraryItem() }
    } else {
        emptyList()
    }
    val albums = if (filter == null || filter == LocalLibraryFilter.ALBUM) {
        index.knownAlbums.map { album ->
            LibraryItem(
                id = "$LOCAL_ALBUM_PREFIX${album.key}",
                title = album.name,
                subtitle = "专辑 · ${album.artist}",
                coverUrl = firstTrackCover(album.tracks, coverForTrack),
                type = LibraryItemType.ALBUM,
                updateTime = album.tracks.maxOfOrNull { it.dateAddedMs } ?: 0L,
                trackCount = album.tracks.size
            )
        }
    } else {
        emptyList()
    }
    val artists = if (filter == null || filter == LocalLibraryFilter.ARTIST) {
        index.artists.map { artist ->
            LibraryItem(
                id = "$LOCAL_ARTIST_PREFIX${artist.name}",
                title = artist.name,
                subtitle = "歌手 · ${artist.tracks.size} 首",
                coverUrl = firstTrackCover(artist.tracks, coverForTrack),
                type = LibraryItemType.ARTIST,
                updateTime = artist.tracks.maxOfOrNull { it.dateAddedMs } ?: 0L,
                trackCount = artist.tracks.size
            )
        }
    } else {
        emptyList()
    }
    val folders = if (filter == null || filter == LocalLibraryFilter.FOLDER) {
        index.folders.map { folder ->
            LibraryItem(
                id = "$LOCAL_FOLDER_PREFIX${folder.path}",
                title = folder.name,
                subtitle = "文件夹 · ${folder.tracks.size} 首",
                coverUrl = firstTrackCover(folder.tracks, coverForTrack),
                type = LibraryItemType.PLAYLIST,
                updateTime = folder.tracks.maxOfOrNull { it.dateAddedMs } ?: 0L,
                trackCount = folder.tracks.size
            )
        }
    } else {
        emptyList()
    }

    val filtered = (userPlaylists + albums + artists + folders).filter {
        query.isBlank() || it.title.contains(query, ignoreCase = true)
    }
    val comparator = localModeComparator(sort)
    val pinnedUser = filtered.filter { it.isPinned }.let { list ->
        if (comparator != null) list.sortedWith(comparator) else list
    }
    val unpinned = filtered.filterNot { it.isPinned }.let { list ->
        if (comparator != null) list.sortedWith(comparator) else list
    }
    return fixedPinned + pinnedUser + unpinned
}

private fun localModeComparator(sort: LocalLibrarySortOrder): Comparator<LibraryItem>? = when (sort) {
    LocalLibrarySortOrder.RECENTLY_ADDED -> compareByDescending<LibraryItem> { it.updateTime }.thenBy(collator) { it.title }
    LocalLibrarySortOrder.NAME -> compareBy(collator) { it.title }
    LocalLibrarySortOrder.CUSTOM -> null
}

internal fun tracksForLocalCollection(item: LibraryItem, index: LocalLibraryIndex): List<LocalTrack> {
    item.localAlbumKey()?.let { return index.album(it)?.tracks.orEmpty() }
    item.localArtistName()?.let { return index.artist(it)?.tracks.orEmpty() }
    item.localFolderPath()?.let { return index.folder(it)?.tracks.orEmpty() }
    return emptyList()
}

internal fun openLocalModeItem(
    item: LibraryItem,
    localVm: LocalLibraryDesktopViewModel,
    navigator: DesktopNavigator,
    onCollapseExpanded: () -> Unit
) {
    val playlistId = item.localPlaylistId()
    when {
        playlistId == LOCAL_ALL_TRACKS_ID -> {
            localVm.selectNav(LocalLibraryNav.SONGS)
            navigator.openHome()
            onCollapseExpanded()
        }
        playlistId == LOCAL_DOWNLOADS_ID -> {
            localVm.selectNav(LocalLibraryNav.DOWNLOADS)
            navigator.openHome()
            onCollapseExpanded()
        }
        playlistId != null -> {
            navigator.openLocalPlaylist(playlistId, item.title)
        }
        item.localAlbumKey() != null -> {
            val album = localVm.index.value.album(item.localAlbumKey()!!) ?: return
            localVm.selectNav(LocalLibraryNav.ALBUMS)
            localVm.selectAlbum(album)
            navigator.openHome()
            onCollapseExpanded()
        }
        item.localArtistName() != null -> {
            val artist = localVm.index.value.artist(item.localArtistName()!!) ?: return
            localVm.selectNav(LocalLibraryNav.ARTISTS)
            localVm.selectArtist(artist)
            navigator.openHome()
            onCollapseExpanded()
        }
        item.localFolderPath() != null -> {
            localVm.selectNav(LocalLibraryNav.FOLDERS)
            localVm.selectFolder(item.localFolderPath()!!)
            navigator.openHome()
            onCollapseExpanded()
        }
    }
}

@Composable
internal fun rememberLibraryDisplayItems(
    viewModel: LibraryViewModel,
    extras: LibraryExtrasEntry,
    isLoggedIn: Boolean,
    hasCloudAccount: Boolean = isLoggedIn
): List<LibraryItem>? {
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
    // 未登录任一云端账号时只剩本地歌单；云端仍在加载或出错时返回 null，交给原有状态展示
    val cloud = when {
        !hasCloudAccount -> emptyList()
        else -> (uiState as? LibraryUiState.Success)?.filteredItems
    }
    val localOnly = extras.selected == LibraryExtraFilter.LOCAL || !hasCloudAccount
    return remember(cloud, summaries, filter, query, sort, localOnly) {
        if (cloud == null && !localOnly) null else mergeLibraryItems(cloud.orEmpty(), summaries, filter, localOnly, query, sort)
    }
}

@Composable
internal fun rememberLocalLibraryDisplayItems(
    localVm: LocalLibraryDesktopViewModel,
    extras: LibraryExtrasEntry
): List<LibraryItem> {
    val summaries by extras.local.repository.summaries.collectAsState()
    val index by localVm.index.collectAsState()
    val downloadTracks by localVm.downloadTracks.collectAsState()
    val filter by localVm.sidebarFilter.collectAsState()
    val query by localVm.sidebarQuery.collectAsState()
    val sort by localVm.sidebarSortOrder.collectAsState()
    return remember(summaries, index, downloadTracks, filter, query, sort) {
        val dlCover = downloadTracks.firstNotNullOfOrNull { localVm.coverUriOf(it) }
        buildLocalModeLibraryItems(
            summaries = summaries,
            index = index,
            downloadTrackCount = downloadTracks.size,
            downloadCoverUri = dlCover,
            coverForTrack = localVm::coverUriOf,
            filter = filter,
            query = query,
            sort = sort
        )
    }
}

@Composable
internal fun LocalPlaylistBadge(modifier: Modifier = Modifier) {
    Badge(modifier = modifier, containerColor = DesktopColors.SurfaceLight, contentColor = DesktopColors.TextGray) {
        Text("本地歌单", fontSize = 10.sp)
    }
}

@Composable
internal fun KugouItemBadge(modifier: Modifier = Modifier) {
    Badge(modifier = modifier, containerColor = DesktopColors.SurfaceLight, contentColor = DesktopColors.TextGray) {
        Text("酷狗", fontSize = 10.sp)
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
    onDismiss: () -> Unit,
    isPureLocalMode: Boolean = false
) {
    val id = item.localPlaylistId() ?: return
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    val repository = extras.local.repository
    val isUserPlaylist = id > 0L
    DesktopMenu(expanded = expanded, onDismiss = onDismiss, offset = offset) {
        MenuItem("播放", icon = MelodiaIcons.Play, onClick = {
            onDismiss()
            extras.local.playPlaylist(id, shuffle = false) { navigator.showMessage("歌单里还没有歌曲") }
        })
        MenuItem("全部加入下一首播放", icon = MelodiaIcons.Queue, onClick = {
            onDismiss()
            extras.local.addPlaylistToPlayNext(id) { count ->
                navigator.showMessage(if (count > 0) "已添加至下一首播放" else "歌单里还没有歌曲")
            }
        })
        if (!isPureLocalMode) {
            MenuItem("全部上传到云盘", icon = MelodiaIcons.Upload, onClick = {
                onDismiss()
                scope.launch {
                    val files = repository.tracksOf(id).first().map { File(it.path) }
                    uploadFiles(extras.cloud, navigator, files)
                }
            })
        }
        if (isUserPlaylist) {
            MenuItem("添加文件夹", icon = MelodiaIcons.NewFolder, onClick = {
                onDismiss()
                scope.launch { pickMusicFolder()?.let { repository.addFolder(id, it) } }
            })
            MenuItem("添加歌曲文件", icon = MelodiaIcons.MusicNote, onClick = {
                onDismiss()
                scope.launch { pickAudioFiles().takeIf { it.isNotEmpty() }?.let { repository.addFiles(id, it) } }
            })
            MenuItem("重命名", icon = MelodiaIcons.Edit, onClick = {
                onDismiss()
                onRename()
            })
        }
        if (!isPureLocalMode || isUserPlaylist) {
            MenuItem(if (item.isPinned) "取消置顶" else "置顶歌单", icon = MelodiaIcons.PushPin, onClick = {
                onDismiss()
                repository.setPinned(id, !item.isPinned)
            })
        }
        MenuItem("重新扫描", icon = MelodiaIcons.Refresh, onClick = {
            onDismiss()
            repository.rescan()
        })
        if (isUserPlaylist) {
            MenuDivider()
            MenuItem("移除歌单", icon = MelodiaIcons.Delete, danger = true, onClick = {
                onDismiss()
                onRemove()
            })
        }
    }
}

@Composable
internal fun LocalCollectionItemMenu(
    expanded: Boolean,
    offset: DpOffset,
    item: LibraryItem,
    localVm: LocalLibraryDesktopViewModel,
    onDismiss: () -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    val index by localVm.index.collectAsState()
    DesktopMenu(expanded = expanded, onDismiss = onDismiss, offset = offset) {
        MenuItem("播放", icon = MelodiaIcons.Play, onClick = {
            onDismiss()
            val tracks = tracksForLocalCollection(item, index)
            if (tracks.isEmpty()) navigator.showMessage("还没有歌曲") else localVm.playTracks(tracks, 0)
        })
        MenuItem("全部加入下一首播放", icon = MelodiaIcons.Queue, onClick = {
            onDismiss()
            val tracks = tracksForLocalCollection(item, index)
            if (tracks.isEmpty()) {
                navigator.showMessage("还没有歌曲")
            } else {
                localVm.addTracksToPlayNext(tracks)
                navigator.showMessage("已添加至下一首播放")
            }
        })
    }
}

internal val LocalBadgeSpacing = 6.dp
