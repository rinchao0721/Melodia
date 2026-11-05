package com.lin0721.linmusic.feature.localmusic.ui.tageditor

import android.Manifest
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.LocalGlobalOverlayOpen
import com.lin0721.linmusic.core.ui.components.EmptyState
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.PlaceholderTextField
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.cloud.domain.formatFileSize
import com.lin0721.linmusic.feature.localmusic.ui.LocalMusicNavigation
import com.lin0721.linmusic.feature.localmusic.ui.components.LocalCover
import com.lin0721.linmusic.feature.player.ui.formatTime
import com.lin0721.linmusic.feature.localmusic.data.tags.LocalTagCoverChange
import org.koin.androidx.compose.koinViewModel

@Composable
fun LocalTagEditorScreen(
    trackUri: String,
    navigation: LocalMusicNavigation,
    viewModel: LocalTagEditorViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(trackUri) {
        viewModel.load(trackUri)
    }

    var showReauthorizeDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val intentSenderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.retrySave()
        } else {
            ToastManager.showToast("未获得修改权限")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.retrySave()
        } else {
            ToastManager.showToast("未获得修改权限")
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.onReauthorized(uri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is LocalTagEditorEvent.Saved -> {
                    ToastManager.showToast("已保存")
                    navigation.onBack()
                }
                is LocalTagEditorEvent.RequestConsent -> {
                    intentSenderLauncher.launch(IntentSenderRequest.Builder(event.intentSender).build())
                }
                is LocalTagEditorEvent.RequestLegacyPermission -> {
                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                is LocalTagEditorEvent.RequestReauthorize -> {
                    showReauthorizeDialog = true
                }
                is LocalTagEditorEvent.Toast -> {
                    ToastManager.showToast(event.message)
                }
            }
        }
    }

    if (showReauthorizeDialog) {
        AlertDialog(
            onDismissRequest = { showReauthorizeDialog = false },
            title = { Text("需要重新授权", fontWeight = FontWeight.Bold) },
            text = { Text("这首歌是导入的，需要重新选择该文件授权写入。") },
            confirmButton = {
                TextButton(onClick = {
                    showReauthorizeDialog = false
                    documentLauncher.launch(arrayOf("audio/*"))
                }) {
                    Text("去授权")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReauthorizeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    val isDirty = (uiState as? LocalTagEditorUiState.Editing)?.isDirty == true
    val onBackClick = { if (isDirty) showDiscardDialog = true else navigation.onBack() }

    // 只在有未保存改动时拦截；全屏播放器等浮层打开时让位给外层返回处理
    BackHandler(enabled = isDirty && !LocalGlobalOverlayOpen.current) { showDiscardDialog = true }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (!result.isSuccessful) {
            result.error?.message?.let { ToastManager.showToast(it) }
            return@rememberLauncherForActivityResult
        }
        val croppedUri = result.uriContent ?: return@rememberLauncherForActivityResult
        val bytes = runCatching { context.contentResolver.openInputStream(croppedUri)?.use { it.readBytes() } }.getOrNull()
        if (bytes != null) viewModel.replaceCover(bytes) else ToastManager.showToast("读取封面失败")
    }

    SecondaryScreenScaffold(
        title = "编辑标签",
        onBack = onBackClick,
        actions = {
            if (uiState is LocalTagEditorUiState.Editing) {
                val state = uiState as LocalTagEditorUiState.Editing
                if (state.isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = MelodiaSpacing.md).size(22.dp)
                    )
                } else {
                    MelodiaTextButton(onClick = { viewModel.save() }) {
                        Text("保存", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is LocalTagEditorUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is LocalTagEditorUiState.Error -> {
                    EmptyState(icon = Icons.Rounded.Info, title = state.message, modifier = Modifier.align(Alignment.Center))
                }
                is LocalTagEditorUiState.Editing -> {
                    if (showDiscardDialog) {
                        AlertDialog(
                            onDismissRequest = { showDiscardDialog = false },
                            title = { Text("放弃修改？", fontWeight = FontWeight.Bold) },
                            text = { Text("确认放弃未保存的更改吗？") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDiscardDialog = false
                                    navigation.onBack()
                                }) {
                                    Text("确认", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDiscardDialog = false }) { Text("取消") }
                            }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MelodiaSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.coverPreview != null) {
                                AsyncImage(
                                    model = state.coverPreview,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                LocalCover(
                                    sourceUri = state.track.uri.takeIf { state.form.cover != LocalTagCoverChange.Remove },
                                    size = 96.dp,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(MelodiaSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.original.fileName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${state.original.formatLabel} · ${formatFileSize(state.track.sizeBytes)} · ${formatTime(state.track.durationMs)}",
                                    color = MelodiaColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row {
                                    MelodiaTextButton(onClick = {
                                        cropLauncher.launch(
                                            CropImageContractOptions(
                                                uri = null,
                                                cropImageOptions = CropImageOptions(
                                                    imageSourceIncludeCamera = false,
                                                    imageSourceIncludeGallery = true,
                                                    cropShape = CropImageView.CropShape.RECTANGLE,
                                                    fixAspectRatio = true,
                                                    aspectRatioX = 1,
                                                    aspectRatioY = 1,
                                                    outputCompressFormat = Bitmap.CompressFormat.JPEG,
                                                    outputCompressQuality = 90,
                                                    outputRequestWidth = 1024,
                                                    outputRequestHeight = 1024,
                                                    outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE,
                                                    activityBackgroundColor = AndroidColor.BLACK,
                                                    toolbarColor = AndroidColor.BLACK,
                                                    toolbarTintColor = AndroidColor.WHITE,
                                                    toolbarTitleColor = AndroidColor.WHITE,
                                                    toolbarBackButtonColor = AndroidColor.WHITE,
                                                    activityMenuIconColor = AndroidColor.WHITE
                                                )
                                            )
                                        )
                                    }) { Text("更换封面") }
                                    Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
                                    MelodiaTextButton(onClick = { viewModel.removeCover() }) { Text("移除封面") }
                                }
                            }
                        }

                        TagField(label = "标题", value = state.form.title, onValueChange = viewModel::updateTitle)
                        TagField(label = "歌手", hint = "多位歌手用 / 分隔", value = state.form.artist, onValueChange = viewModel::updateArtist)
                        TagField(label = "专辑", value = state.form.album, onValueChange = viewModel::updateAlbum)
                        TagField(label = "专辑歌手", value = state.form.albumArtist, onValueChange = viewModel::updateAlbumArtist)
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.weight(1f)) {
                                TagField(label = "年份", value = state.form.year, onValueChange = viewModel::updateYear, keyboardType = KeyboardType.Number)
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                TagField(label = "音轨号", value = state.form.trackNumber, onValueChange = viewModel::updateTrackNumber, keyboardType = KeyboardType.Number)
                            }
                        }
                        TagField(label = "歌词", value = state.form.lyrics, onValueChange = viewModel::updateLyrics, singleLine = false, minLines = 6)
                    }
                }
            }
        }
    }
}

@Composable
private fun TagField(
    label: String,
    hint: String? = null,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = MelodiaSpacing.md, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, color = MelodiaColors.TextSecondary, fontSize = 12.sp)
            if (hint != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = hint, color = MelodiaColors.TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        PlaceholderTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "",
            singleLine = singleLine,
            minLines = minLines,
            containerColor = MaterialTheme.colorScheme.surface,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}
