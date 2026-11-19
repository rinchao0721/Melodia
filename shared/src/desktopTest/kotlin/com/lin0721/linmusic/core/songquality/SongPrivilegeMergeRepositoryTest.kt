package com.lin0721.linmusic.core.songquality

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.cache.testOfflineFallback
import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.model.EmptyBody
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.PlaylistTrackId
import com.lin0721.linmusic.core.model.SongFileInfo
import com.lin0721.linmusic.core.model.SongPrivilege
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.data.SongDetailRequest
import com.lin0721.linmusic.core.player.data.SongDetailResponse
import com.lin0721.linmusic.feature.home.data.DailyRecommendSongsResponse
import com.lin0721.linmusic.feature.player.data.PlayerApi
import com.lin0721.linmusic.feature.player.data.PlayerRepositoryImpl
import com.lin0721.linmusic.feature.player.data.SongChorusRequest
import com.lin0721.linmusic.feature.player.data.SongChorusResponse
import com.lin0721.linmusic.feature.player.data.SongCreatorsRequest
import com.lin0721.linmusic.feature.player.data.SongCreatorsResponse
import com.lin0721.linmusic.feature.player.data.SongWikiSummaryRequest
import com.lin0721.linmusic.feature.player.data.SongWikiSummaryResponse
import com.lin0721.linmusic.feature.playlist.data.AlbumDetailResponse
import com.lin0721.linmusic.feature.playlist.data.AlbumSubscribeRequest
import com.lin0721.linmusic.feature.playlist.data.AlbumSubscribeResponse
import com.lin0721.linmusic.feature.playlist.data.PlaylistApi
import com.lin0721.linmusic.feature.playlist.data.PlaylistDeleteRequest
import com.lin0721.linmusic.feature.playlist.data.PlaylistDeleteResponse
import com.lin0721.linmusic.feature.playlist.data.PlaylistDetailRequest
import com.lin0721.linmusic.feature.playlist.data.PlaylistDetailResponse
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepositoryImpl
import com.lin0721.linmusic.feature.playlist.data.PlaylistSubscribeRequest
import com.lin0721.linmusic.feature.playlist.data.PlaylistSubscribeResponse
import com.lin0721.linmusic.feature.playlist.data.PlaylistTracksManipulateRequest
import com.lin0721.linmusic.feature.playlist.data.PlaylistTracksManipulateResponse
import com.lin0721.linmusic.feature.playlist.data.PlaylistUpdateCoverRequest
import com.lin0721.linmusic.feature.playlist.data.PlaylistUpdateCoverResponse
import com.lin0721.linmusic.feature.playlist.data.PlaylistUpdateDescRequest
import com.lin0721.linmusic.feature.playlist.data.PlaylistUpdateDescResponse
import com.lin0721.linmusic.feature.playlist.data.PlaylistUpdateNameRequest
import com.lin0721.linmusic.feature.playlist.data.PlaylistUpdateNameResponse
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.profile.data.ProfileAlbum
import com.lin0721.linmusic.feature.profile.data.ProfileApi
import com.lin0721.linmusic.feature.profile.data.ProfileArtist
import com.lin0721.linmusic.feature.profile.data.ProfileEventResponse
import com.lin0721.linmusic.feature.profile.data.ProfileFollowListResponse
import com.lin0721.linmusic.feature.profile.data.ProfileListenRecord
import com.lin0721.linmusic.feature.profile.data.ProfileListeningRankRequest
import com.lin0721.linmusic.feature.profile.data.ProfileListeningRankResponse
import com.lin0721.linmusic.feature.profile.data.ProfileRepositoryImpl
import com.lin0721.linmusic.feature.profile.data.ProfileSong
import com.lin0721.linmusic.feature.profile.data.ProfileUserDetailResponse
import com.lin0721.linmusic.feature.profile.data.ProfileUserEventsRequest
import com.lin0721.linmusic.feature.profile.data.ProfileUserFollowedsRequest
import com.lin0721.linmusic.feature.profile.data.ProfileUserFollowsRequest
import com.lin0721.linmusic.feature.profile.data.ProfileUserPlaylistsRequest
import com.lin0721.linmusic.feature.profile.data.ProfileUserPlaylistsResponse
import com.lin0721.linmusic.feature.profile.data.SimpleActionResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class StubPlayerApi(
    private val detailResponse: SongDetailResponse
) : PlayerApi {
    override suspend fun getSongDetail(body: SongDetailRequest): SongDetailResponse = detailResponse
    override suspend fun getSongWikiSummary(body: SongWikiSummaryRequest): SongWikiSummaryResponse = error("unused")
    override suspend fun getSongCreators(body: SongCreatorsRequest): SongCreatorsResponse = error("unused")
    override suspend fun getSongChorus(body: SongChorusRequest): SongChorusResponse = error("unused")
}

private class StubPlaylistApi(
    private val detailResponse: PlaylistDetailResponse
) : PlaylistApi {
    override suspend fun getPlaylistDetail(body: PlaylistDetailRequest): PlaylistDetailResponse = detailResponse
    override suspend fun getAlbumDetail(id: Long, body: EmptyBody): AlbumDetailResponse = error("unused")
    override suspend fun manipulatePlaylistTracks(body: PlaylistTracksManipulateRequest): PlaylistTracksManipulateResponse = error("unused")
    override suspend fun subscribePlaylist(op: String, body: PlaylistSubscribeRequest): PlaylistSubscribeResponse = error("unused")
    override suspend fun subscribeAlbum(op: String, body: AlbumSubscribeRequest): AlbumSubscribeResponse = error("unused")
    override suspend fun updatePlaylistName(body: PlaylistUpdateNameRequest): PlaylistUpdateNameResponse = error("unused")
    override suspend fun updatePlaylistDesc(body: PlaylistUpdateDescRequest): PlaylistUpdateDescResponse = error("unused")
    override suspend fun deletePlaylist(body: PlaylistDeleteRequest): PlaylistDeleteResponse = error("unused")
    override suspend fun updatePlaylistCover(body: PlaylistUpdateCoverRequest): PlaylistUpdateCoverResponse = error("unused")
}

private class StubProfileApi(
    private val rankResponse: ProfileListeningRankResponse
) : ProfileApi {
    override suspend fun getUserDetailEapi(uid: Long, body: EmptyBody): ProfileUserDetailResponse = error("unused")
    override suspend fun getUserDetailWeapi(uid: Long, body: EmptyBody): ProfileUserDetailResponse = error("unused")
    override suspend fun followUser(uid: Long, body: EmptyBody): SimpleActionResponse = error("unused")
    override suspend fun unfollowUser(uid: Long, body: EmptyBody): SimpleActionResponse = error("unused")
    override suspend fun getUserPlaylists(body: ProfileUserPlaylistsRequest): ProfileUserPlaylistsResponse = error("unused")
    override suspend fun getUserFollows(uid: Long, body: ProfileUserFollowsRequest): ProfileFollowListResponse = error("unused")
    override suspend fun getUserFolloweds(uid: Long, body: ProfileUserFollowedsRequest): ProfileFollowListResponse = error("unused")
    override suspend fun getUserEvents(uid: Long, body: ProfileUserEventsRequest): ProfileEventResponse = error("unused")
    override suspend fun getUserListeningRank(body: ProfileListeningRankRequest): ProfileListeningRankResponse = rankResponse
}

class SongPrivilegeMergeRepositoryTest {

    @Test
    fun `歌单详情合并顶层privileges后免除额外音质补查`() = runBlocking {
        val response = PlaylistDetailResponse(
            code = 200,
            playlist = PlaylistDetail(
                id = 3778678L,
                name = "热歌榜",
                tracks = listOf(
                    Track(id = 101L, name = "A", sq = SongFileInfo(br = 999_000)),
                    Track(id = 102L, name = "B", privilege = SongPrivilege(id = 102L, maxBrLevel = "sky")),
                    Track(id = 103L, name = "C")
                ),
                trackIds = listOf(PlaylistTrackId(101L), PlaylistTrackId(102L), PlaylistTrackId(103L))
            ),
            privileges = listOf(
                SongPrivilege(id = 101L, maxBrLevel = "jymaster"),
                SongPrivilege(id = 102L, maxBrLevel = "lossless")
            )
        )
        val playerRepo = PlayerRepositoryImpl(StubPlayerApi(SongDetailResponse(code = 200)))
        val repo = PlaylistRepositoryImpl(
            apiService = StubPlaylistApi(response),
            contentFilter = ContentFilter(UserPreferences(InMemoryPreferencesStore())),
            playerRepository = playerRepo,
            offline = testOfflineFallback()
        )

        val detail = repo.getPlaylistDetail(3778678L).first().getOrThrow()
        assertEquals("jymaster", detail.tracks[0].privilege?.maxBrLevel)
        assertEquals("sky", detail.tracks[1].privilege?.maxBrLevel)
        assertEquals(listOf(103L), tracksNeedingQualityPrefetch(detail.tracks))
    }

    @Test
    fun `songDetail单首与批量接口均合并顶层privileges`() = runBlocking {
        val api = StubPlayerApi(
            SongDetailResponse(
                code = 200,
                songs = listOf(
                    Track(id = 201L, name = "S1", hr = SongFileInfo(br = 1_500_000)),
                    Track(id = 202L, name = "S2", privilege = SongPrivilege(id = 202L, maxBrLevel = "dolby"))
                ),
                privileges = listOf(
                    SongPrivilege(id = 201L, maxBrLevel = "jymaster"),
                    SongPrivilege(id = 202L, maxBrLevel = "lossless")
                )
            )
        )
        val repo = PlayerRepositoryImpl(api)

        val single = repo.getSongDetail(201L).first().getOrThrow()
        assertEquals("jymaster", single.privilege?.maxBrLevel)

        val batch = repo.getSongDetails(listOf(201L, 202L)).first().getOrThrow()
        assertEquals("jymaster", batch[0].privilege?.maxBrLevel)
        assertEquals("dolby", batch[1].privilege?.maxBrLevel)
        assertTrue(tracksNeedingQualityPrefetch(batch).isEmpty())
    }

    @Test
    fun `个人听歌排行映射song内嵌privilege的maxBrLevel`() = runBlocking {
        val repo = ProfileRepositoryImpl(
            StubProfileApi(
                ProfileListeningRankResponse(
                    code = 200,
                    weekData = listOf(
                        ProfileListenRecord(
                            playCount = 12,
                            song = ProfileSong(
                                id = 301L,
                                name = "RankSong",
                                ar = listOf(ProfileArtist(1L, "Artist")),
                                al = ProfileAlbum(10L, "Album", "https://example.com/1.jpg"),
                                privilege = SongPrivilege(id = 301L, maxBrLevel = "jymaster")
                            )
                        )
                    )
                )
            )
        )

        val items = repo.getUserListeningRank(uid = 1L, type = 1).first().getOrThrow()
        assertEquals(1, items.size)
        assertEquals("jymaster", items[0].maxBrLevel)
    }

    @Test
    fun `日推DTO与歌单详情JSON能反序列化privilege字段`() {
        val json = Json { ignoreUnknownKeys = true }
        val dailyPayload = """
            {
              "code": 200,
              "data": {
                "dailySongs": [
                  { "id": 501, "name": "Daily", "privilege": { "id": 501, "maxBrLevel": "sky" } }
                ]
              }
            }
        """.trimIndent()
        val daily = json.decodeFromString(DailyRecommendSongsResponse.serializer(), dailyPayload)
        assertEquals("sky", daily.data?.dailySongs?.firstOrNull()?.privilege?.maxBrLevel)

        val playlistPayload = """
            {
              "code": 200,
              "playlist": { "id": 1, "name": "Top", "tracks": [{ "id": 601, "name": "Hot" }] },
              "privileges": [{ "id": 601, "maxBrLevel": "jymaster" }]
            }
        """.trimIndent()
        val detail = json.decodeFromString(PlaylistDetailResponse.serializer(), playlistPayload)
        assertEquals("jymaster", detail.privileges.firstOrNull()?.maxBrLevel)
    }
}
