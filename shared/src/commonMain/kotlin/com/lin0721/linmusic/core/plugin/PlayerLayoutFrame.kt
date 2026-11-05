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

// 区域外框：宿主保留部分的取舍，各区域只读自己支持的字段
data class PlayerLayoutFrame(
    val header: LayoutHeader = LayoutHeader.HOST,
    val cards: Boolean = true,
    val split: Float? = null,
    val background: FrameBackground = FrameBackground.Host,
    val corners: LayoutCorners? = null,
    val shadow: Boolean = true,
    val border: Boolean = true
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
    val pluginId: String = ""
) {
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

    fun parse(area: PlayerLayoutArea, value: JsonElement?, warnings: MutableList<String>): PlayerLayoutFrame {
        if (value == null) return PlayerLayoutFrame.Default
        val obj = value as? JsonObject ?: return PlayerLayoutFrame.Default.also { warnings += "frame 不是对象，已忽略" }
        var frame = PlayerLayoutFrame.Default
        obj.forEach { (key, item) ->
            if (key !in area.frameFields) {
                warnings += "${area.label}不支持 frame.$key，已忽略"
                return@forEach
            }
            val next = when (key) {
                "header" -> item.text()?.let(LayoutHeader::fromKey)?.let { frame.copy(header = it) }
                "cards" -> item.bool()?.let { frame.copy(cards = it) }
                "split" -> item.number()?.let { frame.copy(split = it.coerceIn(MIN_SPLIT, MAX_SPLIT)) }
                "background" -> when (item.text()) {
                    "host" -> frame.copy(background = FrameBackground.Host)
                    "none" -> frame.copy(background = FrameBackground.None)
                    else -> LayoutStyleParser.fill(item)?.let { frame.copy(background = FrameBackground.Fill(it)) }
                }
                "radius" -> parseCorners(item, MAX_RADIUS)?.let { frame.copy(corners = it) }
                "shadow" -> item.bool()?.let { frame.copy(shadow = it) }
                "border" -> item.bool()?.let { frame.copy(border = it) }
                else -> frame
            }
            frame = next ?: frame.also { warnings += "frame.$key 取值无效，已忽略" }
        }
        return frame
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
