package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.playlistexternal.ExternalSongLikes
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.playlistexternal.likedExternalKey
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.ui.components.CollectPromptEvent
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
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
}
