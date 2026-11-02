package com.lin0721.linmusic.feature.newworks.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// 新作连续播放：从被点击的发布起，按列表顺序（由新到旧）把后续发布的曲目展开成一条队列
object NewWorksQueue {

    // 队列里排在当前曲目之后的曲目不足该数量时，提前加载并追加下一批
    const val PREFETCH_REMAINING = 3

    // 同时补拉专辑详情的发布数上限
    private const val RESOLVE_CONCURRENCY = 5

    // tracksOf 取出某个发布的曲目（专辑没有内联曲目时由它补拉）；alreadyQueued 里的歌曲和批内重复的歌曲只保留首次出现
    suspend fun build(
        releases: List<NewWorksRelease>,
        alreadyQueued: Set<Long>,
        tracksOf: suspend (NewWorksRelease) -> List<NewWorksTrack>
    ): List<NewWorksTrack> {
        val seen = HashSet(alreadyQueued)
        val tracks = releases.chunked(RESOLVE_CONCURRENCY).flatMap { chunk ->
            coroutineScope { chunk.map { async { tracksOf(it) } }.awaitAll() }.flatten()
        }
        return tracks.filter { seen.add(it.id) }
    }

    fun shouldExtend(queueSize: Int, currentIndex: Int): Boolean =
        currentIndex >= 0 && queueSize - currentIndex - 1 <= PREFETCH_REMAINING
}
