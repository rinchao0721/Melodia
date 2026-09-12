package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.log.AppLogger
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
    }

    // 起播时若已处于漫游态，后台预取后续歌曲
    fun prefetchOnPlay(songId: Long, index: Int) {
        if (!isRoaming) return
        roamingJob = scope.launch {
            appendSimilarSongs(songId, index)
        }
    }

    // 播放到队列末尾且临近结束时触发续接
    suspend fun onProgressTick(songId: Long, remainingMs: Long) {
        val curIdx = queue.currentIndex.value
        if (curIdx != queue.size - 1) return
        if (remainingMs > PREFETCH_THRESHOLD_MS) return
        if (lastTriggeredSongId == songId) return
        if (!settingsPreferences.autoPlayNext.first()) return

        lastTriggeredSongId = songId
        queue.setPlayContext(CONTEXT_ROAMING)
        appendSimilarSongs(songId, curIdx)
    }

    // 关闭漫游并还原备份的队列数据
    fun disable() {
        if (!isRoaming) return
        roamingJob?.cancel()
        queue.restoreSnapshot()
        stateStore.saveQueue(queue)
    }

    // 异步拉取相似歌曲并替换后继播放队列
    private suspend fun appendSimilarSongs(songId: Long, index: Int) {
        repository.getSimilarSongs(songId).collect { result ->
            result.onSuccess { simiSongs ->
                if (simiSongs.isNotEmpty() && isRoaming) {
                    val simiItems = simiSongs.map { track ->
                        QueueItem(
                            songId = track.id,
                            title = track.name,
                            artist = track.ar.joinToString("/") { it.name },
                            coverUrl = track.al.picUrl
                        )
                    }
                    queue.appendAfter(index, simiItems)
                    stateStore.saveQueue(queue)
                }
            }.onFailure {
                AppLogger.w(TAG, "漫游续接相似歌曲失败 songId=$songId", it)
            }
        }
    }
}
