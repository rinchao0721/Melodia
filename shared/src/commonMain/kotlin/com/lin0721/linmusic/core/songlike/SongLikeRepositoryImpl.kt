package com.lin0721.linmusic.core.songlike

import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.core.playlistexternal.ExternalSongLikes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 负数 id 是外部平台歌曲，红心只存本机，与网易云红心合并在同一个集合里对外提供
class SongLikeRepositoryImpl(
    private val apiService: SongLikeApi,
    private val externalLikes: ExternalSongLikes? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : SongLikeRepository {

    private val _likedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    override val likedSongIds: StateFlow<Set<Long>> = _likedSongIds.asStateFlow()

    init {
        if (externalLikes != null) {
            scope.launch {
                externalLikes.likedIds.collect { external ->
                    _likedSongIds.update { current -> current.filterTo(HashSet()) { it > 0L } + external }
                }
            }
        }
    }

    override fun getLikedSongIds(uid: Long): Flow<Result<List<Long>>> = apiFlow(
        request = { apiService.getLikedSongIds(LikeSongListRequest(uid = uid)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.ids }
    ).onEach { result ->
        result.onSuccess { ids ->
            _likedSongIds.update { current -> ids.toSet() + current.filter { it < 0L } }
        }
    }

    override fun likeSong(songId: Long, like: Boolean): Flow<Result<Unit>> = flow {
        // 乐观更新全局红心状态
        val previous = _likedSongIds.value
        _likedSongIds.value = if (like) previous + songId else previous - songId

        if (songId < 0L) {
            val result = externalLikes?.setLiked(songId, like)
                ?: Result.failure(IllegalStateException("该歌曲不支持收藏"))
            if (result.isFailure) _likedSongIds.value = previous
            emit(result)
            return@flow
        }

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
