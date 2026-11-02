package com.lin0721.linmusic.feature.localmusic.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.ui.components.LoginBottomSheet
import com.lin0721.linmusic.core.ui.components.PlaylistCollectSheet
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.components.WebViewLoginScreen
import com.lin0721.linmusic.core.ui.theme.ScreenSlideDurationMs
import com.lin0721.linmusic.core.ui.theme.uiTween
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.feature.localmusic.ui.components.rememberLocalCoverUrl
import com.lin0721.linmusic.feature.localmusic.ui.playlist.LocalPlaylistPickerSheet
import com.lin0721.linmusic.feature.playlist.ui.OptionRow
import com.lin0721.linmusic.feature.playlist.ui.PlaylistSongOptionsSheet

// 同时负责投递 toast，每个本地页挂一个即可
@Composable
fun LocalTrackActionsHost(
    viewModel: LocalMusicViewModel,
    navigation: LocalMusicNavigation
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val likedSongIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val collectState by viewModel.collectState.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val pickerTracks by viewModel.playlistPickerTracks.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<LocalTrack?>(null) }
    var collectSongId by remember { mutableStateOf<Long?>(null) }
    var showLoginSheet by remember { mutableStateOf(false) }
    var showWebViewLogin by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    LaunchedEffect(showWebViewLogin) {
        navigation.onLoginScreenVisibilityChanged(showWebViewLogin)
    }

    val state = uiState as? LocalMusicUiState.Success ?: return

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
                onArtistClick = navigation.openOnlineArtist,
                onAlbumClick = navigation.openOnlineAlbum,
                onRequireLogin = { showLoginSheet = true },
                extraOptions = {
                    OptionRow(icon = Icons.AutoMirrored.Rounded.PlaylistAdd, text = "加入本地歌单") {
                        viewModel.openPlaylistPicker(listOf(track))
                    }
                    menu.playlistId?.let { playlistId ->
                        OptionRow(icon = Icons.Rounded.PlaylistRemove, text = "从歌单移除") {
                            viewModel.removeFromPlaylist(playlistId, track)
                        }
                    }
                    OptionRow(icon = Icons.Rounded.Edit, text = "编辑标签") {
                        viewModel.closeTrackMenu()
                        navigation.openTagEditor(track.uri.toString())
                    }
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
            val fallbackCoverUrl = rememberLocalCoverUrl(track.uri)
            LocalTrackOptionsSheet(
                track = track,
                coverUrl = menu.coverUrl ?: fallbackCoverUrl,
                onDismiss = { viewModel.closeTrackMenu() },
                onPlayClick = { viewModel.playTracks(menu.queue, start = track) },
                onPlayNextClick = { viewModel.playNext(track) },
                onShareClick = { shareLocalTrackFile(context, track) },
                onEditTagsClick = {
                    viewModel.closeTrackMenu()
                    navigation.openTagEditor(track.uri.toString())
                },
                onDetailClick = { viewModel.openDetail(track) },
                onDeleteClick = { deleteTarget = track },
                onAddToPlaylistClick = { viewModel.openPlaylistPicker(listOf(track)) },
                onRemoveFromPlaylistClick = menu.playlistId?.let { playlistId -> { viewModel.removeFromPlaylist(playlistId, track) } }
            )
        }
    }

    pickerTracks?.let { tracks ->
        LocalPlaylistPickerSheet(
            tracks = tracks,
            playlists = playlists,
            onDismiss = { viewModel.closePlaylistPicker() },
            onCreate = { name -> viewModel.createPlaylist(name, tracks) },
            onToggleSingle = { playlist, track -> viewModel.togglePlaylistMembership(playlist, track) },
            onAddBatch = { playlist -> viewModel.addTracksToPlaylist(playlist, tracks) }
        )
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

    deleteTarget?.let { target ->
        val imported = target.source == LocalTrackSource.IMPORTED
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (imported) "移除歌曲" else "删除文件", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (imported) "确定要从本地音乐移除「${target.title}」吗？不会删除您的原始音频文件。"
                    else "确定要删除「${target.title}」吗？本地文件会被永久删除，不可恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTrack(target)
                    deleteTarget = null
                }) {
                    Text(if (imported) "移除" else "删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
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
                viewModel.handleLoginSuccess(cookies)
            }
        )
    }
}

private fun shareLocalTrackFile(context: Context, track: LocalTrack) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, track.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享「${track.title}」"))
}
