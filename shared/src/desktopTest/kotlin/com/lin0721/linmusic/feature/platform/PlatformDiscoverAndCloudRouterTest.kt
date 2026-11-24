package com.lin0721.linmusic.feature.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.lin0721.linmusic.core.auth.AccountSwitcher
import com.lin0721.linmusic.core.auth.FakeAuthRepository
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.kugou.KugouAuthService
import com.lin0721.linmusic.core.kugou.KugouClientMode
import com.lin0721.linmusic.core.kugou.KugouCloudService
import com.lin0721.linmusic.core.kugou.KugouConfig
import com.lin0721.linmusic.core.kugou.KugouDeviceStore
import com.lin0721.linmusic.core.kugou.KugouGatewayClient
import com.lin0721.linmusic.core.kugou.KugouPlaybackService
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.OnlineStateProvider
import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.ArtistId
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedPlaylist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.repository.PlatformCloudRepository
import com.lin0721.linmusic.core.platform.repository.PlatformCloudRouter
import com.lin0721.linmusic.core.platform.repository.PlatformDiscoverRepository
import com.lin0721.linmusic.core.platform.repository.PlatformDiscoverRouter
import com.lin0721.linmusic.core.platform.repository.UnifiedCloudPage
import com.lin0721.linmusic.core.platform.repository.UnifiedCloudQuota
import com.lin0721.linmusic.core.platform.repository.UnifiedCloudTrack
import com.lin0721.linmusic.core.platform.repository.UnifiedDailySong
import com.lin0721.linmusic.core.platform.repository.UnifiedDiscoverFeed
import com.lin0721.linmusic.core.platform.repository.UnifiedDiscoverPlaylistPage
import com.lin0721.linmusic.core.platform.repository.UnifiedRank
import com.lin0721.linmusic.core.platform.repository.UnifiedRankTracksPage
import com.lin0721.linmusic.core.player.KugouPlaybackProvider
import com.lin0721.linmusic.core.player.PersonalFmController
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.PlaybackQueue
import com.lin0721.linmusic.core.player.PlaybackStateStore
import com.lin0721.linmusic.core.player.PlaybackTrackMeta
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.playlistexternal.ExternalSongLikes
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.cloud.ui.PlatformCloudStateHolder
import com.lin0721.linmusic.feature.cloud.ui.PlatformCloudUiState
import com.lin0721.linmusic.feature.home.data.DailySong
import com.lin0721.linmusic.feature.home.data.HomeRepository
import com.lin0721.linmusic.feature.home.data.PersonalizedData
import com.lin0721.linmusic.feature.home.domain.HomeBlockPage
import com.lin0721.linmusic.feature.home.domain.ToplistInfo
import com.lin0721.linmusic.feature.home.ui.HomeUiState
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.playlist.domain.CreatePlaylistAndAddSongUseCase
import com.lin0721.linmusic.feature.playlist.domain.FakeCreateRepository
import com.lin0721.linmusic.feature.playlist.domain.FakePlaylistRepository
import com.lin0721.linmusic.feature.playlist.domain.FakeSongDownloader
import com.lin0721.linmusic.feature.playlist.domain.FakeUserPlaylistRepository
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.podcast.ui.FakePlaybackController
import com.lin0721.linmusic.feature.podcast.ui.FakePodcastRepository
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.podcast.ui.PodcastPlayerController
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import com.lin0721.linmusic.feature.recent.data.RecentRepository
import com.lin0721.linmusic.feature.recent.domain.RecentAlbum
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist
import com.lin0721.linmusic.feature.recent.domain.RecentSong
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlatformDiscoverAndCloudRouterTest {

    private val scheduler = TestCoroutineScheduler()
    private var vmStore = ViewModelStore()
    private var vmSeq = 0

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

    private fun kugouTrack(hash: String, name: String): UnifiedTrack = UnifiedTrack(
        id = TrackId.kugou(hash),
        name = name,
        artists = listOf(UnifiedArtist(id = ArtistId.kugou("3520"), name = "周杰伦")),
        artistNames = "周杰伦",
        album = UnifiedAlbum(id = AlbumId.kugou("9001"), name = "叶惠美", coverUrl = "http://img/alb.jpg"),
        durationMs = 245_000L,
        coverUrl = "http://img/cover.jpg",
        kugouMeta = KugouTrackMeta(hash = hash, mixSongId = 8801L, albumAudioId = 7701L, albumId = "9001")
    )

    private fun kugouAccount(uid: String = "889900") = StoredAccount(
        uid = 889900L,
        nickname = "酷狗测试号",
        platform = MusicPlatform.KUGOU,
        platformUid = uid,
        credentials = mapOf("userid" to uid, "token" to "kg_tok")
    )

    @Test
    fun `PlatformDiscoverRouter 与 PlatformCloudRouter 路由正常且受 kugouPlatformEnabled 总开关门控回退网易`() = runTest(scheduler) {
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val neteaseDiscover = FakeDiscoverRepo(MusicPlatform.NETEASE)
        val kugouDiscover = FakeDiscoverRepo(MusicPlatform.KUGOU)
        val discoverRouter = PlatformDiscoverRouter(neteaseDiscover, kugouDiscover) {
            settings.kugouPlatformEnabled.first()
        }

        val neteaseCloud = FakeCloudRepo(MusicPlatform.NETEASE)
        val kugouCloud = FakeCloudRepo(MusicPlatform.KUGOU)
        val cloudRouter = PlatformCloudRouter(neteaseCloud, kugouCloud) {
            settings.kugouPlatformEnabled.first()
        }

        val feedKg = discoverRouter.getDiscoverFeed(MusicPlatform.KUGOU).first().getOrThrow()
        assertEquals(MusicPlatform.KUGOU, feedKg.platform)
        assertEquals(1, kugouDiscover.feedCalls)
        assertEquals(0, neteaseDiscover.feedCalls)

        val cloudKg = cloudRouter.getCloudTracks(MusicPlatform.KUGOU).first().getOrThrow()
        assertEquals(MusicPlatform.KUGOU, cloudKg.platform)
        assertEquals(1, kugouCloud.listCalls)

        // 关闭酷狗总开关：发现页与云盘列表自动回退网易并跳过酷狗请求，排行榜曲目与酷狗删云盘返回关闭错误
        settings.saveKugouPlatformEnabled(false)
        val fallbackFeed = discoverRouter.getDiscoverFeed(MusicPlatform.KUGOU).first().getOrThrow()
        assertEquals(MusicPlatform.NETEASE, fallbackFeed.platform)
        assertEquals(1, kugouDiscover.feedCalls)
        assertEquals(1, neteaseDiscover.feedCalls)

        val fallbackCloud = cloudRouter.getCloudTracks(MusicPlatform.KUGOU).first().getOrThrow()
        assertEquals(MusicPlatform.NETEASE, fallbackCloud.platform)
        assertEquals(1, kugouCloud.listCalls)
        assertEquals(1, neteaseCloud.listCalls)

        val delRes = cloudRouter.deleteCloudTracks(MusicPlatform.KUGOU, cloudKg.tracks).first()
        assertTrue(delRes.isFailure)
    }

    @Test
    fun `KugouPlaybackProvider 云盘曲目优先走酷狗云盘直链通道`() = runTest(scheduler) {
        val server = MockWebServer()
        server.start()
        try {
            val cloudPaths = mutableListOf<String>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    cloudPaths += path
                    return when (path) {
                        "/bsstrackercdngz/v2/query_musicclound_url" -> MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "status": 1,
                              "error_code": 0,
                              "data": {"url": "https://cdn.kugou.com/cloud/test.mp3", "timelen": 200000}
                            }
                            """.trimIndent()
                        )
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            val store = InMemoryPreferencesStore()
            val prefs = UserPreferences(store)
            val baseUrl = server.url("/").toString().trimEnd('/')
            val config = KugouConfig(
                initialMode = KugouClientMode.LITE,
                gatewayBaseUrl = baseUrl,
                baseUrlRewriter = { baseUrl }
            )
            val deviceStore = KugouDeviceStore(store).apply {
                getOrCreateDeviceIdentity()
                updateDfid("dfid_cloud_play")
            }
            val gateway = KugouGatewayClient(config, deviceStore, prefs)
            val authService = KugouAuthService(config, deviceStore, gateway, prefs)
            val playbackService = KugouPlaybackService(config, deviceStore, gateway, prefs) {
                authService.verifyPlaybackAuth()
            }
            val cloudService = KugouCloudService(config, deviceStore, gateway, prefs)
            val provider = KugouPlaybackProvider(
                playbackService = playbackService,
                isEnabled = { true },
                cloudService = cloudService
            )

            val info = provider.resolvePlayback(
                trackId = TrackId.kugou("CLOUD123"),
                meta = PlaybackTrackMeta(
                    title = "云盘测试",
                    kugouMeta = KugouTrackMeta(
                        hash = "cloud123",
                        albumAudioId = 900L,
                        isCloudTrack = true,
                        cloudAudioId = 700L,
                        cloudName = "云盘测试",
                        cloudKvId = 500L
                    )
                )
            ).getOrThrow()

            assertEquals("https://cdn.kugou.com/cloud/test.mp3", info.url)
            assertFalse(info.isFreeTrial)
            assertEquals(listOf("/bsstrackercdngz/v2/query_musicclound_url"), cloudPaths)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `PersonalFmController 支持酷狗私人 FM 预取去重、不喜欢上报与总开关关闭自动回退网易`() = runTest(scheduler) {
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val playbackPrefs = PlaybackPreferences(InMemoryPreferencesStore())
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val stateStore = PlaybackStateStore(scope, playbackPrefs)
        val queue = PlaybackQueue()

        val neteaseRepo = DiscoverPlaybackRepo()
        val kugouDiscover = FakeDiscoverRepo(MusicPlatform.KUGOU).apply {
            fmTracks = listOf(
                kugouTrack("FM_A", "酷狗FM1"),
                kugouTrack("FM_B", "酷狗FM2")
            )
        }
        val discoverRouter = PlatformDiscoverRouter(FakeDiscoverRepo(MusicPlatform.NETEASE), kugouDiscover) {
            settings.kugouPlatformEnabled.first()
        }

        val controller = PersonalFmController(
            scope = scope,
            repository = neteaseRepo,
            settingsPreferences = settings,
            queue = queue,
            stateStore = stateStore,
            userPreferences = prefs,
            discoverRepository = discoverRouter
        )

        val item1 = QueueItem.fromUnified(kugouTrack("FM_A", "酷狗FM1"))
        queue.replaceAll(listOf(item1), 0)
        queue.setPlayContext(PersonalFmController.CONTEXT_PERSONAL_FM)
        queue.setPlayMode(PlayMode.LIST_LOOP)
        controller.setPlatform(MusicPlatform.KUGOU)
        assertEquals(MusicPlatform.KUGOU, controller.currentPlatform.value)

        // 队列只有 1 首且为酷狗 FM：触发预取后去重 FM_A，仅追加 FM_B
        controller.prefetchOnPlay(item1.songId)
        assertEquals(listOf("fm_a", "fm_b"), queue.items.value.map { it.trackId?.rawId })

        // trashCurrent 走酷狗 dislike 上报
        controller.trashCurrent(playedSeconds = 25L, onPlayIndex = {})
        assertEquals(1, kugouDiscover.trashCalls.size)
        assertEquals("fm_a", kugouDiscover.trashCalls.single().first.id.rawId)
        assertEquals(25L, kugouDiscover.trashCalls.single().second)

        // 总开关关闭后：PersonalFmController 自动回退 NETEASE
        settings.saveKugouPlatformEnabled(false)
        assertEquals(MusicPlatform.NETEASE, controller.currentPlatform.value)
    }

    @Test
    fun `HomeViewModel 浏览平台切换、酷狗发现日推榜单私人FM 路由与总开关关闭自动回退网易`() = runTest(scheduler) {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val playbackPrefs = PlaybackPreferences(InMemoryPreferencesStore())
        val player = RecordingPlaybackController()

        val kugouDiscover = FakeDiscoverRepo(MusicPlatform.KUGOU).apply {
            discoverFeedTracks = listOf(kugouTrack("DISC_1", "发现新歌"))
            dailySongs = listOf(UnifiedDailySong(kugouTrack("DAILY_1", "酷狗日推"), reason = "猜你喜欢"))
            toplists = listOf(
                UnifiedRank(MusicPlatform.KUGOU, id = "6666", rawRankId = 6666L, name = "酷狗TOP500", coverUrl = "http://img/rank.jpg")
            )
            rankTracks = listOf(kugouTrack("RANK_1", "热榜第一"))
            fmTracks = listOf(kugouTrack("KG_FM_1", "酷狗私人电台"))
        }
        val discoverRouter = PlatformDiscoverRouter(FakeDiscoverRepo(MusicPlatform.NETEASE), kugouDiscover) {
            settings.kugouPlatformEnabled.first()
        }

        val vm = trackVm { buildHomeViewModel(prefs, settings, playbackPrefs, player, discoverRouter) }
        advanceUntilIdle()

        val initialSuccess = vm.uiState.value as HomeUiState.Success
        assertEquals(MusicPlatform.NETEASE, initialSuccess.data.browsePlatform)
        assertEquals(MusicPlatform.NETEASE, vm.browsePlatform.value)
        assertTrue(vm.isKugouEnabled.value)

        // 切换到酷狗平台 -> 自动重新加载酷狗发现流
        prefs.setCurrentPlatform(MusicPlatform.KUGOU)
        advanceUntilIdle()
        val kgSuccess = vm.uiState.value as HomeUiState.Success
        assertEquals(MusicPlatform.KUGOU, vm.browsePlatform.value)
        assertEquals(MusicPlatform.KUGOU, kgSuccess.data.browsePlatform)
        assertEquals(listOf("发现新歌"), kgSuccess.data.kugouDiscoverTracks.map { it.name })
        assertEquals(listOf("酷狗日推"), kgSuccess.data.kugouDailySongs.map { it.track.name })
        assertEquals(listOf("酷狗TOP500"), kgSuccess.data.kugouToplists.map { it.name })

        vm.playDailySong(0)
        assertEquals(TrackId.kugou("DAILY_1"), player.queueCalls.last().items.single().trackId)

        vm.playKugouDiscoverTrack(0)
        assertEquals(TrackId.kugou("DISC_1"), player.queueCalls.last().items.single().trackId)

        vm.addUnifiedTrackToPlayNext(kugouTrack("DISC_NEXT", "下一首酷狗"))

        vm.playKugouRank(kgSuccess.data.kugouToplists.single(), 0)
        advanceUntilIdle()
        assertEquals(TrackId.kugou("RANK_1"), player.queueCalls.last().items.single().trackId)

        // 未登录酷狗账号时启动酷狗私人 FM 仅提示不改队列
        val toasts = mutableListOf<String>()
        val toastJob = launch { vm.toastEvent.collect { toasts += it } }
        vm.startPersonalFm()
        advanceUntilIdle()
        assertTrue(toasts.any { it.contains("酷狗账号") })
        assertTrue(player.fmPlatformCalls.isEmpty())

        // 登录酷狗账号后启动酷狗私人 FM 成功透传 MusicPlatform.KUGOU
        prefs.upsertPlatformAccount(kugouAccount(), activate = true)
        vm.startPersonalFm()
        advanceUntilIdle()
        assertEquals(1, player.fmPlatformCalls.size)
        assertEquals(MusicPlatform.KUGOU, player.fmPlatformCalls.single().second)
        assertEquals(TrackId.kugou("KG_FM_1"), player.fmPlatformCalls.single().first.single().trackId)

        // 关闭总开关 -> browsePlatform 自动切回 NETEASE 并刷新为网易数据
        settings.saveKugouPlatformEnabled(false)
        advanceUntilIdle()
        assertEquals(MusicPlatform.NETEASE, vm.browsePlatform.value)
        val reverted = vm.uiState.value as HomeUiState.Success
        assertEquals(MusicPlatform.NETEASE, reverted.data.browsePlatform)

        // 总开关关闭时禁止切换到 KUGOU
        val kgCallsBefore = kugouDiscover.feedCalls
        prefs.setCurrentPlatform(MusicPlatform.KUGOU)
        advanceUntilIdle()
        toastJob.cancel()
        assertEquals(MusicPlatform.NETEASE, vm.browsePlatform.value)
        assertEquals(kgCallsBefore, kugouDiscover.feedCalls)
    }

    @Test
    fun `PlatformCloudStateHolder 支持双平台云盘加载、翻页、删除配额更新与总开关回退`() = runTest(scheduler) {
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val kugouCloud = FakeCloudRepo(MusicPlatform.KUGOU).apply {
            page1Tracks = listOf(
                UnifiedCloudTrack(
                    track = kugouTrack("CL_1", "云歌1").copy(
                        kugouMeta = KugouTrackMeta(hash = "CL_1", isCloudTrack = true, cloudKvId = 101L)
                    ),
                    fileSizeBytes = 1000L,
                    deleteKey = "101:7701"
                )
            )
            page2Tracks = listOf(
                UnifiedCloudTrack(
                    track = kugouTrack("CL_2", "云歌2").copy(
                        kugouMeta = KugouTrackMeta(hash = "CL_2", isCloudTrack = true, cloudKvId = 102L)
                    ),
                    fileSizeBytes = 2000L,
                    deleteKey = "102:7702"
                )
            )
            hasMorePage1 = true
            quota = UnifiedCloudQuota(usedBytes = 3000L, maxBytes = 10000L, totalCount = 2)
        }
        val neteaseCloud = FakeCloudRepo(MusicPlatform.NETEASE)
        val cloudRouter = PlatformCloudRouter(neteaseCloud, kugouCloud) {
            settings.kugouPlatformEnabled.first()
        }

        val holder = PlatformCloudStateHolder(
            cloudRouter = cloudRouter,
            settingsPreferences = settings,
            resourceProvider = testResourceProvider,
            scope = CoroutineScope(StandardTestDispatcher(scheduler)),
            pageSize = 1
        )

        holder.selectPlatform(MusicPlatform.KUGOU)
        advanceUntilIdle()
        val s1 = holder.uiState.value as PlatformCloudUiState.Success
        assertEquals(MusicPlatform.KUGOU, s1.platform)
        assertEquals(listOf("云歌1"), s1.tracks.map { it.track.name })
        assertTrue(s1.hasMore)

        holder.loadMore()
        advanceUntilIdle()
        val s2 = holder.uiState.value as PlatformCloudUiState.Success
        assertEquals(listOf("云歌1", "云歌2"), s2.tracks.map { it.track.name })
        assertFalse(s2.hasMore)

        val queueItems = holder.toQueueItems()
        assertEquals(2, queueItems.size)
        assertTrue(queueItems.first().kugouMeta?.isCloudTrack == true)

        var deleteOk = false
        holder.delete(listOf(s2.tracks.first())) { deleteOk = it }
        advanceUntilIdle()
        assertTrue(deleteOk)
        val sAfterDel = holder.uiState.value as PlatformCloudUiState.Success
        assertEquals(listOf("云歌2"), sAfterDel.tracks.map { it.track.name })
        assertEquals(2000L, sAfterDel.quota.usedBytes)
        assertEquals(1, sAfterDel.quota.totalCount)

        // 总开关关闭时自动切回网易云盘
        settings.saveKugouPlatformEnabled(false)
        advanceUntilIdle()
        assertEquals(MusicPlatform.NETEASE, holder.activePlatform.value)
        val sReverted = holder.uiState.value as PlatformCloudUiState.Success
        assertEquals(MusicPlatform.NETEASE, sReverted.platform)
    }

    private fun buildHomeViewModel(
        prefs: UserPreferences,
        settings: SettingsPreferences,
        playbackPrefs: PlaybackPreferences,
        player: PlaybackController,
        discoverRouter: PlatformDiscoverRouter
    ): HomeViewModel {
        val externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        val playlistRepo = FakePlaylistRepository()
        val likeRepo = object : SongLikeRepository {
            override val likedSongIds: StateFlow<Set<Long>> = MutableStateFlow(emptySet())
            override val likedTrackIds: StateFlow<Set<TrackId>> = MutableStateFlow(emptySet())
            override fun getLikedSongIds(uid: Long) = flowOf(Result.success(emptyList<Long>()))
            override fun likeSong(songId: Long, like: Boolean) = flowOf(Result.success(Unit))
            override fun syncLikedSongIds(ids: Set<Long>) = Unit
        }
        val collectDelegate = SongCollectDelegate(
            userPlaylistRepository = FakeUserPlaylistRepository(),
            playlistRepository = playlistRepo,
            songLikeRepository = likeRepo,
            createPlaylistAndAddSongUseCase = CreatePlaylistAndAddSongUseCase(
                FakeCreateRepository(), playlistRepo, PlaylistMutationBus()
            ),
            userPreferences = prefs,
            resourceProvider = testResourceProvider,
            externalStore = externalStore,
            externalSongLikes = ExternalSongLikes(externalStore, prefs) { emptyList() },
            settingsPreferences = settings
        )
        val authRepo = FakeAuthRepository()
        val podcastPlayer = PodcastPlayerController(
            scope = CoroutineScope(StandardTestDispatcher(scheduler)),
            repository = FakePodcastRepository(),
            userPreferences = prefs,
            playbackController = FakePlaybackController(),
            resourceProvider = testResourceProvider
        )
        return HomeViewModel(
            syncProfileAfterLoginUseCase = SyncProfileAfterLoginUseCase(prefs, authRepo),
            accountSwitcher = AccountSwitcher(prefs, authRepo),
            playbackRepository = DiscoverPlaybackRepo(),
            homeRepository = SimpleHomeRepo(),
            recentRepository = SimpleRecentRepo(),
            playerManager = player,
            userPreferences = prefs,
            authRepository = authRepo,
            resourceProvider = testResourceProvider,
            loadLikedSongIdsUseCase = LoadLikedSongIdsUseCase(prefs, likeRepo),
            songLikeRepository = likeRepo,
            songCollectDelegate = collectDelegate,
            podcastPlayer = podcastPlayer,
            playlistRepository = playlistRepo,
            onlineState = object : OnlineStateProvider {
                override val online = MutableStateFlow(true)
            },
            userPlaylistRepository = FakeUserPlaylistRepository(),
            songDownloadManager = FakeSongDownloader(),
            playlistMutationBus = PlaylistMutationBus(),
            settingsPreferences = settings,
            playbackPreferences = playbackPrefs,
            platformDiscoverRouter = discoverRouter
        )
    }

    private class RecordingPlaybackController : PlaybackController by FakePlaybackController() {
        val queueCalls = mutableListOf<FakePlaybackController.QueueCall>()
        val fmPlatformCalls = mutableListOf<Pair<List<QueueItem>, MusicPlatform>>()

        override fun playQueue(
            items: List<QueueItem>,
            startIndex: Int,
            playContext: String?,
            source: PlaySource?,
            startPositionMs: Long
        ) {
            queueCalls += FakePlaybackController.QueueCall(items, startIndex, playContext, startPositionMs)
        }

        override fun startPersonalFm(items: List<QueueItem>, platform: MusicPlatform) {
            fmPlatformCalls += items to platform
        }
    }

    private class FakeDiscoverRepo(private val ownPlatform: MusicPlatform) : PlatformDiscoverRepository {
        var feedCalls = 0
        var discoverFeedTracks: List<UnifiedTrack> = emptyList()
        var dailySongs: List<UnifiedDailySong> = emptyList()
        var toplists: List<UnifiedRank> = emptyList()
        var rankTracks: List<UnifiedTrack> = emptyList()
        var fmTracks: List<UnifiedTrack> = emptyList()
        val trashCalls = mutableListOf<Pair<UnifiedTrack, Long>>()

        override fun getDailyRecommendSongs(platform: MusicPlatform) = flowOf(Result.success(dailySongs))

        override fun getPersonalFmSongs(platform: MusicPlatform, mode: PersonalFmMode) = flowOf(Result.success(fmTracks))

        override fun trashFmSong(platform: MusicPlatform, track: UnifiedTrack, playedSeconds: Long): Flow<Result<Unit>> {
            trashCalls += track to playedSeconds
            return flowOf(Result.success(Unit))
        }

        override fun getRecommendedPlaylists(platform: MusicPlatform, page: Int, pageSize: Int) =
            flowOf(Result.success(UnifiedDiscoverPlaylistPage(emptyList(), page, false)))

        override fun getRankList(platform: MusicPlatform) = flowOf(Result.success(toplists))

        override fun getRankTracks(rank: UnifiedRank, page: Int, pageSize: Int) =
            flowOf(Result.success(UnifiedRankTracksPage(rank, rankTracks, rankTracks.size, page, false)))

        override fun getDiscoverFeed(platform: MusicPlatform, refresh: Boolean): Flow<Result<UnifiedDiscoverFeed>> {
            feedCalls++
            return flowOf(
                Result.success(
                    UnifiedDiscoverFeed(
                        platform = ownPlatform,
                        dailySongs = dailySongs,
                        recommendPlaylists = emptyList(),
                        toplists = toplists,
                        discoverTracks = discoverFeedTracks
                    )
                )
            )
        }
    }

    private class FakeCloudRepo(private val ownPlatform: MusicPlatform) : PlatformCloudRepository {
        var listCalls = 0
        var page1Tracks: List<UnifiedCloudTrack> = emptyList()
        var page2Tracks: List<UnifiedCloudTrack> = emptyList()
        var hasMorePage1 = false
        var quota = UnifiedCloudQuota()

        override fun getCloudTracks(platform: MusicPlatform, page: Int, pageSize: Int): Flow<Result<UnifiedCloudPage>> {
            listCalls++
            val tracks = if (page <= 1) page1Tracks else page2Tracks
            val hasMore = if (page <= 1) hasMorePage1 else false
            return flowOf(Result.success(UnifiedCloudPage(ownPlatform, tracks, quota, page, hasMore)))
        }

        override fun deleteCloudTracks(platform: MusicPlatform, tracks: List<UnifiedCloudTrack>) =
            flowOf(Result.success(Unit))
    }

    private class SimpleHomeRepo : HomeRepository {
        override fun getHomeBlockPage(refresh: Boolean, cursor: String) =
            flowOf(Result.success(HomeBlockPage(emptyList(), nextCursor = null, hasMore = false)))
        override fun getPersonalizedPlaylists() = flowOf(Result.success(PersonalizedData()))
        override fun getToplistDetail() = flowOf(Result.success(emptyList<ToplistInfo>()))
        override fun getDailyRecommendSongs() = flowOf(Result.success(emptyList<DailySong>()))
        override fun dislikeDailySong(songId: Long) = flowOf(Result.success(Unit))
        override fun getHistoryRecommendDates() = flowOf(Result.success(emptyList<String>()))
        override fun getHistoryRecommendDetail(date: String) = flowOf(Result.success(emptyList<DailySong>()))
    }

    private class SimpleRecentRepo : RecentRepository {
        override fun getRecentSongs() = flowOf(Result.success(emptyList<RecentSong>()))
        override fun getRecentPlaylists() = flowOf(Result.success(emptyList<RecentPlaylist>()))
        override fun getRecentAlbums() = flowOf(Result.success(emptyList<RecentAlbum>()))
    }

    private class DiscoverPlaybackRepo : PlaybackRepository {
        override val playlistRecorded: SharedFlow<Long> = MutableSharedFlow()
        override fun getSongUrl(songId: Long) = flowOf(Result.success(""))
        override fun getSongPlaybackInfo(songId: Long) = flowOf(Result.failure<SongPlaybackInfo>(UnsupportedOperationException()))
        override fun getLyrics(songId: Long) = flowOf(Result.success(emptyList<LyricLine>()))
        override fun getRawLyrics(songId: Long) = flowOf(Result.success(""))
        override fun getSongDetail(songId: Long) = flowOf(Result.failure<Track>(UnsupportedOperationException()))
        override fun getSimilarSongs(songId: Long) = flowOf(Result.success(emptyList<Track>()))
        override fun getIntelligenceSongs(songId: Long, playlistId: Long) = flowOf(Result.success(emptyList<Track>()))
        override fun getPersonalFmSongs(mode: PersonalFmMode) = flowOf(Result.success(emptyList<Track>()))
        override fun trashFmSong(songId: Long, playedSeconds: Long) = flowOf(Result.success(Unit))
        override fun reportStartPlay(songId: Long, source: PlaySource?) = flowOf(Result.success(Unit))
        override fun reportPlayEnd(songId: Long, playedSeconds: Long, source: PlaySource?) = flowOf(Result.success(Unit))
    }
}
