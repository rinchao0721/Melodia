package com.lin0721.linmusic.core.songquality

import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// 合并连续变更（整表替换后紧跟插队、扫描分批入库等）
private const val WARM_DEBOUNCE_MS = 500L

// 本地文件、播客与外部平台歌曲没有网易最高音质
fun QueueItem.qualitySongId(): Long? =
    songId.takeIf { it > 0 && localUri == null && radioId == 0L && external == null }

// 不经 ViewModel 预查的来源（播放队列、本地曲库）变化后后台预查，打开列表时徽标直接命中缓存
@OptIn(FlowPreview::class)
fun SongQualityCache.warmIds(ids: Flow<Set<Long>>, scope: CoroutineScope): Job = scope.launch {
    ids
        .map { set -> set.filterTo(HashSet()) { it > 0 } }
        .distinctUntilChanged()
        .debounce(WARM_DEBOUNCE_MS)
        .collectLatest { set -> if (set.isNotEmpty()) prefetchIds(set) }
}

fun SongQualityCache.warmQueue(queue: Flow<List<QueueItem>>, scope: CoroutineScope): Job =
    warmIds(queue.map { items -> items.mapNotNullTo(HashSet()) { it.qualitySongId() } }, scope)
