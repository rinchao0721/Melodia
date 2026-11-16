package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

private const val TAG = "PersonalFmController"

// 私人FM播放控制：管理快照备份、队尾自动补全、不喜欢切歌与模式切换
class PersonalFmController(
    private val scope: CoroutineScope,
    private val repository: PlaybackRepository,
    private val settingsPreferences: SettingsPreferences,
    private val queue: PlaybackQueue,
    private val stateStore: PlaybackStateStore
) {

    companion object {
        const val CONTEXT_PERSONAL_FM = PlaybackController.CONTEXT_PERSONAL_FM

        // 触发按进度兜底补全的剩余时间阈值
        private const val PREFETCH_THRESHOLD_MS = 15_000L

        // 未播曲目少于该数时拉取下一批
        private const val MIN_UNPLAYED_ITEMS = 2

        private const val RECENT_FM_TRACK_LIMIT = 3
    }

    private val _currentMode = MutableStateFlow(PersonalFmMode.DEFAULT)
    val currentMode: StateFlow<PersonalFmMode> = _currentMode.asStateFlow()

    private var fmJob: Job? = null
    private var saveModeJob: Job? = null
    private var lastTriggeredSongId = -1L

    // 防止初始化异步读取首帧覆盖同步触发的模式切换，或预取抢在首帧读取前使用默认模式
    @Volatile
    private var userSwitchedMode = false

    @Volatile
    private var modeInitialized = false

    val isPersonalFm: Boolean get() = queue.playContext.value == CONTEXT_PERSONAL_FM

    init {
        scope.launch {
            var isFirst = true
            settingsPreferences.personalFmMode.collect { mode ->
                modeInitialized = true
                if (isFirst) {
                    isFirst = false
                    if (!userSwitchedMode) _currentMode.value = mode
                } else {
                    _currentMode.value = mode
                }
            }
        }
    }

    fun cancel() {
        fmJob?.cancel()
    }

    // 首次进入 FM 时备份原队列，并强制切回列表循环（禁用单曲循环/随机）
    fun prepare() {
        if (!isPersonalFm) {
            queue.takeSnapshot()
        }
        queue.setPlayMode(PlayMode.LIST_LOOP)
        stateStore.savePlayMode(PlayMode.LIST_LOOP)
        lastTriggeredSongId = -1L
    }

    // 切歌起播时记录最近 FM 曲目，并检查余量，未播曲目不足 2 首则预取下一批
    fun prefetchOnPlay(songId: Long) {
        if (!isPersonalFm) return
        recordRecentFmFromQueue()
        if (queue.upcomingCount >= MIN_UNPLAYED_ITEMS) return
        if (fmJob?.isActive == true) return
        fmJob = scope.launch {
            fetchAndExtend(resolveActiveMode())
        }
    }

    // 兜底续接：若前一次预取失败导致播到队尾，临近结束时再补拉一次
    suspend fun onProgressTick(songId: Long, remainingMs: Long) {
        if (!isPersonalFm) return
        if (queue.upcomingCount > 0) return
        if (remainingMs > PREFETCH_THRESHOLD_MS) return
        if (lastTriggeredSongId == songId) return
        if (fmJob?.isActive == true) return

        lastTriggeredSongId = songId
        fetchAndExtend(resolveActiveMode())
    }

    // 切换 FM 模式：保存偏好，若当前在 FM 中则清掉未播曲目并按新模式重拉，当前曲不打断
    fun switchMode(newMode: PersonalFmMode) {
        userSwitchedMode = true
        _currentMode.value = newMode
        saveModeJob?.cancel()
        saveModeJob = scope.launch {
            settingsPreferences.savePersonalFmMode(newMode)
        }
        if (!isPersonalFm) return
        fmJob?.cancel()
        queue.clearUpcoming()
        stateStore.saveQueue(queue)
        lastTriggeredSongId = -1L
        fmJob = scope.launch {
            fetchAndExtend(newMode)
        }
    }

    // 不喜欢当前曲目：发请求不阻塞，无论成败立即切下一首并移出队列；余量不足同时触发拉取
    fun trashCurrent(playedSeconds: Long, onPlayIndex: (Int) -> Unit) {
        if (!isPersonalFm) return
        val curIndex = queue.currentIndex.value
        val current = queue.currentItem() ?: return

        scope.launch {
            repository.trashFmSong(current.songId, playedSeconds).collect { result ->
                result.onFailure {
                    AppLogger.w(TAG, "私人FM标记不喜欢失败 songId=${current.songId}", it)
                }
            }
        }

        if (queue.upcomingCount > 0) {
            val nextIndex = queue.removeAt(curIndex)
            stateStore.saveQueue(queue)
            if (nextIndex >= 0) {
                onPlayIndex(nextIndex)
            }
            if (isPersonalFm && queue.upcomingCount < MIN_UNPLAYED_ITEMS) {
                prefetchOnPlay(queue.currentItem()?.songId ?: -1L)
            }
            return
        }

        // 队尾已无待播曲目时先拉取新一批再移除当前曲，确保移除后直接落到新歌上
        fmJob?.cancel()
        val trashedSongId = current.songId
        fmJob = scope.launch {
            val mode = resolveActiveMode()
            val added = fetchAndExtend(mode, recordRecent = false)
            if (!isPersonalFm) return@launch
            val removeIdx = queue.items.value.indexOfFirst { it.songId == trashedSongId }
            if (removeIdx >= 0 && queue.size > 1) {
                val replayIndex = queue.removeAt(removeIdx)
                stateStore.saveQueue(queue)
                if (replayIndex >= 0) {
                    onPlayIndex(replayIndex)
                }
            }
            if (added && isPersonalFm && queue.upcomingCount < MIN_UNPLAYED_ITEMS) {
                fetchAndExtend(mode)
            }
        }
    }

    // 退出私人FM并还原进入前的队列快照
    fun disable() {
        if (!isPersonalFm) return
        fmJob?.cancel()
        queue.exitSpecialContext()
        stateStore.saveQueue(queue)
    }

    private suspend fun resolveActiveMode(): PersonalFmMode {
        if (modeInitialized || userSwitchedMode) return _currentMode.value
        val persisted = settingsPreferences.personalFmMode.first()
        modeInitialized = true
        if (!userSwitchedMode) {
            _currentMode.value = persisted
        }
        return _currentMode.value
    }

    private suspend fun fetchAndExtend(mode: PersonalFmMode, recordRecent: Boolean = true): Boolean {
        var added = false
        repository.getPersonalFmSongs(mode).collect { result ->
            result.onSuccess { tracks ->
                if (tracks.isEmpty() || !isPersonalFm) return@onSuccess
                val picked = selectPersonalFmSongs(
                    candidates = tracks,
                    queued = queue.items.value
                )
                if (picked.isEmpty()) {
                    AppLogger.w(TAG, "私人FM续接无可用新歌 mode=${mode.key} candidates=${tracks.size}")
                    return@onSuccess
                }
                val appended = picked.map { track ->
                    QueueItem(
                        songId = track.id,
                        title = track.name,
                        artist = track.ar.joinToString("/") { it.name },
                        coverUrl = track.al.picUrl
                    )
                }
                if (queue.extend(appended)) {
                    stateStore.saveQueue(queue)
                    if (recordRecent) recordRecentFmFromQueue()
                    added = true
                }
            }.onFailure {
                AppLogger.w(TAG, "私人FM拉取下一批失败 mode=${mode.key}", it)
            }
        }
        return added
    }

    private fun recordRecentFmFromQueue() {
        if (!isPersonalFm) return
        val items = queue.items.value
        val curIdx = queue.currentIndex.value
        if (items.isEmpty() || curIdx !in items.indices) return
        val recent = items.drop(curIdx)
            .filter { it.songId > 0L }
            .distinctBy { it.songId }
            .take(RECENT_FM_TRACK_LIMIT)
            .map { RecentFmTrack(songId = it.songId, coverUrl = it.coverUrl, title = it.title) }
        if (recent.isNotEmpty()) {
            stateStore.saveRecentFmTracks(recent)
        }
    }
}

// 过滤掉已在队列里的歌（含已播过的）以及本批内重复项
internal fun selectPersonalFmSongs(
    candidates: List<Track>,
    queued: List<QueueItem>
): List<Track> {
    val queuedIds = queued.mapTo(HashSet()) { it.songId }
    return candidates
        .filter { it.id > 0L && it.id !in queuedIds }
        .distinctBy { it.id }
}
