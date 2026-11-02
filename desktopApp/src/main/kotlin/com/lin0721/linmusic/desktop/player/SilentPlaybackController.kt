package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

// libmpv 加载失败时的占位实现：只维护队列与曲目状态、不出声，每次尝试播放都给出提示
class SilentPlaybackController : DesktopPlaybackController {

    companion object {
        const val UNAVAILABLE_MESSAGE = "播放组件加载失败，当前无法播放，详见日志"
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val messages: SharedFlow<String> = _messages.asSharedFlow()

    override val unavailableReason: String = UNAVAILABLE_MESSAGE

    override val volume: StateFlow<Int> = MutableStateFlow(100).asStateFlow()
    override val lastAudibleVolume: StateFlow<Int> = MutableStateFlow(100).asStateFlow()
    override val audioDevices: StateFlow<List<AudioDevice>> = MutableStateFlow(emptyList<AudioDevice>()).asStateFlow()
    override val audioDevice: StateFlow<String> = MutableStateFlow(AUTO_AUDIO_DEVICE).asStateFlow()

    override fun setVolume(percent: Int) = Unit
    override fun refreshAudioDevices() = Unit
    override fun setAudioDevice(name: String) = Unit
    override fun release() = Unit

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val playWhenReady: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    override val duration: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()
    override val sleepTimerRemaining: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()

    private val _playContext = MutableStateFlow<String?>(null)
    override val playContext: StateFlow<String?> = _playContext.asStateFlow()

    private val _playSource = MutableStateFlow<PlaySource?>(null)
    override val playSource: StateFlow<PlaySource?> = _playSource.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.LIST_LOOP)
    override val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    override val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _currentQueueItem = MutableStateFlow<QueueItem?>(null)
    override val currentQueueItem: StateFlow<QueueItem?> = _currentQueueItem.asStateFlow()

    private val _previousQueueItem = MutableStateFlow<QueueItem?>(null)
    override val previousQueueItem: StateFlow<QueueItem?> = _previousQueueItem.asStateFlow()

    private val _nextQueueItem = MutableStateFlow<QueueItem?>(null)
    override val nextQueueItem: StateFlow<QueueItem?> = _nextQueueItem.asStateFlow()

    override suspend fun initController() = Unit

    override fun playQueue(
        items: List<QueueItem>,
        startIndex: Int,
        playContext: String?,
        source: PlaySource?,
        startPositionMs: Long
    ) {
        if (items.isEmpty()) return
        _messages.tryEmit(UNAVAILABLE_MESSAGE)
        _queue.value = items
        _playContext.value = playContext
        _playSource.value = source
        moveTo(startIndex.coerceIn(0, items.lastIndex), play = true)
    }

    override fun playAudio(
        songId: Long,
        url: String,
        title: String,
        artist: String,
        coverUrl: String,
        startPosition: Long,
        playContext: String?
    ) {
        playQueue(listOf(QueueItem(songId, title, artist, coverUrl)), 0, playContext, null)
    }

    override fun playNextNow(item: QueueItem, startPositionMs: Long) {
        if (_queue.value.isEmpty()) {
            playQueue(listOf(item), 0, null)
            return
        }
        addToPlayNext(listOf(item))
        playNext()
    }

    override fun addToPlayNext(items: List<QueueItem>) {
        if (items.isEmpty()) return
        val current = _queue.value
        if (current.isEmpty()) {
            playQueue(items, 0, null)
            return
        }
        val insertAt = (_currentIndex.value + 1).coerceIn(0, current.size)
        _queue.value = current.take(insertAt) + items + current.drop(insertAt)
        refreshNeighbours()
    }

    override fun appendToQueue(items: List<QueueItem>) {
        if (items.isEmpty()) return
        if (_queue.value.isEmpty()) {
            playQueue(items, 0, null)
            return
        }
        _queue.value = _queue.value + items
        refreshNeighbours()
    }

    override fun extendQueue(source: PlaySource, items: List<QueueItem>) {
        if (_playSource.value != source) return
        val queuedIds = _queue.value.mapTo(HashSet()) { it.songId }
        val fresh = items.filter { it.songId !in queuedIds }
        if (fresh.isEmpty()) return
        _queue.value = _queue.value + fresh
        refreshNeighbours()
    }

    override fun playNext() = step(1)

    override fun playPrevious() = step(-1)

    override fun skipToPrevious() = step(-1)

    override fun playAtIndex(index: Int) {
        if (index in _queue.value.indices) moveTo(index, play = true)
    }

    override fun removeFromQueue(index: Int) {
        val items = _queue.value
        if (index !in items.indices) return
        val remaining = items.filterIndexed { i, _ -> i != index }
        _queue.value = remaining
        when {
            remaining.isEmpty() -> clearQueue()
            index < _currentIndex.value -> {
                _currentIndex.value -= 1
                refreshNeighbours()
            }
            index == _currentIndex.value -> moveTo(index.coerceAtMost(remaining.lastIndex), play = _isPlaying.value)
            else -> refreshNeighbours()
        }
    }

    override fun moveInQueue(from: Int, to: Int) {
        val items = _queue.value.toMutableList()
        if (from !in items.indices || to !in items.indices || from == to) return
        val currentId = _currentQueueItem.value?.songId
        items.add(to, items.removeAt(from))
        _queue.value = items
        _currentIndex.value = items.indexOfFirst { it.songId == currentId }
        refreshNeighbours()
    }

    override fun clearQueue() {
        _queue.value = emptyList()
        _currentIndex.value = -1
        _isPlaying.value = false
        _nowPlaying.value = null
        _currentPosition.value = 0L
        refreshNeighbours()
    }

    override fun toggleShuffle() {
        _playMode.value = if (_playMode.value == PlayMode.SHUFFLE) PlayMode.LIST_LOOP else PlayMode.SHUFFLE
    }

    override fun toggleRepeat() {
        _playMode.value = if (_playMode.value == PlayMode.SINGLE_LOOP) PlayMode.LIST_LOOP else PlayMode.SINGLE_LOOP
    }

    override fun rotatePlayMode() {
        _playMode.value = when (_playMode.value) {
            PlayMode.LIST_LOOP -> PlayMode.SINGLE_LOOP
            PlayMode.SINGLE_LOOP -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.LIST_LOOP
        }
    }

    override fun pause() {
        _isPlaying.value = false
    }

    override fun resume() {
        if (_nowPlaying.value == null) return
        _messages.tryEmit(UNAVAILABLE_MESSAGE)
        _isPlaying.value = true
    }

    override fun togglePlayPause() {
        if (_isPlaying.value) pause() else resume()
    }

    override fun seekTo(positionMs: Long) {
        _currentPosition.value = positionMs.coerceAtLeast(0L)
    }

    override fun reloadCurrentTrack() = Unit

    override fun setSleepTimer(minutes: Int) = Unit

    override fun setPositionUpdateInterval(intervalMs: Long) = Unit

    override fun cancelPendingSkip(): Boolean = false

    override fun disableRoaming() = Unit

    override fun disableIntelligence() = Unit

    private fun step(delta: Int) {
        val size = _queue.value.size
        if (size == 0) return
        val base = _currentIndex.value.coerceAtLeast(0)
        moveTo(((base + delta) % size + size) % size, play = true)
    }

    private fun moveTo(index: Int, play: Boolean) {
        val item = _queue.value.getOrNull(index) ?: return
        _currentIndex.value = index
        _currentPosition.value = 0L
        _nowPlaying.value = NowPlaying(
            mediaId = item.songId.toString(),
            title = item.title,
            artist = item.artist,
            artworkUri = item.coverUrl.takeIf { it.isNotBlank() }
        )
        _isPlaying.value = play
        refreshNeighbours()
    }

    private fun refreshNeighbours() {
        val items = _queue.value
        val index = _currentIndex.value
        val valid = index in items.indices
        _currentQueueItem.value = if (valid) items[index] else null
        _previousQueueItem.value = if (valid && items.size > 1) items[(index - 1 + items.size) % items.size] else null
        _nextQueueItem.value = if (valid && items.size > 1) items[(index + 1) % items.size] else null
    }
}
