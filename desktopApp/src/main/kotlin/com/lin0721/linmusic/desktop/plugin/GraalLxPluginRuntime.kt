package com.lin0721.linmusic.desktop.plugin

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.plugin.ScriptSandbox
import com.lin0721.linmusic.core.plugin.ScriptSandboxFactory
import com.lin0721.linmusic.core.source.LxHttpFetcher
import com.lin0721.linmusic.core.source.LxPluginInfo
import com.lin0721.linmusic.core.source.LxPluginItem
import com.lin0721.linmusic.core.source.LxPluginRuntime
import com.lin0721.linmusic.core.source.LxScriptHost
import com.lin0721.linmusic.core.source.LxScriptSupport
import com.lin0721.linmusic.core.source.LxShim
import com.lin0721.linmusic.core.source.SourceHttpClient
import com.lin0721.linmusic.core.source.lxScriptInfoJs
import com.lin0721.linmusic.core.source.sourceHttpFetcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "GraalLxPluginRuntime"

// 桌面端 LX 插件运行时：每个插件一个脚本沙盒，协议由 LxShim 与 LxScriptHost 定义
class GraalLxPluginRuntime(
    private val fetcher: LxHttpFetcher = sourceHttpFetcher(),
    private val factory: ScriptSandboxFactory = GraalScriptSandboxFactory()
) : LxPluginRuntime {

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lifecycleLock = Mutex()
    private val activeSandboxes = ConcurrentHashMap<String, LxSandbox>()
    private val nextCallId = AtomicInteger(0)

    override suspend fun downloadScript(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val script = SourceHttpClient.get(url)
            if (script.isNotBlank()) Result.success(script) else Result.failure(Exception("脚本内容为空"))
        } catch (e: Exception) {
            AppLogger.e(TAG, "下载远程脚本失败: $url", e)
            Result.failure(e)
        }
    }

    override suspend fun loadAndVerifyScript(scriptText: String): Result<LxPluginInfo> {
        val headerInfo = LxScriptSupport.parseScriptHeader(scriptText)
        val sandbox = try {
            createSandbox("temp_${System.nanoTime()}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建校验沙盒失败", e)
            return Result.failure(Exception("脚本执行失败：${e.message}", e))
        }
        return try {
            sandbox.runScript(headerInfo.name, headerInfo.description, headerInfo.version, headerInfo.author, scriptText).getOrThrow()
            val sources = withTimeoutOrNull(LxScriptSupport.INIT_TIMEOUT_MS) { sandbox.inited.await() }.orEmpty()
            AppLogger.i(TAG, "脚本校验成功: ${headerInfo.name} v${headerInfo.version}, 支持平台: $sources")
            Result.success(headerInfo.copy(sources = sources, rawScript = scriptText))
        } catch (e: Exception) {
            AppLogger.e(TAG, "脚本校验失败", e)
            Result.failure(Exception("脚本执行失败：${e.message}", e))
        } finally {
            sandbox.close()
        }
    }

    override suspend fun activatePlugin(item: LxPluginItem): Boolean = lifecycleLock.withLock { activateLocked(item) }

    override fun deactivatePlugin(pluginId: String) {
        activeSandboxes.remove(pluginId)?.close()
    }

    override suspend fun syncActivePlugins(items: List<LxPluginItem>) = lifecycleLock.withLock {
        val enabledItems = items.filter { it.isEnabled }
        val enabledIds = enabledItems.map { it.id }.toSet()
        activeSandboxes.keys.filter { it !in enabledIds }.forEach { deactivatePlugin(it) }
        for (item in enabledItems) {
            if (!activeSandboxes.containsKey(item.id)) activateLocked(item)
        }
    }

    override fun getSupportedSources(pluginId: String): List<String> =
        activeSandboxes[pluginId]?.supportedSources.orEmpty()

    override suspend fun resolveMusicUrl(
        pluginId: String,
        source: String,
        songId: String,
        songName: String,
        singer: String,
        albumName: String,
        durationMs: Long,
        quality: String
    ): String? {
        val sandbox = activeSandboxes[pluginId] ?: return null
        val callId = nextCallId.incrementAndGet()
        val deferred = CompletableDeferred<String?>()
        sandbox.pendingResolves[callId] = deferred

        val musicInfoJson = LxScriptSupport.buildMusicInfoJson(source, songId, songName, singer, albumName, durationMs)
        val lxQuality = LxScriptSupport.mapQualityToLx(quality)
        return try {
            sandbox.script.call(
                "__melodia_resolve_music_url",
                JsonPrimitive(callId),
                JsonPrimitive(source),
                JsonPrimitive(musicInfoJson),
                JsonPrimitive(lxQuality)
            )
            withTimeoutOrNull(LxScriptSupport.RESOLVE_TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            AppLogger.w(TAG, "插件[$pluginId] 解析直链失败: ${e.message}")
            null
        } finally {
            sandbox.pendingResolves.remove(callId)
        }
    }

    override fun reset() {
        val sandboxes = activeSandboxes.values.toList()
        activeSandboxes.clear()
        sandboxes.forEach { it.close() }
    }

    private suspend fun activateLocked(item: LxPluginItem): Boolean {
        deactivatePlugin(item.id)
        val sandbox = try {
            createSandbox(item.id)
        } catch (e: Exception) {
            AppLogger.e(TAG, "激活插件失败: ${item.name}", e)
            return false
        }
        return try {
            sandbox.supportedSources = item.sources
            sandbox.runScript(item.name, item.description, item.version, item.author, item.rawScript).getOrThrow()
            val sources = withTimeoutOrNull(LxScriptSupport.INIT_TIMEOUT_MS) { sandbox.inited.await() }
            if (!sources.isNullOrEmpty()) sandbox.supportedSources = sources
            activeSandboxes[item.id] = sandbox
            AppLogger.i(TAG, "插件 [${item.name}] 沙盒激活成功, ID=${item.id}, sources=${sandbox.supportedSources}")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "激活插件失败: ${item.name}", e)
            sandbox.close()
            false
        }
    }

    private suspend fun createSandbox(pluginId: String): LxSandbox {
        val pendingResolves = ConcurrentHashMap<Int, CompletableDeferred<String?>>()
        val inited = CompletableDeferred<List<String>>()
        val host = LxScriptHost(
            pluginId = pluginId,
            ioScope = ioScope,
            fetcher = fetcher,
            onInited = { inited.complete(it) },
            onResolved = { callId, url -> pendingResolves[callId]?.complete(url) }
        )
        val script = factory.create("lx-plugin-$pluginId", host, LxShim.script("desktop"))
        host.sandbox = script
        return LxSandbox(script, pendingResolves, inited)
    }

    private class LxSandbox(
        val script: ScriptSandbox,
        val pendingResolves: ConcurrentHashMap<Int, CompletableDeferred<String?>>,
        val inited: CompletableDeferred<List<String>>
    ) {
        @Volatile
        var supportedSources: List<String> = emptyList()

        @Volatile
        private var closed = false

        // 写入脚本元信息并执行脚本，脚本抛错时返回失败
        suspend fun runScript(name: String, description: String, version: String, author: String, raw: String): Result<Unit> {
            script.evaluate(lxScriptInfoJs(name, description, version, author)).onFailure { return Result.failure(it) }
            return script.evaluate(raw)
        }

        fun close() {
            if (closed) return
            closed = true
            pendingResolves.values.forEach { it.complete(null) }
            inited.cancel()
            script.close()
        }
    }
}
