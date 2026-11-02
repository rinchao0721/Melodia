package com.lin0721.linmusic.desktop.ui.library

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGridCellsTest {

    private fun AdaptiveAtLeast.sizes(density: Float, available: Int, spacing: Int): List<Int> =
        with(this) { Density(density).calculateCrossAxisCellSizes(available, spacing) }

    @Test
    fun keepsMinimumColumnsWhenRoundingLosesAPixel() {
        // 125% 缩放下 280dp 侧边栏：可用 324px，两列需要 325px
        val sizes = AdaptiveAtLeast(128.dp, 2).sizes(1.25f, 324, 5)
        assertEquals(2, sizes.size)
        assertEquals(324 - 5, sizes.sum())
    }

    @Test
    fun smallGridKeepsThreeColumnsAtMinimumWidth() {
        val sizes = AdaptiveAtLeast(88.dp, 3).sizes(1.25f, 340, 5)
        assertEquals(3, sizes.size)
        assertEquals(340 - 10, sizes.sum())
    }

    @Test
    fun widensToMoreColumnsLikeAdaptive() {
        assertEquals(5, AdaptiveAtLeast(190.dp, 2).sizes(1f, 1000, 4).size)
    }

    @Test
    fun handlesZeroWidth() {
        assertEquals(listOf(0, 0), AdaptiveAtLeast(128.dp, 2).sizes(1f, 0, 4))
    }
}
