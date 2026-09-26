package com.lin0721.linmusic.feature.create.data

import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.core.model.PlaylistDetail
import kotlinx.coroutines.flow.Flow

class CreateRepositoryImpl(
    private val apiService: CreateApi
) : CreateRepository {

    override fun createPlaylist(name: String, privacy: Int): Flow<Result<PlaylistDetail>> = apiFlow(
        request = { apiService.createPlaylist(PlaylistCreateRequest(name = name, privacy = privacy)) },
        isSuccess = { it.isSuccess && it.playlist != null },
        code = { it.code },
        transform = { it.playlist!! }
    )
}
