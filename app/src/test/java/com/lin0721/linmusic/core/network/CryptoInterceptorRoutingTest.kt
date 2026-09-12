package com.lin0721.linmusic.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CryptoInterceptorRoutingTest {

    private val interceptor = CryptoInterceptor()

    @Test
    fun `eapi路径识别为EAPI`() {
        assertEquals(
            CryptoInterceptor.CryptoType.EAPI,
            interceptor.resolveCryptoType("https://music.163.com/eapi/song/enhance/player/url/v1")
        )
    }

    @Test
    fun `weapi路径识别为WEAPI`() {
        assertEquals(
            CryptoInterceptor.CryptoType.WEAPI,
            interceptor.resolveCryptoType("https://music.163.com/weapi/v1/album/12345")
        )
    }

    @Test
    fun `二维码weapi路径识别为WEAPI`() {
        assertEquals(
            CryptoInterceptor.CryptoType.WEAPI,
            interceptor.resolveCryptoType("https://music.163.com/weapi/login/qrcode/unikey")
        )
        assertEquals(
            CryptoInterceptor.CryptoType.WEAPI,
            interceptor.resolveCryptoType("https://music.163.com/weapi/login/qrcode/client/login")
        )
    }

    @Test
    fun `裸api路径识别为WEAPI`() {
        assertEquals(
            CryptoInterceptor.CryptoType.WEAPI,
            interceptor.resolveCryptoType("https://music.163.com/api/artist/albums/12345")
        )
    }

    @Test
    fun `linux api路径识别为LINUXAPI`() {
        assertEquals(
            CryptoInterceptor.CryptoType.LINUXAPI,
            interceptor.resolveCryptoType("https://music.163.com/linux/api/song/enhance/player/url")
        )
    }

    @Test
    fun `不匹配任何前缀的路径返回null`() {
        assertNull(interceptor.resolveCryptoType("https://music.163.com/other/random/path"))
    }

    @Test
    fun `eapi优先于裸api匹配`() {
        // "/eapi/" 本身不包含 "/api/" 子串，但仍需确认路由结果落在 EAPI 而不是被误判
        assertEquals(
            CryptoInterceptor.CryptoType.EAPI,
            interceptor.resolveCryptoType("https://music.163.com/eapi/v6/playlist/detail")
        )
    }
}
