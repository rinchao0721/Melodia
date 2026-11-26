package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font as PlatformFont
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.font.FONT_ID_BUILTIN_DEFAULT
import com.lin0721.linmusic.core.ui.font.FONT_ID_BUILTIN_FIGTREE
import com.lin0721.linmusic.core.ui.font.FONT_ID_BUILTIN_MONOSPACE
import com.lin0721.linmusic.core.ui.font.FONT_ID_FOLLOW
import com.lin0721.linmusic.core.ui.font.FONT_ID_SKIN
import com.lin0721.linmusic.core.ui.font.FontEntry
import com.lin0721.linmusic.core.ui.font.FontRepository
import com.lin0721.linmusic.core.ui.font.FontRole
import com.lin0721.linmusic.core.ui.font.FontSettings
import com.lin0721.linmusic.core.ui.font.FontSource
import com.lin0721.linmusic.core.ui.font.FontStore
import com.lin0721.linmusic.core.ui.icons.ChevronDown
import com.lin0721.linmusic.core.ui.icons.Close
import com.lin0721.linmusic.core.ui.icons.MelodiaIcons
import com.lin0721.linmusic.desktop.platform.chooseFiles
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FontCardShape = RoundedCornerShape(8.dp)

@Composable
internal fun FontSetting(
    fontStore: FontStore,
    fontRepository: FontRepository,
    settingsPreferences: SettingsPreferences
) {
    val scope = rememberCoroutineScope()
    val navigator = LocalDesktopNavigator.current
    val fontSettings by settingsPreferences.fontSettings.collectAsState(initial = FontSettings())
    var installedFonts by remember { mutableStateOf<List<FontEntry>>(emptyList()) }
    val systemFonts by produceState(emptyList<FontEntry>()) {
        value = withContext(Dispatchers.IO) { fontRepository.listSystemFonts() }
    }
    val figtreeFamily by produceState<FontFamily?>(null, fontRepository) {
        value = withContext(Dispatchers.IO) { fontRepository.figtreeFamily() }
    }
    var roleExpanded by remember { mutableStateOf(false) }

    suspend fun refreshInstalled() {
        installedFonts = withContext(Dispatchers.IO) {
            fontRepository.listInstalledFonts().filter { it.source == FontSource.IMPORTED }
        }
    }

    LaunchedEffect(fontStore) { refreshInstalled() }

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("全局字体", color = DesktopColors.TextPrimary, fontSize = 14.sp)
                Text("应用内默认字体，未单独指定用途时生效", color = DesktopColors.TextGray, fontSize = 12.sp)
            }
            FontPickerDropdown(
                selectedId = fontSettings.globalFontId,
                includeSkin = true,
                includeFollow = false,
                figtreeFamily = figtreeFamily,
                installedFonts = installedFonts,
                systemFonts = systemFonts,
                onSelect = { id -> scope.launch { settingsPreferences.setRoleFont(FontRole.GLOBAL, id) } }
            )
        }

        Column(
            Modifier.fillMaxWidth()
                .clip(FontCardShape)
                .background(DesktopColors.Surface.copy(alpha = 0.5f))
                .border(1.dp, DesktopColors.Divider, FontCardShape)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        roleExpanded = !roleExpanded
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("按用途单独指定（标题 / 歌词 / 数字）", color = DesktopColors.TextPrimary, fontSize = 13.sp)
                Icon(MelodiaIcons.ChevronDown, contentDescription = null, tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
            }
            AnimatedVisibility(visible = roleExpanded) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(FontRole.TITLE, FontRole.LYRICS, FontRole.NUMBERS).forEach { role ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(role.label, color = DesktopColors.TextPrimary, fontSize = 13.sp)
                            FontPickerDropdown(
                                selectedId = fontSettings.fontIdFor(role),
                                includeSkin = false,
                                includeFollow = true,
                                figtreeFamily = figtreeFamily,
                                installedFonts = installedFonts,
                                systemFonts = systemFonts,
                                onSelect = { id -> scope.launch { settingsPreferences.setRoleFont(role, id) } }
                            )
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("已导入字体", color = DesktopColors.TextPrimary, fontSize = 14.sp)
                Text("支持多选 .ttf / .otf，按家族名自动归组", color = DesktopColors.TextGray, fontSize = 12.sp)
            }
            DialogButton("导入字体", onClick = {
                scope.launch {
                    val files = chooseFiles("选择字体文件", "*.ttf;*.otf")
                    if (files.isEmpty()) return@launch
                    val result = withContext(Dispatchers.IO) { fontStore.importFiles(files) }
                    fontRepository.invalidate()
                    refreshInstalled()
                    val msg = when {
                        result.imported.isNotEmpty() && result.failures.isEmpty() -> "已导入 ${result.imported.size} 个字体"
                        result.imported.isNotEmpty() -> "已导入 ${result.imported.size} 个字体，失败 ${result.failures.size} 个：${result.failures.first().reason}"
                        result.failures.isNotEmpty() -> "导入失败：${result.failures.first().reason}"
                        else -> "未导入任何字体"
                    }
                    navigator.showMessage(msg)
                }
            }, primary = true)
        }

        if (installedFonts.isEmpty()) {
            Text("暂无已导入字体", color = DesktopColors.TextGray, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                installedFonts.forEach { entry ->
                    ImportedFontRow(
                        entry = entry,
                        fontStore = fontStore,
                        onDelete = {
                            scope.launch {
                                val deleted = withContext(Dispatchers.IO) { fontStore.delete(entry.id) }
                                if (deleted) {
                                    FontRole.entries.forEach { role ->
                                        if (fontSettings.fontIdFor(role) == entry.id) {
                                            val fallback = if (role == FontRole.GLOBAL) FONT_ID_SKIN else FONT_ID_FOLLOW
                                            settingsPreferences.setRoleFont(role, fallback)
                                        }
                                    }
                                    fontRepository.invalidate()
                                    refreshInstalled()
                                    navigator.showMessage("已删除字体「${entry.name}」")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun FontPickerDropdown(
    selectedId: String,
    includeSkin: Boolean,
    includeFollow: Boolean,
    figtreeFamily: FontFamily?,
    installedFonts: List<FontEntry>,
    systemFonts: List<FontEntry>,
    onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val close = {
        open = false
        query = ""
    }
    val selectedLabel = remember(selectedId, installedFonts, systemFonts) {
        when (selectedId) {
            FONT_ID_SKIN -> "跟随皮肤"
            FONT_ID_FOLLOW -> "跟随全局"
            FONT_ID_BUILTIN_FIGTREE -> "Figtree"
            FONT_ID_BUILTIN_DEFAULT -> "系统默认"
            FONT_ID_BUILTIN_MONOSPACE -> "等宽字体"
            else -> installedFonts.firstOrNull { it.id == selectedId }?.name
                ?: systemFonts.firstOrNull { it.id == selectedId }?.name
                ?: selectedId.removePrefix("system:")
        }
    }

    Box {
        Row(
            Modifier.clip(FontCardShape)
                .background(DesktopColors.Surface)
                .border(1.dp, DesktopColors.Divider, FontCardShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { open = true }
                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                selectedLabel,
                color = DesktopColors.TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
            Spacer(Modifier.width(4.dp))
            Icon(MelodiaIcons.ChevronDown, contentDescription = null, tint = DesktopColors.TextGray, modifier = Modifier.size(16.dp))
        }
        DesktopMenu(expanded = open, onDismiss = close, width = 260.dp) {
            DialogTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索字体",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
            )
            val keyword = query.trim()
            val builtins = buildList {
                if (includeSkin) add(FONT_ID_SKIN to "跟随皮肤")
                if (includeFollow) add(FONT_ID_FOLLOW to "跟随全局")
                add(FONT_ID_BUILTIN_FIGTREE to "Figtree")
                add(FONT_ID_BUILTIN_DEFAULT to "系统默认")
                add(FONT_ID_BUILTIN_MONOSPACE to "等宽字体")
            }.filter { keyword.isEmpty() || it.second.contains(keyword, ignoreCase = true) }
            val filteredInstalled = installedFonts.filter { keyword.isEmpty() || it.name.contains(keyword, ignoreCase = true) }
            val filteredSystem = systemFonts.filter { keyword.isEmpty() || it.name.contains(keyword, ignoreCase = true) }

            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(builtins, key = { "builtin:${it.first}" }) { (id, label) ->
                    val previewFamily = when (id) {
                        FONT_ID_BUILTIN_FIGTREE -> figtreeFamily
                        FONT_ID_BUILTIN_MONOSPACE -> FontFamily.Monospace
                        else -> null
                    }
                    SimpleMenuItem(
                        label,
                        selected = id == selectedId,
                        leading = previewFamily?.let { family ->
                            { Text("Aa", color = DesktopColors.TextGray, fontSize = 12.sp, fontFamily = family) }
                        },
                        onClick = {
                            onSelect(id)
                            close()
                        }
                    )
                }
                items(filteredInstalled, key = { "installed:${it.id}" }) { entry ->
                    SimpleMenuItem(entry.name, selected = entry.id == selectedId, onClick = {
                        onSelect(entry.id)
                        close()
                    })
                }
                items(filteredSystem, key = { it.id }) { entry ->
                    val name = entry.systemFontName ?: entry.name
                    val family = remember(name) { FontFamily(SystemFont(name)) }
                    SimpleMenuItem(
                        name,
                        selected = entry.id == selectedId,
                        leading = { Text("Aa", color = DesktopColors.TextGray, fontSize = 12.sp, fontFamily = family) },
                        onClick = {
                            onSelect(entry.id)
                            close()
                        }
                    )
                }
            }
            if (builtins.isEmpty() && filteredInstalled.isEmpty() && filteredSystem.isEmpty()) {
                Text("没有匹配的字体", color = DesktopColors.TextGray, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun ImportedFontRow(entry: FontEntry, fontStore: FontStore, onDelete: () -> Unit) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val previewFamily by produceState<FontFamily?>(null, entry.id) {
        value = withContext(Dispatchers.IO) {
            val dir = fontStore.directoryOf(entry.id) ?: return@withContext null
            val face = entry.faces.firstOrNull { !it.italic && it.weight == 400 } ?: entry.faces.firstOrNull() ?: return@withContext null
            val file = java.io.File(dir, face.file)
            if (!file.isFile) return@withContext null
            runCatching {
                FontFamily(
                    PlatformFont(
                        identity = "${entry.id}:${face.file}:${file.length()}:${file.lastModified()}",
                        data = file.readBytes(),
                        weight = FontWeight(face.weight.coerceIn(100, 900)),
                        style = if (face.italic) FontStyle.Italic else FontStyle.Normal
                    )
                )
            }.getOrNull()
        }
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(FontCardShape)
            .background(DesktopColors.Surface.copy(alpha = 0.5f))
            .border(1.dp, DesktopColors.Divider, FontCardShape)
            .hoverable(hoverSource)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.name, color = DesktopColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.hasChinese) {
                    Text("含中文", color = DesktopColors.TextGray, fontSize = 11.sp)
                }
                Text("${entry.faces.size} 个字面", color = DesktopColors.TextGray, fontSize = 11.sp)
            }
            Text(
                "Aa 永和九年 0123",
                color = DesktopColors.TextGray,
                fontSize = 13.sp,
                fontFamily = previewFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HoverReveal(revealed = hovered) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(MelodiaIcons.Close, contentDescription = "删除字体", tint = DesktopColors.TextGray, modifier = Modifier.size(16.dp))
            }
        }
    }
}
