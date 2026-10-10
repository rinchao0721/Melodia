package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.SourcePreferences
import com.lin0721.linmusic.core.source.UnmModule
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.preferences.FullPlayerCardLayout
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.CrossfadePolicy
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.desktop.platform.native.AutoStartManager
import com.lin0721.linmusic.desktop.platform.CloseAction
import com.lin0721.linmusic.desktop.platform.DesktopImageLoader
import com.lin0721.linmusic.desktop.platform.native.AppPaths
import com.lin0721.linmusic.desktop.platform.DesktopPreferences
import com.lin0721.linmusic.desktop.platform.native.GlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.HotkeyCombo
import com.lin0721.linmusic.desktop.platform.native.SystemMediaSession
import com.lin0721.linmusic.desktop.player.cache.AudioCache
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.awt.event.KeyEvent as AwtKeyEvent
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser

// 与 Android 音质设置保持同一组选项
private val CacheSizeOptions = listOf(500L, 1024L, 2048L, 5120L, 10240L).map { it * 1024 * 1024 }

private val CrossfadeDurationOptionsMs = listOf(1_000, 2_000, 3_000, 4_000, 6_000, 8_000, 10_000, 12_000)

private val QualityOptions = listOf(
    "standard" to "标准音质",
    "exhigh" to "极高音质",
    "lossless" to "无损音质 (FLAC)",
    "hires" to "Hi-Res 无损",
    "jymaster" to "超清母带"
)

@Composable
fun SettingsPage(modifier: Modifier = Modifier) {
    val koin = remember { GlobalContext.get() }
    val settingsPreferences = remember { koin.get<SettingsPreferences>() }
    val sourcePreferences = remember { koin.get<SourcePreferences>() }
    val desktopPreferences = remember { koin.get<DesktopPreferences>() }
    val hotkeys = remember { koin.get<GlobalHotkeyService>() }
    val audioCache = remember { koin.get<AudioCache>() }
    val navigator = LocalDesktopNavigator.current
    val smtc = remember { koin.get<SystemMediaSession>() }
    val scope = rememberCoroutineScope()

    val quality by settingsPreferences.wifiQuality.collectAsState(initial = "lossless")
    val fallbackEnabled by sourcePreferences.fallbackEnabled.collectAsState(initial = false)
    val searchAggregationEnabled by sourcePreferences.searchAggregationEnabled.collectAsState(initial = false)
    val unmServerUrl by sourcePreferences.unmServerUrl.collectAsState(initial = "")
    val unmRemoteFallbackEnabled by sourcePreferences.unmRemoteFallbackEnabled.collectAsState(initial = false)
    val unmAutoMatch by sourcePreferences.unmAutoMatch.collectAsState(initial = true)
    val unmEnabledModules by sourcePreferences.unmEnabledModules.collectAsState(initial = UnmModule.ALL_KEYS.toSet())
    val unmModuleOrder by sourcePreferences.unmModuleOrder.collectAsState(initial = UnmModule.ALL_KEYS)
    val showDesktopLyric by settingsPreferences.showDesktopLrc.collectAsState(initial = false)
    val logLevelName by settingsPreferences.logLevel.collectAsState(initial = AppLogger.LogLevel.WARN.name)
    var logBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) { logBytes = withContext(Dispatchers.IO) { AppLogger.getLogsSize() } }
    val streamCacheEnabled by settingsPreferences.streamCacheEnabled.collectAsState(initial = true)
    val cacheMaxSize by settingsPreferences.audioCacheMaxSize.collectAsState(initial = CacheSizeOptions.first())
    var cacheUsedBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) { cacheUsedBytes = withContext(Dispatchers.IO) { audioCache.totalSize() } }
    var imageCacheBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) { imageCacheBytes = withContext(Dispatchers.IO) { DesktopImageLoader.diskCacheSize() } }
    val crossfadeEnabled by settingsPreferences.crossfadeEnabled.collectAsState(initial = false)
    val crossfadeDurationMs by settingsPreferences.crossfadeDurationMs.collectAsState(initial = CrossfadePolicy.DEFAULT_DURATION_MS)
    val downloadFolder by settingsPreferences.downloadFolderUri.collectAsState(initial = null)
    val downloadLyrics by settingsPreferences.downloadLyricsEnabled.collectAsState(initial = true)
    val defaultPlaylistPrivate by settingsPreferences.defaultPlaylistPrivate.collectAsState(initial = false)
    val cardLayout by settingsPreferences.fullPlayerCardLayout.collectAsState(initial = FullPlayerCardLayout.DEFAULT)
    val closeAction by desktopPreferences.closeAction.collectAsState(initial = CloseAction.TRAY)
    val mediaKeysEnabled by desktopPreferences.mediaKeysEnabled.collectAsState(initial = true)
    val hotkeyMap by desktopPreferences.hotkeys.collectAsState(initial = HotkeyCombo.defaults)
    val failedHotkeys by hotkeys.failed.collectAsState()
    val smtcAvailable by smtc.available.collectAsState()

    val scrollState = rememberScrollState()
    HoverScrollbarBox(scrollState) {
        Column(
            modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("设置", color = DesktopColors.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)

            SettingsCard("播放") {
                SettingRow("在线播放音质") {
                    QualitySelector(quality) { scope.launch { settingsPreferences.saveWifiQuality(it) } }
                }
                SettingRow("歌曲淡入淡出", subtitle = "自动切歌时前后两首交叉淡化，手动切歌不受影响") {
                    SettingSwitch(crossfadeEnabled) { scope.launch { settingsPreferences.saveCrossfadeEnabled(it) } }
                }
                if (crossfadeEnabled) {
                    SettingRow("淡化时长") {
                        CrossfadeDurationSelector(crossfadeDurationMs) { scope.launch { settingsPreferences.saveCrossfadeDurationMs(it) } }
                    }
                }
            }

            SettingsCard("音源与换源") {
                SettingRow(
                    title = "多平台聚合搜索",
                    subtitle = "在搜索页展示酷狗、酷我、QQ 音乐等多个平台的独立搜索 Tab"
                ) {
                    SettingSwitch(searchAggregationEnabled) { scope.launch { sourcePreferences.saveSearchAggregationEnabled(it) } }
                }

                SettingRow(
                    title = "无版权/VIP 自动换源",
                    subtitle = "官方网易云音源不可用或仅为试听时，优先通过本地直连音源获取完整播放直链"
                ) {
                    SettingSwitch(fallbackEnabled) { scope.launch { sourcePreferences.saveFallbackEnabled(it) } }
                }

                Text(
                    text = "本地直连音源（响应极速、去中心化，按优先级依次尝试）：",
                    color = DesktopColors.TextGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                unmModuleOrder.forEachIndexed { index, moduleKey ->
                    val module = UnmModule.fromKey(moduleKey)
                    val displayName = module?.displayName ?: moduleKey
                    val description = module?.description ?: ""
                    val isEnabled = moduleKey in unmEnabledModules

                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. $displayName ($description)",
                            color = if (isEnabled) DesktopColors.TextPrimary else DesktopColors.TextGray,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        SettingSwitch(isEnabled) {
                            scope.launch { sourcePreferences.toggleUnmModule(moduleKey) }
                        }
                        IconButton(
                            onClick = { scope.launch { sourcePreferences.moveUnmModuleUp(moduleKey) } },
                            enabled = index > 0
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "上移",
                                tint = if (index > 0) DesktopColors.TextPrimary else DesktopColors.SurfaceLight,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { scope.launch { sourcePreferences.moveUnmModuleDown(moduleKey) } },
                            enabled = index < unmModuleOrder.size - 1
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "下移",
                                tint = if (index < unmModuleOrder.size - 1) DesktopColors.TextPrimary else DesktopColors.SurfaceLight,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                SettingRow(
                    title = "启用远程兜底服务",
                    subtitle = "当本地所有已启用的音源均未解析成功时，向远程 UNM 服务器请求兜底"
                ) {
                    SettingSwitch(unmRemoteFallbackEnabled) { scope.launch { sourcePreferences.saveUnmRemoteFallbackEnabled(it) } }
                }

                if (unmRemoteFallbackEnabled) {
                    SettingRow(
                        title = "兜底服务接口 (Base URL)",
                        subtitle = unmServerUrl.ifBlank { "未配置" }
                    ) {
                        DesktopServerUrlInput(
                            currentUrl = unmServerUrl,
                            onSave = { scope.launch { sourcePreferences.saveUnmServerUrl(it) } },
                            onReset = { scope.launch { sourcePreferences.resetUnmServerUrl() } }
                        )
                    }

                    SettingRow(
                        title = "服务端自动选择模式",
                        subtitle = "由服务端自动轮询最优源，关闭后按本地模块顺序向远程请求"
                    ) {
                        SettingSwitch(unmAutoMatch) { scope.launch { sourcePreferences.saveUnmAutoMatch(it) } }
                    }
                }
            }

            SettingsCard("缓存") {
                SettingRow("边听边存", subtitle = "在线播放完整听完的歌曲保存到本地，下次直接播放，离线时也可播放") {
                    SettingSwitch(streamCacheEnabled) { scope.launch { settingsPreferences.saveStreamCacheEnabled(it) } }
                }
                SettingRow("缓存容量上限") {
                    CacheSizeSelector(cacheMaxSize) { size ->
                        scope.launch {
                            settingsPreferences.saveAudioCacheMaxSize(size)
                            withContext(Dispatchers.IO) { audioCache.evict(size) }
                            cacheUsedBytes = withContext(Dispatchers.IO) { audioCache.totalSize() }
                        }
                    }
                }
                SettingRow("图片缓存", subtitle = "${formatBytes(imageCacheBytes)} / ${formatBytes(DesktopImageLoader.DISK_CACHE_MAX_BYTES)}") {
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { DesktopImageLoader.clear() }
                            imageCacheBytes = withContext(Dispatchers.IO) { DesktopImageLoader.diskCacheSize() }
                        }
                    }) {
                        Text("清除图片缓存", color = DesktopColors.Accent)
                    }
                }
                SettingRow("已用空间", subtitle = formatBytes(cacheUsedBytes)) {
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { audioCache.clear() }
                            cacheUsedBytes = withContext(Dispatchers.IO) { audioCache.totalSize() }
                        }
                    }) {
                        Text("清除缓存", color = DesktopColors.Accent)
                    }
                }
            }

            SettingsCard("下载") {
                val customFolder = downloadFolder?.takeIf { it.isNotBlank() }
                SettingRow("下载目录", subtitle = customFolder ?: AppPaths.current.defaultDownloadDir.absolutePath) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (customFolder != null) {
                            TextButton(onClick = { scope.launch { settingsPreferences.saveDownloadFolderUri(null) } }) {
                                Text("恢复默认", color = DesktopColors.TextGray)
                            }
                        }
                        TextButton(onClick = {
                            scope.launch {
                                val initial = File(customFolder ?: AppPaths.current.defaultDownloadDir.absolutePath)
                                chooseDirectory(initial)?.let { settingsPreferences.saveDownloadFolderUri(it.absolutePath) }
                            }
                        }) {
                            Text("更改", color = DesktopColors.Accent)
                        }
                    }
                }
                SettingRow("内嵌歌词", subtitle = "下载时把歌词写入音频文件的标签") {
                    SettingSwitch(downloadLyrics) { scope.launch { settingsPreferences.saveDownloadLyricsEnabled(it) } }
                }
            }

            SettingsCard("歌单") {
                SettingRow("新建歌单默认设为隐私", subtitle = "创建歌单对话框中的隐私开关以此为初始值") {
                    SettingSwitch(defaultPlaylistPrivate) { scope.launch { settingsPreferences.saveDefaultPlaylistPrivate(it) } }
                }
            }

            SettingsCard("桌面歌词") {
                SettingRow("显示桌面歌词") {
                    SettingSwitch(showDesktopLyric) { scope.launch { settingsPreferences.saveShowDesktopLrc(it) } }
                }
            }

            SettingsCard("正在播放面板") {
                Text("拖动调整信息卡片的顺序，关闭开关可隐藏对应卡片", color = DesktopColors.TextGray, fontSize = 12.sp)
                CardLayoutEditor(cardLayout) { scope.launch { settingsPreferences.saveFullPlayerCardLayout(it) } }
            }

            SettingsCard("窗口") {
                SettingRow("关闭主窗口时") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CloseOption("最小化到托盘", closeAction == CloseAction.TRAY) {
                            scope.launch { desktopPreferences.saveCloseAction(CloseAction.TRAY) }
                        }
                        CloseOption("直接退出", closeAction == CloseAction.EXIT) {
                            scope.launch { desktopPreferences.saveCloseAction(CloseAction.EXIT) }
                        }
                    }
                }
                AutoStartRow()
            }

            SettingsCard("日志") {
                SettingRow("日志级别", subtitle = "越详细越利于排查问题，立即生效") {
                    LogLevelSelector(logLevelName) { level ->
                        scope.launch {
                            settingsPreferences.saveLogLevel(level.name)
                            AppLogger.setLevel(level)
                        }
                    }
                }
                SettingRow("日志文件", subtitle = "${formatBytes(logBytes)} · ${AppPaths.current.logDir.absolutePath}") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            if (!openDirectory(AppPaths.current.logDir)) navigator.showMessage("无法打开日志目录")
                        }) {
                            Text("打开目录", color = DesktopColors.Accent)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { AppLogger.clearLogs() }
                                logBytes = withContext(Dispatchers.IO) { AppLogger.getLogsSize() }
                            }
                        }) {
                            Text("清除", color = DesktopColors.TextGray)
                        }
                    }
                }
            }

            SettingsCard("快捷键") {
                SettingRow(
                    title = "系统媒体控制（媒体键与系统播放卡片）",
                    subtitle = if (smtcAvailable) null else "系统卡片不可用，已改用全局媒体键"
                ) {
                    SettingSwitch(mediaKeysEnabled) { scope.launch { desktopPreferences.saveMediaKeysEnabled(it) } }
                }
                HotkeyEditor(
                    hotkeyMap = hotkeyMap,
                    failed = failedHotkeys,
                    hotkeys = hotkeys,
                    onSave = { scope.launch { desktopPreferences.saveHotkeys(it) } }
                )
            }
        }
    }
}

private fun chooseDirectory(initial: File): File? {
    val chooser = JFileChooser(initial.takeIf { it.isDirectory }).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "选择下载目录"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DesktopColors.CardSurface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, color = DesktopColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    subtitleColor: androidx.compose.ui.graphics.Color = DesktopColors.TextGray,
    trailing: @Composable () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DesktopColors.TextPrimary, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, color = subtitleColor, fontSize = 12.sp)
        }
        trailing()
    }
}

@Composable
internal fun SettingSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = DesktopColors.TextPrimary,
            checkedTrackColor = DesktopColors.Accent,
            uncheckedThumbColor = DesktopColors.TextGray,
            uncheckedTrackColor = DesktopColors.SurfaceLight
        )
    )
}

@Composable
private fun LogLevelSelector(currentName: String, onSelect: (AppLogger.LogLevel) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = runCatching { AppLogger.LogLevel.valueOf(currentName) }.getOrDefault(AppLogger.LogLevel.WARN)
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(logLevelLabel(current), color = DesktopColors.TextPrimary)
            Icon(Icons.Rounded.ArrowDropDown, null, tint = DesktopColors.TextGray)
        }
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }, width = 168.dp) {
            AppLogger.LogLevel.entries.forEach { level ->
                SimpleMenuItem(
                    text = logLevelLabel(level),
                    trailing = if (level == current) {
                        { Icon(Icons.Rounded.Check, null, tint = DesktopColors.Accent, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(level)
                    }
                )
            }
        }
    }
}

private fun logLevelLabel(level: AppLogger.LogLevel): String = when (level) {
    AppLogger.LogLevel.DEBUG -> "详细"
    AppLogger.LogLevel.INFO -> "标准"
    AppLogger.LogLevel.WARN -> "精简"
    AppLogger.LogLevel.ERROR -> "仅错误"
}

private fun openDirectory(dir: File): Boolean = try {
    dir.mkdirs()
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        Desktop.getDesktop().open(dir)
        true
    } else {
        false
    }
} catch (_: Exception) {
    false
}

@Composable
private fun CacheSizeSelector(currentBytes: Long, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(formatBytes(currentBytes), color = DesktopColors.TextPrimary)
            Icon(Icons.Rounded.ArrowDropDown, null, tint = DesktopColors.TextGray)
        }
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }, width = 168.dp) {
            CacheSizeOptions.forEach { value ->
                SimpleMenuItem(
                    text = formatBytes(value),
                    trailing = if (value == currentBytes) {
                        { Icon(Icons.Rounded.Check, null, tint = DesktopColors.Accent, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb >= 1024 -> "%.1f GB".format(java.util.Locale.ROOT, mb / 1024)
        mb >= 1 -> "%.0f MB".format(java.util.Locale.ROOT, mb)
        else -> "%.0f KB".format(java.util.Locale.ROOT, bytes / 1024.0)
    }
}

@Composable
private fun CrossfadeDurationSelector(currentMs: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = CrossfadePolicy.normalizeDurationMs(currentMs)
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(formatSeconds(current), color = DesktopColors.TextPrimary)
            Icon(Icons.Rounded.ArrowDropDown, null, tint = DesktopColors.TextGray)
        }
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }, width = 168.dp) {
            CrossfadeDurationOptionsMs.forEach { value ->
                SimpleMenuItem(
                    text = formatSeconds(value),
                    trailing = if (value == current) {
                        { Icon(Icons.Rounded.Check, null, tint = DesktopColors.Accent, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

private fun formatSeconds(ms: Int): String = if (ms % 1000 == 0) "${ms / 1000} 秒" else "${ms / 1000.0} 秒"

@Composable
private fun QualitySelector(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(QualityOptions.firstOrNull { it.first == current }?.second ?: current, color = DesktopColors.TextPrimary)
            Icon(Icons.Rounded.ArrowDropDown, null, tint = DesktopColors.TextGray)
        }
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }, width = 168.dp) {
            QualityOptions.forEach { (value, label) ->
                SimpleMenuItem(
                    text = label,
                    trailing = if (value == current) {
                        { Icon(Icons.Rounded.Check, null, tint = DesktopColors.Accent, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

@Composable
private fun CloseOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = DesktopColors.Accent, unselectedColor = DesktopColors.TextGray)
        )
        Text(label, color = DesktopColors.TextPrimary, fontSize = 14.sp)
    }
}

@Composable
private fun AutoStartRow() {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    val autoStart = remember { GlobalContext.get().get<AutoStartManager>() }
    // null 表示尚未读到自启状态
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        enabled = withContext(Dispatchers.IO) { autoStart.isEnabled() }
    }
    SettingRow(
        title = "开机自动启动",
        subtitle = if (autoStart.isSupported) null else "仅安装版可用"
    ) {
        SettingSwitch(
            checked = enabled == true,
            enabled = autoStart.isSupported && enabled != null
        ) { target ->
            scope.launch {
                val ok = withContext(Dispatchers.IO) { autoStart.setEnabled(target) }
                if (ok) enabled = target else navigator.showMessage("修改开机启动失败")
            }
        }
    }
}

@Composable
private fun HotkeyEditor(
    hotkeyMap: Map<HotkeyAction, HotkeyCombo?>,
    failed: Set<HotkeyAction>,
    hotkeys: GlobalHotkeyService,
    onSave: (Map<HotkeyAction, HotkeyCombo?>) -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    var recording by remember { mutableStateOf<HotkeyAction?>(null) }

    // 离开页面时若仍在录制，恢复热键
    DisposableEffect(Unit) {
        onDispose { if (recording != null) hotkeys.resume() }
    }

    val stopRecording = {
        if (recording != null) {
            recording = null
            hotkeys.resume()
        }
    }

    HotkeyAction.entries.forEach { action ->
        val combo = hotkeyMap[action]
        val isFailed = combo != null && action in failed
        SettingRow(
            title = action.label,
            subtitle = if (isFailed) "已被其他程序占用，未生效" else null,
            subtitleColor = DesktopColors.Accent
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HotkeyRecorder(
                    combo = combo,
                    isRecording = recording == action,
                    hotkeys = hotkeys,
                    onStart = {
                        if (recording == null) hotkeys.pause()
                        recording = action
                    },
                    onCancel = stopRecording,
                    onRecorded = { newCombo ->
                        val updated = hotkeyMap.toMutableMap()
                        // 与其他动作重复时，从原动作上移除
                        val conflict = updated.entries.firstOrNull { it.key != action && it.value == newCombo }?.key
                        if (conflict != null) {
                            updated[conflict] = null
                            navigator.showMessage("${newCombo.label} 已从「${conflict.label}」移除")
                        }
                        updated[action] = newCombo
                        onSave(updated)
                        stopRecording()
                    },
                    onInvalid = navigator.showMessage
                )
                IconButton(
                    onClick = { onSave(hotkeyMap + (action to null)) },
                    enabled = combo != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        "清除",
                        tint = if (combo != null) DesktopColors.TextGray else DesktopColors.SurfaceLight,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { onSave(HotkeyCombo.defaults) }) {
            Text("恢复默认", color = DesktopColors.TextPrimary)
        }
    }
}

@Composable
private fun HotkeyRecorder(
    combo: HotkeyCombo?,
    isRecording: Boolean,
    hotkeys: GlobalHotkeyService,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRecorded: (HotkeyCombo) -> Unit,
    onInvalid: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    HoldTextInputFocus(isRecording)
    LaunchedEffect(isRecording) {
        if (isRecording) focusRequester.requestFocus()
    }
    Box(
        Modifier.widthIn(min = 160.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(DesktopColors.Pane)
            .border(1.dp, if (isRecording) DesktopColors.Accent else DesktopColors.SurfaceLight, RoundedCornerShape(4.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { if (!it.isFocused && isRecording) onCancel() }
            .onPreviewKeyEvent { event ->
                if (!isRecording || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val awtCode = (event.nativeKeyEvent as? AwtKeyEvent)?.keyCode ?: return@onPreviewKeyEvent true
                when (awtCode) {
                    AwtKeyEvent.VK_ESCAPE -> onCancel()
                    AwtKeyEvent.VK_CONTROL, AwtKeyEvent.VK_ALT, AwtKeyEvent.VK_SHIFT, AwtKeyEvent.VK_WINDOWS,
                    AwtKeyEvent.VK_META, AwtKeyEvent.VK_ALT_GRAPH -> Unit
                    else -> {
                        // 键码转换属平台细节，交由热键服务处理
                        val combo = hotkeys.toHotkeyCombo(
                            awtKeyCode = awtCode,
                            ctrl = event.isCtrlPressed,
                            alt = event.isAltPressed,
                            shift = event.isShiftPressed,
                        )
                        when {
                            combo == null -> onInvalid("不支持该按键")
                            !HotkeyCombo.isValid(combo.modifiers, combo.vk) -> onInvalid("快捷键需要包含 Ctrl 或 Alt")
                            else -> onRecorded(combo)
                        }
                    }
                }
                true
            }
            .focusable()
            .clickable(onClick = onStart)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            when {
                isRecording -> "请按下组合键…"
                combo != null -> combo.label
                else -> "未设置"
            },
            color = when {
                isRecording -> DesktopColors.Accent
                combo != null -> DesktopColors.TextPrimary
                else -> DesktopColors.TextGray
            },
            fontSize = 14.sp
        )
    }
}

@Composable
private fun DesktopServerUrlInput(
    currentUrl: String,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var text by remember(currentUrl, showDialog) { mutableStateOf(currentUrl) }

    OutlinedButton(
        onClick = { showDialog = true },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DesktopColors.TextPrimary)
    ) {
        Text("配置接口", fontSize = 12.sp)
    }

    if (showDialog) {
        DesktopDialog(
            title = "配置换源服务接口",
            onDismiss = { showDialog = false },
            width = 440.dp,
            actions = {
                DialogButton("清空", {
                    text = ""
                    onReset()
                    showDialog = false
                }, danger = true)
                DialogButton("取消", { showDialog = false })
                DialogButton("保存", {
                    onSave(text.trim())
                    showDialog = false
                }, primary = true)
            }
        ) {
            Text(
                text = "填写 UNM Utils 服务的基础访问 URL（末尾无需斜杠）：",
                color = DesktopColors.TextGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            DialogTextField(value = text, onValueChange = { text = it }, placeholder = "例如 https://your-unm-server.com")
        }
    }
}
