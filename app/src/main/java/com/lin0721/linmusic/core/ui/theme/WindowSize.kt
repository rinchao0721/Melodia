package com.lin0721.linmusic.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

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
