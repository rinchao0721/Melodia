package com.lin0721.linmusic.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class NeteaseDeviceIdTest {

    @Test
    fun `相同品牌型号主板生成相同32位小写十六进制摘要`() {
        val result = NeteaseDeviceId.compute(brand = "Google", model = "Pixel 7", board = "cheetah")
        assertEquals(32, result.length)
        assertEquals(result, result.lowercase())
        assertEquals(result, NeteaseDeviceId.compute(brand = "Google", model = "Pixel 7", board = "cheetah"))
    }

    @Test
    fun `不同输入生成不同摘要`() {
        val a = NeteaseDeviceId.compute(brand = "Google", model = "Pixel 7", board = "cheetah")
        val b = NeteaseDeviceId.compute(brand = "Xiaomi", model = "MI 9", board = "cepheus")
        assert(a != b)
    }
}
