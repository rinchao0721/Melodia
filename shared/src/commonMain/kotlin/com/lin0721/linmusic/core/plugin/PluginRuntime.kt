package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "PluginRuntime"

data class PluginRuntimeStatus(
    val running: Boolean = false,
    // 脚本注册的能力，形如 audioSource:main
    val registrations: Set<String> = emptySet(),
    val lastError: String? = null
)

// 已启用插件的脚本沙盒：每个插件一个，宿主调用与插件回调都在这里路由；连续出错达到上限时交给 onFailure 停用
class PluginRuntime(
    private val factory: ScriptSandboxFactory,
    private val api: PluginHostApi,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val registry: ExtensionRegistry = ExtensionRegistry()
) {
    private val lifecycleLock = Mutex()
    private val running = ConcurrentHashMap<String, Running>()
    private val nextCallId = AtomicInteger(0)

    @Volatile
    private var failureHandler: suspend (pluginId: String, reason: String) -> Unit = { _, _ -> }

    private val _status = MutableStateFlow<Map<String, PluginRuntimeStatus>>(emptyMap())
    val status: StateFlow<Map<String, PluginRuntimeStatus>> = _status.asStateFlow()

    fun setFailureHandler(handler: suspend (pluginId: String, reason: String) -> Unit) {
        failureHandler = handler
    }

    // 让运行中的沙盒与可运行的已启用插件保持一致；只提供布局的插件不需要沙盒
    suspend fun sync(plugins: List<InstalledPlugin>) = lifecycleLock.withLock {
        val targets = plugins.filter { it.state.enabled && it.canRun && it.manifest?.contributes?.needsScript == true }
            .associateBy { it.id }
        running.keys.filter { it !in targets }.forEach { stopLocked(it) }
        _status.update { it.filterKeys { id -> id in targets } }
        for ((id, plugin) in targets) {
            val manifest = plugin.manifest ?: continue
            val current = running[id]
            if (current != null && current.manifest == manifest) continue
            current?.let { stopLocked(id) }
            startLocked(manifest)
        }
    }

    suspend fun stop(pluginId: String) = lifecycleLock.withLock {
        stopLocked(pluginId)
        _status.update { it - pluginId }
    }

    fun isRunning(pluginId: String): Boolean = running.containsKey(pluginId)

    // 调用插件注册的能力；失败或超时返回 null 并计入连续出错
    suspend fun invoke(pluginId: String, kind: String, name: String, args: JsonElement, timeoutMs: Long): JsonElement? {
        val target = running[pluginId] ?: return null
        val callId = nextCallId.incrementAndGet()
        val deferred = CompletableDeferred<Result<JsonElement>>()
        target.pending[callId] = deferred
        return try {
            val result = try {
                target.sandbox.call(PluginPrelude.INVOKE, JsonPrimitive(callId), JsonPrimitive(kind), JsonPrimitive(name), args)
                withTimeoutOrNull(timeoutMs) { deferred.await() }
                    ?: Result.failure(PluginCallException("$kind:$name 超过 ${timeoutMs / 1000} 秒没有返回"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(PluginCallException("调用 $kind:$name 失败：${e.message}"))
            }
            result.onSuccess { target.recordSuccess() }.onFailure { target.recordError(it.message ?: "调用失败") }.getOrNull()
        } finally {
            target.pending.remove(callId)
        }
    }

    // 播放事件只通知，不等待处理结果
    fun emit(pluginId: String, event: PluginEvent, data: JsonElement) {
        val target = running[pluginId] ?: return
        if (event !in target.manifest.contributes.events) return
        scope.launch {
            try {
                target.sandbox.call(PluginPrelude.EMIT, JsonPrimitive(event.key), data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "[$pluginId] 分发事件 ${event.key} 失败: ${e.message}")
            }
        }
    }

    private suspend fun startLocked(manifest: PluginManifest) {
        val script = manifest.scriptFile?.let { file ->
            try {
                withContext(Dispatchers.IO) { file.readText() }
            } catch (e: IOException) {
                null
            }
        }
        if (script == null) {
            fail(manifest.id, "读取 ${PluginPaths.SCRIPT} 失败")
            return
        }
        val target = Running(manifest)
        val sandbox = try {
            factory.create("plugin-${manifest.id}", target, PluginPrelude.script)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建插件沙盒失败: ${manifest.id}", e)
            fail(manifest.id, "无法创建脚本环境：${e.message}")
            return
        }
        target.sandbox = sandbox
        running[manifest.id] = target
        registerEvents(manifest)
        setStatus(manifest.id) { PluginRuntimeStatus(running = true) }
        val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MS) { sandbox.evaluate(script) }
            ?: Result.failure(PluginCallException("脚本加载超过 ${LOAD_TIMEOUT_MS / 1000} 秒"))
        loaded.onFailure { error ->
            AppLogger.w(TAG, "插件脚本加载失败: ${manifest.id} ${error.message}")
            stopLocked(manifest.id)
            fail(manifest.id, "脚本加载失败：${error.message}")
        }.onSuccess {
            AppLogger.i(TAG, "插件已启动: ${manifest.name} v${manifest.version}")
        }
    }

    private fun stopLocked(pluginId: String) {
        running.remove(pluginId)?.close()
        registry.unregister(pluginId, SCRIPT_POINTS)
    }

    private fun registerEvents(manifest: PluginManifest) {
        val subscribed = manifest.contributes.events
        if (subscribed.isEmpty()) return
        val extension = object : EventExtension {
            override val events: Set<PluginEvent> = subscribed

            override fun onEvent(event: PluginEvent, data: JsonElement) = emit(manifest.id, event, data)
        }
        registry.register(Extension(ExtensionPoints.EVENT, manifest.id, manifest.name, manifest.id, extension))
    }

    // 只接受清单声明过的音源与歌词源
    private fun registerSource(manifest: PluginManifest, kind: String, name: String) {
        when (kind) {
            PluginPrelude.KIND_AUDIO_SOURCE -> {
                val order = manifest.contributes.audioSources.indexOfFirst { it.id == name }
                if (order < 0) return
                registry.register(Extension(ExtensionPoints.AUDIO_SOURCE, manifest.id, manifest.name, name, ScriptAudioSource(manifest.id, name), order))
            }
            PluginPrelude.KIND_LYRICS_SOURCE -> {
                val order = manifest.contributes.lyricsSources.indexOfFirst { it.id == name }
                if (order < 0) return
                registry.register(Extension(ExtensionPoints.LYRICS_SOURCE, manifest.id, manifest.name, name, ScriptLyricsSource(manifest.id, name), order))
            }
        }
    }

    private inner class ScriptAudioSource(private val pluginId: String, private val name: String) : AudioSourceExtension {
        override suspend fun resolve(query: PluginTrackQuery, quality: String): JsonElement? {
            val args = buildJsonObject {
                put("method", "resolve")
                put("params", JsonObject(query.toJson() + ("quality" to JsonPrimitive(quality))))
            }
            return invoke(pluginId, PluginPrelude.KIND_AUDIO_SOURCE, name, args, SOURCE_TIMEOUT_MS)
        }
    }

    private inner class ScriptLyricsSource(private val pluginId: String, private val name: String) : LyricsSourceExtension {
        override suspend fun fetch(query: PluginTrackQuery): JsonElement? {
            val args = buildJsonObject {
                put("method", "fetch")
                put("params", query.toJson())
            }
            return invoke(pluginId, PluginPrelude.KIND_LYRICS_SOURCE, name, args, SOURCE_TIMEOUT_MS)
        }
    }

    private suspend fun fail(pluginId: String, reason: String) {
        setStatus(pluginId) { it.copy(running = false, lastError = reason) }
        try {
            failureHandler(pluginId, reason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "停用出错插件失败: $pluginId", e)
        }
    }

    private fun setStatus(pluginId: String, transform: (PluginRuntimeStatus) -> PluginRuntimeStatus) {
        _status.update { it + (pluginId to transform(it[pluginId] ?: PluginRuntimeStatus())) }
    }

    private inner class Running(val manifest: PluginManifest) : ScriptHost {
        lateinit var sandbox: ScriptSandbox
        val pending = ConcurrentHashMap<Int, CompletableDeferred<Result<JsonElement>>>()
        private val consecutiveErrors = AtomicInteger(0)

        @Volatile
        private var closed = false

        override fun onMessage(method: String, payload: String) {
            if (closed) return
            val data = try {
                Json.parseToJsonElement(payload) as? JsonObject
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            } ?: return
            when (method) {
                PluginPrelude.MESSAGE_CALL -> handleCall(data)
                PluginPrelude.MESSAGE_RESULT -> handleResult(data)
                PluginPrelude.MESSAGE_REGISTER -> handleRegister(data)
            }
        }

        private fun handleCall(data: JsonObject) {
            val id = (data["id"] as? JsonPrimitive)?.intOrNull ?: return
            val method = (data["method"] as? JsonPrimitive)?.content ?: return
            val args = data["args"] ?: JsonNull
            scope.launch {
                val (error, result) = try {
                    null to withTimeout(CALL_TIMEOUT_MS) { api.handle(manifest, method, args) }
                } catch (e: PluginCallException) {
                    e.message to JsonNull
                } catch (e: TimeoutCancellationException) {
                    "$method 超时" to JsonNull
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "[${manifest.id}] 宿主调用 $method 异常", e)
                    "$method 执行失败" to JsonNull
                }
                if (closed) return@launch
                try {
                    sandbox.call(PluginPrelude.REPLY, JsonPrimitive(id), error?.let(::JsonPrimitive) ?: JsonNull, result)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "[${manifest.id}] 回传调用结果失败: ${e.message}")
                }
            }
        }

        private fun handleResult(data: JsonObject) {
            val callId = (data["callId"] as? JsonPrimitive)?.intOrNull ?: return
            val deferred = pending[callId] ?: return
            val error = (data["error"] as? JsonPrimitive)?.content
            deferred.complete(if (error != null) Result.failure(PluginCallException(error)) else Result.success(data["result"] ?: JsonNull))
        }

        private fun handleRegister(data: JsonObject) {
            val kind = (data["kind"] as? JsonPrimitive)?.content ?: return
            val name = (data["name"] as? JsonPrimitive)?.content ?: return
            setStatus(manifest.id) { it.copy(registrations = it.registrations + "$kind:$name") }
            registerSource(manifest, kind, name)
            // 注册期间沙盒可能已经停止
            if (closed && running[manifest.id] == null) registry.unregister(manifest.id, SCRIPT_POINTS)
        }

        fun recordSuccess() {
            consecutiveErrors.set(0)
        }

        fun recordError(reason: String) {
            setStatus(manifest.id) { it.copy(lastError = reason) }
            if (consecutiveErrors.incrementAndGet() < MAX_CONSECUTIVE_ERRORS) return
            scope.launch {
                lifecycleLock.withLock { if (running[manifest.id] === this@Running) stopLocked(manifest.id) }
                fail(manifest.id, "连续出错 $MAX_CONSECUTIVE_ERRORS 次，已自动停用：$reason")
            }
        }

        fun close() {
            if (closed) return
            closed = true
            pending.values.forEach { it.complete(Result.failure(PluginCallException("插件已停止"))) }
            if (::sandbox.isInitialized) {
                try {
                    sandbox.close()
                } catch (e: Exception) {
                    AppLogger.w(TAG, "关闭插件沙盒异常: ${manifest.id}", e)
                }
            }
        }
    }

    companion object {
        const val LOAD_TIMEOUT_MS = 8_000L
        const val CALL_TIMEOUT_MS = 30_000L
        const val MAX_CONSECUTIVE_ERRORS = 5

        // 各类插件调用的上限
        const val SOURCE_TIMEOUT_MS = 8_000L
        const val EVENT_TIMEOUT_MS = 2_000L
        const val ACTION_TIMEOUT_MS = 5_000L

        private val SCRIPT_POINTS: Set<ExtensionPoint<*>> =
            setOf(ExtensionPoints.AUDIO_SOURCE, ExtensionPoints.LYRICS_SOURCE, ExtensionPoints.EVENT)
    }
}
