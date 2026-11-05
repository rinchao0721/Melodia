package com.lin0721.linmusic.feature.localmusic.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.localmusic.domain.AuthorizedFolder
import com.lin0721.linmusic.feature.player.ui.LyricCapsuleSlider
import com.lin0721.linmusic.feature.settings.ui.SettingsGroupCard
import com.lin0721.linmusic.feature.settings.ui.SettingsRow
import com.lin0721.linmusic.feature.settings.ui.SettingsSwitchRow
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

private const val MAX_MIN_DURATION_SEC = 180
private const val MIN_DURATION_STEP_SEC = 10

private val DividerColor: Color @Composable @ReadOnlyComposable get() = MelodiaColors.Divider

@Composable
fun LocalMusicSettingsScreen(
    onBack: () -> Unit,
    viewModel: LocalMusicSettingsViewModel = koinViewModel()
) {
    val minDurationSec by viewModel.minDurationSec.collectAsStateWithLifecycle()
    val hiddenCount by viewModel.hiddenCount.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val authorizedFolders by viewModel.authorizedFolders.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    var removeTarget by remember { mutableStateOf<AuthorizedFolder?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { ToastManager.showToast(it) }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) viewModel.addAuthorizedFolder(treeUri)
    }

    SecondaryScreenScaffold(title = "扫描设置", onBack = onBack) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.lg),
            contentPadding = PaddingValues(
                start = MelodiaSpacing.md,
                end = MelodiaSpacing.md,
                top = MelodiaSpacing.sm,
                bottom = LocalBottomOverlayInset.current + MelodiaSpacing.md
            )
        ) {
            item(key = "filter") {
                SettingsGroupCard("过滤") {
                    Column(modifier = Modifier.padding(vertical = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("忽略短于此时长的音频", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                                Text(
                                    text = if (hiddenCount > 0) "当前已隐藏 $hiddenCount 首" else "过滤铃声、录音等短音频",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = if (minDurationSec == 0) "不过滤" else "$minDurationSec 秒",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LyricCapsuleSlider(
                            value = minDurationSec,
                            onValueChange = { raw ->
                                val stepped = (raw.toFloat() / MIN_DURATION_STEP_SEC).roundToInt() * MIN_DURATION_STEP_SEC
                                if (stepped != minDurationSec) viewModel.setMinDurationSec(stepped)
                            },
                            valueRange = 0f..MAX_MIN_DURATION_SEC.toFloat(),
                            startLabel = "0",
                            endLabel = "3分"
                        )
                    }
                }
            }

            item(key = "folders") {
                SettingsGroupCard("扫描目录") {
                    if (folders.isEmpty()) {
                        Text(
                            text = "还没有扫描到音频文件",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 14.dp)
                        )
                    }
                    folders.forEachIndexed { index, folder ->
                        if (index > 0) HorizontalDivider(color = DividerColor)
                        SettingsSwitchRow(
                            title = folder.name,
                            subtitle = "${folder.trackCount} 首 · ${folder.path}",
                            checked = !folder.excluded,
                            onCheckedChange = { viewModel.setFolderIncluded(folder, it) }
                        )
                    }
                }
            }

            item(key = "authorized") {
                SettingsGroupCard("授权文件夹") {
                    authorizedFolders.forEach { folder ->
                        AuthorizedFolderRow(folder = folder, onRemove = { removeTarget = folder })
                        HorizontalDivider(color = DividerColor)
                    }
                    if (isImporting) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
                            Text("正在导入…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    } else {
                        SettingsRow(
                            title = "添加文件夹",
                            subtitle = "导入其中的歌曲，并读取同名 .lrc 歌词",
                            onClick = { folderPicker.launch(null) }
                        )
                    }
                }
            }
        }
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("移除授权文件夹", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (target.importedCount > 0) "从该文件夹导入的 ${target.importedCount} 首歌曲会一并移出本地音乐，文件本身不会删除。"
                    else "移除后将不再读取该文件夹。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeAuthorizedFolder(target)
                    removeTarget = null
                }) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AuthorizedFolderRow(folder: AuthorizedFolder, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.sm)) {
            Text(
                text = folder.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "已导入 ${folder.importedCount} 首",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        MelodiaIconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "移除 ${folder.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
