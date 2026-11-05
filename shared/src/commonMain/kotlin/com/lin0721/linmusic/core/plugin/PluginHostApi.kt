package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "PluginHostApi"

// 宿主 API 拒绝或执行失败，消息原样回给插件
class PluginCallException(message: String) : Exception(message)

// 插件脚本可调用的宿主能力；每次调用都按清单核对权限
class PluginHostApi(
    private val store: PluginStore,
    private val http: PluginHttpClient = OkHttpPluginHttpClient(),
    private val playback: () -> PlaybackController? = { null },
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val notices: PluginNotices = PluginNotices(),
    private val netease: PluginNeteaseApi? = null,
    private val content: PluginContent? = null,
    private val confirmations: PluginConfirmations = PluginConfirmations(),
    private val links: PluginLinks = PluginLinks(),
    private val now: () -> Long = System::currentTimeMillis
) {
    private val storageLocks = ConcurrentHashMap<String, Mutex>()
    private val linkDenials = ConcurrentHashMap<String, Long>()
    private val pendingLinks = ConcurrentHashMap.newKeySet<String>()
    private val json = Json { prettyPrint = false }

    // 插件页面的绑定数据，key 为 插件 id/页面 id
    private val _uiStates = MutableStateFlow<Map<String, JsonObject>>(emptyMap())
    val uiStates: StateFlow<Map<String, JsonObject>> = _uiStates.asStateFlow()

    suspend fun handle(manifest: PluginManifest, method: String, args: JsonElement): JsonElement = when (method) {
        "http.request" -> request(manifest, args.asObject("http.request"))
        "storage.get" -> storageGet(manifest, args.asObject(method).requireString("key"))
        "storage.set" -> storageSet(manifest, args.asObject(method))
        "storage.remove" -> storageRemove(manifest, args.asObject(method).requireString("key"))
        "player.current" -> current(manifest)
        "player.state" -> playbackState(manifest)
        "player.play" -> control(manifest) { it.resume() }
        "player.pause" -> control(manifest) { it.pause() }
        "player.next" -> control(manifest) { it.playNext() }
        "player.previous" -> control(manifest) { it.playPrevious() }
        "player.enqueue" -> enqueue(manifest, args.asObject(method))
        "ui.setState" -> setUiState(manifest, args.asObject(method))
        "ui.toast" -> toast(manifest, args.asObject(method))
        METHOD_OPEN_URL -> openUrl(manifest, args.asObject(method))
        "settings.get" -> settingValue(manifest, args.asObject(method).requireString("key"))
        "settings.all" -> settings(manifest)
        METHOD_NETEASE -> neteaseCall(manifest, args.asObject(method))
        "library.likedSongIds" -> neteaseApi().likedSongIds(manifest)
        "library.playlists" -> neteaseApi().libraryPlaylists(manifest)
        "content.open" -> openContent(manifest, args.asObject(method))
        else -> throw PluginCallException("不支持的调用 $method")
    }

    fun notice(message: String) = notices.post(message)

    // 页面上的开关与输入框写回状态
    fun setUiField(pluginId: String, pageId: String, field: String, value: JsonElement) {
        val key = "$pluginId/$pageId"
        _uiStates.update { states ->
            states + (key to JsonObject((states[key] ?: JsonObject(emptyMap())) + (field to value)))
        }
    }

    // 页面里的歌曲列表：从点击的那首开始播放整个列表
    suspend fun playTracks(manifest: PluginManifest, tracks: List<PluginTrack>, startIndex: Int) {
        if (PluginPermission.PLAYBACK_CONTROL !in manifest.permissions || startIndex !in tracks.indices) return
        val controller = playback() ?: return
        val items = tracks.map { QueueItem(songId = it.songId, title = it.title, artist = it.artist, coverUrl = it.cover) }
        withContext(mainDispatcher) { controller.playQueue(items, startIndex) }
    }

    // 删除插件时一并清掉页面数据
    fun clearUiState(pluginId: String) {
        _uiStates.update { states -> states.filterKeys { !it.startsWith("$pluginId/") } }
    }

    private fun openContent(manifest: PluginManifest, args: JsonObject): JsonElement {
        require(manifest, PluginPermission.CONTENT_SOURCE)
        val source = args.requireString("source")
        if (manifest.contributes.contentSources.none { it.id == source }) throw PluginCallException("清单里没有内容源 $source")
        val target = content ?: throw PluginCallException("内容入口尚未就绪")
        target.open(manifest.id, source, args.requireString("id"), args.string("title")?.take(MAX_TOAST_LENGTH).orEmpty())
        return JsonNull
    }

    private fun neteaseApi(): PluginNeteaseApi = netease ?: throw PluginCallException("网易接口尚未就绪")

    private suspend fun neteaseCall(manifest: PluginManifest, args: JsonObject): JsonElement =
        neteaseApi().call(manifest, args.requireString("method"), args["params"] as? JsonObject ?: JsonObject(emptyMap()))

    private fun toast(manifest: PluginManifest, args: JsonObject): JsonElement {
        require(manifest, PluginPermission.UI_NOTIFY)
        // 带上插件名，避免提示被当成应用自己发的
        notices.post("${manifest.name}：${args.requireString("text").take(MAX_TOAST_LENGTH)}")
        return JsonNull
    }

    // 每次都要用户确认，弹窗只显示域名，避免把链接里的令牌展示出来
    private suspend fun openUrl(manifest: PluginManifest, args: JsonObject): JsonElement {
        require(manifest, PluginPermission.UI_OPEN_URL)
        val url = args.requireString("url").trim()
        if (url.length > MAX_URL_LENGTH) throw PluginCallException("网址过长")
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            throw PluginCallException("网址无效")
        }
        if (uri.scheme?.lowercase() != "https") throw PluginCallException("只能打开 https 网址")
        val host = uri.host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotEmpty() } ?: throw PluginCallException("网址无效")
        // 拒绝后冷却一段时间，插件不能靠重试反复弹窗
        val denied = linkDenials[manifest.id]
        if (denied != null && now() - denied < LINK_DENIAL_COOLDOWN_MS) throw PluginCallException("用户刚拒绝了打开网页，请稍后再试")
        // 同一插件同时只排一个确认，避免连发请求在拒绝后继续逐个弹窗
        if (!pendingLinks.add(manifest.id)) throw PluginCallException("已有打开网页的请求在等待确认")
        var accepted = false
        try {
            accepted = confirmations.ask("打开网页？", "「${manifest.name}」请求在浏览器打开 $host 的网页。")
        } finally {
            pendingLinks.remove(manifest.id)
            // 超时或调用被取消也按拒绝冷却，否则插件能靠超时反复弹窗
            if (!accepted) linkDenials[manifest.id] = now()
        }
        if (!accepted) throw PluginCallException("用户拒绝了打开网页")
        links.open(uri.toASCIIString())
        return JsonNull
    }

    private suspend fun settingValue(manifest: PluginManifest, key: String): JsonElement {
        if (manifest.contributes.settings.none { it.id == key }) throw PluginCallException("清单里没有设置项 $key")
        return settings(manifest)[key] ?: JsonNull
    }

    // 清单声明的全部设置项，没保存过的取默认值
    suspend fun settings(manifest: PluginManifest): JsonObject = withStorage(manifest, SETTINGS_FILE) { saved ->
        JsonObject(manifest.contributes.settings.associate { it.id to it.normalize(saved[it.id]) })
    }

    suspend fun setSetting(manifest: PluginManifest, key: String, value: JsonElement) {
        val field = manifest.contributes.settings.firstOrNull { it.id == key } ?: return
        withStorage(manifest, SETTINGS_FILE) { saved ->
            writeStorage(manifest, JsonObject(saved + (key to field.normalize(value))), SETTINGS_FILE)
        }
    }

    private fun require(manifest: PluginManifest, permission: PluginPermission) {
        if (permission !in manifest.permissions) throw PluginCallException("缺少权限 ${permission.key}")
    }

    private suspend fun request(manifest: PluginManifest, args: JsonObject): JsonElement {
        require(manifest, PluginPermission.NETWORK)
        var url = args.requireString("url")
        var method = (args.string("method") ?: "GET").uppercase()
        if (method !in HTTP_METHODS) throw PluginCallException("不支持的请求方法 $method")
        val headers = (args["headers"] as? JsonObject)?.mapNotNull { (key, value) ->
            val text = (value as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            if (key.equals("Host", ignoreCase = true)) null else key to text
        }?.toMap().orEmpty()
        var body: String? = when (val value = args["body"]) {
            null, JsonNull -> null
            is JsonPrimitive -> value.content
            else -> value.toString()
        }
        repeat(MAX_REDIRECTS + 1) {
            checkHost(manifest, url)
            val payloadBody = when (method) {
                "GET", "HEAD" -> null
                else -> body ?: ""
            }
            val response = try {
                http.execute(PluginHttpRequest(url, method, headers, payloadBody))
            } catch (e: IOException) {
                throw PluginCallException("网络请求失败：${e.message}")
            } catch (e: IllegalArgumentException) {
                throw PluginCallException("请求参数无效：${e.message}")
            }
            val location = response.headers.entries.firstOrNull { it.key.equals("Location", ignoreCase = true) }?.value
            if (response.status !in REDIRECT_CODES || location.isNullOrBlank()) {
                return buildJsonObject {
                    put("status", response.status)
                    put("headers", JsonObject(response.headers.mapValues { JsonPrimitive(it.value) }))
                    put("body", response.body)
                }
            }
            url = resolve(url, location)
            if (response.status == 303 || (response.status in 301..302 && method == "POST")) {
                method = "GET"
                body = null
            }
        }
        throw PluginCallException("重定向次数过多")
    }

    // 只允许 http(s)，域名须在清单 network.hosts 内；*.example.com 只匹配子域名
    private fun checkHost(manifest: PluginManifest, url: String) {
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            throw PluginCallException("网址无效：$url")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") throw PluginCallException("只允许 http 与 https 请求")
        val host = uri.host?.lowercase()?.trimEnd('.') ?: throw PluginCallException("网址无效：$url")
        if (!isHostAllowed(manifest.networkHosts, host)) throw PluginCallException("不允许访问 $host")
    }

    private fun resolve(base: String, location: String): String = try {
        URI(base).resolve(location.trim()).toString()
    } catch (e: URISyntaxException) {
        throw PluginCallException("重定向地址无效：$location")
    } catch (e: IllegalArgumentException) {
        throw PluginCallException("重定向地址无效：$location")
    }

    private suspend fun storageGet(manifest: PluginManifest, key: String): JsonElement {
        require(manifest, PluginPermission.STORAGE)
        return withStorage(manifest) { it[key] ?: JsonNull }
    }

    private suspend fun storageSet(manifest: PluginManifest, args: JsonObject): JsonElement {
        require(manifest, PluginPermission.STORAGE)
        val key = args.requireString("key")
        val value = args["value"] ?: JsonNull
        return withStorage(manifest) { data ->
            val updated = if (value == JsonNull) data - key else data + (key to value)
            writeStorage(manifest, JsonObject(updated))
            JsonNull
        }
    }

    private suspend fun storageRemove(manifest: PluginManifest, key: String): JsonElement {
        require(manifest, PluginPermission.STORAGE)
        return withStorage(manifest) { data ->
            if (key in data) writeStorage(manifest, JsonObject(data - key))
            JsonNull
        }
    }

    private suspend fun <T> withStorage(manifest: PluginManifest, fileName: String = STORAGE_FILE, block: (JsonObject) -> T): T {
        val lock = storageLocks.getOrPut(manifest.id) { Mutex() }
        return lock.withLock { withContext(Dispatchers.IO) { block(readStorage(manifest, fileName)) } }
    }

    private fun storageFile(manifest: PluginManifest, fileName: String): File =
        store.dataDirectoryOf(manifest.id)?.let { File(it, fileName) } ?: throw PluginCallException("插件 id 无效")

    private fun readStorage(manifest: PluginManifest, fileName: String): JsonObject {
        val file = storageFile(manifest, fileName)
        if (!file.isFile) return JsonObject(emptyMap())
        return try {
            Json.parseToJsonElement(file.readText()) as? JsonObject ?: preserveCorrupt(manifest, file)
        } catch (e: SerializationException) {
            preserveCorrupt(manifest, file)
        } catch (e: IllegalArgumentException) {
            preserveCorrupt(manifest, file)
        } catch (e: IOException) {
            throw PluginCallException("读取插件数据失败")
        }
    }

    // 损坏的文件留一份，下次写入不会把线索也覆盖掉
    private fun preserveCorrupt(manifest: PluginManifest, file: File): JsonObject {
        AppLogger.w(TAG, "[${manifest.id}] ${file.name} 损坏，已另存并按空数据处理")
        val copy = File(file.parentFile, "${file.name}.corrupt")
        copy.delete()
        file.renameTo(copy)
        return JsonObject(emptyMap())
    }

    private fun writeStorage(manifest: PluginManifest, data: JsonObject, fileName: String = STORAGE_FILE) {
        val text = json.encodeToString(JsonObject.serializer(), data)
        if (text.toByteArray(Charsets.UTF_8).size > MAX_STORAGE_BYTES) {
            throw PluginCallException("插件数据超过 ${MAX_STORAGE_BYTES / 1024 / 1024}MB")
        }
        try {
            writeFileAtomically(storageFile(manifest, fileName), text)
        } catch (e: IOException) {
            throw PluginCallException("保存插件数据失败")
        }
    }

    private fun controller(): PlaybackController = playback() ?: throw PluginCallException("播放器尚未就绪")

    private fun current(manifest: PluginManifest): JsonElement {
        require(manifest, PluginPermission.PLAYBACK_READ)
        val nowPlaying = controller().nowPlaying.value ?: return JsonNull
        return buildJsonObject {
            put("id", nowPlaying.mediaId)
            nowPlaying.songId?.takeIf { it > 0 }?.let { put("songId", it) }
            put("title", nowPlaying.title)
            put("artist", nowPlaying.artist)
            put("cover", nowPlaying.artworkUri)
        }
    }

    private fun playbackState(manifest: PluginManifest): JsonElement {
        require(manifest, PluginPermission.PLAYBACK_READ)
        val controller = controller()
        return buildJsonObject {
            put("playing", controller.isPlaying.value)
            put("positionMs", controller.currentPosition.value)
            put("durationMs", controller.duration.value)
        }
    }

    // 播放器要求在主线程操作
    private suspend fun control(manifest: PluginManifest, action: (PlaybackController) -> Unit): JsonElement {
        require(manifest, PluginPermission.PLAYBACK_CONTROL)
        val controller = controller()
        withContext(mainDispatcher) { action(controller) }
        return JsonNull
    }

    private suspend fun enqueue(manifest: PluginManifest, args: JsonObject): JsonElement {
        val songId = (args["songId"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
            ?: throw PluginCallException("enqueue 需要有效的 songId")
        val item = QueueItem(
            songId = songId,
            title = args.string("title")?.take(MAX_TEXT_LENGTH) ?: "",
            artist = args.string("artist")?.take(MAX_TEXT_LENGTH) ?: "",
            coverUrl = args.string("cover")?.take(MAX_TEXT_LENGTH)?.let { PluginBinding.cover(manifest, it) } ?: ""
        )
        return control(manifest) { it.appendToQueue(listOf(item)) }
    }

    private fun setUiState(manifest: PluginManifest, args: JsonObject): JsonElement {
        val page = args.requireString("page")
        if (manifest.contributes.pages.none { it.id == page }) throw PluginCallException("清单里没有页面 $page")
        val patch = args["patch"] as? JsonObject ?: throw PluginCallException("setState 需要对象")
        val key = "${manifest.id}/$page"
        _uiStates.update { states ->
            val merged = JsonObject((states[key] ?: JsonObject(emptyMap())) + patch)
            if (merged.toString().length > MAX_UI_STATE_CHARS) throw PluginCallException("页面数据过大")
            states + (key to merged)
        }
        return JsonNull
    }

    private fun JsonElement.asObject(method: String): JsonObject =
        this as? JsonObject ?: throw PluginCallException("$method 参数应为对象")

    private fun JsonObject.requireString(key: String): String =
        string(key)?.takeIf { it.isNotEmpty() } ?: throw PluginCallException("缺少参数 $key")

    companion object {
        const val METHOD_NETEASE = "netease.call"
        const val METHOD_OPEN_URL = "ui.openUrl"
        private const val STORAGE_FILE = "storage.json"
        private const val SETTINGS_FILE = "settings.json"
        private const val MAX_TOAST_LENGTH = 120
        private const val MAX_STORAGE_BYTES = 1024 * 1024
        private const val MAX_UI_STATE_CHARS = 256 * 1024
        private const val MAX_TEXT_LENGTH = 500
        private const val MAX_REDIRECTS = 5
        private const val MAX_URL_LENGTH = 4096
        private const val LINK_DENIAL_COOLDOWN_MS = 30_000L
        private val HTTP_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        fun isHostAllowed(patterns: List<String>, host: String): Boolean = patterns.any { pattern ->
            if (pattern.startsWith("*.")) host.endsWith(pattern.substring(1)) else host == pattern
        }
    }
}
