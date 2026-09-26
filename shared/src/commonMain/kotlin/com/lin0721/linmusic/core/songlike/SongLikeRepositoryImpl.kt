package com.lin0721.linmusic.core.songlike

import com.lin0721.linmusic.core.network.apiFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

class SongLikeRepositoryImpl(
    private val apiService: SongLikeApi
) : SongLikeRepository {

    private val _likedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    override val likedSongIds: StateFlow<Set<Long>> = _likedSongIds.asStateFlow()

    override fun getLikedSongIds(uid: Long): Flow<Result<List<Long>>> = apiFlow(
        request = { apiService.getLikedSongIds(LikeSongListRequest(uid = uid)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.ids }
    ).onEach { result ->
        result.onSuccess { ids ->
            _likedSongIds.value = ids.toSet()
        }
    }

    override fun likeSong(songId: Long, like: Boolean): Flow<Result<Unit>> = flow {
        // 乐观更新全局红心状态
        val previous = _likedSongIds.value
        _likedSongIds.value = if (like) previous + songId else previous - songId

        apiFlow(
            request = { apiService.likeSong(LikeSongRequest(trackId = songId, like = like)) },
            isSuccess = { it.isSuccess },
            code = { it.code },
            transform = { Unit }
        ).collect { result ->
            result.onFailure {
                // 请求失败时回滚
                _likedSongIds.value = previous
            }
            emit(result)
        }
    }

    override fun syncLikedSongIds(ids: Set<Long>) {
        _likedSongIds.value = ids
    }
}
