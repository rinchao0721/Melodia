package com.lin0721.linmusic.feature.profile.domain

// 用户详情（展平结构）
data class ProfileUserInfo(
    val uid: Long,
    val nickname: String,
    val avatarUrl: String,
    val signature: String,
    val level: Int,
    val followsCount: Int,
    val followedsCount: Int,
    val playlistCount: Int,
    val isFollowedByMe: Boolean
)

// 歌单基本信息
data class ProfilePlaylistInfo(
    val id: Long,
    val name: String,
    val coverImgUrl: String,
    val playCount: Long,
    val trackCount: Int,
    val creatorName: String
)

// 歌单分页
data class ProfilePlaylistPage(
    val playlists: List<ProfilePlaylistInfo>,
    val hasMore: Boolean
)

// 关注/粉丝列表项
data class ProfileFollowUserItem(
    val uid: Long,
    val nickname: String,
    val avatarUrl: String,
    val signature: String,
    val isFollowedByMe: Boolean
)

// 关注/粉丝列表分页
data class ProfileFollowListPage(
    val users: List<ProfileFollowUserItem>,
    val hasMore: Boolean
)

// 动态基本信息
data class ProfileEventInfo(
    val id: Long,
    val showTime: Long,
    val authorUid: Long,
    val authorNickname: String,
    val authorAvatarUrl: String,
    val rawJson: String
)

// 动态分页
data class ProfileEventPage(
    val events: List<ProfileEventInfo>,
    val hasMore: Boolean,
    val lasttime: Long // 翻页游标
)

// 听歌排行项
data class ProfileListenRankItem(
    val playCount: Int,
    val songId: Long,
    val songName: String,
    val artistName: String,
    val albumCoverUrl: String
)
