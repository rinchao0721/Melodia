package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.source.ArchiveScript
import com.lin0721.linmusic.core.source.LxArchiveReader
import com.lin0721.linmusic.core.source.LxPluginItem
import com.lin0721.linmusic.core.source.LxPluginManager
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.desktop.platform.chooseFile
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.settings.ui.LxPluginSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CollapsedPluginCount = 3

// 设置页「音源与换源」分区内的社区源（LX）插件管理：启用开关、插件列表与导入入口
@Composable
internal fun ColumnScope.LxPluginSection(viewModel: LxPluginSettingsViewModel) {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    val enabled by viewModel.pluginEnabled.collectAsState()
    val plugins by viewModel.plugins.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val duplicates by viewModel.pendingDuplicates.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingScripts by remember { mutableStateOf<List<ArchiveScript>>(emptyList()) }
    var fileReading by remember { mutableStateOf(false) }
    var listExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { navigator.showMessage(it) } }

    val handleResult: (LxPluginManager.ImportCheckResult) -> Unit = { result ->
        when (result) {
            is LxPluginManager.ImportCheckResult.Success -> navigator.showMessage("插件导入成功：${result.item.name}")
            is LxPluginManager.ImportCheckResult.Duplicate -> viewModel.addDuplicate(result)
            is LxPluginManager.ImportCheckResult.Failure -> navigator.showMessage("插件解析失败：${result.message}")
        }
    }

    val pickFile: () -> Unit = {
        scope.launch {
            val file = chooseFile("选择 LX 插件脚本或压缩包", "*.js;*.zip;*.7z") ?: return@launch
            fileReading = true
            try {
                val read = withContext(Dispatchers.IO) { file.inputStream().use { LxArchiveReader.readScripts(file.name, it) } }
                val scripts = read.getOrNull()
                when {
                    scripts == null -> navigator.showMessage("读取失败：${read.exceptionOrNull()?.message}")
                    scripts.size == 1 -> handleResult(viewModel.importFrom(scripts[0].content))
                    else -> pendingScripts = scripts
                }
            } catch (e: Exception) {
                navigator.showMessage("读取文件失败：${e.message}")
            } finally {
                fileReading = false
            }
        }
    }

    SettingRow(
        title = "启用社区源插件",
        subtitle = if (plugins.isEmpty()) "尚未导入脚本，请先导入 .js 脚本" else "开启后参与音源换源解析与第三方搜索结果播放"
    ) {
        SettingSwitch(enabled && plugins.isNotEmpty()) {
            if (plugins.isEmpty()) navigator.showMessage("请先导入源脚本") else viewModel.setEnabled(it)
        }
    }

    val collapsible = plugins.size > CollapsedPluginCount
    val shown = if (collapsible && !listExpanded) plugins.take(CollapsedPluginCount) else plugins
    shown.forEachIndexed { index, plugin ->
        PluginRow(
            index = index,
            count = plugins.size,
            plugin = plugin,
            onToggle = { viewModel.toggle(plugin.id) },
            onMoveUp = { viewModel.moveUp(plugin.id) },
            onMoveDown = { viewModel.moveDown(plugin.id) },
            onRemove = { viewModel.remove(plugin.id) }
        )
    }
    if (collapsible) {
        PluginExpandToggle(
            expanded = listExpanded,
            hiddenCount = plugins.size - CollapsedPluginCount,
            onToggle = { listExpanded = !listExpanded }
        )
    }

    Row(
        Modifier.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsActionButton("选取文件 (.js/.zip/.7z)", enabled = !fileReading && !importing, onClick = pickFile)
        SettingsActionButton("粘贴脚本或链接导入", enabled = !fileReading && !importing, onClick = { showImportDialog = true })
        if (fileReading || importing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = DesktopColors.Accent)
    }

    if (showImportDialog) {
        ImportDialog(
            viewModel = viewModel,
            onDismiss = { showImportDialog = false },
            onResult = handleResult
        )
    }

    if (pendingScripts.isNotEmpty()) {
        ScriptPickerDialog(
            scripts = pendingScripts,
            onDismiss = { pendingScripts = emptyList() },
            onConfirm = { selected ->
                pendingScripts = emptyList()
                viewModel.importScriptsInBackground(selected)
            }
        )
    }

    duplicates.firstOrNull()?.let { dup ->
        DesktopDialog(
            title = "检测到同名插件",
            onDismiss = viewModel::skipFirstDuplicate,
            width = 440.dp,
            actions = {
                DialogButton("取消", viewModel::skipFirstDuplicate)
                DialogButton("保留为副本", { viewModel.resolveFirstDuplicate(overwrite = false) })
                DialogButton("覆盖更新", { viewModel.resolveFirstDuplicate(overwrite = true) }, primary = true)
            }
        ) {
            Text(
                text = "插件【${dup.existing.name}】已存在，请选择导入方式：",
                color = DesktopColors.TextPrimary,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "现有版本：v${dup.existing.version}（作者：${dup.existing.author.ifBlank { "未知" }}）",
                color = DesktopColors.TextGray,
                fontSize = 12.sp
            )
            Text(
                text = "导入版本：v${dup.newInfo.version}（作者：${dup.newInfo.author.ifBlank { "未知" }}）",
                color = DesktopColors.TextPrimary,
                fontSize = 12.sp
            )
        }
    }
}

// 压缩包里有多个脚本时让用户勾选要导入的，默认全选
@Composable
private fun ScriptPickerDialog(
    scripts: List<ArchiveScript>,
    onDismiss: () -> Unit,
    onConfirm: (List<ArchiveScript>) -> Unit
) {
    var selected by remember(scripts) { mutableStateOf(scripts.map { it.path }.toSet()) }

    DesktopDialog(
        title = "选择要导入的脚本",
        onDismiss = onDismiss,
        width = 480.dp,
        actions = {
            DialogButton("取消", onDismiss)
            DialogButton(
                text = "导入所选（${selected.size}）",
                onClick = { onConfirm(scripts.filter { it.path in selected }) },
                primary = true,
                enabled = selected.isNotEmpty()
            )
        }
    ) {
        Text(
            text = "压缩包内找到 ${scripts.size} 个 .js 脚本：",
            color = DesktopColors.TextGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            scripts.forEach { script ->
                val checked = script.path in selected
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { selected = if (checked) selected - script.path else selected + script.path }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { selected = if (it) selected + script.path else selected - script.path },
                        colors = CheckboxDefaults.colors(
                            checkedColor = DesktopColors.Accent,
                            uncheckedColor = DesktopColors.TextGray,
                            checkmarkColor = DesktopColors.TextPrimary
                        )
                    )
                    Text(script.path, color = DesktopColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ImportDialog(
    viewModel: LxPluginSettingsViewModel,
    onDismiss: () -> Unit,
    onResult: (LxPluginManager.ImportCheckResult) -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var processing by remember { mutableStateOf(false) }

    DesktopDialog(
        title = "导入 LX 插件脚本",
        onDismiss = { if (!processing) onDismiss() },
        width = 520.dp,
        actions = {
            DialogButton("取消", onDismiss, enabled = !processing)
            DialogButton(
                text = "解析并导入",
                onClick = {
                    processing = true
                    scope.launch {
                        try {
                            val result = viewModel.importFrom(text)
                            if (result is LxPluginManager.ImportCheckResult.Failure) {
                                navigator.showMessage("解析失败：${result.message}")
                            } else {
                                onResult(result)
                                onDismiss()
                            }
                        } catch (e: Exception) {
                            navigator.showMessage("导入异常：${e.message}")
                        } finally {
                            processing = false
                        }
                    }
                },
                primary = true,
                enabled = text.isNotBlank(),
                loading = processing
            )
        }
    ) {
        Text(
            text = "支持粘贴 JS 脚本内容，或粘贴远程脚本链接（http/https）：",
            color = DesktopColors.TextGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        DialogTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().height(180.dp),
            placeholder = "脚本文本或以 http(s):// 开头的下载链接",
            singleLine = false,
            minLines = 8,
            maxLines = 8,
            enabled = !processing
        )
    }
}

@Composable
private fun PluginRow(
    index: Int,
    count: Int,
    plugin: LxPluginItem,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val primary = if (plugin.isEnabled) DesktopColors.TextPrimary else DesktopColors.TextGray
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DesktopColors.CardSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", color = DesktopColors.AccentText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("${plugin.name}（v${plugin.version}）", color = primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            if (plugin.author.isNotBlank()) {
                Text("作者：${plugin.author}", color = DesktopColors.TextGray, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
            if (plugin.description.isNotBlank()) {
                Text(plugin.description, color = DesktopColors.TextGray, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
            if (plugin.sources.isNotEmpty()) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("支持平台：", color = DesktopColors.TextGray, fontSize = 10.sp)
                    plugin.sources.forEach { key ->
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(DesktopColors.SurfaceLight).padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text(MusicPlatform.fromLxKey(key)?.shortName ?: key, color = primary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        SettingSwitch(plugin.isEnabled) { onToggle() }
        PluginIconButton(Icons.Rounded.ArrowUpward, "上移", enabled = index > 0, onClick = onMoveUp)
        PluginIconButton(Icons.Rounded.ArrowDownward, "下移", enabled = index < count - 1, onClick = onMoveDown)
        PluginIconButton(Icons.Rounded.Delete, "卸载", enabled = true, danger = true, onClick = onRemove)
    }
}

@Composable
private fun PluginExpandToggle(expanded: Boolean, hiddenCount: Int, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (expanded) "收起" else "展开其余 $hiddenCount 个插件", color = DesktopColors.TextGray, fontSize = 13.sp)
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            null,
            tint = DesktopColors.TextGray,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PluginIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = when {
                !enabled -> DesktopColors.SurfaceLight
                danger -> DesktopColors.Accent
                else -> DesktopColors.TextPrimary
            },
            modifier = Modifier.size(18.dp)
        )
    }
}

