package com.lin0721.linmusic.feature.create.data

import com.lin0721.linmusic.core.model.PlaylistDetail
import kotlinx.coroutines.flow.Flow

// 新建歌单数据仓储（create 业务域）
// 目前 create/artist/library/playlist 四个域各自独立调用本接口，
// 调用后再各自内联一次 manipulatePlaylistTracks 把当前歌曲加入新歌单，
// 这段重复流程本次迁移不做收敛，行为保持与迁移前一致
interface CreateRepository {

    // 创建歌单
    fun createPlaylist(name: String, privacy: Int = 0): Flow<Result<PlaylistDetail>>
}
