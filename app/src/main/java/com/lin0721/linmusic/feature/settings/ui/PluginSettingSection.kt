package com.lin0721.linmusic.feature.settings.ui

import org.koin.compose.koinInject
import com.lin0721.linmusic.core.plugin.isCard
import com.lin0721.linmusic.core.plugin.PluginPageHost
import com.lin0721.linmusic.core.plugin.ActivePluginPage
import com.lin0721.linmusic.core.plugin.playerLayoutSummary
import com.lin0721.linmusic.core.plugin.playerLayoutOptions
import com.lin0721.linmusic.core.plugin.layoutAreasWithCandidates
import com.lin0721.linmusic.core.plugin.currentPlayerLayoutOption
import com.lin0721.linmusic.core.plugin.PlayerLayoutArea
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.plugin.InstalledPlugin
import com.lin0721.linmusic.core.plugin.PluginImportStep
import com.lin0721.linmusic.core.plugin.PluginRepository
import com.lin0721.linmusic.core.plugin.PluginRuntimeStatus
import com.lin0721.linmusic.core.plugin.pluginExportFileName
import com.lin0721.linmusic.core.plugin.pluginFeatureSummary
import com.lin0721.linmusic.core.plugin.pluginStatusLine
import com.lin0721.linmusic.core.plugin.rememberPluginSettingsController
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaOutlinedButton
import com.lin0721.linmusic.core.ui.components.MelodiaSwitch
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

private val PluginRowShape = RoundedCornerShape(12.dp)
private const val MAX_REPORT_LINES = 8
private const val LAYOUT_CHOICE_AUTO = "auto"

// 插件设置：已安装插件列表与启停、导入导出；导入先在底部弹窗预览权限再确认
@Composable
fun PluginSettingSection(repository: PluginRepository) {
    val controller = rememberPluginSettingsController(repository)
    val installed by controller.installed.collectAsState()
    val status by controller.status.collectAsState()
    val layouts by controller.layouts.collectAsState()
    val pages by controller.pages.collectAsState()
    val pageHost = koinInject<PluginPageHost>()
    var openPage by remember { mutableStateOf<ActivePluginPage?>(null) }
    var layoutPickerArea by remember { mutableStateOf<PlayerLayoutArea?>(null) }
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<InstalledPlugin?>(null) }
    var exportTarget by remember { mutableStateOf<InstalledPlugin?>(null) }

    // 插件包扩展名不在系统 MIME 表里，只能放开类型由解包校验
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) controller.import { context.contentResolver.openInputStream(uri) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val target = exportTarget
        exportTarget = null
        if (uri != null && target != null) controller.export(target) { context.contentResolver.openOutputStream(uri) }
    }

    LaunchedEffect(repository) { controller.refresh() }
    LaunchedEffect(controller.message) {
        controller.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            controller.dismissMessage()
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("插件（实验性）", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        Text(
            "导入插件包，扩展播放器布局、页面、音源与歌词源",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        if (installed.isEmpty()) {
            Text("还没有安装插件", color = MelodiaColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            installed.forEach { plugin ->
                PluginRow(
                    plugin = plugin,
                    status = status[plugin.id],
                    onToggle = { controller.setEnabled(plugin.id, it) },
                    onExport = {
                        exportTarget = plugin
                        exportLauncher.launch(pluginExportFileName(plugin))
                    },
                    onDelete = { deleteTarget = plugin }
                )
            }
        }
        val entryPages = pages.filter { !it.isCard }
        if (entryPages.isNotEmpty()) {
            Text("插件页面", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            entryPages.forEach { active ->
                SettingsRow(title = active.page.title, subtitle = active.manifest.name, onClick = { openPage = active })
            }
        }
        val layoutAreas = layoutAreasWithCandidates(layouts, "android.")
        if (layoutAreas.isNotEmpty()) {
            Text("播放器布局", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            layoutAreas.forEach { area ->
                SettingsRow(
                    title = "${area.label}：${currentPlayerLayoutOption(area, layouts).label}",
                    subtitle = playerLayoutSummary(area, layouts),
                    onClick = { layoutPickerArea = area }
                )
            }
        }
        MelodiaOutlinedButton(
            onClick = { importLauncher.launch(arrayOf("*/*")) },
            enabled = controller.step != PluginImportStep.Working,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text(if (controller.step == PluginImportStep.Working) "正在读取…" else "导入插件", color = MelodiaColors.TextPrimary)
        }
    }

    when (val step = controller.step) {
        is PluginImportStep.Preview -> PluginPreviewSheet(
            preview = step,
            onCancel = controller::cancelImport,
            onConfirm = controller::confirmImport
        )
        is PluginImportStep.Failed -> AlertDialog(
            onDismissRequest = controller::dismissFailure,
            title = { Text("无法导入插件", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { Text(step.message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                MelodiaTextButton(onClick = controller::dismissFailure) { Text("知道了", color = MelodiaColors.AccentText) }
            },
            containerColor = MaterialTheme.colorScheme.background
        )
        else -> Unit
    }

    openPage?.let { active -> PluginPageDialog(active, pageHost, onDismiss = { openPage = null }) }

    layoutPickerArea?.let { area ->
        val options = playerLayoutOptions(area, layouts)
        OptionPickerSheet(
            title = area.label,
            options = options.map { option -> (option.choice ?: LAYOUT_CHOICE_AUTO) to option.label },
            selected = currentPlayerLayoutOption(area, layouts).choice ?: LAYOUT_CHOICE_AUTO,
            onSelect = { key -> controller.chooseLayout(area, key.takeIf { it != LAYOUT_CHOICE_AUTO }) },
            onDismiss = { layoutPickerArea = null }
        )
    }

    deleteTarget?.let { plugin ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除插件", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text("确定删除「${plugin.name}」吗？插件保存的数据也会一并删除。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                MelodiaTextButton(onClick = {
                    deleteTarget = null
                    controller.delete(plugin.id)
                }) { Text("删除", color = MelodiaColors.Error) }
            },
            dismissButton = {
                MelodiaTextButton(onClick = { deleteTarget = null }) { Text("取消", color = MelodiaColors.TextPrimary) }
            },
            containerColor = MaterialTheme.colorScheme.background
        )
    }
}

@Composable
private fun PluginRow(
    plugin: InstalledPlugin,
    status: PluginRuntimeStatus?,
    onToggle: (Boolean) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val manifest = plugin.manifest
    val statusLine = pluginStatusLine(plugin, status)
    Surface(shape = PluginRowShape, color = MelodiaColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val title = if (manifest != null) "${manifest.name}（v${manifest.version}）" else plugin.id
                Text(
                    title,
                    color = if (plugin.state.enabled) MelodiaColors.TextPrimary else MelodiaColors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (manifest != null) {
                    val meta = listOfNotNull(manifest.author, pluginFeatureSummary(manifest).takeIf { it.isNotEmpty() }).joinToString(" · ")
                    if (meta.isNotEmpty()) Text(meta, color = MelodiaColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Text(
                    statusLine.text,
                    color = if (statusLine.isError) MelodiaColors.Error else MelodiaColors.TextTertiary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (manifest != null) {
                IconButton(onClick = onExport, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = "导出插件", tint = MelodiaColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "删除插件", tint = MelodiaColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
            MelodiaSwitch(
                checked = plugin.state.enabled,
                onCheckedChange = onToggle,
                enabled = plugin.state.enabled || plugin.canRun
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginPreviewSheet(
    preview: PluginImportStep.Preview,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val manifest = preview.staged.manifest
    val existing = preview.existing?.manifest
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MelodiaColors.Background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MelodiaSpacing.lg)
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.md)
        ) {
            Text(manifest.name, color = MelodiaColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val version = if (existing != null) "版本 ${existing.version} → ${manifest.version}" else "版本 ${manifest.version}"
            Text(listOfNotNull(manifest.author, version).joinToString(" · "), color = MelodiaColors.TextSecondary, fontSize = 13.sp)
            manifest.description?.let {
                Text(it, color = MelodiaColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
            val features = pluginFeatureSummary(manifest)
            if (features.isNotEmpty()) {
                Text("提供：$features", color = MelodiaColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
            SectionLabel("需要的权限")
            if (manifest.permissions.isEmpty()) Text("无", color = MelodiaColors.TextSecondary, fontSize = 13.sp)
            val newPermissions = preview.newPermissions
            manifest.permissions.forEach { permission ->
                val isNew = existing != null && permission in newPermissions
                Text(
                    if (isNew) "· ${permission.label}（新增）" else "· ${permission.label}",
                    color = if (isNew) MelodiaColors.AccentText else MelodiaColors.TextPrimary,
                    fontSize = 13.sp
                )
            }
            if (manifest.networkHosts.isNotEmpty()) {
                SectionLabel("允许访问的域名")
                Text(manifest.networkHosts.joinToString("、"), color = MelodiaColors.TextPrimary, fontSize = 13.sp)
            }
            val warnings = preview.staged.warnings
            if (warnings.isNotEmpty()) {
                SectionLabel("提示")
                warnings.take(MAX_REPORT_LINES).forEach { Text(it, color = MelodiaColors.TextSecondary, fontSize = 13.sp) }
                if (warnings.size > MAX_REPORT_LINES) Text("等共 ${warnings.size} 项", color = MelodiaColors.TextTertiary, fontSize = 12.sp)
            }
            Text(
                "插件脚本在隔离环境中运行，只能使用上面列出的权限",
                color = MelodiaColors.TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 14.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MelodiaTextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("取消", color = MelodiaColors.TextPrimary)
                }
                MelodiaButton(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = MelodiaColors.Accent, contentColor = MelodiaColors.OnAccent),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (existing != null) "升级" else "安装并启用", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = MelodiaColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}
