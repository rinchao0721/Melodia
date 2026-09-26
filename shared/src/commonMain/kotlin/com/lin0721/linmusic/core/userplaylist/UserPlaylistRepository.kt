package com.lin0721.linmusic.core.userplaylist

import kotlinx.coroutines.flow.Flow

// 当前用户歌单列表仓储（core 共享能力，被 library/artist/playlist 的"收藏到歌单"等入口复用）
interface UserPlaylistRepository {

    fun getUserPlaylists(uid: Long, limit: Int = 1000): Flow<Result<List<UserPlaylist>>>
}
