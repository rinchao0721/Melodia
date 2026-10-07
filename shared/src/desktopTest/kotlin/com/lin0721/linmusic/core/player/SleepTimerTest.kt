package com.lin0721.linmusic.core.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    @Test
    fun `分钟数为零或负数时不启动倒计时`() = runTest {
        var finished = false
        val timer = SleepTimer(this, { testScheduler.currentTime }) { finished = true }

        timer.start(0)
        assertEquals(0L, timer.remaining.value)

        timer.start(-5)
        assertEquals(0L, timer.remaining.value)

        advanceUntilIdle()
        assertFalse(finished)
    }

    @Test
    fun `启动后剩余时间为分钟数换算的毫秒`() = runTest {
        val timer = SleepTimer(this, { testScheduler.currentTime }) {}
        timer.start(30)
        assertEquals(30 * 60 * 1000L, timer.remaining.value)
        timer.cancel()
    }

    // advanceTimeBy 只执行严格早于终点时刻的任务，故各处多推进 1ms 越过 delay 的到期点
    @Test
    fun `每秒递减一次`() = runTest {
        val timer = SleepTimer(this, { testScheduler.currentTime }) {}
        timer.start(1)

        advanceTimeBy(1001L)
        assertEquals(59_000L, timer.remaining.value)

        advanceTimeBy(3000L)
        assertEquals(56_000L, timer.remaining.value)

        timer.cancel()
    }

    @Test
    fun `倒计时归零时触发回调且剩余清零`() = runTest {
        var finished = false
        val timer = SleepTimer(this, { testScheduler.currentTime }) { finished = true }
        timer.start(1)

        // 差一秒时尚未触发
        advanceTimeBy(59_001L)
        assertEquals(1000L, timer.remaining.value)
        assertFalse(finished)

        // 最后一秒走完，nextVal 恰为 0 应判定为结束而非继续
        advanceTimeBy(1000L)
        assertEquals(0L, timer.remaining.value)
        assertTrue(finished)
    }

    @Test
    fun `归零后不再重复触发回调`() = runTest {
        var count = 0
        val timer = SleepTimer(this, { testScheduler.currentTime }) { count++ }
        timer.start(1)

        advanceUntilIdle()
        assertEquals(1, count)
    }

    @Test
    fun `取消后不再触发回调`() = runTest {
        var finished = false
        val timer = SleepTimer(this, { testScheduler.currentTime }) { finished = true }
        timer.start(1)

        advanceTimeBy(30_000L)
        timer.cancel()
        advanceUntilIdle()

        assertFalse(finished)
    }

    @Test
    fun `重新启动会取消上一次倒计时，不产生两次回调`() = runTest {
        var count = 0
        val timer = SleepTimer(this, { testScheduler.currentTime }) { count++ }

        timer.start(1)
        advanceTimeBy(30_000L)
        // 未等第一次走完就改设新时长
        timer.start(2)
        assertEquals(2 * 60 * 1000L, timer.remaining.value)

        advanceUntilIdle()
        assertEquals(1, count)
    }

    @Test
    fun `重新启动为零分钟等同于取消`() = runTest {
        var finished = false
        val timer = SleepTimer(this, { testScheduler.currentTime }) { finished = true }

        timer.start(1)
        advanceTimeBy(30_000L)
        timer.start(0)

        assertEquals(0L, timer.remaining.value)
        advanceUntilIdle()
        assertFalse(finished)
    }

    @Test
    fun `调度延迟后按真实经过时间校准剩余值`() = runTest {
        var clockMs = 0L
        val timer = SleepTimer(this, { clockMs }) {}
        timer.start(1)

        clockMs = 31_000L
        advanceTimeBy(1_001L)

        assertEquals(29_000L, timer.remaining.value)
        timer.cancel()
    }
}
