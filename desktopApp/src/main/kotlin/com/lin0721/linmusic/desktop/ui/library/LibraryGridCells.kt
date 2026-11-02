package com.lin0721.linmusic.desktop.ui.library

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

// 按最小格宽自适应列数，但不少于 minColumns；侧边栏处在最小宽度时像素取整会差一两像素，避免因此掉列
internal class AdaptiveAtLeast(private val minSize: Dp, private val minColumns: Int) : GridCells {

    init {
        require(minColumns > 0) { "minColumns 必须大于 0" }
    }

    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val available = availableSize.coerceAtLeast(0)
        val adaptive = (available + spacing) / (minSize.roundToPx() + spacing).coerceAtLeast(1)
        val count = maxOf(adaptive, minColumns)
        val content = (available - spacing * (count - 1)).coerceAtLeast(0)
        val base = content / count
        val remainder = content % count
        return List(count) { base + if (it < remainder) 1 else 0 }
    }

    override fun equals(other: Any?): Boolean =
        other is AdaptiveAtLeast && minSize == other.minSize && minColumns == other.minColumns

    override fun hashCode(): Int = 31 * minSize.hashCode() + minColumns
}
