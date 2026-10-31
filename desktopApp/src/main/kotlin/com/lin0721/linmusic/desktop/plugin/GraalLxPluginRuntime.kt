package com.lin0721.linmusic.desktop.plugin

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.source.LxPluginInfo
import com.lin0721.linmusic.core.source.LxPluginItem
import com.lin0721.linmusic.core.source.LxPluginRuntime
import com.lin0721.linmusic.core.source.LxScriptSupport
import com.lin0721.linmusic.core.source.LxShim
import com.lin0721.linmusic.core.source.SourceHttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.io.IOAccess
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "GraalLxPluginRuntime"

// 沙盒内 lx.request 的网络出口，默认走共用的 OkHttp 客户端
typealias LxHttpFetcher = suspend (url: String, method: String, headers: Map<String, String>, body: String) -> String

private val defaultFetcher: LxHttpFetcher = { url, method, headers, body ->
    if (method.equals("POST", ignoreCase = true)) {
        SourceHttpClient.post(url, body, headers)
    } else {
        SourceHttpClient.get(url, headers)
    }
}

// 桌面端 LX 插件运行时：每个插件一个 GraalJS Context，各自独占一条线程
class GraalLxPluginRuntime(
    private val fetcher: LxHttpFetcher = defaultFetcher
) : LxPluginRuntime {

    // 所有 Context 共用一个 Engine，省内存且共享解析缓存
    private val engine: Engine by lazy {
        // 发布版经 ProGuard 重打包后 Truffle 的 Multi-Release 清单丢失，需关掉这项自检
        System.setProperty("polyglotimpl.DisableMultiReleaseCheck", "true")
        Engine.newBuilder("js")
            .option("engine.WarnInterpreterOnly", "false")
            .build()
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lifecycleLock = Mutex()
    private val activeSandboxes = ConcurrentHashMap<String, Sandbox>()
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
        val sandbox = createSandbox("temp_${System.nanoTime()}")
        return try {
            sandbox.runScript(headerInfo.name, headerInfo.description, headerInfo.version, headerInfo.author, scriptText)
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
        quality: String,
        timeoutMs: Long
    ): String? {
        val sandbox = activeSandboxes[pluginId] ?: return null
        val callId = nextCallId.incrementAndGet()
        val deferred = CompletableDeferred<String?>()
        sandbox.pendingResolves[callId] = deferred

        val musicInfoJson = LxScriptSupport.buildMusicInfoJson(source, songId, songName, singer, albumName, durationMs)
        val lxQuality = LxScriptSupport.mapQualityToLx(quality)
        return try {
            sandbox.call("__melodia_resolve_music_url", callId, source, musicInfoJson, lxQuality)
            withTimeoutOrNull(timeoutMs) { deferred.await() }
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
        val sandbox = createSandbox(item.id)
        return try {
            sandbox.supportedSources = item.sources
            sandbox.runScript(item.name, item.description, item.version, item.author, item.rawScript)
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

    private suspend fun createSandbox(pluginId: String): Sandbox {
        val sandbox = Sandbox(pluginId)
        sandbox.start()
        return sandbox
    }

    // 单个插件沙盒，所有 JS 调用都排到自己的线程上，避免多线程并发进入同一个 Context
    private inner class Sandbox(val pluginId: String) {
        private val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "lx-plugin-$pluginId").apply { isDaemon = true }
        }
        private val dispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()
        private val scope = CoroutineScope(dispatcher + SupervisorJob())
        private val timers = ConcurrentHashMap<Int, Job>()

        @Volatile
        private var closed = false
        private lateinit var context: Context

        val pendingResolves = ConcurrentHashMap<Int, CompletableDeferred<String?>>()
        val inited = CompletableDeferred<List<String>>()

        @Volatile
        var supportedSources: List<String> = emptyList()

        suspend fun start() = withContext(dispatcher) {
            context = Context.newBuilder("js")
                .engine(engine)
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowNativeAccess(false)
                .build()
            val bridge = LxHostBridge(
                pluginId = pluginId,
                onHttp = ::handleHttpRequest,
                eventSink = ::handleEvent,
                onResolved = { callId, url, error ->
                    if (!error.isNullOrBlank()) AppLogger.w(TAG, "插件沙盒[$pluginId] 解析直链失败: $error")
                    pendingResolves[callId]?.complete(url?.takeIf { it.isNotBlank() })
                },
                onTimer = ::startTimer
            )
            context.getBindings("js").putMember("MelodiaHostBridge", bridge)
            context.eval("js", PRELUDE)
            context.eval("js", LxShim.script("desktop"))
        }

        // 写入脚本元信息并执行脚本，脚本抛错时向上传递
        suspend fun runScript(name: String, description: String, version: String, author: String, script: String) {
            withContext(dispatcher) {
                val info = "window.lx.currentScriptInfo = { name: ${quote(name)}, description: ${quote(description)}, " +
                    "version: ${quote(version)}, author: ${quote(author)} };"
                context.eval("js", info)
                context.eval("js", script)
            }
        }

        // 调用沙盒内的全局函数，参数按宿主类型传入
        suspend fun call(function: String, vararg args: Any?) {
            withContext(dispatcher) {
                if (closed) return@withContext
                context.getBindings("js").getMember(function).execute(*args)
            }
        }

        // 从宿主回调线程把任务投递回沙盒线程
        fun post(function: String, vararg args: Any?) {
            if (closed) return
            scope.launch {
                try {
                    if (!closed) context.getBindings("js").getMember(function).execute(*args)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "[$pluginId] 回调 $function 异常: ${e.message}")
                }
            }
        }

        // 沙盒内 lx.request 的网络请求在 IO 线程执行，结果再投递回沙盒线程
        private fun handleHttpRequest(id: Int, url: String, method: String, headersJson: String, body: String) {
            ioScope.launch {
                try {
                    val headers = mutableMapOf<String, String>()
                    if (headersJson.isNotBlank()) {
                        runCatching {
                            Json.parseToJsonElement(headersJson).jsonObject.forEach { (k, v) ->
                                headers[k] = v.jsonPrimitive.content
                            }
                        }
                    }
                    val respBody = fetcher(url, method, headers, body)
                    post("__melodia_handle_http_response", id, null, "{\"statusCode\":200}", respBody)
                } catch (e: Exception) {
                    post("__melodia_handle_http_response", id, e.message ?: "Network error", null, null)
                }
            }
        }

        private fun handleEvent(event: String, dataJson: String) {
            if (event != "inited") return
            val sources = runCatching {
                Json.parseToJsonElement(dataJson).jsonObject["sources"]?.jsonObject?.keys?.toList()
            }.getOrNull().orEmpty()
            inited.complete(sources)
        }

        private fun startTimer(id: Int, delayMs: Long) {
            timers[id] = scope.launch {
                delay(delayMs)
                timers.remove(id)
                post("__melodia_fire_timer", id)
            }
        }

        fun close() {
            if (closed) return
            closed = true
            timers.values.forEach { it.cancel() }
            pendingResolves.values.forEach { it.complete(null) }
            inited.cancel()
            scope.cancel()
            runCatching { if (::context.isInitialized) context.close(true) }
            executor.shutdownNow()
        }

        private fun quote(text: String): String = JsonPrimitive(text).toString()
    }

    private companion object {
        const val PRELUDE = """
            var window = globalThis;
            (function() {
                var fmt = function(args) {
                    return Array.prototype.map.call(args, function(x) {
                        try { return typeof x === 'string' ? x : JSON.stringify(x); } catch (e) { return String(x); }
                    }).join(' ');
                };
                globalThis.console = {
                    log: function() { MelodiaHostBridge.log('d', fmt(arguments)); },
                    info: function() { MelodiaHostBridge.log('d', fmt(arguments)); },
                    debug: function() { MelodiaHostBridge.log('d', fmt(arguments)); },
                    warn: function() { MelodiaHostBridge.log('w', fmt(arguments)); },
                    error: function() { MelodiaHostBridge.log('w', fmt(arguments)); }
                };
            })();
        """
    }
}
