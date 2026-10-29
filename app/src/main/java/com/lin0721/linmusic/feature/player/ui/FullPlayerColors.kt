package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.lin0721.linmusic.core.ui.skin.CoverGroup
import com.lin0721.linmusic.core.ui.skin.coverBase
import com.lin0721.linmusic.core.ui.theme.PlayerBackdropPalette
import com.lin0721.linmusic.core.ui.theme.rememberImmersiveColors
import com.lin0721.linmusic.core.ui.theme.uiTween

// 切歌时背景色平滑过渡，800ms 与封面淡入节奏对齐；textHighlight 随 base 与主题深浅一起变化，不单独设动画
@Composable
fun rememberFullPlayerColors(palette: PlayerBackdropPalette): PlayerBackdropPalette {
    val animatedBase by animateColorAsState(
        targetValue = coverBase(CoverGroup.PLAYER, palette.base) ?: palette.base,
        animationSpec = uiTween(800),
        label = "bg_base"
    )
    return PlayerBackdropPalette(
        base = animatedBase,
        textHighlight = rememberImmersiveColors(animatedBase).inactive
    )
}
