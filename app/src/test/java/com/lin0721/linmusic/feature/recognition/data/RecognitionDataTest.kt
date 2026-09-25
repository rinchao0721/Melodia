package com.lin0721.linmusic.feature.recognition.data

import com.lin0721.linmusic.feature.recognition.data.dto.AudioMatchResponse
import com.lin0721.linmusic.feature.recognition.domain.RecognitionHistoryEntry
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionDataTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    // 字段取自真实响应，删去了无关字段
    private val hitJson = """
        {"data":{"type":1,"queryId":"q","result":[{"startTime":60000,"song":{"name":"红灯笼","id":1970864953,
        "artists":[{"name":"洛天依","id":0},{"name":"陈秋桦","id":14586859}],
        "album":{"name":"小桦作品集","id":39228636,"picUrl":"http://p1.music.126.net/x.jpg"},
        "duration":240072,"privilege":{"st":0}}}],"mv":null,"moduleList":["relatedSongs"],"noMatchReason":0},
        "code":200,"message":""}
    """.trimIndent()

    private val missJson = """
        {"data":{"type":0,"queryId":"q","result":null,"mv":null,"moduleList":null,"noMatchReason":10},"code":200,"message":""}
    """.trimIndent()

    @Test
    fun `命中响应映射为候选并把封面升级为https`() {
        val candidates = json.decodeFromString<AudioMatchResponse>(hitJson).toCandidates()

        assertEquals(1, candidates.size)
        val c = candidates.first()
        assertEquals(1970864953L, c.songId)
        assertEquals("红灯笼", c.title)
        assertEquals("洛天依, 陈秋桦", c.artists)
        assertEquals("小桦作品集", c.album)
        assertEquals("https://p1.music.126.net/x.jpg", c.coverUrl)
        assertEquals(60_000L, c.startTimeMs)
        assertEquals(240_072L, c.durationMs)
    }

    @Test
    fun `未命中响应result为null时返回空列表`() {
        val response = json.decodeFromString<AudioMatchResponse>(missJson)
        assertEquals(200, response.code)
        assertTrue(response.toCandidates().isEmpty())
    }

    @Test
    fun `缺少歌曲或id无效的结果被丢弃且同曲去重最多3条`() {
        val results = (1..5).joinToString(",") { """{"startTime":$it,"song":{"id":$it,"name":"s$it"}}""" }
        val raw = """{"code":200,"data":{"result":[{"startTime":1},{"song":{"id":0}},{"startTime":9,"song":{"id":1,"name":"dup"}},$results]}}"""
        val candidates = json.decodeFromString<AudioMatchResponse>(raw).toCandidates()
        assertEquals(listOf(1L, 2L, 3L), candidates.map { it.songId })
        assertEquals(9L, candidates.first().startTimeMs)
    }

    private fun entry(id: String) = RecognitionHistoryEntry(
        id = id, songId = 1, title = "t", artists = "a", coverUrl = "", startTimeMs = 0, recognizedAt = 0
    )

    @Test
    fun `新记录插到最前并截断到上限`() {
        val current = (1..100).map { entry("e$it") }
        val updated = prependCapped(current, entry("new"), MAX_RECOGNITION_HISTORY)
        assertEquals(100, updated.size)
        assertEquals("new", updated.first().id)
        assertEquals("e99", updated.last().id)
    }

    @Test
    fun `同一首歌多次识别各记一条`() {
        val updated = prependCapped(listOf(entry("a")), entry("b").copy(songId = 1), 100)
        assertEquals(listOf("b", "a"), updated.map { it.id })
    }
}
