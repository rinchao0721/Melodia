package com.lin0721.linmusic.core.player.domain

import org.junit.Assert.*
import org.junit.Test

class QrcLyricParserTest {
    @Test fun absoluteTimesBecomeOffsetsAndPreserveSpaces() {
        val line = QrcLyricParser.parse("[1000,900]Hello (1100,200)world(1300,700)").single()
        assertEquals("Hello world", line.text)
        assertEquals(listOf(100L, 300L), line.words.map { it.startOffsetMs })
        assertEquals(1000L, line.durationMs)
        assertNull(line.backgroundLine)
        assertEquals(LyricAlignment.START, line.alignment)
    }

    @Test fun relativeTimesUseOneBasisForTheWholeLine() {
        val line = QrcLyricParser.parse("[1000,2400]甲(0,1200)乙(1200,1200)").single()
        assertEquals(listOf(0L, 1200L), line.words.map { it.startOffsetMs })
    }

    @Test fun xmlTranslationAndQrcRomanizationAreCombined() {
        val xml = """<QrcInfos><Lyric_1 LyricContent="[1000,1000]A&amp;B(1000,1000)"/></QrcInfos>"""
        val line = QrcLyricParser.parse(xml,
            "[00:00.00]TME享有本翻译作品的著作权\n[00:01.10]译文",
            "[1000,1000]roma(1000,1000)").single()
        assertEquals("A&B", line.text)
        assertEquals("译文", line.translation)
        assertEquals("roma", line.roma)
    }

    @Test fun farTranslationIsIgnoredAndEachMainLineIsUsedOnce() {
        val lines = QrcLyricParser.parse("[1000,1000]甲(1000,1000)\n[9000,1000]乙(9000,1000)",
            "[00:01.00]第一条\n[00:01.10]重复\n[00:20.00]太远")
        assertEquals("第一条", lines[0].translation)
        assertNull(lines[1].translation)
    }

    @Test fun weakNoticeWordsAreOnlyFilteredBeforeFirstMainLine() {
        val lines = QrcLyricParser.parse("[2000,1000]甲(2000,1000)",
            "[00:00.50]制作人：某某\n[00:20.00]远处的译文")
        assertNull(lines.single().translation)
    }

    @Test fun translationContainingWeakNoticeWordIsKeptInsideTheSong() {
        val lines = QrcLyricParser.parse("[2000,1000]甲(2000,1000)\n[5000,1000]乙(5000,1000)",
            "[00:02.00]译文一\n[00:05.00]我在制作一首歌")
        assertEquals("译文一", lines[0].translation)
        assertEquals("我在制作一首歌", lines[1].translation)
    }

    @Test fun plainLrcWorksAndBackgroundMarkersAreIgnored() {
        val line = QrcLyricParser.parse("[ti:title]\n[bg:和声(1000,100)]\n[00:01.00]普通歌词").single()
        assertEquals("普通歌词", line.text)
        assertTrue(line.words.isEmpty())
    }

    @Test fun malformedTimingFallsBackToLineHighlighting() {
        val line = QrcLyricParser.parse("[1000,2000]甲(1500,500)乙(1200,500)").single()
        assertEquals("甲乙", line.text)
        assertTrue(line.words.isEmpty())
    }
}
