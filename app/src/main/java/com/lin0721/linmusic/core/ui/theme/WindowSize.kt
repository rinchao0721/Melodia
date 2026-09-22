package com.lin0721.linmusic.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 平板适配断点：仅按可用宽度分两档，横竖屏行为一致（见平板适配设计文档第 4 节）
enum class MelodiaWindowSizeClass {
    Compact,
    Expanded
}

private const val EXPANDED_MIN_WIDTH_DP = 600

@Composable
fun rememberMelodiaWindowSizeClass(): MelodiaWindowSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) {
        if (widthDp >= EXPANDED_MIN_WIDTH_DP) {
            MelodiaWindowSizeClass.Expanded
        } else {
            MelodiaWindowSizeClass.Compact
        }
    }
}

val LocalMelodiaWindowSizeClass = staticCompositionLocalOf { MelodiaWindowSizeClass.Compact }

// 平板常驻播放面板宽度：竖屏 340dp / 横屏 400dp（设计文档 6.3 节建议值，真机验证后可再调）。
// 收起态的迷你播放条与展开态的面板共用同一宽度，两者上下贴齐
private val PlayerPanelWidthPortrait = 340.dp
private val PlayerPanelWidthLandscape = 400.dp

@Composable
fun rememberMelodiaPlayerPanelWidth(): Dp {
    val configuration = LocalConfiguration.current
    return if (configuration.screenWidthDp < configuration.screenHeightDp) {
        PlayerPanelWidthPortrait
    } else {
        PlayerPanelWidthLandscape
    }
}
