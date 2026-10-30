package com.lin0721.linmusic.feature.source.plugin

import android.os.SystemClock
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.plugin.ScriptSandbox
import com.lin0721.linmusic.core.plugin.ScriptSandboxFactory
import com.lin0721.linmusic.core.source.LxPluginInfo
import com.lin0721.linmusic.core.source.LxPluginItem
import com.lin0721.linmusic.core.source.LxPluginRuntime
import com.lin0721.linmusic.core.source.LxScriptHost
import com.lin0721.linmusic.core.source.LxScriptSupport
import com.lin0721.linmusic.core.source.LxShim
import com.lin0721.linmusic.core.source.SourceHttpClient
import com.lin0721.linmusic.core.source.lxScriptInfoJs
import com.lin0721.linmusic.core.source.sourceHttpFetcher
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "LxPluginEngine"

// 沙盒代理请求的响应体上限，超限视为请求失败
private const val MAX_PROXY_BODY_BYTES = 4L * 1024 * 1024
// 沙盒空闲自动回收时长，避免不换源时常驻 WebView
private const val SANDBOX_IDLE_TIMEOUT_MS = 3 * 60 * 1000L
// 插件激活失败后的冷却时长，避免每次切歌重复拉起失效插件等待超时
private const val ACTIVATE_FAILURE_COOLDOWN_MS = 5 * 60 * 1000L

// LX Music JS 插件沙盒引擎（多沙盒隔离与生命周期管理）
class LxPluginEngine(private val factory: ScriptSandboxFactory) : LxPluginRuntime {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val fetcher = sourceHttpFetcher(MAX_PROXY_BODY_BYTES)

    // 已启用插件定义表，供按需懒加载沙盒读取脚本与预置平台
    private val registeredPlugins = ConcurrentHashMap<String, LxPluginItem>()
    // 活跃插件沙盒池 (pluginId -> PluginSandbox)
    private val activeSandboxes = ConcurrentHashMap<String, PluginSandbox>()
    // 正在初始化的插件沙盒任务，供并发调用复用，避免单个失效插件拖慢已就绪插件的直链解析
    private val activatingJobs = ConcurrentHashMap<String, Deferred<Boolean>>()
    // 最近激活失败的时间戳 (pluginId -> elapsedRealtime)
    private val failedPlugins = ConcurrentHashMap<String, Long>()
    private val nextCallId = AtomicInteger(0)
    private var idleCleanupJob: Job? = null

    // 内部沙盒实体
    private class PluginSandbox(
        val pluginId: String,
        val script: ScriptSandbox,
        var supportedSources: List<String> = emptyList(),
        val pendingResolves: ConcurrentHashMap<Int, CompletableDeferred<String?>> = ConcurrentHashMap()
    ) {
        // 写入脚本元信息并执行脚本，脚本同步抛错时返回失败
        suspend fun runScript(name: String, description: String, version: String, author: String, raw: String): Result<Unit> {
            script.evaluate(lxScriptInfoJs(name, description, version, author)).onFailure { return Result.failure(it) }
            return script.evaluate(raw)
        }
    }

    // 从远程 URL 下载脚本内容
    override suspend fun downloadScript(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val script = SourceHttpClient.get(url)
            if (script.isNotBlank()) {
                Result.success(script)
            } else {
                Result.failure(Exception("脚本内容为空"))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "下载远程脚本失败: $url", e)
            Result.failure(e)
        }
    }

    // 校验并提取脚本信息（使用临时无头沙盒测试运行并监听 inited）
    override suspend fun loadAndVerifyScript(scriptText: String): Result<LxPluginInfo> = withContext(Dispatchers.Main) {
        val tempId = "temp_${System.currentTimeMillis()}"
        var tempSandbox: PluginSandbox? = null
        try {
            val headerInfo = LxScriptSupport.parseScriptHeader(scriptText)
            val initDeferred = CompletableDeferred<List<String>>()

            tempSandbox = createSandboxInstance(
                pluginId = tempId,
                onInited = { sources -> initDeferred.complete(sources) }
            )

            // 注入插件全局元数据并执行脚本
            tempSandbox.runScript(headerInfo.name, headerInfo.description, headerInfo.version, headerInfo.author, scriptText)
                .getOrThrow()

            // 等待 inited 事件触发（最长 8 秒）
            val sources = withTimeoutOrNull(LxScriptSupport.INIT_TIMEOUT_MS) {
                initDeferred.await()
            } ?: emptyList()

            val info = headerInfo.copy(
                sources = sources,
                rawScript = scriptText
            )
            AppLogger.i(TAG, "脚本校验成功: ${info.name} v${info.version}, 支持平台: $sources")
            Result.success(info)
        } catch (e: Exception) {
            AppLogger.e(TAG, "脚本校验失败", e)
            Result.failure(e)
        } finally {
            tempSandbox?.let { destroySandbox(it) }
        }
    }

    // 注册插件定义，实际 WebView 沙盒延后到首次直链解析时按需创建
    override suspend fun activatePlugin(item: LxPluginItem): Boolean = withContext(Dispatchers.Main) {
        deactivatePlugin(item.id)
        registeredPlugins[item.id] = item
        true
    }

    private suspend fun activateInternal(item: LxPluginItem): Boolean {
        var sandbox: PluginSandbox? = null
        return try {
            val initDeferred = CompletableDeferred<List<String>>()
            sandbox = createSandboxInstance(
                pluginId = item.id,
                onInited = { sources -> initDeferred.complete(sources) }
            )

            sandbox.supportedSources = item.sources
            sandbox.runScript(item.name, item.description, item.version, item.author, item.rawScript).getOrThrow()

            // 等待初始化
            val sources = withTimeoutOrNull(LxScriptSupport.INIT_TIMEOUT_MS) {
                initDeferred.await()
            }
            if (sources != null) {
                if (sources.isNotEmpty()) {
                    sandbox.supportedSources = sources
                }
            } else if (sandbox.supportedSources.isEmpty()) {
                // 未能按时触发 inited 且无预置平台列表，视为激活失败并释放沙盒
                destroySandbox(sandbox)
                failedPlugins[item.id] = SystemClock.elapsedRealtime()
                AppLogger.w(TAG, "插件 [${item.name}] 初始化超时且无支持平台, ID=${item.id}")
                return false
            }

            failedPlugins.remove(item.id)
            activeSandboxes[item.id] = sandbox
            scheduleIdleCleanup()
            AppLogger.i(TAG, "插件 [${item.name}] 沙盒激活成功, ID=${item.id}, sources=${sandbox.supportedSources}")
            true
        } catch (e: CancellationException) {
            sandbox?.let { destroySandbox(it) }
            throw e
        } catch (e: Exception) {
            sandbox?.let { destroySandbox(it) }
            failedPlugins[item.id] = SystemClock.elapsedRealtime()
            AppLogger.e(TAG, "激活插件失败: ${item.name}", e)
            false
        }
    }

    // 注销并销毁指定插件沙盒
    override fun deactivatePlugin(pluginId: String) {
        registeredPlugins.remove(pluginId)
        failedPlugins.remove(pluginId)
        activatingJobs.remove(pluginId)?.cancel()
        activeSandboxes.remove(pluginId)?.let { sandbox ->
            scope.launch(Dispatchers.Main) {
                destroySandbox(sandbox)
            }
        }
    }

    // 批量同步已启用插件定义，卸载已停用沙盒，不提前拉起 WebView
    override suspend fun syncActivePlugins(items: List<LxPluginItem>): Unit = withContext(Dispatchers.Main) {
        val enabledItems = items.filter { it.isEnabled }
        val enabledIds = enabledItems.map { it.id }.toSet()

        // 卸载未启用或已被删除的沙盒
        val toRemove = (registeredPlugins.keys + activeSandboxes.keys + activatingJobs.keys).filter { it !in enabledIds }
        toRemove.forEach { deactivatePlugin(it) }

        for (item in enabledItems) {
            val prev = registeredPlugins.put(item.id, item)
            if (prev != null && prev.rawScript != item.rawScript) {
                failedPlugins.remove(item.id)
                activatingJobs.remove(item.id)?.cancel()
                activeSandboxes.remove(item.id)?.let { destroySandbox(it) }
            }
        }
    }

    private fun isCoolingDown(pluginId: String): Boolean {
        val failedAt = failedPlugins[pluginId] ?: return false
        if (SystemClock.elapsedRealtime() - failedAt < ACTIVATE_FAILURE_COOLDOWN_MS) {
            return true
        }
        failedPlugins.remove(pluginId)
        return false
    }

    private fun ensureActivating(item: LxPluginItem): Deferred<Boolean> {
        activatingJobs[item.id]?.takeIf { it.isActive }?.let { return it }
        lateinit var job: Deferred<Boolean>
        job = scope.async(Dispatchers.Main) {
            try {
                activateInternal(item)
            } finally {
                activatingJobs.remove(item.id, job)
            }
        }
        activatingJobs[item.id] = job
        return job
    }

    private fun scheduleIdleCleanup() {
        idleCleanupJob?.cancel()
        if (activeSandboxes.isEmpty()) return
        idleCleanupJob = scope.launch(Dispatchers.Main) {
            delay(SANDBOX_IDLE_TIMEOUT_MS)
            trimIdleSandboxes()
        }
    }

    // 释放当前无挂起解析任务的空闲 WebView 沙盒，保留 registeredPlugins 供下次按需重建
    fun trimIdleSandboxes() {
        scope.launch(Dispatchers.Main) {
            if (activatingJobs.isNotEmpty()) return@launch
            val idleEntries = activeSandboxes.entries.filter { it.value.pendingResolves.isEmpty() }
            for ((id, sandbox) in idleEntries) {
                if (activeSandboxes.remove(id, sandbox)) {
                    destroySandbox(sandbox)
                    AppLogger.i(TAG, "释放空闲插件沙盒: ID=$id")
                }
            }
        }
    }

    // 获取特定插件当前支持的平台列表
    override fun getSupportedSources(pluginId: String): List<String> {
        return activeSandboxes[pluginId]?.supportedSources?.takeIf { it.isNotEmpty() }
            ?: registeredPlugins[pluginId]?.sources
            ?: emptyList()
    }

    // 针对特定插件调用解析音乐直链，首次命中时才按需拉起 WebView 沙盒
    override suspend fun resolveMusicUrl(
        pluginId: String,
        source: String,
        songId: String,
        songName: String,
        singer: String,
        albumName: String,
        durationMs: Long,
        quality: String
    ): String? = withContext(Dispatchers.IO) {
        if (!activeSandboxes.containsKey(pluginId)) {
            if (isCoolingDown(pluginId)) return@withContext null
            val job = activatingJobs[pluginId] ?: registeredPlugins[pluginId]?.let { ensureActivating(it) }
            job?.await()
        }
        val sandbox = activeSandboxes[pluginId] ?: return@withContext null
        scheduleIdleCleanup()
        val callId = nextCallId.incrementAndGet()
        val deferred = CompletableDeferred<String?>()
        sandbox.pendingResolves[callId] = deferred

        val lxQuality = LxScriptSupport.mapQualityToLx(quality)
        val musicInfoJson = LxScriptSupport.buildMusicInfoJson(source, songId, songName, singer, albumName, durationMs)

        try {
            sandbox.script.call(
                "__melodia_resolve_music_url",
                JsonPrimitive(callId),
                JsonPrimitive(source),
                JsonPrimitive(musicInfoJson),
                JsonPrimitive(lxQuality)
            )
            withTimeoutOrNull(LxScriptSupport.RESOLVE_TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            AppLogger.w(TAG, "插件[$pluginId] 解析直链超时或失败: ${e.message}")
            null
        } finally {
            sandbox.pendingResolves.remove(callId)
            scheduleIdleCleanup()
        }
    }

    // 兼容旧接口：载入并初始化脚本
    suspend fun loadScript(scriptText: String): Result<LxPluginInfo> {
        return loadAndVerifyScript(scriptText)
    }

    // 重置并销毁全部沙盒
    override fun reset() {
        idleCleanupJob?.cancel()
        idleCleanupJob = null
        registeredPlugins.clear()
        failedPlugins.clear()
        activatingJobs.values.forEach { it.cancel() }
        activatingJobs.clear()
        val sandboxes = activeSandboxes.values.toList()
        activeSandboxes.clear()
        scope.launch(Dispatchers.Main) {
            sandboxes.forEach { destroySandbox(it) }
        }
    }

    // 创建隔离的脚本沙盒，宿主消息交给共用的 LxScriptHost
    private suspend fun createSandboxInstance(
        pluginId: String,
        onInited: (List<String>) -> Unit
    ): PluginSandbox {
        val pendingResolves = ConcurrentHashMap<Int, CompletableDeferred<String?>>()
        val host = LxScriptHost(
            pluginId = pluginId,
            ioScope = ioScope,
            fetcher = fetcher,
            onInited = onInited,
            onResolved = { callId, url -> pendingResolves[callId]?.complete(url) }
        )
        val script = factory.create("lx-$pluginId", host, LxShim.script("mobile"))
        host.sandbox = script
        return PluginSandbox(pluginId = pluginId, script = script, pendingResolves = pendingResolves)
    }

    private fun destroySandbox(sandbox: PluginSandbox) {
        sandbox.pendingResolves.values.forEach { it.complete(null) }
        sandbox.script.close()
    }
}
