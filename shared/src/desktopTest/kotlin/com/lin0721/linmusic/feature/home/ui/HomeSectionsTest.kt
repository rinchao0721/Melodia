package com.lin0721.linmusic.feature.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.reflect.KClass
import com.lin0721.linmusic.core.auth.AccountSwitcher
import com.lin0721.linmusic.core.auth.FakeAuthRepository
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.OnlineStateProvider
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedCreator
import com.lin0721.linmusic.core.platform.UnifiedPlaylist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.repository.KugouPlatformDiscoverRepository
import com.lin0721.linmusic.core.platform.repository.NeteasePlatformDiscoverRepository
import com.lin0721.linmusic.core.platform.repository.PlatformDiscoverRouter
import com.lin0721.linmusic.core.platform.repository.PlatformLibraryRepository
import com.lin0721.linmusic.core.platform.repository.UnifiedCollectedAlbum
import com.lin0721.linmusic.core.platform.repository.UnifiedDailySong
import com.lin0721.linmusic.core.platform.repository.UnifiedDiscoverFeed
import com.lin0721.linmusic.core.platform.repository.UnifiedRank
import com.lin0721.linmusic.core.platform.repository.UnifiedRankTracksPage
import com.lin0721.linmusic.core.platform.repository.UnifiedUserPlaylist
import com.lin0721.linmusic.core.platform.repository.UnifiedUserRecordTrack
import com.lin0721.linmusic.core.platform.repository.UnifiedUserSubcount
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.playlistexternal.ExternalSongLikes
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.feature.home.data.DailySong
import com.lin0721.linmusic.feature.home.data.HomeRepository
import com.lin0721.linmusic.feature.home.data.PersonalizedData
import com.lin0721.linmusic.feature.home.data.PersonalizedPlaylist
import com.lin0721.linmusic.feature.home.domain.HomeBlockPage
import com.lin0721.linmusic.feature.home.domain.HomeCard
import com.lin0721.linmusic.feature.home.domain.HomeCardKind
import com.lin0721.linmusic.feature.home.domain.HomeCardTarget
import com.lin0721.linmusic.feature.home.domain.HomeQuickEntryType
import com.lin0721.linmusic.feature.home.domain.HomeSection
import com.lin0721.linmusic.feature.home.domain.HomeShelf
import com.lin0721.linmusic.feature.home.domain.ToplistInfo
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.playlist.domain.CreatePlaylistAndAddSongUseCase
import com.lin0721.linmusic.feature.playlist.domain.FakeCreateRepository
import com.lin0721.linmusic.feature.playlist.domain.FakePlaylistRepository
import com.lin0721.linmusic.feature.playlist.domain.FakeSongDownloader
import com.lin0721.linmusic.feature.playlist.domain.FakeUserPlaylistRepository
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.playlist.ui.kugouRankEntityId
import com.lin0721.linmusic.feature.podcast.ui.FakePlaybackController
import com.lin0721.linmusic.feature.podcast.ui.FakePodcastRepository
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.podcast.ui.PodcastPlayerController
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import com.lin0721.linmusic.feature.recent.data.RecentRepository
import com.lin0721.linmusic.feature.recent.domain.RecentAlbum
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist
import com.lin0721.linmusic.feature.recent.domain.RecentSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeSectionsTest {
    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = StandardTestDispatcher(scheduler)

    private lateinit var vmStore: ViewModelStore
    private var vmSeq = 0

    // ViewModel 统一托管，tearDown 时清理，避免订阅全局 AccountEvents 的协程泄漏到后续测试
    private inline fun <reified T : ViewModel> trackVm(crossinline create: () -> T): T {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <M : ViewModel> create(modelClass: KClass<M>, extras: CreationExtras): M = create() as M
        }
        return ViewModelProvider.create(vmStore, factory)["vm_${vmSeq++}", T::class]
    }

    @Before
    fun setup() {
        vmStore = ViewModelStore()
        vmSeq = 0
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        vmStore.clear()
        scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun `网易首页组装出的区块与现有数据一一对应`() = runTest(scheduler) {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val playbackPrefs = PlaybackPreferences(InMemoryPreferencesStore())
        val player = RecordingPlaybackController()

        val neteaseRepo = object : SimpleHomeRepo() {
            override fun getHomeBlockPage(refresh: Boolean, cursor: String) = flowOf(
                Result.success(
                    HomeBlockPage(
                        shelves = listOf(
                            HomeShelf(
                                blockCode = "HOMEPAGE_BLOCK_PLAYLIST_RCMD",
                                title = "雷达歌单",
                                cards = listOf(
                                    HomeCard.Playlist(101L, "私人雷达", "https://p/101.jpg", "精选")
                                )
                            )
                        ),
                        nextCursor = null,
                        hasMore = false
                    )
                )
            )
            override fun getDailyRecommendSongs() = flowOf(
                Result.success(
                    listOf(
                        DailySong(id = 201L, name = "晴天")
                    )
                )
            )
        }
        val recentRepo = object : SimpleRecentRepo() {
            override fun getRecentPlaylists() = flowOf(
                Result.success(listOf(RecentPlaylist(301L, "最近听的歌单", "https://p/301.jpg", "作者", 1000L, "刚刚")))
            )
        }

        val vm = buildHomeViewModel(prefs, settings, playbackPrefs, player, homeRepository = neteaseRepo, recentRepository = recentRepo)
        scheduler.advanceUntilIdle()

        val sections = vm.sections.value
        assertEquals(3, sections.size)

        val quick = sections[0] as HomeSection.Quick
        assertEquals("301", quick.items.single().entityId)
        assertEquals(HomeCardKind.PLAYLIST, quick.items.single().kind)

        val daily = sections[1] as HomeSection.Daily
        assertEquals(MusicPlatform.NETEASE, daily.platform)
        assertEquals("201", daily.items.single().entityId)

        val shelf = sections[2] as HomeSection.Shelf
        assertEquals("雷达歌单", shelf.title)
        assertEquals("101", shelf.cards.single().entityId)
        assertEquals(HomeCardKind.PLAYLIST, shelf.cards.single().kind)

        // 验证统一打开目标
        val target = vm.openTargetOf(shelf.cards.single())
        assertEquals(HomeCardTarget.PlaylistEntity("101", "私人雷达", isAlbum = false), target)
    }

    @Test
    fun `网易货架缺失时兜底展示推荐歌单`() = runTest(scheduler) {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val playbackPrefs = PlaybackPreferences(InMemoryPreferencesStore())
        val player = RecordingPlaybackController()

        val neteaseRepo = object : SimpleHomeRepo() {
            override fun getPersonalizedPlaylists() = flowOf(
                Result.success(
                    PersonalizedData(
                        playlists = listOf(PersonalizedPlaylist(401L, "兜底歌单", "https://p/401.jpg"))
                    )
                )
            )
        }

        val vm = buildHomeViewModel(prefs, settings, playbackPrefs, player, homeRepository = neteaseRepo)
        scheduler.advanceUntilIdle()

        val shelf = vm.sections.value.single() as HomeSection.Shelf
        assertEquals("推荐歌单", shelf.title)
        assertEquals("401", shelf.cards.single().entityId)
    }

    @Test
    fun `酷狗首页组装预期区块与动作按平台分派`() = runTest(scheduler) {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val playbackPrefs = PlaybackPreferences(InMemoryPreferencesStore())
        val player = RecordingPlaybackController()
        val kugouAccountId = AccountId(MusicPlatform.KUGOU, "10086")
        prefs.upsertPlatformAccount(StoredAccount(10086L, "酷狗用户", "", "token", platform = MusicPlatform.KUGOU))
        prefs.setCurrentPlatform(MusicPlatform.KUGOU)

        val rank = UnifiedRank(
            id = "rank_666",
            platform = MusicPlatform.KUGOU,
            rawRankId = 666L,
            rankCid = 10L,
            name = "酷狗TOP500",
            coverUrl = "https://kg/rank.jpg",
            updateFrequency = "每日更新"
        )
        val discoverTrack = UnifiedTrack(
            id = TrackId(MusicPlatform.KUGOU, "HASH_001"),
            name = "酷狗新歌",
            artists = listOf(UnifiedArtist(name = "歌手A")),
            coverUrl = "https://kg/t1.jpg",
            durationMs = 200_000L,
            kugouMeta = KugouTrackMeta(hash = "HASH_001")
        )
        val recPlaylist = UnifiedPlaylist(
            id = PlaylistId(MusicPlatform.KUGOU, "gcid_rec_1"),
            name = "酷狗推荐歌单",
            coverUrl = "https://kg/pl1.jpg",
            creator = UnifiedCreator(nickname = "官号")
        )
        val discoverRouter = PlatformDiscoverRouter(
            netease = object : com.lin0721.linmusic.core.platform.repository.PlatformDiscoverRepository {
                override fun getDiscoverFeed(platform: MusicPlatform, refresh: Boolean) = flowOf(Result.success(UnifiedDiscoverFeed(platform = MusicPlatform.NETEASE)))
                override fun getDailyRecommendSongs(platform: MusicPlatform) = flowOf(Result.success(emptyList<UnifiedDailySong>()))
                override fun getRecommendedPlaylists(platform: MusicPlatform, page: Int, pageSize: Int) = flowOf(Result.failure<com.lin0721.linmusic.core.platform.repository.UnifiedDiscoverPlaylistPage>(IllegalStateException()))
                override fun getRankList(platform: MusicPlatform) = flowOf(Result.success(emptyList<UnifiedRank>()))
                override fun getRankTracks(rank: UnifiedRank, page: Int, pageSize: Int) = flowOf(Result.failure<UnifiedRankTracksPage>(IllegalStateException()))
                override fun getPersonalFmSongs(platform: MusicPlatform, mode: com.lin0721.linmusic.core.player.PersonalFmMode) = flowOf(Result.success(emptyList<UnifiedTrack>()))
                override fun trashFmSong(platform: MusicPlatform, track: UnifiedTrack, playedSeconds: Long) = flowOf(Result.success(Unit))
            },
            kugou = object : com.lin0721.linmusic.core.platform.repository.PlatformDiscoverRepository {
                override fun getDiscoverFeed(platform: MusicPlatform, refresh: Boolean) = flowOf(
                    Result.success(
                        UnifiedDiscoverFeed(
                            platform = MusicPlatform.KUGOU,
                            discoverTracks = listOf(discoverTrack),
                            dailySongs = listOf(UnifiedDailySong(discoverTrack, "每日推荐")),
                            recommendPlaylists = listOf(recPlaylist),
                            toplists = listOf(rank)
                        )
                    )
                )
                override fun getDailyRecommendSongs(platform: MusicPlatform) = flowOf(Result.success(emptyList<UnifiedDailySong>()))
                override fun getRecommendedPlaylists(platform: MusicPlatform, page: Int, pageSize: Int) = flowOf(Result.failure<com.lin0721.linmusic.core.platform.repository.UnifiedDiscoverPlaylistPage>(IllegalStateException()))
                override fun getRankList(platform: MusicPlatform) = flowOf(Result.success(listOf(rank)))
                override fun getRankTracks(rank: UnifiedRank, page: Int, pageSize: Int) =
                    flowOf(Result.success(UnifiedRankTracksPage(rank = rank, tracks = listOf(discoverTrack), hasMore = false)))
                override fun getPersonalFmSongs(platform: MusicPlatform, mode: com.lin0721.linmusic.core.player.PersonalFmMode) = flowOf(Result.success(emptyList<UnifiedTrack>()))
                override fun trashFmSong(platform: MusicPlatform, track: UnifiedTrack, playedSeconds: Long) = flowOf(Result.success(Unit))
            },
            isKugouEnabled = { true }
        )
        val libraryRepo = object : PlatformLibraryRepository {
            override fun getUserPlaylists(accountId: AccountId, limit: Int) = flowOf(
                Result.success(
                    listOf(
                        UnifiedUserPlaylist(
                            id = PlaylistId(MusicPlatform.KUGOU, "gcid_user_1"),
                            name = "我的酷狗歌单",
                            coverUrl = "https://kg/u1.jpg"
                        )
                    )
                )
            )
            override fun getFavoriteArtists(platform: MusicPlatform) = flowOf(Result.success(emptyList<UnifiedArtist>()))
            override fun getCollectedAlbums(platform: MusicPlatform, limit: Int) = flowOf(Result.success(emptyList<UnifiedCollectedAlbum>()))
            override fun getUserRecord(accountId: AccountId, type: Int) = flowOf(Result.success(emptyList<UnifiedUserRecordTrack>()))
            override fun getUserSubcount(platform: MusicPlatform) = flowOf(Result.success(UnifiedUserSubcount()))
            override fun updatePlaylistOrder(platform: MusicPlatform, playlistIds: List<PlaylistId>) = flowOf(Result.success(Unit))
            override fun setPlaylistTop(playlistId: PlaylistId, top: Boolean) = flowOf(Result.success(Unit))
        }

        val vm = buildHomeViewModel(
            prefs, settings, playbackPrefs, player,
            discoverRouter = discoverRouter,
            platformLibraryRepository = libraryRepo
        )
        scheduler.advanceUntilIdle()

        val sections = vm.sections.value
        assertEquals(5, sections.size)

        // 1. Quick: 4 个固定入口 + 1 个酷狗账号歌单
        val quick = sections[0] as HomeSection.Quick
        assertEquals(5, quick.items.size)
        assertEquals(HomeQuickEntryType.DAILY_RECOMMEND, quick.items[0].quickEntryType)
        assertEquals(HomeQuickEntryType.PERSONAL_FM, quick.items[1].quickEntryType)
        assertEquals(HomeQuickEntryType.TOPLIST, quick.items[2].quickEntryType)
        assertEquals(HomeQuickEntryType.CLOUD, quick.items[3].quickEntryType)
        assertEquals("我的酷狗歌单", quick.items[4].title)

        // 2. Daily
        assertTrue(sections[1] is HomeSection.Daily)

        // 3. 三个货架：发现新歌、推荐歌单、酷狗排行榜
        val sDiscover = sections[2] as HomeSection.Shelf
        assertEquals("发现新歌", sDiscover.title)
        assertEquals(HomeCardKind.SONG, sDiscover.cards.single().kind)

        val sPlaylist = sections[3] as HomeSection.Shelf
        assertEquals("推荐歌单", sPlaylist.title)
        assertEquals(HomeCardKind.PLAYLIST, sPlaylist.cards.single().kind)

        val sRank = sections[4] as HomeSection.Shelf
        assertEquals("酷狗排行榜", sRank.title)
        assertTrue(sRank.showRank)
        assertEquals(HomeCardKind.RANK, sRank.cards.single().kind)
        assertEquals(kugouRankEntityId(rank), sRank.cards.single().entityId)

        // 动作分派：播放发现新歌、下一首播放、打开排行榜
        vm.playCard(sDiscover.cards.single())
        scheduler.advanceUntilIdle()
        assertEquals("酷狗发现", player.queueCalls.last().third)

        vm.addCardToPlayNext(sDiscover.cards.single())
        scheduler.advanceUntilIdle()
        assertEquals(1, player.playNextCalls.size)

        val rankTarget = vm.openTargetOf(sRank.cards.single())
        assertEquals(HomeCardTarget.PlaylistEntity(kugouRankEntityId(rank), "酷狗TOP500", false), rankTarget)

        val toplistEntryTarget = vm.onQuickEntryClick(HomeQuickEntryType.TOPLIST)
        assertEquals(HomeCardTarget.PlaylistEntity(kugouRankEntityId(rank), "酷狗TOP500", false), toplistEntryTarget)
    }

    private fun buildHomeViewModel(
        prefs: UserPreferences,
        settings: SettingsPreferences,
        playbackPrefs: PlaybackPreferences,
        player: PlaybackController,
        homeRepository: HomeRepository = SimpleHomeRepo(),
        recentRepository: RecentRepository = SimpleRecentRepo(),
        discoverRouter: PlatformDiscoverRouter? = null,
        platformLibraryRepository: PlatformLibraryRepository? = null
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
        return trackVm { HomeViewModel(
            syncProfileAfterLoginUseCase = SyncProfileAfterLoginUseCase(prefs, authRepo),
            accountSwitcher = AccountSwitcher(prefs, authRepo),
            playbackRepository = SimplePlaybackRepo(),
            homeRepository = homeRepository,
            recentRepository = recentRepository,
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
            platformDiscoverRouter = discoverRouter,
            platformLibraryRepository = platformLibraryRepository
        ) }
    }

    private class RecordingPlaybackController : PlaybackController by FakePlaybackController() {
        val queueCalls = mutableListOf<Triple<List<QueueItem>, Int, String>>()
        val playNextCalls = mutableListOf<List<QueueItem>>()

        override fun playQueue(
            items: List<QueueItem>,
            startIndex: Int,
            playContext: String?,
            source: com.lin0721.linmusic.core.player.PlaySource?,
            startPositionMs: Long
        ) {
            queueCalls += Triple(items, startIndex, playContext.orEmpty())
        }

        override fun addToPlayNext(items: List<QueueItem>) {
            playNextCalls += items
        }
    }

    private open class SimpleHomeRepo : HomeRepository {
        override fun getHomeBlockPage(refresh: Boolean, cursor: String) =
            flowOf(Result.success(HomeBlockPage(emptyList(), null, false)))
        override fun getPersonalizedPlaylists() = flowOf(Result.success(PersonalizedData()))
        override fun getDailyRecommendSongs() = flowOf(Result.success(emptyList<DailySong>()))
        override fun getToplistDetail() = flowOf(Result.success(emptyList<ToplistInfo>()))
        override fun dislikeDailySong(songId: Long) = flowOf(Result.success(Unit))
        override fun getHistoryRecommendDates() = flowOf(Result.success(emptyList<String>()))
        override fun getHistoryRecommendDetail(date: String) = flowOf(Result.success(emptyList<DailySong>()))
    }

    private class SimplePlaybackRepo : com.lin0721.linmusic.core.player.data.PlaybackRepository {
        override val playlistRecorded = kotlinx.coroutines.flow.MutableSharedFlow<Long>()
        override fun getSongUrl(songId: Long) = flowOf(Result.success(""))
        override fun getSongDetail(songId: Long) = flowOf(Result.success(com.lin0721.linmusic.core.model.Track(songId, "")))
        override fun getSongPlaybackInfo(songId: Long) = flowOf(Result.failure<com.lin0721.linmusic.core.player.data.SongPlaybackInfo>(IllegalStateException("unused")))
        override fun getLyrics(songId: Long) = flowOf(Result.success(emptyList<com.lin0721.linmusic.core.player.domain.LyricLine>()))
        override fun getRawLyrics(songId: Long) = flowOf(Result.success(""))
        override fun reportStartPlay(songId: Long, source: com.lin0721.linmusic.core.player.PlaySource?) = flowOf(Result.success(Unit))
        override fun reportPlayEnd(songId: Long, playedSeconds: Long, source: com.lin0721.linmusic.core.player.PlaySource?) = flowOf(Result.success(Unit))
        override fun getSimilarSongs(id: Long) = flowOf(Result.success(emptyList<com.lin0721.linmusic.core.model.Track>()))
        override fun getIntelligenceSongs(id: Long, pid: Long) = flowOf(Result.success(emptyList<com.lin0721.linmusic.core.model.Track>()))
        override fun getPersonalFmSongs(mode: com.lin0721.linmusic.core.player.PersonalFmMode) = flowOf(Result.success(emptyList<com.lin0721.linmusic.core.model.Track>()))
        override fun trashFmSong(songId: Long, playedSeconds: Long) = flowOf(Result.success(Unit))
    }

    private open class SimpleRecentRepo : RecentRepository {
        override fun getRecentPlaylists() = flowOf(Result.success(emptyList<RecentPlaylist>()))
        override fun getRecentSongs() = flowOf(Result.success(emptyList<com.lin0721.linmusic.feature.recent.domain.RecentSong>()))
        override fun getRecentAlbums() = flowOf(Result.success(emptyList<com.lin0721.linmusic.feature.recent.domain.RecentAlbum>()))
    }
}
