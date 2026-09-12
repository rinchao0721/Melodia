package com.lin0721.linmusic.core.player

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PlayerManager"

// 网络类失败原地重试同一首的退避间隔，耗尽后转为等待网络恢复
private val NETWORK_RETRY_DELAYS_MS = longArrayOf(2000L, 5000L, 10000L)

// 距当前曲目结束的下一首链接预取阈值，用于消除播完到起播下一首之间的网络等待间隙
private const val PREFETCH_URL_WINDOW_MS = 8000L

// 队列拖拽排序的落盘合并窗口，覆盖一次连续拖拽的间隔
private const val QUEUE_MOVE_SAVE_DEBOUNCE_MS = 400L

// 预取到的下一首播放链接，按 songId 校验有效性
private data class PrefetchedUrl(val songId: Long, val url: String)

// 播放门面：对外暴露播放状态与控制入口，队列、控制器、进度、持久化等职责交由协作者承担
class PlayerManager(
    private val context: Context,
    private val playbackPreferences: PlaybackPreferences,
    private val repository: PlaybackRepository,
    private val settingsPreferences: SettingsPreferences
) : Player.Listener {

    companion object {
        const val CONTEXT_INTELLIGENCE = "intelligence"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrack = MutableStateFlow<MediaItem?>(null)
    val currentTrack: StateFlow<MediaItem?> = _currentTrack.asStateFlow()

    // 用户在"连接设备"弹层里手动选过的输出设备 id；null 表示本次会话还没手动选过，交给启发式猜测
    private val _preferredOutputDeviceId = MutableStateFlow<Int?>(null)
    val preferredOutputDeviceId: StateFlow<Int?> = _preferredOutputDeviceId.asStateFlow()

    private val controllerHolder = MediaControllerHolder(context)
    private val playbackQueue = PlaybackQueue()
    private val progress = PlaybackProgressTracker(scope, controllerHolder)
    private val stateStore = PlaybackStateStore(scope, playbackPreferences)
    private val coverPreloader = TrackCoverPreloader(context)
    private val sleepTimer = SleepTimer(scope) { pause() }
    private val roaming = SimilarRoamingController(scope, repository, settingsPreferences, playbackQueue, stateStore)
    private val networkGuard = PlaybackNetworkGuard(
        context = context,
        scope = scope,
        settingsPreferences = settingsPreferences,
        isPlaying = { _isPlaying.value },
        onPauseRequested = { pause() }
    )

    val currentPosition: StateFlow<Long> = progress.currentPosition
    val duration: StateFlow<Long> = progress.duration
    val positionUpdateInterval: StateFlow<Long> = progress.updateInterval
    val sleepTimerRemaining: StateFlow<Long> = sleepTimer.remaining
    val playContext: StateFlow<String?> = playbackQueue.playContext
    val currentIndex: StateFlow<Int> = playbackQueue.currentIndex
    val playMode: StateFlow<PlayMode> = playbackQueue.playMode
    val queue: StateFlow<List<QueueItem>> = playbackQueue.items

    // 滑动切歌手势预览用：队列头尾按循环取相邻曲目，不足两首时为 null
    val previousQueueItem: StateFlow<QueueItem?> = combine(playbackQueue.items, playbackQueue.currentIndex) { items, index ->
        if (items.size > 1 && index in items.indices) items[(index - 1 + items.size) % items.size] else null
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val nextQueueItem: StateFlow<QueueItem?> = combine(playbackQueue.items, playbackQueue.currentIndex) { items, index ->
        if (items.size > 1 && index in items.indices) items[(index + 1) % items.size] else null
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private var activePlayJob: Job? = null
    private var moveSaveJob: Job? = null
    private var consecutiveErrors = 0

    private var prefetchJob: Job? = null
    private var prefetchedNextUrl: PrefetchedUrl? = null
    private var prefetchTriggeredForSongId: Long = -1L

    // 打卡上报用的挂钟计时：轮询到的播放位置在后台/锁屏场景下不可靠（真机验证过，间歇性追不上进度），改用挂钟时间差，不依赖控制器状态同步
    private var trackStartElapsedMs: Long = 0L
    private var trackPausedAccumMs: Long = 0L
    private var trackLastPauseElapsedMs: Long? = null

    init {
        AppLogger.i(TAG, "PlayerManager 初始化 instanceId=${System.identityHashCode(this)}")
        networkGuard.register()

        scope.launch {
            playbackQueue.setPlayMode(stateStore.loadPlayMode())
            // 恢复队列
            val qs = stateStore.loadQueueState()
            playbackQueue.restore(qs.queue, qs.currentIndex, qs.playContext)
        }

        // 监听 playMode 同步，newValue != oldValue 判定防止死循环写入
        scope.launch {
            stateStore.playModeChanges.collectLatest { newMode ->
                if (playbackQueue.playMode.value != newMode) {
                    applyMode(newMode, playbackQueue.currentItem())
                }
            }
        }

        progress.start(
            isPlaying = { _isPlaying.value },
            onTick = { positionMs ->
                val dur = progress.duration.value
                val songId = _currentTrack.value?.mediaId?.toLongOrNull() ?: -1L
                if (dur > 0L && songId != -1L) {
                    val remainingMs = dur - positionMs
                    roaming.onProgressTick(songId, remainingMs)
                    maybePrefetchNextTrackUrl(remainingMs)
                }
            }
        )
    }

    fun setPositionUpdateInterval(intervalMs: Long) {
        progress.setUpdateInterval(intervalMs)
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimer.start(minutes)
    }

    suspend fun initController() {
        if (controllerHolder.isConnected) return

        val lastTrack = stateStore.loadLastTrack()
        if (lastTrack != null && _currentTrack.value == null) {
            _currentTrack.value = lastTrack.mediaItem
            progress.setPosition(lastTrack.positionMs)
            resetTrackTiming(startPlaying = false)
        }

        controllerHolder.connect(this) { mediaController ->
            if (mediaController.currentMediaItem != null) {
                _currentTrack.value = mediaController.currentMediaItem
                _isPlaying.value = mediaController.isPlaying
                progress.setPosition(mediaController.currentPosition)
                progress.setDuration(mediaController.duration)
                resetTrackTiming(startPlaying = mediaController.isPlaying)
            }

            mediaController.repeatMode = if (playbackQueue.playMode.value == PlayMode.SINGLE_LOOP)
                Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    // 设置队列并从指定位置开始播放
    fun playQueue(items: List<QueueItem>, startIndex: Int, playContext: String? = null) {
        if (items.isEmpty()) return

        if (playContext == SimilarRoamingController.CONTEXT_ROAMING) {
            roaming.prepare()
        } else if (playContext == CONTEXT_INTELLIGENCE) {
            // 备份进入心动模式前的队列，供关闭时还原
            playbackQueue.takeSnapshot()
        }

        // 记录替换前正在播放的歌曲 ID（优先取底层已加载的曲目，次选原队列当前曲目）
        val playingSongId = _currentTrack.value?.mediaId?.toLongOrNull()
            ?: playbackQueue.currentItem()?.songId

        playbackQueue.setPlayContext(playContext)
        playbackQueue.replaceAll(items, startIndex)
        consecutiveErrors = 0
        saveQueueState()

        val currentIndex = playbackQueue.currentIndex.value
        val targetItem = playbackQueue.itemAt(currentIndex)
        val isCurrentPlayingTrack = targetItem != null && targetItem.songId == playingSongId

        if ((playContext == CONTEXT_INTELLIGENCE || playContext == SimilarRoamingController.CONTEXT_ROAMING) && isCurrentPlayingTrack) {
            // 以当前正在播放的曲目为种子时平滑衔接，不重载音频与重置进度
            roaming.prefetchOnPlay(targetItem.songId, currentIndex)
            return
        }

        fetchUrlAndPlay(currentIndex)
    }

    // 单曲播放（向后兼容，创建 1 项队列）
    fun playAudio(songId: Long, url: String, title: String, artist: String, coverUrl: String, startPosition: Long = 0, playContext: String? = null) {
        val item = QueueItem(songId, title, artist, coverUrl)
        playbackQueue.replaceWithSingle(item)
        playbackQueue.setPlayContext(playContext)
        consecutiveErrors = 0

        controllerHolder.playItem(item.toMediaItem(url, playContext), playbackQueue.playMode.value, startPosition)
    }

    // 批量插播歌曲到“下一首”播放位置
    fun addToPlayNext(items: List<QueueItem>) {
        if (items.isEmpty()) return
        if (playbackQueue.isEmpty) {
            playQueue(items, 0)
            return
        }

        playbackQueue.insertNext(items)
        saveQueueState()
    }

    fun playNext() {
        if (playbackQueue.isEmpty) return
        consecutiveErrors = 0
        fetchUrlAndPlay(playbackQueue.nextIndex())
    }

    fun playPrevious() {
        if (playbackQueue.isEmpty) return
        val position = controllerHolder.currentPosition
        if (position > 3000) {
            seekTo(0)
            return
        }
        consecutiveErrors = 0
        fetchUrlAndPlay(playbackQueue.previousIndex())
    }

    // 滑动切歌专用
    fun skipToPrevious() {
        if (playbackQueue.isEmpty) return
        consecutiveErrors = 0
        fetchUrlAndPlay(playbackQueue.previousIndex())
    }

    fun playAtIndex(index: Int) {
        if (index < 0 || index >= playbackQueue.size) return
        consecutiveErrors = 0
        fetchUrlAndPlay(index)
    }

    fun removeFromQueue(index: Int) {
        if (index < 0 || index >= playbackQueue.size || playbackQueue.size <= 1) return
        val replayIndex = playbackQueue.removeAt(index)
        if (replayIndex >= 0) fetchUrlAndPlay(replayIndex)
        saveQueueState()
    }

    fun moveInQueue(from: Int, to: Int) {
        if (!playbackQueue.move(from, to)) return
        moveSaveJob?.cancel()
        moveSaveJob = scope.launch {
            delay(QUEUE_MOVE_SAVE_DEBOUNCE_MS)
            saveQueueState()
        }
    }

    fun toggleShuffle() {
        val currentItem = playbackQueue.currentItem()
        when (playbackQueue.playMode.value) {
            PlayMode.SHUFFLE -> applyMode(PlayMode.LIST_LOOP, currentItem)
            else -> applyMode(PlayMode.SHUFFLE, currentItem)
        }
    }

    fun toggleRepeat() {
        val currentItem = playbackQueue.currentItem()
        when (playbackQueue.playMode.value) {
            PlayMode.LIST_LOOP -> applyMode(PlayMode.SINGLE_LOOP, currentItem)
            PlayMode.SINGLE_LOOP -> applyMode(PlayMode.LIST_LOOP, currentItem)
            PlayMode.SHUFFLE -> applyMode(PlayMode.SINGLE_LOOP, currentItem)
        }
    }

    fun rotatePlayMode() {
        val currentItem = playbackQueue.currentItem()
        val nextMode = when (playbackQueue.playMode.value) {
            PlayMode.LIST_LOOP -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.SINGLE_LOOP
            PlayMode.SINGLE_LOOP -> PlayMode.LIST_LOOP
        }
        applyMode(nextMode, currentItem)
    }

    private fun applyMode(newMode: PlayMode, currentItem: QueueItem?) {
        playbackQueue.applyMode(newMode, currentItem)
        controllerHolder.setRepeatMode(newMode)
        stateStore.savePlayMode(newMode)
        saveQueueState()
    }

    fun pause() {
        controllerHolder.pause()
        saveState()
    }

    // 供非音频播放场景（如 MV 播放页）复用同一套"仅 Wi-Fi 播放"策略，保持与主播放器一致的移动网络提醒
    suspend fun shouldBlockPlaybackOnMobile(): Boolean = networkGuard.blockPlaybackOnMobile()

    fun resume() {
        controllerHolder.play()
    }

    fun seekTo(positionMs: Long) {
        controllerHolder.seekTo(positionMs)
        progress.setPosition(positionMs)
    }

    fun setPreferredAudioDevice(deviceId: Int) {
        controllerHolder.setPreferredAudioDevice(deviceId)
        _preferredOutputDeviceId.value = deviceId
    }

    fun togglePlayPause() {
        val item = _currentTrack.value ?: return
        if (!_isPlaying.value && item.localConfiguration == null) {
            // 重启后队列为空，从恢复的 track 元数据重建 1 项队列
            if (playbackQueue.isEmpty) {
                val songId = item.mediaId.toLongOrNull() ?: return
                val qi = QueueItem(
                    songId = songId,
                    title = item.mediaMetadata.title?.toString() ?: "",
                    artist = item.mediaMetadata.artist?.toString() ?: "",
                    coverUrl = item.mediaMetadata.artworkUri?.toString() ?: ""
                )
                playbackQueue.replaceWithSingle(qi)
            }
            fetchUrlAndPlay(playbackQueue.currentIndex.value, progress.currentPosition.value)
            return
        }
        if (_isPlaying.value) pause() else resume()
    }

    fun saveState() {
        val item = _currentTrack.value ?: return
        val songId = item.mediaId.toLongOrNull() ?: -1L
        if (songId == -1L) return

        stateStore.savePlaybackState {
            PlaybackState(
                songId = songId,
                title = item.mediaMetadata.title?.toString() ?: "",
                artist = item.mediaMetadata.artist?.toString() ?: "",
                coverUrl = item.mediaMetadata.artworkUri?.toString() ?: "",
                lastPositionMs = controllerHolder.currentPositionOrNull ?: progress.currentPosition.value
            )
        }
    }

    // 清空播放队列中除当前播放歌曲外的其他歌曲，重置播放状态并同步本地持久化状态
    fun clearQueue() {
        val currentTrackItem = playbackQueue.keepOnlyCurrent()

        if (currentTrackItem != null) {
            _isPlaying.value = false
            progress.setPosition(0L)

            controllerHolder.pauseAndRewind()

            saveQueueState()
            stateStore.savePlaybackState {
                PlaybackState(
                    songId = currentTrackItem.songId,
                    title = currentTrackItem.title,
                    artist = currentTrackItem.artist,
                    coverUrl = currentTrackItem.coverUrl,
                    lastPositionMs = 0L
                )
            }
        } else {
            _currentTrack.value = null
            _isPlaying.value = false
            progress.setPosition(0L)
            progress.setDuration(0L)

            controllerHolder.stopAndClear()

            saveQueueState()
            stateStore.savePlaybackState {
                PlaybackState(
                    songId = -1L,
                    title = "",
                    artist = "",
                    coverUrl = "",
                    lastPositionMs = 0L
                )
            }
        }
    }

    private fun saveQueueState() {
        stateStore.saveQueue(playbackQueue)
    }

    private fun fetchUrlAndPlay(index: Int, startPosition: Long = 0, networkRetryAttempt: Int = 0, prefetchedUrl: String? = null) {
        val item = playbackQueue.itemAt(index) ?: return

        activePlayJob?.cancel()
        roaming.cancel()
        networkGuard.cancelRecoveryWait()

        activePlayJob = scope.launch {
            if (networkGuard.blockPlaybackOnMobile()) return@launch

            playbackQueue.setCurrentIndex(index)
            saveQueueState()

            // 立即重置当前进度与时长，避免上一首歌曲的数据在加载新歌期间残留导致进度条闪烁
            progress.resetTo(startPosition)

            roaming.prefetchOnPlay(item.songId, index)

            // 命中预取缓存时跳过网络请求，最大限度缩短切歌间隙
            if (prefetchedUrl != null) {
                val mediaItem = item.toMediaItem(prefetchedUrl, playbackQueue.playContext.value)
                controllerHolder.playItem(mediaItem, playbackQueue.playMode.value, startPosition)
                return@launch
            }

            repository.getSongUrl(item.songId).collect { result ->
                result.onSuccess { url ->
                    val mediaItem = item.toMediaItem(url, playbackQueue.playContext.value)
                    controllerHolder.playItem(mediaItem, playbackQueue.playMode.value, startPosition)
                }.onFailure { throwable ->
                    AppLogger.w(TAG, "获取播放URL失败 songId=${item.songId} index=$index networkRetryAttempt=$networkRetryAttempt", throwable)
                    if (throwable is AppError.NetworkError) {
                        handleNetworkFailure(index, startPosition, networkRetryAttempt)
                    } else {
                        skipToNextOnError(index)
                    }
                }
            }
        }
    }

    // 临近播完时提前预取下一首链接，供 playNextOnEnded 命中缓存零等待衔接
    private fun maybePrefetchNextTrackUrl(remainingMs: Long) {
        if (remainingMs > PREFETCH_URL_WINDOW_MS) return
        if (playbackQueue.playMode.value == PlayMode.SINGLE_LOOP) return
        val nextItem = playbackQueue.nextItemByMode() ?: return
        if (prefetchTriggeredForSongId == nextItem.songId) return
        prefetchTriggeredForSongId = nextItem.songId

        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            repository.getSongUrl(nextItem.songId).collect { result ->
                result.onSuccess { url ->
                    prefetchedNextUrl = PrefetchedUrl(nextItem.songId, url)
                }
            }
        }
    }

    // 歌曲自然播完时自动切下一首，优先使用预取缓存实现无缝衔接
    private fun playNextOnEnded() {
        if (playbackQueue.isEmpty) return
        consecutiveErrors = 0
        val nextIndex = playbackQueue.nextIndex()
        val nextItem = playbackQueue.itemAt(nextIndex)
        val cachedUrl = prefetchedNextUrl?.takeIf { it.songId == nextItem?.songId }?.url
        prefetchedNextUrl = null
        fetchUrlAndPlay(nextIndex, prefetchedUrl = cachedUrl)
    }

    // 网络类失败原地重试同一首（退避延迟），重试耗尽后不放弃，转为等待网络恢复自动续播
    private fun handleNetworkFailure(index: Int, startPosition: Long, attempt: Int) {
        if (attempt < NETWORK_RETRY_DELAYS_MS.size) {
            val delayMs = NETWORK_RETRY_DELAYS_MS[attempt]
            activePlayJob = scope.launch {
                delay(delayMs)
                fetchUrlAndPlay(index, startPosition, attempt + 1)
            }
            return
        }

        val songId = playbackQueue.itemAt(index)?.songId
        AppLogger.e(TAG, "网络异常重试 $attempt 次后仍失败，等待网络恢复后自动续播 songId=$songId index=$index")
        networkGuard.awaitNetworkRecovery {
            fetchUrlAndPlay(index, startPosition)
        }
    }

    fun release() {
        reportPlayedTrack()
        networkGuard.unregister()
        sleepTimer.cancel()
        activePlayJob?.cancel()
        prefetchJob?.cancel()
        roaming.cancel()
    }

    // 歌曲一开始播放就立即打卡（进「最近播放」），跟切歌时补报的时长上报分开、各自独立失败互不影响
    private fun reportStartPlay(mediaItem: MediaItem) {
        val songId = mediaItem.mediaId.toLongOrNull() ?: return
        scope.launch {
            repository.reportStartPlay(songId).collect { result ->
                result.onFailure { AppLogger.w(TAG, "打卡上报 startplay 失败 songId=$songId", it) }
            }
        }
    }

    // 切歌/退出播放时把刚播完的这首上报播放时长打卡（用挂钟时间差算出的播放时长）；听不满 5 秒的跳过不算
    private fun reportPlayedTrack() {
        val songId = _currentTrack.value?.mediaId?.toLongOrNull() ?: return
        val playedSeconds = currentTrackPlayedMs() / 1000
        if (playedSeconds < 5) {
            AppLogger.i(TAG, "打卡上报跳过（未满5秒）songId=$songId playedSeconds=$playedSeconds")
            return
        }
        AppLogger.i(TAG, "打卡上报触发 songId=$songId playedSeconds=$playedSeconds")
        scope.launch {
            repository.reportPlayEnd(songId, playedSeconds).collect { result ->
                result.onFailure { AppLogger.w(TAG, "打卡上报 play 失败 songId=$songId", it) }
            }
        }
    }

    // 当前曲目从开始播放到现在的挂钟时长，扣掉暂停期间（含正在暂停中的这一段）
    private fun currentTrackPlayedMs(): Long {
        val now = SystemClock.elapsedRealtime()
        val ongoingPauseMs = trackLastPauseElapsedMs?.let { now - it } ?: 0L
        return (now - trackStartElapsedMs - trackPausedAccumMs - ongoingPauseMs).coerceAtLeast(0L)
    }

    // 切换到新曲目/从持久化状态恢复播放进度时，重置计时基准
    private fun resetTrackTiming(startPlaying: Boolean = _isPlaying.value) {
        trackStartElapsedMs = SystemClock.elapsedRealtime()
        trackPausedAccumMs = 0L
        trackLastPauseElapsedMs = if (startPlaying) null else trackStartElapsedMs
    }

    private fun skipToNextOnError(failedIndex: Int) {
        consecutiveErrors++
        if (consecutiveErrors >= 3 || playbackQueue.size <= 1) {
            AppLogger.e(TAG, "连续 $consecutiveErrors 次播放失败，放弃自动切歌 failedIndex=$failedIndex queueSize=${playbackQueue.size}")
            scope.launch {
                Toast.makeText(context, "无法获取该歌曲的播放链接", Toast.LENGTH_SHORT).show()
            }
            return
        }
        fetchUrlAndPlay(playbackQueue.nextIndexFrom(failedIndex))
    }

    // 重新加载当前歌曲（用于切换音质时立即生效）
    fun reloadCurrentTrack() {
        val index = playbackQueue.currentIndex.value
        if (index >= 0 && index < playbackQueue.size) {
            val currentPos = controllerHolder.currentPosition
            fetchUrlAndPlay(index, currentPos)
        }
    }

    // 关闭漫游并还原备份的队列数据
    fun disableRoaming() {
        roaming.disable()
    }

    // 关闭心动模式并还原进入前备份的队列数据
    fun disableIntelligence() {
        if (playbackQueue.playContext.value != CONTEXT_INTELLIGENCE) return
        playbackQueue.restoreSnapshot()
        saveQueueState()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
        if (isPlaying) {
            consecutiveErrors = 0
            trackLastPauseElapsedMs?.let { trackPausedAccumMs += SystemClock.elapsedRealtime() - it }
            trackLastPauseElapsedMs = null
        } else {
            saveState()
            trackLastPauseElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        AppLogger.i(TAG, "切歌: songId=${mediaItem?.mediaId} reason=${transitionReasonName(reason)}")
        reportPlayedTrack()
        resetTrackTiming()
        _currentTrack.value = mediaItem
        playbackQueue.setPlayContext(mediaItem?.mediaMetadata?.extras?.getString("playContext"))
        if (mediaItem != null) {
            reportStartPlay(mediaItem)
            // 切歌过渡时，应当立即将当前进度重置，防止读取上一首残留位置或缓冲位置
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                progress.setPosition(0L)
            }
            progress.updateDurationFromController()
            saveState()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        AppLogger.i(TAG, "播放状态变化: ${playbackStateName(playbackState)}")
        if (playbackState == Player.STATE_READY) {
            progress.updateDurationFromController()
            playbackQueue.nextItemByMode()?.let { coverPreloader.preload(it.coverUrl) }
        }
        // 单曲循环由 ExoPlayer REPEAT_MODE_ONE 处理，不会到达 STATE_ENDED
        if (playbackState == Player.STATE_ENDED && playbackQueue.playMode.value != PlayMode.SINGLE_LOOP) {
            playNextOnEnded()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        AppLogger.e(TAG, "播放器报错 errorCode=${error.errorCodeName} songId=${_currentTrack.value?.mediaId}", error)
        scope.launch {
            Toast.makeText(context, "当前歌曲无法播放，已自动跳过", Toast.LENGTH_SHORT).show()
        }
        skipToNextOnError(playbackQueue.currentIndex.value)
    }

    private fun playbackStateName(state: Int) = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($state)"
    }

    private fun transitionReasonName(reason: Int) = when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
        else -> "UNKNOWN($reason)"
    }
}
