package com.lin0721.linmusic.desktop.ui.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopThemeStyleTest {

    @Test
    fun unknownNameFallsBackToClassic() {
        assertEquals(DesktopThemeStyle.CLASSIC, DesktopThemeStyle.fromName(null))
        assertEquals(DesktopThemeStyle.CLASSIC, DesktopThemeStyle.fromName("NOT_A_THEME"))
        assertEquals(DesktopThemeStyle.TERRACOTTA, DesktopThemeStyle.fromName("TERRACOTTA"))
    }

    // 各主题都要保持 窗口 < 大背景 < 卡片 < 菜单 < 控件 < 浅控件 的明度层级
    @Test
    fun everyPaletteKeepsSurfaceLayering() {
        DesktopThemeStyle.entries.forEach { style ->
            val p = style.palette
            val layers = listOf(p.windowBackground, p.pane, p.cardSurface, p.popupSurface, p.surface, p.surfaceLight)
            layers.zipWithNext().forEach { (lower, upper) ->
                assertTrue("${style.name} 层级倒置", lower.luminance() < upper.luminance())
            }
        }
    }

    @Test
    fun colorTokensFollowSelectedPalette() {
        val original = DesktopColors.palette
        try {
            DesktopColors.palette = DesktopThemeStyle.TERRACOTTA.palette
            assertEquals(DesktopThemeStyle.TERRACOTTA.palette.accent, DesktopColors.Accent)
            assertEquals(DesktopColors.PopupSurface, DesktopColors.PaneHover)
        } finally {
            DesktopColors.palette = original
        }
    }
}
