package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerFormatTest {

    @Test
    fun `不足一小时显示分钟和秒`() {
        assertEquals("0:01", formatSleepTimerRemaining(1_000L))
        assertEquals("29:59", formatSleepTimerRemaining(29 * 60_000L + 59_000L))
    }

    @Test
    fun `超过一小时显示小时分钟和秒`() {
        assertEquals("1:00:00", formatSleepTimerRemaining(60 * 60_000L))
        assertEquals("23:59:59", formatSleepTimerRemaining(23 * 60 * 60_000L + 59 * 60_000L + 59_000L))
    }
}
