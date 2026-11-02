package com.lin0721.linmusic.core.share

import com.lin0721.linmusic.core.player.domain.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricShareSelectionTest {
    private val lines = listOf(
        LyricLine(0, text = ""),
        LyricLine(1000, text = "第一句", translation = "first"),
        LyricLine(2000, text = "第二句"),
        LyricLine(3000, text = "  "),
        LyricLine(4000, text = "第三句", translation = "third"),
        LyricLine(5000, text = "第四句"),
        LyricLine(6000, text = "第五句"),
        LyricLine(7000, text = "第六句")
    )

    @Test fun initialSkipsBlankLineAndIncludesTranslation() {
        assertEquals(LyricPick(setOf(1), setOf(1)), LyricShareSelection.initial(lines, 0))
        assertEquals(LyricPick(setOf(1), setOf(1)), LyricShareSelection.initial(lines, -1))
        assertEquals(LyricPick(setOf(2)), LyricShareSelection.initial(lines, 2))
        assertEquals(LyricPick(), LyricShareSelection.initial(emptyList(), 0))
    }

    @Test fun limitCountsLinesNotEntries() {
        val full = LyricPick(setOf(1, 2, 4, 5, 6), setOf(1, 4))
        assertEquals(5, full.lineCount)
        assertEquals(full, LyricShareSelection.toggleOriginal(lines, full, 7))
        // 已占名额的行可以继续切换另一半
        assertEquals(setOf(1), LyricShareSelection.toggleTranslation(lines, full, 4).translations)
        val freed = LyricShareSelection.toggleOriginal(lines, full, 2)
        assertEquals(setOf(1, 4, 5, 6, 7), LyricShareSelection.toggleOriginal(lines, freed, 7).originals)
    }

    @Test fun translationToggleNeedsTranslation() {
        assertEquals(LyricPick(), LyricShareSelection.toggleTranslation(lines, LyricPick(), 2))
        assertEquals(LyricPick(translations = setOf(4)), LyricShareSelection.toggleTranslation(lines, LyricPick(), 4))
        assertEquals(LyricPick(), LyricShareSelection.toggleOriginal(lines, LyricPick(), 3))
    }

    @Test fun buildKeepsOrderAndPromotesTranslationOnlyLines() {
        val built = LyricShareSelection.build(lines, LyricPick(originals = setOf(1, 2), translations = setOf(1, 4)))
        assertEquals(
            listOf(ShareLyricLine("第一句", "first"), ShareLyricLine("第二句"), ShareLyricLine("third")),
            built
        )
    }

    @Test fun contextSkipsBlankNeighbours() {
        assertEquals("第二句" to "第四句", LyricShareSelection.context(lines, setOf(4)))
        assertEquals(null to "第二句", LyricShareSelection.context(lines, setOf(1)))
    }

    @Test fun cjkDetection() {
        assertTrue(LyricShareSelection.isMostlyCjk(listOf(ShareLyricLine("總有幸福有心疼"))))
        assertFalse(LyricShareSelection.isMostlyCjk(listOf(ShareLyricLine("Hello my love 你好"))))
        assertFalse(LyricShareSelection.isMostlyCjk(emptyList()))
    }
}
