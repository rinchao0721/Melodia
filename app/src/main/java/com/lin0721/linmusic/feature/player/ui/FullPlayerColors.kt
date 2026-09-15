package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lin0721.linmusic.core.ui.theme.PlayerBackdropPalette
import com.lin0721.linmusic.core.ui.theme.saturate

// 切歌时背景色平滑过渡，800ms 与封面淡入节奏对齐；textHighlight 随 base 一起变化，不单独设动画
@Composable
fun rememberFullPlayerColors(palette: PlayerBackdropPalette): PlayerBackdropPalette {
    val animatedBase by animateColorAsState(
        targetValue = palette.base,
        animationSpec = tween(800),
        label = "bg_base"
    )
    // textHighlight 是"未唱到歌词"文字色，从提过饱和度的 base 去混白，
    // 不然原色饱和度低的话，混完白就发灰，跟背景的彩度对不上
    val vividBase = remember(animatedBase) { animatedBase.saturate(0.6f) }
    return PlayerBackdropPalette(
        swatches = palette.swatches,
        base = animatedBase,
        textHighlight = lerp(start = vividBase, stop = Color.White, fraction = 0.85f)
    )
}
