package com.lin0721.linmusic.desktop.ui.miniplayer

import com.lin0721.linmusic.desktop.data.MiniPlayerSize
import com.lin0721.linmusic.desktop.data.MiniPlayerStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniResizeTest {

    private val lyricStart = MiniResizeState(100f, 200f, MiniPlayerStyle.LYRIC.defaultSize)

    @Test
    fun rightAndBottomGrowWithoutMoving() {
        val right = MiniResize.apply(MiniPlayerStyle.LYRIC, MiniResizeHandle.RIGHT, lyricStart, 40f, 0f, 1f)
        assertEquals(MiniResizeState(100f, 200f, MiniPlayerSize(400, 220, 1f)), right)

        val bottom = MiniResize.apply(MiniPlayerStyle.LYRIC, MiniResizeHandle.BOTTOM, lyricStart, 0f, 30f, 1f)
        assertEquals(MiniResizeState(100f, 200f, MiniPlayerSize(360, 250, 1f)), bottom)
    }

    @Test
    fun leftAndTopKeepOppositeEdgeFixed() {
        val left = MiniResize.apply(MiniPlayerStyle.LYRIC, MiniResizeHandle.LEFT, lyricStart, -40f, 0f, 1f)
        assertEquals(MiniResizeState(60f, 200f, MiniPlayerSize(400, 220, 1f)), left)

        val top = MiniResize.apply(MiniPlayerStyle.LYRIC, MiniResizeHandle.TOP, lyricStart, 0f, 20f, 1f)
        assertEquals(MiniResizeState(100f, 220f, MiniPlayerSize(360, 200, 1f)), top)
    }

    @Test
    fun screenDeltaIsConvertedByScale() {
        val scaled = lyricStart.copy(size = lyricStart.size.copy(scale = 1.5f))
        // 屏幕上拖 60，布局尺寸 × 1.5 × 2 = 每布局 dp 占 3 屏幕 dp
        val result = MiniResize.apply(MiniPlayerStyle.LYRIC, MiniResizeHandle.RIGHT, scaled, 60f, 0f, 2f)
        assertEquals(380, result.size.width)
    }

    @Test
    fun sizeStopsAtLowerLimitsOnly() {
        val wide = MiniResize.apply(MiniPlayerStyle.LYRIC, MiniResizeHandle.RIGHT, lyricStart, 1000f, 0f, 1f)
        assertEquals(1360, wide.size.width)

        val big = MiniResize.apply(MiniPlayerStyle.LYRIC, MiniResizeHandle.CORNER, lyricStart, 870f, 870f, 1f)
        assertEquals(4f, big.size.scale, 0.001f)

        val tooNarrow = MiniResize.apply(MiniPlayerStyle.LYRIC, MiniResizeHandle.LEFT, lyricStart, 5000f, 0f, 1f)
        assertEquals(MiniPlayerStyle.LYRIC.minWidth, tooNarrow.size.width)
        assertEquals(100f + (360 - MiniPlayerStyle.LYRIC.minWidth), tooNarrow.x)

        val tooSmall = MiniResize.apply(MiniPlayerStyle.LYRIC, MiniResizeHandle.CORNER, lyricStart, -5000f, -5000f, 1f)
        assertEquals(MiniPlayerSize.MIN_SCALE, tooSmall.size.scale)
    }

    @Test
    fun disallowedHandlesLeaveStateUntouched() {
        val barStart = MiniResizeState(0f, 0f, MiniPlayerStyle.BAR.defaultSize)
        assertEquals(barStart, MiniResize.apply(MiniPlayerStyle.BAR, MiniResizeHandle.BOTTOM, barStart, 0f, 50f, 1f))

        val cardStart = MiniResizeState(0f, 0f, MiniPlayerStyle.CARD.defaultSize)
        assertEquals(cardStart, MiniResize.apply(MiniPlayerStyle.CARD, MiniResizeHandle.RIGHT, cardStart, 50f, 0f, 1f))
    }

    @Test
    fun cornerScalesUniformlyAndKeepsCardSquare() {
        val cardStart = MiniResizeState(0f, 0f, MiniPlayerStyle.CARD.defaultSize)
        // 对角线长 480，横竖共拖 240 → 放大 1.5 倍
        val result = MiniResize.apply(MiniPlayerStyle.CARD, MiniResizeHandle.CORNER, cardStart, 120f, 120f, 1f)
        assertEquals(MiniPlayerSize(240, 240, 1.5f), result.size)
        assertEquals(0f, result.x)
    }
}
