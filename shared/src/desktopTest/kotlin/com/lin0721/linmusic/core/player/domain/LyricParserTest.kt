package com.lin0721.linmusic.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricParserTest {

    // ======================= LRC 解析 =======================

    @Test
    fun `解析标准两位毫秒的LRC行`() {
        val lines = LyricParser.parseLrc("[00:12.34]Hello world")
        assertEquals(1, lines.size)
        assertEquals(12340L, lines[0].timeMs) // 12s + 34*10ms
        assertEquals("Hello world", lines[0].text)
    }

    @Test
    fun `解析三位毫秒的LRC行`() {
        val lines = LyricParser.parseLrc("[00:12.340]Hello world")
        assertEquals(1, lines.size)
        assertEquals(12340L, lines[0].timeMs)
    }

    @Test
    fun `LRC支持冒号或点号作为毫秒分隔符`() {
        val lines = LyricParser.parseLrc("[01:02:500]Colon separated")
        assertEquals(1, lines.size)
        assertEquals(62500L, lines[0].timeMs) // 1*60000 + 2*1000 + 500
    }

    @Test
    fun `LRC多行按时间戳排序`() {
        val text = """
            [00:20.00]Second
            [00:10.00]First
        """.trimIndent()
        val lines = LyricParser.parseLrc(text)
        assertEquals(2, lines.size)
        assertEquals("First", lines[0].text)
        assertEquals("Second", lines[1].text)
    }

    @Test
    fun `LRC空文本行被跳过`() {
        val text = "[00:10.00]\n[00:20.00]Real line"
        val lines = LyricParser.parseLrc(text)
        assertEquals(1, lines.size)
        assertEquals("Real line", lines[0].text)
    }

    @Test
    fun `LRC不匹配格式的行被忽略`() {
        val text = "not a lyric line\n[00:10.00]Valid line"
        val lines = LyricParser.parseLrc(text)
        assertEquals(1, lines.size)
        assertEquals("Valid line", lines[0].text)
    }

    @Test
    fun `LRC空字符串返回空列表`() {
        assertTrue(LyricParser.parseLrc("").isEmpty())
    }

    // ======================= YRC 逐字解析 =======================

    @Test
    fun `解析单行YRC并计算逐字相对偏移`() {
        val yrc = "[0,3000](0,500,0)Hello(500,500,0)World"
        val lines = LyricParser.parseYrc(yrc)
        assertEquals(1, lines.size)
        val line = lines[0]
        assertEquals(0L, line.timeMs)
        assertEquals(3000L, line.durationMs)
        assertEquals("HelloWorld", line.text)
        assertEquals(2, line.words.size)
        assertEquals("Hello", line.words[0].text)
        assertEquals(0L, line.words[0].startOffsetMs)
        assertEquals(500L, line.words[0].durationMs)
        assertEquals("World", line.words[1].text)
        assertEquals(500L, line.words[1].startOffsetMs) // 500(绝对时间) - 0(行起始时间)
    }

    @Test
    fun `YRC单字起始时间相对行起始时间做偏移换算`() {
        // 行起始时间非0时，字词的相对偏移应为 绝对时间-行起始时间
        val yrc = "[1000,2000](1000,300,0)Word1(1300,300,0)Word2"
        val lines = LyricParser.parseYrc(yrc)
        assertEquals(1, lines.size)
        assertEquals(0L, lines[0].words[0].startOffsetMs)
        assertEquals(300L, lines[0].words[1].startOffsetMs)
    }

    @Test
    fun `YRC多行按时间戳排序`() {
        val yrc = "[2000,1000](2000,500,0)Second\n[0,1000](0,500,0)First"
        val lines = LyricParser.parseYrc(yrc)
        assertEquals(2, lines.size)
        assertEquals("First", lines[0].text)
        assertEquals("Second", lines[1].text)
    }

    @Test
    fun `YRC空白行与不匹配行被跳过`() {
        val yrc = "\n   \nnot a yrc line\n[0,1000](0,500,0)Valid"
        val lines = LyricParser.parseYrc(yrc)
        assertEquals(1, lines.size)
        assertEquals("Valid", lines[0].text)
    }

    @Test
    fun `YRC空字符串返回空列表`() {
        assertTrue(LyricParser.parseYrc("").isEmpty())
    }
}
