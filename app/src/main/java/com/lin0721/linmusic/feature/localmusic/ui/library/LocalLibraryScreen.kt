package com.lin0721.linmusic.feature.localmusic.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.localmusic.data.LocalSearch
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack as SharedLocalTrack
import com.lin0721.linmusic.core.localmusic.domain.LocalTrackSource as SharedLocalTrackSource
import com.lin0721.linmusic.core.localmusic.domain.buildLocalLibraryIndex as buildSharedLocalLibraryIndex
import com.lin0721.linmusic.core.localmusic.ui.LocalLibraryTab
import com.lin0721.linmusic.core.localmusic.ui.ResumePlaybackInfo
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.icons.ArrowBack
import com.lin0721.linmusic.core.ui.icons.Close
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.MoreVert
import com.lin0721.linmusic.core.ui.icons.MusicNote
import com.lin0721.linmusic.core.ui.icons.Play
import com.lin0721.linmusic.core.ui.icons.Shuffle
import com.lin0721.linmusic.core.ui.icons.Sort
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.feature.localmusic.domain.LocalAlbum
import com.lin0721.linmusic.feature.localmusic.domain.LocalArtist
import com.lin0721.linmusic.feature.localmusic.domain.LocalFolderGroup
import com.lin0721.linmusic.feature.localmusic.domain.LocalLibraryIndex
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.ui.LocalLibraryStateGate
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicNavigation
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicSortOrder
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicUiState
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicViewModel
import com.lin0721.linmusic.feature.localmusic.ui.LocalTrackActionsHost
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalCover
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalSectionHeader
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalTrackRow
import com.lin0721.linmusic.feature.localmusic.ui.sortTracks
import org.koin.androidx.compose.koinViewModel

private val ScreenHorizontalPadding = 16.dp

@Composable
fun LocalLibraryScreen(
    navigation: LocalMusicNavigation,
    isRootTab: Boolean = false,
    onOpenSidebar: () -> Unit = {},
    viewModel: LocalMusicViewModel = koinViewModel()
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val currentFolderPath by viewModel.currentFolderPath.collectAsStateWithLifecycle()
    val resumePlayback by viewModel.resumePlayback.collectAsStateWithLifecycle()
    val playingMediaId by viewModel.playingMediaId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val importFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }
    val importFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) viewModel.importFolder(treeUri)
    }

    val content: @Composable (PaddingValues) -> Unit = { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LocalLibraryStateGate(viewModel = viewModel) { successState ->
                LocalLibraryContent(
                    state = successState,
                    library = library,
                    selectedTab = selectedTab,
                    onSelectTab = viewModel::selectTab,
                    currentFolderPath = currentFolderPath,
                    onOpenFolderPath = viewModel::openFolderPath,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    resumePlayback = resumePlayback,
                    onResumePlayback = viewModel::resumeLastPlayback,
                    playingMediaId = playingMediaId,
                    isPlaying = isPlaying,
                    onSortChange = viewModel::setSortOrder,
                    onPlayTracks = viewModel::playTracks,
                    onOpenTrackMenu = { track, queue -> viewModel.openTrackMenu(track, queue) },
                    onOpenAlbum = navigation.openAlbum,
                    onOpenArtist = navigation.openArtist,
                    onShuffleAll = viewModel::shuffleAll
                )
            }
        }
    }

    if (isRootTab) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MelodiaColors.PageFill)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "曲库",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MelodiaColors.TextPrimary,
                    modifier = Modifier.clickable(onClick = onOpenSidebar)
                )
                OverflowActionsButton(
                    expanded = showOverflowMenu,
                    onExpandedChange = { showOverflowMenu = it },
                    onRefresh = { viewModel.checkPermissionAndLoad() },
                    onImportFiles = { importFilesLauncher.launch(arrayOf("audio/*")) },
                    onImportFolder = { importFolderLauncher.launch(null) },
                    onOpenSettings = navigation.openSettings
                )
            }
            content(PaddingValues(0.dp))
        }
    } else {
        SecondaryScreenScaffold(
            title = "曲库",
            onBack = navigation.onBack,
            actions = {
                OverflowActionsButton(
                    expanded = showOverflowMenu,
                    onExpandedChange = { showOverflowMenu = it },
                    onRefresh = { viewModel.checkPermissionAndLoad() },
                    onImportFiles = { importFilesLauncher.launch(arrayOf("audio/*")) },
                    onImportFolder = { importFolderLauncher.launch(null) },
                    onOpenSettings = navigation.openSettings
                )
            }
        ) {
            content(PaddingValues(0.dp))
        }
    }

    LocalTrackActionsHost(viewModel = viewModel, navigation = navigation)
}

@Composable
private fun OverflowActionsButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box {
        MelodiaIconButton(onClick = { onExpandedChange(true) }) {
            Icon(MelodiaIcons.MoreVert, contentDescription = "更多操作", tint = MelodiaColors.TextPrimary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text("扫描本地音乐") },
                onClick = {
                    onExpandedChange(false)
                    onRefresh()
                }
            )
            DropdownMenuItem(
                text = { Text("导入音频文件") },
                onClick = {
                    onExpandedChange(false)
                    onImportFiles()
                }
            )
            DropdownMenuItem(
                text = { Text("导入文件夹") },
                onClick = {
                    onExpandedChange(false)
                    onImportFolder()
                }
            )
            DropdownMenuItem(
                text = { Text("扫描与过滤设置") },
                onClick = {
                    onExpandedChange(false)
                    onOpenSettings()
                }
            )
        }
    }
}

@Composable
private fun LocalLibraryContent(
    state: LocalMusicUiState.Success,
    library: LocalLibraryIndex,
    selectedTab: LocalLibraryTab,
    onSelectTab: (LocalLibraryTab) -> Unit,
    currentFolderPath: String,
    onOpenFolderPath: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    resumePlayback: ResumePlaybackInfo?,
    onResumePlayback: () -> Unit,
    playingMediaId: String?,
    isPlaying: Boolean,
    onSortChange: (LocalMusicSortOrder) -> Unit,
    onPlayTracks: (List<LocalTrack>, LocalTrack?) -> Unit,
    onOpenTrackMenu: (LocalTrack, List<LocalTrack>) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onShuffleAll: () -> Unit
) {
    val bottomInset = LocalBottomOverlayInset.current
    var showSortMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibrarySearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = 6.dp)
            )

            if (searchQuery.isBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = ScreenHorizontalPadding, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LocalLibraryTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        Surface(
                            color = if (selected) MelodiaColors.Accent else MelodiaColors.Surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { onSelectTab(tab) }
                        ) {
                            Text(
                                text = tab.label,
                                color = if (selected) MelodiaColors.OnAccent else MelodiaColors.TextPrimary,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (searchQuery.isNotBlank()) {
                LibrarySearchResultList(
                    query = searchQuery,
                    library = library,
                    playingMediaId = playingMediaId,
                    isPlaying = isPlaying,
                    bottomInset = bottomInset,
                    onPlayTracks = onPlayTracks,
                    onOpenTrackMenu = onOpenTrackMenu,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomInset + 88.dp)
                ) {
                    if (resumePlayback != null) {
                        item(key = "resume_playback") {
                            ResumePlaybackRow(
                                info = resumePlayback,
                                onClick = onResumePlayback
                            )
                        }
                    }

                    when (selectedTab) {
                        LocalLibraryTab.SONGS -> {
                            val sorted = sortTracks(state.tracks, state.sortOrder)
                            item(key = "songs_header") {
                                CountAndSortBar(
                                    countText = "共 ${sorted.size} 首歌曲",
                                    sortOrder = state.sortOrder,
                                    showSortMenu = showSortMenu,
                                    onShowSortMenu = { showSortMenu = it },
                                    onSortChange = onSortChange
                                )
                            }
                            items(sorted, key = { it.uri }) { track ->
                                LocalTrackRow(
                                    track = track,
                                    subtitle = "${track.artist} · ${track.album ?: "未知专辑"}",
                                    playingMediaId = playingMediaId,
                                    isPlaying = isPlaying,
                                    onClick = { onPlayTracks(sorted, track) },
                                    onMoreClick = { onOpenTrackMenu(track, sorted) }
                                )
                            }
                        }

                        LocalLibraryTab.ALBUMS -> {
                            item(key = "albums_header") {
                                CountOnlyBar("共 ${library.albums.size} 张专辑")
                            }
                            items(library.albums, key = { it.key }) { album ->
                                LocalAlbumRow(
                                    album = album,
                                    onClick = { onOpenAlbum(album.key) }
                                )
                            }
                        }

                        LocalLibraryTab.ARTISTS -> {
                            item(key = "artists_header") {
                                CountOnlyBar("共 ${library.artists.size} 位歌手")
                            }
                            items(library.artists, key = { it.name }) { artist ->
                                LocalArtistRow(
                                    artist = artist,
                                    onClick = { onOpenArtist(artist.name) }
                                )
                            }
                        }

                        LocalLibraryTab.FOLDERS -> {
                            val folderGroups = library.folders
                            if (currentFolderPath.isEmpty()) {
                                item(key = "folders_header") {
                                    CountOnlyBar("共 ${folderGroups.size} 个文件夹")
                                }
                                items(folderGroups, key = { it.path }) { folder ->
                                    LocalFolderRow(
                                        folder = folder,
                                        onClick = { onOpenFolderPath(folder.path) }
                                    )
                                }
                            } else {
                                val selectedFolder = library.folder(currentFolderPath)
                                val folderTracks = sortTracks(selectedFolder?.tracks.orEmpty(), state.sortOrder)
                                item(key = "folder_back") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenFolderPath("") }
                                            .padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = MelodiaIcons.ArrowBack,
                                            contentDescription = "返回上级",
                                            tint = MelodiaColors.TextPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = selectedFolder?.name ?: currentFolderPath,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MelodiaColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                items(folderTracks, key = { it.uri }) { track ->
                                    LocalTrackRow(
                                        track = track,
                                        subtitle = "${track.artist} · ${track.album ?: "未知专辑"}",
                                        playingMediaId = playingMediaId,
                                        isPlaying = isPlaying,
                                        onClick = { onPlayTracks(folderTracks, track) },
                                        onMoreClick = { onOpenTrackMenu(track, folderTracks) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.tracks.isNotEmpty() && searchQuery.isBlank()) {
            FloatingActionButton(
                onClick = onShuffleAll,
                containerColor = MelodiaColors.Accent,
                contentColor = MelodiaColors.OnAccent,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = ScreenHorizontalPadding, bottom = bottomInset + 16.dp)
            ) {
                Icon(MelodiaIcons.Shuffle, contentDescription = "随机播放全部")
            }
        }
    }
}

@Composable
private fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(MelodiaColors.Surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = MelodiaIcons.Search,
            contentDescription = null,
            tint = MelodiaColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "搜索本地歌曲、专辑、歌手",
                    color = MelodiaColors.TextSecondary,
                    fontSize = 14.sp
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = MelodiaColors.TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(MelodiaColors.Accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = MelodiaIcons.Close,
                contentDescription = "清空",
                tint = MelodiaColors.TextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onQueryChange("") }
            )
        }
    }
}

@Composable
private fun ResumePlaybackRow(
    info: ResumePlaybackInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHorizontalPadding, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MelodiaColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = MelodiaIcons.Play,
            contentDescription = null,
            tint = MelodiaColors.Accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "继续播放 · ${info.trackTitle}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MelodiaColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${info.artist} · 共 ${info.itemCount} 首",
                style = MaterialTheme.typography.bodySmall,
                color = MelodiaColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CountAndSortBar(
    countText: String,
    sortOrder: LocalMusicSortOrder,
    showSortMenu: Boolean,
    onShowSortMenu: (Boolean) -> Unit,
    onSortChange: (LocalMusicSortOrder) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHorizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = countText,
            style = MaterialTheme.typography.bodySmall,
            color = MelodiaColors.TextSecondary
        )
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onShowSortMenu(true) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MelodiaIcons.Sort,
                    contentDescription = "排序",
                    tint = MelodiaColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = sortOrder.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MelodiaColors.TextSecondary
                )
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { onShowSortMenu(false) }
            ) {
                LocalMusicSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(order.label) },
                        onClick = {
                            onShowSortMenu(false)
                            onSortChange(order)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CountOnlyBar(countText: String) {
    Text(
        text = countText,
        style = MaterialTheme.typography.bodySmall,
        color = MelodiaColors.TextSecondary,
        modifier = Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 6.dp)
    )
}

@Composable
private fun LocalAlbumRow(
    album: LocalAlbum,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ScreenHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LocalCover(
            sourceUri = album.coverSourceUri,
            size = 48.dp,
            shape = RoundedCornerShape(8.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MelodiaColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.artist} · ${album.tracks.size} 首",
                style = MaterialTheme.typography.bodySmall,
                color = MelodiaColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LocalArtistRow(
    artist: LocalArtist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ScreenHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LocalCover(
            sourceUri = artist.tracks.firstOrNull()?.uri,
            size = 44.dp,
            shape = CircleShape
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MelodiaColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${artist.tracks.size} 首歌曲 · ${artist.albumCount} 张专辑",
                style = MaterialTheme.typography.bodySmall,
                color = MelodiaColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LocalFolderRow(
    folder: LocalFolderGroup,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ScreenHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MelodiaColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MelodiaIcons.MusicNote,
                contentDescription = null,
                tint = MelodiaColors.Accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MelodiaColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${folder.tracks.size} 首歌曲",
                style = MaterialTheme.typography.bodySmall,
                color = MelodiaColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibrarySearchResultList(
    query: String,
    library: LocalLibraryIndex,
    playingMediaId: String?,
    isPlaying: Boolean,
    bottomInset: Dp,
    onPlayTracks: (List<LocalTrack>, LocalTrack?) -> Unit,
    onOpenTrackMenu: (LocalTrack, List<LocalTrack>) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit
) {
    val searchResult = remember(query, library) {
        val sharedIndex = buildSharedLocalLibraryIndex(
            library.tracks.map { track ->
                SharedLocalTrack(
                    location = track.uri.toString(),
                    mediaStoreId = track.mediaStoreId,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    sizeBytes = track.sizeBytes,
                    path = track.path,
                    folder = null,
                    dateAddedMs = track.dateAddedMs,
                    dateModifiedMs = track.dateAddedMs,
                    source = SharedLocalTrackSource.EXTERNAL,
                    songId = track.songId
                )
            }
        )
        LocalSearch.search(query, sharedIndex)
    }
    val trackByLocation = remember(library) { library.tracks.associateBy { it.uri.toString() } }
    val matchedSongs = remember(searchResult, trackByLocation) {
        searchResult.songs.mapNotNull { trackByLocation[it.location] }
    }
    val matchedAlbums = remember(searchResult, library) {
        searchResult.albums.mapNotNull { library.album(it.key) }
    }
    val matchedArtists = remember(searchResult, library) {
        searchResult.artists.mapNotNull { library.artist(it.name) }
    }

    if (matchedSongs.isEmpty() && matchedAlbums.isEmpty() && matchedArtists.isEmpty()) {
        EmptyState(
            icon = MelodiaIcons.MusicNote,
            title = "没有找到匹配内容",
            subtitle = "换个关键词试试",
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomInset + 24.dp)
    ) {
        if (matchedSongs.isNotEmpty()) {
            item(key = "search_songs_header") {
                LocalSectionHeader(title = "歌曲 (${matchedSongs.size})")
            }
            items(matchedSongs, key = { "s_${it.uri}" }) { track ->
                LocalTrackRow(
                    track = track,
                    subtitle = "${track.artist} · ${track.album ?: "未知专辑"}",
                    playingMediaId = playingMediaId,
                    isPlaying = isPlaying,
                    onClick = { onPlayTracks(matchedSongs, track) },
                    onMoreClick = { onOpenTrackMenu(track, matchedSongs) }
                )
            }
        }
        if (matchedAlbums.isNotEmpty()) {
            item(key = "search_albums_header") {
                LocalSectionHeader(title = "专辑 (${matchedAlbums.size})")
            }
            items(matchedAlbums, key = { "a_${it.key}" }) { album ->
                LocalAlbumRow(album = album, onClick = { onOpenAlbum(album.key) })
            }
        }
        if (matchedArtists.isNotEmpty()) {
            item(key = "search_artists_header") {
                LocalSectionHeader(title = "歌手 (${matchedArtists.size})")
            }
            items(matchedArtists, key = { "ar_${it.name}" }) { artist ->
                LocalArtistRow(artist = artist, onClick = { onOpenArtist(artist.name) })
            }
        }
    }
}
