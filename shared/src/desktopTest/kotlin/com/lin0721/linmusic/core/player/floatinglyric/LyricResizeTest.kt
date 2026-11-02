package com.lin0721.linmusic.core.player.floatinglyric

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricResizeTest {
    private val start = LyricResizeState(x = 100f, width = 800f, fontSizeSp = 28)
    private val limits = LyricResizeLimits(minWidth = 360f, maxWidth = 1600f, minFontSp = 16, maxFontSp = 72)

    @Test fun rightEdgeChangesOnlyWidth() {
        assertEquals(LyricResizeState(100f, 900f, 28), LyricResize.apply(LyricResizeHandle.RIGHT, start, 100f, limits))
    }

    @Test fun leftEdgeKeepsRightEdgeFixed() {
        val result = LyricResize.apply(LyricResizeHandle.LEFT, start, -200f, limits)
        assertEquals(1000f, result.width)
        assertEquals(-100f, result.x)
        assertEquals(start.x + start.width, result.x + result.width)
    }

    @Test fun leftEdgeStopsAtMinWidth() {
        val result = LyricResize.apply(LyricResizeHandle.LEFT, start, 700f, limits)
        assertEquals(360f, result.width)
        assertEquals(540f, result.x)
    }

    @Test fun cornerScalesFontWithWidth() {
        val result = LyricResize.apply(LyricResizeHandle.CORNER, start, 400f, limits)
        assertEquals(1200f, result.width)
        assertEquals(42, result.fontSizeSp)
    }

    @Test fun cornerClampsFont() {
        val result = LyricResize.apply(LyricResizeHandle.CORNER, start, -440f, limits)
        assertEquals(360f, result.width)
        assertEquals(16, result.fontSizeSp)
    }
}
