package com.lin0721.linmusic.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "PluginSettings"

sealed interface PluginImportStep {
    data object Idle : PluginImportStep
    data object Working : PluginImportStep

    // existing 为已安装的同 id 插件，此时确认即升级；unreviewed 表示来自自定义目录
    data class Preview(val staged: StagedPlugin, val existing: InstalledPlugin?, val unreviewed: Boolean = false) : PluginImportStep {
        // 升级时比已确认的多出来的权限，需要重点提示
        val newPermissions: Set<PluginPermission>
            get() = existing?.let { old -> staged.manifest.permissions.filterTo(LinkedHashSet()) { it.key !in old.state.granted } }
                ?: staged.manifest.permissions
    }

    data class Failed(val message: String) : PluginImportStep
}

// 双端插件设置共用的流程：导入先解包预览权限，确认后安装并启用；取消时清理临时目录
@Stable
class PluginSettingsController(
    private val repository: PluginRepository,
    private val scope: CoroutineScope
) {
    val installed: StateFlow<List<InstalledPlugin>> = repository.installed
    val status: StateFlow<Map<String, PluginRuntimeStatus>> = repository.status
    val layouts: StateFlow<PlayerLayoutState> = repository.layouts
    val pages: StateFlow<List<ActivePluginPage>> = repository.pages

    var step: PluginImportStep by mutableStateOf(PluginImportStep.Idle)
        private set

    // 导出等操作的结果提示；null 表示无提示
    var message: String? by mutableStateOf(null)
        private set

    fun refresh() {
        scope.launch { repository.refresh() }
    }

    val backdrops: StateFlow<BackdropState> = repository.backdrops

    fun chooseBackdrop(choice: String?) {
        scope.launch { repository.chooseBackdrop(choice) }
    }

    val nativeSupported: Boolean = repository.nativeSupported
    val nativeAllowed: StateFlow<Boolean> = repository.nativeAllowed

    fun setNativeAllowed(allowed: Boolean) {
        scope.launch { repository.setNativeAllowed(allowed) }
    }

    // open 在 IO 线程调用，返回 null 视为用户取消；sourceName 在 open 成功后读取
    fun import(sourceName: () -> String? = { null }, open: suspend () -> InputStream?) {
        if (step == PluginImportStep.Working) return
        scope.launch {
            step = PluginImportStep.Working
            val input = try {
                withContext(Dispatchers.IO) { open() }
            } catch (e: IOException) {
                AppLogger.w(TAG, "打开插件文件失败", e)
                step = PluginImportStep.Failed("无法打开所选文件")
                return@launch
            } catch (e: SecurityException) {
                AppLogger.w(TAG, "没有读取插件文件的权限", e)
                step = PluginImportStep.Failed("没有读取所选文件的权限")
                return@launch
            }
            if (input == null) {
                step = PluginImportStep.Idle
                return@launch
            }
            step = when (val result = repository.stage(input, sourceName())) {
                is PluginStageResult.Failure -> PluginImportStep.Failed(result.reason)
                is PluginStageResult.Success -> PluginImportStep.Preview(
                    result.staged,
                    repository.installedVersion(result.staged.manifest.id)
                )
            }
        }
    }

    val catalog: StateFlow<PluginCatalogState> = repository.catalog.state

    // 下载进度 0–1，没有在下载时为 null
    var downloadProgress: Float? by mutableStateOf(null)
        private set

    // 正在从目录下载的插件 id
    var downloadingId: String? by mutableStateOf(null)
        private set

    fun loadCatalog() {
        scope.launch { repository.catalog.load() }
    }

    fun setCatalogUrl(url: String?) {
        scope.launch { repository.catalog.setUrl(url)?.let { message = it } }
    }

    fun installFromCatalog(entry: PluginCatalogEntry) {
        if (step == PluginImportStep.Working) return
        if (entry.isNative && !repository.nativeAllowed.value) {
            step = PluginImportStep.Failed("这是原生插件，请先开启「允许原生插件」")
            return
        }
        scope.launch {
            step = PluginImportStep.Working
            val unreviewed = repository.catalog.isCustom()
            downloadProgress = 0f
            downloadingId = entry.id
            val file = try {
                repository.catalog.download(entry) { done -> downloadProgress = done.toFloat() / entry.download.size }
            } catch (e: PluginCatalogException) {
                step = PluginImportStep.Failed(e.message.orEmpty())
                return@launch
            } finally {
                downloadProgress = null
                downloadingId = null
            }
            val result = try {
                repository.stage(withContext(Dispatchers.IO) { file.inputStream() }, entry.download.url.substringAfterLast('/'))
            } catch (e: IOException) {
                AppLogger.w(TAG, "打开下载的插件包失败", e)
                PluginStageResult.Failure("无法读取下载的插件包")
            } finally {
                withContext(Dispatchers.IO) { file.delete() }
            }
            step = when (result) {
                is PluginStageResult.Failure -> PluginImportStep.Failed(result.reason)
                is PluginStageResult.Success -> {
                    val mismatch = pluginCatalogMismatch(entry, result.staged.manifest)
                    if (mismatch != null) {
                        repository.discard(result.staged)
                        PluginImportStep.Failed(mismatch)
                    } else {
                        PluginImportStep.Preview(result.staged, repository.installedVersion(entry.id), unreviewed)
                    }
                }
            }
        }
    }

    fun rollback(plugin: InstalledPlugin) {
        scope.launch {
            val restored = repository.rollback(plugin.id)
            val restart = if (restored?.manifest?.kind == PluginKind.NATIVE) "，重启后生效" else ""
            message = if (restored != null) "已回退到 v${restored.manifest?.version ?: plugin.previousVersion}$restart" else "回退失败"
        }
    }

    fun confirmImport() {
        val preview = step as? PluginImportStep.Preview ?: return
        scope.launch {
            step = PluginImportStep.Working
            val plugin = repository.install(preview.staged)
            if (plugin == null) {
                step = PluginImportStep.Failed("安装插件失败")
            } else {
                step = PluginImportStep.Idle
                val restart = if (plugin.manifest?.kind == PluginKind.NATIVE) "，重启后生效" else ""
                message = (if (preview.existing != null) "已升级「${plugin.name}」" else "已安装并启用「${plugin.name}」") + restart
            }
        }
    }

    fun cancelImport() {
        val preview = step as? PluginImportStep.Preview
        step = PluginImportStep.Idle
        if (preview != null) scope.launch { repository.discard(preview.staged) }
    }

    fun dismissFailure() {
        if (step is PluginImportStep.Failed) step = PluginImportStep.Idle
    }

    fun setEnabled(id: String, enabled: Boolean) {
        scope.launch { repository.setEnabled(id, enabled) }
    }

    fun delete(id: String) {
        scope.launch { repository.delete(id) }
    }

    fun chooseLayout(area: PlayerLayoutArea, choice: String?) {
        scope.launch { repository.chooseLayout(area, choice) }
    }

    // open 在 IO 线程调用，返回 null 视为用户取消
    fun export(plugin: InstalledPlugin, open: suspend () -> OutputStream?) {
        scope.launch {
            val output = try {
                withContext(Dispatchers.IO) { open() }
            } catch (e: IOException) {
                AppLogger.w(TAG, "创建导出文件失败", e)
                null.also { message = "无法写入所选位置" }
            } catch (e: SecurityException) {
                AppLogger.w(TAG, "没有写入导出文件的权限", e)
                null.also { message = "没有写入所选位置的权限" }
            } ?: return@launch
            message = if (repository.export(plugin.id, output)) "已导出「${plugin.name}」" else "导出失败"
        }
    }

    fun dismissMessage() {
        message = null
    }
}

@Composable
fun rememberPluginSettingsController(repository: PluginRepository): PluginSettingsController {
    val scope = rememberCoroutineScope()
    return remember(repository, scope) { PluginSettingsController(repository, scope) }
}

fun pluginExportFileName(plugin: InstalledPlugin): String = "${plugin.id}.$PLUGIN_FILE_EXTENSION"

// 导入预览里的高风险提示
fun pluginRiskWarning(manifest: PluginManifest): String? = when {
    manifest.kind == PluginKind.NATIVE -> "这是原生插件，等同于运行任意程序：上面的权限只是声明，无法真正限制它。只安装你信任的插件"
    PluginPermission.NETEASE_WRITE in manifest.permissions -> "这个插件可以修改你的网易云账号，只安装你信任的插件"
    else -> null
}

// 列表与预览里的能力摘要
fun pluginFeatureSummary(manifest: PluginManifest): String {
    val contributes = manifest.contributes
    return buildList {
        if (contributes.playerLayouts.isNotEmpty()) add("播放器布局 ${contributes.playerLayouts.size} 处")
        if (contributes.pages.isNotEmpty()) add("页面 ${contributes.pages.size} 个")
        if (contributes.audioSources.isNotEmpty()) add("音源 ${contributes.audioSources.size} 个")
        if (contributes.lyricsSources.isNotEmpty()) add("歌词源 ${contributes.lyricsSources.size} 个")
        if (contributes.events.isNotEmpty()) add("播放事件")
        if (contributes.commands.isNotEmpty()) add("命令 ${contributes.commands.size} 个")
        if (contributes.settings.isNotEmpty()) add("设置 ${contributes.settings.size} 项")
        if (contributes.contentSources.isNotEmpty()) add("内容源 ${contributes.contentSources.size} 个")
        if (contributes.backdrops.isNotEmpty()) add("动态背景 ${contributes.backdrops.size} 个")
    }.joinToString("、")
}


data class PluginStatusLine(val text: String, val isError: Boolean)

// 插件列表里的状态说明
fun pluginStatusLine(plugin: InstalledPlugin, status: PluginRuntimeStatus?): PluginStatusLine {
    val manifest = plugin.manifest
    return when {
        manifest == null || plugin.loadError != null -> PluginStatusLine("无法加载：${plugin.loadError ?: "清单无效"}", true)
        plugin.missingPermissions.isNotEmpty() -> PluginStatusLine("有未确认的权限，重新导入插件后才能启用", true)
        !plugin.state.enabled -> PluginStatusLine(plugin.state.disabledReason ?: "已停用", plugin.state.disabledReason != null)
        manifest.kind == PluginKind.NATIVE && status == null -> PluginStatusLine("重启后生效", false)
        manifest.kind == PluginKind.SANDBOX && !manifest.contributes.needsScript -> PluginStatusLine("已启用", false)
        status?.running == true && status.lastError != null -> PluginStatusLine("运行中，最近一次出错：${status.lastError}", true)
        status?.running == true -> PluginStatusLine("运行中", false)
        status?.lastError != null -> PluginStatusLine(status.lastError, true)
        else -> PluginStatusLine("正在启动", false)
    }
}

// 布局来源选项；choice 为 null 表示自动选最近启用的插件
data class PlayerLayoutOption(val choice: String?, val label: String)

fun backdropOptions(state: BackdropState): List<PlayerLayoutOption> =
    listOf(PlayerLayoutOption(null, "自动（最近启用的插件）"), PlayerLayoutOption(LAYOUT_CHOICE_DEFAULT, "不使用")) +
        state.candidates.map { PlayerLayoutOption(it.key, "${it.pluginName} · ${it.entry.name}") }

fun currentBackdropOption(state: BackdropState): PlayerLayoutOption {
    val options = backdropOptions(state)
    return options.firstOrNull { it.choice != null && it.choice == state.choice } ?: options.first()
}

// 本端有插件布局候选的区域，platformPrefix 为 android. 或 desktop.
fun layoutAreasWithCandidates(state: PlayerLayoutState, platformPrefix: String): List<PlayerLayoutArea> =
    PlayerLayoutArea.entries.filter { it.key.startsWith(platformPrefix) && !state.candidates[it].isNullOrEmpty() }

fun playerLayoutOptions(area: PlayerLayoutArea, state: PlayerLayoutState): List<PlayerLayoutOption> =
    listOf(PlayerLayoutOption(null, "自动（最近启用的插件）"), PlayerLayoutOption(LAYOUT_CHOICE_DEFAULT, "内置布局")) +
        state.candidates[area].orEmpty().map { PlayerLayoutOption(it.id, it.name) }

fun currentPlayerLayoutOption(area: PlayerLayoutArea, state: PlayerLayoutState): PlayerLayoutOption {
    val options = playerLayoutOptions(area, state)
    return options.firstOrNull { it.choice != null && it.choice == state.choices[area] } ?: options.first()
}

fun playerLayoutSummary(area: PlayerLayoutArea, state: PlayerLayoutState): String =
    state.active[area]?.let { "正在使用「${it.pluginName}」的布局" } ?: "正在使用内置布局"
