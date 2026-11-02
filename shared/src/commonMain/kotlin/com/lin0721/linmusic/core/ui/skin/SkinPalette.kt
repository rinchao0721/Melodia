package com.lin0721.linmusic.core.ui.skin

import androidx.compose.ui.graphics.Color
import com.lin0721.linmusic.core.preferences.ThemeSettings
import com.lin0721.linmusic.core.ui.theme.MelodiaPalette
import com.lin0721.linmusic.core.ui.theme.MelodiaPalettes
import com.lin0721.linmusic.core.ui.theme.PaneBackdrop
import com.lin0721.linmusic.core.ui.theme.ThemeAccents

// 皮肤文件 colors 下的字段名；key 与 MelodiaPalette 属性同名，改名会让已发布的皮肤失效
enum class PaletteToken(val key: String, val read: (MelodiaPalette) -> Color) {
    WINDOW_BACKGROUND("windowBackground", { it.windowBackground }),
    PANE("pane", { it.pane }),
    SURFACE("surface", { it.surface }),
    CARD_SURFACE("cardSurface", { it.cardSurface }),
    POPUP_SURFACE("popupSurface", { it.popupSurface }),
    SURFACE_LIGHT("surfaceLight", { it.surfaceLight }),
    FIELD_ON_CANVAS("fieldOnCanvas", { it.fieldOnCanvas }),
    FIELD_ON_CANVAS_HOVER("fieldOnCanvasHover", { it.fieldOnCanvasHover }),
    COVER_PLACEHOLDER("coverPlaceholder", { it.coverPlaceholder }),
    TEXT_PRIMARY("textPrimary", { it.textPrimary }),
    TEXT_SECONDARY("textSecondary", { it.textSecondary }),
    TEXT_TERTIARY("textTertiary", { it.textTertiary }),
    TEXT_DISABLED("textDisabled", { it.textDisabled }),
    INVERSE_TEXT("inverseText", { it.inverseText }),
    OVERLAY("overlay", { it.overlay }),
    HOVER("hover", { it.hover }),
    PRESSED("pressed", { it.pressed }),
    SELECTED("selected", { it.selected }),
    FILL("fill", { it.fill }),
    DIVIDER("divider", { it.divider }),
    OUTLINE("outline", { it.outline }),
    SCRIM("scrim", { it.scrim }),
    SHADOW_AMBIENT("shadowAmbient", { it.shadowAmbient }),
    SHADOW_SPOT("shadowSpot", { it.shadowSpot }),
    SUCCESS("success", { it.success }),
    VIP("vip", { it.vip }),
    ERROR("error", { it.error }),
    ACCENT("accent", { it.accent }),
    ON_ACCENT("onAccent", { it.onAccent }),
    ACCENT_TEXT("accentText", { it.accentText }),
    ACCENT_CONTAINER("accentContainer", { it.accentContainer });

    companion object {
        fun fromKey(key: String): PaletteToken? = entries.firstOrNull { it.key == key }

        // 由主色推导的项：只有主色也取自皮肤时，皮肤写的值才生效
        val accentDerived = setOf(ON_ACCENT, ACCENT_TEXT, ACCENT_CONTAINER)
    }
}

fun MelodiaPalette.withOverrides(overrides: Map<PaletteToken, Color>): MelodiaPalette {
    if (overrides.isEmpty()) return this
    fun pick(token: PaletteToken) = overrides[token] ?: token.read(this)
    return copy(
        windowBackground = pick(PaletteToken.WINDOW_BACKGROUND),
        pane = pick(PaletteToken.PANE),
        surface = pick(PaletteToken.SURFACE),
        cardSurface = pick(PaletteToken.CARD_SURFACE),
        popupSurface = pick(PaletteToken.POPUP_SURFACE),
        surfaceLight = pick(PaletteToken.SURFACE_LIGHT),
        fieldOnCanvas = pick(PaletteToken.FIELD_ON_CANVAS),
        fieldOnCanvasHover = pick(PaletteToken.FIELD_ON_CANVAS_HOVER),
        coverPlaceholder = pick(PaletteToken.COVER_PLACEHOLDER),
        textPrimary = pick(PaletteToken.TEXT_PRIMARY),
        textSecondary = pick(PaletteToken.TEXT_SECONDARY),
        textTertiary = pick(PaletteToken.TEXT_TERTIARY),
        textDisabled = pick(PaletteToken.TEXT_DISABLED),
        inverseText = pick(PaletteToken.INVERSE_TEXT),
        overlay = pick(PaletteToken.OVERLAY),
        hover = pick(PaletteToken.HOVER),
        pressed = pick(PaletteToken.PRESSED),
        selected = pick(PaletteToken.SELECTED),
        fill = pick(PaletteToken.FILL),
        divider = pick(PaletteToken.DIVIDER),
        outline = pick(PaletteToken.OUTLINE),
        scrim = pick(PaletteToken.SCRIM),
        shadowAmbient = pick(PaletteToken.SHADOW_AMBIENT),
        shadowSpot = pick(PaletteToken.SHADOW_SPOT),
        success = pick(PaletteToken.SUCCESS),
        vip = pick(PaletteToken.VIP),
        error = pick(PaletteToken.ERROR),
        accent = pick(PaletteToken.ACCENT),
        onAccent = pick(PaletteToken.ON_ACCENT),
        accentText = pick(PaletteToken.ACCENT_TEXT),
        accentContainer = pick(PaletteToken.ACCENT_CONTAINER)
    )
}

fun MelodiaPalette.toTokenMap(): Map<PaletteToken, Color> = PaletteToken.entries.associateWith { it.read(this) }

// 用户选了自定义色或系统动态色时优先于皮肤主色，否则皮肤写了主色就用皮肤的
fun resolveSkinPalette(
    isDark: Boolean,
    accentId: String,
    customHex: String?,
    skin: Skin?,
    dynamicColor: Color? = null
): MelodiaPalette {
    val overrides = skin?.colors(isDark).orEmpty()
    val base = MelodiaPalettes.of(isDark).withOverrides(overrides - PaletteToken.accentDerived - PaletteToken.ACCENT)
    val normalizedId = ThemeAccents.normalizeId(accentId, isDark)
    val userPicked = normalizedId == ThemeAccents.CUSTOM_ID || normalizedId == ThemeAccents.DYNAMIC_ID
    val skinAccent = overrides[PaletteToken.ACCENT]
    if (skinAccent != null && !userPicked) {
        return base.withAccent(skinAccent).withOverrides(overrides.filterKeys { it in PaletteToken.accentDerived })
    }
    return base.withAccent(ThemeAccents.resolve(accentId, customHex, isDark, dynamicColor))
}

// 背景图解码成功后才调用，内部铺底随之改透明
fun MelodiaPalette.withBackdrop(background: SkinBackground?): MelodiaPalette =
    copy(backdrop = background?.let { PaneBackdrop(dim = it.dim, paneOpacity = it.paneOpacity, playerDim = it.effectivePlayerDim) })

// 切换皮肤后画面是否会变：色板或背景图有变化才走切换动画，否则扩散动画等不到新主题
fun skinSwitchChangesTheme(settings: ThemeSettings, systemDark: Boolean, current: Skin?, target: Skin?): Boolean {
    fun paletteOf(skin: Skin?): MelodiaPalette {
        val isDark = settings.mode.isDark(systemDark)
        return resolveSkinPalette(isDark, settings.accentId(isDark), settings.customHex(isDark), skin)
    }
    if (current?.id == target?.id) return false
    val hasBackground = current?.background != null || target?.background != null
    return hasBackground || paletteOf(current) != paletteOf(target)
}
