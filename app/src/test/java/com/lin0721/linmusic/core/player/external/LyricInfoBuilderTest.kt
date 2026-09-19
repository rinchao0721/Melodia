package com.lin0721.linmusic.core.player.external

import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.WordInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricInfoBuilderTest {

    @Test
    fun `LRC时间戳格式化正确`() {
        assertEquals("[00:00.000]", LyricInfoBuilder.formatLrcTag(0L))
        assertEquals("[00:01.050]", LyricInfoBuilder.formatLrcTag(1050L))
        assertEquals("[01:02.345]", LyricInfoBuilder.formatLrcTag(62345L))
        assertEquals("[10:00.000]", LyricInfoBuilder.formatLrcTag(600000L))
    }

    @Test
    fun `逐字时间戳格式化正确`() {
        assertEquals("<00:00.000>", LyricInfoBuilder.formatWordTag(0L))
        assertEquals("<00:01.050>", LyricInfoBuilder.formatWordTag(1050L))
        assertEquals("<01:02.345>", LyricInfoBuilder.formatWordTag(62345L))
    }

    @Test
    fun `生成标准LRC格式`() {
        val lines = listOf(
            LyricLine(timeMs = 1000L, text = "Hello"),
            LyricLine(timeMs = 3500L, text = "World")
        )
        val lrc = LyricInfoBuilder.buildPlainLrc(lines)
        val expected = "[00:01.000]Hello\n[00:03.500]World"
        assertEquals(expected, lrc)
    }

    @Test
    fun `生成带逐字增强的ELRC格式`() {
        val words = listOf(
            WordInfo("Hello", 0L, 500L),
            WordInfo(" ", 500L, 100L),
            WordInfo("World", 600L, 800L)
        )
        val lines = listOf(
            LyricLine(timeMs = 2000L, text = "Hello World", words = words)
        )
        val elrc = LyricInfoBuilder.buildRawLyric(lines)
        val expected = "[00:02.000]<00:02.000>Hello<00:02.500> <00:02.600>World<00:03.400>"
        assertEquals(expected, elrc)
    }

    @Test
    fun `生成LyricInfo完整JSON并校验字段`() {
        val lines = listOf(
            LyricLine(
                timeMs = 1200L,
                text = "测试歌词",
                translation = "Test lyric",
                words = listOf(WordInfo("测试", 0L, 300L), WordInfo("歌词", 300L, 400L))
            )
        )

        val jsonString = LyricInfoBuilder.buildLyricInfoJson(
            songName = "歌曲名称",
            artist = "歌手",
            songId = "123456",
            album = "专辑名称",
            lines = lines,
            showTranslation = true
        )

        val root = JSONObject(jsonString)
        assertEquals("歌曲名称", root.getString("songName"))
        assertEquals("歌手", root.getString("artist"))
        assertEquals("123456", root.getString("songId"))
        assertEquals("专辑名称", root.getString("album"))
        assertEquals("com.lin0721.linmusic", root.getString("source"))
        assertFalse(root.getBoolean("noLyric"))

        val lyric = root.getString("lyric")
        assertTrue(lyric.contains("[00:01.200]测试歌词"))

        val rawLyric = root.getString("rawLyric")
        assertTrue(rawLyric.contains("[00:01.200]<00:01.200>测试<00:01.500>歌词<00:01.900>"))

        val trans = root.getString("translationLyric")
        assertTrue(trans.contains("[00:01.200]Test lyric"))
    }

    @Test
    fun `禁用翻译时translationLyric不应包含在JSON中`() {
        val lines = listOf(
            LyricLine(timeMs = 1000L, text = "主歌词", translation = "翻译歌词")
        )
        val jsonString = LyricInfoBuilder.buildLyricInfoJson(
            songName = "Song",
            artist = "Artist",
            songId = "1",
            album = "Album",
            lines = lines,
            showTranslation = false
        )
        val root = JSONObject(jsonString)
        assertFalse(root.has("translationLyric"))
    }

    @Test
    fun `空歌词列表时生成有效空JSON`() {
        val jsonString = LyricInfoBuilder.buildLyricInfoJson(
            songName = "Empty",
            artist = "Artist",
            songId = "0",
            album = "Album",
            lines = emptyList(),
            showTranslation = true
        )
        val root = JSONObject(jsonString)
        assertTrue(root.getBoolean("noLyric"))
        assertEquals("", root.getString("lyric"))
    }
}
