package com.lin0721.linmusic.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// 插件发给用户的提示，各端用现有提示组件展示
class PluginNotices {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun post(message: String) {
        _messages.tryEmit(message)
    }
}

data class PluginCommandItem(val pluginId: String, val pluginName: String, val commandId: String, val title: String)

// 菜单与按钮的入口：只显示脚本已注册的命令，执行不跟随调用方的生命周期
class PluginCommands(
    private val registry: ExtensionRegistry,
    val notices: PluginNotices,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val confirmations: PluginConfirmations = PluginConfirmations(),
    val content: PluginContent = PluginContent(registry)
) {

    fun items(location: PluginMenuLocation): Flow<List<PluginCommandItem>> =
        registry.entries.map { itemsNow(location) }.distinctUntilChanged()

    fun itemsNow(location: PluginMenuLocation): List<PluginCommandItem> {
        val commands = registry.get(ExtensionPoints.COMMAND)
        return registry.get(ExtensionPoints.MENU)
            .filter { it.value.location == location }
            .mapNotNull { menu ->
                commands.firstOrNull { it.pluginId == menu.pluginId && it.localId == menu.value.commandId }?.let { command ->
                    PluginCommandItem(command.pluginId, command.pluginName, command.localId, command.value.title)
                }
            }
    }

    fun run(item: PluginCommandItem, args: JsonElement = JsonNull) {
        scope.launch {
            val command = registry.get(ExtensionPoints.COMMAND)
                .firstOrNull { it.pluginId == item.pluginId && it.localId == item.commandId } ?: return@launch
            if (!command.value.execute(args)) notices.post("${item.pluginName}：命令执行失败")
        }
    }
}

// 歌曲命令收到的参数；外部平台歌曲没有 songId
fun pluginSongArgs(songId: Long?, title: String, artist: String, album: String, cover: String): JsonObject = buildJsonObject {
    songId?.takeIf { it > 0 }?.let { put("songId", it) }
    put("title", title)
    put("artist", artist)
    put("album", album)
    put("cover", cover)
}

val LocalPluginCommands = staticCompositionLocalOf<PluginCommands?> { null }

@Composable
fun rememberPluginCommands(location: PluginMenuLocation): List<PluginCommandItem> {
    val commands = LocalPluginCommands.current ?: return emptyList()
    val flow = remember(commands, location) { commands.items(location) }
    val items by flow.collectAsState(initial = commands.itemsNow(location))
    return items
}
