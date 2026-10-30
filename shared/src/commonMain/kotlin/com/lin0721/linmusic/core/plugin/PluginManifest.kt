package com.lin0721.linmusic.core.plugin

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File

const val PLUGIN_FORMAT_VERSION = 2
const val PLUGIN_API_MAJOR = 1
const val PLUGIN_API_MINOR = 0
const val PLUGIN_FILE_EXTENSION = "melodiaplugin"

// 插件清单 permissions 里的取值，改名会让已发布的插件失效
enum class PluginPermission(val key: String, val label: String) {
    NETWORK("network", "访问网络"),
    PLAYBACK_READ("playback.read", "读取当前播放的歌曲与进度"),
    PLAYBACK_CONTROL("playback.control", "控制播放：播放、暂停、切歌、加入队列"),
    STORAGE("storage", "保存插件自己的数据"),
    AUDIO_SOURCE("audioSource", "作为音源提供播放链接"),
    LYRICS_SOURCE("lyricsSource", "作为歌词源提供歌词"),
    UI_NOTIFY("ui.notify", "显示应用内提示");

    companion object {
        fun fromKey(key: String): PluginPermission? = entries.firstOrNull { it.key == key }
    }
}

enum class PluginKind(val key: String) {
    SANDBOX("sandbox"),
    NATIVE("native");

    companion object {
        fun fromKey(key: String): PluginKind? = entries.firstOrNull { it.key == key }
    }
}

// 页面入口位置
enum class PluginPagePlacement(val key: String) {
    SIDEBAR("sidebar"),
    NOW_PLAYING_CARD("nowPlayingCard");

    companion object {
        fun fromKey(key: String): PluginPagePlacement? = entries.firstOrNull { it.key == key }
    }
}

// 插件订阅的播放事件
enum class PluginEvent(val key: String) {
    SONG_CHANGED("songChanged"),
    PLAY_STATE_CHANGED("playStateChanged"),
    PROGRESS("progress");

    companion object {
        fun fromKey(key: String): PluginEvent? = entries.firstOrNull { it.key == key }
    }
}

// 命令显示的位置
enum class PluginMenuLocation(val key: String) {
    SONG("song"),
    TOOLBAR("toolbar");

    companion object {
        fun fromKey(key: String): PluginMenuLocation? = entries.firstOrNull { it.key == key }
    }
}

enum class PluginSettingType(val key: String) {
    TOGGLE("toggle"),
    TEXT("text"),
    SELECT("select"),
    NUMBER("number");

    companion object {
        fun fromKey(key: String): PluginSettingType? = entries.firstOrNull { it.key == key }
    }
}

// 可自定义的播放器区域；清单 playerLayouts 下的 key
val PLAYER_LAYOUT_AREAS = listOf(
    "android.fullPlayer",
    "android.fullPlayerWide",
    "android.miniPlayer",
    "desktop.playerBar",
    "desktop.nowPlaying"
)

// 界面描述可以是 ui/ 下的文件，也可以内联在清单里
sealed interface PluginUiRef {
    data class File(val path: String) : PluginUiRef
    data class Inline(val tree: JsonObject) : PluginUiRef
}

data class PluginPage(
    val id: String,
    val title: String,
    val icon: String?,
    val ui: PluginUiRef,
    val placement: PluginPagePlacement
)

data class PluginSourceEntry(val id: String, val name: String)

data class PluginCommandEntry(val id: String, val title: String)

data class PluginMenuEntry(val commandId: String, val location: PluginMenuLocation)

data class PluginOption(val value: String, val label: String)

// default 已按类型校验；min、max、step 只用于 number
data class PluginSettingField(
    val id: String,
    val type: PluginSettingType,
    val title: String,
    val default: JsonElement,
    val options: List<PluginOption> = emptyList(),
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float = 1f
)

// 界面描述只记录引用，界面模型在后续阶段解析
data class PluginContributions(
    val playerLayouts: Map<String, PluginUiRef> = emptyMap(),
    val pages: List<PluginPage> = emptyList(),
    val audioSources: List<PluginSourceEntry> = emptyList(),
    val lyricsSources: List<PluginSourceEntry> = emptyList(),
    val events: Set<PluginEvent> = emptySet(),
    val commands: List<PluginCommandEntry> = emptyList(),
    val menus: List<PluginMenuEntry> = emptyList(),
    val settings: List<PluginSettingField> = emptyList()
) {
    val needsScript: Boolean
        get() = pages.isNotEmpty() || audioSources.isNotEmpty() || lyricsSources.isNotEmpty() || events.isNotEmpty() ||
            commands.isNotEmpty() || settings.isNotEmpty()

    val isEmpty: Boolean
        get() = playerLayouts.isEmpty() && !needsScript

    val uiFiles: List<String>
        get() = (playerLayouts.values + pages.map { it.ui }).filterIsInstance<PluginUiRef.File>().map { it.path }
}

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String?,
    val description: String?,
    val minAppVersion: String?,
    val permissions: Set<PluginPermission>,
    val networkHosts: List<String>,
    val contributes: PluginContributions,
    val raw: JsonObject,
    // 已解包或已安装插件所在目录；校验原始 JSON 时为 null
    val directory: File? = null,
    val kind: PluginKind = PluginKind.SANDBOX,
    // 为空表示所有平台
    val platforms: Set<PluginPlatform> = emptySet(),
    val apiVersion: String? = null
) {
    val scriptFile: File? get() = directory?.let { File(it, PluginPaths.SCRIPT) }?.takeIf { it.isFile }

    fun file(path: String): File? = directory?.takeIf { PluginPaths.isAllowed(path) }?.let { File(it, path) }?.takeIf { it.isFile }
}

// 插件包内允许的文件：清单、脚本入口、ui 下的界面描述、assets 下的图片，都只允许一层
object PluginPaths {
    const val MANIFEST = "plugin.json"
    const val SCRIPT = "main.js"
    const val UI_PREFIX = "ui/"
    const val ASSETS_PREFIX = "assets/"

    private val FILE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp")

    fun isUi(path: String): Boolean = inFolder(path, UI_PREFIX) && extension(path) == "json"

    fun isAsset(path: String): Boolean = inFolder(path, ASSETS_PREFIX) && extension(path) in imageExtensions

    fun isAllowed(path: String): Boolean = path == MANIFEST || path == SCRIPT || isUi(path) || isAsset(path)

    private fun inFolder(path: String, prefix: String): Boolean {
        if (!path.startsWith(prefix)) return false
        val name = path.removePrefix(prefix)
        return FILE_NAME.matches(name) && !name.contains("..")
    }

    private fun extension(path: String): String = path.substringAfterLast('.', "").lowercase()
}
