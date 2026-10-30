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
    private val registry = runtime.registry
    private val _installed = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installed: StateFlow<List<InstalledPlugin>> = _installed.asStateFlow()
    val status: StateFlow<Map<String, PluginRuntimeStatus>> = runtime.status

    private val _layouts = MutableStateFlow(PlayerLayoutState())
    // 各区域生效的插件布局、候选插件与用户的选择
    val layouts: StateFlow<PlayerLayoutState> = _layouts.asStateFlow()

    private val _pages = MutableStateFlow<List<ActivePluginPage>>(emptyList())
    // 已启用插件提供的页面
    val pages: StateFlow<List<ActivePluginPage>> = _pages.asStateFlow()

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
        publish(plugins)
        runtime.sync(plugins)
    }

    private suspend fun publish(plugins: List<InstalledPlugin>) {
        _installed.value = plugins
        withContext(Dispatchers.IO) {
            registry.replace(ExtensionPoints.PLAYER_LAYOUT, layoutExtensions(plugins))
            registry.replace(ExtensionPoints.PAGE, pageExtensions(plugins))
            registry.replace(ExtensionPoints.MENU, menuExtensions(plugins))
            _layouts.value = resolveLayouts()
            _pages.value = registry.get(ExtensionPoints.PAGE).map { it.value }
        }
    }

    private fun menuExtensions(plugins: List<InstalledPlugin>): List<Extension<PluginMenuEntry>> =
        plugins.filter { it.state.enabled && it.canRun }.flatMap { plugin ->
            val manifest = plugin.manifest ?: return@flatMap emptyList()
            manifest.contributes.menus.mapIndexed { index, menu ->
                Extension(ExtensionPoints.MENU, manifest.id, manifest.name, "${menu.location.key}/${menu.commandId}", menu, index)
            }
        }

    private fun pageExtensions(plugins: List<InstalledPlugin>): List<Extension<ActivePluginPage>> =
        plugins.filter { it.state.enabled && it.canRun }.flatMap { plugin ->
            val manifest = plugin.manifest ?: return@flatMap emptyList()
            manifest.contributes.pages.mapIndexed { index, page ->
                val active = when (val result = PlayerLayoutParser.loadPage(manifest, page)) {
                    is PlayerLayoutParseResult.Success -> ActivePluginPage(manifest, page, result.root, null)
                    is PlayerLayoutParseResult.Failure -> ActivePluginPage(manifest, page, null, result.reason)
                }
                Extension(ExtensionPoints.PAGE, manifest.id, manifest.name, page.id, active, index)
            }
        }

    private fun layoutExtensions(plugins: List<InstalledPlugin>): List<Extension<PlayerLayoutExtension>> =
        PlayerLayoutArea.entries.flatMap { area ->
            plugins
                .filter { it.state.enabled && it.canRun && it.manifest?.contributes?.playerLayouts?.containsKey(area.key) == true }
                .mapNotNull { plugin ->
                    when (val result = PlayerLayoutParser.load(plugin.manifest!!, area)) {
                        is PlayerLayoutParseResult.Success ->
                            Extension(ExtensionPoints.PLAYER_LAYOUT, plugin.id, plugin.name, area.key, PlayerLayoutExtension(area, plugin, result.root))
                        is PlayerLayoutParseResult.Failure -> {
                            AppLogger.w(TAG, "插件 ${plugin.id} 的 ${area.key} 布局无效: ${result.reason}")
                            null
                        }
                    }
                }
        }

    // choice 为插件 id、LAYOUT_CHOICE_DEFAULT，或 null 表示按最近启用的插件
    suspend fun chooseLayout(area: PlayerLayoutArea, choice: String?) {
        withContext(Dispatchers.IO) { store.setLayoutChoice(area.key, choice) }
        refresh()
    }

    private fun resolveLayouts(): PlayerLayoutState {
        val choices = store.layoutChoices()
        val active = mutableMapOf<PlayerLayoutArea, ActivePlayerLayout>()
        val candidates = mutableMapOf<PlayerLayoutArea, List<InstalledPlugin>>()
        val extensions = registry.get(ExtensionPoints.PLAYER_LAYOUT).map { it.value }.groupBy { it.area }
        for ((area, valid) in extensions) {
            candidates[area] = valid.map { it.plugin }
            val chosen = when (val choice = choices[area.key]) {
                LAYOUT_CHOICE_DEFAULT -> null
                null -> valid.maxByOrNull { it.plugin.state.enabledAt }
                else -> valid.firstOrNull { it.plugin.id == choice } ?: valid.maxByOrNull { it.plugin.state.enabledAt }
            }
            chosen?.let { active[area] = ActivePlayerLayout(it.plugin.id, it.plugin.name, it.root) }
        }
        return PlayerLayoutState(active, candidates, choices.mapNotNull { (k, v) -> PlayerLayoutArea.fromKey(k)?.let { it to v } }.toMap())
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
        withContext(Dispatchers.IO) {
            store.updateState(id) {
                it.copy(enabled = enabled, disabledReason = null, enabledAt = if (enabled) System.currentTimeMillis() else it.enabledAt)
            }
        }
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
        publish(withContext(Dispatchers.IO) { store.list() })
    }
}

// root 为 null 时页面描述无效，error 为原因
data class ActivePluginPage(
    val manifest: PluginManifest,
    val page: PluginPage,
    val root: PlayerLayoutNode?,
    val error: String?
)

data class ActivePlayerLayout(val pluginId: String, val pluginName: String, val root: PlayerLayoutNode)

data class PlayerLayoutState(
    val active: Map<PlayerLayoutArea, ActivePlayerLayout> = emptyMap(),
    // 提供了有效布局的已启用插件
    val candidates: Map<PlayerLayoutArea, List<InstalledPlugin>> = emptyMap(),
    val choices: Map<PlayerLayoutArea, String> = emptyMap()
) {
    val roots: Map<PlayerLayoutArea, PlayerLayoutNode> get() = active.mapValues { it.value.root }
}
