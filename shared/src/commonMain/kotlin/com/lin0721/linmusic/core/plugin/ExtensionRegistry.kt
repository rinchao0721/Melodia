package com.lin0721.linmusic.core.plugin

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement

// ordered 为 true 时按插件名与声明顺序排序；是否只取一项由使用方决定
class ExtensionPoint<T : Any>(val id: String, val ordered: Boolean = false)

object ExtensionPoints {
    val PLAYER_LAYOUT = ExtensionPoint<PlayerLayoutExtension>("playerLayout")
    val PAGE = ExtensionPoint<ActivePluginPage>("page")
    val AUDIO_SOURCE = ExtensionPoint<AudioSourceExtension>("audioSource", ordered = true)
    val LYRICS_SOURCE = ExtensionPoint<LyricsSourceExtension>("lyricsSource", ordered = true)
    val EVENT = ExtensionPoint<EventExtension>("event")
    val COMMAND = ExtensionPoint<CommandExtension>("command")
    val MENU = ExtensionPoint<PluginMenuEntry>("menu")
    val CONTENT_SOURCE = ExtensionPoint<ContentSourceExtension>("contentSource", ordered = true)
    val ACTION = ExtensionPoint<ActionExtension>("action")
    val BACKDROP = ExtensionPoint<BackdropExtension>("backdropSource")
    val COMPOSE_PAGE = ExtensionPoint<ComposePageExtension>("composeSlot")
}

data class PlayerLayoutExtension(val area: PlayerLayoutArea, val plugin: InstalledPlugin, val spec: PlayerLayoutSpec)

// 返回值的格式与脚本一致，由 PluginResults 宽松解析
interface AudioSourceExtension {
    suspend fun resolve(query: PluginTrackQuery, quality: String): JsonElement?
}

interface LyricsSourceExtension {
    suspend fun fetch(query: PluginTrackQuery): JsonElement?
}

// 事件只通知，实现不能阻塞调用方
interface EventExtension {
    val events: Set<PluginEvent>

    fun onEvent(event: PluginEvent, data: JsonElement)
}

// 第三方内容来源；返回值的格式与脚本一致
interface ContentSourceExtension {
    val searchable: Boolean

    // 返回可以加载的封面地址，不允许时返回空字符串
    fun cover(url: String): String = url

    suspend fun search(keyword: String, offset: Int, limit: Int): JsonElement?

    suspend fun playlist(id: String): JsonElement?

    suspend fun resolve(trackId: String, quality: String): JsonElement?
}

// 执行失败或超时返回 false
interface CommandExtension {
    val title: String

    suspend fun execute(args: JsonElement): Boolean
}

// 原生插件的页面动作；沙盒插件的动作走脚本调用
interface ActionExtension {
    suspend fun run(args: JsonElement): Boolean
}

// 原生插件逐帧渲染背景，在后台线程调用；把 ARGB 写进 pixels，失败返回 false
interface BackdropExtension {
    val maxWidth: Int

    fun render(width: Int, height: Int, timeMs: Long, pixels: IntArray): Boolean

    // render 长时间不返回时调用
    fun reportStalled() {}
}

// 原生插件的 Compose 页面；组合时的异常无法就地捕获，由宿主放进独立窗口层后交给 onError
class ComposePageExtension(val content: @Composable () -> Unit, val onError: (Throwable) -> Unit)

// pluginId + point + localId 唯一；order 为同一插件内的声明顺序
data class Extension<T : Any>(
    val point: ExtensionPoint<T>,
    val pluginId: String,
    val pluginName: String,
    val localId: String,
    val value: T,
    val order: Int = 0
)

// 插件能力的统一入口：宿主只从这里读取，不关心能力由哪种插件提供
class ExtensionRegistry {
    private val _entries = MutableStateFlow<List<Extension<*>>>(emptyList())
    val entries: StateFlow<List<Extension<*>>> = _entries.asStateFlow()

    fun <T : Any> register(extension: Extension<T>) {
        _entries.update { current ->
            current.filterNot {
                it.point == extension.point && it.pluginId == extension.pluginId && it.localId == extension.localId
            } + extension
        }
    }

    // 声明式能力每次按插件列表整体重算
    fun <T : Any> replace(point: ExtensionPoint<T>, extensions: List<Extension<T>>) {
        _entries.update { current -> current.filterNot { it.point == point } + extensions }
    }

    fun unregister(pluginId: String, points: Set<ExtensionPoint<*>>) {
        _entries.update { current -> current.filterNot { it.pluginId == pluginId && it.point in points } }
    }

    fun <T : Any> get(point: ExtensionPoint<T>): List<Extension<T>> {
        @Suppress("UNCHECKED_CAST")
        val matched = _entries.value.filter { it.point == point } as List<Extension<T>>
        return if (point.ordered) {
            matched.sortedWith(compareBy<Extension<T>> { it.pluginName }.thenBy { it.order })
        } else {
            matched
        }
    }
}
