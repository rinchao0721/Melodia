package com.lin0721.linmusic.feature.profile.data

import com.lin0721.linmusic.feature.profile.domain.ProfileEventPage
import com.lin0721.linmusic.feature.profile.domain.ProfileFollowListPage
import com.lin0721.linmusic.feature.profile.domain.ProfileListenRankItem
import com.lin0721.linmusic.feature.profile.domain.ProfilePlaylistPage
import com.lin0721.linmusic.feature.profile.domain.ProfileUserInfo
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun getUserDetail(uid: Long): Flow<Result<ProfileUserInfo>>
    fun getUserDetailFallback(uid: Long): Flow<Result<ProfileUserInfo>>
    fun followUser(uid: Long): Flow<Result<Unit>>
    fun unfollowUser(uid: Long): Flow<Result<Unit>>
    fun getUserPlaylists(uid: Long, offset: Int, limit: Int): Flow<Result<ProfilePlaylistPage>>
    fun getUserFollows(uid: Long, offset: Int, limit: Int): Flow<Result<ProfileFollowListPage>>
    fun getUserFolloweds(uid: Long, offset: Int, limit: Int): Flow<Result<ProfileFollowListPage>>
    fun getUserEvents(uid: Long, time: Long, limit: Int): Flow<Result<ProfileEventPage>>
    fun getUserListeningRank(uid: Long, type: Int): Flow<Result<List<ProfileListenRankItem>>>
}
