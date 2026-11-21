package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.model.PlaybackQuality
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.PlaybackLevel
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

// 播放路由门面：界面与页面 ViewModel 注入此实例，按 activeTarget 自动路由到本机控制器或远端控制器
open class RoutingPlaybackController(
    val localController: PlaybackController,
    val coordinator: LanLinkRemoteCoordinator,
    private val scope: CoroutineScope
) : PlaybackController {

    private val remoteController: RemotePlaybackController
        get() = coordinator.remoteController

    private val target: StateFlow<PlaybackTarget>
        get() = coordinator.activeTarget

    private val activeController: PlaybackController
        get() = if (target.value is PlaybackTarget.Remote) remoteController else localController

    private val emptyPlaybackLevel = MutableStateFlow<PlaybackLevel?>(null)
    private val emptyPlaybackQuality = MutableStateFlow<PlaybackQuality?>(null)

    private fun <T> routeFlow(local: StateFlow<T>, remote: StateFlow<T>): StateFlow<T> =
        combine(target, local, remote) { currentTarget, l, r ->
            if (currentTarget is PlaybackTarget.Remote) r else l
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = if (target.value is PlaybackTarget.Remote) remote.value else local.value
        )

    override val isPlaying: StateFlow<Boolean> = routeFlow(localController.isPlaying, remoteController.isPlaying)
    override val playWhenReady: StateFlow<Boolean> = routeFlow(localController.playWhenReady, remoteController.playWhenReady)
    override val nowPlaying: StateFlow<NowPlaying?> = routeFlow(localController.nowPlaying, remoteController.nowPlaying)
    override val playbackLevel: StateFlow<PlaybackLevel?> = routeFlow(localController.playbackLevel, emptyPlaybackLevel)
    override val playbackQuality: StateFlow<PlaybackQuality?> = routeFlow(localController.playbackQuality, emptyPlaybackQuality)
    override val currentPosition: StateFlow<Long> = routeFlow(localController.currentPosition, remoteController.currentPosition)
    override val duration: StateFlow<Long> = routeFlow(localController.duration, remoteController.duration)
    override val sleepTimerRemaining: StateFlow<Long> = routeFlow(localController.sleepTimerRemaining, remoteController.sleepTimerRemaining)
    override val playContext: StateFlow<String?> = routeFlow(localController.playContext, remoteController.playContext)
    override val playSource: StateFlow<PlaySource?> = routeFlow(localController.playSource, remoteController.playSource)
    override val currentIndex: StateFlow<Int> = routeFlow(localController.currentIndex, remoteController.currentIndex)
    override val playMode: StateFlow<PlayMode> = routeFlow(localController.playMode, remoteController.playMode)
    override val queue: StateFlow<List<QueueItem>> = routeFlow(localController.queue, remoteController.queue)
    override val currentQueueItem: StateFlow<QueueItem?> = routeFlow(localController.currentQueueItem, remoteController.currentQueueItem)
    override val previousQueueItem: StateFlow<QueueItem?> = routeFlow(localController.previousQueueItem, remoteController.previousQueueItem)
    override val nextQueueItem: StateFlow<QueueItem?> = routeFlow(localController.nextQueueItem, remoteController.nextQueueItem)

    val activeTarget: StateFlow<PlaybackTarget>
        get() = coordinator.activeTarget

    val remoteMessages: Flow<String>
        get() = coordinator.messages

    fun switchToLocal(): Boolean = coordinator.switchToLocal()

    fun switchToRemote(): Boolean = coordinator.switchToRemote()

    override fun currentPositionNow(): Long = activeController.currentPositionNow()

    override suspend fun initController() {
        localController.initController()
    }

    override fun playQueue(
        items: List<QueueItem>,
        startIndex: Int,
        playContext: String?,
        source: PlaySource?,
        startPositionMs: Long
    ) = activeController.playQueue(items, startIndex, playContext, source, startPositionMs)

    override fun playAudio(
        songId: Long,
        url: String,
        title: String,
        artist: String,
        coverUrl: String,
        startPosition: Long,
        playContext: String?
    ) = activeController.playAudio(songId, url, title, artist, coverUrl, startPosition, playContext)

    override fun addToPlayNext(items: List<QueueItem>) = activeController.addToPlayNext(items)

    override fun playNextNow(item: QueueItem, startPositionMs: Long) =
        activeController.playNextNow(item, startPositionMs)

    override fun appendToQueue(items: List<QueueItem>) = activeController.appendToQueue(items)

    override fun extendQueue(source: PlaySource, items: List<QueueItem>) =
        activeController.extendQueue(source, items)

    override fun playNext() = activeController.playNext()

    override fun playPrevious() = activeController.playPrevious()

    override fun skipToPrevious() = activeController.skipToPrevious()

    override fun playAtIndex(index: Int) = activeController.playAtIndex(index)

    override fun removeFromQueue(index: Int) = activeController.removeFromQueue(index)

    override fun moveInQueue(from: Int, to: Int) = activeController.moveInQueue(from, to)

    override fun clearQueue() = activeController.clearQueue()

    override fun stopAndClearQueue() = activeController.stopAndClearQueue()

    override fun toggleShuffle() = activeController.toggleShuffle()

    override fun toggleRepeat() = activeController.toggleRepeat()

    override fun rotatePlayMode() = activeController.rotatePlayMode()

    override fun pause() = activeController.pause()

    override fun resume() = activeController.resume()

    override fun togglePlayPause() = activeController.togglePlayPause()

    override fun seekTo(positionMs: Long) = activeController.seekTo(positionMs)

    override fun reloadCurrentTrack() = activeController.reloadCurrentTrack()

    override fun setSleepTimer(minutes: Int) = activeController.setSleepTimer(minutes)

    override fun setPositionUpdateInterval(intervalMs: Long) {
        localController.setPositionUpdateInterval(intervalMs)
        remoteController.setPositionUpdateInterval(intervalMs)
    }

    override fun cancelPendingSkip(): Boolean = activeController.cancelPendingSkip()

    override fun disableRoaming() = activeController.disableRoaming()

    override fun disableIntelligence() = activeController.disableIntelligence()

    override fun startPersonalFm(items: List<QueueItem>) = activeController.startPersonalFm(items)

    override fun disablePersonalFm() = activeController.disablePersonalFm()

    override fun trashCurrentFmSong() = activeController.trashCurrentFmSong()

    override fun switchPersonalFmMode(mode: PersonalFmMode) = activeController.switchPersonalFmMode(mode)
}
