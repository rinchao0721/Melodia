package com.lin0721.linmusic.feature.playlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.LoginBottomSheet
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.WebViewLoginScreen
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.comment.ui.CommentsBottomSheet
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: Long,
    isAlbum: Boolean = false,
    viewModel: PlaylistViewModel = koinViewModel(),
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit
) {
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTrack by viewModel.playerManager.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsStateWithLifecycle()
    val playMode by viewModel.playerManager.playMode.collectAsStateWithLifecycle()
    val playContext by viewModel.playerManager.playContext.collectAsStateWithLifecycle()
    val likedSongIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val collectState by viewModel.collectState.collectAsStateWithLifecycle()
    val userProfile  by viewModel.userProfile.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val historyRecommendState by viewModel.historyRecommendState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    var showLoginSheet by remember { mutableStateOf(false) }
    var showWebViewLogin by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showMoreMenuSheet by remember { mutableStateOf(false) }
    var showImportTargetSheet by remember { mutableStateOf(false) }
    var showEditInfoDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedHistoryDate by remember { mutableStateOf("今天") }

    LaunchedEffect(historyRecommendState.selectedDate) {
        selectedHistoryDate = historyRecommendState.selectedDate ?: "今天"
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { com.lin0721.linmusic.core.ui.components.ToastManager.showToast(it) }
    }
    LaunchedEffect(playlistId, isAlbum) {
        viewModel.loadPlaylist(playlistId, isAlbum)
        if (playlistId == -1L) {
            viewModel.loadHistoryDates()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = uiState) {
            is PlaylistUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            is PlaylistUiState.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加载失败", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(MelodiaSpacing.sm))
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Spacer(Modifier.height(MelodiaSpacing.md))
                        MelodiaButton(onClick = { viewModel.loadPlaylist(playlistId, isAlbum) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Text("重试", color = MaterialTheme.colorScheme.onPrimary)
                        }
                        MelodiaTextButton(onClick = onBack) { Text("返回", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            is PlaylistUiState.Success -> {
                val profile = userProfile
                val isOwnedPlaylist = profile != null &&
                    state.playlist.id > 0L &&
                    state.playlist.id != profile.uid &&
                    state.playlist.creator?.userId == profile.uid
                val isShuffleActive = playMode == PlayMode.SHUFFLE
                // 当前播放队列的来源是否就是这个歌单（playContext 存的是歌单名）
                val isThisPlaylistContext = playContext == state.playlist.name
                val isCurrentlyPlayingThis = isThisPlaylistContext && isPlaying
                PlaylistContent(
                    playlist       = state.playlist,
                    canRemoveFromPlaylist = isOwnedPlaylist,
                    onRemoveFromPlaylist = { songId ->
                        viewModel.removeTrackFromPlaylist(state.playlist.id, songId)
                    },
                    currentTrackId = currentTrack?.mediaId,
                    isPlaying      = isPlaying,
                    likedSongIds   = likedSongIds,
                    collectState   = collectState,
                    isLoggedIn     = userProfile != null,
                    recommendedSongs = state.recommendedSongs,
                    onBack         = onBack,
                    onArtistClick  = onArtistClick,
                    onAlbumClick   = onAlbumClick,
                    onToggleLike   = viewModel::toggleLikeSong,
                    onPlaySong     = { track ->
                        viewModel.playSongInList(track, state.playlist.tracks)
                    },
                    onAddToPlayNext = { track ->
                        viewModel.addTrackToPlayNext(track)
                    },
                    onPlayAll = {
                        if (isThisPlaylistContext) {
                            // 已经是当前播放队列，播放键只做暂停/继续切换
                            viewModel.playerManager.togglePlayPause()
                        } else {
                            val tracks = state.playlist.tracks
                            if (tracks.isNotEmpty()) {
                                // 随机开关是全局播放模式，起播时按当前开关状态决定顺序播放还是打乱播放
                                val ordered = if (isShuffleActive) tracks.shuffled() else tracks
                                viewModel.playSongInList(ordered.first(), ordered)
                            }
                        }
                    },
                    isShuffleActive = isShuffleActive,
                    isCurrentlyPlayingThis = isCurrentlyPlayingThis,
                    onShuffleToggle = {
                        // 纯开关，不直接触发播放；复用全局播放模式，跟全屏播放器的随机按钮保持一致
                        viewModel.playerManager.toggleShuffle()
                    },
                    onLikeClick = { songId ->
                        viewModel.prepareCollectDialog(songId)
                    },
                    onSaveCollection = { songId, items ->
                        viewModel.savePlaylistCollection(songId, items)
                    },
                    onSaveNewCollection = { name, songId ->
                        viewModel.createPlaylistAndAddSong(name, songId)
                    },
                    onRequireLogin = {
                        showLoginSheet = true
                    },
                    onRefreshRecommendations = {
                        viewModel.refreshRecommendations()
                    },
                    onAddRecommendSong = { track ->
                        viewModel.addRecommendSongToPlaylist(state.playlist.id, track)
                    },
                    isSubscribed = state.isSubscribed,
                    onSubscribeClick = {
                        if (userProfile == null) {
                            showLoginSheet = true
                        } else {
                            viewModel.toggleSubscribePlaylist()
                        }
                    },
                    onCommentsClick = {
                        showCommentsSheet = true
                        viewModel.loadPlaylistComments(playlistId)
                    },
                    onMoreClick = {
                        showMoreMenuSheet = true
                    },
                    onHistoryClick = {},
                    historyDates = historyRecommendState.dates,
                    historySongsLoading = historyRecommendState.songsLoading,
                    showHistoryDatePicker = true,
                    selectedHistoryDate = selectedHistoryDate,
                    onSelectedHistoryDateChange = { selectedHistoryDate = it },
                    onLoadHistoryDetail = { viewModel.loadHistoryDetail(it) },
                    onLoadDailyRecommend = { viewModel.loadPlaylist(-1L) }
                )
            }

        }

        if (showLoginSheet) {
            LoginBottomSheet(
                onDismiss = { showLoginSheet = false },
                onWebLogin = {
                    showLoginSheet = false
                    showWebViewLogin = true
                }
            )
        }

        if (showWebViewLogin) {
                    WebViewLoginScreen(
                        onClose = { showWebViewLogin = false },
                        onLoginSuccess = { cookies ->
                            showWebViewLogin = false
                            viewModel.handleLoginSuccess(cookies)
                        }
                    )
        }

        if (showCommentsSheet) {
            CommentsBottomSheet(
                commentsState = commentsState,
                onLikeComment = viewModel::likeComment,
                onDismiss = { showCommentsSheet = false },
                onRetry = { viewModel.loadPlaylistComments(playlistId) }
            )
        }



        val successState = uiState as? PlaylistUiState.Success
        if (showMoreMenuSheet && successState != null) {
            val playlist = successState.playlist
            ModalBottomSheet(
                onDismissRequest = { showMoreMenuSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = BottomSheetShape,
                dragHandle = { MelodiaDragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = MelodiaSpacing.lg, vertical = MelodiaSpacing.md)
                ) {
                    Text(
                        text = "歌单操作",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = MelodiaSpacing.md)
                    )

                    val firstArtist = playlist.tracks.firstOrNull()?.ar?.firstOrNull()
                    val artistName = firstArtist?.name ?: "未知歌手"
                    val resourceLabel = if (isAlbum) "专辑" else "歌单"

                    val isManageable = !isAlbum && userProfile != null &&
                        playlist.creator?.userId == userProfile?.uid &&
                        playlist.id > 0L &&
                        playlist.id != userProfile?.uid

                    val menuItems = buildList {
                        add(
                            PlaylistMenuItem(
                                icon = if (successState.isSubscribed) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                title = if (successState.isSubscribed) "取消收藏$resourceLabel" else "收藏$resourceLabel"
                            ) {
                                showMoreMenuSheet = false
                                if (userProfile == null) {
                                    showLoginSheet = true
                                } else {
                                    viewModel.toggleSubscribePlaylist()
                                }
                            }
                        )
                        add(
                            PlaylistMenuItem(
                                icon = Icons.Default.Person,
                                title = "跳转至艺人"
                            ) {
                                showMoreMenuSheet = false
                                if (firstArtist != null) {
                                    onArtistClick(firstArtist.id)
                                } else {
                                    com.lin0721.linmusic.core.ui.components.ToastManager.showToast("未找到关联艺人信息")
                                }
                            }
                        )
                        add(
                            PlaylistMenuItem(
                                icon = Icons.AutoMirrored.Filled.QueueMusic,
                                title = "加入播放队列"
                            ) {
                                showMoreMenuSheet = false
                                viewModel.addTracksToPlayNext(playlist.tracks)
                            }
                        )
                        add(
                            PlaylistMenuItem(
                                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                                title = "添加到歌单"
                            ) {
                                showMoreMenuSheet = false
                                if (userProfile == null) {
                                    showLoginSheet = true
                                } else {
                                    viewModel.prepareImportTargets(playlist.id)
                                    showImportTargetSheet = true
                                }
                            }
                        )
                        if (isManageable) {
                            add(
                                PlaylistMenuItem(
                                    icon = Icons.Default.Edit,
                                    title = "编辑歌单信息"
                                ) {
                                    showMoreMenuSheet = false
                                    showEditInfoDialog = true
                                }
                            )
                            add(
                                PlaylistMenuItem(
                                    icon = Icons.Default.Delete,
                                    title = "删除歌单",
                                    isDestructive = true
                                ) {
                                    showMoreMenuSheet = false
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(menuItems, key = { it.title }) { item ->
                            val iconColor = if (item.isDestructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color.White.copy(alpha = 0.8f)
                            }
                            val titleColor = if (item.isDestructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = item.onClick)
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = iconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(MelodiaSpacing.md))
                                Text(
                                    text = item.title,
                                    color = titleColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showImportTargetSheet) {
            PlaylistImportTargetSheet(
                importState = importState,
                onDismiss = { showImportTargetSheet = false },
                onSelectTarget = { targetId ->
                    viewModel.importAllTracksTo(targetId)
                    showImportTargetSheet = false
                },
                onCreateAndImport = { name ->
                    viewModel.createPlaylistAndImportAll(name)
                    showImportTargetSheet = false
                }
            )
        }

        if (showEditInfoDialog && successState != null) {
            val playlist = successState.playlist
            EditPlaylistInfoDialog(
                initialName = playlist.name,
                initialDescription = playlist.description.orEmpty(),
                onDismiss = { showEditInfoDialog = false },
                onConfirm = { newName, newDesc ->
                    viewModel.updatePlaylistInfo(
                        id = playlist.id,
                        originalName = playlist.name,
                        newName = newName,
                        originalDesc = playlist.description,
                        newDesc = newDesc
                    ) { success ->
                        if (success) {
                            showEditInfoDialog = false
                        }
                    }
                }
            )
        }

        if (showDeleteConfirmDialog && successState != null) {
            val playlist = successState.playlist
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = {
                    Text(
                        text = "删除歌单",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "确定要删除歌单「${playlist.name}」吗？删除后将无法恢复。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    MelodiaTextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.deletePlaylist(playlist.id) {
                                onBack()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("删除", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    MelodiaTextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            )
        }

    }
}

private data class PlaylistMenuItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)
