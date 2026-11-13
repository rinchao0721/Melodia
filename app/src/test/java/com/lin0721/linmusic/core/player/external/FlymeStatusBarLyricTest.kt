package com.lin0721.linmusic.core.player.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlymeStatusBarLyricTest {

    @Test
    fun `厂商或品牌为魅族即视为魅族设备`() {
        assertTrue(isFlymeDevice("Meizu", null))
        assertTrue(isFlymeDevice(null, " meizu "))
        assertFalse(isFlymeDevice("Xiaomi", "Redmi"))
        assertFalse(isFlymeDevice(null, null))
    }

    @Test
    fun `反射成功时优先使用反射值`() {
        val reflected = FlymeTickerFlags(1, 2)
        assertEquals(reflected, resolveFlymeTickerFlags(reflected, "Xiaomi", "Xiaomi"))
    }

    @Test
    fun `反射失败时魅族设备用兜底数值，其他设备不支持`() {
        assertEquals(
            FlymeTickerFlags(FLYME_FLAG_ALWAYS_SHOW_TICKER_FALLBACK, FLYME_FLAG_ONLY_UPDATE_TICKER_FALLBACK),
            resolveFlymeTickerFlags(null, "Meizu", "meizu")
        )
        assertNull(resolveFlymeTickerFlags(null, "Google", "google"))
    }

    @Test
    fun `仅在支持、开启、播放且有歌词时输出文本`() {
        assertEquals("歌词", resolveFlymeTickerText(supported = true, enabled = true, playing = true, line = " 歌词 "))
        assertNull(resolveFlymeTickerText(supported = false, enabled = true, playing = true, line = "歌词"))
        assertNull(resolveFlymeTickerText(supported = true, enabled = false, playing = true, line = "歌词"))
        assertNull(resolveFlymeTickerText(supported = true, enabled = true, playing = false, line = "歌词"))
        assertNull(resolveFlymeTickerText(supported = true, enabled = true, playing = true, line = "  "))
        assertNull(resolveFlymeTickerText(supported = true, enabled = true, playing = true, line = null))
    }
}
