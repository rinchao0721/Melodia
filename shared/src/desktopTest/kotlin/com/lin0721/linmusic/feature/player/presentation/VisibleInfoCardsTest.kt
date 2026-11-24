package com.lin0721.linmusic.feature.player.presentation

import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.preferences.FullPlayerCard
import com.lin0721.linmusic.core.preferences.FullPlayerCardSetting
import com.lin0721.linmusic.feature.player.domain.SongWikiData
import com.lin0721.linmusic.feature.player.ui.PlayerSongDetailState
import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleInfoCardsTest {

    private val lyricLines = listOf(LyricLine(timeMs = 0, text = "第一行"))
    private val all = FullPlayerCard.entries.toSet()

    private fun layout(vararg cards: FullPlayerCard, hidden: Set<FullPlayerCard> = emptySet()) =
        cards.map { FullPlayerCardSetting(it, visible = it !in hidden) }

    @Test
    fun `歌词加载中不展示`() {
        val state = PlayerSongDetailState(lyrics = lyricLines, isLyricsLoading = true)
        assertEquals(emptyList<FullPlayerCard>(), visibleInfoCards(state, layout(FullPlayerCard.LYRICS)))
    }

    @Test
    fun `歌词就绪后展示`() {
        val state = PlayerSongDetailState(lyrics = lyricLines)
        assertEquals(listOf(FullPlayerCard.LYRICS), visibleInfoCards(state, layout(FullPlayerCard.LYRICS)))
    }

    @Test
    fun `纯音乐与空歌词不展示歌词卡`() {
        val pure = PlayerSongDetailState(lyrics = listOf(LyricLine(timeMs = 0, text = "纯音乐")))
        assertEquals(emptyList<FullPlayerCard>(), visibleInfoCards(pure, layout(FullPlayerCard.LYRICS)))
        assertEquals(emptyList<FullPlayerCard>(), visibleInfoCards(PlayerSongDetailState(), layout(FullPlayerCard.LYRICS)))
    }

    @Test
    fun `配置里隐藏的卡片不展示`() {
        val state = PlayerSongDetailState(lyrics = lyricLines)
        val cards = visibleInfoCards(state, layout(FullPlayerCard.LYRICS, hidden = setOf(FullPlayerCard.LYRICS)))
        assertEquals(emptyList<FullPlayerCard>(), cards)
    }

    @Test
    fun `前一张未出结论时后面的卡片即使数据先到也不展示`() {
        val order = layout(FullPlayerCard.SONG_DETAIL, FullPlayerCard.LYRICS)
        val loading = PlayerSongDetailState(lyrics = lyricLines, isSongWikiLoading = true)
        assertEquals(emptyList<FullPlayerCard>(), visibleInfoCards(loading, order, all))

        val settled = PlayerSongDetailState(lyrics = lyricLines, songWiki = SongWikiData())
        assertEquals(listOf(FullPlayerCard.SONG_DETAIL, FullPlayerCard.LYRICS), visibleInfoCards(settled, order, all))
    }

    @Test
    fun `前一张已出结论但无内容时不占位也不挡后面`() {
        val order = layout(FullPlayerCard.SONG_DETAIL, FullPlayerCard.LYRICS)
        val state = PlayerSongDetailState(lyrics = lyricLines, songWiki = null, isSongWikiLoading = false)
        assertEquals(listOf(FullPlayerCard.LYRICS), visibleInfoCards(state, order, all))
    }

    @Test
    fun `未实现的卡片不参与排序也不阻塞后面`() {
        val order = layout(FullPlayerCard.ABOUT_ARTIST, FullPlayerCard.LYRICS)
        val state = PlayerSongDetailState(lyrics = lyricLines, isSongWikiLoading = true)
        assertEquals(listOf(FullPlayerCard.LYRICS), visibleInfoCards(state, order, setOf(FullPlayerCard.LYRICS)))
    }

    @Test
    fun `本地未匹配歌曲只展示歌词`() {
        val order = layout(FullPlayerCard.SONG_DETAIL, FullPlayerCard.LYRICS)
        val state = PlayerSongDetailState(lyrics = lyricLines, songWiki = SongWikiData(), isLocalOnly = true)
        assertEquals(listOf(FullPlayerCard.LYRICS), visibleInfoCards(state, order, all))
    }

    @Test
    fun `酷狗曲目歌词与艺人数据就绪后展示歌词关于艺人与艺人专辑`() {
        val order = layout(FullPlayerCard.LYRICS, FullPlayerCard.ABOUT_ARTIST, FullPlayerCard.ARTIST_ALBUMS)
        val state = PlayerSongDetailState(
            lyrics = lyricLines,
            isLocalOnly = true,
            isExternal = true,
            artists = listOf(
                com.lin0721.linmusic.feature.player.ui.ArtistCardItem(
                    artistId = 11L,
                    artistName = "周杰伦",
                    artistDetail = com.lin0721.linmusic.core.model.ArtistDetailInfo(id = 11L, name = "周杰伦", avatar = "https://x/a.jpg", briefDesc = "简介")
                )
            ),
            artistAlbums = listOf(com.lin0721.linmusic.core.model.ArtistAlbum(id = 200L, name = "十一月的萧邦"))
        )
        assertEquals(
            listOf(FullPlayerCard.LYRICS, FullPlayerCard.ABOUT_ARTIST, FullPlayerCard.ARTIST_ALBUMS),
            visibleInfoCards(state, order, all)
        )
    }
}
