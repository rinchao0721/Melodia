package com.lin0721.linmusic.feature.library.data

import com.lin0721.linmusic.core.model.Track
import kotlinx.coroutines.flow.Flow

// 听歌排行单曲：附带播放次数，供排行榜页展示
data class UserRecordTrack(
    val track: Track,
    val playCount: Int
)

// 音乐库数据仓储（library 业务域）
interface LibraryRepository {

    // 获取听歌排行
    fun getUserRecord(uid: Long, type: Int): Flow<Result<List<UserRecordTrack>>>

    // 获取收藏专辑
    fun getCollectedAlbums(limit: Int = 1000): Flow<Result<List<AlbumSubItem>>>

    // 获取各分类收藏数
    fun getUserSubcount(): Flow<Result<UserSubcountResponse>>
}
