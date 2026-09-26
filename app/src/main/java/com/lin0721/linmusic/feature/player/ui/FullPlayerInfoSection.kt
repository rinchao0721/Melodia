package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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

private const val EDIT_CARDS_KEY = "edit_cards"

// 按用户配置的顺序与显隐算出当前要展示的卡片：前一张可见卡片还没出结论（在加载中）时，
// 后面的卡片哪怕数据先到也一律不展示，避免顺序被网络到达时机打乱、插到已展示内容上方
private data class VisibleInfoCards(val cards: List<FullPlayerCard>, val allSettled: Boolean)

private fun visibleInfoCards(
    songState: PlayerSongDetailState,
    commentsState: CommentsState,
    cardLayout: List<FullPlayerCardSetting>
): VisibleInfoCards {
    val lyrics = songState.lyrics
    val isPureMusic = lyrics.size == 1 && lyrics[0].text == "纯音乐"

    // 每格 (是否已出结论, 结论是否要展示)
    fun slotState(card: FullPlayerCard): Pair<Boolean, Boolean> = when (card) {
        FullPlayerCard.LYRICS -> Pair(!songState.isLyricsLoading, lyrics.isNotEmpty() && !isPureMusic)
        FullPlayerCard.COMMENTS_PREVIEW -> Pair(commentsState !is CommentsState.Loading, true)
        FullPlayerCard.SONG_DETAIL -> Pair(!songState.isSongWikiLoading, songState.songWiki != null)
        FullPlayerCard.MUSIC_MEMORY -> Pair(!songState.isSongWikiLoading, songState.songWiki?.musicMemory != null)
        FullPlayerCard.ABOUT_ARTIST -> Pair(
            !songState.isArtistDetailLoading,
            songState.artistDetail != null || songState.artists.any { it.artistDetail != null }
        )
        FullPlayerCard.ARTIST_ALBUMS -> Pair(!songState.isArtistAlbumsLoading, songState.artistAlbums.isNotEmpty())
        FullPlayerCard.SIMILAR_ARTISTS -> Pair(!songState.isSimilarArtistsLoading, songState.similarArtists.isNotEmpty())
    }

    val cards = mutableListOf<FullPlayerCard>()
    for (setting in cardLayout) {
        if (!setting.visible) continue
        val (settled, ready) = slotState(setting.card)
        if (!settled) return VisibleInfoCards(cards, allSettled = false)
        if (ready) cards += setting.card
    }
    return VisibleInfoCards(cards, allSettled = true)
}

// 播放器信息区（竖排）：歌词卡、评论预览、歌曲详情、回忆坐标、歌手简介、歌手专辑、相似歌手，按用户配置排序
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
    val visible = visibleInfoCards(songState, commentsState, cardLayout)
    for (card in visible.cards) {
        item(key = card.key) {
            FullPlayerItemEnterAnimation {
                FullPlayerInfoCard(
                    card = card,
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
    if (visible.allSettled) editCardsItem(onEditCardsClick)
}

// 播放器信息区（宽屏两列网格）：按用户配置排序，关于艺人与歌曲百科都可见时并排成一行、落在两者中靠前的位置，
// 其余卡片各占满一行。出现规则与竖排一致，歌词卡不展示（宽屏已有完整歌词区）
fun LazyListScope.fullPlayerInfoGrid(
    songState: PlayerSongDetailState,
    colors: PlayerBackdropPalette,
    commentsState: CommentsState,
    cardLayout: List<FullPlayerCardSetting>,
    onCommentsClick: () -> Unit,
    onRetryComments: () -> Unit,
    onFollowArtistClick: (Long?) -> Unit,
    onArtistClick: (Long) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onSelectArtist: (Int) -> Unit,
    onEditCardsClick: () -> Unit
) {
    val visible = visibleInfoCards(songState, commentsState, cardLayout)
    val cards = visible.cards - FullPlayerCard.LYRICS
    val pairedCards = listOf(FullPlayerCard.ABOUT_ARTIST, FullPlayerCard.SONG_DETAIL)
    val rows = buildList {
        var pairAdded = false
        for (card in cards) {
            if (card in pairedCards) {
                if (!pairAdded) {
                    add(cards.filter { it in pairedCards })
                    pairAdded = true
                }
            } else {
                add(listOf(card))
            }
        }
    }

    for (row in rows) {
        // 并排行的 key 固定，另一张卡后到时原地补位，不整行重新入场
        val rowKey = if (row.any { it in pairedCards }) "grid_artist_detail" else "grid_${row.first().key}"
        item(key = rowKey) {
            FullPlayerItemEnterAnimation {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    for (card in row) {
                        Box(modifier = Modifier.weight(1f)) {
                            FullPlayerInfoCard(
                                card = card,
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
    if (visible.allSettled) editCardsItem(onEditCardsClick)
}

// 等可见卡片全部出结论再出现，避免按钮先露出又被后到的卡片顶下去
private fun LazyListScope.editCardsItem(onEditCardsClick: () -> Unit) {
    item(key = EDIT_CARDS_KEY) {
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

@Composable
private fun FullPlayerInfoCard(
    card: FullPlayerCard,
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
    when (card) {
        FullPlayerCard.LYRICS -> LyricsCard(
            lyrics = songState.lyrics,
            currentIndex = currentLyricIndex,
            isLoading = false,
            base = colors.base,
            onOpenFullScreen = onOpenFullScreenLyrics
        )
        FullPlayerCard.COMMENTS_PREVIEW -> CommentsPreviewCard(
            commentsState = commentsState,
            cardColor = MaterialTheme.colorScheme.surface,
            onClick = onCommentsClick,
            onRetry = onRetryComments
        )
        FullPlayerCard.SONG_DETAIL -> SongDetailCard(
            songWiki = songState.songWiki,
            songDetail = songState.songDetail,
            cardColor = MaterialTheme.colorScheme.surface,
            onAlbumClick = onAlbumClick
        )
        FullPlayerCard.MUSIC_MEMORY -> songState.songWiki?.musicMemory?.let { memory ->
            MusicMemoryCard(
                memory = memory,
                cardColor = MaterialTheme.colorScheme.surface
            )
        }
        FullPlayerCard.ABOUT_ARTIST -> {
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
        FullPlayerCard.ARTIST_ALBUMS -> ArtistAlbumsCard(
            albums = songState.artistAlbums,
            artistName = songState.currentArtistItem?.artistName ?: songState.artistDetail?.name,
            cardColor = MaterialTheme.colorScheme.surface,
            onAlbumClick = onAlbumClick
        )
        FullPlayerCard.SIMILAR_ARTISTS -> SimilarArtistsCard(
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
