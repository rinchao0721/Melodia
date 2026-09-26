package com.lin0721.linmusic.core.songlike

import com.lin0721.linmusic.core.auth.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

// 拉取当前用户已红心的歌曲 ID，供 artist/player/playlist 等域共用
class LoadLikedSongIdsUseCase(
    private val userPreferences: UserPreferences,
    private val songLikeRepository: SongLikeRepository
) {

    // 未登录或请求失败时返回 null，调用方保持原有状态不变
    suspend operator fun invoke(): Set<Long>? {
        val profile = userPreferences.userProfile.first() ?: return null
        return songLikeRepository.getLikedSongIds(profile.uid)
            .firstOrNull()
            ?.getOrNull()
            ?.toSet()
    }
}
