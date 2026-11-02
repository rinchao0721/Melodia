package com.lin0721.linmusic.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class UiScaleTest {

    @Test
    fun `越界百分比被钳制到范围内`() {
        assertEquals(UiScale.MIN_PERCENT, UiScale.normalizePercent(-50))
        assertEquals(UiScale.MIN_PERCENT, UiScale.normalizePercent(0))
        assertEquals(UiScale.MIN_PERCENT, UiScale.normalizePercent(79))
        assertEquals(UiScale.MAX_PERCENT, UiScale.normalizePercent(1000))
    }

    @Test
    fun `范围内的百分比保持不变`() {
        assertEquals(80, UiScale.normalizePercent(80))
        assertEquals(100, UiScale.normalizePercent(100))
        assertEquals(225, UiScale.normalizePercent(225))
        assertEquals(250, UiScale.normalizePercent(250))
    }

    @Test
    fun `下限为百分之八十且默认仍是百分之百`() {
        assertEquals(80, UiScale.MIN_PERCENT)
        assertEquals(100, UiScale.DEFAULT_PERCENT)
    }

    @Test
    fun `百分比换算为缩放系数`() {
        assertEquals(0.8f, UiScale.toFactor(80), 0.0001f)
        assertEquals(1f, UiScale.toFactor(100), 0.0001f)
        assertEquals(2.25f, UiScale.toFactor(225), 0.0001f)
        assertEquals(0.8f, UiScale.toFactor(10), 0.0001f)
        assertEquals(2.5f, UiScale.toFactor(999), 0.0001f)
    }
}
