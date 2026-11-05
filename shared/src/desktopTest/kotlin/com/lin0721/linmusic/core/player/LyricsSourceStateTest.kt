package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.player.domain.LyricLine
import org.junit.Assert.*
import org.junit.Test

class LyricsSourceStateTest {
    private val plain = listOf(LyricLine(1000, 1000, "歌词"))
    private val failure = Result.failure<List<LyricLine>>(IllegalStateException("offline"))

    @Test fun cancelsAmllOnlyWhenNeteaseSucceededEmptyAndQqFinishedEmpty() {
        val state = LyricsSourceState(amllEnabled = true, qqEnabled = true)
        assertFalse(state.shouldCancelAmll())
        state.onNetease(Result.success(emptyList()))
        assertFalse(state.shouldCancelAmll())
        state.onQq(emptyList())
        assertTrue(state.shouldCancelAmll())
    }

    @Test fun neteaseFailureNeverCancelsAmll() {
        val state = LyricsSourceState(amllEnabled = true, qqEnabled = true)
        state.onNetease(failure)
        state.onQq(emptyList())
        assertFalse(state.shouldCancelAmll())
    }

    @Test fun disabledQqNeverCancelsAmll() {
        val state = LyricsSourceState(amllEnabled = true, qqEnabled = false)
        state.onNetease(Result.success(emptyList()))
        assertFalse(state.shouldCancelAmll())
    }

    @Test fun disabledNeteaseDoesNotBlockFinishOrCancelAmll() {
        val state = LyricsSourceState(amllEnabled = true, qqEnabled = true, neteaseEnabled = false)
        state.onQq(emptyList())
        assertFalse(state.shouldCancelAmll())
        assertFalse(state.isFinished)
        state.onAmll(emptyList())
        assertTrue(state.isFinished)
    }

    @Test fun allSourcesDisabledIsFinishedFromTheStart() {
        val state = LyricsSourceState(amllEnabled = false, qqEnabled = false, neteaseEnabled = false)
        assertTrue(state.isFinished)
        assertTrue(state.candidate().getOrThrow().lines.isEmpty())
    }

    @Test fun qqLyricsKeepAmllPending() {
        val state = LyricsSourceState(amllEnabled = true, qqEnabled = true)
        state.onNetease(Result.success(emptyList()))
        state.onQq(plain)
        assertFalse(state.shouldCancelAmll())
    }

    @Test fun finishesOnlyAfterEveryEnabledSourceReports() {
        val state = LyricsSourceState(amllEnabled = true, qqEnabled = true)
        state.onNetease(Result.success(plain))
        state.onQq(emptyList())
        assertFalse(state.isFinished)
        state.onAmll(emptyList())
        assertTrue(state.isFinished)
    }

    @Test fun disabledSourcesDoNotBlockFinish() {
        val state = LyricsSourceState(amllEnabled = false, qqEnabled = false)
        state.onNetease(Result.success(plain))
        assertTrue(state.isFinished)
    }

    @Test fun cancelledAmllIsFinishedAndIgnoresLateResult() {
        val state = LyricsSourceState(amllEnabled = true, qqEnabled = true)
        state.onNetease(Result.success(emptyList()))
        state.onQq(emptyList())
        state.cancelAmll()
        assertTrue(state.isFinished)
        assertTrue(state.isOwnAmllCancellation(LyricsSource.AMLL))
        assertFalse(state.isOwnAmllCancellation(LyricsSource.QQ))
        state.markPublished()
        state.onAmll(plain)
        assertFalse(state.dirty)
    }

    @Test fun dirtyFollowsUpdatesUntilPublished() {
        val state = LyricsSourceState(amllEnabled = false, qqEnabled = false)
        assertFalse(state.dirty)
        state.onNetease(Result.success(plain))
        assertTrue(state.dirty)
        state.markPublished()
        assertFalse(state.dirty)
    }

    @Test fun pluginLyricsAreLowestPriorityAndKeepStateOpenUntilReturned() {
        val pluginLines = listOf(LyricLine(2000, 1000, "插件歌词"))
        val state = LyricsSourceState(amllEnabled = false, qqEnabled = false, pluginEnabled = true)
        state.onNetease(Result.success(emptyList()))
        assertFalse(state.isFinished)
        state.onPlugin(pluginLines)
        assertTrue(state.isFinished)
        assertEquals(LyricsSource.PLUGIN, state.candidate().getOrNull()?.source)

        val withNetease = LyricsSourceState(amllEnabled = false, qqEnabled = false, pluginEnabled = true)
        withNetease.onNetease(Result.success(plain))
        withNetease.onPlugin(pluginLines)
        assertEquals(LyricsSource.NETEASE, withNetease.candidate().getOrNull()?.source)
    }
}
