package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lin0721.linmusic.core.comment.ui.CommentsPreviewCard
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.ui.theme.PlayerBackdropPalette

// 播放器信息区：歌词卡、评论预览、歌曲详情、歌手简介、歌手专辑、相似歌手
// 除单行歌词外，以下卡片固定按此顺序展示：前一张卡片还没出结论（在加载中）时，
// 后面的卡片哪怕数据先到也一律不展示，避免顺序被网络到达时机打乱、插到已展示内容上方
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
    val lyrics = songState.lyrics
    val isPureMusic = lyrics.size == 1 && lyrics[0].text == "纯音乐"
    val songWiki = songState.songWiki
    val artistDetail = songState.artistDetail

    // 每格 (是否已出结论, 结论是否要展示)，严格按声明顺序逐个放行
    val slots = listOf(
        "lyrics" to Pair(!songState.isLyricsLoading, lyrics.isNotEmpty() && !isPureMusic),
        "comments_preview" to Pair(commentsState !is CommentsState.Loading, true),
        "song_detail" to Pair(!songState.isSongWikiLoading, songWiki != null),
        "about_artist" to Pair(!songState.isArtistDetailLoading, artistDetail != null),
        "artist_albums" to Pair(!songState.isArtistAlbumsLoading, songState.artistAlbums.isNotEmpty()),
        "similar_artists" to Pair(!songState.isSimilarArtistsLoading, songState.similarArtists.isNotEmpty())
    )

    for ((key, state) in slots) {
        val (settled, ready) = state
        if (!settled) break
        if (!ready) continue
        when (key) {
            "lyrics" -> item(key = "lyrics") {
                FullPlayerItemEnterAnimation {
                    LyricsCard(
                        lyrics = lyrics,
                        currentIndex = currentLyricIndex,
                        isLoading = false,
                        base = colors.base,
                        highlightColor = colors.textHighlight,
                        onOpenFullScreen = onOpenFullScreenLyrics
                    )
                }
            }
            "comments_preview" -> item(key = "comments_preview") {
                FullPlayerItemEnterAnimation {
                    CommentsPreviewCard(
                        commentsState = commentsState,
                        cardColor = MaterialTheme.colorScheme.surface,
                        onClick = onCommentsClick,
                        onRetry = onRetryComments
                    )
                }
            }
            "song_detail" -> item(key = "song_detail") {
                FullPlayerItemEnterAnimation {
                    SongDetailCard(
                        songWiki = songWiki,
                        songDetail = songState.songDetail,
                        cardColor = MaterialTheme.colorScheme.surface
                    )
                }
            }
            "about_artist" -> if (artistDetail != null) {
                item(key = "about_artist") {
                    FullPlayerItemEnterAnimation {
                        AboutArtistCard(
                            artistDetail = artistDetail,
                            fansCount = songState.artistFansCount,
                            isFollowed = songState.isArtistFollowed,
                            onFollowClick = onFollowArtistClick,
                            cardColor = MaterialTheme.colorScheme.surface,
                            onClick = { onArtistClick(artistDetail.id) }
                        )
                    }
                }
            }
            "artist_albums" -> item(key = "artist_albums") {
                FullPlayerItemEnterAnimation {
                    ArtistAlbumsCard(
                        albums = songState.artistAlbums,
                        artistName = songState.artistDetail?.name,
                        cardColor = MaterialTheme.colorScheme.surface,
                        onAlbumClick = onAlbumClick
                    )
                }
            }
            "similar_artists" -> item(key = "similar_artists") {
                FullPlayerItemEnterAnimation {
                    SimilarArtistsCard(
                        artists = songState.similarArtists,
                        isLoading = false,
                        cardColor = MaterialTheme.colorScheme.surface,
                        onArtistClick = onArtistClick
                    )
                }
            }
        }
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
