package com.lin0721.linmusic.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricMetadataStripperTest {

    private val defaultStripper = LyricMetadataStripper(LyricFilterRules.DEFAULT)

    private fun lines(vararg texts: String) = texts.mapIndexed { i, t -> LyricLine(i * 1000L, 1000, t) }

    private fun LyricMetadataStripper.texts(vararg texts: String) = strip(lines(*texts)).map { it.text }

    @Test fun stripsHeaderCredits() {
        val result = defaultStripper.texts("作词：张三", "作曲 : 李四", "编曲：王五", "第一句", "第二句")
        assertEquals(listOf("第一句", "第二句"), result)
    }

    @Test fun stripsFooterCredits() {
        val result = defaultStripper.texts("第一句", "第二句", "混音：赵六", "母带：钱七")
        assertEquals(listOf("第一句", "第二句"), result)
    }

    @Test fun titleLineBeforeCreditsIsRemoved() {
        val result = defaultStripper.texts("歌名 - 歌手", "作词：张三", "第一句")
        assertEquals(listOf("第一句"), result)
    }

    @Test fun keepsSingerTagsWithoutFollowingCredits() {
        val result = defaultStripper.texts("作词：张三", "男：第一句", "女：第二句", "合：第三句")
        assertEquals(listOf("男：第一句", "女：第二句", "合：第三句"), result)
    }

    @Test fun weakLineBetweenCreditsIsRemoved() {
        val result = defaultStripper.texts("作词：张三", "监修：某人", "作曲：李四", "第一句")
        assertEquals(listOf("第一句"), result)
    }

    @Test fun creditsInMiddleAreKept() {
        val result = defaultStripper.texts("第一句", "作词：张三", "第二句")
        assertEquals(listOf("第一句", "作词：张三", "第二句"), result)
    }

    @Test fun keywordNeedsSeparator() {
        val result = defaultStripper.texts("词语很美", "第二句")
        assertEquals(listOf("词语很美", "第二句"), result)
    }

    @Test fun matchesKeywordIgnoringCaseSpacesAndBrackets() {
        val result = defaultStripper.texts("(Composed by: Someone)", "LYRICS BY：Someone", "【制作人：某人】", "Hello")
        assertEquals(listOf("Hello"), result)
    }

    @Test fun defaultRegexStripsPromotionLines() {
        val result = defaultStripper.texts("未经许可翻唱", "第一句", "酷狗音乐就是歌多")
        assertEquals(listOf("第一句"), result)
    }

    @Test fun pureMusicNoticeIsStripped() {
        assertTrue(defaultStripper.texts("纯音乐，请欣赏").isEmpty())
    }

    @Test fun skipsBlankLinesWhileScanning() {
        val result = defaultStripper.texts("作词：张三", "", "作曲：李四", "第一句")
        assertEquals(listOf("第一句"), result)
    }

    @Test fun invalidRegexIsIgnored() {
        val stripper = LyricMetadataStripper(LyricFilterRules(keywords = listOf("作词"), regexes = listOf("(unclosed")))
        assertEquals(listOf("第一句"), stripper.texts("作词：张三", "第一句"))
    }

    @Test fun emptyRulesReturnInput() {
        val stripper = LyricMetadataStripper(LyricFilterRules())
        assertTrue(stripper.isEmpty)
        assertEquals(listOf("作词：张三", "第一句"), stripper.texts("作词：张三", "第一句"))
    }

    @Test fun rulesRoundTripAndNormalize() {
        val raw = LyricFilterRules.encode(LyricFilterRules(listOf(" a ", "a", ""), listOf("b")))
        val decoded = LyricFilterRules.decodeOrNull(raw)
        assertEquals(LyricFilterRules(listOf("a"), listOf("b")), decoded)
        assertNull(LyricFilterRules.decodeOrNull("not json"))
        assertNotNull(LyricFilterRules.decodeOrNull("{\"keywords\":[\"x\"],\"extra\":1}"))
    }

    @Test fun defaultRegexesAreValid() {
        assertTrue(DEFAULT_LYRIC_FILTER_REGEXES.all(::isValidLyricFilterRegex))
        assertFalse(isValidLyricFilterRegex("(unclosed"))
        assertFalse(isValidLyricFilterRegex(" "))
    }
}
