package com.lin0721.linmusic.feature.recent.data

import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.feature.recent.domain.RecentAlbum
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist
import com.lin0721.linmusic.feature.recent.domain.RecentSong
import com.lin0721.linmusic.feature.recent.domain.toDomain
import kotlinx.coroutines.flow.Flow

// 服务端忽略 limit 一次下发全量（歌曲曾实测 300 条 / 460KB），列表侧统一截断
private const val MAX_RECORDS = 100

class RecentRepositoryImpl(
    private val apiService: RecentApi
) : RecentRepository {

    override fun getRecentSongs(): Flow<Result<List<RecentSong>>> = apiFlow(
        request = { apiService.getRecentSongs() },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { response -> response.data!!.list.take(MAX_RECORDS).map { it.toDomain() } }
    )

    override fun getRecentPlaylists(): Flow<Result<List<RecentPlaylist>>> = apiFlow(
        request = { apiService.getRecentPlaylists() },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { response -> response.data!!.list.take(MAX_RECORDS).map { it.toDomain() } }
    )

    override fun getRecentAlbums(): Flow<Result<List<RecentAlbum>>> = apiFlow(
        request = { apiService.getRecentAlbums() },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { response -> response.data!!.list.take(MAX_RECORDS).map { it.toDomain() } }
    )
}
