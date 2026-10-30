package com.lin0721.linmusic.core.plugin

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.File

sealed interface PluginParseResult {
    data class Success(val manifest: PluginManifest, val warnings: List<String>) : PluginParseResult
    data class Failure(val reason: String) : PluginParseResult
}

// 解析 plugin.json：结构错误与能力缺少权限直接失败，单项取值不合法时忽略该项并记入 warnings
object PluginParser {

    private val ID_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    private val ENTRY_ID_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")
    private val API_VERSION_PATTERN = Regex("^(\\d{1,4})\\.(\\d{1,4})$")
    private val HOST_PATTERN = Regex("^(\\*\\.)?[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
    private const val MAX_ID_LENGTH = 100
    private const val MAX_NAME_LENGTH = 40
    private const val MAX_VERSION_LENGTH = 20
    private const val MAX_DESCRIPTION_LENGTH = 200
    private const val MAX_HOSTS = 20
    private const val MAX_ENTRIES = 16

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String, directory: File? = null, platform: PluginPlatform = currentPluginPlatform): PluginParseResult {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: return PluginParseResult.Failure("${PluginPaths.MANIFEST} 不是有效的 JSON 对象")
        return parse(root, directory, platform)
    }

    fun parse(root: JsonObject, directory: File? = null, platform: PluginPlatform = currentPluginPlatform): PluginParseResult {
        val format = (root["format"] as? JsonPrimitive)?.intOrNull ?: return PluginParseResult.Failure("缺少 format 字段")
        if (format < 1) return PluginParseResult.Failure("format 取值无效")
        if (format > PLUGIN_FORMAT_VERSION) return PluginParseResult.Failure("插件需要更新版本的应用才能使用")

        val id = root.string("id")?.trim().orEmpty()
        if (id.length > MAX_ID_LENGTH || !ID_PATTERN.matches(id)) {
            return PluginParseResult.Failure("id 需要写成 com.example.name 这样的反向域名，只能用小写字母、数字和 _")
        }
        val name = root.string("name")?.trim().orEmpty()
        if (name.isEmpty()) return PluginParseResult.Failure("缺少插件名称")
        val version = root.string("version")?.trim().orEmpty()
        if (version.isEmpty()) return PluginParseResult.Failure("缺少版本号")

        val warnings = mutableListOf<String>()
        val compatibility = if (format >= 2) parseCompatibility(root, platform, warnings) else Compatibility()
        compatibility.failure?.let { return PluginParseResult.Failure(it) }
        val permissions = parsePermissions(root["permissions"], warnings)
        val hosts = parseHosts(root["network"], warnings)
        if (PluginPermission.NETWORK in permissions && hosts.isEmpty()) {
            return PluginParseResult.Failure("申请了网络权限，但 network.hosts 里没有列出允许访问的域名")
        }
        if (PluginPermission.NETWORK !in permissions && hosts.isNotEmpty()) {
            warnings += "没有申请网络权限，network.hosts 已忽略"
        }

        val contributes = when (val value = root["contributes"]) {
            null -> PluginContributions()
            is JsonObject -> parseContributions(value, warnings)
            else -> return PluginParseResult.Failure("contributes 不是对象")
        }
        if (contributes.isEmpty) return PluginParseResult.Failure("插件没有提供任何功能")
        missingPermission(contributes, permissions)?.let { return PluginParseResult.Failure(it) }

        val manifest = PluginManifest(
            id = id,
            name = name.take(MAX_NAME_LENGTH),
            version = version.take(MAX_VERSION_LENGTH),
            author = root.string("author")?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NAME_LENGTH),
            description = root.string("description")?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_DESCRIPTION_LENGTH),
            minAppVersion = root.string("minAppVersion")?.trim()?.takeIf { it.isNotEmpty() },
            permissions = permissions,
            networkHosts = if (PluginPermission.NETWORK in permissions) hosts else emptyList(),
            contributes = contributes,
            raw = root,
            directory = directory,
            kind = compatibility.kind,
            platforms = compatibility.platforms,
            apiVersion = compatibility.apiVersion
        )
        return PluginParseResult.Success(manifest, warnings)
    }

    private class Compatibility(
        val kind: PluginKind = PluginKind.SANDBOX,
        val platforms: Set<PluginPlatform> = emptySet(),
        val apiVersion: String? = null,
        val failure: String? = null
    )

    // format 2 起才有这些字段
    private fun parseCompatibility(root: JsonObject, platform: PluginPlatform, warnings: MutableList<String>): Compatibility {
        val kind = when (val key = root.string("kind")) {
            null -> PluginKind.SANDBOX
            else -> PluginKind.fromKey(key) ?: return Compatibility(failure = "未知的插件类型 $key")
        }
        if (kind == PluginKind.NATIVE) {
            val reason = if (platform == PluginPlatform.ANDROID) "Android 不支持原生插件" else "当前版本还不支持原生插件"
            return Compatibility(failure = reason)
        }
        val platforms = parsePlatforms(root["platforms"], warnings)
        if (platforms.isNotEmpty() && platform !in platforms) {
            return Compatibility(failure = "插件不支持当前平台")
        }
        val apiVersion = root.string("apiVersion")?.trim()
        if (apiVersion == null) return Compatibility(kind, platforms)
        val match = API_VERSION_PATTERN.matchEntire(apiVersion) ?: return Compatibility(failure = "apiVersion 需要写成 1.0 这样的版本号")
        val major = match.groupValues[1].toIntOrNull() ?: return Compatibility(failure = "apiVersion 取值无效")
        val minor = match.groupValues[2].toIntOrNull() ?: return Compatibility(failure = "apiVersion 取值无效")
        if (major != PLUGIN_API_MAJOR || minor > PLUGIN_API_MINOR) {
            return Compatibility(failure = "插件需要 API $apiVersion，当前应用提供 $PLUGIN_API_MAJOR.$PLUGIN_API_MINOR")
        }
        return Compatibility(kind, platforms, apiVersion)
    }

    private fun parsePlatforms(value: JsonElement?, warnings: MutableList<String>): Set<PluginPlatform> {
        if (value == null) return emptySet()
        val array = value as? JsonArray ?: run {
            warnings += "platforms 不是数组，已忽略"
            return emptySet()
        }
        return array.mapNotNullTo(LinkedHashSet()) { item ->
            val key = (item as? JsonPrimitive)?.takeIf { it.isString }?.content
            PluginPlatform.fromKey(key.orEmpty()) ?: null.also { warnings += "未知平台 ${key ?: item}，已忽略" }
        }
    }

    // 能力与权限一一对应：提供了某项能力却没申请对应权限时整个插件无效
    private fun missingPermission(contributes: PluginContributions, permissions: Set<PluginPermission>): String? = when {
        contributes.audioSources.isNotEmpty() && PluginPermission.AUDIO_SOURCE !in permissions ->
            "提供了音源，但没有申请 ${PluginPermission.AUDIO_SOURCE.key} 权限"
        contributes.lyricsSources.isNotEmpty() && PluginPermission.LYRICS_SOURCE !in permissions ->
            "提供了歌词源，但没有申请 ${PluginPermission.LYRICS_SOURCE.key} 权限"
        contributes.events.isNotEmpty() && PluginPermission.PLAYBACK_READ !in permissions ->
            "订阅了播放事件，但没有申请 ${PluginPermission.PLAYBACK_READ.key} 权限"
        else -> null
    }

    private fun parsePermissions(value: JsonElement?, warnings: MutableList<String>): Set<PluginPermission> {
        if (value == null) return emptySet()
        val array = value as? JsonArray ?: run {
            warnings += "permissions 不是数组，已忽略"
            return emptySet()
        }
        return array.mapNotNullTo(LinkedHashSet()) { item ->
            val key = (item as? JsonPrimitive)?.takeIf { it.isString }?.content
            PluginPermission.fromKey(key.orEmpty()) ?: null.also { warnings += "未知权限 ${key ?: item}，已忽略" }
        }
    }

    private fun parseHosts(value: JsonElement?, warnings: MutableList<String>): List<String> {
        val network = value as? JsonObject ?: return emptyList()
        val array = network["hosts"] as? JsonArray ?: return emptyList()
        val hosts = array.mapNotNull { item ->
            val host = (item as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.lowercase()
            if (host != null && HOST_PATTERN.matches(host)) host else null.also { warnings += "域名 ${host ?: item} 无效，已忽略" }
        }.distinct()
        if (hosts.size > MAX_HOSTS) warnings += "允许访问的域名超过 $MAX_HOSTS 个，只保留前 $MAX_HOSTS 个"
        return hosts.take(MAX_HOSTS)
    }

    private fun parseContributions(root: JsonObject, warnings: MutableList<String>): PluginContributions {
        val layouts = LinkedHashMap<String, PluginUiRef>()
        (root["playerLayouts"] as? JsonObject)?.forEach { (area, value) ->
            if (area !in PLAYER_LAYOUT_AREAS) {
                warnings += "未知的播放器区域 $area，已忽略"
                return@forEach
            }
            parseUiRef(value)?.let { layouts[area] = it } ?: run { warnings += "播放器区域 $area 的界面描述无效，已忽略" }
        }
        val pages = parseEntries(root["pages"], "页面", warnings) { item, id ->
            val title = item.string("title")?.trim()?.takeIf { it.isNotEmpty() } ?: return@parseEntries null.also {
                warnings += "页面 $id 缺少标题，已忽略"
            }
            val ui = parseUiRef(item["ui"]) ?: return@parseEntries null.also { warnings += "页面 $id 的界面描述无效，已忽略" }
            val placementKey = item.string("placement")
            val placement = if (placementKey == null) {
                PluginPagePlacement.SIDEBAR
            } else {
                PluginPagePlacement.fromKey(placementKey) ?: PluginPagePlacement.SIDEBAR.also {
                    warnings += "页面 $id 的位置 $placementKey 无效，按侧栏处理"
                }
            }
            PluginPage(id, title.take(MAX_NAME_LENGTH), item.string("icon")?.trim()?.takeIf { it.isNotEmpty() }, ui, placement)
        }
        val audioSources = parseSources(root["audioSources"], "音源", warnings)
        val lyricsSources = parseSources(root["lyricsSources"], "歌词源", warnings)
        val events = (root["events"] as? JsonArray)?.mapNotNullTo(LinkedHashSet()) { item ->
            val key = (item as? JsonPrimitive)?.takeIf { it.isString }?.content
            PluginEvent.fromKey(key.orEmpty()) ?: null.also { warnings += "未知事件 ${key ?: item}，已忽略" }
        }.orEmpty()
        return PluginContributions(layouts, pages, audioSources, lyricsSources, events)
    }

    private fun parseSources(value: JsonElement?, label: String, warnings: MutableList<String>): List<PluginSourceEntry> =
        parseEntries(value, label, warnings) { item, id ->
            PluginSourceEntry(id, item.string("name")?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NAME_LENGTH) ?: id)
        }

    // 数组里的每项都要有合法且不重复的 id，不合法的单项忽略
    private fun <T> parseEntries(
        value: JsonElement?,
        label: String,
        warnings: MutableList<String>,
        build: (JsonObject, String) -> T?
    ): List<T> {
        if (value == null) return emptyList()
        val array = value as? JsonArray ?: run {
            warnings += "${label}列表不是数组，已忽略"
            return emptyList()
        }
        val seen = HashSet<String>()
        val result = array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null.also { warnings += "${label}列表里有无效项，已忽略" }
            val id = obj.string("id")?.trim().orEmpty()
            when {
                !ENTRY_ID_PATTERN.matches(id) -> null.also { warnings += "$label id「$id」无效，已忽略" }
                !seen.add(id) -> null.also { warnings += "$label id「$id」重复，已忽略" }
                else -> build(obj, id)
            }
        }
        if (result.size > MAX_ENTRIES) warnings += "${label}超过 $MAX_ENTRIES 个，只保留前 $MAX_ENTRIES 个"
        return result.take(MAX_ENTRIES)
    }

    private fun parseUiRef(value: JsonElement?): PluginUiRef? = when (value) {
        is JsonObject -> PluginUiRef.Inline(value)
        is JsonPrimitive -> value.takeIf { it.isString }?.content?.takeIf(PluginPaths::isUi)?.let(PluginUiRef::File)
        else -> null
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
