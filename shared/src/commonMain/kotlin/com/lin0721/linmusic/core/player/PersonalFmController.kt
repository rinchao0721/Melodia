package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.repository.PlatformDiscoverRepository
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

private const val TAG = "PersonalFmController"

// 私人FM播放控制：管理快照备份、队尾自动补全、不喜欢切歌、模式与双平台切换
class PersonalFmController(
    private val scope: CoroutineScope,
    private val repository: PlaybackRepository,
    private val settingsPreferences: SettingsPreferences,
    private val queue: PlaybackQueue,
    private val stateStore: PlaybackStateStore,
    private val userPreferences: UserPreferences? = null,
    private val discoverRepository: PlatformDiscoverRepository? = null
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

    private val _currentPlatform = MutableStateFlow(MusicPlatform.NETEASE)
    val currentPlatform: StateFlow<MusicPlatform> = _currentPlatform.asStateFlow()

    private var fmJob: Job? = null
    private var saveModeJob: Job? = null
    private var lastTriggeredSongId = -1L

    // 防止初始化异步读取首帧覆盖同步触发的模式/平台切换
    @Volatile
    private var userSwitchedMode = false

    @Volatile
    private var userSwitchedPlatform = false

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
        if (userPreferences != null) {
            scope.launch {
                combine(
                    userPreferences.currentBrowsePlatform,
                    settingsPreferences.kugouPlatformEnabled
                ) { browse, kugouEnabled ->
                    val effective = if (browse == MusicPlatform.KUGOU && kugouEnabled && discoverRepository != null) {
                        MusicPlatform.KUGOU
                    } else {
                        MusicPlatform.NETEASE
                    }
                    effective to kugouEnabled
                }.distinctUntilChanged().collect { (effective, kugouEnabled) ->
                    if (!kugouEnabled && _currentPlatform.value == MusicPlatform.KUGOU) {
                        if (isPersonalFm) {
                            switchPlatform(MusicPlatform.NETEASE)
                        } else {
                            _currentPlatform.value = MusicPlatform.NETEASE
                        }
                    } else if (!isPersonalFm && !userSwitchedPlatform) {
                        _currentPlatform.value = effective
                    }
                }
            }
        } else {
            scope.launch {
                settingsPreferences.kugouPlatformEnabled.distinctUntilChanged().collect { enabled ->
                    if (!enabled && _currentPlatform.value == MusicPlatform.KUGOU) {
                        if (isPersonalFm) {
                            switchPlatform(MusicPlatform.NETEASE)
                        } else {
                            _currentPlatform.value = MusicPlatform.NETEASE
                        }
                    }
                }
            }
        }
    }

    fun cancel() {
        fmJob?.cancel()
    }

    fun setPlatform(platform: MusicPlatform) {
        userSwitchedPlatform = true
        _currentPlatform.value = if (platform == MusicPlatform.KUGOU && discoverRepository != null) {
            MusicPlatform.KUGOU
        } else {
            MusicPlatform.NETEASE
        }
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
            fetchAndExtend(resolveActiveMode(), resolveActivePlatform())
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
        fetchAndExtend(resolveActiveMode(), resolveActivePlatform())
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
            fetchAndExtend(newMode, resolveActivePlatform())
        }
    }

    // 切换 FM 平台：清空后续待播并按目标平台补拉（总开关关闭时强制网易）
    fun switchPlatform(newPlatform: MusicPlatform) {
        setPlatform(newPlatform)
        if (!isPersonalFm) return
        fmJob?.cancel()
        queue.clearUpcoming()
        stateStore.saveQueue(queue)
        lastTriggeredSongId = -1L
        fmJob = scope.launch {
            fetchAndExtend(resolveActiveMode(), resolveActivePlatform())
        }
    }

    // 统一拉取一批私人 FM 队列项，供入口起播与后续补拉复用
    suspend fun fetchPersonalFmQueueItems(
        mode: PersonalFmMode,
        platform: MusicPlatform
    ): Result<List<QueueItem>> {
        val useKugou = platform == MusicPlatform.KUGOU &&
            discoverRepository != null &&
            settingsPreferences.kugouPlatformEnabled.first()
        return if (useKugou) {
            discoverRepository!!.getPersonalFmSongs(MusicPlatform.KUGOU, mode).first().map { list ->
                list.map(QueueItem::fromUnified).distinctBy { it.effectiveTrackId.canonical }
            }
        } else {
            repository.getPersonalFmSongs(mode).first().map { list ->
                list.filter { it.id > 0L }
                    .distinctBy { it.id }
                    .map { track ->
                        QueueItem(
                            songId = track.id,
                            title = track.name,
                            artist = track.ar.joinToString("/") { it.name },
                            coverUrl = track.al.picUrl
                        )
                    }
            }
        }
    }

    // 不喜欢当前曲目：发请求不阻塞，无论成败立即切下一首并移出队列；余量不足同时触发拉取
    fun trashCurrent(playedSeconds: Long, onPlayIndex: (Int) -> Unit) {
        if (!isPersonalFm) return
        val curIndex = queue.currentIndex.value
        val current = queue.currentItem() ?: return

        scope.launch {
            if (current.effectivePlatform == MusicPlatform.KUGOU) {
                val repo = discoverRepository
                if (repo != null && settingsPreferences.kugouPlatformEnabled.first()) {
                    val track = UnifiedTrack(
                        id = current.effectiveTrackId,
                        name = current.title,
                        kugouMeta = current.kugouMeta
                    )
                    repo.trashFmSong(MusicPlatform.KUGOU, track, playedSeconds).collect { result ->
                        result.onFailure {
                            AppLogger.w(TAG, "酷狗私人FM标记不喜欢失败 trackId=${current.effectiveTrackId.canonical}", it)
                        }
                    }
                }
            } else if (current.songId > 0L) {
                repository.trashFmSong(current.songId, playedSeconds).collect { result ->
                    result.onFailure {
                        AppLogger.w(TAG, "私人FM标记不喜欢失败 songId=${current.songId}", it)
                    }
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
        val trashedTrackId = current.effectiveTrackId
        fmJob = scope.launch {
            val mode = resolveActiveMode()
            val platform = resolveActivePlatform()
            val added = fetchAndExtend(mode, platform, recordRecent = false)
            if (!isPersonalFm) return@launch
            val removeIdx = queue.items.value.indexOfFirst { it.effectiveTrackId == trashedTrackId }
            if (removeIdx >= 0 && queue.size > 1) {
                val replayIndex = queue.removeAt(removeIdx)
                stateStore.saveQueue(queue)
                if (replayIndex >= 0) {
                    onPlayIndex(replayIndex)
                }
            }
            if (added && isPersonalFm && queue.upcomingCount < MIN_UNPLAYED_ITEMS) {
                fetchAndExtend(mode, resolveActivePlatform())
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

    private suspend fun resolveActivePlatform(): MusicPlatform {
        val kugouEnabled = settingsPreferences.kugouPlatformEnabled.first()
        if (!kugouEnabled || discoverRepository == null) {
            if (_currentPlatform.value == MusicPlatform.KUGOU) {
                _currentPlatform.value = MusicPlatform.NETEASE
            }
            return MusicPlatform.NETEASE
        }
        return if (_currentPlatform.value == MusicPlatform.KUGOU) MusicPlatform.KUGOU else MusicPlatform.NETEASE
    }

    private suspend fun fetchAndExtend(
        mode: PersonalFmMode,
        platform: MusicPlatform,
        recordRecent: Boolean = true
    ): Boolean {
        val result = fetchPersonalFmQueueItems(mode, platform)
        var added = false
        result.onSuccess { items ->
            if (items.isEmpty() || !isPersonalFm) return@onSuccess
            val picked = selectPersonalFmQueueItems(
                candidates = items,
                queued = queue.items.value
            )
            if (picked.isEmpty()) {
                AppLogger.w(TAG, "私人FM续接无可用新歌 platform=${platform.key} mode=${mode.key} candidates=${items.size}")
                return@onSuccess
            }
            if (queue.extend(picked)) {
                stateStore.saveQueue(queue)
                if (recordRecent) recordRecentFmFromQueue()
                added = true
            }
        }.onFailure {
            AppLogger.w(TAG, "私人FM拉取下一批失败 platform=${platform.key} mode=${mode.key}", it)
        }
        return added
    }

    private fun recordRecentFmFromQueue() {
        if (!isPersonalFm) return
        val items = queue.items.value
        val curIdx = queue.currentIndex.value
        if (items.isEmpty() || curIdx !in items.indices) return
        val recent = items.drop(curIdx)
            .filter { it.songId > 0L || it.effectivePlatform == MusicPlatform.KUGOU }
            .distinctBy { it.effectiveTrackId.canonical }
            .take(RECENT_FM_TRACK_LIMIT)
            .map { RecentFmTrack(songId = it.songId, coverUrl = it.coverUrl, title = it.title) }
        if (recent.isNotEmpty()) {
            stateStore.saveRecentFmTracks(recent)
        }
    }
}

// 过滤掉已在队列里的歌（含已播过的）以及本批内重复项（兼容既有网易云单测）
internal fun selectPersonalFmSongs(
    candidates: List<Track>,
    queued: List<QueueItem>
): List<Track> {
    val queuedIds = queued.mapTo(HashSet()) { it.songId }
    return candidates
        .filter { it.id > 0L && it.id !in queuedIds }
        .distinctBy { it.id }
}

internal fun selectPersonalFmQueueItems(
    candidates: List<QueueItem>,
    queued: List<QueueItem>
): List<QueueItem> {
    val queuedCanonical = queued.mapTo(HashSet()) { it.effectiveTrackId.canonical }
    return candidates
        .filter {
            (it.effectivePlatform == MusicPlatform.KUGOU || it.songId > 0L) &&
                it.effectiveTrackId.canonical !in queuedCanonical
        }
        .distinctBy { it.effectiveTrackId.canonical }
}
