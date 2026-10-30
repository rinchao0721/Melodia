package com.lin0721.linmusic.core.plugin

import androidx.compose.runtime.staticCompositionLocalOf
import com.lin0721.linmusic.core.ui.skin.PaletteToken
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException

// 布局节点类型；清单里的 type 取值，改名会让已发布的插件失效
enum class LayoutNodeType(val key: String, val isContainer: Boolean = false) {
    COLUMN("column", true),
    ROW("row", true),
    BOX("box", true),
    COVER("cover"),
    SONG_TITLE("songTitle"),
    ARTIST("artist"),
    SONG_INFO("songInfo"),
    LIKE_BUTTON("likeButton"),
    MINI_LYRIC("miniLyric"),
    PROGRESS("progress"),
    CONTROLS("controls"),
    ACTIONS("actions"),
    LYRICS_CARD("lyricsCard"),
    SPACER("spacer"),
    TEXT("text"),

    // 以下只用于插件页面
    HEADING("heading"),
    IMAGE("image"),
    LIST("list"),
    TRACK_LIST("trackList"),
    BUTTON("button"),
    TOGGLE("toggle"),
    INPUT("input"),
    LOADING("loading"),
    EMPTY("empty"),
    SELECT("select"),
    SLIDER("slider"),
    PROGRESS_BAR("progressBar"),
    BADGE("badge"),
    TABS("tabs");

    companion object {
        fun fromKey(key: String): LayoutNodeType? = entries.firstOrNull { it.key == key }
    }
}

enum class LayoutAlign(val key: String) {
    START("start"), CENTER("center"), END("end");

    companion object {
        fun fromKey(key: String): LayoutAlign? = entries.firstOrNull { it.key == key }
    }
}

enum class LayoutSize(val key: String, val scale: Float) {
    SMALL("small", 0.8f), MEDIUM("medium", 1f), LARGE("large", 1.25f);

    companion object {
        fun fromKey(key: String): LayoutSize? = entries.firstOrNull { it.key == key }
    }
}

enum class LayoutShape(val key: String) {
    RECT("rect"), ROUNDED("rounded"), CIRCLE("circle");

    companion object {
        fun fromKey(key: String): LayoutShape? = entries.firstOrNull { it.key == key }
    }
}

enum class LayoutTextStyle(val key: String) {
    TITLE("title"), BODY("body"), CAPTION("caption");

    companion object {
        fun fromKey(key: String): LayoutTextStyle? = entries.firstOrNull { it.key == key }
    }
}

data class LayoutPadding(val top: Float = 0f, val end: Float = 0f, val bottom: Float = 0f, val start: Float = 0f) {
    companion object {
        val Zero = LayoutPadding()
    }
}

// 校验过的布局树；渲染端只读这里的取值，全部已收在合理范围内
data class PlayerLayoutNode(
    val type: LayoutNodeType,
    val children: List<PlayerLayoutNode> = emptyList(),
    val padding: LayoutPadding = LayoutPadding.Zero,
    val spacing: Float = 0f,
    val align: LayoutAlign = LayoutAlign.START,
    val weight: Float? = null,
    val width: Float? = null,
    val height: Float? = null,
    val background: PaletteToken? = null,
    val shape: LayoutShape = LayoutShape.RECT,
    val cornerRadius: Float = DEFAULT_CORNER_RADIUS,
    // controls / actions 的按钮及顺序
    val buttons: List<String> = emptyList(),
    val size: LayoutSize = LayoutSize.MEDIUM,
    val text: String = "",
    val textStyle: LayoutTextStyle = LayoutTextStyle.BODY,
    val maxLines: Int = 1,
    val showTime: Boolean = true,
    // 封面宽高比与最大边长
    val aspect: Float = 1f,
    val maxSize: Float? = null,
    // 页面专用：list / trackList 绑定的数组、image 的地址、toggle / input 写回的状态字段
    val bind: String = "",
    val action: String? = null,
    // 绑定值为假时不显示
    val visible: String? = null,
    // select 的选项
    val options: List<PluginOption> = emptyList(),
    // slider 的取值范围
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float = 0f,
    // list 每行的列数
    val columns: Int = 1
) {
    fun contains(type: LayoutNodeType): Boolean = this.type == type || children.any { it.contains(type) }

    fun hasButton(type: LayoutNodeType, button: String): Boolean =
        (this.type == type && button in buttons) || children.any { it.hasButton(type, button) }

    companion object {
        const val DEFAULT_CORNER_RADIUS = 8f
    }
}

// 各区域允许的组件与按钮
enum class PlayerLayoutArea(
    val key: String,
    val label: String,
    val components: Set<LayoutNodeType>,
    val actionButtons: List<String> = emptyList(),
    val controlButtons: List<String> = CONTROL_BUTTONS
) {
    ANDROID_FULL_PLAYER(
        "android.fullPlayer", "Android 全屏播放页",
        CONTAINERS + setOf(
            LayoutNodeType.COVER, LayoutNodeType.SONG_TITLE, LayoutNodeType.ARTIST, LayoutNodeType.SONG_INFO,
            LayoutNodeType.LIKE_BUTTON, LayoutNodeType.MINI_LYRIC, LayoutNodeType.PROGRESS, LayoutNodeType.CONTROLS,
            LayoutNodeType.ACTIONS, LayoutNodeType.LYRICS_CARD, LayoutNodeType.SPACER, LayoutNodeType.TEXT
        ),
        ANDROID_ACTIONS
    ),
    ANDROID_FULL_PLAYER_WIDE(
        "android.fullPlayerWide", "Android 宽屏播放页",
        CONTAINERS + setOf(
            LayoutNodeType.COVER, LayoutNodeType.SONG_TITLE, LayoutNodeType.ARTIST, LayoutNodeType.SONG_INFO,
            LayoutNodeType.LIKE_BUTTON, LayoutNodeType.MINI_LYRIC, LayoutNodeType.PROGRESS, LayoutNodeType.CONTROLS,
            LayoutNodeType.ACTIONS, LayoutNodeType.SPACER, LayoutNodeType.TEXT
        ),
        ANDROID_ACTIONS
    ),
    ANDROID_MINI_PLAYER(
        "android.miniPlayer", "Android 迷你播放器",
        CONTAINERS + setOf(
            LayoutNodeType.COVER, LayoutNodeType.SONG_TITLE, LayoutNodeType.ARTIST, LayoutNodeType.SONG_INFO,
            LayoutNodeType.LIKE_BUTTON, LayoutNodeType.PROGRESS, LayoutNodeType.CONTROLS,
            LayoutNodeType.SPACER, LayoutNodeType.TEXT
        ),
        controlButtons = listOf("previous", CONTROL_PLAY_PAUSE, "next")
    ),
    DESKTOP_PLAYER_BAR(
        "desktop.playerBar", "桌面播放栏",
        CONTAINERS + setOf(
            LayoutNodeType.COVER, LayoutNodeType.SONG_TITLE, LayoutNodeType.ARTIST, LayoutNodeType.SONG_INFO,
            LayoutNodeType.LIKE_BUTTON, LayoutNodeType.PROGRESS, LayoutNodeType.CONTROLS, LayoutNodeType.ACTIONS,
            LayoutNodeType.SPACER, LayoutNodeType.TEXT
        ),
        listOf("desktopLyric", "lyricsView", "queue", "device", "volume", "miniPlayer", "fullscreen")
    ),
    DESKTOP_NOW_PLAYING(
        "desktop.nowPlaying", "桌面「正在播放」顶部",
        CONTAINERS + setOf(
            LayoutNodeType.COVER, LayoutNodeType.SONG_TITLE, LayoutNodeType.ARTIST, LayoutNodeType.SONG_INFO,
            LayoutNodeType.LIKE_BUTTON, LayoutNodeType.PROGRESS, LayoutNodeType.CONTROLS, LayoutNodeType.SPACER,
            LayoutNodeType.TEXT
        )
    );

    // 缺少必需组件时返回原因
    fun missingRequired(root: PlayerLayoutNode): String? = when (this) {
        DESKTOP_PLAYER_BAR -> when {
            !root.hasButton(LayoutNodeType.CONTROLS, CONTROL_PLAY_PAUSE) -> "播放栏必须包含播放/暂停按钮"
            !root.contains(LayoutNodeType.PROGRESS) -> "播放栏必须包含进度条"
            else -> null
        }
        ANDROID_FULL_PLAYER, ANDROID_FULL_PLAYER_WIDE, ANDROID_MINI_PLAYER ->
            if (root.hasButton(LayoutNodeType.CONTROLS, CONTROL_PLAY_PAUSE)) null else "必须包含播放/暂停按钮"
        DESKTOP_NOW_PLAYING -> null
    }

    companion object {
        fun fromKey(key: String): PlayerLayoutArea? = entries.firstOrNull { it.key == key }
    }
}

private val CONTAINERS = setOf(LayoutNodeType.COLUMN, LayoutNodeType.ROW, LayoutNodeType.BOX)
private val ANDROID_ACTIONS = listOf("device", "queue", "share", "quality", "more")

const val CONTROL_PLAY_PAUSE = "playPause"
val CONTROL_BUTTONS = listOf("shuffle", "previous", CONTROL_PLAY_PAUSE, "next", "repeat")

sealed interface PlayerLayoutParseResult {
    data class Success(val root: PlayerLayoutNode, val warnings: List<String>) : PlayerLayoutParseResult
    data class Failure(val reason: String) : PlayerLayoutParseResult
}

// 布局描述校验：结构超限或缺少必需组件时整个区域无效；单个节点或参数不合法时忽略并提示
object PlayerLayoutParser {

    const val MAX_DEPTH = 8
    const val MAX_NODES = 128
    private const val MAX_PADDING = 64f
    private const val MAX_SPACING = 64f
    private const val MAX_DIMENSION = 1000f
    private const val MAX_WEIGHT = 10f
    private const val MIN_WEIGHT = 0.1f
    private const val MAX_RADIUS = 999f
    private const val MAX_TEXT = 40
    private const val MAX_LINES = 4
    private const val MIN_ASPECT = 0.25f
    private const val MAX_ASPECT = 4f
    private const val MIN_COVER = 24f
    private const val MAX_COLUMNS = 8
    private val ACTION_NAME = Regex("^[A-Za-z0-9_:-]{1,40}$")
    private val PAGE_COMPONENTS = setOf(
        LayoutNodeType.COLUMN, LayoutNodeType.ROW, LayoutNodeType.BOX, LayoutNodeType.SPACER, LayoutNodeType.TEXT,
        LayoutNodeType.HEADING, LayoutNodeType.IMAGE, LayoutNodeType.LIST, LayoutNodeType.TRACK_LIST,
        LayoutNodeType.BUTTON, LayoutNodeType.TOGGLE, LayoutNodeType.INPUT, LayoutNodeType.LOADING, LayoutNodeType.EMPTY,
        LayoutNodeType.SELECT, LayoutNodeType.SLIDER, LayoutNodeType.PROGRESS_BAR, LayoutNodeType.BADGE, LayoutNodeType.TABS
    )

    private class TooLarge(message: String) : Exception(message)

    fun parse(area: PlayerLayoutArea, root: JsonObject): PlayerLayoutParseResult {
        val node = parseTree(root, area.components, area.label, area.controlButtons, area.actionButtons)
        if (node !is PlayerLayoutParseResult.Success) return node
        area.missingRequired(node.root)?.let { return PlayerLayoutParseResult.Failure(it) }
        return node
    }

    // 插件页面：组件集合不同，节点的绑定字段需要额外校验
    fun parsePage(root: JsonObject): PlayerLayoutParseResult =
        parseTree(root, PAGE_COMPONENTS, "页面", emptyList(), emptyList())

    private fun parseTree(
        root: JsonObject,
        components: Set<LayoutNodeType>,
        label: String,
        controlButtons: List<String>,
        actionButtons: List<String>
    ): PlayerLayoutParseResult {
        val warnings = mutableListOf<String>()
        var count = 0
        fun build(json: JsonElement, depth: Int): PlayerLayoutNode? {
            if (depth > MAX_DEPTH) throw TooLarge("布局层级超过 $MAX_DEPTH 层")
            count++
            if (count > MAX_NODES) throw TooLarge("布局节点超过 $MAX_NODES 个")
            val obj = json as? JsonObject ?: return null.also { warnings += "布局里有无效节点，已忽略" }
            val typeKey = obj.string("type")
            val type = typeKey?.let(LayoutNodeType::fromKey) ?: return null.also { warnings += "未知组件 ${typeKey ?: "（缺少 type）"}，已忽略" }
            if (type !in components) return null.also { warnings += "${label}不支持组件 ${type.key}，已忽略" }
            val children = if (type.isContainer || type == LayoutNodeType.LIST || type == LayoutNodeType.TABS) {
                (obj["children"] as? JsonArray)?.mapNotNull { build(it, depth + 1) }.orEmpty()
            } else {
                emptyList()
            }
            val buttons = when (type) {
                LayoutNodeType.CONTROLS -> buttons(obj, controlButtons, "播放控制", warnings) ?: controlButtons
                LayoutNodeType.ACTIONS -> buttons(obj, actionButtons, "操作按钮", warnings) ?: actionButtons
                else -> emptyList()
            }
            if (type == LayoutNodeType.CONTROLS || type == LayoutNodeType.ACTIONS) {
                if (buttons.isEmpty()) return null.also { warnings += "${type.key} 没有可用的按钮，已忽略" }
            }
            val bind = obj.string("bind")?.trim().orEmpty()
            val action = obj.string("action")?.trim()?.takeIf { it.isNotEmpty() }
            pageNodeProblem(type, bind, action)?.let { return null.also { _ -> warnings += "${type.key} $it，已忽略" } }
            val options = if (type == LayoutNodeType.SELECT) PluginParser.parseOptions(obj["options"]) else emptyList()
            if (type == LayoutNodeType.SELECT && options.isEmpty()) return null.also { warnings += "select 没有可选项，已忽略" }
            val min = obj.float("min") ?: 0f
            val max = obj.float("max") ?: 1f
            if (type == LayoutNodeType.SLIDER && min >= max) return null.also { warnings += "slider 的 min、max 无效，已忽略" }
            if (type == LayoutNodeType.TABS && children.isEmpty()) return null.also { warnings += "tabs 没有子节点，已忽略" }
            return PlayerLayoutNode(
                type = type,
                children = children,
                padding = padding(obj["padding"], warnings),
                spacing = obj.float("spacing")?.coerceIn(0f, MAX_SPACING) ?: 0f,
                align = enumValue(obj, "align", LayoutAlign::fromKey, warnings) ?: LayoutAlign.START,
                weight = obj.float("weight")?.coerceIn(MIN_WEIGHT, MAX_WEIGHT),
                width = obj.float("width")?.coerceIn(0f, MAX_DIMENSION),
                height = obj.float("height")?.coerceIn(0f, MAX_DIMENSION),
                background = obj.string("background")?.let { key ->
                    PaletteToken.fromKey(key) ?: null.also { warnings += "背景 $key 不是色板项，已忽略" }
                },
                shape = enumValue(obj, "shape", LayoutShape::fromKey, warnings) ?: LayoutShape.RECT,
                cornerRadius = obj.float("radius")?.coerceIn(0f, MAX_RADIUS) ?: PlayerLayoutNode.DEFAULT_CORNER_RADIUS,
                buttons = buttons,
                size = enumValue(obj, "size", LayoutSize::fromKey, warnings) ?: LayoutSize.MEDIUM,
                text = obj.string("text")?.take(MAX_TEXT).orEmpty(),
                textStyle = enumValue(obj, "style", LayoutTextStyle::fromKey, warnings) ?: LayoutTextStyle.BODY,
                maxLines = obj.int("lines")?.coerceIn(1, MAX_LINES) ?: 1,
                showTime = (obj["showTime"] as? JsonPrimitive)?.booleanOrNull ?: true,
                aspect = obj.float("aspect")?.coerceIn(MIN_ASPECT, MAX_ASPECT) ?: 1f,
                maxSize = obj.float("maxSize")?.coerceIn(MIN_COVER, MAX_DIMENSION),
                bind = bind,
                action = action,
                visible = obj.string("visible")?.trim()?.takeIf { value ->
                    PluginBinding.isBinding(value).also { ok -> if (!ok) warnings += "visible 取值 $value 不是绑定，已忽略" }
                },
                options = options,
                min = min,
                max = max,
                step = obj.float("step")?.takeIf { it > 0f } ?: 0f,
                columns = obj.int("columns")?.coerceIn(1, MAX_COLUMNS) ?: 1
            )
        }
        val node = try {
            build(root, 1)
        } catch (e: TooLarge) {
            return PlayerLayoutParseResult.Failure(e.message ?: "布局过大")
        } ?: return PlayerLayoutParseResult.Failure("布局根节点无效")
        return PlayerLayoutParseResult.Success(node, warnings)
    }

    private fun pageNodeProblem(type: LayoutNodeType, bind: String, action: String?): String? = when (type) {
        LayoutNodeType.LIST, LayoutNodeType.TRACK_LIST ->
            if (PluginBinding.isBinding(bind)) null else "的 bind 需要写成 \$state.xxx 或 \$item.xxx"
        LayoutNodeType.TOGGLE, LayoutNodeType.INPUT, LayoutNodeType.SELECT, LayoutNodeType.SLIDER ->
            if (PluginBinding.isBinding(bind) && bind.startsWith("\$state.")) null else "的 bind 需要写成 \$state.xxx"
        LayoutNodeType.PROGRESS_BAR ->
            if (PluginBinding.isBinding(bind)) null else "的 bind 需要写成 \$state.xxx 或 \$item.xxx"
        LayoutNodeType.IMAGE -> if (bind.isEmpty()) "缺少 bind" else null
        LayoutNodeType.BUTTON -> if (action != null && ACTION_NAME.matches(action)) null else "缺少有效的 action"
        else -> if (action != null && !ACTION_NAME.matches(action)) "的 action 无效" else null
    }

    // 读取清单里某个页面的描述；缺少 playback.control 时去掉歌曲列表
    fun loadPage(manifest: PluginManifest, page: PluginPage): PlayerLayoutParseResult {
        val json = when (val ref = page.ui) {
            is PluginUiRef.Inline -> ref.tree
            is PluginUiRef.File -> readJson(manifest, ref.path) ?: return PlayerLayoutParseResult.Failure("${ref.path} 缺失或不是有效的 JSON 对象")
        }
        val parsed = parsePage(json)
        if (parsed !is PlayerLayoutParseResult.Success || PluginPermission.PLAYBACK_CONTROL in manifest.permissions) return parsed
        if (!parsed.root.contains(LayoutNodeType.TRACK_LIST)) return parsed
        fun strip(node: PlayerLayoutNode): PlayerLayoutNode =
            node.copy(children = node.children.filter { it.type != LayoutNodeType.TRACK_LIST }.map(::strip))
        return parsed.copy(root = strip(parsed.root), warnings = parsed.warnings + "trackList 需要 playback.control 权限，已忽略")
    }

    private fun readJson(manifest: PluginManifest, path: String): JsonObject? {
        val file = manifest.file(path) ?: return null
        return try {
            Json.parseToJsonElement(file.readText()) as? JsonObject
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    // 读取清单里某个区域的布局，文件缺失或不是 JSON 对象时失败
    fun load(manifest: PluginManifest, area: PlayerLayoutArea): PlayerLayoutParseResult {
        val json = when (val ref = manifest.contributes.playerLayouts[area.key]) {
            null -> return PlayerLayoutParseResult.Failure("插件没有提供这个区域的布局")
            is PluginUiRef.Inline -> ref.tree
            is PluginUiRef.File -> readJson(manifest, ref.path) ?: return PlayerLayoutParseResult.Failure("${ref.path} 缺失或不是有效的 JSON 对象")
        }
        return parse(area, json)
    }

    // 未写 buttons 时返回 null 表示用全部按钮
    private fun buttons(obj: JsonObject, allowed: List<String>, label: String, warnings: MutableList<String>): List<String>? {
        val array = obj["buttons"] as? JsonArray ?: return null
        return array.mapNotNull { item ->
            val key = (item as? JsonPrimitive)?.takeIf { it.isString }?.content
            key?.takeIf { it in allowed } ?: null.also { warnings += "$label 不支持按钮 ${key ?: item}，已忽略" }
        }.distinct()
    }

    // 一个数字为四边相同；两个为上下、左右；四个为上、右、下、左
    private fun padding(value: JsonElement?, warnings: MutableList<String>): LayoutPadding {
        fun clamp(v: Float) = v.coerceIn(0f, MAX_PADDING)
        return when (value) {
            null -> LayoutPadding.Zero
            is JsonPrimitive -> value.floatOrNull?.let { clamp(it) }?.let { LayoutPadding(it, it, it, it) }
                ?: LayoutPadding.Zero.also { warnings += "padding 无效，已忽略" }
            is JsonArray -> {
                val numbers = value.mapNotNull { (it as? JsonPrimitive)?.floatOrNull?.let(::clamp) }
                when {
                    numbers.size != value.size -> LayoutPadding.Zero.also { warnings += "padding 无效，已忽略" }
                    numbers.size == 2 -> LayoutPadding(numbers[0], numbers[1], numbers[0], numbers[1])
                    numbers.size == 4 -> LayoutPadding(numbers[0], numbers[1], numbers[2], numbers[3])
                    else -> LayoutPadding.Zero.also { warnings += "padding 无效，已忽略" }
                }
            }
            else -> LayoutPadding.Zero.also { warnings += "padding 无效，已忽略" }
        }
    }

    private fun <T> enumValue(obj: JsonObject, key: String, from: (String) -> T?, warnings: MutableList<String>): T? {
        val text = obj.string(key) ?: return null
        return from(text) ?: null.also { warnings += "$key 取值 $text 无效，已忽略" }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.float(key: String): Float? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.floatOrNull?.takeIf { it.isFinite() }
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
}

// 当前生效的播放器布局，key 为区域；没有插件布局的区域不在表里
val LocalPlayerLayouts = staticCompositionLocalOf<Map<PlayerLayoutArea, PlayerLayoutNode>> { emptyMap() }
