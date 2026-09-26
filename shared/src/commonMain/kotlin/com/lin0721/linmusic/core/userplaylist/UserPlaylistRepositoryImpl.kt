package com.lin0721.linmusic.core.userplaylist

import com.lin0721.linmusic.core.network.apiFlow
import kotlinx.coroutines.flow.Flow

class UserPlaylistRepositoryImpl(
    private val apiService: UserPlaylistApi
) : UserPlaylistRepository {

    override fun getUserPlaylists(uid: Long, limit: Int): Flow<Result<List<UserPlaylist>>> = apiFlow(
        request = { apiService.getUserPlaylists(UserPlaylistRequest(uid = uid, limit = limit)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.playlist }
    )
}
