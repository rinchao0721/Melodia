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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
        var likeGateById: MutableMap<Long, CompletableDeferred<LikeSongResponse>> = mutableMapOf()
        override suspend fun likeSong(body: LikeSongRequest): LikeSongResponse {
            likeGateById[body.trackId]?.let { return it.await() }
            return LikeSongResponse(code = 200)
        }
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
        var shouldFailSync = false
        var fetchCalls = 0
        var likeCalls = 0
        var clearCacheCount = 0
        var likeGateByHash: MutableMap<String, CompletableDeferred<Result<Unit>>> = mutableMapOf()

        override fun clearCache() {
            clearCacheCount++
        }

        override suspend fun fetchLikedTrackMap(): Result<Map<String, Long>> {
            fetchCalls++
            if (shouldFailSync) return Result.failure(IllegalStateException("同步失败"))
            return Result.success(likedMap.toMap())
        }

        override suspend fun likeTrack(
            trackName: String,
            hash: String,
            albumId: String?,
            mixSongId: Long?
        ): Result<Unit> {
            likeCalls++
            val key = hash.lowercase()
            likeGateByHash[key]?.let { return it.await() }
            if (shouldFailLike) return Result.failure(IllegalStateException("云端收藏失败"))
            likedMap[key] = 99L
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
    fun `切换酷狗账号即使新号同步失败也先清空旧号红心不串号`() = runBlocking {
        loginKugou(889900L)
        val kugouService = FakeKugouLikeService(userPreferences)
        val repo = SongLikeRepositoryImpl(
            apiService = FakeSongLikeApi(),
            scope = scope,
            kugouLikeService = kugouService,
            userPreferences = userPreferences
        )
        repo.syncKugouLikedTracks().first()
        val trackA = TrackId.kugou("hash_a")
        repo.likeTrack(trackA, like = true, legacyExternalId = -701L).first()
        assertTrue(TrackId.kugou("hash_synced") in repo.likedTrackIds.value)
        assertTrue(trackA in repo.likedTrackIds.value)
        assertTrue(-701L in repo.likedSongIds.value)

        // 切到账号 B，同时模拟首次同步网络失败
        loginKugou(990011L)
        kugouService.shouldFailSync = true
        AccountEvents.notifyPlatformSwitched(
            platform = MusicPlatform.KUGOU,
            previousAccountId = AccountId.kugou("889900"),
            currentAccountId = AccountId.kugou("990011"),
            reason = AccountSwitchReason.SWITCHED
        )
        delay(50)

        assertFalse(TrackId.kugou("hash_synced") in repo.likedTrackIds.value)
        assertFalse(trackA in repo.likedTrackIds.value)
        assertFalse(-701L in repo.likedSongIds.value)
    }

    @Test
    fun `酷狗与网易红心并发修改时失败回滚仅还原目标单曲不覆盖在途变更`() = runBlocking {
        loginKugou()
        val api = FakeSongLikeApi()
        val kugouService = FakeKugouLikeService(userPreferences)
        val repo = SongLikeRepositoryImpl(
            apiService = api,
            scope = scope,
            kugouLikeService = kugouService,
            userPreferences = userPreferences
        )

        // 1. 酷狗并发：hash_1 在途挂起，hash_2 先成功，随后 hash_1 失败回滚，hash_2 应保留
        val kgGate = CompletableDeferred<Result<Unit>>()
        kugouService.likeGateByHash["hash_1"] = kgGate
        val kgTrack1 = TrackId.kugou("hash_1")
        val kgTrack2 = TrackId.kugou("hash_2")
        val pendingKg1 = async { repo.likeTrack(kgTrack1, like = true, legacyExternalId = -801L).first() }
        val resKg2 = repo.likeTrack(kgTrack2, like = true, legacyExternalId = -802L).first()
        assertTrue(resKg2.isSuccess)
        kgGate.complete(Result.failure(IllegalStateException("失败")))
        assertTrue(pendingKg1.await().isFailure)

        assertFalse(kgTrack1 in repo.likedTrackIds.value)
        assertFalse(-801L in repo.likedSongIds.value)
        assertTrue(kgTrack2 in repo.likedTrackIds.value)
        assertTrue(-802L in repo.likedSongIds.value)

        // 2. 网易并发：201 在途挂起，202 先成功，随后 201 失败回滚，202 应保留
        val neGate = CompletableDeferred<LikeSongResponse>()
        api.likeGateById[201L] = neGate
        val pendingNe1 = async { repo.likeSong(201L, like = true).first() }
        val resNe2 = repo.likeSong(202L, like = true).first()
        assertTrue(resNe2.isSuccess)
        neGate.complete(LikeSongResponse(code = 500))
        assertTrue(pendingNe1.await().isFailure)

        assertFalse(201L in repo.likedSongIds.value)
        assertFalse(TrackId.netease(201L) in repo.likedTrackIds.value)
        assertTrue(202L in repo.likedSongIds.value)
        assertTrue(TrackId.netease(202L) in repo.likedTrackIds.value)
    }

    @Test
    fun `kugouPlatformEnabled 关闭时跳过酷狗同步与红心请求`() = runBlocking {
        loginKugou()
        var enabled = false
        val kugouService = FakeKugouLikeService(userPreferences)
        val repo = SongLikeRepositoryImpl(
            apiService = FakeSongLikeApi(),
            scope = scope,
            kugouLikeService = kugouService,
            userPreferences = userPreferences,
            isKugouEnabled = { enabled }
        )

        val synced = repo.syncKugouLikedTracks().first().getOrThrow()
        assertTrue(synced.isEmpty())
        assertEquals(0, kugouService.fetchCalls)

        val likeRes = repo.likeTrack(TrackId.kugou("hash_disabled"), like = true).first()
        assertTrue(likeRes.isFailure)
        assertEquals(0, kugouService.likeCalls)
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

        // 通过 likeSong 负数 ID 取消本机外部红心也应经由 stateLock 同步更新状态
        val unlikeResult = repo.likeSong(track.queueSongId, like = false).first()
        assertTrue(unlikeResult.isSuccess)
        delay(50)
        assertFalse(track.queueSongId in repo.likedSongIds.value)
        assertFalse(TrackId.kugou("hash_local") in repo.likedTrackIds.value)
    }
}
