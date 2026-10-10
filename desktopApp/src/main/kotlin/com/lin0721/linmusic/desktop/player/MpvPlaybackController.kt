package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.CrossfadePolicy
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.PlaybackController.Companion.CONTEXT_INTELLIGENCE
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.PlaybackQueue
import com.lin0721.linmusic.desktop.platform.DesktopPreferences
import com.lin0721.linmusic.desktop.player.cache.AudioCache
import com.lin0721.linmusic.core.player.PlaybackState
import com.lin0721.linmusic.core.player.PlaybackStateStore
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.SimilarRoamingController
import com.lin0721.linmusic.core.player.SleepTimer
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.desktop.player.mpv.MpvEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MpvPlayback"

// 连续取地址失败达到该数即停止，避免整队无版权时空转
private const val MAX_CONSECUTIVE_FAILURES = 3

// 听不满该时长的曲目不上报播放时长，与 Android 端一致
private const val MIN_REPORT_PLAYED_MS = 5_000L

// 上一首按钮：已播放超过该时长时回到本曲开头
private const val RESTART_THRESHOLD_MS = 3_000L

private const val TICK_INTERVAL_MS = 1_000L

private const val PERIODIC_STATE_SAVE_INTERVAL_MS = 5_000L

// 退出时同步落盘的最长等待
private const val EXIT_SAVE_TIMEOUT_MS = 1_500L

private const val CROSSFADE_PREPARE_TIMEOUT_MS = 8_000L
private const val CROSSFADE_PREPARE_POLL_MS = 100L

// 到点时下一首仍是它才执行
private class CrossfadePlan(val index: Int, val songId: Long, val fadeMs: Long)

// record 为空表示本地文件或缓存命中，非空表示联网播放时录制缓存
private class Playback(val url: String, val record: RecordRequest?)

class MpvPlaybackController(
    private val repository: PlaybackRepository,
    private val settingsPreferences: SettingsPreferences,
    private val preferences: PlaybackPreferences,
    private val desktopPreferences: DesktopPreferences,
    private val localAudioOf: suspend (songId: Long) -> String?,
    private val scope: CoroutineScope,
    // 仅测试用
    engineOptions: Map<String, String> = emptyMap(),
    private val audioCache: AudioCache? = null
) : PlaybackController, AudioOutputControl, MpvEngine.Listener {

    private val playbackQueue = PlaybackQueue()
    private val stateStore = PlaybackStateStore(scope, preferences)
    private val sleepTimer = SleepTimer(scope) { pause() }
    private val roaming = SimilarRoamingController(scope, repository, settingsPreferences, playbackQueue, stateStore)

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

    override val sleepTimerRemaining: StateFlow<Long> = sleepTimer.remaining
    override val playContext: StateFlow<String?> = playbackQueue.playContext
    override val playSource: StateFlow<PlaySource?> = playbackQueue.playSource
    override val currentIndex: StateFlow<Int> = playbackQueue.currentIndex
    override val playMode: StateFlow<PlayMode> = playbackQueue.playMode
    override val queue: StateFlow<List<QueueItem>> = playbackQueue.items

    override val currentQueueItem: StateFlow<QueueItem?> = combine(playbackQueue.items, playbackQueue.currentIndex) { items, index ->
        if (index in items.indices) items[index] else null
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override val previousQueueItem: StateFlow<QueueItem?> = combine(playbackQueue.items, playbackQueue.currentIndex) { items, index ->
        if (items.size > 1 && index in items.indices) items[(index - 1 + items.size) % items.size] else null
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override val nextQueueItem: StateFlow<QueueItem?> = combine(playbackQueue.items, playbackQueue.currentIndex) { items, index ->
        if (items.size > 1 && index in items.indices) items[(index + 1) % items.size] else null
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _audioDevices = MutableStateFlow<List<AudioDevice>>(emptyList())
    override val audioDevices: StateFlow<List<AudioDevice>> = _audioDevices.asStateFlow()

    private val _audioDevice = MutableStateFlow(AUTO_AUDIO_DEVICE)
    override val audioDevice: StateFlow<String> = _audioDevice.asStateFlow()

    // 桌面端专用提示（取地址失败等），由界面层弹出
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var playJob: Job? = null
    private var consecutiveFailures = 0

    // mpv 已载入的曲目；冷启动恢复出的曲目只有元数据，首次播放时才取地址载入
    private var loadedSongId: Long? = null
    private var restoreAttempted = false
    private var lastPeriodicSaveMs = 0L

    // 播放时长上报：累计实际发声的挂钟时长，暂停段不计
    private var reportingSongId: Long? = null
    private var reportingSource: PlaySource? = null
    private var playedMs = 0L
    private var playingSince: Long? = null

    // 交叉淡化：触发位置由 mpv 事件线程读取，其余状态只在主线程读写
    @Volatile private var crossfadeEnabled = false
    private var crossfadeSettingMs = CrossfadePolicy.DEFAULT_DURATION_MS
    private var crossfadePlan: CrossfadePlan? = null
    private var crossfadePrepareJob: Job? = null
    @Volatile private var crossfadeTriggerMs = Long.MAX_VALUE

    // 每首歌只尝试预载一次，避免失败后每个 tick 重复取地址
    private var crossfadeAttemptedSongId: Long? = null

    // 必须最后创建：mpv 事件线程一启动就可能回调，此前所有状态字段须已初始化
    private val engine = MpvEnginePair(this, scope, engineOptions, onRecordingFinished = ::commitRecording)

    init {
        scope.launch {
            while (true) {
                delay(TICK_INTERVAL_MS)
                onTick()
            }
        }
        scope.launch { restoreAudioDevice() }
        scope.launch {
            settingsPreferences.crossfadeEnabled.collect { enabled ->
                crossfadeEnabled = enabled
                if (!enabled) abortCrossfadePlan()
            }
        }
        scope.launch { settingsPreferences.crossfadeDurationMs.collect { crossfadeSettingMs = it } }
    }

    // 保存的设备已不在系统列表里时保持跟随系统默认，偏好不清除，设备插回后下次启动仍可用
    private suspend fun restoreAudioDevice() {
        val saved = desktopPreferences.audioDevice.first()
        val devices = engine.audioDevices()
        _audioDevices.value = selectAudioDevices(devices, saved)
        if (saved == AUTO_AUDIO_DEVICE || devices.none { it.name == saved }) return
        if (engine.setAudioDevice(saved)) _audioDevice.value = saved
    }

    override fun refreshAudioDevices() {
        val devices = engine.audioDevices()
        // 读取失败时返回空列表，不据此判断设备已断开
        if (devices.isEmpty()) return
        val current = _audioDevice.value
        // 展示用筛选后的列表；断开判断用完整列表，避免把被筛掉的设备误认为仍在
        _audioDevices.value = selectAudioDevices(devices, current)
        AppLogger.i(TAG, "输出设备 ${_audioDevices.value.size}/${devices.size} 个（当前：$current）")
        if (current != AUTO_AUDIO_DEVICE && devices.none { it.name == current } && engine.setAudioDevice(AUTO_AUDIO_DEVICE)) {
            _audioDevice.value = AUTO_AUDIO_DEVICE
            _messages.tryEmit("输出设备已断开，已切回系统默认")
        }
    }

    override fun setAudioDevice(name: String) {
        if (name == _audioDevice.value) return
        if (!engine.setAudioDevice(name)) {
            _messages.tryEmit("切换输出设备失败")
            return
        }
        _audioDevice.value = name
        scope.launch { desktopPreferences.saveAudioDevice(name) }
    }

    // 恢复上次退出时的队列、播放模式与曲目进度，恢复后保持暂停
    override suspend fun initController() {
        if (restoreAttempted) return
        restoreAttempted = true
        val mode = stateStore.loadPlayMode()
        val queueState = stateStore.loadQueueState()
        val lastTrack = stateStore.loadLastTrack()
        // 读取期间用户已开始播放时不再覆盖
        if (_nowPlaying.value != null || !playbackQueue.isEmpty) return

        playbackQueue.setPlayMode(mode)
        playbackQueue.restore(queueState.queue, queueState.currentIndex, queueState.playContext, queueState.playSource)
        if (lastTrack == null) return

        val current = playbackQueue.currentItem()
        if (current == null || current.songId != lastTrack.songId) {
            val index = playbackQueue.items.value.indexOfFirst { it.songId == lastTrack.songId }
            if (index >= 0) {
                playbackQueue.setCurrentIndex(index)
            } else {
                playbackQueue.replaceWithSingle(QueueItem(lastTrack.songId, lastTrack.title, lastTrack.artist, lastTrack.coverUrl))
            }
        }
        _nowPlaying.value = NowPlaying(
            mediaId = lastTrack.songId.toString(),
            title = lastTrack.title,
            artist = lastTrack.artist,
            artworkUri = lastTrack.coverUrl.takeIf { it.isNotBlank() }
        )
        _currentPosition.value = lastTrack.lastPositionMs
        _duration.value = lastTrack.durationMs
    }

    override fun playQueue(
        items: List<QueueItem>,
        startIndex: Int,
        playContext: String?,
        source: PlaySource?,
        startPositionMs: Long
    ) {
        if (items.isEmpty()) return

        if (playContext == SimilarRoamingController.CONTEXT_ROAMING) {
            roaming.prepare()
        } else if (playContext == CONTEXT_INTELLIGENCE) {
            // 备份进入心动模式前的队列，供关闭时还原
            playbackQueue.takeSnapshot()
        }

        val playingSongId = _nowPlaying.value?.songId
        playbackQueue.setPlayContext(playContext)
        playbackQueue.setPlaySource(source)
        playbackQueue.replaceAll(items, startIndex)
        consecutiveFailures = 0
        saveQueueState()

        val index = playbackQueue.currentIndex.value
        val target = playbackQueue.itemAt(index)
        val isSeedPlaying = target != null && target.songId == playingSongId
        if ((playContext == CONTEXT_INTELLIGENCE || playContext == SimilarRoamingController.CONTEXT_ROAMING) && isSeedPlaying) {
            // 以当前曲目为种子时平滑衔接，不重载音频
            roaming.prefetchOnPlay(target.songId, index)
            return
        }
        playIndex(index, startPositionMs.coerceAtLeast(0L))
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
        playbackQueue.replaceWithSingle(QueueItem(songId, title, artist, coverUrl))
        playbackQueue.setPlayContext(playContext)
        playbackQueue.setPlaySource(null)
        consecutiveFailures = 0
        saveQueueState()
        playIndex(0, startPosition, knownUrl = url)
    }

    override fun addToPlayNext(items: List<QueueItem>) {
        if (items.isEmpty()) return
        if (playbackQueue.isEmpty) {
            playQueue(items, 0, null)
            return
        }
        playbackQueue.insertNext(items)
        saveQueueState()
    }

    override fun playNext() {
        consecutiveFailures = 0
        nextIndex(fromUser = true)?.let { playIndex(it) }
    }

    override fun playPrevious() {
        if (playbackQueue.isEmpty) return
        consecutiveFailures = 0
        playIndex(playbackQueue.previousIndex())
    }

    override fun skipToPrevious() {
        if (_currentPosition.value > RESTART_THRESHOLD_MS) seekTo(0) else playPrevious()
    }

    override fun playAtIndex(index: Int) {
        if (index !in 0 until playbackQueue.size) return
        consecutiveFailures = 0
        playIndex(index)
    }

    override fun removeFromQueue(index: Int) {
        if (index !in 0 until playbackQueue.size || playbackQueue.size <= 1) return
        val replayIndex = playbackQueue.removeAt(index)
        if (replayIndex >= 0) playIndex(replayIndex)
        saveQueueState()
    }

    override fun moveInQueue(from: Int, to: Int) {
        if (playbackQueue.move(from, to)) saveQueueState()
    }

    // 与 Android 一致：只保留当前曲目并回到开头暂停
    override fun clearQueue() {
        val kept = playbackQueue.keepOnlyCurrent()
        abortCrossfadePlan()
        playJob?.cancel()
        finishReporting()
        _playWhenReady.value = false
        _isPlaying.value = false
        _currentPosition.value = 0L
        if (kept == null) {
            engine.stop()
            loadedSongId = null
            _nowPlaying.value = null
            _duration.value = 0L
        } else if (loadedSongId == kept.songId) {
            engine.setPaused(true)
            engine.seekTo(0L)
        }
        saveQueueState()
        saveState()
    }

    override fun toggleShuffle() {
        applyMode(if (playbackQueue.playMode.value == PlayMode.SHUFFLE) PlayMode.LIST_LOOP else PlayMode.SHUFFLE)
    }

    override fun toggleRepeat() {
        applyMode(if (playbackQueue.playMode.value == PlayMode.SINGLE_LOOP) PlayMode.LIST_LOOP else PlayMode.SINGLE_LOOP)
    }

    override fun rotatePlayMode() {
        applyMode(
            when (playbackQueue.playMode.value) {
                PlayMode.LIST_LOOP -> PlayMode.SHUFFLE
                PlayMode.SHUFFLE -> PlayMode.SINGLE_LOOP
                PlayMode.SINGLE_LOOP -> PlayMode.LIST_LOOP
            }
        )
    }

    private fun applyMode(mode: PlayMode) {
        playbackQueue.applyMode(mode, playbackQueue.currentItem())
        stateStore.savePlayMode(mode)
        saveQueueState()
    }

    override fun pause() {
        _playWhenReady.value = false
        engine.setPaused(true)
        saveState()
    }

    override fun resume() {
        if (_nowPlaying.value == null) return
        val current = playbackQueue.currentItem()
        // 恢复出的曲目尚未载入，按记录的进度取地址起播
        if (current != null && loadedSongId != current.songId) {
            playIndex(playbackQueue.currentIndex.value, _currentPosition.value)
            return
        }
        _playWhenReady.value = true
        engine.setPaused(false)
    }

    override fun togglePlayPause() {
        if (_playWhenReady.value) pause() else resume()
    }

    override fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        _currentPosition.value = target
        if (loadedSongId != null) engine.seekTo(target)
        saveState()
    }

    override fun reloadCurrentTrack() {
        val index = playbackQueue.currentIndex.value
        if (index in 0 until playbackQueue.size) playIndex(index, _currentPosition.value)
    }

    override fun setSleepTimer(minutes: Int) {
        sleepTimer.start(minutes)
    }

    override fun setPositionUpdateInterval(intervalMs: Long) = Unit

    override fun cancelPendingSkip(): Boolean = false

    override fun disableRoaming() {
        roaming.disable()
    }

    override fun disableIntelligence() {
        if (playbackQueue.playContext.value != CONTEXT_INTELLIGENCE) return
        playbackQueue.exitSpecialContext()
        saveQueueState()
    }

    fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _volume.value = clamped
        engine.setVolume(clamped)
    }

    // 退出时同步落盘，异步写入来不及在进程结束前完成
    fun release() {
        playJob?.cancel()
        sleepTimer.cancel()
        roaming.cancel()
        finishReporting()
        val state = currentPlaybackState()
        val items = playbackQueue.original
        val index = playbackQueue.currentIndexInOriginal()
        val context = playbackQueue.playContext.value
        val source = playbackQueue.playSource.value
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(EXIT_SAVE_TIMEOUT_MS) {
                runCatching {
                    if (state != null) preferences.savePlaybackState(state)
                    preferences.saveQueueState(items, index, context, source)
                }.onFailure { AppLogger.w(TAG, "退出时保存播放状态失败", it) }
            }
        }
        engine.release()
    }

    override fun onPositionChanged(positionMs: Long) {
        _currentPosition.value = positionMs
        if (positionMs >= crossfadeTriggerMs) {
            crossfadeTriggerMs = Long.MAX_VALUE
            scope.launch { startCrossfade() }
        }
    }

    override fun onDurationChanged(durationMs: Long) {
        _duration.value = durationMs
    }

    // 以下回调来自 mpv 事件线程，涉及计时与计数的状态统一切回主线程处理
    override fun onPauseChanged(paused: Boolean) {
        scope.launch {
            _isPlaying.value = !paused && _nowPlaying.value != null
            if (paused) pauseClock() else startClock()
        }
    }

    override fun onFileLoaded() {
        scope.launch {
            consecutiveFailures = 0
            _isPlaying.value = _playWhenReady.value
            startClock()
        }
    }

    override fun onEnded(isError: Boolean) {
        scope.launch {
            if (isError) {
                AppLogger.w(TAG, "音频解码失败，跳到下一首")
                handleFailure("这首歌暂时无法播放")
            } else {
                nextIndex(fromUser = false)?.let { playIndex(it) } ?: run { _isPlaying.value = false }
            }
        }
    }

    // 漫游续接与定期存档，只在实际发声时推进
    private suspend fun onTick() {
        if (!_isPlaying.value) return
        val songId = loadedSongId ?: return
        val dur = _duration.value
        if (dur > 0L) roaming.onProgressTick(songId, dur - _currentPosition.value)
        maybePrepareCrossfade(songId)
        val now = System.currentTimeMillis()
        if (now - lastPeriodicSaveMs >= PERIODIC_STATE_SAVE_INTERVAL_MS) {
            lastPeriodicSaveMs = now
            saveState()
        }
    }

    private fun playIndex(index: Int, startMs: Long = 0L, knownUrl: String? = null) {
        val item = playbackQueue.itemAt(index) ?: return
        abortCrossfadePlan()
        crossfadeAttemptedSongId = null
        playJob?.cancel()
        finishReporting()
        // 取播放地址在弱网下可能很久才返回，界面已切到新歌，旧歌不能继续出声；换音质重载同一首不打断
        if (loadedSongId != item.songId) engine.setPaused(true)
        playbackQueue.setCurrentIndex(index)
        loadedSongId = null
        _currentPosition.value = startMs
        _duration.value = 0L
        _playWhenReady.value = true
        _isPlaying.value = false
        _nowPlaying.value = NowPlaying(
            mediaId = item.songId.toString(),
            title = item.title,
            artist = item.artist,
            artworkUri = item.coverUrl.takeIf { it.isNotBlank() }
        )
        saveQueueState()
        playJob = scope.launch {
            val playback = knownUrl?.let { Playback(it, null) } ?: resolvePlayback(item.songId).getOrElse { error ->
                AppLogger.w(TAG, "取播放地址失败 songId=${item.songId}", error)
                handleFailure("《${item.title}》暂无版权或需要会员")
                return@launch
            }
            // 从中途起播录不出完整文件
            engine.load(playback.url, startMs, record = playback.record.takeIf { startMs == 0L })
            loadedSongId = item.songId
            beginReporting(item.songId)
            roaming.prefetchOnPlay(item.songId, index)
            saveState()
        }
    }

    // 已下载 > 缓存命中 > 联网；联网失败退回任意音质缓存
    // 缓存命中不受开关影响，开关只管是否录制
    private suspend fun resolvePlayback(songId: Long): Result<Playback> {
        localAudioOf(songId)?.let { return Result.success(Playback(it, null)) }
        val level = settingsPreferences.wifiQuality.first()
        audioCache?.completeFile(songId, level)?.let { return Result.success(Playback(it.absolutePath, null)) }
        return repository.getSongUrl(songId).first().fold(
            onSuccess = { url ->
                val record = audioCache?.takeIf { settingsPreferences.streamCacheEnabled.first() }
                    ?.let { RecordRequest(songId, it.tempFile(songId, level).absolutePath) }
                Result.success(Playback(url, record))
            },
            onFailure = { error ->
                audioCache?.anyCompleteFile(songId)?.let { Result.success(Playback(it.absolutePath, null)) }
                    ?: Result.failure(error)
            }
        )
    }

    // 来自 mpv 事件线程；改名要等 mpv 释放文件，放 IO 线程重试
    private fun commitRecording(songId: Long, path: String) {
        val cache = audioCache ?: return
        scope.launch(Dispatchers.IO) {
            if (cache.commit(File(path), settingsPreferences.audioCacheMaxSize.first())) {
                AppLogger.i(TAG, "已缓存 songId=$songId")
            }
        }
    }

    private fun maybePrepareCrossfade(songId: Long) {
        if (!crossfadeEnabled || !_playWhenReady.value || crossfadePlan != null) return
        if (crossfadePrepareJob?.isActive == true || engine.isFading || crossfadeAttemptedSongId == songId) return
        val duration = _duration.value
        val remaining = duration - _currentPosition.value
        val fadeMs = CrossfadePolicy.autoFadeMs(crossfadeSettingMs, duration)
        if (fadeMs <= 0L || remaining <= 0L || remaining > CrossfadePolicy.prefetchWindowMs(true)) return

        val nextIdx = nextIndex(fromUser = false) ?: return
        val next = playbackQueue.itemAt(nextIdx) ?: return
        // 单曲循环或队列只有一首时没有可交叉的下一首
        if (nextIdx == playbackQueue.currentIndex.value || next.songId == songId) return

        crossfadeAttemptedSongId = songId
        crossfadePrepareJob = scope.launch {
            val playback = resolvePlayback(next.songId).getOrNull() ?: return@launch
            if (!engine.prepareSpare(playback.url, playback.record)) return@launch
            val ready = withTimeoutOrNull(CROSSFADE_PREPARE_TIMEOUT_MS) {
                while (!engine.spareReady) delay(CROSSFADE_PREPARE_POLL_MS)
                true
            }
            // 预载期间已切歌或被取消时，备用引擎里的内容作废
            if (ready != true || loadedSongId != songId) {
                engine.discardSpare()
                return@launch
            }
            crossfadePlan = CrossfadePlan(nextIdx, next.songId, fadeMs)
            crossfadeTriggerMs = CrossfadePolicy.triggerPositionMs(duration, fadeMs)
        }
    }

    // 到点时下一首仍是计划中的才交接，否则走常规切歌
    private fun startCrossfade() {
        val plan = crossfadePlan ?: return
        crossfadePlan = null
        val stillNext = nextIndex(fromUser = false) == plan.index &&
            playbackQueue.itemAt(plan.index)?.songId == plan.songId
        if (!_playWhenReady.value || loadedSongId == null || !stillNext) {
            engine.discardSpare()
            return
        }
        val (position, duration) = engine.promote(plan.fadeMs) ?: run {
            engine.discardSpare()
            return
        }
        val item = playbackQueue.itemAt(plan.index) ?: return
        finishReporting()
        playbackQueue.setCurrentIndex(plan.index)
        loadedSongId = item.songId
        consecutiveFailures = 0
        _currentPosition.value = position
        _duration.value = duration
        _nowPlaying.value = NowPlaying(
            mediaId = item.songId.toString(),
            title = item.title,
            artist = item.artist,
            artworkUri = item.coverUrl.takeIf { it.isNotBlank() }
        )
        saveQueueState()
        beginReporting(item.songId)
        startClock()
        roaming.prefetchOnPlay(item.songId, plan.index)
        saveState()
    }

    private fun abortCrossfadePlan() {
        crossfadeTriggerMs = Long.MAX_VALUE
        crossfadePrepareJob?.cancel()
        crossfadePrepareJob = null
        if (crossfadePlan != null) engine.discardSpare()
        crossfadePlan = null
    }

    private fun handleFailure(message: String) {
        consecutiveFailures++
        _messages.tryEmit(message)
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES || playbackQueue.size <= 1) {
            _messages.tryEmit("连续多首无法播放，已停止")
            consecutiveFailures = 0
            _playWhenReady.value = false
            _isPlaying.value = false
            loadedSongId = null
            engine.stop()
            return
        }
        nextIndex(fromUser = true)?.let { playIndex(it) }
    }

    // 单曲循环仅在自然播完时重播本曲，手动切歌照常前进；随机顺序已由队列打乱
    private fun nextIndex(fromUser: Boolean): Int? {
        if (playbackQueue.isEmpty) return null
        return if (playbackQueue.playMode.value == PlayMode.SINGLE_LOOP && !fromUser) {
            playbackQueue.currentIndex.value.coerceAtLeast(0)
        } else {
            playbackQueue.nextIndex()
        }
    }

    private fun saveQueueState() {
        stateStore.saveQueue(playbackQueue)
    }

    private fun saveState() {
        stateStore.savePlaybackState { currentPlaybackState() ?: PlaybackState() }
    }

    private fun currentPlaybackState(): PlaybackState? {
        val track = _nowPlaying.value ?: return null
        val songId = track.songId ?: return null
        return PlaybackState(
            songId = songId,
            title = track.title,
            artist = track.artist,
            coverUrl = track.artworkUri.orEmpty(),
            lastPositionMs = _currentPosition.value,
            durationMs = _duration.value
        )
    }

    private fun beginReporting(songId: Long) {
        reportingSongId = songId
        val source = playbackQueue.playSource.value
        reportingSource = source
        playedMs = 0L
        playingSince = null
        scope.launch {
            repository.reportStartPlay(songId, source).collect { result ->
                result.onFailure { AppLogger.w(TAG, "打卡上报 startplay 失败 songId=$songId", it) }
            }
        }
    }

    private fun startClock() {
        if (reportingSongId != null && playingSince == null) playingSince = System.currentTimeMillis()
    }

    private fun pauseClock() {
        val since = playingSince ?: return
        playedMs += System.currentTimeMillis() - since
        playingSince = null
    }

    private fun finishReporting() {
        val songId = reportingSongId ?: return
        pauseClock()
        val played = playedMs
        reportingSongId = null
        playedMs = 0L
        if (played < MIN_REPORT_PLAYED_MS) return
        val playedSeconds = played / 1000
        val source = reportingSource
        scope.launch {
            repository.reportPlayEnd(songId, playedSeconds, source).collect { result ->
                result.onFailure { AppLogger.w(TAG, "打卡上报 play 失败 songId=$songId", it) }
            }
        }
    }
}
