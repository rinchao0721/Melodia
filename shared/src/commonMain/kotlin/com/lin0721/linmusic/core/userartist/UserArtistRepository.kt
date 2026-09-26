package com.lin0721.linmusic.core.userartist

import com.lin0721.linmusic.core.model.ArtistInfo
import kotlinx.coroutines.flow.Flow

// 关注歌手列表仓储（core 共享能力，被 home 的"你最爱的艺人"与 library 的歌手聚合复用）
interface UserArtistRepository {

    // 获取最爱的歌手（优先已关注，兜底热门）
    fun getFavoriteArtists(): Flow<Result<List<ArtistInfo>>>
}
