package com.lin0721.linmusic.feature.player.ui

import com.lin0721.linmusic.core.player.domain.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniLyricContentTest {

    private val lines = listOf(
        LyricLine(timeMs = 5_000L, text = "第一句"),
        LyricLine(timeMs = 9_000L, text = "第二句")
    )

    @Test
    fun `加载中且无歌词显示加载动画`() {
        assertEquals(MiniLyricContent(null), resolveMiniLyricContent(emptyList(), -1, isLoading = true))
    }

    @Test
    fun `前奏显示首行并降低透明度`() {
        assertEquals(MiniLyricContent("第一句", dimmed = true), resolveMiniLyricContent(lines, -1, isLoading = false))
    }

    @Test
    fun `加载完无歌词显示暂无歌词`() {
        assertEquals(MiniLyricContent("暂无歌词", dimmed = true), resolveMiniLyricContent(emptyList(), -1, isLoading = false))
    }

    @Test
    fun `正常显示当前行`() {
        assertEquals(MiniLyricContent("第二句"), resolveMiniLyricContent(lines, 1, isLoading = false))
    }

    @Test
    fun `纯音乐优先显示`() {
        val pure = listOf(LyricLine(timeMs = 0L, text = "纯音乐"))
        assertEquals(MiniLyricContent("纯音乐"), resolveMiniLyricContent(pure, 0, isLoading = true))
    }
}
