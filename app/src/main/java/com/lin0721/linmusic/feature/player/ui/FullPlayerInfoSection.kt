package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lin0721.linmusic.core.comment.ui.CommentsPreviewCard
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.ui.theme.PlayerBackdropPalette

private const val CARD_LYRICS = "lyrics"
private const val CARD_COMMENTS = "comments_preview"
private const val CARD_SONG_DETAIL = "song_detail"
private const val CARD_ABOUT_ARTIST = "about_artist"
private const val CARD_ARTIST_ALBUMS = "artist_albums"
private const val CARD_SIMILAR_ARTISTS = "similar_artists"

// 除单行歌词外，卡片固定按此顺序展示：前一张卡片还没出结论（在加载中）时，
// 后面的卡片哪怕数据先到也一律不展示，避免顺序被网络到达时机打乱、插到已展示内容上方
private fun visibleInfoCardKeys(
    songState: PlayerSongDetailState,
    commentsState: CommentsState
): List<String> {
    val lyrics = songState.lyrics
    val isPureMusic = lyrics.size == 1 && lyrics[0].text == "纯音乐"

    // 每格 (是否已出结论, 结论是否要展示)，严格按声明顺序逐个放行
    val slots = listOf(
        CARD_LYRICS to Pair(!songState.isLyricsLoading, lyrics.isNotEmpty() && !isPureMusic),
        CARD_COMMENTS to Pair(commentsState !is CommentsState.Loading, true),
        CARD_SONG_DETAIL to Pair(!songState.isSongWikiLoading, songState.songWiki != null),
        CARD_ABOUT_ARTIST to Pair(
            !songState.isArtistDetailLoading,
            songState.artistDetail != null || songState.artists.any { it.artistDetail != null }
        ),
        CARD_ARTIST_ALBUMS to Pair(!songState.isArtistAlbumsLoading, songState.artistAlbums.isNotEmpty()),
        CARD_SIMILAR_ARTISTS to Pair(!songState.isSimilarArtistsLoading, songState.similarArtists.isNotEmpty())
    )

    val keys = mutableListOf<String>()
    for ((key, state) in slots) {
        val (settled, ready) = state
        if (!settled) break
        if (ready) keys += key
    }
    return keys
}

// 播放器信息区（竖排）：歌词卡、评论预览、歌曲详情、歌手简介、歌手专辑、相似歌手
fun LazyListScope.fullPlayerInfoSection(
    songState: PlayerSongDetailState,
    colors: PlayerBackdropPalette,
    commentsState: CommentsState,
    currentLyricIndex: Int,
    onOpenFullScreenLyrics: () -> Unit,
    onCommentsClick: () -> Unit,
    onRetryComments: () -> Unit,
    onFollowArtistClick: (Long?) -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit = {},
    onSelectArtist: (Int) -> Unit = {}
) {
    for (key in visibleInfoCardKeys(songState, commentsState)) {
        item(key = key) {
            FullPlayerItemEnterAnimation {
                FullPlayerInfoCard(
                    key = key,
                    songState = songState,
                    colors = colors,
                    commentsState = commentsState,
                    currentLyricIndex = currentLyricIndex,
                    onOpenFullScreenLyrics = onOpenFullScreenLyrics,
                    onCommentsClick = onCommentsClick,
                    onRetryComments = onRetryComments,
                    onFollowArtistClick = onFollowArtistClick,
                    onArtistClick = onArtistClick,
                    onAlbumClick = onAlbumClick,
                    onSelectArtist = onSelectArtist
                )
            }
        }
    }
}

// 播放器信息区（宽屏两列网格）：评论占满一行；关于艺人与歌曲信息并排；专辑、相似艺人各占满一行。
// 出现规则与竖排一致，歌词卡不展示（宽屏已有完整歌词区）
fun LazyListScope.fullPlayerInfoGrid(
    songState: PlayerSongDetailState,
    colors: PlayerBackdropPalette,
    commentsState: CommentsState,
    onCommentsClick: () -> Unit,
    onRetryComments: () -> Unit,
    onFollowArtistClick: (Long?) -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onSelectArtist: (Int) -> Unit
) {
    val keys = visibleInfoCardKeys(songState, commentsState)
    val rows = buildList {
        if (CARD_COMMENTS in keys) add(listOf(CARD_COMMENTS))
        val pairRow = listOf(CARD_ABOUT_ARTIST, CARD_SONG_DETAIL).filter { it in keys }
        if (pairRow.isNotEmpty()) add(pairRow)
        if (CARD_ARTIST_ALBUMS in keys) add(listOf(CARD_ARTIST_ALBUMS))
        if (CARD_SIMILAR_ARTISTS in keys) add(listOf(CARD_SIMILAR_ARTISTS))
    }

    for (row in rows) {
        // 并排行的 key 固定，另一张卡后到时原地补位，不整行重新入场
        val rowKey = if (CARD_ABOUT_ARTIST in row || CARD_SONG_DETAIL in row) "grid_artist_detail" else "grid_${row.first()}"
        item(key = rowKey) {
            FullPlayerItemEnterAnimation {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    for (key in row) {
                        Box(modifier = Modifier.weight(1f)) {
                            FullPlayerInfoCard(
                                key = key,
                                songState = songState,
                                colors = colors,
                                commentsState = commentsState,
                                currentLyricIndex = -1,
                                onOpenFullScreenLyrics = {},
                                onCommentsClick = onCommentsClick,
                                onRetryComments = onRetryComments,
                                onFollowArtistClick = onFollowArtistClick,
                                onArtistClick = onArtistClick,
                                onAlbumClick = onAlbumClick,
                                onSelectArtist = onSelectArtist
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullPlayerInfoCard(
    key: String,
    songState: PlayerSongDetailState,
    colors: PlayerBackdropPalette,
    commentsState: CommentsState,
    currentLyricIndex: Int,
    onOpenFullScreenLyrics: () -> Unit,
    onCommentsClick: () -> Unit,
    onRetryComments: () -> Unit,
    onFollowArtistClick: (Long?) -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onSelectArtist: (Int) -> Unit
) {
    when (key) {
        CARD_LYRICS -> LyricsCard(
            lyrics = songState.lyrics,
            currentIndex = currentLyricIndex,
            isLoading = false,
            base = colors.base,
            onOpenFullScreen = onOpenFullScreenLyrics
        )
        CARD_COMMENTS -> CommentsPreviewCard(
            commentsState = commentsState,
            cardColor = MaterialTheme.colorScheme.surface,
            onClick = onCommentsClick,
            onRetry = onRetryComments
        )
        CARD_SONG_DETAIL -> SongDetailCard(
            songWiki = songState.songWiki,
            songDetail = songState.songDetail,
            cardColor = MaterialTheme.colorScheme.surface
        )
        CARD_ABOUT_ARTIST -> {
            val artistDetail = songState.artistDetail
            if (songState.artists.isNotEmpty()) {
                AboutArtistPagerCard(
                    artists = songState.artists,
                    selectedIndex = songState.selectedArtistIndex,
                    onSelectArtist = onSelectArtist,
                    onFollowArtistClick = { artistId -> onFollowArtistClick(artistId) },
                    cardColor = MaterialTheme.colorScheme.surface,
                    onArtistClick = onArtistClick
                )
            } else if (artistDetail != null) {
                AboutArtistCard(
                    artistDetail = artistDetail,
                    fansCount = songState.artistFansCount,
                    isFollowed = songState.isArtistFollowed,
                    onFollowClick = { onFollowArtistClick(artistDetail.id) },
                    cardColor = MaterialTheme.colorScheme.surface,
                    onClick = { onArtistClick(artistDetail.id) }
                )
            }
        }
        CARD_ARTIST_ALBUMS -> ArtistAlbumsCard(
            albums = songState.artistAlbums,
            artistName = songState.currentArtistItem?.artistName ?: songState.artistDetail?.name,
            cardColor = MaterialTheme.colorScheme.surface,
            onAlbumClick = onAlbumClick
        )
        CARD_SIMILAR_ARTISTS -> SimilarArtistsCard(
            artists = songState.similarArtists,
            isLoading = false,
            cardColor = MaterialTheme.colorScheme.surface,
            onArtistClick = onArtistClick
        )
    }
}

// 全屏播放页各列表项的入场动效：淡入 + 轻微上移；同时用 animateItem() 让下方内容跟着平滑让位。
// 卡片与大封面共用同一套动效，保持整页打开时的视觉节奏一致
@Composable
fun LazyItemScope.FullPlayerItemEnterAnimation(content: @Composable () -> Unit) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = true
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 6 },
        modifier = Modifier.animateItem()
    ) {
        content()
    }
}
