package com.lin0721.linmusic.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.lin0721.linmusic.core.ui.skin.CoverGroup
import com.lin0721.linmusic.core.ui.skin.LocalCoverPolicy
import com.lin0721.linmusic.core.ui.skin.LocalImmersiveBasePalette

private val NavPillSelectedDark = Color(0xFF383A4A)
private val FilterPillInactiveDark = Color(0xFF332B2B)

// 当前主题的界面语义色，语义见 MelodiaPalette；仅能在组合作用域内读取
object MelodiaColors {
    val WindowBackground: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.windowBackground
    val Background: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.pane
    val CanvasFill: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.canvasFill
    val ShellFill: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.shellFill
    val PageFill: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.pageFill
    val HasBackdrop: Boolean @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.backdrop != null

    // 封面色卡片上的文字与图标；卡片透出皮肤背景图时改用色板文字色，Inverse 为其上白底按钮里的图标色
    val OnCoverCard: Color
        @Composable @ReadOnlyComposable get() = if (LocalCoverPolicy.current.isBackdrop(CoverGroup.CARDS)) TextPrimary else Color.White
    val OnCoverCardInverse: Color
        @Composable @ReadOnlyComposable get() = if (LocalCoverPolicy.current.isBackdrop(CoverGroup.CARDS)) Background else Color.Black

    // 页面铺底按比例降低不透明度；有皮肤背景图时始终全透明
    @Composable
    @ReadOnlyComposable
    fun pageFill(alpha: Float): Color = PageFill.let { it.copy(alpha = it.alpha * alpha) }
    val CardSurface: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.cardSurface
    val PopupSurface: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.popupSurface
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.surface
    val SurfaceLight: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.surfaceLight
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.textSecondary
    val TextTertiary: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.textTertiary
    val TextDisabled: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.textDisabled
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.accent
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.onAccent
    val AccentText: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.accentText
    val AccentContainer: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.accentContainer
    val Overlay: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.overlay
    val Hover: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.hover
    val Pressed: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.pressed
    val Selected: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.selected
    val Fill: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.fill
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.divider
    val Outline: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.outline
    val Scrim: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.scrim
    val ShadowSpot: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.shadowSpot
    val ShadowAmbient: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.shadowAmbient

    // 播放区（封面色或沉浸深色底）上的文字与图标，跟随皮肤的深色配色
    val OnPlayer: Color @Composable @ReadOnlyComposable get() = LocalImmersiveBasePalette.current.textPrimary
    val Success: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.success
    val Vip: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.vip
    val Error: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.error
    val InverseText: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.inverseText
    val CoverPlaceholder: Color @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.coverPlaceholder
    val IsDark: Boolean @Composable @ReadOnlyComposable get() = LocalMelodiaPalette.current.isDark

    // 底栏底色：深色与页面同色，浅色落在画布色上，与白色页面区分
    val NavigationBar: Color @Composable @ReadOnlyComposable get() = if (IsDark) Background else WindowBackground

    // 底栏选中项的药丸指示器底色
    val NavPillSelected: Color @Composable @ReadOnlyComposable get() = if (IsDark) NavPillSelectedDark else Selected

    // 未选中药丸、标签上的文字与图标：深色沿用原浅灰，浅色改主文字色
    val PillContent: Color @Composable @ReadOnlyComposable get() = if (IsDark) Color.LightGray else TextPrimary

    // 次要提示文字：深色沿用原中灰，浅色改三级文字色
    val HintText: Color @Composable @ReadOnlyComposable get() = if (IsDark) Color.Gray else TextTertiary

    // 筛选药丸未选中态底色，深色取 overlay 10% 叠在顶栏渐变上的等效不透明色
    val FilterPillInactive: Color @Composable @ReadOnlyComposable get() = if (IsDark) FilterPillInactiveDark else Surface
}
