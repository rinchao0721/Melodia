package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.auth.AccountSwitcher
import com.lin0721.linmusic.core.auth.FakeAuthRepository
import com.lin0721.linmusic.core.auth.PlatformSessionRegistry
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedPlaylist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.repository.PlatformAlbumRepository
import com.lin0721.linmusic.core.platform.repository.PlatformArtistRepository
import com.lin0721.linmusic.core.platform.repository.PlatformLibraryRepository
import com.lin0721.linmusic.core.platform.repository.PlatformLibraryRouter
import com.lin0721.linmusic.core.platform.repository.PlatformPlaylistRepository
import com.lin0721.linmusic.core.platform.repository.PlatformPlaylistRouter
import com.lin0721.linmusic.core.platform.repository.UnifiedCollectedAlbum
import com.lin0721.linmusic.core.platform.repository.UnifiedUserPlaylist
import com.lin0721.linmusic.core.platform.repository.UnifiedUserRecordTrack
import com.lin0721.linmusic.core.platform.repository.UnifiedUserSubcount
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.playlistexternal.ExternalSongLikes
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.playlistexternal.likedExternalKey
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.ui.components.CollectPromptEvent
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongCollectDelegateTest {

    private fun createEnv(
        playlists: List<UserPlaylist> = emptyList(),
        queueItems: () -> List<QueueItem> = { emptyList() }
    ): Env {
        SongCollectDelegate.clearCache()
        val userPreferences = UserPreferences(InMemoryPreferencesStore())
        val likeRepo = FakeSongLikeRepository()
        val playlistRepo = FakePlaylistRepository()
        val externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        val delegate = SongCollectDelegate(
            userPlaylistRepository = FakeUserPlaylistRepository(playlists),
            playlistRepository = playlistRepo,
            songLikeRepository = likeRepo,
            createPlaylistAndAddSongUseCase = CreatePlaylistAndAddSongUseCase(
                FakeCreateRepository(),
                playlistRepo,
                PlaylistMutationBus()
            ),
            userPreferences = userPreferences,
            resourceProvider = testResourceProvider,
            externalStore = externalStore,
            externalSongLikes = ExternalSongLikes(externalStore, userPreferences, queueItems)
        )
        return Env(userPreferences, likeRepo, playlistRepo, externalStore, delegate)
    }

    private data class Env(
        val userPreferences: UserPreferences,
        val likeRepo: FakeSongLikeRepository,
        val playlistRepo: FakePlaylistRepository,
        val externalStore: PlaylistExternalTrackStore,
        val delegate: SongCollectDelegate
    )

    @Test
    fun `quickLike 未登录时提示登录且不调用接口`() = runBlocking {
        val env = createEnv()
        val toasts = mutableListOf<String>()
        val events = mutableListOf<CollectPromptEvent>()
        val job = launch(UnconfinedTestDispatcher()) {
            env.delegate.collectPromptEvent.collect { events += it }
        }

        env.delegate.quickLike(100L) { toasts += it }
        job.cancel()

        assertEquals(listOf("请先登录账号"), toasts)
        assertTrue(env.likeRepo.likeCalls.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `quickLike 占位id跳过请求`() = runBlocking {
        val env = createEnv()
        env.userPreferences.saveUserProfile(UserProfile(uid = 1L, nickname = "我", avatarUrl = ""))
        val toasts = mutableListOf<String>()
        val events = mutableListOf<CollectPromptEvent>()
        val job = launch(UnconfinedTestDispatcher()) {
            env.delegate.collectPromptEvent.collect { events += it }
        }

        env.delegate.quickLike(-1L) { toasts += it }
        job.cancel()

        assertTrue(toasts.isEmpty())
        assertTrue(env.likeRepo.likeCalls.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `quickLike 网易歌曲成功时收藏并发送收藏提示事件`() = runBlocking {
        val env = createEnv()
        env.userPreferences.saveUserProfile(UserProfile(uid = 1L, nickname = "我", avatarUrl = ""))
        val toasts = mutableListOf<String>()
        val events = mutableListOf<CollectPromptEvent>()
        val job = launch(UnconfinedTestDispatcher()) {
            env.delegate.collectPromptEvent.collect { events += it }
        }

        env.delegate.quickLike(42L) { toasts += it }
        job.cancel()

        assertEquals(listOf(42L), env.likeRepo.likeCalls)
        assertTrue(42L in env.likeRepo.likedSongIds.value)
        assertEquals(listOf(CollectPromptEvent(42L)), events)
        assertTrue(toasts.isEmpty())
    }

    @Test
    fun `quickLike 失败时不发送提示事件并回传错误提示`() = runBlocking {
        val env = createEnv()
        env.userPreferences.saveUserProfile(UserProfile(uid = 1L, nickname = "我", avatarUrl = ""))
        env.likeRepo.failingIds += 77L
        val toasts = mutableListOf<String>()
        val events = mutableListOf<CollectPromptEvent>()
        val job = launch(UnconfinedTestDispatcher()) {
            env.delegate.collectPromptEvent.collect { events += it }
        }

        env.delegate.quickLike(77L) { toasts += it }
        job.cancel()

        assertEquals(1, toasts.size)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `quickLike 外部歌曲写入本机喜欢列表并发送提示事件`() = runBlocking {
        val track = ExternalTrack(
            id = "mid-1",
            name = "晴天",
            artists = "周杰伦",
            platform = MusicPlatform.QQ
        )
        val env = createEnv {
            listOf(
                QueueItem(
                    songId = track.queueSongId,
                    title = track.name,
                    artist = track.artists,
                    coverUrl = "",
                    external = track
                )
            )
        }
        env.userPreferences.saveUserProfile(UserProfile(uid = 8L, nickname = "我", avatarUrl = ""))
        val events = mutableListOf<CollectPromptEvent>()
        val toasts = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher()) {
            env.delegate.collectPromptEvent.collect { events += it }
        }

        env.delegate.quickLike(track.queueSongId) { toasts += it }
        job.cancel()

        val saved = env.externalStore.entriesOf(likedExternalKey(8L)).first()
        assertEquals(listOf(track.queueSongId), saved.map { it.track.queueSongId })
        assertTrue(track.queueSongId in env.likeRepo.likedSongIds.value)
        assertEquals(listOf(CollectPromptEvent(track.queueSongId)), events)
        assertTrue(toasts.isEmpty())
    }

    private val ownPlaylists = listOf(
        UserPlaylist(id = 1L, name = "我喜欢的音乐", trackCount = 321, userId = 1L),
        UserPlaylist(id = 2L, name = "通勤", trackCount = 12, userId = 1L)
    )

    private fun collectItem(playlistId: Long, name: String, contains: Boolean) = PlaylistCollectItem(
        playlistId = playlistId,
        playlistName = name,
        coverUrl = "",
        isInitiallyContains = !contains,
        isContains = contains
    )

    @Test
    fun `prepare 已点赞歌单带出歌曲数`() = runBlocking {
        val env = createEnv(playlists = ownPlaylists.take(1))
        env.userPreferences.saveUserProfile(UserProfile(uid = 1L, nickname = "我", avatarUrl = ""))

        env.delegate.prepare(5L, setOf(5L)) {}

        val items = env.delegate.state.value.collectItems
        assertEquals(listOf(321), items.map { it.trackCount })
        assertTrue(items.single().isContains)
    }

    @Test
    fun `prepareExternal 各歌单带出歌曲数`() = runBlocking {
        val env = createEnv(playlists = ownPlaylists)
        env.userPreferences.saveUserProfile(UserProfile(uid = 1L, nickname = "我", avatarUrl = ""))
        val track = ExternalTrack(id = "mid-2", name = "七里香", artists = "周杰伦", platform = MusicPlatform.QQ)

        env.delegate.prepareExternal(track) {}

        assertEquals(listOf(321, 12), env.delegate.state.value.collectItems.map { it.trackCount })
    }

    @Test
    fun `PlaylistCollectItem 歌曲数默认为0`() {
        assertEquals(0, collectItem(2L, "通勤", true).trackCount)
    }

    @Test
    fun `save silent 时不提示成功`() = runBlocking {
        val env = createEnv()
        env.userPreferences.saveUserProfile(UserProfile(uid = 1L, nickname = "我", avatarUrl = ""))
        val toasts = mutableListOf<String>()

        env.delegate.save(8L, listOf(collectItem(2L, "通勤", true)), emptySet(), { toasts += it }, {}, silent = true)

        assertEquals(1, env.playlistRepo.manipulateCalls.size)
        assertTrue(toasts.isEmpty())
    }

    @Test
    fun `save 默认提示成功`() = runBlocking {
        val env = createEnv()
        env.userPreferences.saveUserProfile(UserProfile(uid = 1L, nickname = "我", avatarUrl = ""))
        val toasts = mutableListOf<String>()

        env.delegate.save(8L, listOf(collectItem(2L, "通勤", true)), emptySet(), { toasts += it }, {})

        assertEquals(listOf("歌单收藏更新成功"), toasts)
    }

    @Test
    fun `save silent 时失败仍提示`() = runBlocking {
        val env = createEnv()
        env.userPreferences.saveUserProfile(UserProfile(uid = 1L, nickname = "我", avatarUrl = ""))
        env.playlistRepo.manipulateResult = Result.failure(IllegalStateException("网络错误"))
        val toasts = mutableListOf<String>()

        env.delegate.save(8L, listOf(collectItem(2L, "通勤", true)), emptySet(), { toasts += it }, {}, silent = true)

        assertEquals(1, toasts.size)
        assertTrue(toasts.none { it == "歌单收藏更新成功" })
    }

    @Test
    fun `saveExternal silent 时不提示成功`() = runBlocking {
        val env = createEnv()
        env.userPreferences.saveUserProfile(UserProfile(uid = 1L, nickname = "我", avatarUrl = ""))
        val track = ExternalTrack(id = "mid-3", name = "稻香", artists = "周杰伦", platform = MusicPlatform.QQ)
        val toasts = mutableListOf<String>()

        env.delegate.saveExternal(track, listOf(collectItem(2L, "通勤", true)), silent = true) { toasts += it }

        assertTrue(toasts.isEmpty())
        assertEquals(1, env.externalStore.all.first()[2L].orEmpty().size)
    }

    @Test
    fun `酷狗歌单曲目缓存按账号隔离，关闭开关与切号退登时正确清理`() = runBlocking {
        SongCollectDelegate.clearCache()
        val prefsStore = InMemoryPreferencesStore()
        val userPreferences = UserPreferences(prefsStore)
        val settings = SettingsPreferences(prefsStore)
        val likeRepo = FakeSongLikeRepository()
        val playlistRepo = FakePlaylistRepository()
        val externalStore = PlaylistExternalTrackStore(prefsStore)

        val accountA = StoredAccount(
            uid = 101L,
            nickname = "酷狗A",
            platform = MusicPlatform.KUGOU,
            platformUid = "101",
            credentials = mapOf("token" to "ta", "userid" to "101")
        )
        val accountB = StoredAccount(
            uid = 102L,
            nickname = "酷狗B",
            platform = MusicPlatform.KUGOU,
            platformUid = "102",
            credentials = mapOf("token" to "tb", "userid" to "102")
        )
        userPreferences.upsertPlatformAccount(accountA, activate = true)
        userPreferences.upsertPlatformAccount(accountB, activate = false)

        val sharedPlaylistId = PlaylistId.kugou("3001")
        val targetTrack = UnifiedTrack(id = TrackId.kugou("KG_ISO_1"), name = "晴天")
        var detailCalls = 0
        var playlistsCalls = 0
        val kugouLib = object : PlatformLibraryRepository {
            override fun getUserPlaylists(accountId: AccountId, limit: Int): Flow<Result<List<UnifiedUserPlaylist>>> {
                playlistsCalls++
                return flowOf(Result.success(listOf(UnifiedUserPlaylist(id = sharedPlaylistId, name = "共用名歌单", trackCount = 1))))
            }
            override fun getFavoriteArtists(platform: MusicPlatform) = flowOf(Result.success(emptyList<UnifiedArtist>()))
            override fun getCollectedAlbums(platform: MusicPlatform, limit: Int) = flowOf(Result.success(emptyList<UnifiedCollectedAlbum>()))
            override fun getUserRecord(accountId: AccountId, type: Int) = flowOf(Result.success(emptyList<UnifiedUserRecordTrack>()))
            override fun getUserSubcount(platform: MusicPlatform) = flowOf(Result.success(UnifiedUserSubcount()))
            override fun updatePlaylistOrder(platform: MusicPlatform, playlistIds: List<PlaylistId>) = flowOf(Result.success(Unit))
            override fun setPlaylistTop(playlistId: PlaylistId, top: Boolean) = flowOf(Result.success(Unit))
        }
        val kugouPlaylistRepo = object : PlatformPlaylistRepository {
            override fun getPlaylistDetail(playlistId: PlaylistId): Flow<Result<UnifiedPlaylist>> {
                detailCalls++
                val activeUid = kotlinx.coroutines.runBlocking { userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()?.uid }
                val tracks = if (activeUid == 101L) listOf(targetTrack) else emptyList()
                return flowOf(Result.success(UnifiedPlaylist(id = playlistId, name = "共用名歌单", tracks = tracks, trackCount = tracks.size)))
            }
            override fun loadMoreTracks(trackIds: List<TrackId>) = flowOf(Result.success(emptyList<UnifiedTrack>()))
            override fun loadAllTracks(detail: UnifiedPlaylist) = flowOf(Result.success(detail.tracks))
            override fun createPlaylist(platform: MusicPlatform, name: String, isPrivate: Boolean) = flowOf(Result.failure<UnifiedPlaylist>(UnsupportedOperationException()))
            override fun subscribePlaylist(playlistId: PlaylistId, subscribe: Boolean) = flowOf(Result.success(Unit))
            override fun addTracksToPlaylist(playlistId: PlaylistId, tracks: List<UnifiedTrack>) = flowOf(Result.success(Unit))
            override fun removeTracksFromPlaylist(playlistId: PlaylistId, tracks: List<UnifiedTrack>) = flowOf(Result.success(Unit))
            override fun renamePlaylist(playlistId: PlaylistId, name: String) = flowOf(Result.success(Unit))
            override fun updateDescription(playlistId: PlaylistId, description: String) = flowOf(Result.success(Unit))
            override fun deletePlaylist(playlistId: PlaylistId) = flowOf(Result.success(Unit))
            override fun updatePlaylistCover(playlistId: PlaylistId, imageBytes: ByteArray) = flowOf(Result.success(""))
            override fun sortPlaylistTracks(playlistId: PlaylistId, orderedTracks: List<UnifiedTrack>, listVer: Long) = flowOf(Result.success(Unit))
        }
        val stubLib = object : PlatformLibraryRepository by kugouLib {}
        val stubPl = object : PlatformPlaylistRepository by kugouPlaylistRepo {}
        val delegate = SongCollectDelegate(
            userPlaylistRepository = FakeUserPlaylistRepository(),
            playlistRepository = playlistRepo,
            songLikeRepository = likeRepo,
            createPlaylistAndAddSongUseCase = CreatePlaylistAndAddSongUseCase(FakeCreateRepository(), playlistRepo, PlaylistMutationBus()),
            userPreferences = userPreferences,
            resourceProvider = testResourceProvider,
            externalStore = externalStore,
            externalSongLikes = ExternalSongLikes(externalStore, userPreferences) { emptyList() },
            platformLibraryRouter = PlatformLibraryRouter(stubLib, kugouLib) { runBlocking { settings.kugouPlatformEnabled.first() } },
            platformPlaylistRouter = PlatformPlaylistRouter(stubPl, kugouPlaylistRepo, externalStore) { runBlocking { settings.kugouPlatformEnabled.first() } },
            settingsPreferences = settings
        )

        // 1. 账号 A 命中包含该曲目
        delegate.prepareUnified(targetTrack, emptySet()) {}
        assertTrue(delegate.state.value.collectItems.single().isContains)
        assertEquals(1, detailCalls)

        // 2. 切到账号 B：即使不显式清缓存也不应串用账号 A 的 contains 结果
        userPreferences.switchPlatformAccount(accountB.accountId)
        delegate.prepareUnified(targetTrack, emptySet()) {}
        assertFalse(delegate.state.value.collectItems.single().isContains)
        assertEquals(2, detailCalls)

        // 3. 切回账号 A 命中缓存不重复请求；再通过 AccountSwitcher 切换（metadataCache=null）应清掉酷狗缓存
        userPreferences.switchPlatformAccount(accountA.accountId)
        delegate.prepareUnified(targetTrack, emptySet()) {}
        assertEquals(2, detailCalls)

        val switcher = AccountSwitcher(
            userPreferences = userPreferences,
            authRepository = FakeAuthRepository(userPreferences),
            metadataCache = null,
            platformSessionRegistry = PlatformSessionRegistry(emptyList())
        )
        assertTrue(switcher.switchPlatformAccount(accountB.accountId))
        assertTrue(switcher.switchPlatformAccount(accountA.accountId))
        delegate.prepareUnified(targetTrack, emptySet()) {}
        assertEquals(3, detailCalls)

        // 4. 关闭酷狗开关会清空酷狗歌单与曲目缓存，重新打开后需重新拉取
        settings.saveKugouPlatformEnabled(false)
        settings.saveKugouPlatformEnabled(true)
        val playlistsBeforeDisable = playlistsCalls
        delegate.prepareUnified(targetTrack, emptySet()) {}
        assertTrue(playlistsCalls > playlistsBeforeDisable)
        assertEquals(4, detailCalls)
    }
}
