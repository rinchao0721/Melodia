package com.lin0721.linmusic.desktop.ui.local

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.localmusic.domain.LocalAlbum
import com.lin0721.linmusic.core.localmusic.domain.LocalArtist
import com.lin0721.linmusic.core.localmusic.domain.LocalPlaylist
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack
import com.lin0721.linmusic.core.localmusic.ui.LocalFolderNode
import com.lin0721.linmusic.core.localmusic.ui.LocalTrackSortOrder
import com.lin0721.linmusic.core.localmusic.ui.sortLocalTracks
import com.lin0721.linmusic.core.ui.icons.Album
import com.lin0721.linmusic.core.ui.icons.ArrowBack
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.MoreHoriz
import com.lin0721.linmusic.core.ui.icons.MusicNote
import com.lin0721.linmusic.core.ui.icons.Edit
import com.lin0721.linmusic.core.ui.icons.Play
import com.lin0721.linmusic.core.ui.icons.PlaylistAdd
import com.lin0721.linmusic.core.ui.icons.Sparkle
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.library.MenuOption
import com.lin0721.linmusic.desktop.data.local.LocalLibraryRepository
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

@Composable
fun LocalLibraryDesktopPage(
    viewModel: LocalLibraryDesktopViewModel,
    localLibrary: LocalLibraryRepository,
    modifier: Modifier = Modifier
) {
    val selectedNav by viewModel.selectedNav.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val index by viewModel.index.collectAsState()
    val sortedTracks by viewModel.sortedTracks.collectAsState()
    val downloadTracks by viewModel.downloadTracks.collectAsState()
    val folderTree by viewModel.folderTree.collectAsState()
    val selectedFolderPath by viewModel.selectedFolderPath.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val resumeInfo by viewModel.resumeInfo.collectAsState()

    var matchTargetPath by remember { mutableStateOf<String?>(null) }
    var tagEditTargetPath by remember { mutableStateOf<String?>(null) }

    val activeTracks = when (selectedNav) {
        LocalLibraryNav.SONGS -> sortedTracks
        LocalLibraryNav.DOWNLOADS -> downloadTracks
        LocalLibraryNav.ALBUMS -> selectedAlbum?.let { sortLocalTracks(it.tracks, sortOrder) } ?: sortedTracks
        LocalLibraryNav.ARTISTS -> selectedArtist?.let { sortLocalTracks(it.tracks, sortOrder) } ?: sortedTracks
        LocalLibraryNav.FOLDERS -> findFolderNode(folderTree, selectedFolderPath)?.tracks ?: sortedTracks
    }

    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        // 顶部操作栏：随机播放全部 + 继续播放 + 数量与排序
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DialogButton(
                text = "随机播放全部",
                onClick = { viewModel.shuffleAll(activeTracks) },
                primary = true
            )
            resumeInfo?.let { info ->
                DialogButton(
                    text = "继续播放 · ${info.trackTitle}",
                    onClick = { viewModel.resumeLastQueue() },
                    primary = false
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "共 ${activeTracks.size} 首",
                color = DesktopColors.TextGray,
                fontSize = 12.sp
            )
            SortOrderSelector(
                current = sortOrder,
                onSelect = viewModel::setSortOrder
            )
        }

        Spacer(Modifier.height(14.dp))

        when (selectedNav) {
            LocalLibraryNav.SONGS -> LocalTrackList(
                tracks = sortedTracks,
                playlists = playlists,
                onPlay = { idx -> viewModel.playTracks(sortedTracks, idx) },
                onAddToPlaylist = viewModel::addTrackToPlaylist,
                onMatch = { matchTargetPath = it.path },
                onEditTags = { tagEditTargetPath = it.path },
                emptyText = "暂无本地歌曲，可在左侧新建歌单并添加文件夹"
            )
            LocalLibraryNav.DOWNLOADS -> LocalTrackList(
                tracks = downloadTracks,
                playlists = playlists,
                onPlay = { idx -> viewModel.playTracks(downloadTracks, idx) },
                onAddToPlaylist = viewModel::addTrackToPlaylist,
                onMatch = { matchTargetPath = it.path },
                onEditTags = { tagEditTargetPath = it.path },
                emptyText = "暂无已下载歌曲"
            )
            LocalLibraryNav.ALBUMS -> {
                val album = selectedAlbum
                if (album != null) {
                    DetailHeader(
                        title = "${album.name} · ${album.artist}",
                        onBack = { viewModel.selectAlbum(null) }
                    )
                    val list = sortLocalTracks(album.tracks, sortOrder)
                    LocalTrackList(
                        tracks = list,
                        playlists = playlists,
                        onPlay = { idx -> viewModel.playTracks(list, idx) },
                        onAddToPlaylist = viewModel::addTrackToPlaylist,
                        onMatch = { matchTargetPath = it.path },
                        onEditTags = { tagEditTargetPath = it.path },
                        emptyText = "该专辑暂无歌曲"
                    )
                } else {
                    LocalAlbumGrid(
                        albums = index.knownAlbums,
                        onSelect = viewModel::selectAlbum
                    )
                }
            }
            LocalLibraryNav.ARTISTS -> {
                val artist = selectedArtist
                if (artist != null) {
                    DetailHeader(
                        title = artist.name,
                        onBack = { viewModel.selectArtist(null) }
                    )
                    val list = sortLocalTracks(artist.tracks, sortOrder)
                    LocalTrackList(
                        tracks = list,
                        playlists = playlists,
                        onPlay = { idx -> viewModel.playTracks(list, idx) },
                        onAddToPlaylist = viewModel::addTrackToPlaylist,
                        onMatch = { matchTargetPath = it.path },
                        onEditTags = { tagEditTargetPath = it.path },
                        emptyText = "该歌手暂无歌曲"
                    )
                } else {
                    LocalArtistGrid(
                        artists = index.artists,
                        onSelect = viewModel::selectArtist
                    )
                }
            }
            LocalLibraryNav.FOLDERS -> LocalFolderSplitView(
                root = folderTree,
                selectedPath = selectedFolderPath,
                onSelectFolder = viewModel::selectFolder,
                playlists = playlists,
                onPlay = { list, idx -> viewModel.playTracks(list, idx) },
                onAddToPlaylist = viewModel::addTrackToPlaylist,
                onMatch = { matchTargetPath = it.path },
                onEditTags = { tagEditTargetPath = it.path }
            )
        }
    }

    matchTargetPath?.let { path ->
        val desktopTrack = localLibrary.data.value.tracks.firstOrNull { it.path == path }
        if (desktopTrack != null) {
            LocalMatchDialog(
                track = desktopTrack,
                repository = localLibrary,
                onDismiss = { matchTargetPath = null }
            )
        }
    }

    tagEditTargetPath?.let { path ->
        val desktopTrack = localLibrary.data.value.tracks.firstOrNull { it.path == path }
        if (desktopTrack != null) {
            LocalTagEditorDialog(
                track = desktopTrack,
                repository = localLibrary,
                onDismiss = { tagEditTargetPath = null }
            )
        }
    }
}

@Composable
private fun SortOrderSelector(
    current: LocalTrackSortOrder,
    onSelect: (LocalTrackSortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(DesktopColors.Hover)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(current.label, color = DesktopColors.TextPrimary, fontSize = 12.sp)
        }
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }) {
            LocalTrackSortOrder.entries.forEach { order ->
                MenuOption(order.label, selected = order == current) {
                    expanded = false
                    onSelect(order)
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(MelodiaIcons.ArrowBack, contentDescription = "返回", tint = DesktopColors.TextPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(title, color = DesktopColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LocalTrackList(
    tracks: List<LocalTrack>,
    playlists: List<LocalPlaylist>,
    onPlay: (Int) -> Unit,
    onAddToPlaylist: (Long, LocalTrack) -> Unit,
    onMatch: (LocalTrack) -> Unit,
    onEditTags: (LocalTrack) -> Unit,
    emptyText: String
) {
    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = DesktopColors.TextGray, fontSize = 13.sp)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(tracks, key = { _, t -> t.location }) { index, track ->
            LocalTrackListRow(
                index = index + 1,
                track = track,
                playlists = playlists,
                onPlay = { onPlay(index) },
                onAddToPlaylist = { pid -> onAddToPlaylist(pid, track) },
                onMatch = { onMatch(track) },
                onEditTags = { onEditTags(track) }
            )
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LocalTrackListRow(
    index: Int,
    track: LocalTrack,
    playlists: List<LocalPlaylist>,
    onPlay: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onMatch: () -> Unit,
    onEditTags: () -> Unit
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var playlistMenuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) DesktopColors.Hover else Color.Transparent)
            .hoverable(hover)
            .clickable(onClick = onPlay)
            .pointerInput(track.location) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.button == PointerButton.Secondary) menuOpen = true
                    }
                }
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = index.toString().padStart(2, '0'),
                color = DesktopColors.TextGray,
                fontSize = 12.sp,
                modifier = Modifier.width(32.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = DesktopColors.TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(track.artist, track.album?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    color = DesktopColors.TextGray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatDurationMs(track.durationMs),
                color = DesktopColors.TextGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            HoverReveal(revealed = hovered || menuOpen) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(MelodiaIcons.MoreHoriz, contentDescription = "更多", tint = DesktopColors.TextGray, modifier = Modifier.size(16.dp))
                }
            }
        }

        // 本地模式菜单：不出现红心、在线收藏等在线项，保留加入本地歌单
        DesktopMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
            SimpleMenuItem(text = "播放", icon = MelodiaIcons.Play, onClick = {
                menuOpen = false
                onPlay()
            })
            SimpleMenuItem(text = "加入本地歌单", icon = MelodiaIcons.PlaylistAdd, onClick = {
                menuOpen = false
                playlistMenuOpen = true
            })
            MenuDivider()
            SimpleMenuItem(text = "匹配歌词与封面", icon = MelodiaIcons.Sparkle, onClick = {
                menuOpen = false
                onMatch()
            })
            SimpleMenuItem(text = "编辑标签", icon = MelodiaIcons.Edit, onClick = {
                menuOpen = false
                onEditTags()
            })
        }

        DesktopMenu(expanded = playlistMenuOpen, onDismiss = { playlistMenuOpen = false }) {
            if (playlists.isEmpty()) {
                SimpleMenuItem(text = "暂无本地歌单", enabled = false, onClick = { playlistMenuOpen = false })
            } else {
                playlists.forEach { playlist ->
                    SimpleMenuItem(text = playlist.name, onClick = {
                        playlistMenuOpen = false
                        onAddToPlaylist(playlist.id)
                    })
                }
            }
        }
    }
}

@Composable
private fun LocalAlbumGrid(albums: List<LocalAlbum>, onSelect: (LocalAlbum) -> Unit) {
    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无本地专辑", color = DesktopColors.TextGray, fontSize = 13.sp)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { "${it.name}|${it.artist}" }) { album ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(DesktopColors.Hover.copy(alpha = 0.5f))
                    .clickable { onSelect(album) }
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DesktopColors.Hover),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(MelodiaIcons.Album, contentDescription = null, tint = DesktopColors.TextGray, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(album.name, color = DesktopColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${album.artist} · ${album.tracks.size} 首", color = DesktopColors.TextGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LocalArtistGrid(artists: List<LocalArtist>, onSelect: (LocalArtist) -> Unit) {
    if (artists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无本地歌手", color = DesktopColors.TextGray, fontSize = 13.sp)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(artists, key = { it.name }) { artist ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(DesktopColors.Hover.copy(alpha = 0.5f))
                    .clickable { onSelect(artist) }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(42.dp))
                        .background(DesktopColors.Hover),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(MelodiaIcons.MusicNote, contentDescription = null, tint = DesktopColors.TextGray, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(artist.name, color = DesktopColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${artist.tracks.size} 首", color = DesktopColors.TextGray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun LocalFolderSplitView(
    root: LocalFolderNode,
    selectedPath: String,
    onSelectFolder: (String) -> Unit,
    playlists: List<LocalPlaylist>,
    onPlay: (List<LocalTrack>, Int) -> Unit,
    onAddToPlaylist: (Long, LocalTrack) -> Unit,
    onMatch: (LocalTrack) -> Unit,
    onEditTags: (LocalTrack) -> Unit
) {
    val flatFolders = remember(root) { flattenFolders(root) }
    val currentNode = remember(root, selectedPath) { findFolderNode(root, selectedPath) ?: root }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.width(240.dp).fillMaxHeight().padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(flatFolders, key = { _, pair -> pair.first.path }) { _, (node, depth) ->
                val isSelected = node.path == currentNode.path
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) DesktopColors.Hover else Color.Transparent)
                        .clickable { onSelectFolder(node.path) }
                        .padding(start = (8 + depth * 14).dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        MelodiaIcons.MusicNote,
                        contentDescription = null,
                        tint = if (isSelected) DesktopColors.Accent else DesktopColors.TextGray,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = node.name,
                        color = if (isSelected) DesktopColors.Accent else DesktopColors.TextPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${node.totalTrackCount}", color = DesktopColors.TextGray, fontSize = 11.sp)
                }
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(DesktopColors.Divider.copy(alpha = 0.4f))
        )

        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp)) {
            LocalTrackList(
                tracks = currentNode.tracks,
                playlists = playlists,
                onPlay = { idx -> onPlay(currentNode.tracks, idx) },
                onAddToPlaylist = onAddToPlaylist,
                onMatch = onMatch,
                onEditTags = onEditTags,
                emptyText = "该文件夹下暂无歌曲"
            )
        }
    }
}

private fun flattenFolders(node: LocalFolderNode, depth: Int = 0): List<Pair<LocalFolderNode, Int>> {
    val result = ArrayList<Pair<LocalFolderNode, Int>>()
    result.add(node to depth)
    for (child in node.childFolders) {
        result.addAll(flattenFolders(child, depth + 1))
    }
    return result
}

private fun findFolderNode(node: LocalFolderNode, path: String): LocalFolderNode? {
    if (node.path == path) return node
    for (child in node.childFolders) {
        val found = findFolderNode(child, path)
        if (found != null) return found
    }
    return null
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%02d:%02d".format(min, sec)
}
