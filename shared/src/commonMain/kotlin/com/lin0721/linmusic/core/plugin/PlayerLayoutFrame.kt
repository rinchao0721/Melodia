package com.lin0721.linmusic.core.plugin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull

enum class LayoutHeader(val key: String) {
    HOST("host"), NONE("none");

    companion object {
        fun fromKey(key: String): LayoutHeader? = entries.firstOrNull { it.key == key }
    }
}

sealed interface FrameBackground {
    // 宿主默认底色（封面取色）
    data object Host : FrameBackground
    data object None : FrameBackground
    data class Fill(val fill: LayoutFill) : FrameBackground
}

data class LayoutCorners(val topStart: Float, val topEnd: Float, val bottomEnd: Float, val bottomStart: Float) {
    constructor(all: Float) : this(all, all, all, all)
}

// 宿主原生模糊封面背景（API 1.12 起）；dim 为主题底色遮罩的透明度，分深浅色
data class LayoutBackdrop(
    val blur: Float = DEFAULT_BLUR,
    val scale: Float = DEFAULT_SCALE,
    val saturation: Float = DEFAULT_SATURATION,
    val dimLight: Float = DEFAULT_DIM_LIGHT,
    val dimDark: Float = DEFAULT_DIM_DARK,
    val drift: Boolean = true
) {
    companion object {
        const val DEFAULT_BLUR = 80f
        const val DEFAULT_SCALE = 1.3f
        const val DEFAULT_SATURATION = 1.2f
        const val DEFAULT_DIM_LIGHT = 0.08f
        const val DEFAULT_DIM_DARK = 0.35f
    }
}

// 歌词样式（API 1.12 起）；align、activeScale 为空表示沿用宿主与用户设置
data class LayoutLyricsStyle(
    val fontScale: Float = 1f,
    val align: LayoutAlign? = null,
    val activeScale: Float? = null,
    val inactiveBlur: Float = 0f,
    val lineSpacing: Float = 1f
)

// 宽屏右栏歌词的左右留白
data class LayoutLyricsPadding(val start: Float, val end: Float)

// 区域外框：宿主保留部分的取舍，各区域只读自己支持的字段
data class PlayerLayoutFrame(
    val header: LayoutHeader = LayoutHeader.HOST,
    val cards: Boolean = true,
    val split: Float? = null,
    val background: FrameBackground = FrameBackground.Host,
    val corners: LayoutCorners? = null,
    val shadow: Boolean = true,
    val border: Boolean = true,
    val contentColor: LayoutColor? = null,
    val canvas: String? = null,
    val canvasColors: Map<String, LayoutColor> = emptyMap(),
    val sungColor: LayoutColor? = null,
    val unsungColor: LayoutColor? = null,
    val harmonySungColor: LayoutColor? = null,
    val harmonyUnsungColor: LayoutColor? = null,
    val translationColor: LayoutColor? = null,
    val romanizationColor: LayoutColor? = null,
    val backdrop: LayoutBackdrop? = null,
    // 画布需要封面图（state.cover）
    val canvasCover: Boolean = false,
    val lyricsStyle: LayoutLyricsStyle? = null,
    val lyricsPadding: LayoutLyricsPadding? = null,
    val lyricsMaxWidth: Float? = null
) {
    companion object {
        val Default = PlayerLayoutFrame()
    }
}

enum class LayoutOrientation(val key: String) {
    PORTRAIT("portrait"), LANDSCAPE("landscape");

    companion object {
        fun fromKey(key: String): LayoutOrientation? = entries.firstOrNull { it.key == key }
    }
}

data class LayoutCondition(
    val minWidth: Float? = null,
    val maxWidth: Float? = null,
    val orientation: LayoutOrientation? = null
) {
    val isEmpty: Boolean get() = minWidth == null && maxWidth == null && orientation == null

    fun matches(widthDp: Float, landscape: Boolean): Boolean =
        (minWidth == null || widthDp >= minWidth) &&
            (maxWidth == null || widthDp <= maxWidth) &&
            (orientation == null || (orientation == LayoutOrientation.LANDSCAPE) == landscape)
}

data class PlayerLayoutVariant(val condition: LayoutCondition, val root: PlayerLayoutNode)

// 一个区域的完整布局：默认布局树、外框与按尺寸替换的变体
data class PlayerLayoutSpec(
    val root: PlayerLayoutNode,
    val frame: PlayerLayoutFrame = PlayerLayoutFrame.Default,
    val variants: List<PlayerLayoutVariant> = emptyList(),
    // 提供布局的插件，脚本动作发给它
    val pluginId: String = "",
    val hostContent: Boolean = false,
    val canvasScript: String? = null,
    // 插件声明 apiVersion ≥ 1.10 时横屏改读 android.fullPlayerWide
    val separateLandscape: Boolean = false,
    // 插件声明 apiVersion ≥ 1.12 时才启用的行为（宽屏右栏卡片开关等）
    val extendedApi: Boolean = false
) {
    // 自定义背景：画布或原生模糊封面，任一存在时播放页不再画宿主默认背景
    val hasCustomBackdrop: Boolean get() = canvasScript != null || frame.backdrop != null

    val usesScript: Boolean get() = root.usesScript() || variants.any { it.root.usesScript() }

    // 第一条满足条件的变体生效，都不满足时用默认布局
    fun select(widthDp: Float, landscape: Boolean): PlayerLayoutNode =
        variants.firstOrNull { it.condition.matches(widthDp, landscape) }?.root ?: root

    // 默认布局或任一变体里有这个组件
    fun contains(type: LayoutNodeType): Boolean = root.contains(type) || variants.any { it.root.contains(type) }
}

internal object PlayerLayoutFrameParser {

    private const val MIN_SPLIT = 0.3f
    private const val MAX_SPLIT = 0.7f
    private const val MAX_RADIUS = 999f
    private const val MAX_WIDTH_CONDITION = 10000f

    private const val MAX_LYRICS_PADDING = 120f
    private const val MIN_LYRICS_WIDTH = 320f
    private const val MAX_LYRICS_WIDTH = 1000f

    fun parse(
        area: PlayerLayoutArea,
        value: JsonElement?,
        warnings: MutableList<String>,
        apiMinor: Int = PLUGIN_API_MINOR
    ): PlayerLayoutFrame {
        if (value == null) return PlayerLayoutFrame.Default
        val obj = value as? JsonObject ?: return PlayerLayoutFrame.Default.also { warnings += "frame 不是对象，已忽略" }
        var frame = PlayerLayoutFrame.Default
        val extended = apiMinor >= PlayerLayoutArea.EXTENDED_API_MINOR
        val fields = if (extended) area.frameFields + area.extendedFrameFields else area.frameFields
        obj.forEach { (key, item) ->
            if (key !in fields) {
                warnings += "${area.label}不支持 frame.$key，已忽略"
                return@forEach
            }
            val next = when (key) {
                "canvasColors" -> (item as? JsonObject)?.takeIf { it.size <= 16 }?.let { colors ->
                    val parsed = colors.mapNotNull { (name, value) ->
                        if (!name.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,31}"))) null
                        else LayoutStyleParser.color(value, extended)?.let { name to it }
                    }
                    if (parsed.size == colors.size) frame.copy(canvasColors = parsed.toMap()) else null
                }
                "canvas" -> item.text()?.takeIf(PluginPaths::isBackground)?.let { frame.copy(canvas = it) }
                "sungColor" -> LayoutStyleParser.color(item, extended)?.let { frame.copy(sungColor = it) }
                "unsungColor" -> LayoutStyleParser.color(item, extended)?.let { frame.copy(unsungColor = it) }
                "harmonySungColor" -> LayoutStyleParser.color(item, extended)?.let { frame.copy(harmonySungColor = it) }
                "harmonyUnsungColor" -> LayoutStyleParser.color(item, extended)?.let { frame.copy(harmonyUnsungColor = it) }
                "translationColor" -> LayoutStyleParser.color(item, extended)?.let { frame.copy(translationColor = it) }
                "romanizationColor" -> LayoutStyleParser.color(item, extended)?.let { frame.copy(romanizationColor = it) }
                "contentColor" -> LayoutStyleParser.color(item, extended)?.let { frame.copy(contentColor = it) }
                "header" -> item.text()?.let(LayoutHeader::fromKey)?.let { frame.copy(header = it) }
                "cards" -> item.bool()?.let { frame.copy(cards = it) }
                "split" -> item.number()?.let { frame.copy(split = it.coerceIn(MIN_SPLIT, MAX_SPLIT)) }
                "background" -> when (item.text()) {
                    "host" -> frame.copy(background = FrameBackground.Host)
                    "none" -> frame.copy(background = FrameBackground.None)
                    else -> LayoutStyleParser.fill(item, extended)?.let { frame.copy(background = FrameBackground.Fill(it)) }
                }
                "radius" -> parseCorners(item, MAX_RADIUS)?.let { frame.copy(corners = it) }
                "shadow" -> item.bool()?.let { frame.copy(shadow = it) }
                "border" -> item.bool()?.let { frame.copy(border = it) }
                "backdrop" -> backdrop(item, warnings)?.let { frame.copy(backdrop = it) }
                "canvasCover" -> item.bool()?.let { frame.copy(canvasCover = it) }
                "lyricsStyle" -> lyricsStyle(item, warnings)?.let { frame.copy(lyricsStyle = it) }
                "lyricsPadding" -> (item as? JsonArray)?.takeIf { it.size == 2 }?.mapNotNull { it.number() }
                    ?.takeIf { it.size == 2 }
                    ?.let { frame.copy(lyricsPadding = LayoutLyricsPadding(it[0].coerceIn(0f, MAX_LYRICS_PADDING), it[1].coerceIn(0f, MAX_LYRICS_PADDING))) }
                "lyricsMaxWidth" -> item.number()?.let { frame.copy(lyricsMaxWidth = it.coerceIn(MIN_LYRICS_WIDTH, MAX_LYRICS_WIDTH)) }
                else -> frame
            }
            frame = next ?: frame.also { warnings += "frame.$key 取值无效，已忽略" }
        }
        return frame
    }

    // type 缺省按 coverBlur；其他类型整体无效，单个字段非法时只忽略该字段
    private fun backdrop(value: JsonElement, warnings: MutableList<String>): LayoutBackdrop? {
        val obj = value as? JsonObject ?: return null
        val type = obj["type"]?.let { it.text() ?: return null } ?: "coverBlur"
        if (type != "coverBlur") return null
        var result = LayoutBackdrop()
        obj.forEach { (key, item) ->
            val next = when (key) {
                "type" -> result
                "blur" -> item.number()?.let { result.copy(blur = it.coerceIn(0f, 150f)) }
                "scale" -> item.number()?.let { result.copy(scale = it.coerceIn(1f, 2f)) }
                "saturation" -> item.number()?.let { result.copy(saturation = it.coerceIn(0f, 3f)) }
                "drift" -> item.bool()?.let { result.copy(drift = it) }
                "dim" -> dim(item, result)
                else -> {
                    warnings += "backdrop 不支持字段 $key，已忽略"
                    return@forEach
                }
            }
            result = next ?: result.also { warnings += "backdrop.$key 取值无效，已忽略" }
        }
        return result
    }

    // 数字为深浅色共用；对象里缺省的一侧保持当前值
    private fun dim(item: JsonElement, current: LayoutBackdrop): LayoutBackdrop? {
        if (item !is JsonObject) return item.number()?.let { current.copy(dimLight = it.coerceIn(0f, 1f), dimDark = it.coerceIn(0f, 1f)) }
        val light = if ("light" in item) item["light"]?.number() ?: return null else current.dimLight
        val dark = if ("dark" in item) item["dark"]?.number() ?: return null else current.dimDark
        return current.copy(dimLight = light.coerceIn(0f, 1f), dimDark = dark.coerceIn(0f, 1f))
    }

    private fun lyricsStyle(value: JsonElement, warnings: MutableList<String>): LayoutLyricsStyle? {
        val obj = value as? JsonObject ?: return null
        var result = LayoutLyricsStyle()
        obj.forEach { (key, item) ->
            val next = when (key) {
                "fontScale" -> item.number()?.let { result.copy(fontScale = it.coerceIn(0.7f, 1.6f)) }
                "align" -> when (item.text()) {
                    "start" -> result.copy(align = LayoutAlign.START)
                    "center" -> result.copy(align = LayoutAlign.CENTER)
                    else -> null
                }
                "activeScale" -> item.number()?.let { result.copy(activeScale = it.coerceIn(1f, 1.3f)) }
                "inactiveBlur" -> item.number()?.let { result.copy(inactiveBlur = it.coerceIn(0f, 6f)) }
                "lineSpacing" -> item.number()?.let { result.copy(lineSpacing = it.coerceIn(0.8f, 2f)) }
                else -> {
                    warnings += "lyricsStyle 不支持字段 $key，已忽略"
                    return@forEach
                }
            }
            result = next ?: result.also { warnings += "lyricsStyle.$key 取值无效，已忽略" }
        }
        return result
    }

    // 未写任何有效条件时返回 null
    fun parseCondition(value: JsonElement?, warnings: MutableList<String>, label: String): LayoutCondition? {
        val obj = value as? JsonObject ?: return null
        var condition = LayoutCondition()
        obj.forEach { (key, item) ->
            val next = when (key) {
                "minWidth" -> item.number()?.let { condition.copy(minWidth = it.coerceIn(0f, MAX_WIDTH_CONDITION)) }
                "maxWidth" -> item.number()?.let { condition.copy(maxWidth = it.coerceIn(0f, MAX_WIDTH_CONDITION)) }
                "orientation" -> item.text()?.let(LayoutOrientation::fromKey)?.let { condition.copy(orientation = it) }
                else -> null
            }
            condition = next ?: condition.also { warnings += "$label 的条件 $key 无效，已忽略" }
        }
        return condition.takeUnless { it.isEmpty }
    }

    private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement.bool(): Boolean? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
    private fun JsonElement.number(): Float? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.floatOrNull?.takeIf { it.isFinite() }
}

// 一个数字为四角相同；四个数字为左上、右上、右下、左下
internal fun parseCorners(value: JsonElement?, max: Float): LayoutCorners? {
    fun number(element: JsonElement): Float? =
        (element as? JsonPrimitive)?.takeIf { !it.isString }?.floatOrNull?.takeIf { it.isFinite() }?.coerceIn(0f, max)
    return when (value) {
        is JsonPrimitive -> number(value)?.let(::LayoutCorners)
        is JsonArray -> {
            val numbers = value.mapNotNull(::number)
            if (numbers.size == 4 && value.size == 4) LayoutCorners(numbers[0], numbers[1], numbers[2], numbers[3]) else null
        }
        else -> null
    }
}
