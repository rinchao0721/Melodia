package com.lin0721.linmusic.feature.localmusic.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.feature.localmusic.data.PreferredLocalAudioRepository
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.playlist.ui.OptionRow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel

/** Reused by every online-song menu. The scanned library is the primary entry; files remain a fallback. */
@Composable
fun LocalAudioLinkOptions(songId: Long, onlineTrack: Track? = null) {
    if (songId <= 0L) return
    val repository: PreferredLocalAudioRepository = koinInject()
    val context = LocalContext.current
    val viewModel: OnlineLocalAudioPickerViewModel = koinViewModel()
    val pickerState by viewModel.state.collectAsStateWithLifecycle()
    var documentTarget by remember(songId) { mutableStateOf<Long?>(null) }
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }
    val scope = rememberCoroutineScope()
    val manualUri by remember(repository, songId) { repository.manualUri(songId) }.collectAsStateWithLifecycle(initialValue = null)
    val automaticExcluded by remember(repository, songId) { repository.automaticExcluded(songId) }.collectAsStateWithLifecycle(initialValue = false)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = documentTarget
        documentTarget = null
        if (uri != null && target != null) {
            // Re-selecting an imported track can renew its grant even when its URI is already in Room.
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* Existing MediaStore/folder access is checked when saving. */ }
            viewModel.selectFile(uri, target)
        }
    }
    OptionRow(
        icon = Icons.Rounded.Link,
        text = if (manualUri == null) "关联本地音频（优先播放）" else "更换关联的本地音频",
        onClick = { viewModel.open(onlineTrack?.takeIf { it.id == songId } ?: Track(id = songId)) }
    )
    pickerState?.let { state ->
        OnlineLocalAudioPicker(state = state, onDismiss = viewModel::close,
            onQueryChange = viewModel::updateQuery, onFilterChange = viewModel::updateFilter,
            onSelect = { local -> viewModel.select(local, state.online.id) },
            onChooseFile = {
                if (viewModel.state.value?.savingUri == null) {
                    documentTarget = state.online.id
                    picker.launch(arrayOf("audio/*"))
                }
            })
    }
    if (manualUri != null) OptionRow(
        icon = Icons.Rounded.LinkOff,
        text = "解除本地关联",
        onClick = {
            if (viewModel.state.value?.savingUri != null) return@OptionRow
            scope.launch {
                repository.unlink(songId)
                ToastManager.showToast("已解除关联，并停用本曲自动匹配；原文件保留")
            }
        }
    )
    if (automaticExcluded) OptionRow(
        icon = Icons.Rounded.Restore,
        text = "恢复本曲自动匹配",
        onClick = { scope.launch { repository.allowAutomaticMatch(songId) } }
    )
}
