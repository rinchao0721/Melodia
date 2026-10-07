package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerFormatTest {
    @Test
    fun `不足一小时显示分和秒`() {
        assertEquals("0:00", formatSleepTimerRemaining(0L))
        assertEquals("9:05", formatSleepTimerRemaining(545_000L))
    }

    @Test
    fun `超过一小时显示时分秒`() {
        assertEquals("1:00:00", formatSleepTimerRemaining(3_600_000L))
        assertEquals("2:03:04", formatSleepTimerRemaining(7_384_000L))
    }

    @Test
    fun `不足整秒的剩余时间向上取整`() {
        assertEquals("0:01", formatSleepTimerRemaining(1L))
        assertEquals("1:00", formatSleepTimerRemaining(59_001L))
    }
}
