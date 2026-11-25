package com.lin0721.linmusic.feature.settings.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.components.MelodiaOutlinedButton
import com.lin0721.linmusic.core.ui.components.MelodiaTextButton
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.font.FONT_ID_BUILTIN_DEFAULT
import com.lin0721.linmusic.core.ui.font.FONT_ID_BUILTIN_MONOSPACE
import com.lin0721.linmusic.core.ui.font.FONT_ID_FOLLOW
import com.lin0721.linmusic.core.ui.font.FONT_ID_SKIN
import com.lin0721.linmusic.core.ui.font.FontEntry
import com.lin0721.linmusic.core.ui.font.FontImportFailure
import com.lin0721.linmusic.core.ui.font.FontImportResult
import com.lin0721.linmusic.core.ui.font.MAX_FONT_FILE_BYTES
import com.lin0721.linmusic.core.ui.font.FontRepository
import com.lin0721.linmusic.core.ui.font.FontRole
import com.lin0721.linmusic.core.ui.font.FontSettings
import com.lin0721.linmusic.core.ui.font.FontSource
import com.lin0721.linmusic.core.ui.font.FontStore
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FontSettingSection(
    settings: FontSettings,
    repository: FontRepository,
    store: FontStore,
    onSelectRoleFont: (FontRole, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installedFonts by remember { mutableStateOf<List<FontEntry>>(emptyList()) }
    LaunchedEffect(repository) {
        installedFonts = withContext(Dispatchers.IO) { repository.listInstalledFonts() }
    }
    var showManageSheet by remember { mutableStateOf(false) }
    var roleExpanded by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }

    val importedFonts = remember(installedFonts) {
        installedFonts.filter { it.source == FontSource.IMPORTED }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                importFontUris(context, store, uris)
            }
            repository.invalidate()
            installedFonts = withContext(Dispatchers.IO) { repository.listInstalledFonts() }
            if (result.imported.isNotEmpty()) {
                ToastManager.showToast("已导入 ${result.imported.size} 个字体")
            }
            if (result.failures.isNotEmpty()) {
                importErrorMessage = result.failures.joinToString("\n") { "${it.fileName}: ${it.reason}" }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("字体", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                Text(
                    text = "当前全局：${resolveFontDisplayName(settings.globalFontId, importedFonts, isGlobal = true)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            MelodiaOutlinedButton(onClick = { showManageSheet = true }) {
                Text("管理字体", fontSize = 12.sp)
            }
        }

        FontChoiceChips(
            selectedId = settings.globalFontId,
            includeSkinOption = true,
            includeFollowOption = false,
            importedFonts = importedFonts,
            onSelect = { onSelectRoleFont(FontRole.GLOBAL, it) }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { roleExpanded = !roleExpanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "按用途单独设置",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (roleExpanded) "收起" else "展开",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
        }

        AnimatedVisibility(visible = roleExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(FontRole.TITLE, FontRole.LYRICS, FontRole.NUMBERS).forEach { role ->
                    val currentId = settings.fontIdFor(role)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${role.label}：${resolveFontDisplayName(currentId, importedFonts, isGlobal = false)}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                        FontChoiceChips(
                            selectedId = currentId,
                            includeSkinOption = false,
                            includeFollowOption = true,
                            importedFonts = importedFonts,
                            onSelect = { onSelectRoleFont(role, it) }
                        )
                    }
                }
            }
        }
    }

    if (showManageSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showManageSheet = false },
            sheetState = sheetState,
            containerColor = MelodiaColors.Surface,
            dragHandle = { MelodiaDragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "字体管理",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    MelodiaButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) }
                    ) {
                        Text("导入字体", fontSize = 13.sp)
                    }
                }

                if (importedFonts.isEmpty()) {
                    Text(
                        text = "暂无已导入字体，支持多选 .ttf / .otf 文件并按家族名自动归组。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    importedFonts.forEach { entry ->
                        val previewFamily = remember(entry.id) {
                            buildPreviewFontFamily(entry, store.directoryOf(entry.id))
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MelodiaColors.Divider)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.name,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val tags = buildList {
                                            add("${entry.faces.size} 个字面")
                                            if (entry.hasChinese) add("含中文")
                                        }.joinToString(" · ")
                                        Text(
                                            text = tags,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    MelodiaTextButton(
                                        onClick = {
                                            scope.launch {
                                                val deleted = withContext(Dispatchers.IO) { store.delete(entry.id) }
                                                if (deleted) {
                                                    resetDeletedFontReferences(entry.id, settings, onSelectRoleFont)
                                                    repository.invalidate()
                                                    installedFonts = withContext(Dispatchers.IO) { repository.listInstalledFonts() }
                                                    ToastManager.showToast("已删除「${entry.name}」")
                                                }
                                            }
                                        }
                                    ) {
                                        Text("删除", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                                    }
                                }
                                HorizontalDivider(color = MelodiaColors.Divider)
                                Text(
                                    text = "Aa 永和九年 0123",
                                    fontFamily = previewFamily ?: FontFamily.Default,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    importErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { importErrorMessage = null },
            title = { Text("部分字体导入失败") },
            text = { Text(message, fontSize = 13.sp) },
            confirmButton = {
                MelodiaTextButton(onClick = { importErrorMessage = null }) {
                    Text("确定")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontChoiceChips(
    selectedId: String,
    includeSkinOption: Boolean,
    includeFollowOption: Boolean,
    importedFonts: List<FontEntry>,
    onSelect: (String) -> Unit
) {
    val options = buildList {
        if (includeSkinOption) add(FONT_ID_SKIN to "跟随皮肤")
        if (includeFollowOption) add(FONT_ID_FOLLOW to "跟随全局")
        add(FONT_ID_BUILTIN_DEFAULT to "系统默认")
        add(FONT_ID_BUILTIN_MONOSPACE to "等宽")
        importedFonts.forEach { add(it.id to it.name) }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (id, label) ->
            FilterChip(
                selected = selectedId == id,
                onClick = { onSelect(id) },
                label = { Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

private fun resolveFontDisplayName(
    fontId: String,
    importedFonts: List<FontEntry>,
    isGlobal: Boolean
): String = when (fontId) {
    FONT_ID_SKIN -> if (isGlobal) "跟随皮肤" else "跟随全局"
    FONT_ID_FOLLOW -> "跟随全局"
    FONT_ID_BUILTIN_DEFAULT -> "系统默认"
    FONT_ID_BUILTIN_MONOSPACE -> "等宽"
    else -> importedFonts.firstOrNull { it.id == fontId }?.name ?: "默认"
}

private fun resetDeletedFontReferences(
    deletedId: String,
    settings: FontSettings,
    onSelectRoleFont: (FontRole, String) -> Unit
) {
    if (settings.globalFontId == deletedId) onSelectRoleFont(FontRole.GLOBAL, FONT_ID_SKIN)
    if (settings.titleFontId == deletedId) onSelectRoleFont(FontRole.TITLE, FONT_ID_FOLLOW)
    if (settings.lyricsFontId == deletedId) onSelectRoleFont(FontRole.LYRICS, FONT_ID_FOLLOW)
    if (settings.numbersFontId == deletedId) onSelectRoleFont(FontRole.NUMBERS, FONT_ID_FOLLOW)
}

private fun importFontUris(
    context: Context,
    store: FontStore,
    uris: List<Uri>
): FontImportResult {
    val tempDir = File(context.cacheDir, "font_import_${UUID.randomUUID()}")
    if (!tempDir.mkdirs()) {
        return FontImportResult(emptyList(), listOf(FontImportFailure("", "无法创建临时目录")))
    }
    val copiedFiles = mutableListOf<File>()
    val failures = mutableListOf<FontImportFailure>()
    try {
        for ((index, uri) in uris.withIndex()) {
            val rawName = runCatching { queryDisplayName(context, uri) }.getOrNull() ?: "font_$index.ttf"
            val fileName = rawName.replace(Regex("""[\\/:*?"<>|]"""), "_")
            val target = File(tempDir, "${index}_$fileName")
            try {
                val input = context.contentResolver.openInputStream(uri)
                if (input == null) {
                    failures += FontImportFailure(fileName, "无法读取文件")
                    continue
                }
                // 超限的文件只拷到上限多一字节，交给字体库按体积拒绝
                val copied = input.use { source ->
                    target.outputStream().use { output -> copyLimited(source, output, MAX_FONT_FILE_BYTES + 1) }
                }
                if (copied > 0) copiedFiles += target else failures += FontImportFailure(fileName, "文件为空")
            } catch (e: IOException) {
                failures += FontImportFailure(fileName, "读取失败：${e.message}")
            } catch (e: SecurityException) {
                failures += FontImportFailure(fileName, "没有读取权限")
            }
        }
        val result = store.importFiles(copiedFiles)
        return result.copy(failures = failures + result.failures)
    } finally {
        tempDir.deleteRecursively()
    }
}

private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (total < limit) {
        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
        if (read < 0) break
        output.write(buffer, 0, read)
        total += read
    }
    return total
}

private fun buildPreviewFontFamily(entry: FontEntry, dir: File?): FontFamily? {
    if (dir == null || entry.faces.isEmpty()) return null
    val fonts = entry.faces.mapNotNull { face ->
        val file = File(dir, face.file)
        if (!file.isFile) return@mapNotNull null
        Font(
            file = file,
            weight = FontWeight(face.weight.coerceIn(1, 1000)),
            style = if (face.italic) FontStyle.Italic else FontStyle.Normal
        )
    }
    return if (fonts.isEmpty()) null else FontFamily(fonts)
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return cursor.getString(idx)?.takeIf { it.isNotBlank() }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}
