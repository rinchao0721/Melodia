package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.feature.create.data.CreateRepository
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// 新建歌单并立即把指定歌曲（支持多首）加入其中，供 artist/library/playlist 等域共同复用
class CreatePlaylistAndAddSongUseCase(
    private val createRepository: CreateRepository,
    private val playlistRepository: PlaylistRepository
) {
    operator fun invoke(name: String, songIds: List<Long>, privacy: Int = 0): Flow<Result<PlaylistDetail>> = flow {
        createRepository.createPlaylist(name, privacy).collect { createResult ->
            createResult.fold(
                onSuccess = { playlist ->
                    if (songIds.isEmpty()) {
                        emit(Result.success(playlist))
                    } else {
                        playlistRepository.manipulatePlaylistTracks("add", playlist.id, songIds).collect { addResult ->
                            addResult.fold(
                                onSuccess = { emit(Result.success(playlist)) },
                                // 透传 Repository 层的 AppError，不重新包装以免丢失错误类型
                                onFailure = { e -> emit(Result.failure(e)) }
                            )
                        }
                    }
                },
                onFailure = { e -> emit(Result.failure(e)) }
            )
        }
    }
}
