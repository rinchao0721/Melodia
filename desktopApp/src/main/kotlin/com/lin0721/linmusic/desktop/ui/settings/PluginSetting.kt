package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Settings
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
import com.lin0721.linmusic.core.plugin.LocalPluginCards
import com.lin0721.linmusic.core.plugin.PLUGIN_FILE_EXTENSION
import com.lin0721.linmusic.core.plugin.PluginImportStep
import com.lin0721.linmusic.core.plugin.PluginKind
import com.lin0721.linmusic.core.plugin.PluginManifest
import com.lin0721.linmusic.core.plugin.PluginRepository
import com.lin0721.linmusic.core.plugin.PluginRuntimeStatus
import com.lin0721.linmusic.core.plugin.PluginPageView
import com.lin0721.linmusic.core.plugin.PluginSettingsView
import com.lin0721.linmusic.core.plugin.settingsPage
import com.lin0721.linmusic.core.plugin.backdropOptions
import com.lin0721.linmusic.core.plugin.currentBackdropOption
import com.lin0721.linmusic.core.plugin.currentPlayerLayoutOption
import com.lin0721.linmusic.core.plugin.layoutAreasWithCandidates
import com.lin0721.linmusic.core.plugin.playerLayoutOptions
import com.lin0721.linmusic.core.plugin.playerLayoutSummary
import com.lin0721.linmusic.core.plugin.pluginCatalogWarning
import com.lin0721.linmusic.core.plugin.pluginExportFileName
import com.lin0721.linmusic.core.plugin.pluginFeatureSummary
import com.lin0721.linmusic.core.plugin.pluginRiskWarning
import com.lin0721.linmusic.core.plugin.pluginStatusLine
import com.lin0721.linmusic.core.plugin.pluginUpdates
import com.lin0721.linmusic.core.plugin.rememberPluginSettingsController
import com.lin0721.linmusic.desktop.platform.chooseFile
import com.lin0721.linmusic.desktop.platform.chooseSaveFile
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogLabel
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.plugin.DesktopPluginPageUi
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import java.io.File

private val PluginRowShape = RoundedCornerShape(8.dp)
private const val MAX_REPORT_LINES = 8
private const val HASH_PREVIEW_LENGTH = 16

// 插件设置：已安装插件列表与启停、导入导出；导入先预览权限再确认
@Composable
internal fun PluginSetting(repository: PluginRepository) {
    val controller = rememberPluginSettingsController(repository)
    val installed by controller.installed.collectAsState()
    val status by controller.status.collectAsState()
    val layouts by controller.layouts.collectAsState()
    val pages by controller.pages.collectAsState()
    val nativeAllowed by controller.nativeAllowed.collectAsState()
    val navigator = LocalDesktopNavigator.current
    var confirmNative by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<InstalledPlugin?>(null) }
    var settingsTarget by remember { mutableStateOf<PluginManifest?>(null) }
    var rollbackTarget by remember { mutableStateOf<InstalledPlugin?>(null) }
    var catalogOpen by remember { mutableStateOf(false) }
    val catalog by controller.catalog.collectAsState()
    val updates = pluginUpdates(installed, catalog.entries)
    val pageHost = LocalPluginCards.current?.host

    LaunchedEffect(repository) {
        controller.refresh()
        controller.loadCatalog()
    }
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
                    onDelete = { deleteTarget = plugin },
                    onSettings = { settingsTarget = plugin.manifest },
                    hasSettings = plugin.manifest?.contributes?.settings?.isNotEmpty() == true || pages.settingsPage(plugin.id) != null,
                    updateVersion = updates[plugin.id]?.version,
                    onRollback = { rollbackTarget = plugin }
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
        val backdropState by controller.backdrops.collectAsState()
        if (backdropState.candidates.isNotEmpty()) {
            SettingRow("动态背景", backdropState.active?.let { "正在使用「${it.pluginName}」的${it.entry.name}" } ?: "未使用") {
                SettingsDropdown(
                    options = backdropOptions(backdropState),
                    current = currentBackdropOption(backdropState),
                    label = { it.label },
                    onSelect = { controller.chooseBackdrop(it.choice) },
                    menuWidth = 220.dp
                )
            }
        }
        if (controller.nativeSupported) {
            SettingRow("允许原生插件", "原生插件在主进程运行，等同于运行任意程序。关闭后立即卸载，开启后重启生效") {
                SettingSwitch(nativeAllowed, onChange = { if (it) confirmNative = true else controller.setNativeAllowed(false) })
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogButton("插件目录", onClick = { catalogOpen = true }, primary = updates.isNotEmpty())
            DialogButton(
                "导入插件",
                onClick = {
                    var chosen: File? = null
                    controller.import(sourceName = { chosen?.name }) {
                        chooseFile("选择插件包", "*.$PLUGIN_FILE_EXTENSION")?.also { chosen = it }?.inputStream()
                    }
                },
                loading = controller.step == PluginImportStep.Working
            )
        }
    }

    if (catalogOpen) PluginCatalogDialog(controller, onDismiss = { catalogOpen = false })

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

    if (confirmNative) {
        DesktopDialog(
            title = "允许原生插件？",
            onDismiss = { confirmNative = false },
            actions = {
                DialogButton("取消", onClick = { confirmNative = false })
                DialogButton("允许", onClick = {
                    confirmNative = false
                    controller.setNativeAllowed(true)
                }, danger = true)
            }
        ) {
            Text(
                "原生插件在主进程运行，可以读写你的文件、访问网络和账号，权限声明无法限制它。崩溃也可能带崩应用，下次启动会自动停用导致崩溃的插件；用 --safe-mode 启动可跳过所有原生插件。",
                color = DesktopColors.TextGray,
                fontSize = 13.sp
            )
        }
    }

    val settingsManifest = settingsTarget
    if (settingsManifest != null && pageHost != null) {
        DesktopDialog(
            title = "${settingsManifest.name} 设置",
            onDismiss = { settingsTarget = null },
            actions = { DialogButton("完成", onClick = { settingsTarget = null }, primary = true) }
        ) {
            val settingsPage = pages.settingsPage(settingsManifest.id)
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                settingsPage?.let { PluginPageView(it, pageHost, DesktopPluginPageUi, Modifier.fillMaxWidth()) }
                if (settingsManifest.contributes.settings.isNotEmpty()) {
                    PluginSettingsView(settingsManifest, pageHost, DesktopPluginPageUi, Modifier.fillMaxWidth())
                }
            }
        }
    }

    rollbackTarget?.let { plugin ->
        DesktopDialog(
            title = "回退插件",
            onDismiss = { rollbackTarget = null },
            actions = {
                DialogButton("取消", onClick = { rollbackTarget = null })
                DialogButton("回退", onClick = {
                    rollbackTarget = null
                    controller.rollback(plugin)
                }, primary = true)
            }
        ) {
            Text(
                "把「${plugin.name}」回退到 v${plugin.previousVersion}？当前版本不会保留，插件数据不变。",
                color = DesktopColors.TextGray,
                fontSize = 13.sp
            )
        }
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
    onDelete: () -> Unit,
    onSettings: () -> Unit,
    hasSettings: Boolean,
    updateVersion: String?,
    onRollback: () -> Unit
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
                val kindLabel = "原生".takeIf { manifest.kind == PluginKind.NATIVE }
                val meta = listOfNotNull(kindLabel, manifest.author, pluginFeatureSummary(manifest).takeIf { it.isNotEmpty() }).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, color = DesktopColors.TextGray, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                if (manifest.kind == PluginKind.NATIVE) {
                    Text(
                        listOfNotNull(plugin.state.source, plugin.state.nativeHash?.let { "SHA-256 ${it.take(HASH_PREVIEW_LENGTH)}" }).joinToString(" · "),
                        color = DesktopColors.TextTertiary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
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
            updateVersion?.let {
                Text("可更新到 v$it，到插件目录里更新", color = DesktopColors.AccentText, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        HoverReveal(hovered) {
            Row {
                plugin.previousVersion?.let { RowIconButton(Icons.Rounded.Restore, "回退到 v$it", hoverSource, onRollback) }
                if (hasSettings) RowIconButton(Icons.Rounded.Settings, "设置", hoverSource, onSettings)
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
            pluginRiskWarning(manifest)?.let {
                Text(it, color = DesktopColors.Error, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
            pluginCatalogWarning(preview)?.let {
                Text(it, color = DesktopColors.Error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (preview.staged.warnings.isNotEmpty()) {
                DialogLabel("提示", Modifier.padding(top = 12.dp))
                preview.staged.warnings.take(MAX_REPORT_LINES).forEach { Text(it, color = DesktopColors.TextGray, fontSize = 12.sp) }
                if (preview.staged.warnings.size > MAX_REPORT_LINES) {
                    Text("等共 ${preview.staged.warnings.size} 项", color = DesktopColors.TextTertiary, fontSize = 12.sp)
                }
            }
            if (manifest.kind == PluginKind.SANDBOX) {
                Text(
                    "插件脚本在隔离环境中运行，只能使用上面列出的权限",
                    color = DesktopColors.TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
