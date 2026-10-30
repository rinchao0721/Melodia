package com.lin0721.linmusic.core.plugin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement

// 同一扩展点有多个提供者时的规则；SINGLE 只列出候选，由使用方决定生效项
enum class ExtensionMerge { ALL, ORDERED, SINGLE }

class ExtensionPoint<T : Any>(val id: String, val merge: ExtensionMerge)

object ExtensionPoints {
    val PLAYER_LAYOUT = ExtensionPoint<PlayerLayoutExtension>("playerLayout", ExtensionMerge.SINGLE)
    val PAGE = ExtensionPoint<ActivePluginPage>("page", ExtensionMerge.ALL)
    val AUDIO_SOURCE = ExtensionPoint<AudioSourceExtension>("audioSource", ExtensionMerge.ORDERED)
    val LYRICS_SOURCE = ExtensionPoint<LyricsSourceExtension>("lyricsSource", ExtensionMerge.ORDERED)
    val EVENT = ExtensionPoint<EventExtension>("event", ExtensionMerge.ALL)
    val COMMAND = ExtensionPoint<CommandExtension>("command", ExtensionMerge.ALL)
    val MENU = ExtensionPoint<PluginMenuEntry>("menu", ExtensionMerge.ALL)
}

data class PlayerLayoutExtension(val area: PlayerLayoutArea, val plugin: InstalledPlugin, val root: PlayerLayoutNode)

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

// 执行失败或超时返回 false
interface CommandExtension {
    val title: String

    suspend fun execute(args: JsonElement): Boolean
}

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
        return if (point.merge == ExtensionMerge.ORDERED) {
            matched.sortedWith(compareBy<Extension<T>> { it.pluginName }.thenBy { it.order })
        } else {
            matched
        }
    }
}
