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
        fun kugouTrack(hash: String) = UnifiedTrack(
            id = TrackId(MusicPlatform.KUGOU, hash),
            name = "歌 $hash",
            artists = listOf(UnifiedArtist(name = "歌手B")),
            kugouMeta = KugouTrackMeta(hash = hash)
        )
        val newSongs = listOf(kugouTrack("NEW_1"), kugouTrack("NEW_2"))
        val styleTracks = listOf(kugouTrack("STYLE_1"), kugouTrack("STYLE_2"), kugouTrack("STYLE_3"))
        val newAlbum = com.lin0721.linmusic.core.platform.UnifiedAlbum(
            id = com.lin0721.linmusic.core.platform.AlbumId.kugou("215588294"),
            name = "沉默交响",
            coverUrl = "https://kg/a1.jpg",
            artists = listOf(UnifiedArtist(name = "鲜克"))
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
                            toplists = listOf(rank),
                            newSongs = newSongs,
                            newAlbums = listOf(newAlbum),
                            styleRecommend = com.lin0721.linmusic.core.platform.repository.UnifiedStyleRecommend("华语流行", styleTracks)
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
        // 无酷狗最近播放记录时不输出 Quick 区块：Daily + 6 个 Shelf
        assertEquals(7, sections.size)

        // 1. Daily
        assertTrue(sections[0] is HomeSection.Daily)

        // 2. 六个货架按固定顺序
        val shelves = sections.drop(1).map { it as HomeSection.Shelf }
        assertEquals(
            listOf("发现新歌", "新歌速递", "风格推荐 · 华语流行", "推荐歌单", "新碟上架", "酷狗排行榜"),
            shelves.map { it.title }
        )
        val sDiscover = shelves[0]
        assertEquals(HomeCardKind.SONG, sDiscover.cards.single().kind)
        val sStyle = shelves[2]
        assertEquals(3, sStyle.cards.size)
        assertEquals(HomeCardKind.PLAYLIST, shelves[3].cards.single().kind)
        val sAlbum = shelves[4]
        assertEquals(HomeCardKind.ALBUM, sAlbum.cards.single().kind)
        val sRank = shelves[5]
        assertTrue(sRank.showRank)
        assertEquals(HomeCardKind.RANK, sRank.cards.single().kind)
        assertEquals(kugouRankEntityId(rank), sRank.cards.single().entityId)

        // 风格推荐的歌以本货架为队列，从点中的那首开始
        vm.playCard(sStyle.cards[1], sStyle.cards, sStyle.title)
        scheduler.advanceUntilIdle()
        val (styleQueue, styleStart, styleContext) = player.queueCalls.last()
        assertEquals(styleTracks.map { it.id.canonical }, styleQueue.map { it.effectiveTrackId.canonical })
        assertEquals(1, styleStart)
        assertEquals("风格推荐 · 华语流行", styleContext)

        val albumTarget = vm.openTargetOf(sAlbum.cards.single()) as HomeCardTarget.PlaylistEntity
        assertTrue(albumTarget.isAlbum)

        // 动作分派：播放发现新歌、下一首播放、打开排行榜
        vm.playCard(sDiscover.cards.single())
        scheduler.advanceUntilIdle()
        assertEquals("酷狗发现", player.queueCalls.last().third)

        vm.addCardToPlayNext(sDiscover.cards.single())
        scheduler.advanceUntilIdle()
        assertEquals(1, player.playNextCalls.size)

        val rankTarget = vm.openTargetOf(sRank.cards.single())
        assertEquals(HomeCardTarget.PlaylistEntity(kugouRankEntityId(rank), "酷狗TOP500", false), rankTarget)
    }

    @Test
    fun `kugou quick section filters by current account and caps at 8 items`() = runTest(scheduler) {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val playbackPrefs = PlaybackPreferences(InMemoryPreferencesStore())
        val account1 = AccountId(MusicPlatform.KUGOU, "1001")
        val account2 = AccountId(MusicPlatform.KUGOU, "1002")
        prefs.upsertPlatformAccount(
            StoredAccount(
                uid = 1001L,
                platform = MusicPlatform.KUGOU,
                platformUid = "1001",
                nickname = "酷狗1",
                credentials = mapOf("token" to "1")
            ),
            activate = true
        )
        prefs.setCurrentPlatform(MusicPlatform.KUGOU)

        // 写入：1 条 null accountKey（旧网易）、1 条其他酷狗账号、10 条当前酷狗账号
        playbackPrefs.recordRecentPlaylist(
            com.lin0721.linmusic.core.player.LocalRecentPlaylist(
                id = 1L, name = "旧网易", playTime = 100L, platform = MusicPlatform.NETEASE, rawId = "1", accountKey = null
            )
        )
        playbackPrefs.recordRecentPlaylist(
            com.lin0721.linmusic.core.player.LocalRecentPlaylist(
                id = 0L, name = "其他酷狗账号歌单", playTime = 200L, platform = MusicPlatform.KUGOU, rawId = "kg_other", accountKey = account2.canonical
            )
        )
        for (i in 1..10) {
            playbackPrefs.recordRecentPlaylist(
                com.lin0721.linmusic.core.player.LocalRecentPlaylist(
                    id = 0L,
                    name = "酷狗记录$i",
                    playTime = 1000L + i,
                    platform = MusicPlatform.KUGOU,
                    rawId = "kg_$i",
                    accountKey = account1.canonical,
                    kind = when (i % 3) {
                        0 -> com.lin0721.linmusic.core.player.PlaySource.Kind.ALBUM
                        1 -> com.lin0721.linmusic.core.player.PlaySource.Kind.PLAYLIST
                        else -> com.lin0721.linmusic.core.player.PlaySource.Kind.RANK
                    }
                )
            )
        }

        val emptyDiscoverRepo = object : com.lin0721.linmusic.core.platform.repository.PlatformDiscoverRepository {
            override fun getDiscoverFeed(platform: MusicPlatform, refresh: Boolean) = flowOf(
                Result.success(UnifiedDiscoverFeed(platform = platform))
            )
            override fun getDailyRecommendSongs(platform: MusicPlatform) = flowOf(Result.success(emptyList<UnifiedDailySong>()))
            override fun getRecommendedPlaylists(platform: MusicPlatform, page: Int, pageSize: Int) =
                flowOf(Result.failure<com.lin0721.linmusic.core.platform.repository.UnifiedDiscoverPlaylistPage>(IllegalStateException()))
            override fun getRankList(platform: MusicPlatform) = flowOf(Result.success(emptyList<UnifiedRank>()))
            override fun getRankTracks(rank: UnifiedRank, page: Int, pageSize: Int) =
                flowOf(Result.failure<UnifiedRankTracksPage>(IllegalStateException()))
            override fun getPersonalFmSongs(platform: MusicPlatform, mode: com.lin0721.linmusic.core.player.PersonalFmMode) =
                flowOf(Result.success(emptyList<UnifiedTrack>()))
            override fun trashFmSong(platform: MusicPlatform, track: UnifiedTrack, playedSeconds: Long) = flowOf(Result.success(Unit))
        }
        val discoverRouter = PlatformDiscoverRouter(netease = emptyDiscoverRepo, kugou = emptyDiscoverRepo, isKugouEnabled = { true })
        val vm = buildHomeViewModel(prefs, settings, playbackPrefs, RecordingPlaybackController(), discoverRouter = discoverRouter)
        scheduler.advanceUntilIdle()

        val quick = vm.sections.value.filterIsInstance<HomeSection.Quick>().single()
        assertEquals(8, quick.items.size)
        assertTrue(quick.items.all { it.platform == MusicPlatform.KUGOU })
        assertTrue(quick.items.none { it.title == "旧网易" || it.title == "其他酷狗账号歌单" })
        // 最新一条（i = 10，10 % 3 == 1 -> PLAYLIST）排在第一位
        assertEquals("酷狗记录10", quick.items.first().title)
        assertEquals(HomeCardKind.PLAYLIST, quick.items.first().kind)
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
