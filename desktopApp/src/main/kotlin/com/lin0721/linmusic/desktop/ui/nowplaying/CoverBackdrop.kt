package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.lin0721.linmusic.core.ui.skin.CoverGroup
import com.lin0721.linmusic.core.ui.skin.coverBase
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.palette.FallbackCoverPalette
import com.lin0721.linmusic.desktop.ui.palette.extractCoverPaletteFromUrl

private const val BASE_TRANSITION_MS = 800

// 封面主色，取色完成前保持上一首的颜色，切歌时平滑过渡
@Composable
fun rememberCoverBase(url: String?): Color {
    var base by remember { mutableStateOf(FallbackCoverPalette.base) }
    LaunchedEffect(url) {
        base = if (url.isNullOrBlank()) FallbackCoverPalette.base else extractCoverPaletteFromUrl(url).base
    }
    val target = coverBase(CoverGroup.PLAYER, base) ?: base
    val animated by animateColorAsState(target, uiTween(BASE_TRANSITION_MS), label = "coverBase")
    return animated
}
