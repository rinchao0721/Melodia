package com.lin0721.linmusic.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class FullPlayerCardLayoutTest {

    @Test
    fun `空值或空白串回退默认布局`() {
        assertEquals(FullPlayerCardLayout.DEFAULT, FullPlayerCardLayout.decode(null))
        assertEquals(FullPlayerCardLayout.DEFAULT, FullPlayerCardLayout.decode("   "))
    }

    @Test
    fun `编码后再解码保持顺序与显隐`() {
        val layout = listOf(
            FullPlayerCardSetting(FullPlayerCard.SIMILAR_ARTISTS, visible = true),
            FullPlayerCardSetting(FullPlayerCard.COMMENTS_PREVIEW, visible = false),
            FullPlayerCardSetting(FullPlayerCard.LYRICS, visible = true),
            FullPlayerCardSetting(FullPlayerCard.SONG_DETAIL, visible = false),
            FullPlayerCardSetting(FullPlayerCard.ARTIST_ALBUMS, visible = true),
            FullPlayerCardSetting(FullPlayerCard.ABOUT_ARTIST, visible = true)
        )
        assertEquals(layout, FullPlayerCardLayout.decode(FullPlayerCardLayout.encode(layout)))
    }

    @Test
    fun `丢弃未知与重复项并在末尾补齐缺失卡片`() {
        val decoded = FullPlayerCardLayout.decode("unknown:1,song_detail:0,song_detail:1,lyrics:1")
        val expected = listOf(
            FullPlayerCardSetting(FullPlayerCard.SONG_DETAIL, visible = false),
            FullPlayerCardSetting(FullPlayerCard.LYRICS, visible = true),
            FullPlayerCardSetting(FullPlayerCard.COMMENTS_PREVIEW, visible = true),
            FullPlayerCardSetting(FullPlayerCard.ABOUT_ARTIST, visible = true),
            FullPlayerCardSetting(FullPlayerCard.ARTIST_ALBUMS, visible = true),
            FullPlayerCardSetting(FullPlayerCard.SIMILAR_ARTISTS, visible = true)
        )
        assertEquals(expected, decoded)
    }

    @Test
    fun `缺少或非法的显隐标记按可见处理`() {
        val decoded = FullPlayerCardLayout.decode("lyrics,comments_preview:x,,:0")
        assertEquals(FullPlayerCardLayout.DEFAULT, decoded)
    }
}
