package com.lin0721.linmusic.feature.recent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.repository.PlatformLibraryRepository
import com.lin0721.linmusic.core.platform.repository.UnifiedCollectedAlbum
import com.lin0721.linmusic.core.platform.repository.UnifiedUserPlaylist
import com.lin0721.linmusic.core.platform.repository.UnifiedUserRecordTrack
import com.lin0721.linmusic.core.platform.repository.UnifiedUserSubcount
import com.lin0721.linmusic.core.player.LocalRecentPlaylist
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.podcast.ui.FakePlaybackController
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import com.lin0721.linmusic.feature.recent.data.RecentRepository
import com.lin0721.linmusic.feature.recent.domain.RecentAlbum
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist
import com.lin0721.linmusic.feature.recent.domain.RecentSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
class RecentPlayViewModelPlatformTest {
    private val scheduler = TestCoroutineScheduler()
    private lateinit var vmStore: ViewModelStore
    private var vmSeq = 0

    private val kugouAccount = AccountId(MusicPlatform.KUGOU, "10086")
    private val otherAccount = AccountId(MusicPlatform.KUGOU, "20000")

    // ViewModel 统一托管，tearDown 时清理
    private inline fun <reified T : ViewModel> trackVm(crossinline create: () -> T): T {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <M : ViewModel> create(modelClass: KClass<M>, extras: CreationExtras): M = create() as M
        }
        return ViewModelProvider.create(vmStore, factory)["vm_${vmSeq++}", T::class]
    }

    @Before
    fun setUp() {
        vmStore = ViewModelStore()
        vmSeq = 0
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        vmStore.clear()
        scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private fun kugouTrack(hash: String) = UnifiedTrack(
        id = TrackId(MusicPlatform.KUGOU, hash),
        name = "歌 $hash",
        artists = listOf(UnifiedArtist(name = "歌手")),
        kugouMeta = KugouTrackMeta(hash = hash)
    )

    private fun local(
        raw: String,
        kind: PlaySource.Kind,
        account: AccountId?,
        playTime: Long,
        platform: MusicPlatform = MusicPlatform.KUGOU
    ) = LocalRecentPlaylist(
        id = raw.hashCode().toLong(),
        name = "名称$raw",
        coverUrl = "https://cover/$raw",
        playTime = playTime,
        platform = platform,
        rawId = raw,
        accountKey = account?.canonical,
        kind = kind
    )

    private class CountingRecentRepository : RecentRepository {
        var songCalls = 0
        override fun getRecentSongs(): Flow<Result<List<RecentSong>>> {
            songCalls++
            return flowOf(Result.success(listOf(RecentSong(Track(id = 7L), 100L, "10:00"))))
        }
        override fun getRecentPlaylists(): Flow<Result<List<RecentPlaylist>>> =
            flowOf(Result.success(listOf(RecentPlaylist(5L, "网易歌单", "", "某人", 90L, "09:00"))))
        override fun getRecentAlbums(): Flow<Result<List<RecentAlbum>>> =
            flowOf(Result.success(listOf(RecentAlbum(6L, "网易专辑", "", "歌手", 80L, "08:00"))))
    }

    private class RecordingLibraryRepo(
        private val records: List<UnifiedUserRecordTrack>
    ) : PlatformLibraryRepository {
        val recordAccounts = mutableListOf<AccountId>()
        override fun getUserPlaylists(accountId: AccountId, limit: Int) = flowOf(Result.success(emptyList<UnifiedUserPlaylist>()))
        override fun getFavoriteArtists(platform: MusicPlatform) = flowOf(Result.success(emptyList<UnifiedArtist>()))
        override fun getCollectedAlbums(platform: MusicPlatform, limit: Int) = flowOf(Result.success(emptyList<UnifiedCollectedAlbum>()))
        override fun getUserRecord(accountId: AccountId, type: Int): Flow<Result<List<UnifiedUserRecordTrack>>> {
            recordAccounts += accountId
            return flowOf(Result.success(records))
        }
        override fun getUserSubcount(platform: MusicPlatform) = flowOf(Result.success(UnifiedUserSubcount()))
        override fun updatePlaylistOrder(platform: MusicPlatform, playlistIds: List<PlaylistId>) = flowOf(Result.success(Unit))
        override fun setPlaylistTop(playlistId: PlaylistId, top: Boolean) = flowOf(Result.success(Unit))
    }

    private class RecordingPlayer : PlaybackController by FakePlaybackController() {
        val queueCalls = mutableListOf<Pair<List<QueueItem>, Int>>()
        override fun playQueue(
            items: List<QueueItem>,
            startIndex: Int,
            playContext: String?,
            source: PlaySource?,
            startPositionMs: Long
        ) {
            queueCalls += items to startIndex
        }
    }

    private suspend fun seedPrefs(
        platform: MusicPlatform,
        kugouUid: Long = 10086L
    ): Pair<UserPreferences, PlaybackPreferences> {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        prefs.upsertPlatformAccount(StoredAccount(kugouUid, "酷狗用户", "", "token", platform = MusicPlatform.KUGOU))
        prefs.upsertPlatformAccount(StoredAccount(1L, "网易用户", "", "token", platform = MusicPlatform.NETEASE))
        prefs.setCurrentPlatform(platform)
        val playback = PlaybackPreferences(InMemoryPreferencesStore())
        playback.recordRecentPlaylist(local("pl_1", PlaySource.Kind.PLAYLIST, kugouAccount, 300L))
        playback.recordRecentPlaylist(local("rank_9", PlaySource.Kind.RANK, kugouAccount, 200L))
        playback.recordRecentPlaylist(local("al_1", PlaySource.Kind.ALBUM, kugouAccount, 100L))
        playback.recordRecentPlaylist(local("pl_other", PlaySource.Kind.PLAYLIST, otherAccount, 400L))
        playback.recordRecentPlaylist(local("al_other", PlaySource.Kind.ALBUM, otherAccount, 500L))
        playback.recordRecentPlaylist(local("12345", PlaySource.Kind.PLAYLIST, null, 600L, MusicPlatform.NETEASE))
        return prefs to playback
    }

    @Test
    fun `酷狗账号三个 Tab 分别取听歌历史与本机歌单专辑记录并按账号过滤`() = runTest(scheduler) {
        val (prefs, playback) = seedPrefs(MusicPlatform.KUGOU)
        val recent = CountingRecentRepository()
        val library = RecordingLibraryRepo(
            listOf(
                UnifiedUserRecordTrack(kugouTrack("H1"), 3),
                UnifiedUserRecordTrack(kugouTrack("H2"), 1)
            )
        )

        val vm = trackVm {
            RecentPlayViewModel(
                recentRepository = recent,
                playerManager = RecordingPlayer(),
                resourceProvider = testResourceProvider,
                userPreferences = prefs,
                playbackPreferences = playback,
                platformLibraryRepository = library
            )
        }
        advanceUntilIdle()

        val state = vm.uiState.value as RecentPlayUiState.Success
        assertEquals(MusicPlatform.KUGOU, state.platform)
        // 歌曲 Tab 走酷狗听歌历史，且只查当前酷狗账号
        assertEquals(listOf(kugouAccount.canonical), library.recordAccounts.map { it.canonical })
        assertEquals(0, recent.songCalls)
        assertEquals(listOf("歌 H1", "歌 H2"), state.songs.map { it.track.name })
        assertTrue(state.songs.all { it.unified != null })

        // 歌单 Tab：歌单与榜单，排除其他账号与网易记录，按播放时间倒序
        assertEquals(listOf("名称pl_1", "名称rank_9"), state.playlists.map { it.name })
        assertEquals(
            listOf(PlaylistId.of(MusicPlatform.KUGOU, "pl_1").canonical, "rank_9"),
            state.playlists.map { it.entityId }
        )

        // 专辑 Tab：只含当前账号的专辑记录
        assertEquals(listOf("名称al_1"), state.albums.map { it.name })
        assertEquals(AlbumId.of(MusicPlatform.KUGOU, "al_1").canonical, state.albums.single().entityId)
    }

    @Test
    fun `当前酷狗账号没有本机记录时歌单与专辑 Tab 为空`() = runTest(scheduler) {
        // 本机记录都属于 10086 与 20000，当前账号是 30000
        val (prefs, playback) = seedPrefs(MusicPlatform.KUGOU, kugouUid = 30000L)
        val library = RecordingLibraryRepo(listOf(UnifiedUserRecordTrack(kugouTrack("H1"), 1)))

        val vm = trackVm {
            RecentPlayViewModel(
                recentRepository = CountingRecentRepository(),
                playerManager = RecordingPlayer(),
                resourceProvider = testResourceProvider,
                userPreferences = prefs,
                playbackPreferences = playback,
                platformLibraryRepository = library
            )
        }
        advanceUntilIdle()

        val state = vm.uiState.value as RecentPlayUiState.Success
        assertEquals(1, state.songs.size)
        assertEquals(listOf(AccountId(MusicPlatform.KUGOU, "30000").canonical), library.recordAccounts.map { it.canonical })
        assertTrue(state.playlists.isEmpty())
        assertTrue(state.albums.isEmpty())
    }

    @Test
    fun `酷狗歌曲点击后以统一模型入队并定位到点击项`() = runTest(scheduler) {
        val (prefs, playback) = seedPrefs(MusicPlatform.KUGOU)
        val library = RecordingLibraryRepo(
            listOf(UnifiedUserRecordTrack(kugouTrack("H1"), 3), UnifiedUserRecordTrack(kugouTrack("H2"), 1))
        )
        val player = RecordingPlayer()

        val vm = trackVm {
            RecentPlayViewModel(
                recentRepository = CountingRecentRepository(),
                playerManager = player,
                resourceProvider = testResourceProvider,
                userPreferences = prefs,
                playbackPreferences = playback,
                platformLibraryRepository = library
            )
        }
        advanceUntilIdle()
        val state = vm.uiState.value as RecentPlayUiState.Success
        vm.playSong(state.songs[1])

        val (items, startIndex) = player.queueCalls.single()
        assertEquals(1, startIndex)
        assertEquals(
            listOf(TrackId(MusicPlatform.KUGOU, "H1"), TrackId(MusicPlatform.KUGOU, "H2")),
            items.map { it.trackId }
        )
    }

    @Test
    fun `网易账号仍走原有仓储且忽略酷狗本机记录`() = runTest(scheduler) {
        val (prefs, playback) = seedPrefs(MusicPlatform.NETEASE)
        val recent = CountingRecentRepository()
        val library = RecordingLibraryRepo(listOf(UnifiedUserRecordTrack(kugouTrack("H1"), 1)))
        val player = RecordingPlayer()

        val vm = trackVm {
            RecentPlayViewModel(
                recentRepository = recent,
                playerManager = player,
                resourceProvider = testResourceProvider,
                userPreferences = prefs,
                playbackPreferences = playback,
                platformLibraryRepository = library
            )
        }
        advanceUntilIdle()

        val state = vm.uiState.value as RecentPlayUiState.Success
        assertEquals(MusicPlatform.NETEASE, state.platform)
        assertEquals(listOf(7L), state.songs.map { it.track.id })
        assertEquals(listOf(5L), state.playlists.map { it.id })
        assertEquals(listOf(6L), state.albums.map { it.id })
        assertNull(state.playlists.single().entityId)
        assertNull(state.albums.single().entityId)
        assertTrue(library.recordAccounts.isEmpty())

        vm.playSong(state.songs.single())
        assertEquals(listOf(7L), player.queueCalls.single().first.map { it.songId })
    }

    @Test
    fun `未注入平台依赖时按网易路径处理`() = runTest(scheduler) {
        val recent = CountingRecentRepository()
        val vm = trackVm {
            RecentPlayViewModel(
                recentRepository = recent,
                playerManager = RecordingPlayer(),
                resourceProvider = testResourceProvider
            )
        }
        advanceUntilIdle()

        val state = vm.uiState.value as RecentPlayUiState.Success
        assertEquals(MusicPlatform.NETEASE, state.platform)
        assertEquals(1, recent.songCalls)
    }
}
