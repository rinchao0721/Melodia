package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.UnifiedCreator
import kotlinx.serialization.Serializable

// 酷狗每日推荐结果（module/everyday_recommend.js）
@Serializable
data class KugouDailyRecommendResult(
    val date: String = "",
    val songCount: Int = 0,
    val tracks: List<KugouTrackItemDto> = emptyList()
)

// 酷狗首页刷歌推荐单条（module/home_discover.js）
@Serializable
data class KugouHomeDiscoverItemDto(
    val itemId: String = "",
    val itemType: String = "song",
    val algPath: String = "",
    val startTimeMs: Long = 0L,
    val track: KugouTrackItemDto
)

// 酷狗私人 FM 推荐批次（module/personal_fm.js）
@Serializable
data class KugouPersonalFmResult(
    val songPoolId: Long = 0L,
    val curMark: String = "",
    val tracks: List<KugouTrackItemDto> = emptyList()
)

// 酷狗推荐歌单广场条目（module/top_playlist.js）
@Serializable
data class KugouSpecialPlaylistDto(
    val specialId: Long = 0L,
    val globalCollectionId: String = "",
    val name: String = "",
    val coverUrl: String = "",
    val intro: String = "",
    val playCount: Long = 0L,
    val collectCount: Long = 0L,
    val creatorUid: Long = 0L,
    val creatorNickname: String = "",
    val creatorAvatarUrl: String = "",
    val tags: List<String> = emptyList()
) {
    // 优先使用 global_collection_id，缺失时使用 special: 前缀防止与私有云歌单 listid 混淆
    fun preferredPlaylistId(): PlaylistId {
        val cleanGid = globalCollectionId.trim()
        return if (cleanGid.isNotEmpty()) {
            PlaylistId.kugou(cleanGid)
        } else {
            PlaylistId.kugou("special:$specialId")
        }
    }

    fun toCreatorOrNull(): UnifiedCreator? {
        if (creatorUid <= 0L && creatorNickname.isBlank()) return null
        return UnifiedCreator(
            id = if (creatorUid > 0L) AccountId.kugou(creatorUid) else null,
            nickname = creatorNickname,
            avatarUrl = creatorAvatarUrl
        )
    }
}

@Serializable
data class KugouRecommendedPlaylistsPage(
    val playlists: List<KugouSpecialPlaylistDto> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false
)

// 酷狗排行榜单条（module/rank_list.js）
@Serializable
data class KugouRankDto(
    val rankId: Long,
    val rankCid: Long = 0L,
    val name: String,
    val coverUrl: String = "",
    val intro: String = "",
    val updateFrequency: String = "",
    val playTimes: Long = 0L,
    val classify: Int = 0,
    val topSongs: List<String> = emptyList()
)

// 酷狗排行榜曲目分页（module/rank_audio.js）
@Serializable
data class KugouRankTracksPage(
    val rankId: Long,
    val rankCid: Long = 0L,
    val tracks: List<KugouTrackItemDto> = emptyList(),
    val totalCount: Int = tracks.size,
    val page: Int = 1,
    val hasMore: Boolean = false
)
