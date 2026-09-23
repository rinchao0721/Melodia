package com.lin0721.linmusic.feature.player.ui

import android.media.AudioDeviceInfo
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.ui.theme.PlayerBackdropPalette

// 播放器主区之间的留白项个数，与下方 fillGapItem 调用次数保持一致
const val FullPlayerFillGapCount = 4

// 撑满一屏时参与高度计算的主区项，需与下方 item key 保持一致
val FullPlayerPlaybackItemKeys = listOf("cover", "song_info", "mini_lyric", "progress", "controls", "actions")

// 播放器主区：封面、歌名歌手、单行歌词、进度条、播放控制、快捷操作
// fillGap 非空时在各区块间插入等高留白，并在末尾补 fillTail 高度，使主区恰好铺满首屏、后续项留在屏外
fun LazyListScope.fullPlayerPlaybackSection(
    songState: PlayerSongDetailState,
    colors: PlayerBackdropPalette,
    coverUrl: String,
    previousCoverUrl: String?,
    nextCoverUrl: String?,
    onSwipeToPrevious: () -> Unit,
    onCancelSwipe: () -> Boolean,
    currentKey: Any,
    previousKey: Any? = null,
    nextKey: Any? = null,
    title: String,
    artist: String,
    playContext: String?,
    currentLyricIndex: Int,
    isPlaying: Boolean,
    // 大播放按钮专用：弱网缓冲期间也要立刻显示"暂停中"，不受歌词区仍用的严格 isPlaying 影响
    playWhenReady: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    playMode: PlayMode,
    onClose: () -> Unit,
    onPaletteExtracted: (PlayerBackdropPalette) -> Unit,
    onMoreClick: () -> Unit,
    onToggleLike: () -> Unit,
    onArtistClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onDisableRoaming: () -> Unit,
    onDisableIntelligence: () -> Unit,
    onOutputDeviceClick: () -> Unit,
    onQueueClick: () -> Unit,
    onShareClick: () -> Unit,
    connectedDevice: AudioDeviceInfo? = null,
    fillGap: Dp? = null,
    fillTail: Dp = 0.dp
) {
    fun fillGapItem(index: Int) {
        if (fillGap == null) return
        item(key = "$FullPlayerFillGapKeyPrefix$index") {
            Spacer(modifier = Modifier.height(fillGap))
        }
    }

    item(key = "cover") {
        FullPlayerItemEnterAnimation {
            FullPlayerCoverArt(
                coverUrl = coverUrl,
                title = title,
                playContext = playContext,
                onClose = onClose,
                onPaletteExtracted = onPaletteExtracted,
                onMoreClick = onMoreClick,
                previousCoverUrl = previousCoverUrl,
                nextCoverUrl = nextCoverUrl,
                onSwipeToPrevious = onSwipeToPrevious,
                onSwipeToNext = onPlayNext,
                currentKey = currentKey,
                previousKey = previousKey,
                nextKey = nextKey,
                onCancelSwipe = onCancelSwipe
            )
        }
    }

    fillGapItem(1)

    item(key = "song_info") {
        SongInfo(
            title = title,
            artist = artist,
            isLiked = songState.isLiked,
            onToggleLike = onToggleLike,
            onArtistClick = onArtistClick
        )
    }

    item(key = "mini_lyric") {
        MiniLyricLine(
            lyrics = songState.lyrics,
            currentLyricIndex = currentLyricIndex,
            isPlaying = isPlaying
        )
    }

    fillGapItem(2)

    item(key = "progress") {
        // 播放器尚未拿到时长时退回歌曲详情里的时长
        val displayDuration = if (duration > 0L) duration else (songState.songDetail?.dt ?: 0L)
        ProgressSection(
            currentPositionProvider = currentPositionProvider,
            duration = displayDuration,
            onSeek = onSeek
        )
    }

    fillGapItem(3)

    item(key = "controls") {
        PlaybackControls(
            isPlaying = playWhenReady,
            onTogglePlay = onTogglePlay,
            onPlayNext = onPlayNext,
            onPlayPrevious = onPlayPrevious,
            onToggleShuffle = onToggleShuffle,
            onToggleRepeat = onToggleRepeat,
            playMode = playMode,
            isRoaming = playContext == "similar_roaming",
            onDisableRoaming = onDisableRoaming,
            isIntelligence = playContext == "intelligence",
            onDisableIntelligence = onDisableIntelligence
        )
    }

    fillGapItem(4)

    item(key = "actions") {
        ActionButtons(
            onOutputDeviceClick = onOutputDeviceClick,
            onQueueClick = onQueueClick,
            onShareClick = onShareClick,
            connectedDevice = connectedDevice
        )
    }

    if (fillGap != null) {
        item(key = "${FullPlayerFillGapKeyPrefix}tail") {
            Spacer(modifier = Modifier.height(fillTail))
        }
    }
}
