package com.lin0721.linmusic.core.plugin

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File

sealed interface PluginParseResult {
    data class Success(val manifest: PluginManifest, val warnings: List<String>) : PluginParseResult
    data class Failure(val reason: String) : PluginParseResult
}

// 解析 plugin.json：结构错误与能力缺少权限直接失败，单项取值不合法时忽略该项并记入 warnings
object PluginParser {

    private val ENTRY_ID_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")
    private val API_VERSION_PATTERN = Regex("^(\\d{1,4})\\.(\\d{1,4})$")
    private val CLASS_NAME_PATTERN = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+$")
    private val HOST_PATTERN = Regex("^(\\*\\.)?[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
    private const val MAX_ID_LENGTH = 100
    private const val MAX_NAME_LENGTH = 40
    private const val MAX_VERSION_LENGTH = 20
    private const val MAX_DESCRIPTION_LENGTH = 200
    private const val MAX_HOSTS = 20
    private const val MAX_ENTRIES = 16
    private const val MAX_OPTIONS = 32
    private const val MAX_BACKDROP_FRAMES = 32
    private const val MAX_BACKDROP_FPS = 30
    private const val DEFAULT_BACKDROP_FPS = 12
    private const val DEFAULT_BACKDROP_DIM = 0.3f
    private const val DEFAULT_BACKDROP_PANE_OPACITY = 0.6f

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
        if (id.length > MAX_ID_LENGTH || !PLUGIN_ID_PATTERN.matches(id)) {
            return PluginParseResult.Failure("id 需要写成 com.example.name 这样的反向域名，只能用小写字母、数字和 _")
        }
        val name = root.string("name")?.trim().orEmpty()
        if (name.isEmpty()) return PluginParseResult.Failure("缺少插件名称")
        val version = root.string("version")?.trim().orEmpty()
        if (version.isEmpty()) return PluginParseResult.Failure("缺少版本号")
        val minAppVersion = root.string("minAppVersion")?.trim()?.takeIf { it.isNotEmpty() }
        minAppVersion?.let(::minAppVersionFailure)?.let { return PluginParseResult.Failure(it) }

        val warnings = mutableListOf<String>()
        val compatibility = if (format >= 2) parseCompatibility(root, platform, warnings) else Compatibility()
        compatibility.failure?.let { return PluginParseResult.Failure(it) }
        val permissions = parsePermissions(root["permissions"], warnings).withKind(compatibility.kind, warnings)
        val hosts = parseHosts(root["network"], warnings)
        if (PluginPermission.NETWORK in permissions && hosts.isEmpty()) {
            return PluginParseResult.Failure("申请了网络权限，但 network.hosts 里没有列出允许访问的域名")
        }
        if (PluginPermission.NETWORK !in permissions && hosts.isNotEmpty()) {
            warnings += "没有申请网络权限，network.hosts 已忽略"
        }

        val contributes = when (val value = root["contributes"]) {
            null -> PluginContributions()
            is JsonObject -> parseContributions(value, warnings, format, compatibility.kind)
            else -> return PluginParseResult.Failure("contributes 不是对象")
        }
        // 原生插件可以只在后台运行
        if (contributes.isEmpty && compatibility.kind != PluginKind.NATIVE) return PluginParseResult.Failure("插件没有提供任何功能")
        missingPermission(contributes, permissions)?.let { return PluginParseResult.Failure(it) }

        val manifest = PluginManifest(
            id = id,
            name = name.take(MAX_NAME_LENGTH),
            version = version.take(MAX_VERSION_LENGTH),
            author = root.string("author")?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NAME_LENGTH),
            description = root.string("description")?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_DESCRIPTION_LENGTH),
            minAppVersion = minAppVersion,
            permissions = permissions,
            networkHosts = if (PluginPermission.NETWORK in permissions) hosts else emptyList(),
            contributes = contributes,
            raw = root,
            directory = directory,
            kind = compatibility.kind,
            platforms = compatibility.platforms,
            apiVersion = compatibility.apiVersion,
            main = compatibility.main
        )
        return PluginParseResult.Success(manifest, warnings)
    }

    private class Compatibility(
        val kind: PluginKind = PluginKind.SANDBOX,
        val platforms: Set<PluginPlatform> = emptySet(),
        val apiVersion: String? = null,
        val main: String? = null,
        val failure: String? = null
    )

    // native 权限只跟插件类型走，不能靠清单自行声明
    private fun Set<PluginPermission>.withKind(kind: PluginKind, warnings: MutableList<String>): Set<PluginPermission> = when {
        kind == PluginKind.NATIVE -> this + PluginPermission.NATIVE
        PluginPermission.NATIVE in this -> (this - PluginPermission.NATIVE).also { warnings += "沙盒插件不能申请 native 权限，已忽略" }
        else -> this
    }

    // format 2 起才有这些字段
    private fun parseCompatibility(root: JsonObject, platform: PluginPlatform, warnings: MutableList<String>): Compatibility {
        val kind = when (val key = root.string("kind")) {
            null -> PluginKind.SANDBOX
            else -> PluginKind.fromKey(key) ?: return Compatibility(failure = "未知的插件类型 $key")
        }
        if (kind == PluginKind.NATIVE && platform == PluginPlatform.ANDROID) return Compatibility(failure = "Android 不支持原生插件")
        val platforms = parsePlatforms(root["platforms"], warnings)
        if (platforms.isNotEmpty() && platform !in platforms) {
            return Compatibility(failure = "插件不支持当前平台")
        }
        val main = root.string("main")?.trim()
        if (kind == PluginKind.NATIVE && (main == null || !CLASS_NAME_PATTERN.matches(main))) {
            return Compatibility(failure = "原生插件需要在 main 里写明入口类的完整类名")
        }
        val apiVersion = root.string("apiVersion")?.trim()
        if (apiVersion == null) {
            if (kind == PluginKind.NATIVE) return Compatibility(failure = "原生插件需要声明 apiVersion")
            return Compatibility(kind, platforms)
        }
        apiVersionFailure(apiVersion)?.let { return Compatibility(failure = it) }
        return Compatibility(kind, platforms, apiVersion, main.takeIf { kind == PluginKind.NATIVE })
    }

    fun apiVersionFailure(apiVersion: String): String? {
        val match = API_VERSION_PATTERN.matchEntire(apiVersion) ?: return "apiVersion 需要写成 1.0 这样的版本号"
        val major = match.groupValues[1].toIntOrNull() ?: return "apiVersion 取值无效"
        val minor = match.groupValues[2].toIntOrNull() ?: return "apiVersion 取值无效"
        return if (major != PLUGIN_API_MAJOR || minor > PLUGIN_API_MINOR) {
            "插件需要 API $apiVersion，当前应用提供 $PLUGIN_API_MAJOR.$PLUGIN_API_MINOR"
        } else {
            null
        }
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
        contributes.contentSources.isNotEmpty() && PluginPermission.CONTENT_SOURCE !in permissions ->
            "提供了内容源，但没有申请 ${PluginPermission.CONTENT_SOURCE.key} 权限"
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

    private fun parseContributions(root: JsonObject, warnings: MutableList<String>, format: Int, kind: PluginKind): PluginContributions {
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
            if ((item["compose"] as? JsonPrimitive)?.booleanOrNull == true) {
                if (kind != PluginKind.NATIVE) return@parseEntries null.also { warnings += "页面 $id 用了 compose，只有原生插件支持，已忽略" }
                return@parseEntries PluginPage(
                    id, title.take(MAX_NAME_LENGTH), item.string("icon")?.trim()?.takeIf { it.isNotEmpty() },
                    PluginUiRef.Inline(JsonObject(emptyMap())), PluginPagePlacement.SIDEBAR, compose = true
                )
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
        if (format < 2) return PluginContributions(layouts, pages, audioSources, lyricsSources, events)
        val commands = parseEntries(root["commands"], "命令", warnings) { item, id ->
            val title = item.string("title")?.trim()?.takeIf { it.isNotEmpty() } ?: return@parseEntries null.also {
                warnings += "命令 $id 缺少标题，已忽略"
            }
            PluginCommandEntry(id, title.take(MAX_NAME_LENGTH))
        }
        val settings = parseEntries(root["settings"], "设置项", warnings) { item, id -> parseSetting(item, id, warnings) }
        val contentSources = parseEntries(root["contentSources"], "内容源", warnings) { item, id ->
            PluginContentEntry(
                id,
                item.string("name")?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NAME_LENGTH) ?: id,
                (item["search"] as? JsonPrimitive)?.booleanOrNull == true
            )
        }
        val backdrops = parseEntries(root["backdrops"], "动态背景", warnings) { item, id -> parseBackdrop(item, id, kind, warnings) }
        return PluginContributions(
            layouts, pages, audioSources, lyricsSources, events, commands, parseMenus(root["menus"], commands, warnings), settings, contentSources, backdrops,
            layoutActions = (root["layoutActions"] as? JsonPrimitive)?.booleanOrNull == true
        )
    }

    private fun parseBackdrop(item: JsonObject, id: String, kind: PluginKind, warnings: MutableList<String>): PluginBackdropEntry? {
        val frames = (item["frames"] as? JsonArray).orEmpty().mapNotNull { frame ->
            val path = (frame as? JsonPrimitive)?.takeIf { it.isString }?.content
            path?.takeIf(PluginPaths::isAsset) ?: null.also { warnings += "动态背景 $id 的图片 ${path ?: frame} 无效，已忽略" }
        }
        if (frames.size > MAX_BACKDROP_FRAMES) warnings += "动态背景 $id 超过 $MAX_BACKDROP_FRAMES 帧，只保留前 $MAX_BACKDROP_FRAMES 帧"
        if (frames.isEmpty() && kind == PluginKind.SANDBOX) return null.also { warnings += "动态背景 $id 没有图片，已忽略" }
        return PluginBackdropEntry(
            id,
            item.string("name")?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NAME_LENGTH) ?: id,
            (item["fps"] as? JsonPrimitive)?.intOrNull?.coerceIn(1, MAX_BACKDROP_FPS) ?: DEFAULT_BACKDROP_FPS,
            item.number("dim")?.coerceIn(0f, 1f) ?: DEFAULT_BACKDROP_DIM,
            item.number("paneOpacity")?.coerceIn(0f, 1f) ?: DEFAULT_BACKDROP_PANE_OPACITY,
            frames.take(MAX_BACKDROP_FRAMES)
        )
    }

    private fun parseMenus(value: JsonElement?, commands: List<PluginCommandEntry>, warnings: MutableList<String>): List<PluginMenuEntry> {
        if (value == null) return emptyList()
        val array = value as? JsonArray ?: run {
            warnings += "菜单列表不是数组，已忽略"
            return emptyList()
        }
        val result = array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null.also { warnings += "菜单列表里有无效项，已忽略" }
            val command = obj.string("command")?.trim().orEmpty()
            val locationKey = obj.string("location")
            val location = locationKey?.let(PluginMenuLocation::fromKey)
            when {
                commands.none { it.id == command } -> null.also { warnings += "菜单引用了未声明的命令 $command，已忽略" }
                location == null -> null.also { warnings += "命令 $command 的位置 $locationKey 无效，已忽略" }
                else -> PluginMenuEntry(command, location)
            }
        }.distinct()
        if (result.size > MAX_ENTRIES) warnings += "菜单超过 $MAX_ENTRIES 项，只保留前 $MAX_ENTRIES 项"
        return result.take(MAX_ENTRIES)
    }

    private fun parseSetting(item: JsonObject, id: String, warnings: MutableList<String>): PluginSettingField? {
        val typeKey = item.string("type")
        val type = typeKey?.let(PluginSettingType::fromKey) ?: return null.also { warnings += "设置项 $id 的类型 $typeKey 无效，已忽略" }
        val title = item.string("title")?.trim()?.takeIf { it.isNotEmpty() } ?: return null.also { warnings += "设置项 $id 缺少标题，已忽略" }
        val base = PluginSettingField(id, type, title.take(MAX_NAME_LENGTH), JsonNull)
        return when (type) {
            PluginSettingType.TOGGLE -> base.copy(default = JsonPrimitive(false))
            PluginSettingType.TEXT -> base.copy(default = JsonPrimitive(""))
            PluginSettingType.SELECT -> {
                val options = parseOptions(item["options"])
                if (options.isEmpty()) return null.also { warnings += "设置项 $id 没有可选项，已忽略" }
                base.copy(options = options, default = JsonPrimitive(options.first().value))
            }
            PluginSettingType.NUMBER -> {
                val min = item.number("min")
                val max = item.number("max")
                if (min == null || max == null || min >= max) return null.also { warnings += "设置项 $id 的 min、max 无效，已忽略" }
                base.copy(default = JsonPrimitive(min), min = min, max = max, step = item.number("step")?.takeIf { it > 0f } ?: 1f)
            }
        }.let { field -> field.copy(default = field.normalize(item["default"])) }
    }

    fun parseOptions(value: JsonElement?): List<PluginOption> = (value as? JsonArray).orEmpty().mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val key = obj.string("value") ?: return@mapNotNull null
        PluginOption(key.take(MAX_NAME_LENGTH), (obj.string("label") ?: key).take(MAX_NAME_LENGTH))
    }.distinctBy { it.value }.take(MAX_OPTIONS)

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

    private fun JsonObject.number(key: String): Float? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.floatOrNull?.takeIf { it.isFinite() }
}
