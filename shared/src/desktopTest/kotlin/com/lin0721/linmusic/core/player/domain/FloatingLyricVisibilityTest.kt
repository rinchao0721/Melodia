package com.lin0721.linmusic.core.player.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingLyricVisibilityTest {

    @Test
    fun `暂停 无歌曲或歌词未解析时不显示悬浮歌词`() {
        assertFalse(shouldShowFloatingLyrics(false, true, true, false))
        assertFalse(shouldShowFloatingLyrics(true, false, true, false))
        assertFalse(shouldShowFloatingLyrics(true, true, false, false))
    }

    @Test
    fun `纯音乐不显示悬浮歌词`() {
        val lyrics = listOf(LyricLine(timeMs = 0L, text = "纯音乐"))
        assertTrue(lyrics.isPureMusicLyrics())
        assertFalse(shouldShowFloatingLyrics(true, true, true, lyrics.isPureMusicLyrics()))
        assertTrue(shouldShowFloatingLyrics(true, true, true, lyrics.isPureMusicLyrics(), showPureMusicPreview = true))
    }

    @Test
    fun `播放有歌词歌曲时显示悬浮歌词`() {
        val lyrics = listOf(LyricLine(timeMs = 0L, text = "第一句歌词"))
        assertFalse(lyrics.isPureMusicLyrics())
        assertTrue(shouldShowFloatingLyrics(true, true, true, lyrics.isPureMusicLyrics()))
    }
}
