package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerSelectionTest {

    @Test
    fun `分钟调整准确处理跨小时`() {
        assertEquals(SleepTimerSelection(1, 5), SleepTimerSelection(0, 50).adjustMinutes(15))
        assertEquals(SleepTimerSelection(0, 50), SleepTimerSelection(1, 5).adjustMinutes(-15))
        assertEquals(SleepTimerSelection(2, 0), SleepTimerSelection(1, 30).adjustMinutes(30))
    }

    @Test
    fun `分钟调整不会跨越零点或二十四点`() {
        val minimum = SleepTimerSelection(0, 10)
        val maximum = SleepTimerSelection(23, 50)
        assertFalse(minimum.canAdjustMinutes(-15))
        assertFalse(maximum.canAdjustMinutes(15))
        assertEquals(minimum, minimum.adjustMinutes(-15))
        assertEquals(maximum, maximum.adjustMinutes(15))
    }

    @Test
    fun `小时调整在边界处禁用且不改变分钟`() {
        val firstHour = SleepTimerSelection(0, 30)
        val lastHour = SleepTimerSelection(23, 30)
        assertFalse(firstHour.canAdjustHours(-1))
        assertFalse(lastHour.canAdjustHours(1))
        assertEquals(firstHour, firstHour.adjustHours(-1))
        assertEquals(lastHour, lastHour.adjustHours(1))
        assertTrue(firstHour.canAdjustHours(1))
        assertEquals(SleepTimerSelection(1, 30), firstHour.adjustHours(1))
    }
}
