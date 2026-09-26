package com.lin0721.linmusic.core.network.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseCryptoTest {

    @Test
    fun `eapi 对相同输入始终产生相同密文（确定性）`() {
        val first = NeteaseCrypto.eapi("/api/song/enhance/player/url/v1", """{"ids":"[123]"}""")
        val second = NeteaseCrypto.eapi("/api/song/enhance/player/url/v1", """{"ids":"[123]"}""")
        assertEquals(first, second)
    }

    @Test
    fun `eapi 不同输入产生不同密文`() {
        val a = NeteaseCrypto.eapi("/api/song/enhance/player/url/v1", """{"ids":"[123]"}""")
        val b = NeteaseCrypto.eapi("/api/song/enhance/player/url/v1", """{"ids":"[456]"}""")
        assertNotEquals(a["params"], b["params"])
    }

    @Test
    fun `eapi 输出为大写十六进制字符串`() {
        val result = NeteaseCrypto.eapi("/api/test", "{}")
        val params = result.getValue("params")
        assertTrue(params.isNotEmpty())
        assertTrue(params.all { it.isDigit() || it in 'A'..'F' })
        assertEquals(params, params.uppercase())
    }

    @Test
    fun `linuxapi 对相同输入始终产生相同密文`() {
        val first = NeteaseCrypto.linuxapi("""{"foo":"bar"}""")
        val second = NeteaseCrypto.linuxapi("""{"foo":"bar"}""")
        assertEquals(first, second)
    }

    @Test
    fun `linuxapi 输出为大写十六进制字符串`() {
        val result = NeteaseCrypto.linuxapi("""{"foo":"bar"}""")
        val eparams = result.getValue("eparams")
        assertTrue(eparams.isNotEmpty())
        assertTrue(eparams.all { it.isDigit() || it in 'A'..'F' })
    }

    @Test
    fun `testWeapi 固定密钥下对相同输入始终产生相同密文`() {
        val first = NeteaseCrypto.testWeapi("hello", "0123456789abcdef")
        val second = NeteaseCrypto.testWeapi("hello", "0123456789abcdef")
        assertEquals(first, second)
    }

    @Test
    fun `testWeapi 不同密钥产生不同的params与encSecKey`() {
        val a = NeteaseCrypto.testWeapi("hello", "0123456789abcdef")
        val b = NeteaseCrypto.testWeapi("hello", "fedcba9876543210")
        assertNotEquals(a["params"], b["params"])
        assertNotEquals(a["encSecKey"], b["encSecKey"])
    }

    @Test
    fun `weapi 返回的Map包含params与encSecKey两个键`() {
        val result = NeteaseCrypto.weapi("""{"foo":"bar"}""")
        assertTrue(result.containsKey("params"))
        assertTrue(result.containsKey("encSecKey"))
        assertTrue(result.getValue("params").isNotEmpty())
        assertTrue(result.getValue("encSecKey").isNotEmpty())
    }
}
