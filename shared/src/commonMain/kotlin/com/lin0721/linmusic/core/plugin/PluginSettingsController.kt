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

    // existing 为已安装的同 id 插件，此时确认即升级
    data class Preview(val staged: StagedPlugin, val existing: InstalledPlugin?) : PluginImportStep {
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

    var step: PluginImportStep by mutableStateOf(PluginImportStep.Idle)
        private set

    // 导出等操作的结果提示；null 表示无提示
    var message: String? by mutableStateOf(null)
        private set

    fun refresh() {
        scope.launch { repository.refresh() }
    }

    // open 在 IO 线程调用，返回 null 视为用户取消
    fun import(open: suspend () -> InputStream?) {
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
            step = when (val result = repository.stage(input)) {
                is PluginStageResult.Failure -> PluginImportStep.Failed(result.reason)
                is PluginStageResult.Success -> PluginImportStep.Preview(
                    result.staged,
                    repository.installedVersion(result.staged.manifest.id)
                )
            }
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
                message = if (preview.existing != null) "已升级「${plugin.name}」" else "已安装并启用「${plugin.name}」"
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

// 列表与预览里的能力摘要
fun pluginFeatureSummary(manifest: PluginManifest): String {
    val contributes = manifest.contributes
    return buildList {
        if (contributes.playerLayouts.isNotEmpty()) add("播放器布局 ${contributes.playerLayouts.size} 处")
        if (contributes.pages.isNotEmpty()) add("页面 ${contributes.pages.size} 个")
        if (contributes.audioSources.isNotEmpty()) add("音源 ${contributes.audioSources.size} 个")
        if (contributes.lyricsSources.isNotEmpty()) add("歌词源 ${contributes.lyricsSources.size} 个")
        if (contributes.events.isNotEmpty()) add("播放事件")
    }.joinToString("、")
}

// 后续版本才生效的能力，在列表与预览里提示
fun pluginPendingFeatures(manifest: PluginManifest): String? {
    val contributes = manifest.contributes
    val pending = buildList {
        if (contributes.playerLayouts.isNotEmpty()) add("播放器布局")
        if (contributes.pages.isNotEmpty()) add("页面")
    }
    return pending.takeIf { it.isNotEmpty() }?.joinToString("、", postfix = "将在后续版本生效")
}

data class PluginStatusLine(val text: String, val isError: Boolean)

// 插件列表里的状态说明
fun pluginStatusLine(plugin: InstalledPlugin, status: PluginRuntimeStatus?): PluginStatusLine {
    val manifest = plugin.manifest
    return when {
        manifest == null || plugin.loadError != null -> PluginStatusLine("无法加载：${plugin.loadError ?: "清单无效"}", true)
        plugin.missingPermissions.isNotEmpty() -> PluginStatusLine("有未确认的权限，重新导入插件后才能启用", true)
        !plugin.state.enabled -> PluginStatusLine(plugin.state.disabledReason ?: "已停用", plugin.state.disabledReason != null)
        !manifest.contributes.needsScript -> PluginStatusLine("已启用", false)
        status?.running == true && status.lastError != null -> PluginStatusLine("运行中，最近一次出错：${status.lastError}", true)
        status?.running == true -> PluginStatusLine("运行中", false)
        status?.lastError != null -> PluginStatusLine(status.lastError, true)
        else -> PluginStatusLine("正在启动", false)
    }
}
