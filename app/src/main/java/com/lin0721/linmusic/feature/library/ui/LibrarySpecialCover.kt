package com.lin0721.linmusic.feature.library.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lin0721.linmusic.core.ui.theme.LibraryLikedCoverDark
import com.lin0721.linmusic.core.ui.theme.LibraryLikedCoverLight
import com.lin0721.linmusic.core.ui.theme.LibraryRankCoverDark
import com.lin0721.linmusic.core.ui.theme.LibraryRankCoverLight
import com.lin0721.linmusic.core.ui.theme.LikedCoverColors
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.RadiusCompact
import com.lin0721.linmusic.core.ui.theme.RankCoverColors

enum class SpecialCoverKind { Liked, Rank }

// 音乐库特殊歌单的几何拼贴封面，图形按封面边长比例绘制，列表、网格、操作面板共用
@Composable
fun LibrarySpecialCover(kind: SpecialCoverKind, modifier: Modifier = Modifier) {
    val isDark = MelodiaColors.IsDark
    Box(
        modifier = modifier.clip(RoundedCornerShape(RadiusCompact)),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            SpecialCoverKind.Rank -> {
                val colors = if (isDark) LibraryRankCoverDark else LibraryRankCoverLight
                Canvas(Modifier.matchParentSize()) { drawRankCover(colors) }
            }
            SpecialCoverKind.Liked -> {
                val colors = if (isDark) LibraryLikedCoverDark else LibraryLikedCoverLight
                Canvas(Modifier.matchParentSize()) { drawLikedCover(colors) }
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize(LIKED_HEART_FRACTION)
                )
            }
        }
    }
}

// 柱子从底边长出，顶部圆角、底部直角
private fun DrawScope.drawRankCover(colors: RankCoverColors) {
    drawRect(colors.background)
    val side = size.minDimension
    val barWidth = side * RANK_BAR_WIDTH
    val radius = CornerRadius(side * RANK_BAR_RADIUS)
    colors.bars.take(RANK_BAR_LEFTS.size).forEachIndexed { index, color ->
        val left = side * RANK_BAR_LEFTS[index]
        val height = side * RANK_BAR_HEIGHTS[index]
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = left,
                    top = size.height - height,
                    right = left + barWidth,
                    bottom = size.height,
                    topLeftCornerRadius = radius,
                    topRightCornerRadius = radius
                )
            )
        }
        drawPath(path, color)
    }
    drawCircle(colors.circle, radius = side * RANK_CIRCLE_RADIUS, center = Offset(size.width - side * RANK_CIRCLE_INSET, side * RANK_CIRCLE_INSET))
}

private fun DrawScope.drawLikedCover(colors: LikedCoverColors) {
    drawRect(colors.background)
    val side = size.minDimension
    drawCircle(colors.topCircle, radius = side * LIKED_TOP_RADIUS, center = Offset(side * LIKED_TOP_CENTER, side * LIKED_TOP_CENTER))
    drawCircle(
        colors.bottomCircle,
        radius = side * LIKED_BOTTOM_RADIUS,
        center = Offset(size.width - side * LIKED_BOTTOM_INSET, size.height - side * LIKED_BOTTOM_INSET)
    )
}

// 以下比例按 56 边长的设计稿换算
private const val RANK_BAR_WIDTH = 0.18f
private const val RANK_BAR_RADIUS = 0.054f
private val RANK_BAR_LEFTS = floatArrayOf(0.107f, 0.357f, 0.607f)
private val RANK_BAR_HEIGHTS = floatArrayOf(0.39f, 0.64f, 0.5f)
private const val RANK_CIRCLE_RADIUS = 0.232f
private const val RANK_CIRCLE_INSET = 0.054f
private const val LIKED_TOP_RADIUS = 0.304f
private const val LIKED_TOP_CENTER = 0.16f
private const val LIKED_BOTTOM_RADIUS = 0.268f
private const val LIKED_BOTTOM_INSET = 0.16f
private const val LIKED_HEART_FRACTION = 0.42f
