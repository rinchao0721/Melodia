package com.lin0721.linmusic.desktop.plugin

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.plugin.ExtensionPoint
import com.lin0721.linmusic.core.plugin.ExtensionPoints
import com.lin0721.linmusic.core.plugin.ExtensionRegistry
import com.lin0721.linmusic.core.plugin.InstalledPlugin
import com.lin0721.linmusic.core.plugin.NativePluginHost
import com.lin0721.linmusic.core.plugin.PLUGIN_API_MAJOR
import com.lin0721.linmusic.core.plugin.PluginHostApi
import com.lin0721.linmusic.core.plugin.PluginKind
import com.lin0721.linmusic.core.plugin.PluginManifest
import com.lin0721.linmusic.core.plugin.PluginNotices
import com.lin0721.linmusic.core.plugin.PluginPackage
import com.lin0721.linmusic.core.plugin.PluginPaths
import com.lin0721.linmusic.core.plugin.PluginRuntime
import com.lin0721.linmusic.core.plugin.PluginRuntimeStatus
import com.lin0721.linmusic.core.plugin.PluginState
import com.lin0721.linmusic.core.plugin.PluginStore
import com.lin0721.linmusic.plugin.api.MelodiaPlugin
import com.lin0721.linmusic.plugin.api.PluginApiVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "NativePlugin"
private const val LOAD_TIMEOUT_MS = 10_000L
private const val UNLOAD_TIMEOUT_MS = 2_000L
private const val MAX_CONSECUTIVE_ERRORS = 5
private const val MARKER_FILE = "loading"
private const val RUN_DIRECTORY = "run"
private const val REASON_SAFE_MODE = "安全模式下未加载"
private const val REASON_NOT_ALLOWED = "「允许原生插件」未开启"
private val BLOCK_REASONS = setOf(REASON_SAFE_MODE, REASON_NOT_ALLOWED)

// 这些类由宿主提供，保证插件与宿主看到的是同一份；其余类只能来自 JDK 或插件自己的 JAR
private val HOST_PACKAGES = listOf(
    "com.lin0721.linmusic.plugin.api.",
    "kotlin.",
    "kotlinx.coroutines.",
    "androidx.compose.",
    "androidx.collection.",
    "androidx.lifecycle.",
    "androidx.savedstate.",
    "org.jetbrains.compose.",
    "org.jetbrains.skia.",
    "org.jetbrains.skiko."
)

// 图标库体积大，发布版已裁掉，插件要用就自己打进 JAR
private const val PLUGIN_OWNED_PACKAGE = "androidx.compose.material.icons."

internal class NativePluginClassLoader(jar: File, private val host: ClassLoader) :
    URLClassLoader("melodia-plugin", arrayOf(jar.toURI().toURL()), ClassLoader.getPlatformClassLoader()) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> =
        if (!name.startsWith(PLUGIN_OWNED_PACKAGE) && HOST_PACKAGES.any(name::startsWith)) host.loadClass(name) else super.loadClass(name, resolve)
}

private val NATIVE_POINTS: Set<ExtensionPoint<*>> = setOf(
    ExtensionPoints.AUDIO_SOURCE,
    ExtensionPoints.LYRICS_SOURCE,
    ExtensionPoints.EVENT,
    ExtensionPoints.COMMAND,
    ExtensionPoints.CONTENT_SOURCE,
    ExtensionPoints.ACTION,
    ExtensionPoints.BACKDROP,
    ExtensionPoints.COMPOSE_PAGE
)

private class NativeLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

// 只在进程启动后的第一次同步里加载；崩溃在加载期留下标记，下次启动据此停用对应插件
class DesktopNativePluginHost(
    private val store: PluginStore,
    private val api: PluginHostApi,
    private val registry: ExtensionRegistry,
    private val notices: PluginNotices,
    private val safeMode: Boolean = false,
    private val hostLoader: ClassLoader = DesktopNativePluginHost::class.java.classLoader,
    private val loadTimeoutMs: Long = LOAD_TIMEOUT_MS
) : NativePluginHost {
    private val lock = Mutex()
    private val loaded = ConcurrentHashMap<String, LoadedPlugin>()
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val marker = File(store.nativeRoot, MARKER_FILE)
    private val runRoot = File(store.nativeRoot, RUN_DIRECTORY)

    @Volatile
    private var started = false

    @Volatile
    private var failureHandler: suspend (String, String) -> Unit = { _, _ -> }

    private val _status = MutableStateFlow<Map<String, PluginRuntimeStatus>>(emptyMap())
    override val status: StateFlow<Map<String, PluginRuntimeStatus>> = _status.asStateFlow()

    override fun setFailureHandler(handler: suspend (pluginId: String, reason: String) -> Unit) {
        failureHandler = handler
    }

    override fun isActive(pluginId: String): Boolean = loaded.containsKey(pluginId)

    override suspend fun sync(plugins: List<InstalledPlugin>, allowed: Boolean) = lock.withLock {
        val natives = plugins.filter { it.manifest?.kind == PluginKind.NATIVE }
        val runnable = if (allowed && !safeMode) natives.filter { it.state.enabled && it.canRun } else emptyList()
        loaded.values.filter { current -> runnable.none { it.manifest == current.manifest } }.forEach { unloadLocked(it.manifest.id) }
        if (!started) {
            started = true
            val crashed = withContext(Dispatchers.IO) { recoverFromCrash() }
            for (plugin in runnable) {
                if (plugin.id == crashed) reportCrash(plugin) else load(plugin)
            }
        }
        publishBlocked(natives, allowed)
    }

    override suspend fun unload(pluginId: String) = lock.withLock {
        unloadLocked(pluginId)
        _status.update { it - pluginId }
    }

    // 已启用却没加载的插件要说明原因；没有状态的表示等重启
    private fun publishBlocked(natives: List<InstalledPlugin>, allowed: Boolean) {
        val reason = when {
            safeMode -> REASON_SAFE_MODE
            !allowed -> REASON_NOT_ALLOWED
            else -> null
        }
        _status.update { current ->
            val kept = current.filter { (id, status) ->
                loaded.containsKey(id) || (natives.any { it.id == id } && status.lastError != null && status.lastError !in BLOCK_REASONS)
            }
            if (reason == null) {
                kept
            } else {
                kept + natives.filter { it.state.enabled && !loaded.containsKey(it.id) }.associate { it.id to PluginRuntimeStatus(lastError = reason) }
            }
        }
    }

    // 返回上次加载时崩溃的插件；运行副本只有上一个进程在用，这里可以安全清掉
    private fun recoverFromCrash(): String? {
        val crashed = try {
            marker.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: IOException) {
            AppLogger.w(TAG, "读取加载标记失败", e)
            null
        }
        marker.delete()
        runRoot.deleteRecursively()
        return crashed
    }

    private suspend fun reportCrash(plugin: InstalledPlugin) {
        notices.post("「${plugin.name}」上次启动时导致应用退出，已自动停用")
        fail(plugin.id, "上次启动时导致应用退出，已自动停用")
    }

    private suspend fun load(plugin: InstalledPlugin) {
        val manifest = plugin.manifest ?: return
        try {
            val entry = withContext(Dispatchers.IO) {
                val workDirectory = prepare(manifest, plugin.state)
                marker.parentFile?.mkdirs()
                marker.writeText(manifest.id)
                instantiate(manifest, workDirectory)
            }
            val target = LoadedPlugin(manifest, entry)
            val context = NativePluginContext(manifest, target, api, registry, File(runRoot, manifest.id), store.dataDirectoryOf(manifest.id)!!)

            runBounded("onLoad", loadTimeoutMs) { entry.onLoad(context) }
            loaded[manifest.id] = target
            _status.update { it + (manifest.id to PluginRuntimeStatus(running = true, registrations = context.registrations)) }
            AppLogger.i(TAG, "原生插件已加载: ${manifest.name} v${manifest.version}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: NativeLoadException) {
            AppLogger.w(TAG, "加载原生插件失败: ${manifest.id} ${e.message}", e.cause)
            registry.unregister(manifest.id, NATIVE_POINTS)
            fail(manifest.id, e.message ?: "加载失败")
        } catch (e: IOException) {
            AppLogger.w(TAG, "加载原生插件失败: ${manifest.id}", e)
            fail(manifest.id, "读取插件文件失败：${e.message}")
        } finally {
            withContext(Dispatchers.IO) { marker.delete() }
        }
    }

    // 在运行副本上加载，安装目录不被文件锁占用，运行中也能升级和删除
    private fun prepare(manifest: PluginManifest, state: PluginState): File {
        val directory = manifest.directory ?: throw NativeLoadException("安装目录无效")
        val apiMajor = manifest.apiVersion?.substringBefore('.')?.toIntOrNull()
        if (apiMajor != PLUGIN_API_MAJOR || PluginApiVersion.MAJOR != PLUGIN_API_MAJOR) {
            throw NativeLoadException("插件需要 API ${manifest.apiVersion}，当前应用提供 $PLUGIN_API_MAJOR.x")
        }
        val jar = File(directory, PluginPaths.JAR)
        if (PluginPackage.sha256(jar) != state.nativeHash) throw NativeLoadException("${PluginPaths.JAR} 与安装时不一致，请重新导入")
        val workDirectory = File(runRoot, manifest.id)
        workDirectory.deleteRecursively()
        workDirectory.mkdirs()
        jar.copyTo(File(workDirectory, PluginPaths.JAR), overwrite = true)
        File(directory, "native").takeIf { it.isDirectory }?.copyRecursively(File(workDirectory, "native"), overwrite = true)
        return workDirectory
    }

    private fun instantiate(manifest: PluginManifest, workDirectory: File): MelodiaPlugin {
        val className = manifest.main ?: throw NativeLoadException("清单缺少入口类")
        val classLoader = NativePluginClassLoader(File(workDirectory, PluginPaths.JAR), hostLoader)
        // 插件 JAR 不可信，缺类、版本不符等都是 Error 而不是 Exception
        return try {
            val type = classLoader.loadClass(className)
            type.getDeclaredConstructor().newInstance() as? MelodiaPlugin
                ?: throw NativeLoadException("$className 没有实现 MelodiaPlugin")
        } catch (e: NativeLoadException) {
            throw e
        } catch (e: ClassNotFoundException) {
            throw NativeLoadException("找不到入口类 $className", e)
        } catch (e: NoSuchMethodException) {
            throw NativeLoadException("$className 需要公开的无参构造函数", e)
        } catch (e: Throwable) {
            throw NativeLoadException("无法创建 $className：${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    // 线程无法强制结束，超时后放弃等待，线程留在后台
    private fun runBounded(label: String, timeoutMs: Long, block: () -> Unit) {
        var failure: Throwable? = null
        val worker = Thread({
            try {
                block()
            } catch (e: Throwable) {
                failure = e
            }
        }, "native-plugin-$label").apply {
            isDaemon = true
            start()
        }
        worker.join(timeoutMs)
        if (worker.isAlive) throw NativeLoadException("$label 超过 ${timeoutMs / 1000} 秒没有返回")
        failure?.let { throw NativeLoadException("$label 失败：${it.message ?: it.javaClass.simpleName}", it) }
    }

    private fun unloadLocked(pluginId: String) {
        val target = loaded.remove(pluginId) ?: return
        registry.unregister(pluginId, NATIVE_POINTS)
        try {
            runBounded("onUnload", UNLOAD_TIMEOUT_MS) { target.entry.onUnload() }
        } catch (e: NativeLoadException) {
            AppLogger.w(TAG, "[$pluginId] ${e.message}", e.cause)
        }
        target.scope.cancel()
        // 类加载器不关闭：插件的后台线程可能还在懒加载类，运行副本下次启动时清理
        AppLogger.i(TAG, "原生插件已卸载: $pluginId")
    }

    private suspend fun fail(pluginId: String, reason: String) {
        _status.update { it + (pluginId to PluginRuntimeStatus(lastError = reason)) }
        try {
            failureHandler(pluginId, reason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "停用出错插件失败: $pluginId", e)
        }
    }

    internal inner class LoadedPlugin(val manifest: PluginManifest, val entry: MelodiaPlugin) : NativeCallGuard {
        private val consecutiveErrors = AtomicInteger(0)

        override val scope: CoroutineScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error -> recordError(error.message ?: error.javaClass.simpleName) }
        )

        override suspend fun <T> guarded(label: String, timeoutMs: Long, block: suspend () -> T): T? = try {
            withTimeout(timeoutMs) { withContext(Dispatchers.Default) { block() } }.also { consecutiveErrors.set(0) }
        } catch (e: TimeoutCancellationException) {
            recordError("$label 超过 ${timeoutMs / 1000} 秒没有返回")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLogger.w(TAG, "[${manifest.id}] $label 出错", e)
            recordError("$label 出错：${e.message ?: e.javaClass.simpleName}")
            null
        }

        override fun recordSuccess() {
            consecutiveErrors.set(0)
        }

        override fun recordError(reason: String) {
            _status.update { current -> current[manifest.id]?.let { current + (manifest.id to it.copy(lastError = reason)) } ?: current }
            if (consecutiveErrors.incrementAndGet() < MAX_CONSECUTIVE_ERRORS) return
            background.launch {
                lock.withLock { if (loaded[manifest.id] === this@LoadedPlugin) unloadLocked(manifest.id) }
                fail(manifest.id, "连续出错 $MAX_CONSECUTIVE_ERRORS 次，已自动停用：$reason")
            }
        }
    }

    internal companion object {
        val CALL_TIMEOUT_MS get() = PluginRuntime.SOURCE_TIMEOUT_MS
        val ACTION_TIMEOUT_MS get() = PluginRuntime.ACTION_TIMEOUT_MS
    }
}

// 插件回调的错误边界：异常、超时都按没有结果处理并计入连续出错
internal interface NativeCallGuard {
    val scope: CoroutineScope

    suspend fun <T> guarded(label: String, timeoutMs: Long, block: suspend () -> T): T?

    fun recordSuccess()

    fun recordError(reason: String)
}
