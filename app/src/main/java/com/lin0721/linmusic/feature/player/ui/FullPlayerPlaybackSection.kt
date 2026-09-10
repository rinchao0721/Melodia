package com.lin0721.linmusic.feature.player.ui

import android.media.AudioDeviceInfo
import androidx.compose.foundation.lazy.LazyListScope
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.ui.theme.PlayerBackdropPalette

// 播放器主区：封面、歌名歌手、单行歌词、进度条、播放控制、快捷操作
fun LazyListScope.fullPlayerPlaybackSection(
    songState: PlayerSongDetailState,
    colors: PlayerBackdropPalette,
    coverUrl: String,
    previousCoverUrl: String?,
    nextCoverUrl: String?,
    onSwipeToPrevious: () -> Unit,
    currentKey: Any,
    title: String,
    artist: String,
    playContext: String?,
    currentLyricIndex: Int,
    isPlaying: Boolean,
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
    connectedDevice: AudioDeviceInfo? = null
) {
    item(key = "cover") {
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
            currentKey = currentKey
        )
    }

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

    item(key = "progress") {
        // 播放器尚未拿到时长时退回歌曲详情里的时长
        val displayDuration = if (duration > 0L) duration else (songState.songDetail?.dt ?: 0L)
        ProgressSection(
            currentPositionProvider = currentPositionProvider,
            duration = displayDuration,
            onSeek = onSeek
        )
    }

    item(key = "controls") {
        PlaybackControls(
            isPlaying = isPlaying,
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

    item(key = "actions") {
        ActionButtons(
            onOutputDeviceClick = onOutputDeviceClick,
            onQueueClick = onQueueClick,
            onShareClick = onShareClick,
            connectedDevice = connectedDevice
        )
    }
}
