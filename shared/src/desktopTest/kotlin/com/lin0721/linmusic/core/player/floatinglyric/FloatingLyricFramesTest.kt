package com.lin0721.linmusic.core.player.floatinglyric

import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricProgressTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingLyricFramesTest {
    private val track = NowPlaying("1", "晴天", "周杰伦", null)
    private val lines = listOf(
        LyricLine(1000, 1000, "第一句", translation = "first", roma = "dai ichi"),
        LyricLine(2000, 1000, " "),
        LyricLine(3000, 1000, "第三句", translation = "third")
    )

    @Test fun idleWithoutTrack() {
        val frame = FloatingLyricFrames.of(lines, 0, null, FloatingLyricSecondary.TRANSLATION)
        assertNull(frame.primary)
        assertEquals(FloatingLyricFrames.IDLE_TEXT, frame.fallback)
    }

    @Test fun noLyricsFallsBackToTitle() {
        val frame = FloatingLyricFrames.of(emptyList(), -1, track, FloatingLyricSecondary.NEXT)
        assertNull(frame.primary)
        assertNull(frame.secondary)
        assertEquals("晴天 - 周杰伦", frame.fallback)
    }

    @Test fun secondaryModes() {
        assertEquals("first", FloatingLyricFrames.of(lines, 0, track, FloatingLyricSecondary.TRANSLATION).secondary)
        assertEquals("dai ichi", FloatingLyricFrames.of(lines, 0, track, FloatingLyricSecondary.ROMA).secondary)
        assertNull(FloatingLyricFrames.of(lines, 0, track, FloatingLyricSecondary.NONE).secondary)
        // 下一句跳过空行
        assertEquals("第三句", FloatingLyricFrames.of(lines, 0, track, FloatingLyricSecondary.NEXT).secondary)
        assertNull(FloatingLyricFrames.of(lines, 2, track, FloatingLyricSecondary.NEXT).secondary)
    }

    @Test fun introShowsTitleWithFirstLineAsNext() {
        val frame = FloatingLyricFrames.of(lines, -1, track, FloatingLyricSecondary.NEXT)
        assertNull(frame.primary)
        assertEquals("第一句", frame.secondary)
    }

    @Test fun blankLineFallsBackToTitle() {
        val frame = FloatingLyricFrames.of(lines, 1, track, FloatingLyricSecondary.TRANSLATION)
        assertNull(frame.primary)
        assertNull(frame.secondary)
    }

    @Test fun keyParsingFallsBackToDefaults() {
        assertEquals(FloatingLyricSecondary.TRANSLATION, FloatingLyricSecondary.fromKey("bogus"))
        assertEquals(FloatingLyricSecondary.ROMA, FloatingLyricSecondary.fromKey("roma"))
        assertEquals(FloatingLyricAlign.CENTER, FloatingLyricAlign.fromKey(null))
        assertEquals(FloatingLyricAlign.END, FloatingLyricAlign.fromKey("end"))
    }

    @Test fun parseArgb() {
        assertEquals(0xFFFFFFFFL, FloatingLyricPalette.parseArgb("#FFFFFF"))
        assertEquals(0x99FFFFFFL, FloatingLyricPalette.parseArgb("#99FFFFFF"))
        assertNull(FloatingLyricPalette.parseArgb("#FFF"))
        assertNull(FloatingLyricPalette.parseArgb("red"))
    }

    @Test fun shadowArgbAppliesStrengthToColor() {
        assertNull(FloatingLyricStyle(shadow = false).shadowArgb())
        assertEquals(0xCC000000L, FloatingLyricStyle(shadowAlpha = 80, shadowColor = "#000000").shadowArgb())
        assertEquals(0xFFFFFFFFL, FloatingLyricStyle(shadowAlpha = 100, shadowColor = "#FFFFFF").shadowArgb())
        // 颜色自带的透明度被强度覆盖，非法颜色回退黑色
        assertEquals(0x33E03E3EL, FloatingLyricStyle(shadowAlpha = 20, shadowColor = "#00E03E3E").shadowArgb())
        assertEquals(0x00000000L, FloatingLyricStyle(shadowAlpha = 0, shadowColor = "bad").shadowArgb())
    }

    @Test fun trackerTreatsLargeJumpAsSeek() {
        val tracker = LyricProgressTracker()
        assertEquals(0, tracker.update(lines, 1500).primaryIndex)
        // 直接跳到第三句，不保留第一句的暂留状态
        assertEquals(2, tracker.update(lines, 3500).primaryIndex)
        tracker.reset()
        assertEquals(-1, tracker.state.primaryIndex)
    }
}
