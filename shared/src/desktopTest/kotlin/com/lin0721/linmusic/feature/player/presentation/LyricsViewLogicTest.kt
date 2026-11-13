package com.lin0721.linmusic.feature.player.presentation

import com.lin0721.linmusic.core.player.domain.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsViewLogicTest {

    private fun line(text: String, translation: String? = null, roma: String? = null) =
        LyricLine(timeMs = 0, text = text, translation = translation, roma = roma)

    @Test
    fun `加载中优先于其他占位`() {
        assertEquals(LyricsPlaceholder.Loading, lyricsPlaceholder(emptyList(), isLoading = true))
        assertEquals(LyricsPlaceholder.Loading, lyricsPlaceholder(listOf(line("词")), isLoading = true))
    }

    @Test
    fun `没有歌词与纯音乐各有占位，正常歌词没有占位`() {
        assertEquals(LyricsPlaceholder.Empty, lyricsPlaceholder(emptyList(), isLoading = false))
        assertEquals(LyricsPlaceholder.PureMusic, lyricsPlaceholder(listOf(line("纯音乐")), isLoading = false))
        assertNull(lyricsPlaceholder(listOf(line("纯音乐"), line("别的")), isLoading = false))
        assertNull(lyricsPlaceholder(listOf(line("词")), isLoading = false))
    }

    @Test
    fun `副行按模式取翻译或罗马音，缺失或空白不显示`() {
        val l = line("词", translation = "译文", roma = "romaji")
        assertEquals("译文", secondaryText(l, "translation"))
        assertEquals("romaji", secondaryText(l, "roma"))
        assertNull(secondaryText(l, "none"))
        assertNull(secondaryText(line("词"), "translation"))
        assertNull(secondaryText(line("词", translation = "  "), "translation"))
    }

    @Test
    fun `主声部和背景和声都可提供副行选项`() {
        val background = line("和声", translation = "和声译文", roma = "  ")
        val main = line("主歌词", roma = "romaji").copy(backgroundLine = background)
        val lines = listOf(main)

        assertTrue(hasSecondaryText(lines, "translation"))
        assertTrue(hasSecondaryText(lines, "roma"))
        assertFalse(hasSecondaryText(lines, "none"))
        assertFalse(hasSecondaryText(listOf(main.copy(roma = null)), "roma"))
    }

    @Test
    fun `高亮集合非空时按集合，否则退回主行`() {
        assertTrue(isActiveLine(2, primaryIndex = 2, activeIndices = emptySet()))
        assertFalse(isActiveLine(3, primaryIndex = 2, activeIndices = emptySet()))
        assertTrue(isActiveLine(3, primaryIndex = 2, activeIndices = setOf(2, 3)))
        assertFalse(isActiveLine(2, primaryIndex = 2, activeIndices = setOf(3)))
    }
}
