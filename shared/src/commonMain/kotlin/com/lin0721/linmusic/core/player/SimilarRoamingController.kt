package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "SimilarRoamingController"

// 私人漫游：播放到队列末尾时自动续接相似歌曲，退出漫游时还原原队列
class SimilarRoamingController(
    private val scope: CoroutineScope,
    private val repository: PlaybackRepository,
    private val settingsPreferences: SettingsPreferences,
    private val queue: PlaybackQueue,
    private val stateStore: PlaybackStateStore
) {

    companion object {
        const val CONTEXT_ROAMING = "similar_roaming"

        // 距当前曲目结束的续接预取阈值
        private const val PREFETCH_THRESHOLD_MS = 15000L

        // 队尾至少留这么多首未播曲目，够用就不再拉新的一批，避免每切一首歌都打一次网络
        private const val MIN_UNPLAYED_ITEMS = 5

        // 每次续接把队尾未播曲目补到这么多首
        private const val TARGET_UNPLAYED_ITEMS = 20
    }

    private var roamingJob: Job? = null

    // 记录已触发过续接的歌曲，避免同一首歌反复拉取
    private var lastTriggeredSongId = -1L

    val isRoaming: Boolean get() = queue.playContext.value == CONTEXT_ROAMING

    fun cancel() {
        roamingJob?.cancel()
    }

    // 进入漫游前备份队列，并强制切回列表循环
    fun prepare() {
        queue.takeSnapshot()
        queue.setPlayMode(PlayMode.LIST_LOOP)
        stateStore.savePlayMode(PlayMode.LIST_LOOP)
        lastTriggeredSongId = -1L
    }

    // 起播时若已处于漫游态，队尾不够就后台预取后续歌曲
    fun prefetchOnPlay(songId: Long) {
        if (!isRoaming) return
        if (queue.upcomingCount >= MIN_UNPLAYED_ITEMS) return
        if (roamingJob?.isActive == true) return
        roamingJob = scope.launch {
            appendSimilarSongs(songId)
        }
    }

    // 播放到队列末尾且临近结束时触发续接
    suspend fun onProgressTick(songId: Long, remainingMs: Long) {
        if (queue.upcomingCount > 0) return
        if (remainingMs > PREFETCH_THRESHOLD_MS) return
        if (lastTriggeredSongId == songId) return
        if (!settingsPreferences.autoPlayNext.first()) return

        lastTriggeredSongId = songId
        queue.setPlayContext(CONTEXT_ROAMING)
        appendSimilarSongs(songId)
    }

    // 关闭漫游并还原备份的队列数据
    fun disable() {
        if (!isRoaming) return
        roamingJob?.cancel()
        queue.exitSpecialContext()
        stateStore.saveQueue(queue)
    }

    // 异步拉取相似歌曲并接到队尾。
    // 这里只能追加、不能重写队尾：原先的 appendAfter(index, …) 会把还没播的曲目整段丢掉，
    // 而相似列表之间高度重叠，队尾被反复换成同一批歌就成了「两三首来回循环」。
    private suspend fun appendSimilarSongs(songId: Long) {
        repository.getSimilarSongs(songId).collect { result ->
            result.onSuccess { simiSongs ->
                if (simiSongs.isEmpty() || !isRoaming) return@onSuccess
                val need = (TARGET_UNPLAYED_ITEMS - queue.upcomingCount).coerceAtLeast(1)
                val picked = selectRoamingSongs(
                    candidates = simiSongs,
                    seedSongId = songId,
                    queued = queue.items.value,
                    limit = need
                )
                if (picked.isEmpty()) {
                    AppLogger.w(TAG, "漫游续接无可用新歌 songId=$songId candidates=${simiSongs.size}")
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
                }
            }.onFailure {
                AppLogger.w(TAG, "漫游续接相似歌曲失败 songId=$songId", it)
            }
        }
    }
}

// 从相似歌曲列表里挑出还能接进漫游队列的曲目，排除当前曲目自荐与所有已在队列里的歌（含已播完的）。
internal fun selectRoamingSongs(
    candidates: List<Track>,
    seedSongId: Long,
    queued: List<QueueItem>,
    limit: Int
): List<Track> {
    if (limit <= 0) return emptyList()
    val queuedIds = queued.mapTo(HashSet()) { it.songId }
    return candidates
        .filter { it.id != seedSongId && it.id !in queuedIds }
        .distinctBy { it.id }
        .take(limit)
}
