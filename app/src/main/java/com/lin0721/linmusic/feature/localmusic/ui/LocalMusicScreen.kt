package com.lin0721.linmusic.feature.localmusic.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.localmusic.LocalCoverArtCache
import com.lin0721.linmusic.core.localmusic.LocalTrack
import com.lin0721.linmusic.core.localmusic.LocalTrackSource
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.LoginBottomSheet
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.PlaylistCollectSheet
import com.lin0721.linmusic.core.ui.components.SearchResultRowSkeleton
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.WebViewLoginScreen
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.core.ui.theme.ScreenSlideDurationMs
import com.lin0721.linmusic.feature.cloud.domain.formatFileSize
import com.lin0721.linmusic.feature.playlist.ui.OptionRow
import com.lin0721.linmusic.feature.playlist.ui.PlaylistSongOptionsSheet
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val SKELETON_ROW_COUNT = 8

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScreen(
    viewModel: LocalMusicViewModel = koinViewModel(),
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit = {},
    onAlbumClick: (Long) -> Unit = {},
    onLoginScreenVisibilityChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val coverCache: LocalCoverArtCache = koinInject()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val likedSongIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val collectState by viewModel.collectState.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<LocalTrack?>(null) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var collectSongId by remember { mutableStateOf<Long?>(null) }
    var showLoginSheet by remember { mutableStateOf(false) }
    var showWebViewLogin by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.checkPermissionAndLoad() }

    val importFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFiles(uris)
        }
    }

    val importFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.importFolder(treeUri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.checkPermissionAndLoad()
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    LaunchedEffect(showWebViewLogin) {
        onLoginScreenVisibilityChanged(showWebViewLogin)
    }

    val state = uiState

    if (state is LocalMusicUiState.Success && state.selectedGroupKey != null) {
        BackHandler { viewModel.handleBackFromGroupDetail() }
    }

    SecondaryScreenScaffold(
        title = "本地音乐",
        onBack = onBack,
        actions = {
            if (state is LocalMusicUiState.Success && state.isSelectionMode) {
                MelodiaIconButton(onClick = {
                    if (state.selectedUris.isEmpty()) {
                        viewModel.toggleSelectionMode()
                    } else {
                        confirmDeleteSelected = true
                    }
                }) {
                    if (state.selectedUris.isEmpty()) {
                        Icon(Icons.Rounded.Close, contentDescription = "退出多选", tint = Color.White)
                    } else {
                        Icon(Icons.Rounded.Delete, contentDescription = "删除选中", tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                MelodiaIconButton(onClick = { showImportSheet = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "导入歌曲", tint = Color.White)
                }
                if (state is LocalMusicUiState.Success && state.tracks.isNotEmpty()) {
                    MelodiaIconButton(onClick = { viewModel.toggleSearch() }) {
                        Icon(
                            if (state.isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (state.isSearchActive) "关闭搜索" else "搜索",
                            tint = Color.White
                        )
                    }
                    Box {
                        MelodiaIconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = Color.White)
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("导入歌曲") },
                                onClick = {
                                    showOverflowMenu = false
                                    showImportSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("多选") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.toggleSelectionMode()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("重新扫描") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.load()
                                }
                            )
                        }
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                LocalMusicUiState.Loading -> {
                    Column(modifier = Modifier.padding(top = MelodiaSpacing.sm)) {
                        repeat(SKELETON_ROW_COUNT) { SearchResultRowSkeleton() }
                    }
                }

                is LocalMusicUiState.NeedsPermission -> {
                    val alreadyDenied = remember(state.permission) {
                        ContextCompat.checkSelfPermission(context, state.permission) == PackageManager.PERMISSION_DENIED
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(
                                icon = Icons.Rounded.LibraryMusic,
                                title = "需要访问设备存储权限",
                                subtitle = if (alreadyDenied) {
                                    "才能扫描并展示手机里的音频文件，请到系统设置里手动开启"
                                } else {
                                    "用于扫描并展示手机里已有的音频文件"
                                }
                            )
                            MelodiaButton(onClick = { permissionLauncher.launch(state.permission) }) {
                                Text("授权", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }

                is LocalMusicUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorState(message = state.message, onRetry = { viewModel.load() })
                    }
                }

                is LocalMusicUiState.Success -> {
                    if (state.tracks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                EmptyState(
                                    icon = Icons.Rounded.LibraryMusic,
                                    title = "还没有本地音频文件",
                                    subtitle = "下载的歌曲和手机里已有的音频文件都会出现在这里"
                                )
                                Spacer(modifier = Modifier.height(MelodiaSpacing.md))
                                MelodiaButton(onClick = { showImportSheet = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(MelodiaSpacing.xs))
                                    Text("导入歌曲", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (state.isSearchActive) {
                                LocalMusicSearchRow(
                                    query = state.searchQuery,
                                    onQueryChange = viewModel::updateSearchQuery
                                )
                            } else {
                                LocalMusicGroupTabsRow(selected = state.groupMode, onSelect = viewModel::setGroupMode)
                            }

                            val browsingGroups = state.groupMode != LocalMusicGroupMode.SONGS && state.selectedGroupKey == null
                            if (browsingGroups) {
                                LocalMusicStorageRow(
                                    availableBytes = state.availableStorageBytes,
                                    usedBytes = state.totalSizeBytes
                                )
                                if (state.groups.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        EmptyState(icon = Icons.Rounded.LibraryMusic, title = "没有匹配的结果")
                                    }
                                } else {
                                    LazyColumn(
                                        contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
                                    ) {
                                        items(state.groups, key = { it.key }) { group ->
                                            LocalMusicGroupRow(
                                                name = group.key,
                                                trackCount = group.tracks.size,
                                                onClick = { viewModel.selectGroup(group.key) }
                                            )
                                        }
                                    }
                                }
                            } else {
                                val list = state.currentTrackList.orEmpty()
                                if (list.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        EmptyState(icon = Icons.Rounded.LibraryMusic, title = "没有匹配的结果")
                                    }
                                } else {
                                    LazyColumn(
                                        contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
                                    ) {
                                        item(key = "header") {
                                            Column {
                                                if (state.groupMode != LocalMusicGroupMode.SONGS) {
                                                    BackToGroupsRow(
                                                        groupMode = state.groupMode,
                                                        groupName = state.selectedGroupKey.orEmpty(),
                                                        onClick = { viewModel.handleBackFromGroupDetail() }
                                                    )
                                                }
                                                LocalMusicStorageRow(
                                                    availableBytes = state.availableStorageBytes,
                                                    usedBytes = state.totalSizeBytes
                                                )
                                                LocalMusicPlayAllRow(
                                                    count = list.size,
                                                    sortOrder = state.sortOrder,
                                                    showSortMenu = showSortMenu,
                                                    onPlayAll = { viewModel.playAll() },
                                                    onSortClick = { showSortMenu = true },
                                                    onDismissSortMenu = { showSortMenu = false },
                                                    onSortSelected = {
                                                        showSortMenu = false
                                                        viewModel.setSortOrder(it)
                                                    }
                                                )
                                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                            }
                                        }
                                        items(list, key = { it.uri.toString() }) { track ->
                                            val selected = track.uri.toString() in state.selectedUris
                                            // 异步解析内嵌封面
                                            val coverUrl by produceState<String?>(initialValue = null, track.uri) {
                                                value = coverCache.coverUriFor(track.uri)?.toString()
                                            }
                                            SongRow(
                                                data = SongRowData(
                                                    id = track.songId ?: track.mediaStoreId,
                                                    title = track.title,
                                                    artist = track.artist,
                                                    coverUrl = coverUrl,
                                                    durationText = formatFileSize(track.sizeBytes)
                                                ),
                                                showDownloadBadge = false,
                                                onClick = {
                                                    if (state.isSelectionMode) viewModel.toggleSelected(track)
                                                    else viewModel.playTrack(track)
                                                },
                                                trailingSlot = {
                                                    if (state.isSelectionMode) {
                                                        Icon(
                                                            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                                            contentDescription = null,
                                                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier
                                                                .padding(start = MelodiaSpacing.sm)
                                                                .size(20.dp)
                                                        )
                                                    } else {
                                                        MelodiaIconButton(onClick = { viewModel.openTrackMenu(track, coverUrl) }) {
                                                            Icon(
                                                                Icons.Rounded.MoreVert,
                                                                contentDescription = "更多",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state is LocalMusicUiState.Success) {
        when (val menu = state.menuState) {
            null -> Unit

            is LocalMusicMenuState.Matched -> {
                val track = menu.track
                val fullTrack = menu.fullTrack
                PlaylistSongOptionsSheet(
                    track = fullTrack,
                    isLiked = fullTrack.id in likedSongIds,
                    isLoggedIn = userProfile != null,
                    onDismiss = { viewModel.closeTrackMenu() },
                    onAddToPlayNext = { viewModel.addTrackToPlayNext(it) },
                    onToggleLike = { songId, like -> viewModel.toggleLikeSong(songId, like) },
                    onCollectClick = { songId ->
                        collectSongId = songId
                        viewModel.prepareCollectDialog(songId)
                    },
                    onArtistClick = onArtistClick,
                    onAlbumClick = onAlbumClick,
                    onRequireLogin = { showLoginSheet = true },
                    extraOptions = {
                        OptionRow(icon = Icons.Rounded.Info, text = "查看详情") {
                            viewModel.openDetail(track)
                        }
                        OptionRow(
                            icon = Icons.Rounded.Delete,
                            text = "删除本地文件",
                            iconTint = MaterialTheme.colorScheme.error
                        ) {
                            viewModel.closeTrackMenu()
                            deleteTarget = track
                        }
                    }
                )
            }

            is LocalMusicMenuState.Unmatched -> {
                val track = menu.track
                val fallbackCoverUrl by produceState<String?>(initialValue = null, track.uri) {
                    value = coverCache.coverUriFor(track.uri)?.toString()
                }
                val coverUrl = menu.coverUrl ?: fallbackCoverUrl
                LocalTrackOptionsSheet(
                    track = track,
                    coverUrl = coverUrl,
                    onDismiss = { viewModel.closeTrackMenu() },
                    onPlayClick = { viewModel.playTrack(track) },
                    onPlayNextClick = { viewModel.playNext(track) },
                    onShareClick = { shareLocalTrackFile(context, track) },
                    onDetailClick = { viewModel.openDetail(track) },
                    onDeleteClick = { deleteTarget = track }
                )
            }
        }

        state.detailTrack?.let { track ->
            LocalTrackDetailDialog(track = track, onDismiss = { viewModel.closeDetail() })
        }

        collectSongId?.let { songId ->
            PlaylistCollectSheet(
                songId = songId,
                collectState = collectState,
                onDismiss = { collectSongId = null },
                onSaveCollection = { id, items -> viewModel.savePlaylistCollection(id, items) },
                onSaveNewCollection = { name, id -> viewModel.createPlaylistAndAddSong(name, id) }
            )
        }
    }

    if (showLoginSheet) {
        LoginBottomSheet(
            onDismiss = { showLoginSheet = false },
            onWebLogin = {
                showLoginSheet = false
                showWebViewLogin = true
            },
            onLoginSuccess = { cookies ->
                showLoginSheet = false
                viewModel.handleLoginSuccess(cookies)
            }
        )
    }

    AnimatedVisibility(
        visible = showWebViewLogin,
        enter = slideInVertically(tween(ScreenSlideDurationMs)) { it } + fadeIn(tween(ScreenSlideDurationMs)),
        exit = slideOutVertically(tween(ScreenSlideDurationMs)) { it } + fadeOut(tween(ScreenSlideDurationMs))
    ) {
        WebViewLoginScreen(
            onClose = { showWebViewLogin = false },
            onLoginSuccess = { cookies ->
                showWebViewLogin = false
                viewModel.handleLoginSuccess(cookies)
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (target.source == LocalTrackSource.IMPORTED) "移除歌曲" else "删除文件", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (target.source == LocalTrackSource.IMPORTED) {
                        "确定要从本地音乐移除「${target.title}」吗？不会删除您的原始音频文件。"
                    } else {
                        "确定要删除「${target.title}」吗？本地文件会被永久删除，不可恢复。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTrack(target)
                    deleteTarget = null
                }) {
                    Text(
                        if (target.source == LocalTrackSource.IMPORTED) "移除" else "删除",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    if (confirmDeleteSelected) {
        val count = (uiState as? LocalMusicUiState.Success)?.selectedUris?.size ?: 0
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text("批量处理", fontWeight = FontWeight.Bold) },
            text = { Text("确定要处理选中的 $count 首歌曲吗？普通本地文件会被永久删除，导入的歌曲将被移出列表。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    confirmDeleteSelected = false
                }) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelected = false }) { Text("取消") }
            }
        )
    }

    if (showImportSheet) {
        val importSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showImportSheet = false },
            sheetState = importSheetState,
            containerColor = BackgroundDark,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PillRadius))
                        .pressable(MelodiaPress.Row) {
                            showImportSheet = false
                            importFilesLauncher.launch(arrayOf("audio/*"))
                        }
                        .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(MelodiaSpacing.md))
                    Column {
                        Text(
                            text = "选择音频文件",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "从系统存储中批量多选音频文件导入",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PillRadius))
                        .pressable(MelodiaPress.Row) {
                            showImportSheet = false
                            importFolderLauncher.launch(null)
                        }
                        .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(MelodiaSpacing.md))
                    Column {
                        Text(
                            text = "选择文件夹",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "自动递归扫描并导入文件夹内的全部音频",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (isImporting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(BackgroundDark)
                    .padding(horizontal = 28.dp, vertical = 22.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(MelodiaSpacing.md))
                Text(
                    text = "正在导入并解析音频...",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun LocalMusicGroupTabsRow(selected: LocalMusicGroupMode, onSelect: (LocalMusicGroupMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
    ) {
        LocalMusicGroupMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .pressable(MelodiaPress.Pill) { onSelect(mode) }
                    .clip(RoundedCornerShape(PillRadius))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = MelodiaSpacing.md, vertical = 7.dp)
            ) {
                Text(
                    text = mode.label,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LocalMusicSearchRow(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(PillRadius))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = MelodiaSpacing.md, vertical = 10.dp)
        ) {
            if (query.isEmpty()) {
                Text("按歌名/歌手搜索本地文件", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LocalMusicStorageRow(availableBytes: Long, usedBytes: Long) {
    Text(
        text = "可用空间 ${formatFileSize(availableBytes)} · 本页占用 ${formatFileSize(usedBytes)}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.5.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.xs)
    )
}

@Composable
private fun LocalMusicPlayAllRow(
    count: Int,
    sortOrder: LocalMusicSortOrder,
    showSortMenu: Boolean,
    onPlayAll: () -> Unit,
    onSortClick: () -> Unit,
    onDismissSortMenu: () -> Unit,
    onSortSelected: (LocalMusicSortOrder) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .pressable(MelodiaPress.Row) { onPlayAll() }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(MelodiaSpacing.sm))
            Text("播放全部", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                " ($count)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 2.dp)
            )
        }

        Box {
            Row(
                modifier = Modifier.pressable(MelodiaPress.Pill) { onSortClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(sortOrder.label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.SwapVert,
                    contentDescription = "排序",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = onDismissSortMenu) {
                LocalMusicSortOrder.entries.forEach { order ->
                    DropdownMenuItem(text = { Text(order.label) }, onClick = { onSortSelected(order) })
                }
            }
        }
    }
}

@Composable
private fun BackToGroupsRow(groupMode: LocalMusicGroupMode, groupName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回${groupMode.label}列表",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(MelodiaSpacing.sm))
        Text(
            text = groupName,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LocalMusicGroupRow(name: String, trackCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MelodiaSpacing.md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(MelodiaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$trackCount 首",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// 分享本地音频文件
private fun shareLocalTrackFile(context: android.content.Context, track: LocalTrack) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, track.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享「${track.title}」"))
}
