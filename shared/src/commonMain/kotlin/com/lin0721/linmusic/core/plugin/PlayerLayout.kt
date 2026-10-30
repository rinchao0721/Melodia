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
    TEXT("text");

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
    val maxSize: Float? = null
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

    private class TooLarge(message: String) : Exception(message)

    fun parse(area: PlayerLayoutArea, root: JsonObject): PlayerLayoutParseResult {
        val warnings = mutableListOf<String>()
        var count = 0
        fun build(json: JsonElement, depth: Int): PlayerLayoutNode? {
            if (depth > MAX_DEPTH) throw TooLarge("布局层级超过 $MAX_DEPTH 层")
            count++
            if (count > MAX_NODES) throw TooLarge("布局节点超过 $MAX_NODES 个")
            val obj = json as? JsonObject ?: return null.also { warnings += "布局里有无效节点，已忽略" }
            val typeKey = obj.string("type")
            val type = typeKey?.let(LayoutNodeType::fromKey) ?: return null.also { warnings += "未知组件 ${typeKey ?: "（缺少 type）"}，已忽略" }
            if (type !in area.components) return null.also { warnings += "${area.label}不支持组件 ${type.key}，已忽略" }
            val children = if (type.isContainer) {
                (obj["children"] as? JsonArray)?.mapNotNull { build(it, depth + 1) }.orEmpty()
            } else {
                emptyList()
            }
            val buttons = when (type) {
                LayoutNodeType.CONTROLS -> buttons(obj, area.controlButtons, "播放控制", warnings) ?: area.controlButtons
                LayoutNodeType.ACTIONS -> buttons(obj, area.actionButtons, "操作按钮", warnings) ?: area.actionButtons
                else -> emptyList()
            }
            if (type == LayoutNodeType.CONTROLS || type == LayoutNodeType.ACTIONS) {
                if (buttons.isEmpty()) return null.also { warnings += "${type.key} 没有可用的按钮，已忽略" }
            }
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
                maxSize = obj.float("maxSize")?.coerceIn(MIN_COVER, MAX_DIMENSION)
            )
        }
        val node = try {
            build(root, 1)
        } catch (e: TooLarge) {
            return PlayerLayoutParseResult.Failure(e.message ?: "布局过大")
        } ?: return PlayerLayoutParseResult.Failure("布局根节点无效")
        area.missingRequired(node)?.let { return PlayerLayoutParseResult.Failure(it) }
        return PlayerLayoutParseResult.Success(node, warnings)
    }

    // 读取清单里某个区域的布局，文件缺失或不是 JSON 对象时失败
    fun load(manifest: PluginManifest, area: PlayerLayoutArea): PlayerLayoutParseResult {
        val json = when (val ref = manifest.contributes.playerLayouts[area.key]) {
            null -> return PlayerLayoutParseResult.Failure("插件没有提供这个区域的布局")
            is PluginUiRef.Inline -> ref.tree
            is PluginUiRef.File -> {
                val file = manifest.file(ref.path) ?: return PlayerLayoutParseResult.Failure("缺少界面描述 ${ref.path}")
                try {
                    Json.parseToJsonElement(file.readText()) as? JsonObject
                } catch (e: SerializationException) {
                    null
                } catch (e: IllegalArgumentException) {
                    null
                } catch (e: IOException) {
                    null
                } ?: return PlayerLayoutParseResult.Failure("${ref.path} 不是有效的 JSON 对象")
            }
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
