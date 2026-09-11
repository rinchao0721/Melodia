package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import com.lin0721.linmusic.core.comment.ui.CommentsPreviewCard
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.ui.theme.PlayerBackdropPalette

// 播放器信息区：歌词卡、评论预览、歌曲详情、歌手简介、相似歌手、歌手专辑
fun LazyListScope.fullPlayerInfoSection(
    songState: PlayerSongDetailState,
    colors: PlayerBackdropPalette,
    commentsState: CommentsState,
    currentLyricIndex: Int,
    onOpenFullScreenLyrics: () -> Unit,
    onCommentsClick: () -> Unit,
    onRetryComments: () -> Unit,
    onFollowArtistClick: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit = {}
) {
    // 纯音乐没有可滚动歌词，不占位
    val lyrics = songState.lyrics
    val isPureMusic = lyrics.size == 1 && lyrics[0].text == "纯音乐"
    if (songState.isLyricsLoading || (lyrics.isNotEmpty() && !isPureMusic)) {
        item(key = "lyrics") {
            LyricsCard(
                lyrics = lyrics,
                currentIndex = currentLyricIndex,
                isLoading = songState.isLyricsLoading,
                base = colors.base,
                highlightColor = colors.textHighlight,
                onOpenFullScreen = onOpenFullScreenLyrics
            )
        }
    }

    item(key = "comments_preview") {
        CommentsPreviewCard(
            commentsState = commentsState,
            cardColor = MaterialTheme.colorScheme.surface,
            onClick = onCommentsClick,
            onRetry = onRetryComments
        )
    }

    item(key = "song_detail") {
        SongDetailCard(songWiki = songState.songWiki, songDetail = songState.songDetail, cardColor = MaterialTheme.colorScheme.surface)
    }

    item(key = "about_artist") {
        val isArtistFollowed = songState.isArtistFollowed
        AboutArtistCard(
            artistDetail = songState.artistDetail,
            fansCount = songState.artistFansCount,
            isFollowed = isArtistFollowed,
            onFollowClick = onFollowArtistClick,
            cardColor = MaterialTheme.colorScheme.surface,
            onClick = {
                songState.artistDetail?.id?.let { id ->
                    onArtistClick(id)
                }
            }
        )
    }

    item(key = "similar_artists") {
        SimilarArtistsCard(
            artists = songState.similarArtists,
            isLoading = songState.isSimilarArtistsLoading,
            cardColor = MaterialTheme.colorScheme.surface,
            onArtistClick = onArtistClick
        )
    }

    item(key = "artist_albums") {
        ArtistAlbumsCard(
            albums = songState.artistAlbums,
            artistName = songState.artistDetail?.name,
            cardColor = MaterialTheme.colorScheme.surface,
            onAlbumClick = onAlbumClick
        )
    }
}
