package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "PluginRepository"

// 插件安装、启停与运行时同步；任何改动后都重新读取列表并让运行时跟上
class PluginRepository(
    private val store: PluginStore,
    private val runtime: PluginRuntime,
    private val api: PluginHostApi,
    private val events: PluginEventDispatcher? = null
) {
    private val refreshLock = Mutex()
    private val _installed = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installed: StateFlow<List<InstalledPlugin>> = _installed.asStateFlow()
    val status: StateFlow<Map<String, PluginRuntimeStatus>> = runtime.status

    init {
        runtime.setFailureHandler(::disableForErrors)
    }

    // 启动时调用一次，拉起已启用插件并开始分发播放事件
    suspend fun warmUp() {
        refresh()
        events?.start()
    }

    suspend fun refresh() = refreshLock.withLock {
        val plugins = withContext(Dispatchers.IO) { store.list() }
        _installed.value = plugins
        runtime.sync(plugins)
    }

    // 调用方负责打开输入流，这里读完即关闭
    suspend fun stage(input: InputStream): PluginStageResult = withContext(Dispatchers.IO) { input.use { store.stage(it) } }

    suspend fun discard(staged: StagedPlugin) = withContext(Dispatchers.IO) { store.discard(staged) }

    suspend fun installedVersion(id: String): InstalledPlugin? = withContext(Dispatchers.IO) { store.installed(id) }

    // 安装即视为用户确认了清单里的全部权限，并直接启用
    suspend fun install(staged: StagedPlugin): InstalledPlugin? {
        runtime.stop(staged.manifest.id)
        val plugin = withContext(Dispatchers.IO) { store.install(staged, enabled = true) }
        refresh()
        return plugin
    }

    // 重新启用时清掉自动停用的原因；权限未确认的插件不能启用
    suspend fun setEnabled(id: String, enabled: Boolean) {
        val plugin = withContext(Dispatchers.IO) { store.installed(id) } ?: return
        if (enabled && !plugin.canRun) return
        withContext(Dispatchers.IO) { store.updateState(id) { it.copy(enabled = enabled, disabledReason = null) } }
        refresh()
    }

    suspend fun delete(id: String) {
        runtime.stop(id)
        api.clearUiState(id)
        withContext(Dispatchers.IO) { store.delete(id) }
        refresh()
    }

    // 写入失败返回 false
    suspend fun export(id: String, output: OutputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            output.use { store.export(id, it) }
        } catch (e: IOException) {
            AppLogger.w(TAG, "导出插件失败: $id", e)
            false
        }
    }

    private suspend fun disableForErrors(id: String, reason: String) {
        withContext(Dispatchers.IO) { store.updateState(id) { it.copy(enabled = false, disabledReason = reason) } }
        _installed.value = withContext(Dispatchers.IO) { store.list() }
    }
}
