package com.lin0721.linmusic.feature.localmusic.ui.collection

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.localmusic.domain.LocalAlbum
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.domain.albumKey
import com.lin0721.linmusic.feature.localmusic.domain.albumName
import com.lin0721.linmusic.feature.localmusic.domain.displayTrackNumber
import com.lin0721.linmusic.feature.localmusic.ui.LocalLibraryStateGate
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicNavigation
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicUiState
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicViewModel
import com.lin0721.linmusic.feature.localmusic.ui.LocalTrackActionsHost
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalAlbumCard
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalCover
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalBatchDeleteDialog
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalHeroHeader
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalSelectionActions
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalPlayShuffleButtons
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalSectionHeader
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalTrackRow
import org.koin.androidx.compose.koinViewModel

private val ArtistAvatarSize = 96.dp
private val AlbumCoverSize = 132.dp
private val ArtistAlbumCardWidth = 120.dp

@Composable
fun LocalArtistScreen(
    name: String,
    navigation: LocalMusicNavigation,
    viewModel: LocalMusicViewModel = koinViewModel()
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playingMediaId by viewModel.playingMediaId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val success = uiState as? LocalMusicUiState.Success
    var confirmDelete by remember { mutableStateOf(false) }
    LocalSelectionLifecycle(viewModel, selecting = success?.isSelectionMode == true)
    val artist = library.artist(name)
    val albums = remember(artist, library) {
        artist?.tracks?.mapNotNull { library.album(it.albumKey) }?.filterNot { it.isUnknown }?.distinctBy { it.key }.orEmpty()
    }

    Box(modifier = Modifier.fillMaxSize().background(MelodiaColors.PageFill)) {
        LocalLibraryStateGate(viewModel) { state ->
            if (artist == null) {
                MissingContent(title = "该歌手已没有本地歌曲", onBack = navigation.onBack)
                return@LocalLibraryStateGate
            }
            LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md)) {
                item(key = "hero") {
                    LocalHeroHeader(
                        coverSourceUri = artist.tracks.first().uri,
                        onBack = navigation.onBack,
                        actions = { LocalSelectionActions(state, artist.tracks, viewModel) { confirmDelete = true } }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LocalCover(
                                sourceUri = artist.tracks.first().uri,
                                size = ArtistAvatarSize,
                                shape = CircleShape,
                                fallbackIcon = Icons.Rounded.Person
                            )
                            Spacer(Modifier.width(MelodiaSpacing.md))
                            Column {
                                Text(
                                    text = artist.name,
                                    color = MelodiaColors.TextPrimary,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${artist.tracks.size} 首 · ${artist.albumCount} 张专辑",
                                    color = MelodiaColors.TextPrimary.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(MelodiaSpacing.md))
                        LocalPlayShuffleButtons(
                            onPlay = { viewModel.playTracks(artist.tracks) },
                            onShuffle = { viewModel.playTracks(artist.tracks, shuffle = true) }
                        )
                    }
                }
                if (albums.isNotEmpty()) {
                    item(key = "albums_header") { LocalSectionHeader("专辑") }
                    item(key = "albums") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = MelodiaSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
                        ) {
                            items(albums, key = { it.key }) { album ->
                                LocalAlbumCard(
                                    coverSourceUri = album.coverSourceUri,
                                    title = album.name,
                                    subtitle = albumMeta(album),
                                    width = ArtistAlbumCardWidth,
                                    onClick = { navigation.openAlbum(album.key) }
                                )
                            }
                        }
                    }
                }
                item(key = "songs_header") { LocalSectionHeader("歌曲") }
                trackItems(
                    tracks = artist.tracks,
                    subtitleOf = { it.albumName },
                    playingMediaId = playingMediaId,
                    isPlaying = isPlaying,
                    viewModel = viewModel,
                    state = state
                )
            }
        }
    }

    LocalTrackActionsHost(viewModel = viewModel, navigation = navigation)

    if (confirmDelete) {
        LocalBatchDeleteDialog(
            count = success?.selectedUris?.size ?: 0,
            onConfirm = {
                viewModel.deleteSelected()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
fun LocalAlbumScreen(
    albumKey: String,
    navigation: LocalMusicNavigation,
    viewModel: LocalMusicViewModel = koinViewModel()
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playingMediaId by viewModel.playingMediaId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val success = uiState as? LocalMusicUiState.Success
    var confirmDelete by remember { mutableStateOf(false) }
    LocalSelectionLifecycle(viewModel, selecting = success?.isSelectionMode == true)
    val album = library.album(albumKey)

    Box(modifier = Modifier.fillMaxSize().background(MelodiaColors.PageFill)) {
        LocalLibraryStateGate(viewModel) { state ->
            if (album == null) {
                MissingContent(title = "该专辑已没有本地歌曲", onBack = navigation.onBack)
                return@LocalLibraryStateGate
            }
            LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md)) {
                item(key = "hero") {
                    LocalHeroHeader(
                        coverSourceUri = album.coverSourceUri,
                        onBack = navigation.onBack,
                        actions = { LocalSelectionActions(state, album.tracks, viewModel) { confirmDelete = true } }
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            LocalCover(
                                sourceUri = album.coverSourceUri,
                                size = AlbumCoverSize,
                                shape = RoundedCornerShape(8.dp),
                                fallbackIcon = Icons.Rounded.Album
                            )
                            Spacer(Modifier.width(MelodiaSpacing.md))
                            Column {
                                Text(
                                    text = album.name,
                                    color = MelodiaColors.TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${album.artist} ›",
                                    color = MelodiaColors.TextPrimary.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(top = MelodiaSpacing.xs)
                                        .pressable(MelodiaPress.Pill, enabled = library.artist(album.artist) != null) {
                                            navigation.openArtist(album.artist)
                                        }
                                )
                                Text(
                                    text = albumMeta(album, withDuration = true),
                                    color = MelodiaColors.TextPrimary.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(MelodiaSpacing.md))
                        LocalPlayShuffleButtons(
                            onPlay = { viewModel.playTracks(album.tracks) },
                            onShuffle = { viewModel.playTracks(album.tracks, shuffle = true) }
                        )
                    }
                }
                trackItems(
                    tracks = album.tracks,
                    subtitleOf = { it.artist },
                    playingMediaId = playingMediaId,
                    isPlaying = isPlaying,
                    viewModel = viewModel,
                    state = state,
                    showTrackNumber = true
                )
            }
        }
    }

    LocalTrackActionsHost(viewModel = viewModel, navigation = navigation)

    if (confirmDelete) {
        LocalBatchDeleteDialog(
            count = success?.selectedUris?.size ?: 0,
            onConfirm = {
                viewModel.deleteSelected()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
fun LocalFolderScreen(
    path: String,
    navigation: LocalMusicNavigation,
    viewModel: LocalMusicViewModel = koinViewModel()
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playingMediaId by viewModel.playingMediaId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val success = uiState as? LocalMusicUiState.Success
    var confirmDelete by remember { mutableStateOf(false) }
    LocalSelectionLifecycle(viewModel, selecting = success?.isSelectionMode == true)
    val folder = library.folder(path)

    SecondaryScreenScaffold(
        title = folder?.name ?: path.substringAfterLast('/'),
        onBack = navigation.onBack,
        actions = {
            if (success != null && folder != null) {
                LocalSelectionActions(success, folder.tracks, viewModel) { confirmDelete = true }
            }
        }
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LocalLibraryStateGate(viewModel) { state ->
                if (folder == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(icon = Icons.Rounded.LibraryMusic, title = "该文件夹已没有本地歌曲")
                    }
                    return@LocalLibraryStateGate
                }
                LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md)) {
                    item(key = "header") {
                        Column(modifier = Modifier.padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)) {
                            Text(
                                text = "${folder.tracks.size} 首 · ${folder.path}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(MelodiaSpacing.sm))
                            LocalPlayShuffleButtons(
                                onPlay = { viewModel.playTracks(folder.tracks) },
                                onShuffle = { viewModel.playTracks(folder.tracks, shuffle = true) }
                            )
                        }
                    }
                    trackItems(
                        tracks = folder.tracks,
                        subtitleOf = { it.artist },
                        playingMediaId = playingMediaId,
                        isPlaying = isPlaying,
                        viewModel = viewModel,
                        state = state
                    )
                }
            }
        }
    }

    LocalTrackActionsHost(viewModel = viewModel, navigation = navigation)

    if (confirmDelete) {
        LocalBatchDeleteDialog(
            count = success?.selectedUris?.size ?: 0,
            onConfirm = {
                viewModel.deleteSelected()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

private fun LazyListScope.trackItems(
    tracks: List<LocalTrack>,
    subtitleOf: (LocalTrack) -> String,
    playingMediaId: String?,
    isPlaying: Boolean,
    viewModel: LocalMusicViewModel,
    state: LocalMusicUiState.Success,
    showTrackNumber: Boolean = false
) {
    items(tracks, key = { "track_${it.uri}" }) { track ->
        LocalTrackRow(
            track = track,
            subtitle = subtitleOf(track),
            playingMediaId = playingMediaId,
            isPlaying = isPlaying,
            index = if (showTrackNumber) track.displayTrackNumber else null,
            selectionMode = state.isSelectionMode,
            selected = track.uri.toString() in state.selectedUris,
            onClick = {
                if (state.isSelectionMode) viewModel.toggleSelected(track)
                else viewModel.playTracks(tracks, start = track)
            },
            onMoreClick = { coverUrl -> viewModel.openTrackMenu(track, tracks, coverUrl) }
        )
    }
}

// 多选状态在 ViewModel 里跨页共享：返回键先退出多选，离开页面时也要复位，避免带到上一页
@Composable
private fun LocalSelectionLifecycle(viewModel: LocalMusicViewModel, selecting: Boolean) {
    BackHandler(enabled = selecting) { viewModel.exitSelectionMode() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.exitSelectionMode() }
    }
}

@Composable
private fun MissingContent(title: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        MelodiaIconButton(onClick = onBack, modifier = Modifier.padding(MelodiaSpacing.xs)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MelodiaColors.TextPrimary)
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(icon = Icons.Rounded.LibraryMusic, title = title)
        }
    }
}

private fun albumMeta(album: LocalAlbum, withDuration: Boolean = false): String = listOfNotNull(
    album.year?.toString(),
    "${album.tracks.size} 首",
    album.totalDurationMs.takeIf { withDuration && it > 0 }?.let { "${(it / 60_000).coerceAtLeast(1)} 分钟" }
).joinToString(" · ")
