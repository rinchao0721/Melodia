package com.lin0721.linmusic.feature.cloud.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.platform.repository.UnifiedCloudQuota
import com.lin0721.linmusic.core.platform.repository.UnifiedCloudTrack
import com.lin0721.linmusic.core.platform.toDisplayTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.ErrorState
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.PlaylistCollectSheet
import com.lin0721.linmusic.core.ui.components.SearchResultRowSkeleton
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.interaction.pressScale
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import com.lin0721.linmusic.core.ui.theme.themedShadow
import com.lin0721.linmusic.core.ui.theme.uiTween
import com.lin0721.linmusic.feature.cloud.domain.formatFileSize
import com.lin0721.linmusic.feature.cloud.upload.UploadStatus
import com.lin0721.linmusic.feature.home.ui.BrowsePlatformSwitcherPill
import org.koin.androidx.compose.koinViewModel
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.core.ui.icons.Close
import com.lin0721.linmusic.core.ui.icons.Delete
import com.lin0721.linmusic.core.ui.icons.Upload

private const val SKELETON_ROW_COUNT = 8
// LazyColumn 第 0 项是容量海报头图，歌曲行的 list index 相应整体 +1
private const val HERO_ITEM_OFFSET = 1

@Composable
fun CloudScreen(
    viewModel: CloudViewModel = koinViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isKugouEnabled by viewModel.isKugouEnabled.collectAsStateWithLifecycle()
    val kugouAccount by viewModel.kugouAccount.collectAsStateWithLifecycle()
    val selectedPlatform by viewModel.selectedCloudPlatform.collectAsStateWithLifecycle()
    val kugouUiState by viewModel.kugouUiState.collectAsStateWithLifecycle()
    val currentTrack by viewModel.playerManager.nowPlaying.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsStateWithLifecycle()
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()
    val collectState by viewModel.collectState.collectAsStateWithLifecycle()
    val likedSongIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val uploadTasks by viewModel.uploadTasks.collectAsStateWithLifecycle()
    var showUploadSheet by remember { mutableStateOf(false) }
    var kugouDeletingTarget by remember { mutableStateOf<UnifiedCloudTrack?>(null) }

    val successState = uiState as? CloudUiState.Success
    BackHandler(enabled = successState?.isSearchActive == true) {
        viewModel.toggleSearch()
    }

    val hasActiveUploads = uploadTasks.any { it.status != UploadStatus.SUCCESS && it.status != UploadStatus.FAILED }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.startUpload(uris)
            showUploadSheet = true
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    SecondaryScreenScaffold(
        title = "我的云盘",
        onBack = onBack,
        actions = {
            if (selectedPlatform == MusicPlatform.NETEASE) {
                val success = uiState as? CloudUiState.Success
                if (success != null && (success.songs.isNotEmpty() || success.isSearchActive)) {
                    MelodiaIconButton(onClick = { viewModel.toggleSearch() }) {
                        Crossfade(
                            targetState = success.isSearchActive,
                            animationSpec = uiTween(durationMillis = 200),
                            label = "cloud_search_icon"
                        ) { isSearching ->
                            Icon(
                                if (isSearching) MelodiaIcons.Close else MelodiaIcons.Search,
                                contentDescription = if (isSearching) "关闭搜索" else "搜索",
                                tint = MelodiaColors.TextPrimary
                            )
                        }
                    }
                }
            }
        }
    ) {
        if (isKugouEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrowsePlatformSwitcherPill(
                    browsePlatform = selectedPlatform,
                    onSelect = viewModel::selectCloudPlatform
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selectedPlatform == MusicPlatform.KUGOU && isKugouEnabled) {
                KugouCloudContent(
                    kugouLoggedIn = kugouAccount != null,
                    uiState = kugouUiState,
                    currentTrackId = currentTrack?.songId?.toString(),
                    isPlaying = isPlaying,
                    onRetry = { viewModel.loadKugou(refresh = true) },
                    onLoadMore = { viewModel.loadMoreKugou() },
                    onPlayTrack = { track, all -> viewModel.playKugouTrack(track, all) },
                    onRequestDelete = { kugouDeletingTarget = it }
                )
            } else {
            when (val state = uiState) {
                CloudUiState.Loading -> {
                    Column(modifier = Modifier.padding(top = MelodiaSpacing.sm)) {
                        repeat(SKELETON_ROW_COUNT) { SearchResultRowSkeleton() }
                    }
                }

                is CloudUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorState(message = state.message, onRetry = { viewModel.load() })
                    }
                }

                is CloudUiState.Success -> {
                    val displaySongs = if (state.isSearchActive) state.filteredSongs else state.songs
                    val listState = rememberLazyListState()
                    val shouldLoadMore by remember(state.hasMore, state.isLoadingMore, state.isSearchActive) {
                        derivedStateOf {
                            if (state.isSearchActive) return@derivedStateOf false
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= state.songs.size - 3 + HERO_ITEM_OFFSET && state.hasMore && !state.isLoadingMore
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) viewModel.loadMore()
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(
                            visible = state.isSearchActive,
                            enter = expandVertically(
                                animationSpec = uiTween(durationMillis = 260, easing = FastOutSlowInEasing),
                                expandFrom = Alignment.Top
                            ) + fadeIn(
                                animationSpec = uiTween(durationMillis = 200)
                            ),
                            exit = shrinkVertically(
                                animationSpec = uiTween(durationMillis = 220, easing = FastOutSlowInEasing),
                                shrinkTowards = Alignment.Top
                            ) + fadeOut(
                                animationSpec = uiTween(durationMillis = 150)
                            )
                        ) {
                            CloudSearchField(
                                query = state.searchQuery,
                                isFetchingAll = state.isFetchingAll,
                                onQueryChange = viewModel::updateSearchQuery,
                                onClear = { viewModel.updateSearchQuery("") }
                            )
                        }

                        if (state.isSearchActive && displaySongs.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                EmptyState(
                                    icon = MelodiaIcons.Upload,
                                    title = "没有匹配的歌曲"
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                // 行内组件（CloudSongRow/CloudStorageHeroBlock）自带水平内边距，这里只留垂直间距
                                contentPadding = PaddingValues(
                                    top = if (state.isSearchActive) 0.dp else MelodiaSpacing.sm,
                                    bottom = LocalBottomOverlayInset.current + 16.dp
                                )
                            ) {
                                if (!state.isSearchActive) {
                                    item(key = "hero") {
                                        CloudStorageHeroBlock(
                                            quota = state.quota,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
                                        )
                                    }
                                }

                                items(displaySongs, key = { it.songId }) { song ->
                                    CloudSongRow(
                                        song = song,
                                        onClick = { viewModel.playSong(song) },
                                        onOptionsClick = { viewModel.openOptions(song) }
                                    )
                                }

                                if (state.isLoadingMore && !state.isSearchActive) {
                                    item(key = "loading_more") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(MelodiaSpacing.md),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
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

            if (selectedPlatform == MusicPlatform.NETEASE) {
            // 有上传在跑时点它回看进度，没有时点它选文件
            val fabInteraction = remember { MutableInteractionSource() }
            FloatingActionButton(
                onClick = {
                    if (hasActiveUploads) {
                        showUploadSheet = true
                    } else {
                        filePickerLauncher.launch(arrayOf("audio/*"))
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                interactionSource = fabInteraction,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = MelodiaSpacing.md,
                        bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md
                    )
                    .pressScale(MelodiaPress.Transport, fabInteraction)
                    .size(56.dp)
                    .themedShadow(8.dp, CircleShape)
            ) {
                if (hasActiveUploads) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    Icon(
                        imageVector = MelodiaIcons.Upload,
                        contentDescription = "上传到云盘",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            }
        }
    }

    kugouDeletingTarget?.let { target ->
        val deleting = (kugouUiState as? PlatformCloudUiState.Success)?.isDeleting == true
        CloudDeleteConfirmDialog(
            songName = target.track.name,
            isDeleting = deleting,
            onConfirm = {
                viewModel.deleteKugouTrack(target) {
                    kugouDeletingTarget = null
                }
            },
            onDismiss = { if (!deleting) kugouDeletingTarget = null }
        )
    }

    if (showUploadSheet && uploadTasks.isNotEmpty()) {
        CloudUploadProgressSheet(
            tasks = uploadTasks,
            onRetry = { taskId -> viewModel.retryUpload(taskId) },
            onDismiss = { showUploadSheet = false }
        )
    }

    when (val current = overlay) {
        CloudOverlay.Hidden -> Unit

        is CloudOverlay.Options -> {
            CloudSongOptionsSheet(
                song = current.song,
                isLiked = current.song.playSongId in likedSongIds,
                onDismiss = { viewModel.dismissOverlay() },
                onToggleLikeClick = { viewModel.toggleLike() },
                onAddToPlaylistClick = { viewModel.openAddToPlaylist() },
                onMatchClick = { viewModel.openMatch() },
                onDeleteClick = { viewModel.requestDelete() }
            )
        }

        is CloudOverlay.AddToPlaylist -> {
            PlaylistCollectSheet(
                songId = current.song.playSongId,
                collectState = collectState,
                onDismiss = { viewModel.dismissOverlay() },
                onSaveCollection = { songId, items -> viewModel.saveAddToPlaylist(songId, items, silent = true) },
                onSaveNewCollection = { name, songId -> viewModel.createPlaylistAndAddSong(name, songId) }
            )
        }

        is CloudOverlay.DeleteConfirm -> {
            CloudDeleteConfirmDialog(
                songName = current.song.name,
                isDeleting = current.isDeleting,
                onConfirm = { viewModel.confirmDelete() },
                onDismiss = { viewModel.dismissOverlay() }
            )
        }

        is CloudOverlay.Match -> {
            CloudMatchSheet(
                overlay = current,
                onQueryChange = { viewModel.updateMatchQuery(it) },
                onSelect = { viewModel.selectMatchCandidate(it) },
                onCancelConfirm = { viewModel.cancelMatchConfirm() },
                onConfirm = { viewModel.confirmMatch() },
                onDismiss = { viewModel.dismissOverlay() }
            )
        }
    }
}

@Composable
private fun CloudSearchField(
    query: String,
    isFetchingAll: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
            .clip(RoundedCornerShape(PillRadius))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = MelodiaSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "按歌名/歌手/专辑搜索云盘歌曲",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
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

        AnimatedVisibility(
            visible = isFetchingAll,
            enter = fadeIn(uiTween(150)),
            exit = fadeOut(uiTween(150))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(MelodiaSpacing.xs))
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = !isFetchingAll && query.isNotEmpty(),
            enter = fadeIn(uiTween(150)),
            exit = fadeOut(uiTween(150))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(MelodiaSpacing.xs))
                MelodiaIconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = MelodiaIcons.Close,
                        contentDescription = "清空输入",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KugouCloudContent(
    kugouLoggedIn: Boolean,
    uiState: PlatformCloudUiState,
    currentTrackId: String?,
    isPlaying: Boolean,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPlayTrack: (com.lin0721.linmusic.core.platform.UnifiedTrack, List<com.lin0721.linmusic.core.platform.UnifiedTrack>) -> Unit,
    onRequestDelete: (UnifiedCloudTrack) -> Unit
) {
    if (!kugouLoggedIn) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = MelodiaIcons.Cloud,
                title = "请先登录酷狗账号",
                subtitle = "登录后即可查看与播放酷狗云盘歌曲"
            )
        }
        return
    }
    when (uiState) {
        PlatformCloudUiState.Idle, PlatformCloudUiState.Loading -> {
            Column(modifier = Modifier.padding(top = MelodiaSpacing.sm)) {
                repeat(SKELETON_ROW_COUNT) { SearchResultRowSkeleton() }
            }
        }
        is PlatformCloudUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(message = uiState.message, onRetry = onRetry)
            }
        }
        is PlatformCloudUiState.Success -> {
            val tracks = uiState.tracks
            if (tracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(icon = MelodiaIcons.Cloud, title = "酷狗云盘暂无歌曲")
                }
                return
            }
            val listState = rememberLazyListState()
            val shouldLoadMore by remember(uiState.hasMore, uiState.isLoadingMore, tracks.size) {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    uiState.hasMore && !uiState.isLoadingMore && lastVisible >= tracks.size - 3 + HERO_ITEM_OFFSET
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) onLoadMore()
            }
            val unifiedList = remember(tracks) { tracks.map { it.track } }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = MelodiaSpacing.sm,
                    bottom = LocalBottomOverlayInset.current + 16.dp
                )
            ) {
                item(key = "kugou_hero") {
                    KugouCloudQuotaCard(uiState.quota, tracks.size)
                }
                items(tracks, key = { "${it.deleteKey}_${it.track.id.canonical}" }) { cloudTrack ->
                    val display = remember(cloudTrack) { cloudTrack.track.toDisplayTrack() }
                    SongRow(
                        data = SongRowData(
                            id = display.id,
                            title = display.name,
                            artist = display.ar.joinToString(" / ") { ar -> ar.name },
                            coverUrl = display.al.picUrl,
                            platformTag = MusicPlatform.KUGOU.badgeName,
                            platformTagColorArgb = MusicPlatform.KUGOU.brandColorArgb
                        ),
                        isActive = currentTrackId == display.id.toString(),
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(cloudTrack.track, unifiedList) },
                        trailingSlot = {
                            MelodiaIconButton(
                                onClick = { onRequestDelete(cloudTrack) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = MelodiaIcons.Delete,
                                    contentDescription = "删除云盘歌曲",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }
                if (uiState.isLoadingMore) {
                    item(key = "kugou_loading_more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(MelodiaSpacing.md),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KugouCloudQuotaCard(quota: UnifiedCloudQuota, fallbackCount: Int) {
    val totalCount = if (quota.totalCount > 0) quota.totalCount else fallbackCount
    val usedText = if (quota.maxBytes > 0L) {
        "${formatFileSize(quota.usedBytes)} / ${formatFileSize(quota.maxBytes)}"
    } else {
        "酷狗云端存储"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(MelodiaSpacing.md)
    ) {
        Text(
            text = "酷狗音乐云盘",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "$totalCount 首歌曲 · $usedText",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

