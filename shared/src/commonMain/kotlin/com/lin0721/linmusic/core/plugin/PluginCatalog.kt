package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.source.SourceHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.TimeUnit

private const val TAG = "PluginCatalog"

const val DEFAULT_PLUGIN_CATALOG_URL = "https://raw.githubusercontent.com/rinchao0721/melodia-plugins/main/index.json"

private const val CATALOG_FORMAT = 1
private const val MAX_CATALOG_ENTRIES = 1000
private const val MAX_CATALOG_BYTES = 4L * 1024 * 1024
private const val MAX_SANDBOX_DOWNLOAD_BYTES = 10L * 1024 * 1024
private const val MAX_NATIVE_DOWNLOAD_BYTES = 200L * 1024 * 1024
private const val MAX_NAME_LENGTH = 40
private const val MAX_VERSION_LENGTH = 20
private const val MAX_URL_LENGTH = 2000
private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
private const val BUFFER_SIZE = 64 * 1024
private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

@Serializable
data class PluginCatalogDownload(val url: String, val sha256: String, val size: Long)

@Serializable
data class PluginCatalogEntry(
    val id: String,
    val name: String,
    val version: String,
    val kind: String = PluginKind.SANDBOX.key,
    val author: String? = null,
    val description: String? = null,
    val platforms: List<String> = emptyList(),
    val apiVersion: String? = null,
    val minAppVersion: String? = null,
    val permissions: List<String> = emptyList(),
    // 源码地址，原生插件必填
    val source: String? = null,
    val download: PluginCatalogDownload
) {
    val isNative: Boolean get() = kind == PluginKind.NATIVE.key
}

data class PluginCatalog(
    val entries: List<PluginCatalogEntry>,
    // 不合规被跳过的条目数
    val skipped: Int = 0,
    val updatedAt: String? = null
)

object PluginCatalogParser {
    private val json = Json { ignoreUnknownKeys = true }

    // 整体格式不对返回 null；单个条目不合规时跳过，不影响其他条目
    fun parse(text: String, platform: PluginPlatform = currentPluginPlatform): PluginCatalog? {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            AppLogger.w(TAG, "目录不是有效的 JSON", e)
            null
        } ?: return null
        if ((root["format"] as? JsonPrimitive)?.intOrNull != CATALOG_FORMAT) return null
        val items = root["plugins"] as? JsonArray ?: return null
        val entries = LinkedHashMap<String, PluginCatalogEntry>()
        var skipped = 0
        items.take(MAX_CATALOG_ENTRIES).forEach { item ->
            val entry = decode(item)
            when {
                entry == null -> skipped++
                entry.id in entries -> skipped++
                // Android 不支持原生插件，不显示
                entry.isNative && platform == PluginPlatform.ANDROID -> Unit
                else -> entries[entry.id] = entry
            }
        }
        return PluginCatalog(entries.values.toList(), skipped, (root["updatedAt"] as? JsonPrimitive)?.takeIf { it.isString }?.content)
    }

    // 不能在当前应用上安装的原因
    fun incompatibility(entry: PluginCatalogEntry, platform: PluginPlatform = currentPluginPlatform): String? {
        if (entry.platforms.isNotEmpty() && platform.key !in entry.platforms) return "不支持当前平台"
        entry.minAppVersion?.let(::minAppVersionFailure)?.let { return it }
        return entry.apiVersion?.let(PluginParser::apiVersionFailure)
    }

    private fun decode(item: JsonElement): PluginCatalogEntry? {
        val entry = try {
            json.decodeFromJsonElement<PluginCatalogEntry>(item)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        val download = entry.download
        val maxBytes = if (entry.isNative) MAX_NATIVE_DOWNLOAD_BYTES else MAX_SANDBOX_DOWNLOAD_BYTES
        val valid = PLUGIN_ID_PATTERN.matches(entry.id) &&
            entry.name.isNotBlank() && entry.name.length <= MAX_NAME_LENGTH &&
            entry.version.isNotBlank() && entry.version.length <= MAX_VERSION_LENGTH &&
            (entry.kind == PluginKind.SANDBOX.key || entry.kind == PluginKind.NATIVE.key) &&
            isHttps(download.url) &&
            SHA256_PATTERN.matches(download.sha256) &&
            download.size in 1..maxBytes &&
            (!entry.isNative || entry.source?.let(::isHttps) == true)
        return entry.takeIf { valid }
    }
}

internal fun isHttps(url: String): Boolean {
    if (url.length > MAX_URL_LENGTH) return false
    return try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()
    } catch (e: URISyntaxException) {
        false
    }
}

// 与语义化版本一致：+ 后的构建信息不参与比较，带 - 后缀的预发布版低于同号正式版
fun comparePluginVersions(first: String, second: String): Int {
    val left = first.trim().removePrefix("v").substringBefore('+')
    val right = second.trim().removePrefix("v").substringBefore('+')
    val core = compareVersionSegments(left.substringBefore('-'), right.substringBefore('-'))
    if (core != 0) return core
    val leftSuffix = left.substringAfter('-', "")
    val rightSuffix = right.substringAfter('-', "")
    return when {
        leftSuffix.isEmpty() && rightSuffix.isEmpty() -> 0
        leftSuffix.isEmpty() -> 1
        rightSuffix.isEmpty() -> -1
        else -> compareVersionSegments(leftSuffix, rightSuffix)
    }
}

// 按数字逐段比较，非数字段按文本比较，缺的段视为 0
private fun compareVersionSegments(first: String, second: String): Int {
    val left = first.split('.')
    val right = second.split('.')
    for (index in 0 until maxOf(left.size, right.size)) {
        val a = left.getOrElse(index) { "0" }
        val b = right.getOrElse(index) { "0" }
        val aNumber = a.toLongOrNull()
        val bNumber = b.toLongOrNull()
        val result = if (aNumber != null && bNumber != null) aNumber.compareTo(bNumber) else a.compareTo(b)
        if (result != 0) return result
    }
    return 0
}

// 目录里版本更新且能安装的已装插件，key 为插件 id
fun pluginUpdates(installed: List<InstalledPlugin>, entries: List<PluginCatalogEntry>): Map<String, PluginCatalogEntry> {
    val byId = entries.associateBy { it.id }
    return installed.mapNotNull { plugin ->
        val entry = byId[plugin.id] ?: return@mapNotNull null
        val current = plugin.manifest?.version ?: return@mapNotNull null
        (plugin.id to entry).takeIf {
            comparePluginVersions(entry.version, current) > 0 && PluginCatalogParser.incompatibility(entry) == null
        }
    }.toMap()
}

// 包内信息和目录不一致时的原因
fun pluginCatalogMismatch(entry: PluginCatalogEntry, manifest: PluginManifest): String? = when {
    manifest.id != entry.id -> "插件包的 id 与目录不一致"
    manifest.version != entry.version -> "插件包的版本与目录不一致"
    manifest.kind.key != entry.kind -> "插件包的类型与目录不一致"
    else -> null
}

// 目录哈希来自目录本身，自定义目录下无法证明内容经过审核
fun pluginCatalogWarning(preview: PluginImportStep.Preview): String? =
    if (preview.unreviewed && preview.staged.manifest.kind == PluginKind.NATIVE) {
        "这个原生插件来自自定义目录，没有经过官方目录审核，无法确认内容未被改动。只在完全信任目录来源时安装"
    } else {
        null
    }

// 按名称、作者、说明和 id 搜索，忽略大小写
fun filterCatalog(entries: List<PluginCatalogEntry>, query: String): List<PluginCatalogEntry> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return entries
    return entries.filter { entry ->
        listOfNotNull(entry.name, entry.id, entry.author, entry.description).any { it.contains(keyword, ignoreCase = true) }
    }
}

// 目录列表里的类型、作者与权限数
fun catalogEntryMeta(entry: PluginCatalogEntry): String = listOfNotNull(
    "原生".takeIf { entry.isNative },
    entry.author,
    "${entry.permissions.size} 项权限".takeIf { entry.permissions.isNotEmpty() }
).joinToString(" · ")

data class PluginCatalogAction(val text: String, val enabled: Boolean)

// 目录条目右侧的按钮；progress 非空表示正有任务在下载或解包
fun catalogActionLabel(installed: InstalledPlugin?, hasUpdate: Boolean, problem: String?, progress: Float?): PluginCatalogAction = when {
    problem != null -> PluginCatalogAction("不可安装", false)
    installed != null && !hasUpdate -> PluginCatalogAction("已安装", false)
    progress != null && progress > 0f -> PluginCatalogAction("${(progress * 100).toInt().coerceIn(0, 100)}%", true)
    hasUpdate -> PluginCatalogAction("更新", true)
    else -> PluginCatalogAction("安装", true)
}

class PluginCatalogException(message: String) : Exception(message)

interface PluginCatalogHttp {
    suspend fun fetchText(url: String, maxBytes: Long): String

    suspend fun download(url: String, maxBytes: Long, target: File, onProgress: (Long) -> Unit)
}

// 跟随重定向以支持 GitHub 发布文件，但不允许降级到 http
class OkHttpPluginCatalogHttp(
    private val client: OkHttpClient = SourceHttpClient.client.newBuilder()
        .followRedirects(true)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : PluginCatalogHttp {

    override suspend fun fetchText(url: String, maxBytes: Long): String = withContext(Dispatchers.IO) {
        execute(url, maxBytes).use { response ->
            val body = response.body ?: throw IOException("响应为空")
            val peeked = response.peekBody(maxBytes + 1)
            if (peeked.contentLength() > maxBytes) throw IOException("响应超过大小限制")
            body.close()
            peeked.string()
        }
    }

    override suspend fun download(url: String, maxBytes: Long, target: File, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        execute(url, maxBytes).use { response ->
            val input = response.body?.byteStream() ?: throw IOException("响应为空")
            target.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw IOException("下载内容超过目录声明的大小")
                    output.write(buffer, 0, read)
                    onProgress(total)
                }
            }
        }
    }

    private fun execute(url: String, maxBytes: Long): Response {
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            throw IOException("地址无效", e)
        }
        val response = client.newCall(request).execute()
        val failure = when {
            !response.isSuccessful -> "服务器返回 ${response.code}"
            !response.request.url.isHttps -> "地址被重定向到了非 https"
            (response.body?.contentLength() ?: -1L) > maxBytes -> "响应超过大小限制"
            else -> null
        }
        if (failure != null) {
            response.close()
            throw IOException(failure)
        }
        return response
    }
}

data class PluginCatalogState(
    val url: String = DEFAULT_PLUGIN_CATALOG_URL,
    val isDefault: Boolean = true,
    val entries: List<PluginCatalogEntry> = emptyList(),
    val skipped: Int = 0,
    val loading: Boolean = false,
    val error: String? = null
)

// 目录读取与下载校验；读取失败时保留上次成功的结果
class PluginCatalogService(
    private val store: PluginStore,
    private val http: PluginCatalogHttp = OkHttpPluginCatalogHttp(),
    private val platform: PluginPlatform = currentPluginPlatform,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val loadLock = Mutex()
    private val _state = MutableStateFlow(PluginCatalogState())
    val state: StateFlow<PluginCatalogState> = _state.asStateFlow()

    private fun currentUrl(): String = store.catalogUrl() ?: DEFAULT_PLUGIN_CATALOG_URL

    // 以存储为准，state 要等 load 后才更新
    suspend fun isCustom(): Boolean = withContext(Dispatchers.IO) { store.catalogUrl() != null }

    // 打开设置时调用，先显示缓存再联网
    suspend fun load() = loadLock.withLock {
        withContext(Dispatchers.IO) {
            val url = currentUrl()
            val cached = store.catalogCache()?.let { PluginCatalogParser.parse(it, platform) }
            _state.update {
                it.copy(
                    url = url,
                    isDefault = url == DEFAULT_PLUGIN_CATALOG_URL,
                    entries = cached?.entries ?: it.entries,
                    skipped = cached?.skipped ?: it.skipped,
                    loading = true,
                    error = null
                )
            }
            fetch(url)
        }
    }

    // 启动时调用，距上次成功读取不足一天时不联网，只返回缓存结果
    suspend fun entriesForStartup(): List<PluginCatalogEntry> = loadLock.withLock {
        withContext(Dispatchers.IO) {
            val checkedAt = store.catalogCheckedAt()
            if (now() - checkedAt >= CHECK_INTERVAL_MS) fetch(currentUrl())
            store.catalogCache()?.let { PluginCatalogParser.parse(it, platform) }?.entries.orEmpty()
        }
    }

    private suspend fun fetch(url: String) {
        val failure = try {
            val text = http.fetchText(url, MAX_CATALOG_BYTES)
            val catalog = PluginCatalogParser.parse(text, platform)
            if (catalog != null) {
                store.writeCatalogCache(text)
                _state.update { it.copy(entries = catalog.entries, skipped = catalog.skipped) }
                null
            } else {
                "目录格式无效"
            }
        } catch (e: CancellationException) {
            _state.update { it.copy(loading = false) }
            throw e
        } catch (e: IOException) {
            AppLogger.w(TAG, "读取目录失败: $url", e)
            "读取目录失败：${e.message ?: "网络错误"}"
        }
        _state.update { it.copy(loading = false, error = failure) }
    }

    // url 为空表示恢复默认目录；地址无效时返回原因
    suspend fun setUrl(url: String?): String? {
        val value = url?.trim()?.takeIf { it.isNotEmpty() && it != DEFAULT_PLUGIN_CATALOG_URL }
        if (value != null && !isHttps(value)) return "目录地址需要是 https 开头的完整网址"
        withContext(Dispatchers.IO) {
            store.setCatalogUrl(value)
            store.writeCatalogCache(null)
        }
        _state.update { it.copy(entries = emptyList(), skipped = 0, error = null) }
        load()
        return null
    }

    // 校验通过后返回临时文件，由调用方删除；失败时抛 PluginCatalogException
    suspend fun download(entry: PluginCatalogEntry, onProgress: (Long) -> Unit): File {
        val target = withContext(Dispatchers.IO) { store.downloadFile() }
        val expected = entry.download
        try {
            http.download(expected.url, expected.size, target, onProgress)
            val failure = withContext(Dispatchers.IO) {
                when {
                    target.length() != expected.size -> "下载内容的大小与目录不一致"
                    PluginPackage.sha256(target) != expected.sha256 -> "下载内容没有通过校验"
                    else -> null
                }
            }
            if (failure != null) throw PluginCatalogException(failure)
            return target
        } catch (e: IOException) {
            target.delete()
            throw PluginCatalogException("下载失败：${e.message ?: "网络错误"}")
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
    }
}
