package com.lin0721.linmusic.feature.player.ui

import android.media.AudioDeviceInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.LyricsSource
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.plugin.LayoutNodeType
import com.lin0721.linmusic.core.plugin.PlayerLayoutNode
import com.lin0721.linmusic.core.plugin.PlayerLayoutView
import com.lin0721.linmusic.core.plugin.layoutShape
import com.lin0721.linmusic.core.ui.components.SwipeToSkipCover
import com.lin0721.linmusic.core.ui.theme.ImmersiveTheme
import com.lin0721.linmusic.core.ui.theme.PlayerBackdropPalette
import com.lin0721.linmusic.feature.podcast.ui.PodcastPlayerState

// 全屏与宽屏播放页的插件布局：把布局节点映射到已有组件，状态与回调都来自 FullPlayerScreen
class FullPlayerLayoutParts(
    val title: String,
    val artist: String,
    val songState: PlayerSongDetailState,
    val podcast: PodcastPlayerState,
    val colors: PlayerBackdropPalette,
    val coverUrl: String,
    val previousCoverUrl: String?,
    val nextCoverUrl: String?,
    val currentKey: Any,
    val previousKey: Any?,
    val nextKey: Any?,
    val currentLyricIndex: Int,
    val isPlaying: Boolean,
    val playWhenReady: Boolean,
    val currentPositionProvider: () -> Long,
    val duration: Long,
    val playMode: PlayMode,
    val animateLyricsMesh: Boolean,
    val connectedDevice: AudioDeviceInfo?,
    val qualityLabel: String?,
    val onToggleLike: () -> Unit,
    val onToggleSubscribe: () -> Unit,
    val onArtistClick: () -> Unit,
    val onAlbumClick: (() -> Unit)?,
    val onMiniLyricClick: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onTogglePlay: () -> Unit,
    val onPlayNext: () -> Unit,
    val onPlayPrevious: () -> Unit,
    val onCancelSkip: () -> Boolean,
    val onToggleShuffle: () -> Unit,
    val onToggleRepeat: () -> Unit,
    val onOutputDeviceClick: () -> Unit,
    val onQueueClick: () -> Unit,
    val onShareClick: () -> Unit,
    val onMoreClick: () -> Unit,
    val onOpenFullScreenLyrics: () -> Unit
) {
    @Composable
    fun Component(node: PlayerLayoutNode, modifier: Modifier) {
        val scale = node.size.scale
        when (node.type) {
            LayoutNodeType.COVER -> {
                // 占比分配高度时以高度为准定宽，否则以宽度为准定高
                val byHeight = node.weight != null
                val limited = node.maxSize?.let { modifier.widthIn(max = it.dp) } ?: modifier
                SwipeToSkipCover(
                    coverUrl = coverUrl,
                    previousCoverUrl = previousCoverUrl,
                    nextCoverUrl = nextCoverUrl,
                    onConfirmPrevious = onPlayPrevious,
                    onConfirmNext = onPlayNext,
                    currentKey = currentKey,
                    previousKey = previousKey,
                    nextKey = nextKey,
                    contentScale = ContentScale.Crop,
                    shape = node.layoutShape(),
                    modifier = (if (byHeight) limited.fillMaxHeight() else limited.fillMaxWidth())
                        .aspectRatio(node.aspect, matchHeightConstraintsFirst = byHeight),
                    onCancelPending = onCancelSkip
                )
            }
            LayoutNodeType.SONG_TITLE -> SongTitleText(title, onAlbumClick, modifier, (22 * scale).sp, node.maxLines)
            LayoutNodeType.ARTIST -> SongArtistText(artist, onArtistClick, modifier, (15 * scale).sp)
            LayoutNodeType.SONG_INFO -> Column(modifier) {
                SongTitleText(title, onAlbumClick, Modifier, (22 * scale).sp, node.maxLines)
                SongArtistText(artist, onArtistClick, Modifier, (15 * scale).sp)
            }
            LayoutNodeType.LIKE_BUTTON -> SongLikeButton(songState.isLiked, onToggleLike, podcast, onToggleSubscribe, modifier)
            LayoutNodeType.MINI_LYRIC -> Box(modifier) {
                MiniLyricLine(
                    lyrics = songState.lyrics,
                    currentLyricIndex = currentLyricIndex,
                    isPlaying = isPlaying,
                    onClick = onMiniLyricClick
                )
            }
            LayoutNodeType.PROGRESS -> Box(modifier) {
                ProgressSection(
                    currentPositionProvider = currentPositionProvider,
                    duration = if (duration > 0L) duration else (songState.songDetail?.dt ?: 0L),
                    onSeek = onSeek,
                    chorusStartMs = songState.chorusStartMs,
                    showTime = node.showTime,
                    horizontalPadding = 0.dp
                )
            }
            LayoutNodeType.CONTROLS -> PlaybackControlRow(
                buttons = node.buttons,
                isPlaying = playWhenReady,
                onTogglePlay = onTogglePlay,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                playMode = playMode,
                showModeButtons = !podcast.isPodcast,
                modifier = modifier,
                scale = scale
            )
            LayoutNodeType.ACTIONS -> PlayerActionRow(
                buttons = node.buttons,
                onOutputDeviceClick = onOutputDeviceClick,
                onQueueClick = onQueueClick,
                onShareClick = onShareClick,
                onMoreClick = onMoreClick,
                connectedDevice = connectedDevice,
                qualityLabel = qualityLabel,
                modifier = modifier
            )
            // 歌词卡在浅色页面里保持有色底白字
            LayoutNodeType.LYRICS_CARD -> ImmersiveTheme {
                Box(modifier) {
                    LyricsCard(
                        lyrics = songState.lyrics,
                        currentIndex = currentLyricIndex,
                        isLoading = false,
                        base = colors.base,
                        animateMesh = animateLyricsMesh,
                        isAmll = songState.lyricsSource == LyricsSource.AMLL,
                        isQrc = songState.lyricsSource == LyricsSource.QQ && songState.lyrics.any { it.words.isNotEmpty() },
                        onOpenFullScreen = onOpenFullScreenLyrics
                    )
                }
            }
            else -> Unit
        }
    }
}

// 插件布局的首屏：整体作为一个视口高度的列表项，key 沿用 cover，滚动指标与背景位移照常工作
fun LazyListScope.fullPlayerPluginSection(
    root: PlayerLayoutNode,
    parts: FullPlayerLayoutParts,
    sourceBar: @Composable (Modifier) -> Unit
) {
    item(key = "cover") {
        FullPlayerItemEnterAnimation {
            Column(Modifier.fillParentMaxHeight()) {
                sourceBar(Modifier.fillMaxWidth())
                PlayerLayoutView(root, Modifier.weight(1f).fillMaxWidth()) { node, modifier ->
                    parts.Component(node, modifier)
                }
            }
        }
    }
}
