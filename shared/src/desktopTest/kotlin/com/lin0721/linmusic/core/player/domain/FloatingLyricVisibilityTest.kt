package com.lin0721.linmusic.core.player.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingLyricVisibilityTest {

    @Test
    fun `暂停 无歌曲或歌词未解析时不显示悬浮歌词`() {
        assertFalse(shouldShowFloatingLyrics(false, true, LyricsKind.LYRICS))
        assertFalse(shouldShowFloatingLyrics(true, false, LyricsKind.LYRICS))
        assertFalse(shouldShowFloatingLyrics(true, true, null))
        assertFalse(shouldShowFloatingLyrics(true, true, LyricsKind.UNAVAILABLE))
    }

    @Test
    fun `纯音乐不显示悬浮歌词`() {
        val lyrics = listOf(LyricLine(timeMs = 0L, text = "纯音乐"))
        assertTrue(lyrics.isPureMusicLyrics())
        assertFalse(shouldShowFloatingLyrics(true, true, LyricsKind.PURE_MUSIC))
        assertTrue(shouldShowFloatingLyrics(true, true, LyricsKind.PURE_MUSIC, showPureMusicPreview = true))
    }

    @Test
    fun `播放有歌词歌曲时显示悬浮歌词`() {
        val lyrics = listOf(LyricLine(timeMs = 0L, text = "第一句歌词"))
        assertFalse(lyrics.isPureMusicLyrics())
        assertTrue(shouldShowFloatingLyrics(true, true, LyricsKind.LYRICS))
    }

    @Test
    fun `纯音乐标识兼容空格与括号变体`() {
        assertTrue(listOf(LyricLine(timeMs = 0L, text = " （纯音乐，请欣赏） ")).isPureMusicLyrics())
        assertTrue(listOf(LyricLine(timeMs = 0L, text = "[ Instrumental ]")).isPureMusicLyrics())
        assertFalse(listOf(LyricLine(timeMs = 0L, text = "Instrumental in my life")).isPureMusicLyrics())
    }
}
