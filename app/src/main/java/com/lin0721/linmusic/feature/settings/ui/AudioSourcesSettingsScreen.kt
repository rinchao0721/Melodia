package com.lin0721.linmusic.feature.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.source.ArchiveScript
import com.lin0721.linmusic.core.source.LxArchiveReader
import com.lin0721.linmusic.core.source.LxPluginManager
import com.lin0721.linmusic.core.source.SourcePreferences
import com.lin0721.linmusic.core.source.UnmApiClient
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.MelodiaSwitch
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AudioSourcesSettingsView(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val searchAggregationEnabled by viewModel.searchAggregationEnabled.collectAsStateWithLifecycle()
    val fallbackEnabled by viewModel.fallbackEnabled.collectAsStateWithLifecycle()
    val unmServerUrl by viewModel.unmServerUrl.collectAsStateWithLifecycle()
    val unmRemoteFallbackEnabled by viewModel.unmRemoteFallbackEnabled.collectAsStateWithLifecycle()
    val unmAutoMatch by viewModel.unmAutoMatch.collectAsStateWithLifecycle()
    val communityPriority by viewModel.communityPriority.collectAsStateWithLifecycle()
    val lxPluginEnabled by viewModel.lxPluginEnabled.collectAsStateWithLifecycle()
    val lxPlugins by viewModel.lxPlugins.collectAsStateWithLifecycle()

    var showServerUrlDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var duplicateQueue by remember { mutableStateOf<List<LxPluginManager.ImportCheckResult.Duplicate>>(emptyList()) }
    val duplicateCheckResult = duplicateQueue.firstOrNull()
    var pendingScripts by remember { mutableStateOf<List<ArchiveScript>>(emptyList()) }

    // 系统文件选取器 (选取 .js 文件)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val name = uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "script.js" }
                    val read = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { LxArchiveReader.readScripts(name, it) }
                    }
                    val scripts = read?.getOrNull()
                    when {
                        read == null -> ToastManager.showToast("读取文件失败")
                        scripts == null -> ToastManager.showToast("读取失败: ${read.exceptionOrNull()?.message}")
                        scripts.size > 1 -> pendingScripts = scripts
                        else -> when (val result = viewModel.precheckAndImportScript(scripts[0].content)) {
                            is LxPluginManager.ImportCheckResult.Success -> {
                                ToastManager.showToast("插件导入成功: ${result.item.name}")
                            }
                            is LxPluginManager.ImportCheckResult.Duplicate -> {
                                duplicateQueue = duplicateQueue + result
                            }
                            is LxPluginManager.ImportCheckResult.Failure -> {
                                ToastManager.showToast("插件解析失败: ${result.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    ToastManager.showToast("读取文件失败: ${e.message}")
                }
            }
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
    ) {
        // 分组 1：换源基础设置
        item {
            SettingsGroupCard(SettingsSubMenu.AUDIO_SOURCES.sectionTitles[0]) {
                SettingsSwitchRow(
                    title = "多平台聚合搜索",
                    subtitle = "在搜索结果页提供酷狗、酷我、QQ 音乐的平台标签，切换后才会发起搜索；播放通过换源插件解析",
                    checked = searchAggregationEnabled,
                    onCheckedChange = { viewModel.updateSearchAggregationEnabled(it) }
                )
                HorizontalDivider(color = MelodiaColors.Divider)
                SettingsSwitchRow(
                    title = "无版权/VIP 自动换源",
                    subtitle = "官方网易云音源不可用或仅为试听时，自动从可用音源解析完整播放直链",
                    checked = fallbackEnabled,
                    onCheckedChange = { viewModel.updateFallbackEnabled(it) }
                )
                if (fallbackEnabled) {
                    HorizontalDivider(color = MelodiaColors.Divider)
                    SettingsSwitchRow(
                        title = "社区源优先",
                        subtitle = "开启后优先尝试通过 LX 插件脚本解析，失败后 fallback 到远程 UNM 服务；关闭则先尝试远程 UNM",
                        checked = communityPriority,
                        onCheckedChange = { viewModel.updateCommunityPriority(it) }
                    )
                }
            }
        }

        // 分组 2：社区扩展源
        item {
            SettingsGroupCard(SettingsSubMenu.AUDIO_SOURCES.sectionTitles[1]) {
                val hasPlugins = lxPlugins.isNotEmpty()

                SettingsSwitchRow(
                    title = "启用社区源插件",
                    subtitle = if (hasPlugins) "开启后参与音源换源解析" else "尚未导入脚本，请先导入 .js 脚本",
                    checked = lxPluginEnabled && hasPlugins,
                    onCheckedChange = {
                        if (hasPlugins) {
                            viewModel.updateLxPluginEnabled(it)
                        } else {
                            ToastManager.showToast("请先导入源脚本")
                        }
                    }
                )

                if (hasPlugins) {
                    Spacer(modifier = Modifier.height(MelodiaSpacing.xs))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        lxPlugins.forEachIndexed { index, plugin ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (plugin.isEnabled) 0.5f else 0.25f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = if (plugin.isEnabled) 0.15f else 0.05f),
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (plugin.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${plugin.name} (v${plugin.version})",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (plugin.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            MelodiaSwitch(
                                                checked = plugin.isEnabled,
                                                onCheckedChange = { viewModel.toggleLxPlugin(plugin.id) }
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            MelodiaIconButton(
                                                onClick = { viewModel.moveLxPluginUp(plugin.id) },
                                                enabled = index > 0
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowUpward,
                                                    contentDescription = "上移",
                                                    tint = if (index > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            MelodiaIconButton(
                                                onClick = { viewModel.moveLxPluginDown(plugin.id) },
                                                enabled = index < lxPlugins.size - 1
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDownward,
                                                    contentDescription = "下移",
                                                    tint = if (index < lxPlugins.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            MelodiaIconButton(
                                                onClick = { viewModel.removeLxPlugin(plugin.id) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "卸载",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (plugin.author.isNotBlank()) {
                                        Text(
                                            text = "作者: ${plugin.author}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (plugin.isEnabled) 1f else 0.5f),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    if (plugin.description.isNotBlank()) {
                                        Text(
                                            text = plugin.description,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (plugin.isEnabled) 1f else 0.5f),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }

                                    if (plugin.sources.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "支持平台:",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (plugin.isEnabled) 1f else 0.5f)
                                            )
                                            plugin.sources.forEach { sourceKey ->
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (plugin.isEnabled) 0.15f else 0.05f)
                                                ) {
                                                    Text(
                                                        text = formatSourceBadge(sourceKey),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (plugin.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(MelodiaSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
                ) {
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(PillRadius),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("选取文件 (.js/.zip/.7z)", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(PillRadius),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("粘贴脚本导入", fontSize = 13.sp)
                    }
                }
            }
        }

        // 分组 3：远程 UNM 服务
        item {
            SettingsGroupCard(SettingsSubMenu.AUDIO_SOURCES.sectionTitles[2]) {
                SettingsSwitchRow(
                    title = "启用远程 UNM 服务",
                    subtitle = "向远程自建或第三方的 UNM（UnblockNeteaseMusic）服务器请求音频直链",
                    checked = unmRemoteFallbackEnabled,
                    onCheckedChange = { viewModel.updateUnmRemoteFallbackEnabled(it) }
                )

                if (unmRemoteFallbackEnabled) {
                    HorizontalDivider(color = MelodiaColors.Divider)

                    SettingsRow(
                        title = "远程服务接口 (Base URL)",
                        subtitle = unmServerUrl.ifBlank { "未配置（点击设置服务器地址）" },
                        onClick = { showServerUrlDialog = true }
                    )

                    HorizontalDivider(color = MelodiaColors.Divider)

                    SettingsSwitchRow(
                        title = "服务端自动选择模式",
                        subtitle = "由服务端自动轮询最优源匹配",
                        checked = unmAutoMatch,
                        onCheckedChange = { viewModel.updateUnmAutoMatch(it) }
                    )
                }
            }
        }
    }

    // 服务器地址配置弹窗
    if (showServerUrlDialog) {
        ServerUrlEditDialog(
            currentUrl = unmServerUrl,
            onDismiss = { showServerUrlDialog = false },
            onSave = { newUrl ->
                viewModel.updateUnmServerUrl(newUrl)
                showServerUrlDialog = false
            },
            onReset = {
                viewModel.resetUnmServerUrl()
                showServerUrlDialog = false
            }
        )
    }

    // 粘贴/下载脚本导入弹窗
    if (showImportDialog) {
        LxScriptImportDialog(
            viewModel = viewModel,
            onDismiss = { showImportDialog = false },
            onDuplicate = { duplicate ->
                duplicateQueue = duplicateQueue + duplicate
            }
        )
    }

    // 压缩包内多脚本勾选弹窗
    if (pendingScripts.isNotEmpty()) {
        ScriptPickerDialog(
            scripts = pendingScripts,
            onDismiss = { pendingScripts = emptyList() },
            onConfirm = { selected ->
                pendingScripts = emptyList()
                // 在 ViewModel 作用域里导入，离开设置页不会中断
                viewModel.importScriptsInBackground(selected) { batch ->
                    duplicateQueue = duplicateQueue + batch.duplicates
                    ToastManager.showToast(batch.summary())
                    batch.failures.firstOrNull()?.let { ToastManager.showToast("插件解析失败: $it") }
                }
            }
        )
    }

    // 重名插件处理选择弹窗
    duplicateCheckResult?.let { duplicate ->
        DuplicatePluginDialog(
            duplicate = duplicate,
            onDismiss = { duplicateQueue = duplicateQueue.drop(1) },
            onOverwrite = {
                coroutineScope.launch {
                    viewModel.confirmImportPlugin(
                        info = duplicate.newInfo,
                        script = duplicate.rawScript,
                        overwriteExistingId = duplicate.existing.id
                    )
                    ToastManager.showToast("已覆盖更新插件: ${duplicate.newInfo.name}")
                    duplicateQueue = duplicateQueue.drop(1)
                }
            },
            onKeepBoth = {
                coroutineScope.launch {
                    viewModel.confirmImportPlugin(
                        info = duplicate.newInfo,
                        script = duplicate.rawScript,
                        overwriteExistingId = null
                    )
                    ToastManager.showToast("已作为新插件添加: ${duplicate.newInfo.name}")
                    duplicateQueue = duplicateQueue.drop(1)
                }
            }
        )
    }
}

// 格式化平台支持角标
private fun formatSourceBadge(key: String): String = when (key) {
    "kw" -> "酷我"
    "kg" -> "酷狗"
    "tx" -> "QQ"
    "mg" -> "咪咕"
    "wy" -> "网易云"
    else -> key.uppercase()
}

// 服务器地址配置弹窗
@Composable
private fun ServerUrlEditDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var textValue by remember { mutableStateOf(currentUrl) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "配置换源服务器接口", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "填写 UNM Utils 服务的基础访问 URL（末尾无需带斜杠）：",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
                )
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        // 复制来的地址常带换行，单行输入框只显示首行会像是空的
                        textValue = it.filterNot(Char::isWhitespace)
                        testResult = null
                    },
                    placeholder = { Text("例如 https://your-unm-server.com", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                isTesting = true
                                testResult = UnmApiClient().testConnection(textValue.trim())
                                isTesting = false
                            }
                        },
                        enabled = !isTesting && textValue.isNotBlank()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("检测中...", fontSize = 12.sp)
                        } else {
                            Text("测试连接", fontSize = 12.sp)
                        }
                    }

                    testResult?.let { success ->
                        Text(
                            text = if (success) "✓ 服务正常可用" else "✕ 连接失败，请检查地址",
                            color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(textValue.trim()) }
            ) {
                Text("保存", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    textValue = ""
                    testResult = null
                    onReset()
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清空")
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

// 粘贴脚本或远程 URL 导入弹窗
@Composable
private fun LxScriptImportDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onDuplicate: (LxPluginManager.ImportCheckResult.Duplicate) -> Unit
) {
    var textValue by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = {
            if (!isProcessing) onDismiss()
        },
        title = {
            Text(text = "导入 LX 插件脚本", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "支持粘贴 JS 脚本内容，或粘贴远程脚本 URL (http/https)：",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
                )
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    placeholder = { Text("粘贴脚本文本或以 http(s):// 开头的下载链接...", fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    enabled = !isProcessing,
                    maxLines = 10
                )

                if (isProcessing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val input = textValue.trim()
                    if (input.isBlank()) return@Button
                    isProcessing = true
                    val isRemote = input.startsWith("http://", ignoreCase = true) || input.startsWith("https://", ignoreCase = true)
                    statusText = if (isRemote) "正在从远端下载脚本..." else "正在校验沙盒环境..."

                    scope.launch {
                        try {
                            val contentRes = viewModel.resolveScriptContent(input)
                            if (contentRes.isFailure) {
                                isProcessing = false
                                ToastManager.showToast("下载远程脚本失败: ${contentRes.exceptionOrNull()?.message}")
                                return@launch
                            }
                            statusText = "正在加载并校验脚本..."
                            val script = contentRes.getOrThrow()
                            when (val result = viewModel.precheckAndImportScript(script)) {
                                is LxPluginManager.ImportCheckResult.Success -> {
                                    isProcessing = false
                                    ToastManager.showToast("插件导入成功: ${result.item.name}")
                                    onDismiss()
                                }
                                is LxPluginManager.ImportCheckResult.Duplicate -> {
                                    isProcessing = false
                                    onDuplicate(result)
                                    onDismiss()
                                }
                                is LxPluginManager.ImportCheckResult.Failure -> {
                                    isProcessing = false
                                    ToastManager.showToast("解析失败: ${result.message}")
                                }
                            }
                        } catch (e: Exception) {
                            isProcessing = false
                            ToastManager.showToast("导入异常: ${e.message}")
                        }
                    }
                },
                enabled = !isProcessing && textValue.isNotBlank()
            ) {
                Text("解析并导入", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text("取消")
            }
        }
    )
}

// 重名插件冲突处理弹窗
@Composable
private fun DuplicatePluginDialog(
    duplicate: LxPluginManager.ImportCheckResult.Duplicate,
    onDismiss: () -> Unit,
    onOverwrite: () -> Unit,
    onKeepBoth: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "检测到同名插件", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "插件【${duplicate.existing.name}】已存在，请选择导入方式：",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "现有版本: v${duplicate.existing.version} (作者: ${duplicate.existing.author.ifBlank { "未知" }})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "导入版本: v${duplicate.newInfo.version} (作者: ${duplicate.newInfo.author.ifBlank { "未知" }})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOverwrite,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("覆盖更新", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onKeepBoth
                ) {
                    Text("保留为副本", fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// 压缩包里有多个脚本时让用户勾选要导入的，默认全选
@Composable
private fun ScriptPickerDialog(
    scripts: List<ArchiveScript>,
    onDismiss: () -> Unit,
    onConfirm: (List<ArchiveScript>) -> Unit
) {
    var selected by remember(scripts) { mutableStateOf(scripts.map { it.path }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "选择要导入的脚本", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "压缩包内找到 ${scripts.size} 个 .js 脚本：",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = MelodiaSpacing.sm)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    scripts.forEach { script ->
                        val checked = script.path in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = if (checked) selected - script.path else selected + script.path },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { selected = if (it) selected + script.path else selected - script.path }
                            )
                            Text(
                                text = script.path,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(scripts.filter { it.path in selected }) },
                enabled = selected.isNotEmpty()
            ) {
                Text("导入所选（${selected.size}）", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
