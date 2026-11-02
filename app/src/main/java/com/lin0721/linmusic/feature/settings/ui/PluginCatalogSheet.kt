package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.lin0721.linmusic.core.ui.components.MelodiaOutlinedButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

private val CatalogRowShape = RoundedCornerShape(12.dp)

// 插件目录：浏览、搜索、安装与更新，安装走和手动导入相同的预览确认
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginCatalogSheet(controller: PluginSettingsController, onDismiss: () -> Unit) {
    val catalog by controller.catalog.collectAsState()
    val installed by controller.installed.collectAsState()
    var query by remember { mutableStateOf("") }
    var editingUrl by remember { mutableStateOf(false) }
    var urlText by remember(catalog.url) { mutableStateOf(catalog.url) }
    val busy = controller.step == PluginImportStep.Working
    val updates = pluginUpdates(installed, catalog.entries)
    val entries = filterCatalog(catalog.entries, query)

    LaunchedEffect(controller) { controller.loadCatalog() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MelodiaColors.Background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MelodiaSpacing.lg)
                .navigationBarsPadding()
                .padding(bottom = MelodiaSpacing.md)
        ) {
            Text("插件目录", color = MelodiaColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索插件") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            if (!catalog.isDefault) {
                Text(
                    "正在使用自定义目录，其中的插件没有经过官方审核",
                    color = MelodiaColors.Error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            catalog.error?.let {
                Text(it, color = MelodiaColors.Error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (editingUrl) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it.filterNot(Char::isWhitespace) },
                    label = { Text("目录地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MelodiaOutlinedButton(onClick = {
                        editingUrl = false
                        controller.setCatalogUrl(urlText)
                    }) { Text("保存", color = MelodiaColors.TextPrimary) }
                    MelodiaOutlinedButton(onClick = {
                        editingUrl = false
                        controller.setCatalogUrl(null)
                    }) { Text("恢复默认", color = MelodiaColors.TextPrimary) }
                }
            }
            if (entries.isEmpty()) {
                val text = when {
                    catalog.loading -> "正在读取目录…"
                    catalog.entries.isEmpty() -> "目录里还没有插件"
                    else -> "没有找到匹配的插件"
                }
                Text(text, color = MelodiaColors.TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.padding(top = 12.dp).heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MelodiaTextButton(onClick = { editingUrl = !editingUrl }) {
                    Text(if (editingUrl) "收起地址" else "目录地址", color = MelodiaColors.TextPrimary)
                }
                MelodiaTextButton(onClick = controller::loadCatalog, enabled = !catalog.loading) {
                    Text("刷新", color = MelodiaColors.AccentText)
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
    Surface(shape = CatalogRowShape, color = MelodiaColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${entry.name}（v${entry.version}）",
                    color = MelodiaColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = catalogEntryMeta(entry)
                if (meta.isNotEmpty()) Text(meta, color = MelodiaColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                entry.description?.let {
                    Text(it, color = MelodiaColors.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                problem?.let {
                    Text(it, color = MelodiaColors.Error, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            val action = catalogActionLabel(installed, hasUpdate, problem, progress)
            if (action.enabled) {
                MelodiaOutlinedButton(onClick = onInstall, enabled = !busy) {
                    Text(action.text, color = MelodiaColors.TextPrimary)
                }
            } else {
                Text(action.text, color = MelodiaColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}
