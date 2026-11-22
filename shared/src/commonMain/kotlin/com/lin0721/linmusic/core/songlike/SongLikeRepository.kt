package com.lin0721.linmusic.core.songlike

import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.TrackId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

// 歌曲红心数据仓储（core 共享能力，被播放引擎与 player/playlist/artist 等多个域复用）
interface SongLikeRepository {

    val likedSongIds: StateFlow<Set<Long>>

    // 跨平台统一红心集合（合并网易红心 + 酷狗云端红心 + 本机外部红心）
    val likedTrackIds: StateFlow<Set<TrackId>>

    fun getLikedSongIds(uid: Long): Flow<Result<List<Long>>>

    fun likeSong(songId: Long, like: Boolean): Flow<Result<Unit>>

    fun syncLikedSongIds(ids: Set<Long>)

    fun syncKugouLikedTracks(): Flow<Result<Set<TrackId>>> = flowOf(Result.success(emptySet()))

    fun likeTrack(
        trackId: TrackId,
        like: Boolean,
        title: String = "",
        kugouMeta: KugouTrackMeta? = null,
        legacyExternalId: Long? = null
    ): Flow<Result<Unit>> = flowOf(Result.failure(UnsupportedOperationException("不支持跨平台收藏")))
}
