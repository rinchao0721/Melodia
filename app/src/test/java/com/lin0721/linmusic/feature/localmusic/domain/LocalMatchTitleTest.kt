package com.lin0721.linmusic.feature.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMatchTitleTest {
    @Test fun stripsObviousZeroPrefixedIndicesWithWhitespace() {
        listOf("01 五味瓶", "001 五味瓶", "01\t五味瓶").forEach {
            assertEquals("五味瓶", LocalMatchTitle.forSearch(it))
        }
    }

    @Test fun stripsMarkedIndicesWithoutRemovingTheActualTitle() {
        listOf("01. 五味瓶", "01 - 五味瓶", "1. 五味瓶", "12 - 五味瓶", "01.五味瓶", "02、五味瓶").forEach {
            assertEquals("五味瓶", LocalMatchTitle.forSearch(it))
        }
    }

    @Test fun fullwidthTrackNumberAndSeparatorAreRecognized() {
        assertEquals("五味瓶", LocalMatchTitle.forSearch("　０１． 五味瓶　"))
        assertEquals("五味瓶", LocalMatchTitle.forSearch("０１  五味瓶"))
        assertEquals("五味瓶", LocalMatchTitle.forSearch("０２ － 五味瓶"))
    }

    @Test fun trueNumericTitlesYearsAndTimeLikeNamesRemainIntact() {
        listOf("1989", "1989 Song", "505", "505 Song", "7 rings", "01", "001", "1.23", "01.23", "01:23", "10-Feet").forEach {
            assertEquals(it, LocalMatchTitle.forSearch(it))
        }
    }

    @Test fun preservesVersionSuffixAndTitlePunctuation() {
        assertEquals("Song (Live)", LocalMatchTitle.forSearch("01 Song (Live)"))
        assertEquals("Song - Remastered", LocalMatchTitle.forSearch("02. Song - Remastered"))
        assertEquals("7 rings", LocalMatchTitle.forSearch("01 7 rings"))
    }

    @Test fun stripsOnlyOneIndexAndRequiresRemainingTitle() {
        assertEquals("01 Song", LocalMatchTitle.forSearch("02 01 Song"))
        assertEquals("01.", LocalMatchTitle.forSearch("01."))
        assertEquals("", LocalMatchTitle.forSearch("  "))
    }

    @Test fun matchingCleansTheLocalSideOnly() {
        assertTrue(LocalMatchTitle.matches("五味瓶", "01 五味瓶"))
        assertTrue(LocalMatchTitle.matches("01 五味瓶", "01 五味瓶"))
        assertFalse(LocalMatchTitle.matches("01 五味瓶", "五味瓶"))
        assertFalse(LocalMatchTitle.matches("7 rings", "7 Song"))
        assertFalse(LocalMatchTitle.matches("Song", "01 Song (Live)"))
    }
}
