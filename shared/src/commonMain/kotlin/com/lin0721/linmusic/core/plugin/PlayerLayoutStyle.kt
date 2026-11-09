package com.lin0721.linmusic.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
    data class HuePalette(val source: LayoutColor, val minSaturation: Float, val neutral: LayoutColor,
        val stops: List<Pair<Float, LayoutColor>>) : LayoutColor
    data class Adjust(val source: LayoutColor, val lightHue: Float, val darkHue: Float, val lightSaturation: Float, val darkSaturation: Float) : LayoutColor
    data class Mix(val from: LayoutColor, val to: LayoutColor, val lightAmount: Float, val darkAmount: Float) : LayoutColor
}

sealed interface LayoutFill {
    data class Solid(val color: LayoutColor) : LayoutFill
    data class Gradient(val colors: List<LayoutColor>, val angle: Float) : LayoutFill
}

data class LayoutBorder(val width: Float, val color: LayoutColor)

@Composable
@ReadOnlyComposable
fun LayoutColor.resolve(cover: LayoutCoverColors? = LocalLayoutCoverColors.current): Color {
    val palette = LocalMelodiaPalette.current
    return when (this) {
        is LayoutColor.Token -> token.read(palette)
        is LayoutColor.Cover -> cover?.let { if (accent) it.accent else it.base }
            ?: if (accent) palette.accent else palette.coverPlaceholder
        is LayoutColor.Fixed -> if (palette.isDark) dark else light
        is LayoutColor.HuePalette -> {
            val input = source.resolve(cover)
            select(input).resolve(cover)
        }
        is LayoutColor.Adjust -> source.resolve(cover).adjustHsl(
            if (palette.isDark) darkHue else lightHue,
            if (palette.isDark) darkSaturation else lightSaturation)
        is LayoutColor.Mix -> lerp(from.resolve(cover), to.resolve(cover), if (palette.isDark) darkAmount else lightAmount)
    }
}

// Preserve HSL lightness and alpha; achromatic inputs stay achromatic.
internal fun Color.adjustHsl(hue: Float, saturation: Float): Color {
    val hi = maxOf(red, green, blue)
    val lo = minOf(red, green, blue)
    val delta = hi - lo
    if (delta == 0f || (hue == 0f && saturation == 1f)) return this
    val lightness = (hi + lo) / 2f
    val capacity = 1f - kotlin.math.abs(2f * lightness - 1f)
    val sat = (delta / capacity * saturation).coerceIn(0f, 1f)
    val originalHue = when (hi) {
        red -> (green - blue) / delta
        green -> (blue - red) / delta + 2f
        else -> (red - green) / delta + 4f
    } * 60f
    val h = (((originalHue + hue) % 360f) + 360f) % 360f / 60f
    val chroma = capacity * sat
    val x = chroma * (1f - kotlin.math.abs(h % 2f - 1f))
    val m = lightness - chroma / 2f
    val rgb = when (h.toInt()) {
        0 -> floatArrayOf(chroma, x, 0f)
        1 -> floatArrayOf(x, chroma, 0f)
        2 -> floatArrayOf(0f, chroma, x)
        3 -> floatArrayOf(0f, x, chroma)
        4 -> floatArrayOf(x, 0f, chroma)
        else -> floatArrayOf(chroma, 0f, x)
    }
    return Color((rgb[0] + m).coerceIn(0f, 1f), (rgb[1] + m).coerceIn(0f, 1f),
        (rgb[2] + m).coerceIn(0f, 1f), alpha, colorSpace)
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

    fun color(value: JsonElement?): LayoutColor? = color(value, 0)

    private fun color(value: JsonElement?, depth: Int): LayoutColor? {
        if (depth > 4) return null
        return when (value) {
            is JsonPrimitive -> value.takeIf { it.isString }?.content?.trim()?.let { text ->
                COVER[text]?.let(LayoutColor::Cover)
                    ?: hex(text)?.let { LayoutColor.Fixed(it, it) }
                    ?: PaletteToken.fromKey(text)?.let(LayoutColor::Token)
            }
            is JsonObject -> {
                if ("huePalette" in value) {
                    val source = color(value["huePalette"], depth + 1) ?: return null
                    val neutral = color(value["neutral"], depth + 1) ?: return null
                    val min = (value["minSaturation"] as? JsonPrimitive)?.takeIf { !it.isString }
                        ?.floatOrNull?.takeIf { it.isFinite() && it in 0f..1f } ?: return null
                    val entries = value["stops"] as? JsonArray ?: return null
                    if (entries.size !in 1..16) return null
                    val stops = mutableListOf<Pair<Float, LayoutColor>>()
                    for (entry in entries) {
                        val obj = entry as? JsonObject ?: return null
                        val hue = (obj["hue"] as? JsonPrimitive)?.takeIf { !it.isString }
                            ?.floatOrNull?.takeIf { it.isFinite() && it >= 0f && it < 360f } ?: return null
                        if (stops.isNotEmpty() && hue <= stops.last().first) return null
                        val output = color(obj["color"], depth + 1) ?: return null
                        stops += hue to output
                    }
                    return LayoutColor.HuePalette(source, min, neutral, stops)
                }
                if ("adjust" in value) {
                    if ("mix" in value) return null
                    val source = color(value["adjust"], depth + 1) ?: return null
                    fun number(item: JsonElement?): Float? = (item as? JsonPrimitive)
                        ?.takeIf { !it.isString }?.floatOrNull?.takeIf { it.isFinite() }
                    fun parameter(key: String, default: Float): Pair<Float, Float>? {
                        val item = value[key] ?: return default to default
                        if (item is JsonObject) {
                            val light = number(item["light"]) ?: return null
                            val dark = number(item["dark"]) ?: return null
                            return light to dark
                        }
                        val scalar = number(item) ?: return null
                        return scalar to scalar
                    }
                    val hue = parameter("hue", 0f) ?: return null
                    val saturation = parameter("saturation", 1f) ?: return null
                    fun wrap(v: Float) = ((v % 360f) + 360f) % 360f
                    return LayoutColor.Adjust(source, wrap(hue.first), wrap(hue.second),
                        saturation.first.coerceIn(0f, 4f), saturation.second.coerceIn(0f, 4f))
                }
                if ("mix" in value) {
                    val pair = value["mix"] as? JsonArray ?: return null
                    if (pair.size != 2) return null
                    val from = color(pair[0], depth + 1) ?: return null
                    val to = color(pair[1], depth + 1) ?: return null
                    fun amount(item: JsonElement?): Float? = (item as? JsonPrimitive)
                        ?.takeIf { !it.isString }?.floatOrNull?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
                    val weights = value["amount"]
                    val light = if (weights is JsonObject) amount(weights["light"]) else amount(weights)
                    val dark = if (weights is JsonObject) amount(weights["dark"]) else amount(weights)
                    if (light == null || dark == null) return null
                    return LayoutColor.Mix(from, to, light, dark)
                }
                val light = (value["light"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::hex)
                val dark = (value["dark"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::hex)
                if (light != null && dark != null) LayoutColor.Fixed(light, dark) else null
            }
            else -> null
        }
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

// Generic hue buckets. The plugin owns all thresholds, stops and output colors.
internal fun LayoutColor.HuePalette.select(input: Color): LayoutColor {
    val hi = maxOf(input.red, input.green, input.blue)
    val lo = minOf(input.red, input.green, input.blue)
    val delta = hi - lo
    val saturation = if (hi == 0f) 0f else delta / hi
    if (delta == 0f || saturation < minSaturation) return neutral
    val raw = when (hi) {
        input.red -> (input.green - input.blue) / delta
        input.green -> (input.blue - input.red) / delta + 2f
        else -> (input.red - input.green) / delta + 4f
    } * 60f
    val hue = (raw + 360f) % 360f
    return stops.lastOrNull { hue >= it.first }?.second ?: stops.last().second
}
