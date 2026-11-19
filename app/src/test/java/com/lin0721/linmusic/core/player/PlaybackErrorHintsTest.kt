package com.lin0721.linmusic.core.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackErrorHintsTest {

    private val timeout = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

    @Test
    fun `第三方直链超时返回其域名`() {
        val host = thirdPartySourceUnreachableHost(
            timeout,
            "http://Yinyue.Haitangw.net/kg/kg_song_kw.php?type=mp3&id=875f&level=exhigh"
        )

        assertEquals("yinyue.haitangw.net", host)
    }

    @Test
    fun `连接失败同样提示`() {
        val host = thirdPartySourceUnreachableHost(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            "https://example.org/a.mp3"
        )

        assertEquals("example.org", host)
    }

    @Test
    fun `网易自家链接不提示`() {
        assertNull(thirdPartySourceUnreachableHost(timeout, "http://m701.music.126.net/2026/a.flac"))
        assertNull(thirdPartySourceUnreachableHost(timeout, "https://music.163.com/song/media/outer/url?id=1"))
    }

    @Test
    fun `本地代理与非网络地址不提示`() {
        assertNull(thirdPartySourceUnreachableHost(timeout, "http://127.0.0.1:8080/stream"))
        assertNull(thirdPartySourceUnreachableHost(timeout, "content://media/external/audio/1"))
        assertNull(thirdPartySourceUnreachableHost(timeout, "file:///sdcard/a.mp3"))
    }

    @Test
    fun `非连接类错误与空地址不提示`() {
        assertNull(thirdPartySourceUnreachableHost(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, "https://example.org/a.mp3"))
        assertNull(thirdPartySourceUnreachableHost(timeout, null))
        assertNull(thirdPartySourceUnreachableHost(timeout, ""))
        assertNull(thirdPartySourceUnreachableHost(timeout, "http://bad host/ a"))
    }

    @Test
    fun `提示文案带域名`() {
        assertEquals("音源服务器无响应（example.org）", formatSourceUnreachableMessage("example.org"))
    }
}
