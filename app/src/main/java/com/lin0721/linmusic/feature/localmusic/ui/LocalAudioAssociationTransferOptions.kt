package com.lin0721.linmusic.feature.localmusic.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.feature.localmusic.data.PreferredLocalAudioRepository
import com.lin0721.linmusic.feature.localmusic.data.transfer.AssociationImportPreview
import com.lin0721.linmusic.feature.localmusic.data.transfer.AssociationImportRow
import com.lin0721.linmusic.feature.localmusic.data.transfer.AssociationImportStatus
import com.lin0721.linmusic.feature.localmusic.data.transfer.AssociationTransferIssue
import com.lin0721.linmusic.feature.settings.ui.SettingsRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private data class AssociationTransferReport(
    val title: String,
    val summary: String,
    val guidance: String,
    val issues: List<AssociationTransferIssue>
)

/** JSON is a backup of confirmed references, never a new audio permission grant. */
@Composable
fun LocalAudioAssociationTransferOptions() {
    val repository: PreferredLocalAudioRepository = koinInject()
    val scope = rememberCoroutineScope()
    var choosingDocument by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<AssociationImportPreview?>(null) }
    var report by remember { mutableStateOf<AssociationTransferReport?>(null) }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { destination ->
        choosingDocument = false
        if (destination == null || operation != null) return@rememberLauncherForActivityResult
        operation = "正在导出关联…"
        scope.launch {
            try {
                val result = repository.exportAssociations(destination)
                report = AssociationTransferReport(
                    title = if (result.issues.isEmpty()) "关联备份已导出" else "关联备份已写入，部分记录需重新核对",
                    summary = "已写入 ${result.recordCount} 条已确认关联（手动或批量）。备份不含音频文件与临时播放时的自动匹配结果。",
                    guidance = if (result.issues.isEmpty()) {
                        "在其他设备导入前，请先扫描或导入对应的本地音频。"
                    } else {
                        "不可读取或信息不全的记录已保留，恢复时需重新选择。请先扫描或导入对应音频，再重试导入；也可从本地歌曲菜单重新关联网易云歌曲。"
                    },
                    issues = result.issues
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ToastManager.showToast("导出失败，请检查保存位置后重试")
            } finally {
                operation = null
            }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        choosingDocument = false
        if (source == null || operation != null) return@rememberLauncherForActivityResult
        operation = "正在读取关联备份…"
        scope.launch {
            try {
                // Reading this document only previews references. It does not persist audio grants.
                preview = repository.previewAssociationImport(source)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ToastManager.showToast("无法读取关联备份，请确认文件格式后重试")
            } finally {
                operation = null
            }
        }
    }

    fun canChooseDocument() = !choosingDocument && operation == null && preview == null && report == null

    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    SettingsRow(
        title = "导出已确认的本地关联",
        subtitle = "备份已确认关联（手动或批量），不含音频与临时播放匹配结果",
        onClick = {
            if (canChooseDocument()) {
                choosingDocument = true
                try {
                    exportPicker.launch("Melodia-local-associations.json")
                } catch (cancelled: CancellationException) {
                    choosingDocument = false
                    throw cancelled
                } catch (_: Exception) {
                    choosingDocument = false
                    ToastManager.showToast("无法打开保存位置，请稍后重试")
                }
            }
        }
    )
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    SettingsRow(
        title = "导入本地关联",
        subtitle = "先预览并确认；最多 10000 条、20 MB，请先扫描或导入对应音频",
        onClick = {
            if (canChooseDocument()) {
                choosingDocument = true
                try {
                    importPicker.launch(arrayOf("application/json", "text/plain"))
                } catch (cancelled: CancellationException) {
                    choosingDocument = false
                    throw cancelled
                } catch (_: Exception) {
                    choosingDocument = false
                    ToastManager.showToast("无法打开备份文件，请稍后重试")
                }
            }
        }
    )

    preview?.let { currentPreview ->
        val saving = operation != null
        AlertDialog(
            onDismissRequest = { if (operation == null) preview = null },
            title = { Text("导入本地关联预览") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "新增 ${currentPreview.newCount} · 已有相同 ${currentPreview.sameCount}\n" +
                            "冲突保留 ${currentPreview.conflictCount} · 待重选 ${currentPreview.unresolvedCount}"
                    )
                    Text(
                        "确认后仅导入可用的新关联，冲突项保留当前关联。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (currentPreview.unresolvedCount > 0) {
                        Text(
                            "待重选：请先扫描或导入对应音频后重试，也可从本地歌曲菜单关联网易云歌曲。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (currentPreview.rows.isEmpty()) {
                        Text("备份中没有已确认的关联。")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                            items(currentPreview.rows) { row ->
                                AssociationImportPreviewRow(row)
                                HorizontalDivider()
                            }
                        }
                    }
                    if (saving) AssociationTransferProgress("正在保存关联…")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving && currentPreview.newCount > 0,
                    onClick = {
                        if (operation == null && currentPreview.newCount > 0) {
                            operation = "正在保存关联…"
                            scope.launch {
                                try {
                                    val result = repository.applyAssociationImport(currentPreview)
                                    preview = null
                                    report = AssociationTransferReport(
                                        title = "关联导入结果",
                                        summary = "已导入 ${result.importedCount} 条 · 已有相同 ${result.sameCount} 条 · 跳过 ${result.skippedCount} 条。",
                                        guidance = if (result.skippedCount > 0 || result.issues.isNotEmpty()) {
                                            "冲突项保留当前关联。不可用的文件请先扫描或导入后重试，也可从本地歌曲菜单重新关联网易云歌曲。"
                                        } else {
                                            "已确认的本地关联已保存，原音频文件保留。"
                                        },
                                        issues = result.issues
                                    )
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    ToastManager.showToast("导入失败，请重试或重新选择备份")
                                } finally {
                                    operation = null
                                }
                            }
                        }
                    }
                ) {
                    Text(if (saving) "正在保存…" else "确认导入 ${currentPreview.newCount} 条")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (operation == null) preview = null }, enabled = !saving) {
                    Text(if (currentPreview.newCount > 0) "取消" else "关闭")
                }
            }
        )
    }

    if (operation != null && preview == null && report == null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("本地关联备份") },
            text = { AssociationTransferProgress(operation.orEmpty()) },
            confirmButton = {}
        )
    }

    report?.let { currentReport ->
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text(currentReport.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(currentReport.summary)
                    Text(
                        currentReport.guidance,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (currentReport.issues.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                            items(currentReport.issues) { issue ->
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(issue.title.orEmpty().ifBlank { "网易云歌曲 ${issue.songId}" })
                                    Text(
                                        "ID ${issue.songId} · ${issue.message}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { report = null }) { Text("知道了") } }
        )
    }
}

@Composable
private fun AssociationImportPreviewRow(row: AssociationImportRow) {
    val online = row.record.online
    val local = row.record.local
    val statusText = when (row.status) {
        AssociationImportStatus.NEW -> "新增"
        AssociationImportStatus.SAME -> "已有相同"
        AssociationImportStatus.CONFLICT -> "冲突，保留当前关联"
        AssociationImportStatus.UNRESOLVED -> "待重选音频"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(online.title.orEmpty().ifBlank { "网易云歌曲 ${online.songId}" })
        Text(
            "${online.artist.orEmpty().ifBlank { "歌手未知" }} · ID ${online.songId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "本地文件：${local.fileName.orEmpty().ifBlank { "文件名未记录" }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            statusText + row.reason.takeIf(String::isNotBlank)?.let { "：$it" }.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = if (row.status == AssociationImportStatus.UNRESOLVED || row.status == AssociationImportStatus.CONFLICT) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun AssociationTransferProgress(message: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
