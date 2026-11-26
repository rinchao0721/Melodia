package com.lin0721.linmusic.desktop.ui.local

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.lin0721.linmusic.core.ui.icons.MusicNote
import com.lin0721.linmusic.desktop.data.local.LocalLibraryRepository
import com.lin0721.linmusic.desktop.data.local.toDesktopTrack
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.library.MenuOption
import com.lin0721.linmusic.desktop.ui.playlist.TrackColumnHeader
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.track.LocalLikeControl
import com.lin0721.linmusic.desktop.ui.track.TrackRow
import com.lin0721.linmusic.feature.playlist.presentation.PlaylistSortOrder

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
    val likedLocations by viewModel.likedLocations.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val resumeInfo by viewModel.resumeInfo.collectAsState()

    var matchTargetPath by remember { mutableStateOf<String?>(null) }
    var tagEditTargetPath by remember { mutableStateOf<String?>(null) }
    val rowContext = LocalRowContext(
        viewModel = viewModel,
        playlists = playlists,
        likedLocations = likedLocations,
        currentSongId = nowPlaying?.songId,
        onMatch = { matchTargetPath = it.path },
        onEditTags = { tagEditTargetPath = it.path }
    )

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
                context = rowContext,
                emptyText = "暂无本地歌曲，可在左侧新建歌单并添加文件夹"
            )
            LocalLibraryNav.DOWNLOADS -> LocalTrackList(
                tracks = downloadTracks,
                context = rowContext,
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
                        context = rowContext,
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
                        context = rowContext,
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
                context = rowContext
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalTrackList(
    tracks: List<LocalTrack>,
    context: LocalRowContext,
    emptyText: String
) {
    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = DesktopColors.TextGray, fontSize = 13.sp)
        }
        return
    }
    val navigator = LocalDesktopNavigator.current
    val vm = context.viewModel
    var order by remember { mutableStateOf(PlaylistSortOrder()) }
    val pairs = remember(tracks, vm) {
        tracks.map { shared ->
            val desktop = shared.toDesktopTrack()
            LocalTrackRowModel(desktop.toDisplayTrack(vm.coverUriOf(shared)), desktop) to shared
        }
    }
    val desktopToShared = remember(pairs) { pairs.associate { it.first.track.path to it.second } }
    val shown = remember(pairs, order) {
        filterAndSortLocalRows(pairs.map { it.first }, query = "", order = order)
    }
    val orderedShared = remember(shown, desktopToShared) {
        shown.mapNotNull { desktopToShared[it.track.path] }
    }
    val listState = rememberLazyListState()

    HoverScrollbarBox(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            stickyHeader(key = "columns") {
                TrackColumnHeader(order, showAdded = false) { order = order.toggled(it) }
            }
            itemsIndexed(shown, key = { _, row -> row.track.path }) { index, row ->
                val shared = desktopToShared[row.track.path] ?: return@itemsIndexed
                val trackLiked = vm.isLiked(shared, context.likedLocations)
                TrackRow(
                    index = index,
                    track = row.display,
                    localLike = LocalLikeControl(
                        isLiked = trackLiked,
                        onToggle = { vm.toggleLiked(shared) }
                    ),
                    qualitySongId = row.track.songId,
                    localQualityLevel = row.track.resolvedQuality(),
                    isCurrent = context.currentSongId == row.track.queueSongId,
                    onPlay = { vm.playTracks(orderedShared, index) },
                    modifier = if (LocalUiMotion.current.enabled) Modifier.animateItem() else Modifier,
                    menuContent = { dismiss ->
                        LocalTrackMenuItems(
                            track = row.track,
                            isLiked = trackLiked,
                            dismiss = dismiss,
                            onPlay = { vm.playTracks(orderedShared, index) },
                            onPlayNext = {
                                vm.addTracksToPlayNext(listOf(shared))
                                navigator.showMessage("已添加至下一首播放")
                            },
                            onToggleLike = { vm.toggleLiked(shared) },
                            onEditTags = { context.onEditTags(shared) },
                            onMatch = if (vm.isDownloadedFile(shared)) null else ({ context.onMatch(shared) }),
                            playlists = context.playlists,
                            onAddToPlaylist = { pid -> vm.addTrackToPlaylist(pid, shared) }
                        )
                    }
                )
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
    context: LocalRowContext
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
                context = context,
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

// 曲库各列表共用的状态与操作入口
private class LocalRowContext(
    val viewModel: LocalLibraryDesktopViewModel,
    val playlists: List<LocalPlaylist>,
    val likedLocations: Set<String>,
    val currentSongId: Long?,
    val onMatch: (LocalTrack) -> Unit,
    val onEditTags: (LocalTrack) -> Unit
)
