package com.lin0721.linmusic.core.songlike

import com.lin0721.linmusic.core.auth.AccountEvents
import com.lin0721.linmusic.core.auth.AccountSwitchReason
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.kugou.KugouConfig
import com.lin0721.linmusic.core.kugou.KugouDeviceStore
import com.lin0721.linmusic.core.kugou.KugouGatewayClient
import com.lin0721.linmusic.core.kugou.KugouLikeService
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.playlistexternal.ExternalSongLikes
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SongLikeRepositoryImplKugouTest {

    private lateinit var store: InMemoryPreferencesStore
    private lateinit var userPreferences: UserPreferences
    private lateinit var scope: CoroutineScope

    private class FakeSongLikeApi : SongLikeApi {
        var likedIds = listOf(101L)
        override suspend fun likeSong(body: LikeSongRequest) = LikeSongResponse(code = 200)
        override suspend fun getLikedSongIds(body: LikeSongListRequest) = LikeSongListResponse(code = 200, ids = likedIds)
    }

    private class FakeKugouLikeService(
        userPreferences: UserPreferences
    ) : KugouLikeService(
        gatewayClient = KugouGatewayClient(
            config = KugouConfig(),
            deviceStore = KugouDeviceStore(InMemoryPreferencesStore()),
            userPreferences = userPreferences
        ),
        userPreferences = userPreferences
    ) {
        var likedMap: MutableMap<String, Long> = linkedMapOf("hash_synced" to 11L)
        var shouldFailLike = false
        var clearCacheCount = 0

        override fun clearCache() {
            clearCacheCount++
        }

        override suspend fun fetchLikedTrackMap(): Result<Map<String, Long>> =
            Result.success(likedMap.toMap())

        override suspend fun likeTrack(
            trackName: String,
            hash: String,
            albumId: String?,
            mixSongId: Long?
        ): Result<Unit> {
            if (shouldFailLike) return Result.failure(IllegalStateException("云端收藏失败"))
            likedMap[hash.lowercase()] = 99L
            return Result.success(Unit)
        }

        override suspend fun unlikeTrack(hash: String): Result<Unit> {
            if (shouldFailLike) return Result.failure(IllegalStateException("云端取消失败"))
            likedMap.remove(hash.lowercase())
            return Result.success(Unit)
        }
    }

    @Before
    fun setUp() {
        store = InMemoryPreferencesStore()
        userPreferences = UserPreferences(store)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun loginKugou(uid: Long = 889900L) {
        userPreferences.upsertPlatformAccount(
            StoredAccount(
                uid = uid,
                nickname = "酷狗测试",
                platform = MusicPlatform.KUGOU,
                platformUid = uid.toString(),
                credentials = mapOf("userid" to uid.toString(), "token" to "token_$uid")
            ),
            activate = true
        )
    }

    @Test
    fun `已登录酷狗同步云端红心并与网易红心合并到 likedTrackIds，失败时回滚`() = runBlocking {
        loginKugou()
        val kugouService = FakeKugouLikeService(userPreferences)
        val repo = SongLikeRepositoryImpl(
            apiService = FakeSongLikeApi(),
            scope = scope,
            kugouLikeService = kugouService,
            userPreferences = userPreferences
        )

        // 拉取网易红心
        repo.getLikedSongIds(1L).first()
        val synced = repo.syncKugouLikedTracks().first().getOrThrow()
        assertEquals(setOf(TrackId.kugou("hash_synced")), synced)
        assertTrue(TrackId.netease(101L) in repo.likedTrackIds.value)
        assertTrue(TrackId.kugou("hash_synced") in repo.likedTrackIds.value)

        // 乐观收藏成功
        val newTrack = TrackId.kugou("HASH_NEW")
        val legacyId = -501L
        val addRes = repo.likeTrack(
            trackId = newTrack,
            like = true,
            title = "新歌",
            kugouMeta = KugouTrackMeta(hash = "hash_new", albumId = "12", mixSongId = 34L),
            legacyExternalId = legacyId
        ).first()
        assertTrue(addRes.isSuccess)
        assertTrue(newTrack in repo.likedTrackIds.value)
        assertTrue(legacyId in repo.likedSongIds.value)

        // 取消收藏失败时自动回滚
        kugouService.shouldFailLike = true
        val failRes = repo.likeTrack(newTrack, like = false, legacyExternalId = legacyId).first()
        assertTrue(failRes.isFailure)
        assertTrue(newTrack in repo.likedTrackIds.value)
        assertTrue(legacyId in repo.likedSongIds.value)
    }

    @Test
    fun `酷狗退出账号时清空云端红心缓存与状态`() = runBlocking {
        loginKugou()
        val kugouService = FakeKugouLikeService(userPreferences)
        val repo = SongLikeRepositoryImpl(
            apiService = FakeSongLikeApi(),
            scope = scope,
            kugouLikeService = kugouService,
            userPreferences = userPreferences
        )
        repo.syncKugouLikedTracks().first()
        assertTrue(TrackId.kugou("hash_synced") in repo.likedTrackIds.value)

        userPreferences.removePlatformAccount(AccountId.kugou("889900"))
        AccountEvents.notifyPlatformSwitched(
            platform = MusicPlatform.KUGOU,
            previousAccountId = AccountId.kugou("889900"),
            currentAccountId = null,
            reason = AccountSwitchReason.LOGGED_OUT
        )
        delay(50)

        assertTrue(kugouService.clearCacheCount >= 1)
        assertFalse(TrackId.kugou("hash_synced") in repo.likedTrackIds.value)
    }

    @Test
    fun `未登录酷狗时传入 legacyExternalId 回退到本机 ExternalSongLikes`() = runBlocking {
        val track = ExternalTrack(
            id = "hash_local",
            name = "酷狗歌",
            artists = "歌手",
            albumName = "专辑",
            durationMs = 180_000L,
            platform = MusicPlatform.KUGOU,
            hash = "hash_local"
        )
        userPreferences.upsertPlatformAccount(
            StoredAccount(uid = 1000L, nickname = "网易用户", platform = MusicPlatform.NETEASE, platformUid = "1000"),
            activate = true
        )
        val externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        val externalLikes = ExternalSongLikes(
            store = externalStore,
            userPreferences = userPreferences,
            queueSnapshot = {
                listOf(
                    com.lin0721.linmusic.core.player.QueueItem(
                        songId = track.queueSongId,
                        title = track.name,
                        artist = track.artists,
                        coverUrl = "",
                        external = track
                    )
                )
            }
        )
        val kugouService = FakeKugouLikeService(userPreferences)
        val repo = SongLikeRepositoryImpl(
            apiService = FakeSongLikeApi(),
            externalLikes = externalLikes,
            scope = scope,
            kugouLikeService = kugouService,
            userPreferences = userPreferences
        )

        val result = repo.likeTrack(
            trackId = TrackId.kugou("hash_local"),
            like = true,
            title = track.name,
            legacyExternalId = track.queueSongId
        ).first()
        assertTrue(result.isSuccess)
        delay(50)
        assertTrue(track.queueSongId in repo.likedSongIds.value)
        assertTrue(TrackId.kugou("hash_local") in repo.likedTrackIds.value)
    }
}
