package com.lin0721.linmusic.core.playlistexternal

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.source.ExternalTrack
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

// 「我喜欢的音乐」的真实歌单 id 要联网才知道，外部歌曲红心改用账号派生的固定 key 存放，负数避开真实歌单 id
fun likedExternalKey(uid: Long): Long = -uid

// 外部歌曲的本机红心：等价于把歌曲放进「我喜欢的音乐」的外部条目
class ExternalSongLikes(
    private val store: PlaylistExternalTrackStore,
    private val userPreferences: UserPreferences,
    // 正在播放的搜索结果不在任何歌单里，红心时要从播放队列找回歌曲信息
    private val queueSnapshot: () -> List<QueueItem>
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val likedIds: Flow<Set<Long>> = userPreferences.userProfile.flatMapLatest { profile ->
        if (profile == null) {
            flowOf(emptySet())
        } else {
            store.entriesOf(likedExternalKey(profile.uid)).map { entries ->
                entries.mapTo(HashSet()) { it.track.queueSongId }
            }
        }
    }

    suspend fun setLiked(songId: Long, like: Boolean): Result<Unit> {
        val uid = userPreferences.userProfile.first()?.uid
            ?: return Result.failure(IllegalStateException("请先登录账号"))
        val key = likedExternalKey(uid)
        if (!like) {
            store.remove(key, setOf(songId))
            return Result.success(Unit)
        }
        val track = find(songId) ?: return Result.failure(IllegalStateException("找不到该歌曲信息，无法收藏"))
        store.add(key, listOf(track))
        return Result.success(Unit)
    }

    suspend fun find(songId: Long): ExternalTrack? {
        queueSnapshot().firstOrNull { it.songId == songId }?.external?.let { return it }
        return store.all.first().values.asSequence().flatten()
            .firstOrNull { it.track.queueSongId == songId }?.track
    }
}
