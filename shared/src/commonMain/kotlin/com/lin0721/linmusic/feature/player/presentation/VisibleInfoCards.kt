package com.lin0721.linmusic.feature.player.presentation

import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.preferences.FullPlayerCard
import com.lin0721.linmusic.core.preferences.FullPlayerCardSetting
import com.lin0721.linmusic.feature.player.ui.PlayerSongDetailState

// 按用户配置的顺序与显隐算出当前要展示的卡片：前一张可见卡片还没出结论（在加载中）时，
// 后面的卡片哪怕数据先到也一律不展示，避免顺序被网络到达时机打乱、插到已展示内容上方。
// supported 为当前端已实现的卡片，未实现的卡片不参与出现顺序，避免它们的加载状态挡住后面的卡片
fun visibleInfoCards(
    songState: PlayerSongDetailState,
    cardLayout: List<FullPlayerCardSetting>,
    supported: Set<FullPlayerCard> = FullPlayerCard.entries.toSet(),
    commentsState: CommentsState = CommentsState.Success(emptyList(), emptyList(), 0)
): List<FullPlayerCard> {
    val lyrics = songState.lyrics
    val isPureMusic = lyrics.size == 1 && lyrics[0].text == "纯音乐"

    // 每张卡 (是否已出结论, 结论是否要展示)
    fun slotState(card: FullPlayerCard): Pair<Boolean, Boolean> = when (card) {
        FullPlayerCard.LYRICS -> Pair(!songState.isLyricsLoading, lyrics.isNotEmpty() && !isPureMusic)
        FullPlayerCard.COMMENTS_PREVIEW -> Pair(commentsState !is CommentsState.Loading, true)
        FullPlayerCard.SONG_DETAIL -> Pair(!songState.isSongWikiLoading, songState.songWiki != null)
        FullPlayerCard.MUSIC_MEMORY -> Pair(
            !songState.isSongWikiLoading,
            songState.songWiki?.musicMemory?.let(::hasMusicMemoryContent) == true
        )
        FullPlayerCard.ABOUT_ARTIST -> Pair(
            !songState.isArtistDetailLoading,
            validAboutArtists(songState.artists).isNotEmpty()
        )
        FullPlayerCard.ARTIST_ALBUMS -> Pair(!songState.isArtistAlbumsLoading, songState.artistAlbums.isNotEmpty())
        FullPlayerCard.SIMILAR_ARTISTS -> Pair(!songState.isSimilarArtistsLoading, songState.similarArtists.isNotEmpty())
    }

    val cards = mutableListOf<FullPlayerCard>()
    for (setting in cardLayout) {
        if (!setting.visible || setting.card !in supported) continue
        // 本地未匹配歌曲不请求在线数据，只展示歌词；外部平台歌曲另有 QQ 音乐评论
        if (songState.isLocalOnly && setting.card != FullPlayerCard.LYRICS &&
            !(songState.isExternal && setting.card == FullPlayerCard.COMMENTS_PREVIEW)
        ) continue
        val (settled, ready) = slotState(setting.card)
        if (!settled) return cards
        if (ready) cards += setting.card
    }
    return cards
}
