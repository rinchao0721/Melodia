package com.lin0721.linmusic.core.player

import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lin0721.linmusic.core.lanlink.PlaybackTarget
import com.lin0721.linmusic.core.lanlink.RoutingPlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

// 远程播放代理播放器：向 MediaSession 暴露远端队列、状态与系统音量控制，并将所有控制命令路由给远端设备
@OptIn(UnstableApi::class)
class RemoteSessionPlayer(
    private val routingPlaybackController: RoutingPlaybackController,
    private val scope: CoroutineScope,
    private val routingControllerIdFlow: StateFlow<String?> = MutableStateFlow(null)
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val remoteController
        get() = routingPlaybackController.coordinator.remoteController

    private val observeJob: Job

    init {
        val stateFlowPart = combine(
            remoteController.isPlaying,
            remoteController.playWhenReady,
            remoteController.nowPlaying,
            remoteController.duration,
            remoteController.playMode
        ) { playing, ready, nowPlaying, duration, playMode ->
            StateMeta(playing, ready, nowPlaying, duration, playMode)
        }
        val queueFlowPart = combine(
            remoteController.playContext,
            remoteController.queue,
            remoteController.currentIndex,
            routingPlaybackController.activeTarget
        ) { playContext, queue, index, target ->
            StateQueue(playContext, queue, index, target)
        }
        val deviceFlowPart = combine(
            remoteController.volume,
            routingControllerIdFlow
        ) { volume, controllerId ->
            volume to controllerId
        }
        var lastPositionMs = -1L
        observeJob = scope.launch(Dispatchers.Main.immediate) {
            launch {
                combine(stateFlowPart, queueFlowPart, deviceFlowPart) { meta, queue, device ->
                    Triple(meta, queue, device)
                }
                    .distinctUntilChanged()
                    .collect { invalidateState() }
            }
            launch {
                remoteController.currentPosition.collect { pos ->
                    val jumped = lastPositionMs < 0L || abs(pos - lastPositionMs) > 1_200L
                    lastPositionMs = pos
                    if (!remoteController.isPlaying.value || jumped) {
                        invalidateState()
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun getState(): State {
        val remote = remoteController
        val peerName = (routingPlaybackController.activeTarget.value as? PlaybackTarget.Remote)
            ?.peer?.deviceName
            ?.takeIf { it.isNotBlank() }
            ?: "远程设备"

        val isPlaying = remote.isPlaying.value
        val playWhenReady = remote.playWhenReady.value
        val nowPlaying = remote.nowPlaying.value
        val durationMs = remote.duration.value.coerceAtLeast(0L)
        val durationUs = if (durationMs > 0L) durationMs * 1_000L else C.TIME_UNSET
        val playContext = remote.playContext.value
        val isPersonalFm = playContext == PlaybackController.CONTEXT_PERSONAL_FM
        val playMode = remote.playMode.value
        val queueItems = remote.queue.value
        val rawIndex = remote.currentIndex.value

        val currentIndex: Int
        val playlist: List<MediaItemData>
        if (queueItems.isNotEmpty()) {
            currentIndex = rawIndex.coerceIn(0, queueItems.lastIndex)
            playlist = queueItems.mapIndexed { idx, item ->
                val isCurrent = idx == currentIndex
                val title = if (isCurrent) nowPlaying?.title?.ifBlank { item.title } ?: item.title else item.title
                val artist = if (isCurrent) nowPlaying?.artist?.ifBlank { item.artist } ?: item.artist else item.artist
                val artwork = if (isCurrent) nowPlaying?.artworkUri ?: item.coverUrl else item.coverUrl
                val mediaId = if (isCurrent) nowPlaying?.mediaId ?: item.songId.toString() else item.songId.toString()
                val metadata = buildRemoteMetadata(item.songId, title, artist, artwork, playContext, peerName)
                val mediaItem = MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setMediaMetadata(metadata)
                    .build()
                MediaItemData.Builder("${idx}_${item.songId}")
                    .setMediaItem(mediaItem)
                    .setMediaMetadata(metadata)
                    .setIsSeekable(true)
                    .setDurationUs(if (isCurrent) durationUs else C.TIME_UNSET)
                    .build()
            }
        } else if (nowPlaying != null) {
            currentIndex = 0
            val songId = nowPlaying.mediaId.toLongOrNull() ?: -1L
            val metadata = buildRemoteMetadata(
                songId = songId,
                title = nowPlaying.title,
                artist = nowPlaying.artist,
                artworkUri = nowPlaying.artworkUri,
                playContext = playContext,
                peerName = peerName
            )
            val mediaItem = MediaItem.Builder()
                .setMediaId(nowPlaying.mediaId)
                .setMediaMetadata(metadata)
                .build()
            playlist = listOf(
                MediaItemData.Builder("now_${nowPlaying.mediaId}")
                    .setMediaItem(mediaItem)
                    .setMediaMetadata(metadata)
                    .setIsSeekable(true)
                    .setDurationUs(durationUs)
                    .build()
            )
        } else {
            currentIndex = C.INDEX_UNSET
            playlist = emptyList()
        }

        val playbackState = when {
            playlist.isEmpty() -> Player.STATE_IDLE
            playWhenReady && !isPlaying -> Player.STATE_BUFFERING
            else -> Player.STATE_READY
        }

        val (repeatMode, shuffleEnabled) = when (playMode) {
            PlayMode.SINGLE_LOOP -> Player.REPEAT_MODE_ONE to false
            PlayMode.SHUFFLE -> Player.REPEAT_MODE_ALL to true
            PlayMode.LIST_LOOP -> Player.REPEAT_MODE_ALL to false
        }

        val commandsBuilder = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_SET_SHUFFLE_MODE,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_AUDIO_ATTRIBUTES,
            Player.COMMAND_GET_DEVICE_VOLUME,
            Player.COMMAND_SET_DEVICE_VOLUME,
            Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
            Player.COMMAND_ADJUST_DEVICE_VOLUME,
            Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS
        )
        if (!isPersonalFm) {
            commandsBuilder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
            commandsBuilder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val volumePercent = remote.volume.value
        val volumeStep = LanLinkRemoteVolume.percentToStep(volumePercent)
        val isDeviceMuted = volumePercent <= 0
        val deviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
            .setMinVolume(0)
            .setMaxVolume(LanLinkRemoteVolume.MAX_STEPS)
            .setRoutingControllerId(routingControllerIdFlow.value)
            .build()

        val stateBuilder = State.Builder()
            .setAvailableCommands(commandsBuilder.build())
            .setAudioAttributes(audioAttributes)
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(volumeStep)
            .setIsDeviceMuted(isDeviceMuted)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
            .setRepeatMode(repeatMode)
            .setShuffleModeEnabled(shuffleEnabled)
            .setPlaylist(playlist)
            .setContentPositionMs { remoteController.currentPositionNow().coerceAtLeast(0L) }

        if (currentIndex != C.INDEX_UNSET) {
            stateBuilder.setCurrentMediaItemIndex(currentIndex)
        }
        return stateBuilder.build()
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        remoteController.setVolume(LanLinkRemoteVolume.stepToPercent(deviceVolume))
        return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        val nextStep = LanLinkRemoteVolume.increaseStep(remoteController.volume.value)
        remoteController.setVolume(LanLinkRemoteVolume.stepToPercent(nextStep))
        return Futures.immediateVoidFuture()
    }

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        val nextStep = LanLinkRemoteVolume.decreaseStep(remoteController.volume.value)
        remoteController.setVolume(LanLinkRemoteVolume.stepToPercent(nextStep))
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceMuted(muted: Boolean, flags: Int): ListenableFuture<*> {
        val targetPercent = if (muted) {
            0
        } else {
            LanLinkRemoteVolume.unmutePercent(remoteController.lastAudibleVolume.value)
        }
        remoteController.setVolume(targetPercent)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) {
            routingPlaybackController.resume()
        } else {
            routingPlaybackController.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        routingPlaybackController.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        observeJob.cancel()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        routingPlaybackController.toggleRepeat()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        routingPlaybackController.toggleShuffle()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                routingPlaybackController.playNext()
            }

            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                if (routingPlaybackController.playContext.value != PlaybackController.CONTEXT_PERSONAL_FM) {
                    routingPlaybackController.playPrevious()
                }
            }

            Player.COMMAND_SEEK_TO_MEDIA_ITEM -> {
                if (mediaItemIndex >= 0 && mediaItemIndex != currentMediaItemIndex) {
                    routingPlaybackController.playAtIndex(mediaItemIndex)
                } else {
                    val target = if (positionMs == C.TIME_UNSET) 0L else positionMs.coerceAtLeast(0L)
                    routingPlaybackController.seekTo(target)
                }
            }

            else -> {
                val target = if (positionMs == C.TIME_UNSET) 0L else positionMs.coerceAtLeast(0L)
                routingPlaybackController.seekTo(target)
            }
        }
        return Futures.immediateVoidFuture()
    }

    private fun buildRemoteMetadata(
        songId: Long,
        title: String,
        artist: String,
        artworkUri: String?,
        playContext: String?,
        peerName: String
    ): MediaMetadata {
        val extras = Bundle().apply {
            if (songId != -1L) putLong("songId", songId)
            if (playContext != null) putString("playContext", playContext)
        }
        return MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setSubtitle("在 $peerName 上播放")
            .setArtworkUri(artworkUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
            .setExtras(extras)
            .build()
    }

    private data class StateMeta(
        val isPlaying: Boolean,
        val playWhenReady: Boolean,
        val nowPlaying: NowPlaying?,
        val duration: Long,
        val playMode: PlayMode
    )

    private data class StateQueue(
        val playContext: String?,
        val queue: List<QueueItem>,
        val index: Int,
        val target: PlaybackTarget
    )
}
