package com.lin0721.linmusic.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class KrcLyricParserTest {

    @Test
    fun parsesKaraokeWordsTranslationAndRoma() {
        val langJson = """
            {
              "content": [
                {
                  "type": 1,
                  "lyricContent": [
                    ["第一行翻译"],
                    ["//"],
                    ["第三行", "翻译"]
                  ]
                },
                {
                  "type": 0,
                  "lyricContent": [
                    ["di", "yi", "hang"],
                    ["//"],
                    ["san ", "hang"]
                  ]
                }
              ]
            }
        """.trimIndent()
        val langB64 = Base64.getEncoder().encodeToString(langJson.toByteArray(Charsets.UTF_8))

        val krc = """
            [id:123456]
            [ar:测试歌手]
            [ti:测试歌曲]
            [offset:0]
            [language:$langB64]
            [1000,1500]<0,400,0>第<400,500,0>一<900,600,0>行
            [3000,1000]<0,500,0>纯<500,500,0>伴
            [5000,1200]普通整行歌词
        """.trimIndent()

        val lines = KrcLyricParser.parse(krc)
        assertEquals(3, lines.size)

        val first = lines[0]
        assertEquals(1000L, first.timeMs)
        assertEquals(1500L, first.durationMs)
        assertEquals("第一行", first.text)
        assertEquals("第一行翻译", first.translation)
        assertEquals("di yi hang", first.roma)
        assertEquals(3, first.words.size)
        assertEquals(WordInfo("第", 0L, 400L), first.words[0])
        assertEquals(WordInfo("一", 400L, 500L), first.words[1])
        assertEquals(WordInfo("行", 900L, 600L), first.words[2])

        val second = lines[1]
        assertEquals(3000L, second.timeMs)
        assertEquals("纯伴", second.text)
        assertNull(second.translation)
        assertNull(second.roma)
        assertEquals(2, second.words.size)

        val third = lines[2]
        assertEquals(5000L, third.timeMs)
        assertEquals(1200L, third.durationMs)
        assertEquals("普通整行歌词", third.text)
        assertEquals("第三行翻译", third.translation)
        assertEquals("san hang", third.roma)
        assertTrue(third.words.isEmpty())
    }

    @Test
    fun ignoresMalformedLanguageBlockWithoutCrashing() {
        val krc = """
            [language:!!!invalid-base64!!!]
            [1200,800]<0,400,0>测<400,400,0>试
        """.trimIndent()

        val lines = KrcLyricParser.parse(krc)
        assertEquals(1, lines.size)
        assertEquals("测试", lines[0].text)
        assertNull(lines[0].translation)
        assertNull(lines[0].roma)
    }

    @Test
    fun fallsBackToStandardLrcWhenNoKrcLinePresent() {
        val lrc = """
            [00:01.50]Hello World
            [00:04.00]Second Line
        """.trimIndent()

        val lines = KrcLyricParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(1500L, lines[0].timeMs)
        assertEquals("Hello World", lines[0].text)
        assertEquals(4000L, lines[1].timeMs)
        assertEquals("Second Line", lines[1].text)
    }

    @Test
    fun returnsEmptyForBlankInput() {
        assertTrue(KrcLyricParser.parse("").isEmpty())
        assertTrue(KrcLyricParser.parse("   \n  ").isEmpty())
    }
}
