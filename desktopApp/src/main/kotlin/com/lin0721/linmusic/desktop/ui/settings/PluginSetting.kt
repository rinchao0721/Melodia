package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.plugin.InstalledPlugin
import com.lin0721.linmusic.core.plugin.PLUGIN_FILE_EXTENSION
import com.lin0721.linmusic.core.plugin.PluginImportStep
import com.lin0721.linmusic.core.plugin.PluginRepository
import com.lin0721.linmusic.core.plugin.PluginRuntimeStatus
import com.lin0721.linmusic.core.plugin.currentPlayerLayoutOption
import com.lin0721.linmusic.core.plugin.layoutAreasWithCandidates
import com.lin0721.linmusic.core.plugin.playerLayoutOptions
import com.lin0721.linmusic.core.plugin.playerLayoutSummary
import com.lin0721.linmusic.core.plugin.pluginExportFileName
import com.lin0721.linmusic.core.plugin.pluginFeatureSummary
import com.lin0721.linmusic.core.plugin.pluginPendingFeatures
import com.lin0721.linmusic.core.plugin.pluginStatusLine
import com.lin0721.linmusic.core.plugin.rememberPluginSettingsController
import com.lin0721.linmusic.desktop.platform.chooseFile
import com.lin0721.linmusic.desktop.platform.chooseSaveFile
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogLabel
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private val PluginRowShape = RoundedCornerShape(8.dp)
private const val MAX_REPORT_LINES = 8

// 插件设置：已安装插件列表与启停、导入导出；导入先预览权限再确认
@Composable
internal fun PluginSetting(repository: PluginRepository) {
    val controller = rememberPluginSettingsController(repository)
    val installed by controller.installed.collectAsState()
    val status by controller.status.collectAsState()
    val layouts by controller.layouts.collectAsState()
    val navigator = LocalDesktopNavigator.current
    var deleteTarget by remember { mutableStateOf<InstalledPlugin?>(null) }

    LaunchedEffect(repository) { controller.refresh() }
    LaunchedEffect(controller.message) {
        controller.message?.let {
            navigator.showMessage(it)
            controller.dismissMessage()
        }
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("插件（实验性）", color = DesktopColors.TextPrimary, fontSize = 14.sp)
        Text(
            "导入 .$PLUGIN_FILE_EXTENSION 插件包，扩展播放器布局、页面、音源与歌词源",
            color = DesktopColors.TextGray,
            fontSize = 12.sp
        )
        if (installed.isEmpty()) {
            Text("还没有安装插件", color = DesktopColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Column(Modifier.padding(top = 8.dp)) {
            installed.forEach { plugin ->
                PluginRow(
                    plugin = plugin,
                    status = status[plugin.id],
                    onToggle = { controller.setEnabled(plugin.id, it) },
                    onExport = {
                        controller.export(plugin) {
                            chooseSaveFile("导出插件", pluginExportFileName(plugin), PLUGIN_FILE_EXTENSION)?.outputStream()
                        }
                    },
                    onDelete = { deleteTarget = plugin }
                )
            }
        }
        val layoutAreas = layoutAreasWithCandidates(layouts, "desktop.")
        if (layoutAreas.isNotEmpty()) {
            Text("播放器布局", color = DesktopColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            layoutAreas.forEach { area ->
                SettingRow(area.label, playerLayoutSummary(area, layouts)) {
                    SettingsDropdown(
                        options = playerLayoutOptions(area, layouts),
                        current = currentPlayerLayoutOption(area, layouts),
                        label = { it.label },
                        onSelect = { controller.chooseLayout(area, it.choice) },
                        menuWidth = 220.dp
                    )
                }
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            DialogButton(
                "导入插件",
                onClick = { controller.import { chooseFile("选择插件包", "*.$PLUGIN_FILE_EXTENSION")?.inputStream() } },
                loading = controller.step == PluginImportStep.Working
            )
        }
    }

    when (val step = controller.step) {
        is PluginImportStep.Preview -> PluginPreviewDialog(
            preview = step,
            onCancel = controller::cancelImport,
            onConfirm = controller::confirmImport
        )
        is PluginImportStep.Failed -> DesktopDialog(
            title = "无法导入插件",
            onDismiss = controller::dismissFailure,
            actions = { DialogButton("知道了", onClick = controller::dismissFailure, primary = true) }
        ) {
            Text(step.message, color = DesktopColors.TextGray, fontSize = 13.sp)
        }
        else -> Unit
    }

    deleteTarget?.let { plugin ->
        DesktopDialog(
            title = "删除插件",
            onDismiss = { deleteTarget = null },
            actions = {
                DialogButton("取消", onClick = { deleteTarget = null })
                DialogButton("删除", onClick = {
                    deleteTarget = null
                    controller.delete(plugin.id)
                }, danger = true)
            }
        ) {
            Text("确定删除「${plugin.name}」吗？插件保存的数据也会一并删除。", color = DesktopColors.TextGray, fontSize = 13.sp)
        }
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
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val manifest = plugin.manifest
    val statusLine = pluginStatusLine(plugin, status)
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(PluginRowShape)
            .background(DesktopColors.CardSurface)
            .hoverable(hoverSource)
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            val title = if (manifest != null) "${manifest.name}（v${manifest.version}）" else plugin.id
            val primary = if (plugin.state.enabled) DesktopColors.TextPrimary else DesktopColors.TextGray
            Text(title, color = primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (manifest != null) {
                val meta = listOfNotNull(manifest.author, pluginFeatureSummary(manifest).takeIf { it.isNotEmpty() }).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, color = DesktopColors.TextGray, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                manifest.description?.let {
                    Text(it, color = DesktopColors.TextGray, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Text(
                statusLine.text,
                color = if (statusLine.isError) DesktopColors.Error else DesktopColors.TextTertiary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        HoverReveal(hovered) {
            Row {
                if (manifest != null) RowIconButton(Icons.Rounded.FileUpload, "导出", hoverSource, onExport)
                RowIconButton(Icons.Rounded.Delete, "删除", hoverSource, onDelete)
            }
        }
        SettingSwitch(plugin.state.enabled, enabled = plugin.state.enabled || plugin.canRun, onChange = onToggle)
    }
}

@Composable
private fun RowIconButton(icon: ImageVector, description: String, hoverSource: MutableInteractionSource, onClick: () -> Unit) {
    DesktopTooltip(description) {
        IconButton(onClick = onClick, modifier = Modifier.size(32.dp).hoverable(hoverSource)) {
            Icon(icon, contentDescription = description, tint = DesktopColors.TextPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PluginPreviewDialog(
    preview: PluginImportStep.Preview,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val manifest = preview.staged.manifest
    val existing = preview.existing?.manifest
    DesktopDialog(
        title = if (existing != null) "升级插件" else "导入插件",
        onDismiss = onCancel,
        width = 460.dp,
        actions = {
            DialogButton("取消", onClick = onCancel)
            DialogButton(if (existing != null) "升级" else "安装并启用", onClick = onConfirm, primary = true)
        }
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text(manifest.name, color = DesktopColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            val version = if (existing != null) "版本 ${existing.version} → ${manifest.version}" else "版本 ${manifest.version}"
            Text(listOfNotNull(manifest.author, version).joinToString(" · "), color = DesktopColors.TextGray, fontSize = 12.sp)
            manifest.description?.let { Text(it, color = DesktopColors.TextGray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            val features = pluginFeatureSummary(manifest)
            if (features.isNotEmpty()) {
                Text("提供：$features", color = DesktopColors.TextGray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            pluginPendingFeatures(manifest)?.let {
                Text(it, color = DesktopColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            DialogLabel("需要的权限", Modifier.padding(top = 12.dp))
            if (manifest.permissions.isEmpty()) {
                Text("无", color = DesktopColors.TextGray, fontSize = 12.sp)
            }
            val newPermissions = preview.newPermissions
            manifest.permissions.forEach { permission ->
                val isNew = existing != null && permission in newPermissions
                Text(
                    if (isNew) "· ${permission.label}（新增）" else "· ${permission.label}",
                    color = if (isNew) DesktopColors.AccentText else DesktopColors.TextPrimary,
                    fontSize = 12.sp
                )
            }
            if (manifest.networkHosts.isNotEmpty()) {
                DialogLabel("允许访问的域名", Modifier.padding(top = 12.dp))
                Text(manifest.networkHosts.joinToString("、"), color = DesktopColors.TextPrimary, fontSize = 12.sp)
            }
            if (preview.staged.warnings.isNotEmpty()) {
                DialogLabel("提示", Modifier.padding(top = 12.dp))
                preview.staged.warnings.take(MAX_REPORT_LINES).forEach { Text(it, color = DesktopColors.TextGray, fontSize = 12.sp) }
                if (preview.staged.warnings.size > MAX_REPORT_LINES) {
                    Text("等共 ${preview.staged.warnings.size} 项", color = DesktopColors.TextTertiary, fontSize = 12.sp)
                }
            }
            Text(
                "插件脚本在隔离环境中运行，只能使用上面列出的权限",
                color = DesktopColors.TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
