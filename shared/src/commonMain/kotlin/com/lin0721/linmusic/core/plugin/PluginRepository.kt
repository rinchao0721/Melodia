package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "PluginRepository"

// 插件安装、启停与运行时同步；任何改动后都重新读取列表并让运行时跟上
class PluginRepository(
    private val store: PluginStore,
    private val runtime: PluginRuntime,
    private val api: PluginHostApi,
    private val events: PluginEventDispatcher? = null,
    private val native: NativePluginHost? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    // 传入时启动后检查目录更新并提示
    private val notices: PluginNotices? = null,
    val catalog: PluginCatalogService = PluginCatalogService(store)
) {
    private val refreshLock = Mutex()
    private val publishLock = Mutex()
    private var nativeSynced = false
    private val registry = runtime.registry
    private val _installed = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installed: StateFlow<List<InstalledPlugin>> = _installed.asStateFlow()
    val status: StateFlow<Map<String, PluginRuntimeStatus>> = native?.let { host ->
        combine(runtime.status, host.status) { script, loaded -> script + loaded }
            .stateIn(scope, SharingStarted.Eagerly, runtime.status.value + host.status.value)
    } ?: runtime.status

    // Android 没有原生层
    val nativeSupported: Boolean = native != null
    private val _nativeAllowed = MutableStateFlow(false)
    val nativeAllowed: StateFlow<Boolean> = _nativeAllowed.asStateFlow()

    private val _layouts = MutableStateFlow(PlayerLayoutState())
    // 各区域生效的插件布局、候选插件与用户的选择
    val layouts: StateFlow<PlayerLayoutState> = _layouts.asStateFlow()

    private val _pages = MutableStateFlow<List<ActivePluginPage>>(emptyList())
    // 已启用插件提供的页面
    val pages: StateFlow<List<ActivePluginPage>> = _pages.asStateFlow()

    private val _backdrops = MutableStateFlow(BackdropState())
    // 已启用插件提供的动态背景；目前只有桌面端接入
    val backdrops: StateFlow<BackdropState> = _backdrops.asStateFlow()

    init {
        runtime.setFailureHandler(::disableForErrors)
        native?.setFailureHandler(::disableForErrors)
    }

    // 启动时调用一次，拉起已启用插件并开始分发播放事件
    suspend fun warmUp() {
        _nativeAllowed.value = withContext(Dispatchers.IO) { store.nativeAllowed() }
        refresh()
        events?.start()
        if (notices != null) scope.launch { announceUpdates(notices) }
    }

    private suspend fun announceUpdates(notices: PluginNotices) {
        val updates = pluginUpdates(_installed.value, catalog.entriesForStartup())
        if (updates.isNotEmpty()) notices.post("有 ${updates.size} 个插件可以更新，到插件设置里查看")
    }

    suspend fun refresh() = refreshLock.withLock {
        val plugins = publishLatest()
        runtime.sync(plugins)
        // 启动时没有原生插件也要同步一次，之后导入的才会按约定等重启
        if (native != null && (!nativeSynced || plugins.any { it.manifest?.kind == PluginKind.NATIVE })) {
            val allowed = withContext(Dispatchers.IO) { store.nativeAllowed() }
            _nativeAllowed.value = allowed
            native.sync(plugins, allowed)
            nativeSynced = true
            // 页面和菜单要等原生插件加载后才生效
            publishLatest()
        }
    }

    // 同步途中可能有插件被自动停用，每次都读最新状态；refresh 持锁期间也会走到这里，所以用单独的锁
    private suspend fun publishLatest(): List<InstalledPlugin> = publishLock.withLock {
        withContext(Dispatchers.IO) { store.list() }.also { publish(it) }
    }

    // 沙盒插件随状态生效；原生插件只有本次启动加载成功的才算
    private fun isActive(plugin: InstalledPlugin): Boolean =
        if (plugin.manifest?.kind == PluginKind.NATIVE) native?.isActive(plugin.id) == true else plugin.state.enabled && plugin.canRun

    private suspend fun publish(plugins: List<InstalledPlugin>) {
        _installed.value = plugins
        withContext(Dispatchers.IO) {
            registry.replace(ExtensionPoints.PLAYER_LAYOUT, layoutExtensions(plugins))
            registry.replace(ExtensionPoints.PAGE, pageExtensions(plugins))
            registry.replace(ExtensionPoints.MENU, menuExtensions(plugins))
            _layouts.value = resolveLayouts()
            _pages.value = registry.get(ExtensionPoints.PAGE).map { it.value }
            _backdrops.value = resolveBackdrops(plugins)
        }
    }

    // 图片序列随状态生效；逐帧渲染要等原生插件注册后才可用
    private fun resolveBackdrops(plugins: List<InstalledPlugin>): BackdropState {
        val renderers = registry.get(ExtensionPoints.BACKDROP)
        val candidates = plugins.filter(::isActive).flatMap { plugin ->
            val manifest = plugin.manifest ?: return@flatMap emptyList()
            manifest.contributes.backdrops.mapNotNull { entry ->
                val renderer = renderers.firstOrNull { it.pluginId == manifest.id && it.localId == entry.id }?.value
                val frames = entry.frames.mapNotNull(manifest::file)
                if (renderer == null && frames.isEmpty()) return@mapNotNull null
                ActiveBackdrop("${manifest.id}/${entry.id}", manifest.name, entry, renderer, frames) to plugin.state.enabledAt
            }
        }
        val choice = store.layoutChoices()[BACKDROP_CHOICE_KEY]
        val automatic = { candidates.maxByOrNull { it.second }?.first }
        val active = when (choice) {
            LAYOUT_CHOICE_DEFAULT -> null
            null -> automatic()
            else -> candidates.firstOrNull { it.first.key == choice }?.first ?: automatic()
        }
        return BackdropState(active, candidates.map { it.first }, choice)
    }

    // choice 为背景 key、LAYOUT_CHOICE_DEFAULT（不使用），或 null 表示按最近启用的插件
    suspend fun chooseBackdrop(choice: String?) {
        withContext(Dispatchers.IO) { store.setLayoutChoice(BACKDROP_CHOICE_KEY, choice) }
        refresh()
    }

    private fun menuExtensions(plugins: List<InstalledPlugin>): List<Extension<PluginMenuEntry>> =
        plugins.filter(::isActive).flatMap { plugin ->
            val manifest = plugin.manifest ?: return@flatMap emptyList()
            manifest.contributes.menus.mapIndexed { index, menu ->
                Extension(ExtensionPoints.MENU, manifest.id, manifest.name, "${menu.location.key}/${menu.commandId}", menu, index)
            }
        }

    private fun pageExtensions(plugins: List<InstalledPlugin>): List<Extension<ActivePluginPage>> =
        plugins.filter(::isActive).flatMap { plugin ->
            val manifest = plugin.manifest ?: return@flatMap emptyList()
            manifest.contributes.pages.mapIndexed { index, page ->
                val active = if (page.compose) {
                    ActivePluginPage(manifest, page, null, null)
                } else {
                    when (val result = PlayerLayoutParser.loadPage(manifest, page)) {
                        is PlayerLayoutParseResult.Success -> ActivePluginPage(manifest, page, result.root, null)
                        is PlayerLayoutParseResult.Failure -> ActivePluginPage(manifest, page, null, result.reason)
                    }
                }
                Extension(ExtensionPoints.PAGE, manifest.id, manifest.name, page.id, active, index)
            }
        }

    private fun layoutExtensions(plugins: List<InstalledPlugin>): List<Extension<PlayerLayoutExtension>> =
        PlayerLayoutArea.entries.flatMap { area ->
            plugins
                .filter { isActive(it) && it.manifest?.contributes?.playerLayouts?.containsKey(area.key) == true }
                .mapNotNull { plugin ->
                    when (val result = PlayerLayoutParser.load(plugin.manifest!!, area)) {
                        is PlayerLayoutParseResult.Success ->
                            Extension(ExtensionPoints.PLAYER_LAYOUT, plugin.id, plugin.name, area.key, PlayerLayoutExtension(area, plugin, result.spec.copy(pluginId = plugin.id)))
                        is PlayerLayoutParseResult.Failure -> {
                            AppLogger.w(TAG, "插件 ${plugin.id} 的 ${area.key} 布局无效: ${result.reason}")
                            null
                        }
                    }
                }
        }

    fun composePage(pluginId: String, pageId: String): ComposePageExtension? =
        registry.get(ExtensionPoints.COMPOSE_PAGE).firstOrNull { it.pluginId == pluginId && it.localId == pageId }?.value

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
            chosen?.let { active[area] = ActivePlayerLayout(it.plugin.id, it.plugin.name, it.spec) }
        }
        return PlayerLayoutState(active, candidates, choices.mapNotNull { (k, v) -> PlayerLayoutArea.fromKey(k)?.let { it to v } }.toMap())
    }

    // 调用方负责打开输入流，这里读完即关闭
    suspend fun stage(input: InputStream, sourceName: String? = null): PluginStageResult = withContext(Dispatchers.IO) {
        val result = input.use { store.stage(it, sourceName) }
        val staged = (result as? PluginStageResult.Success)?.staged
        if (staged?.manifest?.kind == PluginKind.NATIVE && !store.nativeAllowed()) {
            store.discard(staged)
            PluginStageResult.Failure("这是原生插件，请先开启「允许原生插件」")
        } else {
            result
        }
    }

    // 关闭后立即卸载已加载的原生插件；开启后要重启才加载
    suspend fun setNativeAllowed(allowed: Boolean) {
        withContext(Dispatchers.IO) { store.setNativeAllowed(allowed) }
        _nativeAllowed.value = allowed
        refresh()
    }

    suspend fun discard(staged: StagedPlugin) = withContext(Dispatchers.IO) { store.discard(staged) }

    suspend fun installedVersion(id: String): InstalledPlugin? = withContext(Dispatchers.IO) { store.installed(id) }

    // 安装即视为用户确认了清单里的全部权限，并直接启用
    suspend fun install(staged: StagedPlugin): InstalledPlugin? {
        runtime.stop(staged.manifest.id)
        native?.unload(staged.manifest.id)
        val plugin = withContext(Dispatchers.IO) {
            // 用户手动停用的插件升级后保持停用；因出错被自动停用的，新版本可以再试
            val state = store.installed(staged.manifest.id)?.state
            store.install(staged, enabled = state == null || state.enabled || state.disabledReason != null)
        }
        refresh()
        return plugin
    }

    // 没有可回退的版本或替换失败时返回 null
    suspend fun rollback(id: String): InstalledPlugin? {
        runtime.stop(id)
        native?.unload(id)
        val plugin = withContext(Dispatchers.IO) { store.rollback(id) }
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
        native?.unload(id)
        api.clearUiState(id)
        runtime.clearData(id)
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
        publishLatest()
    }
}

private const val BACKDROP_CHOICE_KEY = "backdrop"

// renderer 与 frames 至少有一个；同时有时优先逐帧渲染
data class ActiveBackdrop(
    val key: String,
    val pluginName: String,
    val entry: PluginBackdropEntry,
    val renderer: BackdropExtension?,
    val frames: List<File>
)

data class BackdropState(
    val active: ActiveBackdrop? = null,
    val candidates: List<ActiveBackdrop> = emptyList(),
    val choice: String? = null
)

// root 为 null 时页面描述无效，error 为原因
data class ActivePluginPage(
    val manifest: PluginManifest,
    val page: PluginPage,
    val root: PlayerLayoutNode?,
    val error: String?
)

data class ActivePlayerLayout(val pluginId: String, val pluginName: String, val spec: PlayerLayoutSpec)

data class PlayerLayoutState(
    val active: Map<PlayerLayoutArea, ActivePlayerLayout> = emptyMap(),
    // 提供了有效布局的已启用插件
    val candidates: Map<PlayerLayoutArea, List<InstalledPlugin>> = emptyMap(),
    val choices: Map<PlayerLayoutArea, String> = emptyMap()
) {
    val specs: Map<PlayerLayoutArea, PlayerLayoutSpec> get() = active.mapValues { it.value.spec }
}
