package com.lin0721.linmusic.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lin0721.linmusic.core.model.PlaybackQuality
import com.lin0721.linmusic.feature.listentogether.ui.ListenTogetherPlayerEntry
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectSheet
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape

// 全屏播放器的底部弹层：播放队列、更多操作、收藏到歌单、睡眠定时、连接设备、私人FM模式、音质详情
@Composable
fun FullPlayerSheets(
    songState: PlayerSongDetailState,
    showQueueSheet: Boolean,
    showMoreOptionsSheet: Boolean,
    collectSongId: Long?,
    collectState: PlaylistCollectState,
    showTimerSheet: Boolean,
    showOutputDeviceSheet: Boolean,
    queue: List<QueueItem>,
    currentQueueIndex: Int,
    playMode: PlayMode,
    playContext: String?,
    isPlaying: Boolean,
    title: String,
    artist: String,
    coverUrl: String,
    sleepTimerRemaining: Long,
    activeQuality: String,
    showMiniLyric: Boolean = true,
    isPodcast: Boolean = false,
    personalFmMode: PersonalFmMode = PersonalFmMode.DEFAULT,
    showPersonalFmModeSheet: Boolean = false,
    qualityDetail: PlaybackQuality? = null,
    onQualityDetailDismiss: () -> Unit = {},
    onToggleMiniLyric: (Boolean) -> Unit = {},
    onPlayAtIndex: (Int) -> Unit,
    onRemoveSong: (Long) -> Boolean,
    onMoveQueueItem: (from: Int, to: Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onClearQueue: () -> Unit,
    onDisableRoaming: () -> Unit,
    onDisableIntelligence: () -> Unit,
    onDisablePersonalFm: () -> Unit = {},
    onPersonalFmModeClick: () -> Unit = {},
    onSelectPersonalFmMode: (PersonalFmMode) -> Unit = {},
    onPersonalFmModeDismiss: () -> Unit = {},
    onQueueDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onAlbumClick: () -> Unit,
    onArtistClick: () -> Unit,
    onShowTimerClick: () -> Unit,
    onQualitySelected: (String) -> Unit,
    onToggleIntelligence: (Boolean) -> Unit,
    onStartSimilarRoaming: () -> Unit,
    onInsertSimilarSongs: () -> Unit,
    onCollectClick: () -> Unit,
    onShareClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onSaveCollection: (Long, List<PlaylistCollectItem>) -> Unit,
    onSaveNewCollection: (String, Long) -> Unit,
    onCollectDismiss: () -> Unit,
    onMoreOptionsDismiss: () -> Unit,
    onSetTimer: (Int) -> Unit,
    onTimerDismiss: () -> Unit,
    onOutputDeviceSelected: (Int) -> Unit,
    onOutputDeviceDismiss: () -> Unit
) {
    if (showQueueSheet) {
        PlayQueueSheet(
            queue = queue,
            currentIndex = currentQueueIndex,
            playMode = playMode,
            playContext = playContext,
            isPlaying = isPlaying,
            sleepTimerRemaining = sleepTimerRemaining,
            onPlayAtIndex = onPlayAtIndex,
            onRemoveSong = onRemoveSong,
            onMoveItem = onMoveQueueItem,
            onToggleShuffle = onToggleShuffle,
            onClearQueue = onClearQueue,
            onDisableRoaming = onDisableRoaming,
            onDisableIntelligence = onDisableIntelligence,
            personalFmMode = personalFmMode,
            onDisablePersonalFm = onDisablePersonalFm,
            onPersonalFmModeClick = onPersonalFmModeClick,
            onShowTimerClick = onShowTimerClick,
            onDismiss = onQueueDismiss
        )
    }

    var listenTogetherRequested by remember { mutableStateOf(false) }
    ListenTogetherPlayerEntry(
        requested = listenTogetherRequested,
        onRequestHandled = { listenTogetherRequested = false }
    )

    if (showMoreOptionsSheet) {
        val albumName = songState.songDetail?.al?.name ?: songState.songWiki?.album ?: "未知专辑"
        val currentSongId = songState.songDetail?.id
        SongMoreOptionsSheet(
            title = title,
            artist = artist,
            coverUrl = coverUrl,
            albumName = albumName,
            isLiked = songState.isLiked,
            sleepTimerRemaining = sleepTimerRemaining,
            currentQuality = activeQuality,
            isLocalOnly = songState.isLocalOnly,
            songId = currentSongId,
            isPodcast = isPodcast,
            showMiniLyric = showMiniLyric,
            onToggleMiniLyric = onToggleMiniLyric,
            onToggleLike = onToggleLike,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onShowTimerClick = onShowTimerClick,
            onQualitySelected = onQualitySelected,
            isIntelligence = playContext == "intelligence",
            onToggleIntelligence = onToggleIntelligence,
            onStartSimilarRoaming = onStartSimilarRoaming,
            onInsertSimilarSongs = onInsertSimilarSongs,
            onCollectClick = onCollectClick,
            onShareClick = onShareClick,
            onDownloadClick = onDownloadClick,
            onDismiss = onMoreOptionsDismiss,
            onListenTogetherClick = { listenTogetherRequested = true },
            isPersonalFm = playContext == PlaybackController.CONTEXT_PERSONAL_FM,
            personalFmMode = personalFmMode,
            onPersonalFmModeClick = onPersonalFmModeClick,
            onDisablePersonalFm = onDisablePersonalFm
        )
    }

    if (collectSongId != null) {
        PlaylistCollectSheet(
            songId = collectSongId,
            collectState = collectState,
            onDismiss = onCollectDismiss,
            onSaveCollection = onSaveCollection,
            onSaveNewCollection = onSaveNewCollection,
            sheetShape = BottomSheetShape
        )
    }

    if (showTimerSheet) {
        SleepTimerSheet(
            sleepTimerRemaining = sleepTimerRemaining,
            onSetTimer = onSetTimer,
            onDismiss = onTimerDismiss
        )
    }

    if (showOutputDeviceSheet) {
        OutputDeviceSheet(
            onDeviceSelected = onOutputDeviceSelected,
            onDismiss = onOutputDeviceDismiss
        )
    }

    if (showPersonalFmModeSheet) {
        PersonalFmModeSheet(
            currentMode = personalFmMode,
            onSelectMode = onSelectPersonalFmMode,
            onDismiss = onPersonalFmModeDismiss
        )
    }

    if (qualityDetail != null) {
        PlaybackQualityDetailSheet(
            quality = qualityDetail,
            onDismiss = onQualityDetailDismiss
        )
    }
}
