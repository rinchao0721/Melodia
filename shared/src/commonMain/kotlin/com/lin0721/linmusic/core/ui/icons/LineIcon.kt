package com.lin0721.linmusic.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// 24 格圆头圆角线性图标构建器，默认 2px 描边
fun lineIcon(name: String, vararg paths: String): ImageVector =
    lineIcon(name = name, strokeWidth = 2f, *paths)

fun lineIcon(name: String, strokeWidth: Float = 2f, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        paths.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

// 实心激活态构建器：填充外描 2px 保持与线性版同尺寸，奇偶规则挖洞
fun solidIcon(name: String, fillPaths: List<String>, linePaths: List<String> = emptyList()): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        fillPaths.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        linePaths.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

// 纯填充构建器：无描边，镂空细节不被描边吃掉
fun fillIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        paths.forEach { data ->
            addPath(pathData = addPathNodes(data), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
        }
    }.build()
