package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lin0721.linmusic.core.comment.ui.CommentsPreviewCard
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.preferences.FullPlayerCard
import com.lin0721.linmusic.core.preferences.FullPlayerCardSetting
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PlayerBackdropPalette

// 播放器信息区：歌词卡、评论预览、歌曲详情、歌手简介、歌手专辑、相似歌手
// 卡片按用户配置的顺序展示：前一张可见卡片还没出结论（在加载中）时，
// 后面的卡片哪怕数据先到也一律不展示，避免顺序被网络到达时机打乱、插到已展示内容上方
fun LazyListScope.fullPlayerInfoSection(
    songState: PlayerSongDetailState,
    colors: PlayerBackdropPalette,
    commentsState: CommentsState,
    currentLyricIndex: Int,
    cardLayout: List<FullPlayerCardSetting>,
    onOpenFullScreenLyrics: () -> Unit,
    onCommentsClick: () -> Unit,
    onRetryComments: () -> Unit,
    onFollowArtistClick: (Long?) -> Unit,
    onArtistClick: (Long) -> Unit,
    onEditCardsClick: () -> Unit,
    onAlbumClick: (Long) -> Unit = {},
    onSelectArtist: (Int) -> Unit = {}
) {
    val lyrics = songState.lyrics
    val isPureMusic = lyrics.size == 1 && lyrics[0].text == "纯音乐"
    val songWiki = songState.songWiki
    val artistDetail = songState.artistDetail

    // 每格 (是否已出结论, 结论是否要展示)
    fun slotState(card: FullPlayerCard): Pair<Boolean, Boolean> = when (card) {
        FullPlayerCard.LYRICS -> Pair(!songState.isLyricsLoading, lyrics.isNotEmpty() && !isPureMusic)
        FullPlayerCard.COMMENTS_PREVIEW -> Pair(commentsState !is CommentsState.Loading, true)
        FullPlayerCard.SONG_DETAIL -> Pair(!songState.isSongWikiLoading, songWiki != null)
        FullPlayerCard.ABOUT_ARTIST -> Pair(!songState.isArtistDetailLoading, artistDetail != null || songState.artists.any { it.artistDetail != null })
        FullPlayerCard.ARTIST_ALBUMS -> Pair(!songState.isArtistAlbumsLoading, songState.artistAlbums.isNotEmpty())
        FullPlayerCard.SIMILAR_ARTISTS -> Pair(!songState.isSimilarArtistsLoading, songState.similarArtists.isNotEmpty())
    }

    var allSettled = true
    for (setting in cardLayout) {
        if (!setting.visible) continue
        val (settled, ready) = slotState(setting.card)
        if (!settled) {
            allSettled = false
            break
        }
        if (!ready) continue
        when (setting.card) {
            FullPlayerCard.LYRICS -> item(key = "lyrics") {
                FullPlayerItemEnterAnimation {
                    LyricsCard(
                        lyrics = lyrics,
                        currentIndex = currentLyricIndex,
                        isLoading = false,
                        base = colors.base,
                        onOpenFullScreen = onOpenFullScreenLyrics
                    )
                }
            }
            FullPlayerCard.COMMENTS_PREVIEW -> item(key = "comments_preview") {
                FullPlayerItemEnterAnimation {
                    CommentsPreviewCard(
                        commentsState = commentsState,
                        cardColor = MaterialTheme.colorScheme.surface,
                        onClick = onCommentsClick,
                        onRetry = onRetryComments
                    )
                }
            }
            FullPlayerCard.SONG_DETAIL -> item(key = "song_detail") {
                FullPlayerItemEnterAnimation {
                    SongDetailCard(
                        songWiki = songWiki,
                        songDetail = songState.songDetail,
                        cardColor = MaterialTheme.colorScheme.surface
                    )
                }
            }
            FullPlayerCard.ABOUT_ARTIST -> if (songState.artists.isNotEmpty()) {
                item(key = "about_artist") {
                    FullPlayerItemEnterAnimation {
                        AboutArtistPagerCard(
                            artists = songState.artists,
                            selectedIndex = songState.selectedArtistIndex,
                            onSelectArtist = onSelectArtist,
                            onFollowArtistClick = { artistId -> onFollowArtistClick(artistId) },
                            cardColor = MaterialTheme.colorScheme.surface,
                            onArtistClick = onArtistClick
                        )
                    }
                }
            } else if (artistDetail != null) {
                item(key = "about_artist") {
                    FullPlayerItemEnterAnimation {
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
            }
            FullPlayerCard.ARTIST_ALBUMS -> item(key = "artist_albums") {
                FullPlayerItemEnterAnimation {
                    ArtistAlbumsCard(
                        albums = songState.artistAlbums,
                        artistName = songState.currentArtistItem?.artistName ?: songState.artistDetail?.name,
                        cardColor = MaterialTheme.colorScheme.surface,
                        onAlbumClick = onAlbumClick
                    )
                }
            }
            FullPlayerCard.SIMILAR_ARTISTS -> item(key = "similar_artists") {
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

    // 等可见卡片全部出结论再出现，避免按钮先露出又被后到的卡片顶下去
    if (allSettled) {
        item(key = "edit_cards") {
            FullPlayerItemEnterAnimation {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MelodiaSpacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    PlayerCapsuleButton(text = "编辑卡片", onClick = onEditCardsClick)
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
