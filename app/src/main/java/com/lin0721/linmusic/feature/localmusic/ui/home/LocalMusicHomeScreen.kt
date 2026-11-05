package com.lin0721.linmusic.feature.localmusic.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.CreatePlaylistDialog
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.feature.cloud.domain.formatFileSize
import com.lin0721.linmusic.feature.localmusic.domain.LocalLibraryIndex
import com.lin0721.linmusic.feature.localmusic.domain.LocalPlaylist
import com.lin0721.linmusic.feature.localmusic.ui.LocalLibraryStateGate
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicNavigation
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicUiState
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicViewModel
import com.lin0721.linmusic.feature.localmusic.ui.LocalTrackActionsHost
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalAlbumCard
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalCover
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalSectionHeader
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalTrackRow
import com.lin0721.linmusic.feature.localmusic.ui.playlist.LocalPlaylistCover
import com.lin0721.linmusic.feature.localmusic.ui.playlist.NewPlaylistTile
import org.koin.androidx.compose.koinViewModel

private const val RECENT_TRACK_COUNT = 5
private const val SHELF_ITEM_COUNT = 10
private val ArtistAvatarSize = 64.dp
private val AlbumCardWidth = 108.dp

@Composable
fun LocalMusicHomeScreen(
    navigation: LocalMusicNavigation,
    viewModel: LocalMusicViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playingMediaId by viewModel.playingMediaId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val importFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }
    val importFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) viewModel.importFolder(treeUri)
    }

    SecondaryScreenScaffold(
        title = "本地音乐",
        onBack = navigation.onBack,
        actions = {
            if (uiState is LocalMusicUiState.Success && library.tracks.isNotEmpty()) {
                MelodiaIconButton(onClick = {
                    viewModel.openSearch()
                    navigation.openSongs()
                }) {
                    Icon(Icons.Rounded.Search, contentDescription = "搜索", tint = MelodiaColors.TextPrimary)
                }
            }
            Box {
                MelodiaIconButton(onClick = { showOverflowMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = MelodiaColors.TextPrimary)
                }
                DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                    DropdownMenuItem(text = { Text("导入歌曲") }, onClick = {
                        showOverflowMenu = false
                        showImportSheet = true
                    })
                    DropdownMenuItem(text = { Text("重新扫描") }, onClick = {
                        showOverflowMenu = false
                        viewModel.load()
                    })
                    DropdownMenuItem(text = { Text("扫描设置") }, onClick = {
                        showOverflowMenu = false
                        navigation.openSettings()
                    })
                }
            }
        }
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LocalLibraryStateGate(viewModel) { state ->
                if (state.tracks.isEmpty()) {
                    EmptyLibrary(onImport = { showImportSheet = true })
                } else {
                    HomeContent(
                        library = library,
                        playlists = playlists,
                        onCreatePlaylist = { showCreatePlaylist = true },
                        playingMediaId = playingMediaId,
                        isPlaying = isPlaying,
                        viewModel = viewModel,
                        navigation = navigation
                    )
                }
            }
        }
    }

    LocalTrackActionsHost(viewModel = viewModel, navigation = navigation)

    if (showImportSheet) {
        ImportSheet(
            onDismiss = { showImportSheet = false },
            onPickFiles = {
                showImportSheet = false
                importFilesLauncher.launch(arrayOf("audio/*"))
            },
            onPickFolder = {
                showImportSheet = false
                importFolderLauncher.launch(null)
            }
        )
    }

    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylist = false },
            onCreate = { viewModel.createPlaylist(it) },
            title = "新建本地歌单",
            confirmText = "创建"
        )
    }

    if (isImporting) ImportingOverlay()
}

@Composable
private fun HomeContent(
    library: LocalLibraryIndex,
    playlists: List<LocalPlaylist>,
    onCreatePlaylist: () -> Unit,
    playingMediaId: String?,
    isPlaying: Boolean,
    viewModel: LocalMusicViewModel,
    navigation: LocalMusicNavigation
) {
    val recentTracks = remember(library) { library.tracks.sortedByDescending { it.dateAddedMs } }

    LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md)) {
        item(key = "stats") {
            StatsCard(
                library = library,
                onPlay = { viewModel.playTracks(recentTracks) },
                onShuffle = { viewModel.playTracks(recentTracks, shuffle = true) }
            )
        }
        item(key = "entries") {
            QuickEntries(library = library, navigation = navigation)
        }

        item(key = "playlists_header") { LocalSectionHeader("本地歌单", onMoreClick = navigation.openPlaylists) }
        item(key = "playlists_shelf") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = MelodiaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
            ) {
                item(key = "new_playlist") {
                    Column(modifier = Modifier.width(AlbumCardWidth).pressable(MelodiaPress.Card, onClick = onCreatePlaylist)) {
                        NewPlaylistTile(size = AlbumCardWidth)
                        Spacer(Modifier.height(6.dp))
                        Text("新建歌单", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                }
                items(playlists.take(SHELF_ITEM_COUNT), key = { "playlist_${it.id}" }) { playlist ->
                    Column(modifier = Modifier.width(AlbumCardWidth).pressable(MelodiaPress.Card) { navigation.openPlaylist(playlist.id) }) {
                        LocalPlaylistCover(tracks = playlist.tracks, size = AlbumCardWidth)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = playlist.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("${playlist.tracks.size} 首", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        }

        item(key = "recent_header") { LocalSectionHeader("最近添加", onMoreClick = navigation.openSongs) }
        items(recentTracks.take(RECENT_TRACK_COUNT), key = { "recent_${it.uri}" }) { track ->
            LocalTrackRow(
                track = track,
                subtitle = track.artist,
                playingMediaId = playingMediaId,
                isPlaying = isPlaying,
                onClick = { viewModel.playTracks(recentTracks, start = track) },
                onMoreClick = { coverUrl -> viewModel.openTrackMenu(track, recentTracks, coverUrl) }
            )
        }

        if (library.artists.isNotEmpty()) {
            item(key = "artists_header") { LocalSectionHeader("歌手", onMoreClick = navigation.openArtists) }
            item(key = "artists_shelf") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = MelodiaSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.md)
                ) {
                    items(library.artists.take(SHELF_ITEM_COUNT), key = { it.name }) { artist ->
                        Column(
                            modifier = Modifier
                                .width(ArtistAvatarSize)
                                .pressable(MelodiaPress.Card) { navigation.openArtist(artist.name) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LocalCover(
                                sourceUri = artist.tracks.first().uri,
                                size = ArtistAvatarSize,
                                shape = CircleShape,
                                fallbackIcon = Icons.Rounded.Person
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = artist.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        if (library.knownAlbums.isNotEmpty()) {
            item(key = "albums_header") { LocalSectionHeader("专辑", onMoreClick = navigation.openAlbums) }
            item(key = "albums_shelf") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = MelodiaSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
                ) {
                    items(library.recentAlbums.take(SHELF_ITEM_COUNT), key = { it.key }) { album ->
                        LocalAlbumCard(
                            coverSourceUri = album.coverSourceUri,
                            title = album.name,
                            subtitle = album.artist,
                            width = AlbumCardWidth,
                            onClick = { navigation.openAlbum(album.key) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(library: LocalLibraryIndex, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(MelodiaSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${library.tracks.size}", color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(" 首", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text(
                text = "${library.artists.size} 位歌手 · ${library.knownAlbums.size} 张专辑 · ${formatFileSize(library.totalSizeBytes)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        MelodiaIconButton(onClick = onShuffle, containerColor = MelodiaColors.Overlay.copy(alpha = 0.12f), modifier = Modifier.size(44.dp)) {
            Icon(Icons.Rounded.Shuffle, contentDescription = "随机播放", tint = MelodiaColors.TextPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(MelodiaSpacing.sm))
        MelodiaIconButton(onClick = onPlay, containerColor = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "播放全部", tint = MelodiaColors.OnAccent, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun QuickEntries(library: LocalLibraryIndex, navigation: LocalMusicNavigation) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MelodiaSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
    ) {
        QuickEntry(Icons.Rounded.MusicNote, "全部歌曲", "${library.tracks.size}", navigation.openSongs, Modifier.weight(1f))
        QuickEntry(Icons.Rounded.Mic, "歌手", "${library.artists.size}", navigation.openArtists, Modifier.weight(1f))
        QuickEntry(Icons.Rounded.Album, "专辑", "${library.knownAlbums.size}", navigation.openAlbums, Modifier.weight(1f))
        QuickEntry(Icons.Rounded.Folder, "文件夹", "${library.folders.size}", navigation.openFolders, Modifier.weight(1f))
    }
}

@Composable
private fun QuickEntry(icon: ImageVector, label: String, count: String, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .pressable(MelodiaPress.Card, onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
        Text(count, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                icon = Icons.Rounded.LibraryMusic,
                title = "还没有本地音频文件",
                subtitle = "下载的歌曲和手机里已有的音频文件都会出现在这里"
            )
            Spacer(modifier = Modifier.height(MelodiaSpacing.md))
            MelodiaButton(onClick = onImport) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(MelodiaSpacing.xs))
                Text("导入歌曲", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(onDismiss: () -> Unit, onPickFiles: () -> Unit, onPickFolder: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MelodiaColors.Background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.md)
                .padding(bottom = MelodiaSpacing.xl)
        ) {
            Text(
                text = "导入歌曲",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = MelodiaSpacing.sm, vertical = MelodiaSpacing.sm)
            )
            Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
            ImportOption(Icons.Rounded.LibraryMusic, "选择音频文件", "从系统存储中批量多选音频文件导入", onPickFiles)
            Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
            ImportOption(Icons.Rounded.Folder, "选择文件夹", "自动递归扫描并导入文件夹内的全部音频", onPickFolder)
        }
    }
}

@Composable
private fun ImportOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PillRadius))
            .pressable(MelodiaPress.Row, onClick = onClick)
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(MelodiaSpacing.md))
        Column {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ImportingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MelodiaColors.Background)
                .padding(horizontal = 28.dp, vertical = 22.dp)
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
            Spacer(modifier = Modifier.height(MelodiaSpacing.md))
            Text(text = "正在导入并解析音频...", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        }
    }
}
