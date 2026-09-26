package com.lin0721.linmusic.feature.cloud.data

import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.feature.cloud.domain.CloudQuota
import com.lin0721.linmusic.feature.cloud.domain.toDomain
import kotlinx.coroutines.flow.Flow

class CloudRepositoryImpl(
    private val apiService: CloudApi
) : CloudRepository {

    override fun getCloudSongs(limit: Int, offset: Int): Flow<Result<CloudPage>> = apiFlow(
        request = { apiService.getCloudSongs(CloudListRequest(limit = limit, offset = offset)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            val now = System.currentTimeMillis()
            CloudPage(
                songs = response.data.map { it.toDomain(now) },
                quota = CloudQuota(
                    usedBytes = response.size,
                    maxBytes = response.maxSize,
                    totalCount = response.count
                ),
                hasMore = response.hasMore
            )
        }
    )

    override fun deleteCloudSong(songId: Long): Flow<Result<Unit>> = apiFlow(
        request = { apiService.deleteCloudSong(CloudDeleteRequest(songIds = listOf(songId))) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = {}
    )

    override fun matchCloudSong(userId: Long, songId: Long, adjustSongId: Long): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.matchCloudSong(CloudMatchRequest(userId = userId, songId = songId, adjustSongId = adjustSongId))
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = {}
    )
}
