package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricRenderClockTest {
    @Test fun discreteSamplesStillAdvanceBetweenPollsWithoutFlashingBack() {
        val clock = LyricRenderClock()
        var previous = clock.sample(1000, 0, true)
        for (time in 16L..1000L step 16) {
            val raw = 1000 + time / 50 * 50
            val next = clock.sample(raw, time, true)
            assertTrue("frame at $time should advance smoothly", next - previous in 14L..18L)
            previous = next
        }
        assertTrue(absDifference(previous, 1992) < 50)
    }

    @Test fun smallBackwardCorrectionDoesNotReverseTheHighlight() {
        val clock = LyricRenderClock()
        clock.sample(1000, 0, true)
        val before = clock.sample(1000, 48, true)
        val corrected = clock.sample(1020, 64, true)
        val regressedSample = clock.sample(1010, 80, true)
        assertTrue(corrected > before)
        assertTrue(regressedSample > corrected)
    }

    @Test fun pausedClockFreezesAndResumeExcludesPausedTime() {
        val clock = LyricRenderClock()
        clock.sample(1000, 0, true)
        val paused = clock.sample(1016, 16, false)
        assertEquals(paused, clock.sample(1016, 5016, false))
        assertEquals(1016, clock.sample(1016, 5016, true))
        assertTrue(clock.sample(1032, 5032, true) - 1016 in 14L..18L)
    }

    @Test fun explicitSeekImmediatelyResetsAndRejectsStalePlaybackPosition() {
        val clock = LyricRenderClock()
        clock.sample(5000, 0, true)
        clock.reset(1000, 16, true, awaitSeek = true)
        assertEquals(1000, clock.sample(5000, 16, true))
        assertEquals(1016, clock.sample(5000, 32, true))
        assertTrue(clock.sample(1032, 48, true) in 1030L..1033L)
        clock.reset(8000, 64, false, awaitSeek = true)
        assertEquals(8000, clock.sample(1032, 64, false))
        assertEquals(8000, clock.sample(8000, 80, false))
    }

    @Test fun evenSmallPausedSeekResetsImmediately() {
        val clock = LyricRenderClock()
        clock.sample(1000, 0, false)
        clock.reset(950, 16, false, awaitSeek = true)
        assertEquals(950, clock.sample(950, 16, false))
    }

    @Test fun loopOrTrackChangeDoesNotKeepPreviousSongsProgress() {
        val clock = LyricRenderClock()
        clock.sample(10_000, 0, true)
        assertEquals(0, clock.sample(0, 16, true))
        clock.reset(500, 32, true)
        assertEquals(500, clock.sample(500, 32, true))
    }

    @Test fun stalledSamplesHaveBoundedExtrapolationAndSameFrameReadsAgree() {
        val clock = LyricRenderClock()
        clock.sample(1000, 0, true)
        assertEquals(1250, clock.sample(1000, 250, true))
        assertEquals(1250, clock.sample(1000, 250, true))
        for (now in 266L..2000L step 16) clock.sample(1000, now, true)
        assertEquals(1500, clock.sample(1000, 2000, true))
        assertEquals(1500, clock.sample(1000, 2016, true))
    }

    @Test fun returningAfterNoFramesDoesNotFastForwardTheLastWord() {
        val clock = LyricRenderClock()
        clock.sample(43_347, 0, true, "exile") // before 的原始起点
        // 中间没有绘制：以前会先外推到 raw + 500ms，导致末词只剩一半时间。
        assertEquals(43_347, clock.sample(43_347, 5000, true, "exile"))
        val middle = clock.sample(43_879, 5532, true, "exile")
        assertTrue(middle < 44_411) // before 应当在原始终点结束
    }

    @Test fun switchingBackReanchorsEvenWhenSongPositionsAreClose() {
        val clock = LyricRenderClock()
        clock.sample(44_000, 0, true, "exile")
        assertEquals(43_950, clock.sample(43_950, 16, true, "other"))
        assertEquals(43_347, clock.sample(43_347, 32, true, "exile"))
        assertTrue(clock.sample(43_363, 48, true, "exile") in 43_361L..43_365L)
    }

    @Test fun pausedLyricsReopenedBeforeResumeDoNotRewindAcrossNextLine() {
        val clock = LyricRenderClock()
        clock.sample(39_950, 0, true, "song")
        val beforePause = clock.sample(39_950, 80, true, "song")
        assertTrue(beforePause >= 40_000)
        val paused = clock.sample(39_980, 80, false, "song")
        // 退出全屏后没有绘制，返回时先显示暂停的进度，再点击继续播放。
        assertEquals(paused, clock.sample(39_980, 5000, false, "song"))
        assertEquals(paused, clock.sample(39_980, 5100, false, "song"))
        val resumed = clock.sample(39_980, 5100, true, "song")
        assertEquals("resume must not erase an already highlighted word", paused, resumed)
        assertTrue(clock.sample(39_996, 5116, true, "song") >= resumed)
    }

    @Test fun returningToPlayingLyricsDoesNotRewindAnExtrapolatedWord() {
        val clock = LyricRenderClock()
        clock.sample(39_950, 0, true, "song")
        val beforeHide = clock.sample(39_950, 80, true, "song")
        val returned = clock.sample(39_980, 5000, true, "song")
        assertEquals(beforeHide, returned)
        assertTrue(clock.sample(39_996, 5016, true, "song") >= returned)
    }

    @Test fun explicitSeekWhileLyricsAreClosedStillOverridesFrozenProgress() {
        val clock = LyricRenderClock()
        clock.sample(40_050, 0, true, "song")
        clock.sample(40_050, 16, false, "song")
        clock.reset(39_980, 5000, false, awaitSeek = true)
        assertEquals(39_980, clock.sample(39_980, 5000, false, "song"))
        assertEquals(39_980, clock.sample(39_980, 5100, true, "song"))
    }

    @Test fun externalBackwardSeekWhilePausedStillReanchorsOnResume() {
        val clock = LyricRenderClock()
        clock.sample(40_050, 0, true, "song")
        clock.sample(40_050, 16, false, "song")
        assertEquals(10_000, clock.sample(10_000, 5000, true, "song"))
    }

    private fun absDifference(a: Long, b: Long) = kotlin.math.abs(a - b)
}
