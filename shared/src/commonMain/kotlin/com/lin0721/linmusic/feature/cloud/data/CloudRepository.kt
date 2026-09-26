package com.lin0721.linmusic.feature.cloud.data

import com.lin0721.linmusic.feature.cloud.domain.CloudQuota
import com.lin0721.linmusic.feature.cloud.domain.CloudSong
import kotlinx.coroutines.flow.Flow

// 我的云盘数据仓储
interface CloudRepository {

    // 分页拉取云盘歌曲，quota 随每页响应一起下发（服务端行为，非独立接口）
    fun getCloudSongs(limit: Int, offset: Int): Flow<Result<CloudPage>>

    fun deleteCloudSong(songId: Long): Flow<Result<Unit>>

    fun matchCloudSong(userId: Long, songId: Long, adjustSongId: Long): Flow<Result<Unit>>
}

data class CloudPage(
    val songs: List<CloudSong>,
    val quota: CloudQuota,
    val hasMore: Boolean
)
