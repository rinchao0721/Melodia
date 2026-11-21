package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val DEFAULT_POSITION_TICK_MS = 250L
private const val MIN_POSITION_TICK_MS = 50L
private const val MAX_POSITION_TICK_MS = 1_000L
private const val MAX_CLOCK_SKEW_TOLERANCE_MS = 1_000L
private const val MAX_NETWORK_LATENCY_COMPENSATION_MS = 500L

// 远端播放控制器：实现 PlaybackController 契约，状态来自对端推送并在本地插值进度，操作转为遥控命令
class RemotePlaybackController(
    private val sendCommand: (LanLinkMessage) -> Unit,
    private val onUserMessage: (String) -> Unit,
    private val scope: CoroutineScope,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : PlaybackController {

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playWhenReady = MutableStateFlow(false)
    override val playWhenReady: StateFlow<Boolean> = _playWhenReady.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow(0L)
    override val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining.asStateFlow()

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

    override val currentQueueItem: StateFlow<QueueItem?> =
        combine(_queue, _currentIndex) { items, index ->
            if (index in items.indices) items[index] else null
        }.stateIn(scope, SharingStarted.Eagerly, null)

    override val previousQueueItem: StateFlow<QueueItem?> =
        combine(_queue, _currentIndex, _playContext) { items, index, ctx ->
            if (ctx != PlaybackController.CONTEXT_PERSONAL_FM && items.size > 1 && index in items.indices) {
                items[(index - 1 + items.size) % items.size]
            } else null
        }.stateIn(scope, SharingStarted.Eagerly, null)

    override val nextQueueItem: StateFlow<QueueItem?> =
        combine(_queue, _currentIndex) { items, index ->
            if (items.size > 1 && index in items.indices) items[(index + 1) % items.size] else null
        }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _lastAudibleVolume = MutableStateFlow(100)
    val lastAudibleVolume: StateFlow<Int> = _lastAudibleVolume.asStateFlow()

    @Volatile
    private var basePositionMs: Long = 0L

    @Volatile
    private var anchorClockMs: Long = 0L

    @Volatile
    private var lastQueueVersion: Long = -1L

    @Volatile
    private var positionTickIntervalMs: Long = DEFAULT_POSITION_TICK_MS

    private var tickerJob: Job? = null

    override suspend fun initController() = Unit

    override fun currentPositionNow(): Long {
        val base = basePositionMs
        val raw = if (_isPlaying.value && anchorClockMs > 0L) {
            base + (clockMs() - anchorClockMs).coerceAtLeast(0L)
        } else {
            base
        }
        val max = _duration.value
        return if (max > 0L) raw.coerceIn(0L, max) else raw.coerceAtLeast(0L)
    }

    fun onPlaybackState(state: RemotePlaybackStateCompat) = applyPlaybackState(state)

    fun onPlaybackState(state: LanLinkMessage.RemotePlaybackState) = applyPlaybackState(
        RemotePlaybackStateCompat(
            isPlaying = state.isPlaying,
            playWhenReady = state.playWhenReady,
            nowPlaying = state.nowPlaying?.toNowPlaying(),
            positionMs = state.positionMs,
            sampledAtMs = state.sampledAtMs,
            durationMs = state.durationMs,
            playMode = state.playMode,
            playContext = state.playContext,
            playSource = state.playSource,
            currentIndex = state.currentIndex,
            volumePercent = state.volumePercent,
            queueVersion = state.queueVersion
        )
    )

    private fun applyPlaybackState(state: RemotePlaybackStateCompat) {
        val nowMs = clockMs()
        val delta = nowMs - state.sampledAtMs
        // 两端系统时钟存在偏差时只使用本机接收时刻作锚点，同机/对齐时钟下则补偿极小网络延迟
        val initialDelay = if (state.isPlaying && delta in 0L..MAX_CLOCK_SKEW_TOLERANCE_MS) {
            delta.coerceAtMost(MAX_NETWORK_LATENCY_COMPENSATION_MS)
        } else {
            0L
        }
        val clampedDuration = state.durationMs.coerceAtLeast(0L)
        val effectivePos = if (clampedDuration > 0L) {
            (state.positionMs + initialDelay).coerceIn(0L, clampedDuration)
        } else {
            (state.positionMs + initialDelay).coerceAtLeast(0L)
        }

        _duration.value = clampedDuration
        _isPlaying.value = state.isPlaying
        _playWhenReady.value = state.playWhenReady
        _nowPlaying.value = state.nowPlaying
        _playMode.value = state.playMode
        _playContext.value = state.playContext
        _playSource.value = state.playSource
        _currentIndex.value = state.currentIndex
        val vol = state.volumePercent.coerceIn(0, 100)
        _volume.value = vol
        if (vol > 0) _lastAudibleVolume.value = vol

        basePositionMs = effectivePos
        anchorClockMs = nowMs
        _currentPosition.value = effectivePos

        if (state.isPlaying) {
            ensureTickerRunning()
        } else {
            stopTicker()
        }

        if (state.queueVersion > 0L && state.queueVersion != lastQueueVersion) {
            sendCommand(LanLinkMessage.RemoteRequestSync)
        }
    }

    fun onQueueSync(sync: LanLinkMessage.RemoteQueueSync) {
        lastQueueVersion = sync.queueVersion
        _queue.value = sync.items
        _currentIndex.value = sync.currentIndex
        _playContext.value = sync.playContext
        _playSource.value = sync.playSource
    }

    fun reset() {
        stopTicker()
        basePositionMs = 0L
        anchorClockMs = 0L
        lastQueueVersion = -1L
        _isPlaying.value = false
        _playWhenReady.value = false
        _nowPlaying.value = null
        _currentPosition.value = 0L
        _duration.value = 0L
        _sleepTimerRemaining.value = 0L
        _playContext.value = null
        _playSource.value = null
        _currentIndex.value = -1
        _playMode.value = PlayMode.LIST_LOOP
        _queue.value = emptyList()
        _volume.value = 100
        _lastAudibleVolume.value = 100
    }

    fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _volume.value = clamped
        if (clamped > 0) _lastAudibleVolume.value = clamped
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.SET_VOLUME, volumePercent = clamped))
    }

    override fun playQueue(
        items: List<QueueItem>,
        startIndex: Int,
        playContext: String?,
        source: PlaySource?,
        startPositionMs: Long
    ) {
        val targetItem = items.getOrNull(startIndex)
        if (targetItem != null && isLocalOnlyQueueItem(targetItem)) {
            onUserMessage("本地歌曲无法在远程设备播放")
            return
        }
        val kept = items.mapIndexedNotNull { idx, item ->
            if (isLocalOnlyQueueItem(item)) null else idx to item
        }
        if (kept.isEmpty()) {
            onUserMessage("本地歌曲无法在远程设备播放")
            return
        }
        val mappedStart = kept.indexOfFirst { it.first == startIndex }.takeIf { it >= 0 } ?: 0
        sendCommand(
            LanLinkMessage.RemotePlayQueue(
                items = kept.map { it.second },
                startIndex = mappedStart,
                playContext = playContext,
                source = source,
                startPositionMs = startPositionMs
            )
        )
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
        if (isLocalOnlyAudio(songId, url)) {
            onUserMessage("本地歌曲无法在远程设备播放")
            return
        }
        sendCommand(
            LanLinkMessage.RemotePlayAudio(
                songId = songId,
                url = url,
                title = title,
                artist = artist,
                coverUrl = coverUrl,
                startPosition = startPosition,
                playContext = playContext
            )
        )
    }

    override fun addToPlayNext(items: List<QueueItem>) {
        val filtered = items.filterNot(::isLocalOnlyQueueItem)
        if (filtered.isEmpty()) {
            onUserMessage("本地歌曲无法在远程设备播放")
            return
        }
        sendCommand(LanLinkMessage.RemoteAddToPlayNext(filtered))
    }

    override fun playNextNow(item: QueueItem, startPositionMs: Long) {
        if (isLocalOnlyQueueItem(item)) {
            onUserMessage("本地歌曲无法在远程设备播放")
            return
        }
        sendCommand(LanLinkMessage.RemotePlayNextNow(item, startPositionMs))
    }

    override fun appendToQueue(items: List<QueueItem>) {
        val filtered = items.filterNot(::isLocalOnlyQueueItem)
        if (filtered.isEmpty()) {
            onUserMessage("本地歌曲无法在远程设备播放")
            return
        }
        sendCommand(LanLinkMessage.RemoteAppendToQueue(filtered))
    }

    override fun extendQueue(source: PlaySource, items: List<QueueItem>) {
        val filtered = items.filterNot(::isLocalOnlyQueueItem)
        if (filtered.isEmpty()) return
        sendCommand(LanLinkMessage.RemoteExtendQueue(source, filtered))
    }

    override fun playNext() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.NEXT))
    }

    override fun playPrevious() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.PREVIOUS))
    }

    override fun skipToPrevious() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.SKIP_TO_PREVIOUS))
    }

    override fun playAtIndex(index: Int) {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.PLAY_AT_INDEX, index = index))
    }

    override fun removeFromQueue(index: Int) {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.REMOVE_FROM_QUEUE, index = index))
    }

    override fun moveInQueue(from: Int, to: Int) {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.MOVE_IN_QUEUE, fromIndex = from, toIndex = to))
    }

    override fun clearQueue() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.CLEAR_QUEUE))
    }

    override fun stopAndClearQueue() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.STOP_AND_CLEAR_QUEUE))
    }

    override fun toggleShuffle() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.TOGGLE_SHUFFLE))
    }

    override fun toggleRepeat() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.TOGGLE_REPEAT))
    }

    override fun rotatePlayMode() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.ROTATE_PLAY_MODE))
    }

    override fun pause() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.PAUSE))
    }

    override fun resume() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.PLAY))
    }

    override fun togglePlayPause() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.TOGGLE_PLAY_PAUSE))
    }

    override fun seekTo(positionMs: Long) {
        val max = _duration.value
        val clamped = if (max > 0L) positionMs.coerceIn(0L, max) else positionMs.coerceAtLeast(0L)
        basePositionMs = clamped
        anchorClockMs = clockMs()
        _currentPosition.value = clamped
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.SEEK, positionMs = clamped))
    }

    override fun reloadCurrentTrack() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.RELOAD_CURRENT))
    }

    override fun setSleepTimer(minutes: Int) = Unit

    override fun setPositionUpdateInterval(intervalMs: Long) {
        positionTickIntervalMs = intervalMs.coerceIn(MIN_POSITION_TICK_MS, MAX_POSITION_TICK_MS)
        if (_isPlaying.value) {
            stopTicker()
            ensureTickerRunning()
        }
    }

    override fun cancelPendingSkip(): Boolean {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.CANCEL_PENDING_SKIP))
        return false
    }

    override fun disableRoaming() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.DISABLE_ROAMING))
    }

    override fun disableIntelligence() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.DISABLE_INTELLIGENCE))
    }

    override fun startPersonalFm(items: List<QueueItem>) {
        val filtered = items.filterNot(::isLocalOnlyQueueItem)
        if (filtered.isEmpty()) {
            onUserMessage("本地歌曲无法在远程设备播放")
            return
        }
        sendCommand(LanLinkMessage.RemoteStartPersonalFm(filtered))
    }

    override fun disablePersonalFm() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.DISABLE_PERSONAL_FM))
    }

    override fun trashCurrentFmSong() {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.TRASH_FM))
    }

    override fun switchPersonalFmMode(mode: PersonalFmMode) {
        sendCommand(LanLinkMessage.RemoteCommand(RemoteCommandAction.SWITCH_FM_MODE, personalFmMode = mode))
    }

    private fun ensureTickerRunning() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive && _isPlaying.value) {
                delay(positionTickIntervalMs)
                if (_isPlaying.value) {
                    _currentPosition.value = currentPositionNow()
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    companion object {
        fun isLocalOnlyQueueItem(item: QueueItem): Boolean =
            !item.localUri.isNullOrBlank() || (item.songId < 0L && item.external == null)

        fun isLocalOnlyAudio(songId: Long, url: String): Boolean =
            songId < 0L ||
                url.startsWith("content://", ignoreCase = true) ||
                url.startsWith("file://", ignoreCase = true)
    }
}

data class RemotePlaybackStateCompat(
    val isPlaying: Boolean,
    val playWhenReady: Boolean,
    val nowPlaying: NowPlaying?,
    val positionMs: Long,
    val sampledAtMs: Long,
    val durationMs: Long,
    val playMode: PlayMode,
    val playContext: String?,
    val playSource: PlaySource?,
    val currentIndex: Int,
    val volumePercent: Int,
    val queueVersion: Long
)
