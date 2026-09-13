package com.lin0721.linmusic.core.network

import com.lin0721.linmusic.core.network.crypto.XeapiKeyStore
import com.lin0721.linmusic.core.network.crypto.XeapiPublicKeyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CryptoInterceptorRoutingTest {

    // resolveCryptoType 是纯 URL 字符串判断，不会真的用到 XeapiKeyStore，这里给个不触碰
    // Context/网络的假实现即可，避免这个纯单测文件被迫引入 Robolectric
    private val fakeXeapiKeyStore = object : XeapiKeyStore {
        override suspend fun getOrFetchPublicKey(): XeapiPublicKeyState? = null
        override suspend fun refresh(): XeapiPublicKeyState? = null
    }

    private val interceptor = CryptoInterceptor(fakeXeapiKeyStore)

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

    @Test
    fun `xeapi路径识别为XEAPI`() {
        assertEquals(
            CryptoInterceptor.CryptoType.XEAPI,
            interceptor.resolveCryptoType("https://interface3.music.163.com/xeapi/resource/comments/add")
        )
    }

    @Test
    fun `xeapi不会被裸api兜底逻辑误判为WEAPI`() {
        assertEquals(
            CryptoInterceptor.CryptoType.XEAPI,
            interceptor.resolveCryptoType("https://music.163.com/xeapi/v1/resource/comments/reply")
        )
    }
}
