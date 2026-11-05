package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.SelectionAction
import com.lin0721.linmusic.core.ui.components.TrackSelectionUi
import com.lin0721.linmusic.core.download.ui.DownloadQualityPickerSheet
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.uiTween
import com.lin0721.linmusic.feature.playlist.ui.PlaylistImportTargetSheet
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.LoginBottomSheet
import com.lin0721.linmusic.core.ui.components.WebViewLoginScreen
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.ScreenSlideDurationMs
import org.koin.androidx.compose.koinViewModel

@Composable
fun ArtistScreen(
    artistId: Long,
    viewModel: ArtistViewModel = koinViewModel(),
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val likedSongIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val collectState by viewModel.collectState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val currentTrack by viewModel.playerManager.nowPlaying.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerManager.playWhenReady.collectAsStateWithLifecycle()

    val songSearch by viewModel.songSearch.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    var showLoginSheet by remember { mutableStateOf(false) }
    var showWebViewLogin by remember { mutableStateOf(false) }
    var showBatchTargetSheet by remember { mutableStateOf(false) }
    var showBatchDownloadSheet by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { com.lin0721.linmusic.core.ui.components.ToastManager.showToast(it) }
    }
    LaunchedEffect(artistId) {
        viewModel.loadArtistDataIfNeeded(artistId)
    }

    Box(modifier = Modifier.fillMaxSize().background(MelodiaColors.PageFill)) {
        when (val state = uiState) {
            is ArtistUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is ArtistUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加载失败", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(MelodiaSpacing.sm))
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Spacer(Modifier.height(MelodiaSpacing.md))
                        MelodiaButton(
                            onClick = { viewModel.loadArtistData(artistId) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("重试", color = MaterialTheme.colorScheme.onPrimary)
                        }
                        MelodiaTextButton(onClick = onBack) { Text("返回", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            is ArtistUiState.Success -> {
                val blockedArtistIds by viewModel.blockedArtistIds.collectAsStateWithLifecycle()
                ArtistContent(
                    artist = state.artist,
                    isFollowed = state.isFollowed,
                    fansCount = state.fansCount,
                    topSongs = state.topSongs,
                    albums = state.albums,
                    albumsHasMore = state.albumsHasMore,
                    albumsLoadingMore = state.albumsLoadingMore,
                    similarArtists = state.similarArtists,
                    likedSongIds = likedSongIds,
                    blockedArtistIds = blockedArtistIds,
                    currentTrackId = currentTrack?.mediaId,
                    isPlaying = isPlaying,
                    collectState = collectState,
                    isLoggedIn = userProfile != null,
                    onBack = onBack,
                    onArtistClick = onArtistClick,
                    onPlaylistClick = onPlaylistClick,
                    onAlbumClick = onAlbumClick,
                    onFollowClick = { viewModel.toggleFollow(artistId) },
                    onBlockClick = { viewModel.toggleBlockArtist(artistId) },
                    onPlaySong = { track, queue ->
                        viewModel.playSongInList(track, queue)
                    },
                    onLikeClick = { songId ->
                        viewModel.prepareCollectDialog(songId)
                    },
                    onToggleLike = { songId, like ->
                        viewModel.toggleLikeSong(songId, like)
                    },
                    onAddToPlayNext = { track ->
                        viewModel.addTrackToPlayNext(track)
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
                    onLoadMoreAlbums = { viewModel.loadMoreAlbums() },
                    songSearch = songSearch,
                    onSongSearchQueryChange = viewModel::updateSongSearchQuery,
                    onLoadMoreSongSearch = viewModel::loadMoreSongSearch,
                    selectionUi = TrackSelectionUi(
                        active = selection.active,
                        selectedIds = selection.selectedIds,
                        onEnter = viewModel::enterSelection,
                        onToggle = viewModel::toggleSelected
                    ),
                    selectionActions = listOf(
                        SelectionAction("下一首播放", Icons.AutoMirrored.Filled.QueueMusic) { viewModel.batchPlayNext() },
                        SelectionAction("添加到歌单", Icons.AutoMirrored.Filled.PlaylistAdd) {
                            if (userProfile == null) {
                                showLoginSheet = true
                            } else {
                                viewModel.prepareImportTargets()
                                showBatchTargetSheet = true
                            }
                        },
                        SelectionAction("喜欢", Icons.Default.FavoriteBorder) {
                            if (userProfile == null) showLoginSheet = true else viewModel.batchLike()
                        },
                        SelectionAction("下载", Icons.Default.Download) { showBatchDownloadSheet = true }
                    ),
                    onToggleSelectAll = viewModel::toggleSelectAll,
                    onExitSelection = viewModel::exitSelection
                )
            }
        }

        if (showBatchTargetSheet) {
            PlaylistImportTargetSheet(
                importState = importState,
                onDismiss = { showBatchTargetSheet = false },
                onSelectTarget = { targetId ->
                    viewModel.batchAddToPlaylist(targetId)
                    showBatchTargetSheet = false
                },
                onCreateAndImport = { name ->
                    viewModel.batchCreatePlaylistAndAdd(name)
                    showBatchTargetSheet = false
                }
            )
        }

        if (showBatchDownloadSheet) {
            DownloadQualityPickerSheet(
                onQualitySelected = { level ->
                    viewModel.batchDownload(level)
                    showBatchDownloadSheet = false
                },
                onDismiss = { showBatchDownloadSheet = false },
                headline = "批量下载",
                supportingText = "共 ${selection.count} 首",
                batchSongIds = selection.selectedIds.toList()
            )
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
            enter = slideInVertically(uiTween(ScreenSlideDurationMs)) { it } + fadeIn(uiTween(ScreenSlideDurationMs)),
            exit = slideOutVertically(uiTween(ScreenSlideDurationMs)) { it } + fadeOut(uiTween(ScreenSlideDurationMs))
        ) {
            WebViewLoginScreen(
                onClose = { showWebViewLogin = false },
                onLoginSuccess = { cookies ->
                    showWebViewLogin = false
                    // 登录成功，通知 viewModel 同步用户信息并更新数据
                    viewModel.handleLoginSuccess(cookies)
                }
            )
        }
    }
}
