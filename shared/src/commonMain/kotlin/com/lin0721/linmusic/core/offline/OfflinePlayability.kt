package com.lin0721.linmusic.core.offline

import com.lin0721.linmusic.core.network.OnlineStateProvider
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// 本机已持有完整音频（下载或播放缓存）的歌曲索引，由各平台实现
fun interface CachedAudioIndex {
    suspend fun playableIds(ids: Collection<Long>): Set<Long>
    val changes: Flow<Unit> get() = flowOf(Unit)
}

// 离线时判定哪些歌曲不可播放；在线时所有歌曲都可播放
class OfflinePlayability(
    private val onlineState: OnlineStateProvider,
    private val index: CachedAudioIndex
) {
    val online: StateFlow<Boolean> = onlineState.online
    val changes: Flow<Unit> = index.changes

    suspend fun unplayableIds(ids: Collection<Long>): Set<Long> {
        if (ids.isEmpty() || onlineState.isOnline()) return emptySet()
        val playable = index.playableIds(ids)
        return ids.filterTo(HashSet()) { it !in playable }
    }
}
