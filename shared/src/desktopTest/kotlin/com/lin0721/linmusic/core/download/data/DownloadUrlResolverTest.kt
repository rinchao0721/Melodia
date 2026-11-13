package com.lin0721.linmusic.core.download.data

import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.plugin.PluginDownloadLink
import com.lin0721.linmusic.core.plugin.PluginTrackQuery
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadUrlResolverTest {

    private val query = PluginTrackQuery("1", "歌", "歌手", "专辑", 1000, MusicPlatform.NETEASE)

    private class Fixture(
        private val response: SongDownloadUrlResponse,
        private val fallback: Boolean = true,
        private val link: PluginDownloadLink? = null,
        private val pluginThrows: Boolean = false
    ) {
        var pluginCalls = 0
            private set
        var pluginLevel: String? = null
            private set

        val resolver = DownloadUrlResolver(
            official = { _, _ -> response },
            fallbackEnabled = { fallback },
            plugins = { _, level ->
                pluginCalls++
                pluginLevel = level
                if (pluginThrows) throw IllegalStateException("插件出错")
                link
            }
        )
    }

    private fun official(url: String?, encodeType: String? = "mp3", level: String? = "exhigh", time: Long = 0, code: Int = 200) =
        SongDownloadUrlResponse(code, SongDownloadUrlItem(url = url, size = 4096, level = level, encodeType = encodeType, time = time))

    private fun link(url: String = "https://cdn.example.com/a.flac", type: String? = null, quality: String? = null, headers: Map<String, String> = emptyMap()) =
        PluginDownloadLink(url, type, quality, headers)

    private fun ready(outcome: DownloadUrlOutcome): ResolvedDownload = (outcome as DownloadUrlOutcome.Ready).download

    private fun rejected(outcome: DownloadUrlOutcome): String = (outcome as DownloadUrlOutcome.Rejected).reason

    @Test
    fun `官方链接可用时不询问插件`() = runBlocking {
        val fixture = Fixture(official("https://music.126.net/a.mp3"), link = link())
        val download = ready(fixture.resolver.resolve(1, "exhigh", query))
        assertEquals("https://music.126.net/a.mp3", download.url)
        assertEquals("mp3", download.encodeType)
        assertEquals(4096L, download.size)
        assertEquals("exhigh", download.level)
        assertFalse(download.fromPlugin)
        assertEquals(0, fixture.pluginCalls)
    }

    @Test
    fun `换源开关关闭时按官方原因失败且不询问插件`() = runBlocking {
        val fixture = Fixture(SongDownloadUrlResponse(code = -1), fallback = false, link = link())
        assertEquals("获取下载链接失败", rejected(fixture.resolver.resolve(1, "exhigh", query)))
        assertEquals(0, fixture.pluginCalls)
    }

    @Test
    fun `官方失败时使用插件链接并传入请求音质`() = runBlocking {
        val fixture = Fixture(SongDownloadUrlResponse(code = -1), link = link(quality = "lossless"))
        val download = ready(fixture.resolver.resolve(1, "hires", query))
        assertTrue(download.fromPlugin)
        assertEquals("https://cdn.example.com/a.flac", download.url)
        assertEquals("flac", download.encodeType)
        assertEquals(0L, download.size)
        assertEquals("lossless", download.level)
        assertEquals("hires", fixture.pluginLevel)
    }

    @Test
    fun `官方只有试听时询问插件，插件没有结果保留试听原因`() = runBlocking {
        val trial = official("https://music.126.net/trial.mp3", time = 30_000)
        assertTrue(ready(Fixture(trial, link = link()).resolver.resolve(1, "standard", query)).fromPlugin)
        assertEquals("该音质仅支持试听，需要 VIP/购买后才能完整下载", rejected(Fixture(trial).resolver.resolve(1, "standard", query)))
    }

    @Test
    fun `无损被静默降级时询问插件，插件没有结果保留会员原因`() = runBlocking {
        val downgraded = official("https://music.126.net/a.mp3", encodeType = "mp3", level = "exhigh")
        val fixture = Fixture(downgraded, link = link())
        assertTrue(ready(fixture.resolver.resolve(1, "lossless", query)).fromPlugin)
        assertEquals(1, fixture.pluginCalls)
        assertEquals("需要更高会员等级才能下载该音质", rejected(Fixture(downgraded).resolver.resolve(1, "lossless", query)))
    }

    @Test
    fun `插件抛异常视为没有结果`() = runBlocking {
        val fixture = Fixture(SongDownloadUrlResponse(code = -1), pluginThrows = true)
        assertEquals("获取下载链接失败", rejected(fixture.resolver.resolve(1, "standard", query)))
    }

    @Test
    fun `格式优先取插件声明，其次链接后缀，最后按 mp3`() = runBlocking {
        suspend fun typeOf(link: PluginDownloadLink) =
            ready(Fixture(SongDownloadUrlResponse(code = -1), link = link).resolver.resolve(1, "standard", query)).encodeType
        assertEquals("m4a", typeOf(link(url = "https://cdn.example.com/a.flac", type = "M4A")))
        assertEquals("flac", typeOf(link(url = "https://cdn.example.com/a.flac?sign=1#x", type = "../exe")))
        assertEquals("mp3", typeOf(link(url = "https://cdn.example.com/stream?id=9")))
    }

    @Test
    fun `插件音质不认识时按 standard 记录`() = runBlocking {
        val download = ready(Fixture(SongDownloadUrlResponse(code = -1), link = link(quality = "ultra")).resolver.resolve(1, "hires", query))
        assertEquals("standard", download.level)
    }

    @Test
    fun `请求头丢弃宿主控制项、非 ASCII 取值与非法项`() = runBlocking {
        val headers = mapOf(
            "Referer" to "https://example.com/",
            "User-Agent" to "Mozilla/5.0\tTest",
            "range" to "bytes=0-",
            "Accept-Encoding" to "gzip, br",
            "Host" to "evil.example.com",
            "If-None-Match" to "abc",
            "X-Title" to "歌曲",
            "Bad Name" to "x",
            "X-Inject" to "a\r\nHost: evil"
        )
        val download = ready(Fixture(SongDownloadUrlResponse(code = -1), link = link(headers = headers)).resolver.resolve(1, "standard", query))
        assertEquals(mapOf("Referer" to "https://example.com/", "User-Agent" to "Mozilla/5.0\tTest"), download.headers)
    }

    @Test
    fun `格式不合法的插件链接视为没有结果`() = runBlocking {
        for (url in listOf("https://", "https://a b.com/x.mp3")) {
            val fixture = Fixture(SongDownloadUrlResponse(code = -1), link = link(url = url))
            assertEquals("获取下载链接失败", rejected(fixture.resolver.resolve(1, "standard", query)))
        }
    }

    @Test
    fun `插件音质不高于已有文件时判定为不更好`() {
        fun record(quality: String) = DownloadRecord(1, "file", quality, 0, 1, "歌", "歌手")
        val fromPlugin = ResolvedDownload("https://cdn.example.com/a.flac", "flac", 0, "standard", emptyMap(), fromPlugin = true)
        assertTrue(fromPlugin.notBetterThan(record("exhigh")))
        assertTrue(fromPlugin.notBetterThan(record("standard")))
        assertFalse(fromPlugin.copy(level = "lossless").notBetterThan(record("exhigh")))
        assertFalse(fromPlugin.notBetterThan(null))
        assertFalse(fromPlugin.copy(fromPlugin = false).notBetterThan(record("exhigh")))
    }
}
