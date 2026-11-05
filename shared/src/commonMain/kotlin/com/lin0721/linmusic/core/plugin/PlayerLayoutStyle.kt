package com.lin0721.linmusic.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.lin0721.linmusic.core.ui.skin.PaletteToken
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaPalette
import com.lin0721.linmusic.core.ui.theme.MelodiaPalette
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull

// 封面取色；各端在播放器区域外层提供，没有时退回色板
data class LayoutCoverColors(val base: Color, val accent: Color)

val LocalLayoutCoverColors = compositionLocalOf<LayoutCoverColors?> { null }

sealed interface LayoutColor {
    data class Token(val token: PaletteToken) : LayoutColor
    data class Cover(val accent: Boolean) : LayoutColor
    // 固定颜色按深浅色分开，只写一个值时两种模式共用
    data class Fixed(val light: Color, val dark: Color) : LayoutColor
}

sealed interface LayoutFill {
    data class Solid(val color: LayoutColor) : LayoutFill
    data class Gradient(val colors: List<LayoutColor>, val angle: Float) : LayoutFill
}

data class LayoutBorder(val width: Float, val color: LayoutColor)

@Composable
@ReadOnlyComposable
fun LayoutColor.resolve(): Color {
    val palette = LocalMelodiaPalette.current
    return when (this) {
        is LayoutColor.Token -> token.read(palette)
        is LayoutColor.Cover -> LocalLayoutCoverColors.current?.let { if (accent) it.accent else it.base }
            ?: if (accent) palette.accent else palette.coverPlaceholder
        is LayoutColor.Fixed -> if (palette.isDark) dark else light
    }
}

// 子树里的文字色都换成同一个颜色，次级与三级按透明度递减
fun MelodiaPalette.withContentColor(color: Color): MelodiaPalette = copy(
    textPrimary = color,
    textSecondary = color.copy(alpha = color.alpha * SECONDARY_ALPHA),
    textTertiary = color.copy(alpha = color.alpha * TERTIARY_ALPHA),
    textDisabled = color.copy(alpha = color.alpha * DISABLED_ALPHA)
)

private const val SECONDARY_ALPHA = 0.72f
private const val TERTIARY_ALPHA = 0.5f
private const val DISABLED_ALPHA = 0.3f

internal object LayoutStyleParser {

    const val MAX_STYLES = 32
    private const val MAX_EXTENDS = 4
    private const val MAX_GRADIENT = 4
    private val HEX = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
    private val COVER = mapOf("cover.base" to false, "cover.accent" to true)
    // 样式里只能写外观属性
    private val FORBIDDEN = setOf("type", "children", "buttons", "bind", "action", "visible", "class")

    fun color(value: JsonElement?): LayoutColor? = when (value) {
        is JsonPrimitive -> value.takeIf { it.isString }?.content?.trim()?.let { text ->
            COVER[text]?.let(LayoutColor::Cover)
                ?: hex(text)?.let { LayoutColor.Fixed(it, it) }
                ?: PaletteToken.fromKey(text)?.let(LayoutColor::Token)
        }
        is JsonObject -> {
            val light = (value["light"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::hex)
            val dark = (value["dark"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::hex)
            if (light != null && dark != null) LayoutColor.Fixed(light, dark) else null
        }
        else -> null
    }

    fun fill(value: JsonElement?): LayoutFill? {
        val obj = value as? JsonObject
        if (obj == null || "gradient" !in obj) return color(value)?.let(LayoutFill::Solid)
        val colors = (obj["gradient"] as? JsonArray)?.map(::color) ?: return null
        if (colors.size !in 2..MAX_GRADIENT || colors.any { it == null }) return null
        val angle = (obj["angle"] as? JsonPrimitive)?.takeIf { !it.isString }?.floatOrNull?.takeIf { it.isFinite() } ?: 90f
        return LayoutFill.Gradient(colors.filterNotNull(), ((angle % 360f) + 360f) % 360f)
    }

    fun border(value: JsonElement?, maxWidth: Float): LayoutBorder? {
        val obj = value as? JsonObject ?: return null
        val width = (obj["width"] as? JsonPrimitive)?.takeIf { !it.isString }?.floatOrNull?.takeIf { it.isFinite() } ?: return null
        val color = color(obj["color"]) ?: return null
        return LayoutBorder(width.coerceIn(0f, maxWidth), color)
    }

    private fun hex(text: String): Color? {
        if (!HEX.matches(text)) return null
        val digits = text.removePrefix("#")
        val argb = if (digits.length == 6) "FF$digits" else digits
        return argb.toLongOrNull(16)?.let { Color(it.toInt()) }
    }

    // 展开继承后的样式表；无效的样式不进表，引用时按不存在处理
    fun styles(value: JsonElement?, warnings: MutableList<String>): Map<String, JsonObject> {
        if (value == null) return emptyMap()
        val raw = value as? JsonObject ?: return emptyMap<String, JsonObject>().also { warnings += "styles 不是对象，已忽略" }
        if (raw.size > MAX_STYLES) warnings += "styles 最多 $MAX_STYLES 个，多出的已忽略"
        val entries = raw.entries.take(MAX_STYLES).mapNotNull { (name, style) ->
            (style as? JsonObject)?.let { name to it } ?: null.also { warnings += "样式 $name 不是对象，已忽略" }
        }.toMap()
        val resolved = LinkedHashMap<String, JsonObject>()
        for ((name, style) in entries) {
            val chain = mutableListOf(name)
            var merged = style
            var parentName = (style["extends"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            var failure: String? = null
            while (parentName != null) {
                if (parentName in chain) { failure = "继承出现循环"; break }
                if (chain.size > MAX_EXTENDS) { failure = "继承超过 $MAX_EXTENDS 层"; break }
                val parent = entries[parentName]
                if (parent == null) { failure = "继承的样式 $parentName 不存在"; break }
                chain += parentName
                merged = JsonObject(parent + merged)
                parentName = (parent["extends"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            }
            if (failure != null) {
                warnings += "样式 $name 无效（$failure），已忽略"
                continue
            }
            val forbidden = merged.keys.filter { it in FORBIDDEN }
            if (forbidden.isNotEmpty()) warnings += "样式 $name 不能写 ${forbidden.joinToString("、")}，已忽略这些字段"
            resolved[name] = JsonObject(merged.filterKeys { it !in FORBIDDEN && it != "extends" })
        }
        return resolved
    }
}
