package com.lin0721.linmusic.feature.player.data

import com.lin0721.linmusic.feature.player.domain.SongMusicMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicMemoryParseTest {

    private fun block(vararg resources: SongWikiResource) = SongWikiBlock(
        code = "SONG_PLAY_ABOUT_MUSIC_MEMORY",
        creatives = listOf(SongWikiCreative(creativeType = "memoryGrid", resources = resources.toList()))
    )

    @Test
    fun `首次收听与累计播放都齐全`() {
        val memory = parseMusicMemory(
            block(
                SongWikiResource(
                    resourceType = "FIRST_LISTEN",
                    resourceExt = SongWikiResourceExt(
                        musicFirstListenDto = MusicFirstListenDto(date = "2026.08.06 22:39", season = "夏末", period = "深夜")
                    )
                ),
                SongWikiResource(
                    resourceType = "TOTAL_PLAY",
                    resourceExt = SongWikiResourceExt(
                        musicTotalPlayDto = MusicTotalPlayDto(playCount = 3, text = "如同看了3600字的诗")
                    )
                )
            )
        )
        assertEquals(SongMusicMemory("2026.08.06 22:39", "夏末", "深夜", 3, "如同看了3600字的诗"), memory)
    }

    @Test
    fun `未登录时区块为空返回 null`() {
        assertNull(parseMusicMemory(SongWikiBlock(code = "SONG_PLAY_ABOUT_MUSIC_MEMORY")))
    }

    @Test
    fun `只有累计播放时仍然返回`() {
        val memory = parseMusicMemory(
            block(
                SongWikiResource(
                    resourceType = "TOTAL_PLAY",
                    resourceExt = SongWikiResourceExt(musicTotalPlayDto = MusicTotalPlayDto(playCount = 5))
                )
            )
        )
        assertEquals(SongMusicMemory("", "", "", 5, ""), memory)
    }

    @Test
    fun `播放次数为 0 且无首次收听视为没有回忆`() {
        val memory = parseMusicMemory(
            block(
                SongWikiResource(
                    resourceType = "TOTAL_PLAY",
                    resourceExt = SongWikiResourceExt(musicTotalPlayDto = MusicTotalPlayDto(playCount = 0))
                )
            )
        )
        assertNull(memory)
    }
}
