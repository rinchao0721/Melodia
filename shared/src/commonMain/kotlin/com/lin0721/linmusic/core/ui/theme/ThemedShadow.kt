package com.lin0721.linmusic.core.ui.theme

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 按主题取阴影色：深色沿用默认纯黑，浅色改为低透明度柔影
@Composable
fun Modifier.themedShadow(elevation: Dp, shape: Shape = RectangleShape, clip: Boolean = elevation > 0.dp): Modifier {
    val palette = LocalMelodiaPalette.current
    return shadow(elevation, shape, clip, ambientColor = palette.shadowAmbient, spotColor = palette.shadowSpot)
}

// 浮层轮廓：浅色下补一圈细线托住白底浮层，深色靠底色明暗区分不加线；皮肤要求面板描边时两种模式都按皮肤线宽画
@Composable
fun Modifier.floatingOutline(shape: Shape): Modifier {
    val palette = LocalMelodiaPalette.current
    val skinBorder = LocalMelodiaStyle.current.paneBorder
    return when {
        skinBorder != null -> border(skinBorder, palette.outline, shape)
        palette.isDark -> this
        else -> border(1.dp, palette.outline, shape)
    }
}
