package com.lin0721.linmusic.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

// 双端共用的界面语义色。层级：画布 windowBackground → 面板 pane → 面板内卡片 cardSurface → 浮层 popupSurface；
// surface / surfaceLight 是控件底，fieldOnCanvas 是直接放在画布上的输入框与圆形按钮底。
// hover / pressed / selected / fill / divider / outline 是半透明状态层，叠在任意底色上使用；
// 浅色靠 outline 细线与 shadowAmbient / shadowSpot 阴影分层，深色阴影保持默认纯黑
@Immutable
data class MelodiaPalette(
    val isDark: Boolean,
    val windowBackground: Color,
    val pane: Color,
    val surface: Color,
    val cardSurface: Color,
    val popupSurface: Color,
    val surfaceLight: Color,
    val fieldOnCanvas: Color,
    val fieldOnCanvasHover: Color,
    val coverPlaceholder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val inverseText: Color,
    val overlay: Color,
    val hover: Color,
    val pressed: Color,
    val selected: Color,
    val fill: Color,
    val divider: Color,
    val outline: Color,
    val scrim: Color,
    val shadowAmbient: Color,
    val shadowSpot: Color,
    val success: Color,
    val vip: Color,
    val error: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val accentContainer: Color,
    val backdrop: PaneBackdrop? = null
) {
    // 铺底分三类：画布、面板外壳各铺一次，页面内部叠在外壳上；有皮肤背景图时画布与页面内部改为同色全透明，
    // 渐变以它们收尾时只淡出不发灰；需要挡住内容的吸顶层改用 paneBackdrop
    val canvasFill: Color get() = if (backdrop == null) windowBackground else windowBackground.copy(alpha = 0f)
    val shellFill: Color get() = backdrop?.let { pane.copy(alpha = it.paneOpacity) } ?: pane
    val pageFill: Color get() = if (backdrop == null) pane else pane.copy(alpha = 0f)

    fun withAccent(color: Color): MelodiaPalette = copy(
        accent = color,
        onAccent = contentColorOn(color),
        // 深色保持主色原值作文字，避免改变现有深色观感
        accentText = if (isDark) color else accentTextOn(color, pane),
        accentContainer = color.copy(alpha = if (isDark) DARK_ACCENT_CONTAINER_ALPHA else LIGHT_ACCENT_CONTAINER_ALPHA)
    )

    // 不透明的悬停色，供需要实色的场景（动画插值、拖拽中的行）使用
    fun hoverOn(background: Color): Color = hover.compositeOver(background)
}

// 皮肤背景图透出参数：dim 为画布遮罩不透明度，paneOpacity 为面板外壳不透明度，playerDim 为播放区直接压在背景图上的遮罩
@Immutable
data class PaneBackdrop(val dim: Float, val paneOpacity: Float, val playerDim: Float = dim)

private const val DARK_ACCENT_CONTAINER_ALPHA = 0.16f
private const val LIGHT_ACCENT_CONTAINER_ALPHA = 0.12f

object MelodiaPalettes {
    // 状态层透明度取自迁移前深色界面的实际用法，迁移后深色像素保持一致
    val dark = MelodiaPalette(
        isDark = true,
        windowBackground = Color(0xFF000000),
        pane = Color(0xFF121212),
        surface = Color(0xFF2A2A2A),
        cardSurface = Color(0xFF1F1F1F),
        popupSurface = Color(0xFF282828),
        surfaceLight = Color(0xFF3E3E3E),
        fieldOnCanvas = Color(0xFF1F1F1F),
        fieldOnCanvasHover = Color(0xFF2A2A2A),
        coverPlaceholder = Color(0xFF2C2C2C),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB3B3B3),
        textTertiary = Color(0xFF737373),
        textDisabled = Color(0xFF595959),
        inverseText = Color.Black,
        overlay = Color.White,
        hover = Color.White.copy(alpha = 0.09f),
        pressed = Color.White.copy(alpha = 0.16f),
        selected = Color.White.copy(alpha = 0.12f),
        fill = Color.White.copy(alpha = 0.08f),
        divider = Color.White.copy(alpha = 0.08f),
        outline = Color.White.copy(alpha = 0.12f),
        scrim = Color.Black.copy(alpha = 0.6f),
        shadowAmbient = Color.Black,
        shadowSpot = Color.Black,
        success = Color(0xFF10B981),
        vip = Color(0xFFD4A017),
        error = Color(0xFFFF6B6B),
        accent = ThemeAccents.dark.first().color,
        onAccent = Color.White,
        accentText = ThemeAccents.dark.first().color,
        accentContainer = ThemeAccents.dark.first().color.copy(alpha = DARK_ACCENT_CONTAINER_ALPHA)
    )

    val light = MelodiaPalette(
        isDark = false,
        windowBackground = Color(0xFFECECEC),
        pane = Color(0xFFFFFFFF),
        surface = Color(0xFFF0F0F0),
        cardSurface = Color(0xFFF6F6F6),
        popupSurface = Color(0xFFFFFFFF),
        surfaceLight = Color(0xFFE3E3E3),
        fieldOnCanvas = Color(0xFFFFFFFF),
        fieldOnCanvasHover = Color(0xFFF7F7F7),
        coverPlaceholder = Color(0xFFE6E6E6),
        textPrimary = Color(0xFF121212),
        textSecondary = Color(0xFF5E5E5E),
        textTertiary = Color(0xFF757575),
        textDisabled = Color(0xFFA7A7A7),
        inverseText = Color.White,
        overlay = Color.Black,
        hover = Color.Black.copy(alpha = 0.04f),
        pressed = Color.Black.copy(alpha = 0.1f),
        selected = Color.Black.copy(alpha = 0.07f),
        fill = Color.Black.copy(alpha = 0.04f),
        divider = Color.Black.copy(alpha = 0.08f),
        outline = Color.Black.copy(alpha = 0.1f),
        scrim = Color.Black.copy(alpha = 0.32f),
        shadowAmbient = Color.Black.copy(alpha = 0.04f),
        shadowSpot = Color.Black.copy(alpha = 0.14f),
        success = Color(0xFF047857),
        vip = Color(0xFFA16207),
        error = Color(0xFFC62828),
        accent = ThemeAccents.light.first().color,
        onAccent = Color.White,
        accentText = ThemeAccents.light.first().color,
        accentContainer = ThemeAccents.light.first().color.copy(alpha = LIGHT_ACCENT_CONTAINER_ALPHA)
    )

    fun of(isDark: Boolean): MelodiaPalette = if (isDark) dark else light
}

val LocalMelodiaPalette = staticCompositionLocalOf { MelodiaPalettes.dark }

data class ThemeAccent(val id: String, val label: String, val color: Color)

object ThemeAccents {
    const val DEFAULT_ID = "netease_red"
    const val CUSTOM_ID = "custom"
    // 仅 Android 12+ 可选，其他平台按默认处理
    const val DYNAMIC_ID = "dynamic"

    val dark = listOf(
        ThemeAccent(DEFAULT_ID, "网易红", Color(0xFFC20C0C)),
        ThemeAccent("coral", "珊瑚橙", Color(0xFFF26B3A)),
        ThemeAccent("amber", "琥珀", Color(0xFFE0A100)),
        ThemeAccent("green", "翠绿", Color(0xFF1DB954)),
        ThemeAccent("sky", "天蓝", Color(0xFF3D8BFF)),
        ThemeAccent("violet", "紫罗兰", Color(0xFF9B6BFF))
    )

    // 白字与白底对比度均不低于 4.5
    val light = listOf(
        ThemeAccent(DEFAULT_ID, "网易红", Color(0xFFD3261B)),
        ThemeAccent("orange", "橙", Color(0xFFC2410C)),
        ThemeAccent("green", "绿", Color(0xFF18803A)),
        ThemeAccent("blue", "蓝", Color(0xFF1767CC)),
        ThemeAccent("violet", "紫", Color(0xFF6E45D9)),
        ThemeAccent("rose", "玫红", Color(0xFFC9245E))
    )

    // 浅色预设改名前保存的 id
    private val legacyLightIds = mapOf("forest" to "green", "lake" to "blue", "indigo" to "violet")

    fun presets(isDark: Boolean): List<ThemeAccent> = if (isDark) dark else light

    fun normalizeId(accentId: String, isDark: Boolean): String =
        if (isDark) accentId else legacyLightIds[accentId] ?: accentId

    // dynamicColor 为平台提供的系统动态色，不支持时传 null
    fun resolve(accentId: String, customHex: String?, isDark: Boolean, dynamicColor: Color? = null): Color {
        val presets = presets(isDark)
        val fallback = presets.first().color
        return when (val id = normalizeId(accentId, isDark)) {
            CUSTOM_ID -> customHex?.let(::parseRgbHex) ?: fallback
            DYNAMIC_ID -> dynamicColor ?: fallback
            else -> presets.firstOrNull { it.id == id }?.color ?: fallback
        }
    }
}
