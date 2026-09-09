package com.lin0721.linmusic.feature.player.ui

import androidx.compose.runtime.Composable
import com.lin0721.linmusic.core.comment.ui.CommentsBottomSheet
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectSheet
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape

// 全屏播放器的底部弹层：播放队列、更多操作、收藏到歌单、睡眠定时、评论、连接设备
@Composable
fun FullPlayerSheets(
    songState: PlayerSongDetailState,
    showQueueSheet: Boolean,
    showMoreOptionsSheet: Boolean,
    collectSongId: Long?,
    collectState: PlaylistCollectState,
    showTimerSheet: Boolean,
    showCommentsSheet: Boolean,
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
    commentsState: CommentsState,
    onPlayAtIndex: (Int) -> Unit,
    onRemoveAtIndex: (Int) -> Unit,
    onMoveQueueItem: (from: Int, to: Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onClearQueue: () -> Unit,
    onDisableRoaming: () -> Unit,
    onDisableIntelligence: () -> Unit,
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
    onSaveCollection: (Long, List<PlaylistCollectItem>) -> Unit,
    onSaveNewCollection: (String, Long) -> Unit,
    onCollectDismiss: () -> Unit,
    onMoreOptionsDismiss: () -> Unit,
    onSetTimer: (Int) -> Unit,
    onTimerDismiss: () -> Unit,
    onLikeComment: (CommentItem) -> Unit,
    onRetryComments: () -> Unit,
    onCommentsDismiss: () -> Unit,
    onOutputDeviceSelected: (Int) -> Unit,
    onOutputDeviceDismiss: () -> Unit,
    onNavigateToProfile: (Long) -> Unit = {}
) {
    if (showQueueSheet) {
        PlayQueueSheet(
            queue = queue,
            currentIndex = currentQueueIndex,
            playMode = playMode,
            playContext = playContext,
            isPlaying = isPlaying,
            onPlayAtIndex = onPlayAtIndex,
            onRemoveAtIndex = onRemoveAtIndex,
            onMoveItem = onMoveQueueItem,
            onToggleShuffle = onToggleShuffle,
            onClearQueue = onClearQueue,
            onDisableRoaming = onDisableRoaming,
            onDisableIntelligence = onDisableIntelligence,
            onDismiss = onQueueDismiss
        )
    }

    if (showMoreOptionsSheet) {
        val albumName = songState.songDetail?.al?.name ?: songState.songWiki?.album ?: "未知专辑"
        SongMoreOptionsSheet(
            title = title,
            artist = artist,
            coverUrl = coverUrl,
            albumName = albumName,
            isLiked = songState.isLiked,
            sleepTimerRemaining = sleepTimerRemaining,
            currentQuality = activeQuality,
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
            onDismiss = onMoreOptionsDismiss
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

    if (showCommentsSheet) {
        CommentsBottomSheet(
            commentsState = commentsState,
            onLikeComment = onLikeComment,
            onDismiss = onCommentsDismiss,
            onRetry = onRetryComments,
            onUserClick = onNavigateToProfile
        )
    }

    if (showOutputDeviceSheet) {
        OutputDeviceSheet(
            onDeviceSelected = onOutputDeviceSelected,
            onDismiss = onOutputDeviceDismiss
        )
    }
}
