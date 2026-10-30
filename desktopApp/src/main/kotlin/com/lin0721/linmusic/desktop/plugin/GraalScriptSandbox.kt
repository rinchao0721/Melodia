package com.lin0721.linmusic.desktop.plugin

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.plugin.ScriptHost
import com.lin0721.linmusic.core.plugin.ScriptSandbox
import com.lin0721.linmusic.core.plugin.ScriptSandboxFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.io.IOAccess
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private const val TAG = "GraalScriptSandbox"

// 桌面端脚本沙盒工厂；所有 Context 共用一个 Engine，省内存且共享解析缓存
class GraalScriptSandboxFactory : ScriptSandboxFactory {

    private val engine: Engine by lazy {
        // 发布版经 ProGuard 重打包后 Truffle 的 Multi-Release 清单丢失，需关掉这项自检
        System.setProperty("polyglotimpl.DisableMultiReleaseCheck", "true")
        Engine.newBuilder("js")
            .option("engine.WarnInterpreterOnly", "false")
            .build()
    }

    override suspend fun create(name: String, host: ScriptHost, prelude: String): ScriptSandbox {
        val sandbox = GraalScriptSandbox(engine, name, host)
        try {
            sandbox.start(prelude)
        } catch (e: Exception) {
            sandbox.close()
            throw e
        }
        return sandbox
    }
}

// 单个 GraalJS 沙盒：独占一条线程，所有 JS 调用都排到这条线程上，避免多线程并发进入同一个 Context
class GraalScriptSandbox internal constructor(
    private val engine: Engine,
    private val name: String,
    private val host: ScriptHost
) : ScriptSandbox {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "script-$name").apply { isDaemon = true }
    }
    private val dispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val timers = ConcurrentHashMap<Int, Job>()

    @Volatile
    private var closed = false
    private lateinit var context: Context

    internal suspend fun start(prelude: String) = withContext(dispatcher) {
        context = Context.newBuilder("js")
            .engine(engine)
            .allowHostAccess(HostAccess.EXPLICIT)
            .allowIO(IOAccess.NONE)
            .allowCreateThread(false)
            .allowNativeAccess(false)
            .build()
        context.getBindings("js").putMember("MelodiaHostBridge", GraalHostBridge(name, host, ::startTimer))
        context.eval("js", BASE_PRELUDE)
        context.eval("js", prelude)
    }

    override suspend fun evaluate(script: String): Result<Unit> = withContext(dispatcher) {
        if (closed) return@withContext Result.failure(IllegalStateException("沙盒已关闭"))
        try {
            context.eval("js", script)
            Result.success(Unit)
        } catch (e: PolyglotException) {
            Result.failure(Exception(e.message ?: "脚本执行出错", e))
        } catch (e: IllegalStateException) {
            Result.failure(e)
        }
    }

    // 参数整体序列化为 JSON 数组交给沙盒内的分发函数，免得每次调用都重新解析一段源码
    override suspend fun call(function: String, vararg args: JsonElement) {
        withContext(dispatcher) { invokeLocked(function, JsonArray(args.toList()).toString()) }
    }

    private fun invokeLocked(function: String, argsJson: String) {
        if (closed) return
        context.getBindings("js").getMember(APPLY).execute(function, argsJson)
    }

    private fun startTimer(id: Int, delayMs: Long) {
        if (closed) return
        timers[id] = scope.launch {
            delay(delayMs)
            timers.remove(id)
            try {
                if (!closed) context.getBindings("js").getMember(FIRE_TIMER).execute(id)
            } catch (e: PolyglotException) {
                AppLogger.w(TAG, "[$name] 定时器回调异常: ${e.message}")
            } catch (e: IllegalStateException) {
                AppLogger.w(TAG, "[$name] 定时器回调时沙盒已关闭")
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        timers.values.forEach { it.cancel() }
        scope.cancel()
        runCatching { if (::context.isInitialized) context.close(true) }
        executor.shutdownNow()
    }

    private companion object {
        const val APPLY = "__melodia_apply"
        const val FIRE_TIMER = "__melodia_fire_timer"

        // GraalJS 没有 window、console 与定时器，统一补上
        const val BASE_PRELUDE = """
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
                var timers = {};
                var timerSeq = 0;
                var schedule = function(fn, ms, repeat, extra) {
                    var id = ++timerSeq;
                    timers[id] = { fn: fn, repeat: repeat, ms: ms || 0, args: extra };
                    MelodiaHostBridge.setTimer(id, ms || 0);
                    return id;
                };
                globalThis.setTimeout = function(fn, ms) { return schedule(fn, ms, false, Array.prototype.slice.call(arguments, 2)); };
                globalThis.setInterval = function(fn, ms) { return schedule(fn, ms, true, Array.prototype.slice.call(arguments, 2)); };
                globalThis.clearTimeout = function(id) { delete timers[id]; };
                globalThis.clearInterval = globalThis.clearTimeout;
                globalThis.__melodia_fire_timer = function(id) {
                    var t = timers[id];
                    if (!t) return;
                    if (t.repeat) MelodiaHostBridge.setTimer(id, t.ms); else delete timers[id];
                    if (typeof t.fn === 'function') t.fn.apply(null, t.args);
                };
                globalThis.__melodia_apply = function(name, argsJson) {
                    var fn = globalThis[name];
                    if (typeof fn === 'function') fn.apply(null, JSON.parse(argsJson));
                };
            })();
        """
    }
}

// 暴露给沙盒脚本的宿主接口，HostAccess.EXPLICIT 下只有带 Export 注解的方法可被调用
class GraalHostBridge internal constructor(
    private val name: String,
    private val host: ScriptHost,
    private val onTimer: (id: Int, delayMs: Long) -> Unit
) {

    @HostAccess.Export
    fun post(method: String, payload: String) {
        try {
            host.onMessage(method, payload)
        } catch (e: Exception) {
            AppLogger.w(TAG, "[$name] 处理脚本消息 $method 异常", e)
        }
    }

    @HostAccess.Export
    fun invokeSync(method: String, payload: String): String = try {
        host.onSyncCall(method, payload)
    } catch (e: Exception) {
        AppLogger.w(TAG, "[$name] 同步调用 $method 异常", e)
        ""
    }

    // JS 数字可能是小数或负数，统一夹到合法范围
    @HostAccess.Export
    fun setTimer(id: Int, delayMs: Double) {
        onTimer(id, delayMs.coerceIn(0.0, MAX_TIMER_MS).toLong())
    }

    @HostAccess.Export
    fun log(level: String, message: String) {
        if (level == "w") AppLogger.w(TAG, "[$name] $message") else AppLogger.d(TAG, "[$name] $message")
    }

    private companion object {
        // 一天，防止脚本写入超大延时占住协程
        const val MAX_TIMER_MS = 86_400_000.0
    }
}
