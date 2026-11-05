package com.lin0721.linmusic.core.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class PluginTrack(val songId: Long, val title: String, val artist: String, val cover: String)

sealed interface PluginImageSource {
    data class Asset(val file: java.io.File) : PluginImageSource
    data class Remote(val url: String) : PluginImageSource
}

// 绑定写法：$state.xxx 取页面状态，$item.xxx 取列表当前项，可用点号取下一层
object PluginBinding {
    private val PATTERN = Regex("^\\$(state|item)(\\.[A-Za-z0-9_]+)*$")

    fun isBinding(value: String): Boolean = PATTERN.matches(value)

    fun resolve(binding: String, state: JsonObject, item: JsonElement?): JsonElement? {
        val parts = binding.removePrefix("$").split('.')
        var current: JsonElement? = if (parts.first() == "state") state else item
        for (key in parts.drop(1)) current = (current as? JsonObject)?.get(key)
        return current
    }

    fun text(value: JsonElement?): String = when (value) {
        null, JsonNull -> ""
        is JsonPrimitive -> value.content
        else -> value.toString()
    }

    fun truthy(value: JsonElement?): Boolean = when (value) {
        null, JsonNull -> false
        is JsonPrimitive -> when {
            value.isString -> value.content.isNotEmpty()
            value.booleanOrNull != null -> value.booleanOrNull == true
            else -> (value.doubleOrNull ?: 0.0) != 0.0
        }
        is JsonArray -> value.isNotEmpty()
        is JsonObject -> value.isNotEmpty()
    }

    // 只放行包内图片与清单允许的域名
    fun image(manifest: PluginManifest, value: String): PluginImageSource? {
        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            val host = runCatching { java.net.URI(value).host }.getOrNull()?.lowercase() ?: return null
            return if (PluginHostApi.isHostAllowed(manifest.networkHosts, host)) PluginImageSource.Remote(value) else null
        }
        if (!PluginPaths.isAsset(value)) return null
        return manifest.file(value)?.let(PluginImageSource::Asset)
    }

    // 封面由图片加载器直接请求，沙盒插件同样只能用清单允许的域名，其他地址返回空字符串
    fun cover(manifest: PluginManifest, value: String): String =
        if (manifest.kind == PluginKind.NATIVE || image(manifest, value) is PluginImageSource.Remote) value else ""

    fun tracks(manifest: PluginManifest, value: JsonElement?): List<PluginTrack> = (value as? JsonArray).orEmpty().mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val songId = (obj["songId"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return@mapNotNull null
        PluginTrack(songId, text(obj["title"]), text(obj["artist"]), cover(manifest, text(obj["cover"])))
    }
}

// 各端提供的页面组件，全部用现有组件库实现
interface PluginPageUi {
    @Composable fun Image(source: PluginImageSource, node: PlayerLayoutNode, modifier: Modifier)
    @Composable fun Tracks(tracks: List<PluginTrack>, onPlay: (Int) -> Unit, modifier: Modifier)
    @Composable fun Button(text: String, busy: Boolean, onClick: () -> Unit, modifier: Modifier)
    @Composable fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier)
    @Composable fun Input(label: String, value: String, secret: Boolean, onChange: (String) -> Unit, onCommit: () -> Unit, modifier: Modifier)
    @Composable fun Loading(modifier: Modifier)
    @Composable fun Select(label: String, options: List<PluginOption>, selected: String, onSelect: (String) -> Unit, modifier: Modifier)
    @Composable fun Slider(label: String, value: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit, onCommit: () -> Unit, modifier: Modifier)
    @Composable fun ProgressBar(fraction: Float, modifier: Modifier)
    @Composable fun Badge(text: String, modifier: Modifier)
    @Composable fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier)
}

// 页面运行所需的宿主操作
class PluginPageHost(private val runtime: PluginRuntime, private val api: PluginHostApi) {
    private fun nativeAction(pluginId: String, name: String): ActionExtension? =
        runtime.registry.get(ExtensionPoints.ACTION).firstOrNull { it.pluginId == pluginId && it.localId == name }?.value

    private fun hasAction(pluginId: String, name: String): Boolean = nativeAction(pluginId, name) != null ||
        "${PluginPrelude.KIND_ACTION}:$name" in runtime.status.value[pluginId]?.registrations.orEmpty()

    fun state(pluginId: String, pageId: String): JsonObject =
        api.uiStates.value["$pluginId/$pageId"] ?: JsonObject(emptyMap())

    val states get() = api.uiStates

    val statuses get() = runtime.status

    suspend fun action(pluginId: String, name: String, args: JsonElement = JsonNull): Boolean =
        nativeAction(pluginId, name)?.run(args)
            ?: (runtime.invoke(pluginId, PluginPrelude.KIND_ACTION, name, args, PluginRuntime.ACTION_TIMEOUT_MS) != null)

    fun canOpen(pluginId: String, pageId: String): Boolean = hasAction(pluginId, "open:$pageId")

    // 页面打开时给脚本一次刷新数据的机会
    suspend fun open(pluginId: String, pageId: String) {
        if (canOpen(pluginId, pageId)) action(pluginId, "open:$pageId")
    }

    fun setField(pluginId: String, pageId: String, field: String, value: JsonElement) =
        api.setUiField(pluginId, pageId, field, value)

    suspend fun play(manifest: PluginManifest, tracks: List<PluginTrack>, index: Int) =
        api.playTracks(manifest, tracks, index)

    // 读取失败时提示并回到默认值，页面不能因为磁盘错误崩溃
    suspend fun settings(manifest: PluginManifest): JsonObject = try {
        api.settings(manifest)
    } catch (e: PluginCallException) {
        api.notice("${manifest.name}：${e.message}")
        JsonObject(manifest.contributes.settings.associate { it.id to it.default })
    }

    // 脚本注册了 settingsChanged 时通知一次
    suspend fun setSetting(manifest: PluginManifest, key: String, value: JsonElement) {
        try {
            api.setSetting(manifest, key, value)
        } catch (e: PluginCallException) {
            api.notice("${manifest.name}：${e.message}")
            return
        }
        if (hasAction(manifest.id, SETTINGS_CHANGED)) {
            action(manifest.id, SETTINGS_CHANGED, buildJsonObject {
                put("key", key)
                put("value", value)
            })
        }
    }

    private companion object {
        const val SETTINGS_CHANGED = "settingsChanged"
    }
}

private const val MAX_LIST_ITEMS = 200

// 页面骨架：显隐、绑定与列表循环在这里处理，具体组件交给 ui
@Composable
fun PluginPageView(
    page: ActivePluginPage,
    host: PluginPageHost,
    ui: PluginPageUi,
    modifier: Modifier = Modifier
) {
    val root = page.root ?: return
    val pluginId = page.manifest.id
    val pageId = page.page.id
    val states by host.states.collectAsState()
    val state = states["$pluginId/$pageId"] ?: JsonObject(emptyMap())
    // 页面先于脚本发布，要等脚本注册了 open 动作再调用
    val readiness = remember(pluginId, pageId) { host.statuses.map { host.canOpen(pluginId, pageId) }.distinctUntilChanged() }
    val canOpen by readiness.collectAsState(initial = host.canOpen(pluginId, pageId))
    LaunchedEffect(pluginId, pageId, canOpen) { if (canOpen) host.open(pluginId, pageId) }
    PageNode(root, modifier, PageScope(page, host, ui, state, null))
}

private class PageScope(
    val page: ActivePluginPage,
    val host: PluginPageHost,
    val ui: PluginPageUi,
    val state: JsonObject,
    val item: JsonElement?
) {
    fun value(binding: String): JsonElement? = PluginBinding.resolve(binding, state, item)

    fun text(value: String): String = if (PluginBinding.isBinding(value)) PluginBinding.text(value(value)) else value

    // 文字绑定在这里换成实际内容，标题与空态复用文字节点
    fun prepare(node: PlayerLayoutNode): PlayerLayoutNode? {
        if (node.visible != null && !PluginBinding.truthy(value(node.visible))) return null
        return when (node.type) {
            LayoutNodeType.TEXT -> node.copy(text = text(node.text))
            LayoutNodeType.HEADING -> node.copy(type = LayoutNodeType.TEXT, text = text(node.text), textStyle = LayoutTextStyle.TITLE)
            LayoutNodeType.EMPTY -> node.copy(type = LayoutNodeType.TEXT, text = text(node.text), textStyle = LayoutTextStyle.CAPTION)
            else -> node
        }
    }
}

@Composable
private fun PageNode(node: PlayerLayoutNode, modifier: Modifier, scope: PageScope) {
    PlayerLayoutView(node, modifier, scope::prepare) { current, nodeModifier -> PageComponent(current, nodeModifier, scope) }
}

@Composable
private fun PageComponent(node: PlayerLayoutNode, modifier: Modifier, scope: PageScope) {
    val coroutine = rememberCoroutineScope()
    val pluginId = scope.page.manifest.id
    val pageId = scope.page.page.id
    when (node.type) {
        LayoutNodeType.LIST -> Column(modifier, verticalArrangement = Arrangement.spacedBy(node.spacing.dp)) {
            val elements = (scope.value(node.bind) as? JsonArray).orEmpty().take(MAX_LIST_ITEMS)
            fun itemScope(element: JsonElement) = PageScope(scope.page, scope.host, scope.ui, scope.state, element)
            if (node.columns <= 1) {
                elements.forEach { element -> node.children.forEach { child -> PageNode(child, Modifier, itemScope(element)) } }
            } else {
                elements.chunked(node.columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(node.spacing.dp)) {
                        row.forEach { element ->
                            Column(Modifier.weight(1f)) { node.children.forEach { child -> PageNode(child, Modifier, itemScope(element)) } }
                        }
                        repeat(node.columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        LayoutNodeType.TRACK_LIST -> {
            val tracks = PluginBinding.tracks(scope.page.manifest, scope.value(node.bind)).take(MAX_LIST_ITEMS)
            scope.ui.Tracks(tracks, { index -> coroutine.launch { scope.host.play(scope.page.manifest, tracks, index) } }, modifier)
        }
        LayoutNodeType.IMAGE -> {
            val source = PluginBinding.image(scope.page.manifest, scope.text(node.bind))
            if (source != null) scope.ui.Image(source, node, modifier)
        }
        LayoutNodeType.BUTTON -> {
            var busy by remember { mutableStateOf(false) }
            val action = node.action ?: return
            // 列表项里的按钮传当前项，其他按钮传点击时的页面数据，脚本由此拿到输入框里未提交的内容
            val item = scope.item
            scope.ui.Button(
                scope.text(node.text),
                busy,
                {
                    if (!busy) {
                        busy = true
                        val args = item ?: scope.host.state(pluginId, pageId)
                        coroutine.launch {
                            try {
                                scope.host.action(pluginId, action, args)
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                modifier
            )
        }
        LayoutNodeType.TOGGLE -> {
            val field = node.bind.removePrefix("\$state.")
            scope.ui.Toggle(
                scope.text(node.text),
                PluginBinding.truthy(scope.value(node.bind)),
                { checked ->
                    scope.host.setField(pluginId, pageId, field, JsonPrimitive(checked))
                    node.action?.let { name ->
                        coroutine.launch { scope.host.action(pluginId, name, buildJsonObject { put("value", checked) }) }
                    }
                },
                modifier
            )
        }
        LayoutNodeType.INPUT -> {
            val field = node.bind.removePrefix("\$state.")
            val value = PluginBinding.text(scope.value(node.bind))
            scope.ui.Input(
                scope.text(node.text),
                value,
                node.secret,
                { scope.host.setField(pluginId, pageId, field, JsonPrimitive(it)) },
                {
                    node.action?.let { name ->
                        coroutine.launch { scope.host.action(pluginId, name, buildJsonObject { put("value", value) }) }
                    }
                },
                modifier
            )
        }
        LayoutNodeType.LOADING -> scope.ui.Loading(modifier)
        LayoutNodeType.SELECT -> {
            val field = node.bind.removePrefix("\$state.")
            scope.ui.Select(
                scope.text(node.text),
                node.options,
                PluginBinding.text(scope.value(node.bind)),
                { value ->
                    scope.host.setField(pluginId, pageId, field, JsonPrimitive(value))
                    node.action?.let { name ->
                        coroutine.launch { scope.host.action(pluginId, name, buildJsonObject { put("value", value) }) }
                    }
                },
                modifier
            )
        }
        LayoutNodeType.SLIDER -> {
            val field = node.bind.removePrefix("\$state.")
            val value = ((scope.value(node.bind) as? JsonPrimitive)?.floatOrNull?.takeIf { it.isFinite() } ?: node.min).coerceIn(node.min, node.max)
            scope.ui.Slider(
                scope.text(node.text),
                value,
                node.min,
                node.max,
                node.step,
                { scope.host.setField(pluginId, pageId, field, JsonPrimitive(it)) },
                {
                    node.action?.let { name ->
                        coroutine.launch { scope.host.action(pluginId, name, buildJsonObject { put("value", value) }) }
                    }
                },
                modifier
            )
        }
        LayoutNodeType.PROGRESS_BAR -> {
            val fraction = (scope.value(node.bind) as? JsonPrimitive)?.floatOrNull?.takeIf { it.isFinite() } ?: 0f
            scope.ui.ProgressBar(fraction.coerceIn(0f, 1f), modifier)
        }
        LayoutNodeType.BADGE -> scope.ui.Badge(scope.text(node.text), modifier)
        LayoutNodeType.TABS -> {
            if (node.children.isEmpty()) return
            var selected by remember { mutableStateOf(0) }
            Column(modifier, verticalArrangement = Arrangement.spacedBy(node.spacing.dp)) {
                scope.ui.Tabs(node.children.map { scope.text(it.text) }, selected, { selected = it }, Modifier)
                PageNode(node.children[selected.coerceIn(node.children.indices)], Modifier, scope)
            }
        }
        else -> Unit
    }
}
