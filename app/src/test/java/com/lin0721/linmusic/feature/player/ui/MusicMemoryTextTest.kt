package com.lin0721.linmusic.feature.player.ui

import androidx.compose.ui.text.SpanStyle
import com.lin0721.linmusic.feature.player.domain.SongMusicMemory
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicMemoryTextTest {

    private val emphasis = SpanStyle()

    private fun text(memory: SongMusicMemory) = buildMusicMemoryText(memory, emphasis).text

    @Test
    fun `字段齐全时拼成完整叙述`() {
        assertEquals(
            "2026.08.06，一个夏末的深夜，你第一次听到这首歌。至今播放 3 次，如同看了3600字的诗。",
            text(SongMusicMemory("2026.08.06 22:39", "夏末", "深夜", 3, "如同看了3600字的诗"))
        )
    }

    @Test
    fun `缺季节时只保留时段`() {
        assertEquals(
            "2026.08.06，一个深夜，你第一次听到这首歌。",
            text(SongMusicMemory("2026.08.06 22:39", "", "深夜", 0, ""))
        )
    }

    @Test
    fun `只有播放次数时换用独立句式并去掉重复句号`() {
        assertEquals(
            "这首歌你已播放 5 次，如同听了一场雨。",
            text(SongMusicMemory("", "", "", 5, "如同听了一场雨。"))
        )
    }
}
