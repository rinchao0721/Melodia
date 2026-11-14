package com.lin0721.linmusic.desktop.ui.share

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareSentCapsuleTest {

    @Test
    fun emptyOrBlankNicknameFallsBackToGenericLabel() {
        assertEquals("对方", sentRecipientDisplayName(""))
        assertEquals("对方", sentRecipientDisplayName("   "))
        assertEquals("猫咖宣传", sentRecipientDisplayName("猫咖宣传"))
    }

    @Test
    fun timeoutCompletesAfterFullDurationWithoutHover() = runTest {
        val hovered = MutableStateFlow(false)
        var finished = false
        val job = launch {
            awaitSentCapsuleTimeout(hovered, timeoutMs = 2_500L, nowMs = testScheduler::currentTime)
            finished = true
        }

        advanceTimeBy(2_499L)
        runCurrent()
        assertFalse(finished)

        advanceTimeBy(1L)
        runCurrent()
        assertTrue(finished)
        job.cancel()
    }

    @Test
    fun hoverPausesCountdownAndResumesRemainingTimeOnExit() = runTest {
        val hovered = MutableStateFlow(false)
        var finished = false
        val job = launch {
            awaitSentCapsuleTimeout(hovered, timeoutMs = 2_500L, nowMs = testScheduler::currentTime)
            finished = true
        }

        advanceTimeBy(1_000L)
        runCurrent()
        hovered.value = true
        runCurrent()

        // 悬停期间即使超过原定总时长也不应提前结束
        advanceTimeBy(5_000L)
        runCurrent()
        assertFalse(finished)

        hovered.value = false
        runCurrent()
        advanceTimeBy(1_499L)
        runCurrent()
        assertFalse(finished)

        advanceTimeBy(1L)
        runCurrent()
        assertTrue(finished)
        job.cancel()
    }

    @Test
    fun hoveringFromStartWaitsFullDurationAfterExit() = runTest {
        val hovered = MutableStateFlow(true)
        var finished = false
        val job = launch {
            awaitSentCapsuleTimeout(hovered, timeoutMs = 2_500L, nowMs = testScheduler::currentTime)
            finished = true
        }

        advanceTimeBy(3_000L)
        runCurrent()
        assertFalse(finished)

        hovered.value = false
        runCurrent()
        advanceTimeBy(2_499L)
        runCurrent()
        assertFalse(finished)

        advanceTimeBy(1L)
        runCurrent()
        assertTrue(finished)
        job.cancel()
    }
}
