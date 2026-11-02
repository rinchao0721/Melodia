package com.lin0721.linmusic.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// MaterialTheme 的圆角梯度
val MelodiaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// BottomSheet 顶部圆角
val BottomSheetShape: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

// BottomSheet 拖拽把手的胶囊圆角
val DragHandleShape: Shape = RoundedCornerShape(2.dp)

// 封面图/缩略图圆角；皮肤 style.radius.cover 可覆盖
val RadiusCompact: Dp @Composable @ReadOnlyComposable get() = styledCoverRadius(6.dp)

// 播放详情页信息卡片圆角（歌词/评论/歌曲详情/类似艺人/关于艺人/艺人专辑）；皮肤 style.radius.card 可覆盖
val InfoCardRadius: Dp @Composable @ReadOnlyComposable get() = styledCardRadius(16.dp)

// 胶囊型按钮/Chip 圆角
val PillRadius = 20.dp
val PillRadiusLarge = 25.dp
