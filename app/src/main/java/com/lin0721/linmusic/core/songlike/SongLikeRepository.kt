package com.lin0721.linmusic.core.songlike

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// 歌曲红心数据仓储（core 共享能力，被播放引擎与 player/playlist/artist 等多个域复用）
interface SongLikeRepository {

    val likedSongIds: StateFlow<Set<Long>>

    fun getLikedSongIds(uid: Long): Flow<Result<List<Long>>>

    fun likeSong(songId: Long, like: Boolean): Flow<Result<Unit>>

    fun syncLikedSongIds(ids: Set<Long>)
}
