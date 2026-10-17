package com.lin0721.linmusic.feature.localmusic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.feature.settings.ui.SettingsRow
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.launch

/** The same manager is hosted from settings and the two album-detail shortcuts. */
@Composable
fun LocalAlbumAssociationHost(viewModel: LocalAlbumAssociationViewModel) {
    val current by viewModel.state.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val hub by viewModel.hub.collectAsStateWithLifecycle()
    var showSongs by rememberSaveable { mutableStateOf(false) }
    var showAlbums by rememberSaveable { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val albums: BatchAlbumMatchViewModel = koinViewModel()
    val albumReport by albums.state.collectAsStateWithLifecycle()
    LaunchedEffect(showAlbums) { if (showAlbums) albums.ensureLoaded() }
    if (showSongs) {
        BatchLocalMatchManager(onDismiss = { showSongs = false })
    } else if (showAlbums) {
        BatchAlbumMatchDialog(albumReport, onDismiss = { albums.pause(); showAlbums = false },
            onStart = albums::start, onPause = albums::pause,
            onFilter = albums::filter, onQuery = albums::query, onOpenRow = { key ->
                if (!opening) {
                    opening = true
                    scope.launch {
                        try {
                            albums.pauseAndAwait()
                            albumReport.rows.firstOrNull { it.local.key == key }?.let(viewModel::openBatchRow)
                        } finally { opening = false }
                    }
                }
            })
    } else hub?.let { state ->
        LocalMatchingHubDialog(state, onDismiss = viewModel::close,
            onQueryChange = viewModel::hubQuery, onOnlineModeChange = viewModel::hubMode,
            onSearch = viewModel::hubSearch, onChooseLocal = viewModel::hubChooseLocal,
            onChooseOnline = viewModel::hubChooseOnline, onSongs = { showSongs = true },
            onAlbums = { showAlbums = true })
    }
    picker?.let { state ->
        OnlineAlbumLocalPickerDialog(state, onDismiss = viewModel::close,
            onQueryChange = viewModel::pickerQuery, onShowAllChange = viewModel::pickerShowAll,
            onSelect = { viewModel.chooseLocal(it, state.online.id) })
    }
    current?.let { state ->
        LocalAlbumAssociationDialog(state, onDismiss = viewModel::close,
            onQueryChange = viewModel::query, onSearch = viewModel::searchAlbums,
            onSelectAlbum = viewModel::chooseAlbum, onChangeAlbum = viewModel::changeAlbum,
            onRefresh = viewModel::refresh, onApply = viewModel::apply, onStop = viewModel::stop,
            onLink = { local, online -> viewModel.link(local, online, state.selected?.id ?: 0L) },
            onForget = viewModel::forget)
    }
}

@Composable
fun LocalMatchingSettingsEntry(viewModel: LocalAlbumAssociationViewModel = koinViewModel()) {
    SettingsRow(title = "本地与网易云匹配", subtitle = "歌曲批量匹配、双向专辑对应与专辑内宽松匹配",
        onClick = viewModel::openHub)
    LocalAlbumAssociationHost(viewModel)
}
