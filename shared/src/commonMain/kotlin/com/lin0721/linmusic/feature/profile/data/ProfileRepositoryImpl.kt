package com.lin0721.linmusic.feature.profile.data

import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.feature.profile.domain.ProfileEventInfo
import com.lin0721.linmusic.feature.profile.domain.ProfileEventPage
import com.lin0721.linmusic.feature.profile.domain.ProfileFollowListPage
import com.lin0721.linmusic.feature.profile.domain.ProfileFollowUserItem
import com.lin0721.linmusic.feature.profile.domain.ProfileListenRankItem
import com.lin0721.linmusic.feature.profile.domain.ProfilePlaylistInfo
import com.lin0721.linmusic.feature.profile.domain.ProfilePlaylistPage
import com.lin0721.linmusic.feature.profile.domain.ProfileUserInfo
import kotlinx.coroutines.flow.Flow

class ProfileRepositoryImpl(
    private val profileApi: ProfileApi
) : ProfileRepository {

    override fun getUserDetail(uid: Long): Flow<Result<ProfileUserInfo>> = apiFlow(
        request = { profileApi.getUserDetailEapi(uid) },
        isSuccess = { it.isSuccess },
        code = { 200 }, // getUserDetail 返回体没有 code 字段，成功时模拟 200
        transform = {
            ProfileUserInfo(
                uid = it.profile.userId,
                nickname = it.profile.nickname,
                avatarUrl = it.profile.avatarUrl,
                signature = it.profile.signature,
                level = it.level,
                followsCount = it.profile.follows,
                followedsCount = it.profile.followeds,
                playlistCount = it.profile.playlistCount,
                isFollowedByMe = it.profile.followed
            )
        }
    )

    override fun getUserDetailFallback(uid: Long): Flow<Result<ProfileUserInfo>> = apiFlow(
        request = { profileApi.getUserDetailWeapi(uid) },
        isSuccess = { it.isSuccess },
        code = { 200 },
        transform = {
            ProfileUserInfo(
                uid = it.profile.userId,
                nickname = it.profile.nickname,
                avatarUrl = it.profile.avatarUrl,
                signature = it.profile.signature,
                level = it.level,
                followsCount = it.profile.follows,
                followedsCount = it.profile.followeds,
                playlistCount = it.profile.playlistCount,
                isFollowedByMe = it.profile.followed
            )
        }
    )

    override fun followUser(uid: Long): Flow<Result<Unit>> = apiFlow(
        request = { profileApi.followUser(uid) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { Unit }
    )

    override fun unfollowUser(uid: Long): Flow<Result<Unit>> = apiFlow(
        request = { profileApi.unfollowUser(uid) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { Unit }
    )

    override fun getUserPlaylists(uid: Long, offset: Int, limit: Int): Flow<Result<ProfilePlaylistPage>> = apiFlow(
        request = {
            profileApi.getUserPlaylists(
                ProfileUserPlaylistsRequest(
                    uid = uid,
                    limit = limit,
                    offset = offset,
                    includeVideo = true
                )
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            ProfilePlaylistPage(
                playlists = response.playlist.map { playlist ->
                    ProfilePlaylistInfo(
                        id = playlist.id,
                        name = playlist.name,
                        coverImgUrl = playlist.coverImgUrl,
                        playCount = playlist.playCount,
                        trackCount = playlist.trackCount,
                        creatorName = playlist.creator?.nickname ?: ""
                    )
                },
                hasMore = response.more
            )
        }
    )

    override fun getUserFollows(uid: Long, offset: Int, limit: Int): Flow<Result<ProfileFollowListPage>> = apiFlow(
        request = {
            profileApi.getUserFollows(
                uid,
                ProfileUserFollowsRequest(
                    offset = offset,
                    limit = limit,
                    order = true
                )
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            ProfileFollowListPage(
                users = response.follow.map { user ->
                    ProfileFollowUserItem(
                        uid = user.userId,
                        nickname = user.nickname,
                        avatarUrl = user.avatarUrl,
                        signature = user.signature,
                        isFollowedByMe = user.followed
                    )
                },
                hasMore = response.more
            )
        }
    )

    override fun getUserFolloweds(uid: Long, offset: Int, limit: Int): Flow<Result<ProfileFollowListPage>> = apiFlow(
        request = {
            profileApi.getUserFolloweds(
                uid,
                ProfileUserFollowedsRequest(
                    userId = uid,
                    time = "0",
                    limit = limit,
                    offset = offset,
                    getcounts = "true"
                )
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            ProfileFollowListPage(
                users = response.followeds.map { user ->
                    ProfileFollowUserItem(
                        uid = user.userId,
                        nickname = user.nickname,
                        avatarUrl = user.avatarUrl,
                        signature = user.signature,
                        isFollowedByMe = user.followed
                    )
                },
                hasMore = response.more
            )
        }
    )

    override fun getUserEvents(uid: Long, time: Long, limit: Int): Flow<Result<ProfileEventPage>> = apiFlow(
        request = {
            profileApi.getUserEvents(
                uid,
                ProfileUserEventsRequest(
                    getcounts = true,
                    time = if (time <= 0L) -1L else time,
                    limit = limit,
                    total = false,
                    fromRN = "true"
                )
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            ProfileEventPage(
                events = response.events.map { event ->
                    ProfileEventInfo(
                        id = event.id,
                        showTime = event.showTime,
                        authorUid = event.user?.userId ?: 0L,
                        authorNickname = event.user?.nickname ?: "",
                        authorAvatarUrl = event.user?.avatarUrl ?: "",
                        rawJson = event.json
                    )
                },
                hasMore = response.more,
                lasttime = response.lasttime
            )
        }
    )

    override fun getUserListeningRank(uid: Long, type: Int): Flow<Result<List<ProfileListenRankItem>>> = apiFlow(
        request = {
            profileApi.getUserListeningRank(
                ProfileListeningRankRequest(
                    uid = uid,
                    type = type
                )
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { response ->
            val targetList = if (type == 1) response.weekData else response.allData
            targetList.map { record ->
                ProfileListenRankItem(
                    playCount = record.playCount,
                    songId = record.song?.id ?: 0L,
                    songName = record.song?.name ?: "",
                    artistName = record.song?.ar?.firstOrNull()?.name ?: "",
                    albumCoverUrl = record.song?.al?.picUrl ?: ""
                )
            }
        }
    )
}
