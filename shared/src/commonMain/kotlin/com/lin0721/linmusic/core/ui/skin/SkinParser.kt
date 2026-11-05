package com.lin0721.linmusic.core.ui.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.theme.MelodiaStyle
import com.lin0721.linmusic.core.ui.theme.ThemeControlStyle
import com.lin0721.linmusic.core.ui.theme.ThemeDensity
import com.lin0721.linmusic.core.ui.theme.ThemeFont
import com.lin0721.linmusic.core.ui.theme.ThemeProgressStyle
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import kotlin.math.roundToInt

sealed interface SkinParseResult {
    data class Success(val skin: Skin, val warnings: List<String>) : SkinParseResult
    data class Failure(val reason: String) : SkinParseResult
}

// 解析 skin.json：结构错误直接失败，单项取值不合法时忽略该项并记入 warnings
object SkinParser {

    private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")
    private const val MAX_NAME_LENGTH = 40
    private const val MAX_VERSION_LENGTH = 20

    private const val MAX_PANE_RADIUS = 24f
    private const val MAX_CARD_RADIUS = 32f
    private const val MAX_SHAPE_RADIUS = 999f
    private const val MIN_BORDER_WIDTH = 0.5f
    private const val MAX_BORDER_WIDTH = 2f
    private const val MAX_BLUR = 40f
    private const val MAX_DIM = 0.9f
    private const val MIN_PANE_OPACITY = 0.5f

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String, directory: File? = null): SkinParseResult {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: return SkinParseResult.Failure("skin.json 不是有效的 JSON 对象")
        return parse(root, directory)
    }

    fun parse(root: JsonObject, directory: File? = null): SkinParseResult {
        val format = root.int("format") ?: return SkinParseResult.Failure("缺少 format 字段")
        if (format < 1) return SkinParseResult.Failure("format 取值无效")
        if (format > SKIN_FORMAT_VERSION) return SkinParseResult.Failure("皮肤需要更新版本的应用才能使用")

        val id = root.string("id")?.trim().orEmpty()
        if (!ID_PATTERN.matches(id) || id == BUILTIN_SKIN_ID) {
            return SkinParseResult.Failure("id 只能由小写字母、数字、- 和 _ 组成，且不能为 $BUILTIN_SKIN_ID")
        }
        val name = root.string("name")?.trim().orEmpty()
        if (name.isEmpty()) return SkinParseResult.Failure("缺少皮肤名称")

        val warnings = mutableListOf<String>()
        val modes = parseModes(root["modes"], warnings)
        val colors = root["colors"] as? JsonObject
        if (root["colors"] != null && colors == null) warnings += "colors 不是对象，已忽略"

        val skin = Skin(
            id = id,
            name = name.take(MAX_NAME_LENGTH),
            author = root.string("author")?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NAME_LENGTH),
            version = root.string("version")?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_VERSION_LENGTH),
            modes = modes,
            darkColors = parseColors(colors?.get(SkinMode.DARK.key), SkinMode.DARK, warnings),
            lightColors = parseColors(colors?.get(SkinMode.LIGHT.key), SkinMode.LIGHT, warnings),
            background = parseBackground(root["background"], directory, warnings),
            style = parseStyle(root["style"], warnings),
            layouts = parseLayouts(root["layouts"], warnings),
            directory = directory,
            cover = parseCover(root["cover"], warnings)
        )
        return SkinParseResult.Success(skin, warnings)
    }

    // 接受 #RRGGBB 与 #AARRGGBB
    fun parseColor(text: String): Color? {
        val body = text.trim().removePrefix("#")
        if ((body.length != 6 && body.length != 8) || body.any { it.digitToIntOrNull(16) == null }) return null
        val value = body.toLong(16)
        val argb = if (body.length == 6) value or 0xFF000000L else value
        return Color(argb.toInt())
    }

    fun encodeColor(color: Color): String {
        fun channel(value: Float) = (value.coerceIn(0f, 1f) * 255f).roundToInt().toString(16).padStart(2, '0')
        val rgb = channel(color.red) + channel(color.green) + channel(color.blue)
        val body = if (color.alpha >= 1f) rgb else channel(color.alpha) + rgb
        return "#" + body.uppercase()
    }

    private fun parseModes(element: JsonElement?, warnings: MutableList<String>): Set<SkinMode> {
        val all = SkinMode.entries.toSet()
        if (element == null) return all
        val list = element as? JsonArray
        val modes = list?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.let(SkinMode::fromKey) }?.toSet()
        if (modes.isNullOrEmpty()) {
            warnings += "modes 取值无效，按同时支持深色与浅色处理"
            return all
        }
        return modes
    }

    private fun parseColors(element: JsonElement?, mode: SkinMode, warnings: MutableList<String>): Map<PaletteToken, Color> {
        if (element == null) return emptyMap()
        val obj = element as? JsonObject ?: run {
            warnings += "colors.${mode.key} 不是对象，已忽略"
            return emptyMap()
        }
        val result = LinkedHashMap<PaletteToken, Color>()
        obj.forEach { (key, value) ->
            val token = PaletteToken.fromKey(key)
            if (token == null) {
                warnings += "colors.${mode.key}.$key 不是可识别的颜色项，已忽略"
                return@forEach
            }
            val color = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::parseColor)
            if (color == null) {
                warnings += "colors.${mode.key}.$key 色值无效，已忽略"
                return@forEach
            }
            result[token] = color
        }
        return result
    }

    private fun parseBackground(element: JsonElement?, directory: File?, warnings: MutableList<String>): SkinBackground? {
        if (element == null) return null
        val obj = element as? JsonObject
        val image = obj?.string("image")?.trim()
        if (obj == null || image.isNullOrEmpty()) {
            warnings += "background 缺少 image，已忽略背景图"
            return null
        }
        if (!SkinPaths.isImageAsset(image)) {
            warnings += "背景图路径 $image 无效，只支持 assets/ 下的 png、jpg、webp"
            return null
        }
        if (directory != null && !File(directory, image).isFile) {
            warnings += "皮肤包里找不到背景图 $image"
            return null
        }
        val scopeKey = obj.string("scope")
        val scope = scopeKey?.let(SkinBackgroundScope::fromKey) ?: SkinBackgroundScope.APP.also {
            if (scopeKey != null) warnings += "background.scope 取值无效，按 app 处理"
        }
        return SkinBackground(
            image = image,
            blur = obj.clampedFloat("blur", 0f, MAX_BLUR, 0f, "background.blur", warnings),
            dim = obj.clampedFloat("dim", 0f, MAX_DIM, 0f, "background.dim", warnings),
            paneOpacity = obj.clampedFloat("paneOpacity", MIN_PANE_OPACITY, 1f, 1f, "background.paneOpacity", warnings),
            scope = scope,
            playerDim = if (obj["playerDim"] == null) null else obj.clampedFloat("playerDim", 0f, MAX_DIM, 0f, "background.playerDim", warnings)
        )
    }

    private fun parseStyle(element: JsonElement?, warnings: MutableList<String>): MelodiaStyle {
        val default = MelodiaStyle.Default
        if (element == null) return default
        val obj = element as? JsonObject ?: run {
            warnings += "style 不是对象，已忽略"
            return default
        }
        val radius = obj["radius"] as? JsonObject
        val font = obj["font"] as? JsonObject
        val border = obj["border"] as? JsonObject
        val fontKey = font?.string("family")
        val fontFamily = when {
            fontKey == null -> ThemeFont.DEFAULT
            else -> ThemeFont.fromKey(fontKey) ?: ThemeFont.DEFAULT.also {
                warnings += if (fontKey.startsWith(SkinPaths.ASSETS_PREFIX)) "暂不支持随包字体文件，已使用默认字体" else "style.font.family 取值无效，已使用默认字体"
            }
        }
        val paneBorder = if (border?.boolean("panes") == true) {
            border.clampedFloat("width", MIN_BORDER_WIDTH, MAX_BORDER_WIDTH, 1f, "style.border.width", warnings).dp
        } else {
            null
        }
        return MelodiaStyle(
            paneRadius = radius?.optionalRadius("pane", MAX_PANE_RADIUS, warnings) ?: default.paneRadius,
            cardRadius = radius?.optionalRadius("card", MAX_CARD_RADIUS, warnings),
            coverRadius = radius?.optionalRadius("cover", MAX_SHAPE_RADIUS, warnings),
            buttonRadius = radius?.optionalRadius("button", MAX_SHAPE_RADIUS, warnings),
            font = fontFamily,
            density = obj.enumValue("density", ThemeDensity::fromKey, default.density, warnings),
            paneBorder = paneBorder,
            progress = obj.enumValue("progress", ThemeProgressStyle::fromKey, default.progress, warnings),
            controls = obj.enumValue("controlStyle", ThemeControlStyle::fromKey, default.controls, warnings)
        )
    }

    // 缺省与无法识别的取值都按跟随封面处理；透出背景图在没有背景图时也按跟随封面处理
    private fun parseCover(element: JsonElement?, warnings: MutableList<String>): SkinCover {
        if (element == null) return SkinCover()
        val obj = element as? JsonObject ?: run {
            warnings += "cover 不是对象，已忽略"
            return SkinCover()
        }
        val rules = LinkedHashMap<CoverGroup, CoverRule>()
        obj.forEach { (key, value) ->
            val group = CoverGroup.fromKey(key)
            if (group == null) {
                warnings += "cover.$key 不是可识别的分组，已忽略"
                return@forEach
            }
            val rule = value as? JsonObject
            val sourceKey = rule?.string("source")
            val source = sourceKey?.let(CoverSource::fromKey)
            if (source == null) {
                warnings += "cover.$key.source 取值无效，按跟随封面处理"
                return@forEach
            }
            rules[group] = when (source) {
                CoverSource.COLOR -> parseCoverColor(rule["color"], "cover.$key.color", warnings)
                    ?: CoverRule.Default.also { warnings += "cover.$key 缺少有效的 color，按跟随封面处理" }
                else -> CoverRule(source)
            }
        }
        return SkinCover(rules)
    }

    // color 可写一个色值两种模式共用，也可写 { "dark": ..., "light": ... }
    private fun parseCoverColor(element: JsonElement?, label: String, warnings: MutableList<String>): CoverRule? {
        fun read(value: JsonElement?): Color? = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::parseColor)
        return when (element) {
            is JsonPrimitive -> read(element)?.let { CoverRule(CoverSource.COLOR, it, it) }
            is JsonObject -> {
                val dark = read(element[SkinMode.DARK.key])
                val light = read(element[SkinMode.LIGHT.key])
                if (dark == null && light == null) return null
                if (dark == null || light == null) warnings += "$label 只写了一种模式，另一种按跟随封面处理"
                CoverRule(CoverSource.COLOR, dark, light)
            }
            else -> null
        }
    }

    private fun parseLayouts(element: JsonElement?, warnings: MutableList<String>): JsonObject? {
        if (element == null) return null
        return element as? JsonObject ?: run {
            warnings += "layouts 不是对象，已忽略"
            null
        }
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? = primitive(key)?.takeIf { !it.isString }?.intOrNull

    private fun JsonObject.boolean(key: String): Boolean? = primitive(key)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonObject.float(key: String): Float? = primitive(key)?.takeIf { !it.isString }?.floatOrNull?.takeIf { it.isFinite() }

    private fun JsonObject.clampedFloat(
        key: String,
        min: Float,
        max: Float,
        fallback: Float,
        label: String,
        warnings: MutableList<String>
    ): Float {
        if (this[key] == null) return fallback
        val value = float(key) ?: run {
            warnings += "$label 不是数字，已使用默认值"
            return fallback
        }
        if (value < min || value > max) warnings += "$label 超出范围 $min~$max，已收到边界"
        return value.coerceIn(min, max)
    }

    private fun JsonObject.optionalRadius(key: String, max: Float, warnings: MutableList<String>): Dp? {
        if (this[key] == null) return null
        return clampedFloat(key, 0f, max, 0f, "style.radius.$key", warnings).dp
    }

    private fun <T> JsonObject.enumValue(
        key: String,
        fromKey: (String) -> T?,
        fallback: T,
        warnings: MutableList<String>
    ): T {
        if (this[key] == null) return fallback
        return string(key)?.let(fromKey) ?: fallback.also { warnings += "style.$key 取值无效，已使用默认值" }
    }
}

object SkinPaths {
    const val MANIFEST = "skin.json"
    const val ASSETS_PREFIX = "assets/"

    private val ASSET_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
    val imageExtensions = setOf("png", "jpg", "jpeg", "webp")
    val fontExtensions = setOf("ttf", "otf")

    // 只允许 assets/ 下一层的白名单文件，杜绝路径穿越
    fun isAsset(path: String): Boolean {
        if (!path.startsWith(ASSETS_PREFIX)) return false
        val name = path.removePrefix(ASSETS_PREFIX)
        if (!ASSET_NAME.matches(name) || name.contains("..")) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in imageExtensions || ext in fontExtensions
    }

    fun isImageAsset(path: String): Boolean =
        isAsset(path) && path.substringAfterLast('.', "").lowercase() in imageExtensions
}
