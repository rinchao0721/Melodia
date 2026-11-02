package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.plugin.InstalledPlugin
import com.lin0721.linmusic.core.plugin.PluginCatalogEntry
import com.lin0721.linmusic.core.plugin.PluginCatalogParser
import com.lin0721.linmusic.core.plugin.PluginImportStep
import com.lin0721.linmusic.core.plugin.PluginSettingsController
import com.lin0721.linmusic.core.plugin.catalogActionLabel
import com.lin0721.linmusic.core.plugin.catalogEntryMeta
import com.lin0721.linmusic.core.plugin.filterCatalog
import com.lin0721.linmusic.core.plugin.pluginUpdates
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogLabel
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private val CatalogRowShape = RoundedCornerShape(8.dp)

// 插件目录：浏览、搜索、安装与更新，安装走和手动导入相同的预览确认
@Composable
internal fun PluginCatalogDialog(controller: PluginSettingsController, onDismiss: () -> Unit) {
    val catalog by controller.catalog.collectAsState()
    val installed by controller.installed.collectAsState()
    var query by remember { mutableStateOf("") }
    var editingUrl by remember { mutableStateOf(false) }
    var urlText by remember(catalog.url) { mutableStateOf(catalog.url) }
    val busy = controller.step == PluginImportStep.Working
    val updates = pluginUpdates(installed, catalog.entries)
    val entries = filterCatalog(catalog.entries, query)

    LaunchedEffect(controller) { controller.loadCatalog() }

    DesktopDialog(
        title = "插件目录",
        onDismiss = onDismiss,
        width = 560.dp,
        actions = {
            DialogButton(if (editingUrl) "收起地址" else "目录地址", onClick = { editingUrl = !editingUrl })
            DialogButton("刷新", onClick = controller::loadCatalog, loading = catalog.loading)
            DialogButton("关闭", onClick = onDismiss, primary = true)
        }
    ) {
        Column {
            DialogTextField(query, { query = it }, placeholder = "搜索插件")
            if (!catalog.isDefault) {
                Text(
                    "正在使用自定义目录，其中的插件没有经过官方审核，原生插件尤其要谨慎",
                    color = DesktopColors.Error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            catalog.error?.let {
                Text(it, color = DesktopColors.Error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (editingUrl) {
                DialogLabel("目录地址", Modifier.padding(top = 12.dp))
                DialogTextField(urlText, { urlText = it }, placeholder = "https://…/index.json")
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogButton("保存", onClick = {
                        editingUrl = false
                        controller.setCatalogUrl(urlText)
                    })
                    DialogButton("恢复默认", onClick = {
                        editingUrl = false
                        controller.setCatalogUrl(null)
                    })
                }
            }
            if (entries.isEmpty()) {
                val text = when {
                    catalog.loading -> "正在读取目录…"
                    catalog.entries.isEmpty() -> "目录里还没有插件"
                    else -> "没有找到匹配的插件"
                }
                Text(text, color = DesktopColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            } else {
                LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = 400.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        CatalogRow(
                            entry = entry,
                            installed = installed.firstOrNull { it.id == entry.id },
                            hasUpdate = entry.id in updates,
                            busy = busy,
                            progress = controller.downloadProgress.takeIf { controller.downloadingId == entry.id },
                            onInstall = { controller.installFromCatalog(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(
    entry: PluginCatalogEntry,
    installed: InstalledPlugin?,
    hasUpdate: Boolean,
    busy: Boolean,
    progress: Float?,
    onInstall: () -> Unit
) {
    val problem = PluginCatalogParser.incompatibility(entry)
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(CatalogRowShape)
            .background(DesktopColors.CardSurface)
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${entry.name}（v${entry.version}）",
                color = DesktopColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = catalogEntryMeta(entry)
            if (meta.isNotEmpty()) Text(meta, color = DesktopColors.TextGray, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            entry.description?.let {
                Text(it, color = DesktopColors.TextGray, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            entry.source?.let {
                Text(it, color = DesktopColors.TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            problem?.let {
                Text(it, color = DesktopColors.Error, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        val label = catalogActionLabel(installed, hasUpdate, problem, progress)
        if (label.enabled) {
            DialogButton(label.text, onClick = { if (!busy) onInstall() }, primary = hasUpdate, loading = progress != null)
        } else {
            Text(label.text, color = DesktopColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}
