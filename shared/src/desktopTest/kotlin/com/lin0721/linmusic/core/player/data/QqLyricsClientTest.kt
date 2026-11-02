package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class QqLyricsClientTest {
    private val target = Track(id = 1, name = "测试", ar = listOf(Artist(name = "歌手")), dt = 100000)

    private fun candidate(id: String = "42", name: String = "测试", artist: String = "歌手", duration: Long = 100000) =
        ExternalTrack(id, name, artist, artistList = listOf(artist), durationMs = duration, platform = MusicPlatform.QQ)

    @Test fun matchingRejectsWrongSongArtistDurationAndMissingNumericId() {
        val client = QqLyricsClient()
        for (bad in listOf(candidate(name = "其他"), candidate(artist = "别人"), candidate(duration = 105001), candidate(id = "mid"), candidate(duration = 0))) {
            assertNull(client.pickCandidate(listOf(bad), target))
        }
        assertEquals("42", client.pickCandidate(listOf(candidate(duration = 105000)), target)?.id)
    }

    @Test fun decryptsProtocolVectorAndRejectsInvalidPayloads() {
        val text = "[1000,1000]你好(1000,1000)"
        assertEquals(text, QqLyricDecoder.decodeLyric(encrypt(text)))
        assertEquals("", QqLyricDecoder.decodeLyric("not hex"))
        assertEquals("", QqLyricDecoder.decodeLyric("0".repeat(16)))
    }

    @Test fun searchDecryptParseAndCacheWorkTogether() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":0,"search":{"code":0,"data":{"body":{"item_song":[{"id":42,"title":"测试","interval":100,"singer":[{"name":"歌手"}]}]}}}}"""))
            val lyric = encrypt("""<QrcInfos><Lyric_1 LyricType="1" LyricContent="[1000,1000]你(1000,500)好(1500,500)"/></QrcInfos>""")
            val trans = encrypt("[00:01.00]hello")
            server.enqueue(MockResponse().setBody("""{"lyric":{"code":0,"data":{"lyric":"$lyric","trans":"$trans","qrc_t":1}}}"""))
            val client = QqLyricsClient(OkHttpClient(), server.url("/").toString())
            val lines = client.fetch(target)
            assertEquals("你好", lines.single().text)
            assertEquals("hello", lines.single().translation)
            assertEquals(2, lines.single().words.size)
            assertEquals(lines, client.fetch(target))
            assertEquals(2, server.requestCount)
            val searchRequest = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("https://y.qq.com/", searchRequest.getHeader("Referer"))
            assertNull(searchRequest.getHeader("Cookie"))
            val lyricRequest = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            val params = lyricRequest["lyric"]!!.jsonObject["param"]!!.jsonObject
            assertEquals(42, params["songID"]!!.jsonPrimitive.int)
            assertEquals(1, params["qrc"]!!.jsonPrimitive.int)
            assertEquals(100, params["interval"]!!.jsonPrimitive.int)
        }
    }

    @Test fun malformedQrcRequestsLrcFallback() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"search":{"code":0,"data":{"body":{"item_song":[{"id":42,"title":"测试","interval":100,"singer":[{"name":"歌手"}]}]}}}}"""))
            server.enqueue(MockResponse().setBody("""{"lyric":{"code":0,"data":{"lyric":"broken"}}}"""))
            server.enqueue(MockResponse().setBody("""{"lyric":{"code":0,"data":{"lyric":"${encrypt("[00:01.00]普通歌词")}"}}}"""))
            val client = QqLyricsClient(OkHttpClient(), server.url("/").toString())
            val line = client.fetch(target).single()
            assertEquals("普通歌词", line.text)
            assertTrue(line.words.isEmpty())
            server.takeRequest(); server.takeRequest()
            val params = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["lyric"]!!.jsonObject["param"]!!.jsonObject
            assertEquals(0, params["qrc"]!!.jsonPrimitive.int)
        }
    }

    @Test fun litePermissionErrorFallsBackToPublicSearchWithNumericSongId() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":0,"search":{"code":2001}}"""))
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"song":{"list":[{"songid":42,"songmid":"abc","songname":"测试","interval":100,"singer":[{"name":"歌手"}]}]}}}"""))
            server.enqueue(MockResponse().setBody("""{"lyric":{"code":0,"data":{"lyric":"${encrypt("[1000,1000]你好(1000,1000)")}"}}}"""))
            val client = QqLyricsClient(OkHttpClient(), server.url("/musicu").toString(), server.url("/public").toString())
            assertEquals("你好", client.fetch(target).single().text)
            server.takeRequest()
            val fallback = server.takeRequest()
            assertEquals("GET", fallback.method)
            assertEquals("测试 歌手", fallback.requestUrl!!.queryParameter("w"))
            val params = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["lyric"]!!.jsonObject["param"]!!.jsonObject
            assertEquals(42, params["songID"]!!.jsonPrimitive.int)
        }
    }

    @Test fun noMatchIsCachedBriefly() = runTest {
        MockWebServer().use { server ->
            val emptySearch = """{"search":{"code":0,"data":{"body":{"item_song":[]}}}}"""
            repeat(2) { server.enqueue(MockResponse().setBody(emptySearch)) }
            val client = QqLyricsClient(OkHttpClient(), server.url("/").toString())
            assertTrue(client.fetch(target).isEmpty())
            assertEquals(2, server.requestCount)
            assertTrue(client.fetch(target).isEmpty())
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun requestFailureIsNotCached() = runTest {
        MockWebServer().use { server ->
            val client = QqLyricsClient(OkHttpClient(), server.url("/musicu").toString(), server.url("/public").toString())
            repeat(2) {
                server.enqueue(MockResponse().setBody("""{"code":0,"search":{"code":2001}}"""))
                server.enqueue(MockResponse().setResponseCode(500))
            }
            assertTrue(client.fetch(target).isEmpty())
            assertEquals(2, server.requestCount)
            assertTrue(client.fetch(target).isEmpty())
            assertEquals(4, server.requestCount)
        }
    }

    companion object {
        // 固定合成样本由 SPlayer/LDDC 的独立实现生成；QQ 的 DES 变体不能用标准 JCE 替代。
        private val samples = mapOf(
            "[1000,1000]你好(1000,1000)" to "d6a2be95d6447372edf0b214ea33651dfed7654c16f095bbcfd8096c3751608b",
            """<QrcInfos><Lyric_1 LyricType="1" LyricContent="[1000,1000]你(1000,500)好(1500,500)"/></QrcInfos>""" to "ee7befb746f93831d42dcdf2e21f0d5d833c044042289012e0c11abf870c7dfb9c809065c5235ff1c2cb724752bd931569db18e6a7c7b6273b6818974b1d21bedc60b4a5fad826c451c054c8d72ecd2b5ad57d78eb3e9663",
            "[00:01.00]hello" to "32dabb4c5e9846fa7a4eb4ea8db4d7fea1168a842853cee0",
            "[00:01.00]普通歌词" to "32dabb4c5e9846fa46588ca11ead6726dfb4b970e9cc481eeaf570c6dc647422"
        )
        fun encrypt(text: String): String = samples.getValue(text)
    }
}
