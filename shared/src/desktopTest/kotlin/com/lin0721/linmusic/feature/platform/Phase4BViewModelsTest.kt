package com.lin0721.linmusic.feature.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.lin0721.linmusic.core.auth.FakeAuthRepository
import kotlin.reflect.KClass
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.comment.data.CommentRepository
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.comment.data.CommentsResponse
import com.lin0721.linmusic.core.comment.data.CommentsV2Data
import com.lin0721.linmusic.core.comment.data.FloorCommentsData
import com.lin0721.linmusic.core.download.recognition.DownloadRecognizer
import com.lin0721.linmusic.core.download.recognition.LocalAudioIndexStore
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.OnlineStateProvider
import com.lin0721.linmusic.core.platform.AccountId
import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.ArtistId
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedComment
import com.lin0721.linmusic.core.platform.UnifiedCreator
import com.lin0721.linmusic.core.platform.UnifiedPlaylist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.toDisplayTrack
import com.lin0721.linmusic.core.platform.repository.NeteasePlatformCommentRepository
import com.lin0721.linmusic.core.platform.repository.PlatformAlbumRepository
import com.lin0721.linmusic.core.platform.repository.PlatformAlbumRouter
import com.lin0721.linmusic.core.platform.repository.PlatformArtistRepository
import com.lin0721.linmusic.core.platform.repository.PlatformArtistRouter
import com.lin0721.linmusic.core.platform.repository.PlatformCommentRepository
import com.lin0721.linmusic.core.platform.repository.PlatformCommentRouter
import com.lin0721.linmusic.core.platform.repository.PlatformLibraryRepository
import com.lin0721.linmusic.core.platform.repository.PlatformLibraryRouter
import com.lin0721.linmusic.core.platform.repository.PlatformPlaylistRepository
import com.lin0721.linmusic.core.platform.repository.PlatformPlaylistRouter
import com.lin0721.linmusic.core.platform.repository.PlatformSearchRepository
import com.lin0721.linmusic.core.platform.repository.PlatformSearchRouter
import com.lin0721.linmusic.core.platform.repository.UnifiedAlbumDetail
import com.lin0721.linmusic.core.platform.repository.UnifiedArtistAlbumsPage
import com.lin0721.linmusic.core.platform.repository.UnifiedArtistDetail
import com.lin0721.linmusic.core.platform.repository.UnifiedArtistSongsPage
import com.lin0721.linmusic.core.platform.repository.UnifiedCollectedAlbum
import com.lin0721.linmusic.core.platform.repository.UnifiedCommentsPage
import com.lin0721.linmusic.core.platform.repository.UnifiedHotSearch
import com.lin0721.linmusic.core.platform.repository.UnifiedSearchPageResult
import com.lin0721.linmusic.core.platform.repository.UnifiedSearchResultItem
import com.lin0721.linmusic.core.platform.repository.UnifiedSearchSuggestion
import com.lin0721.linmusic.core.platform.repository.UnifiedSearchType
import com.lin0721.linmusic.core.platform.repository.UnifiedUserPlaylist
import com.lin0721.linmusic.core.platform.repository.UnifiedUserRecordTrack
import com.lin0721.linmusic.core.platform.repository.UnifiedUserSubcount
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.playlistexternal.ExternalSongLikes
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.plugin.ExtensionRegistry
import com.lin0721.linmusic.core.plugin.PluginContent
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.userartist.UserArtistRepository
import com.lin0721.linmusic.feature.artist.data.ArtistRepository
import com.lin0721.linmusic.feature.artist.domain.ArtistAlbumPage
import com.lin0721.linmusic.feature.artist.domain.ArtistSongsPage
import com.lin0721.linmusic.feature.artist.ui.ArtistUiState
import com.lin0721.linmusic.feature.artist.ui.ArtistViewModel
import com.lin0721.linmusic.feature.cloud.data.CloudInfoRequest
import com.lin0721.linmusic.feature.cloud.data.CloudInfoResponse
import com.lin0721.linmusic.feature.cloud.data.CloudPublishRequest
import com.lin0721.linmusic.feature.cloud.data.CloudUploadApi
import com.lin0721.linmusic.feature.cloud.data.CloudUploadCheckRequest
import com.lin0721.linmusic.feature.cloud.data.CloudUploadCheckResponse
import com.lin0721.linmusic.feature.cloud.data.NosTokenRequest
import com.lin0721.linmusic.feature.cloud.data.NosTokenResponse
import com.lin0721.linmusic.feature.cloud.upload.NosUploadClient
import com.lin0721.linmusic.feature.home.data.DailySong
import com.lin0721.linmusic.feature.home.data.HomeRepository
import com.lin0721.linmusic.feature.home.data.PersonalizedData
import com.lin0721.linmusic.feature.home.domain.HomeBlockPage
import com.lin0721.linmusic.feature.home.domain.ToplistInfo
import com.lin0721.linmusic.feature.library.data.AlbumSubItem
import com.lin0721.linmusic.feature.library.data.LibraryPreferences
import com.lin0721.linmusic.feature.library.data.LibraryRepository
import com.lin0721.linmusic.feature.library.data.UserRecordTrack
import com.lin0721.linmusic.feature.library.data.UserSubcountResponse
import com.lin0721.linmusic.feature.library.ui.KUGOU_RECENT_PLAYLIST_ID
import com.lin0721.linmusic.feature.library.ui.LibraryUiState
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import com.lin0721.linmusic.feature.playlist.domain.CreatePlaylistAndAddSongUseCase
import com.lin0721.linmusic.feature.playlist.domain.FakeCreateRepository
import com.lin0721.linmusic.feature.playlist.domain.FakePlaylistRepository
import com.lin0721.linmusic.feature.playlist.domain.FakeSongDownloader
import com.lin0721.linmusic.feature.playlist.domain.FakeUserPlaylistRepository
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.playlist.domain.TrackBatchActionsDelegate
import com.lin0721.linmusic.feature.playlist.domain.UpdatePlaylistCoverUseCase
import com.lin0721.linmusic.feature.playlist.ui.KUGOU_DAILY_RECOMMEND_ID
import com.lin0721.linmusic.feature.playlist.ui.PlaylistUiState
import com.lin0721.linmusic.feature.playlist.ui.PlaylistViewModel
import com.lin0721.linmusic.feature.playlist.ui.kugouRankEntityId
import com.lin0721.linmusic.feature.playlist.ui.parseKugouRankEntityId
import com.lin0721.linmusic.feature.podcast.ui.FakePlaybackController
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import com.lin0721.linmusic.feature.recent.data.RecentRepository
import com.lin0721.linmusic.feature.recent.domain.RecentAlbum
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist
import com.lin0721.linmusic.feature.recent.domain.RecentSong
import com.lin0721.linmusic.feature.search.data.SearchHistoryPreferences
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.HotSearch
import com.lin0721.linmusic.feature.search.domain.PlaylistCategoryPage
import com.lin0721.linmusic.feature.search.domain.PlaylistTag
import com.lin0721.linmusic.feature.search.domain.SearchPageResult
import com.lin0721.linmusic.feature.search.domain.SearchSuggestion
import com.lin0721.linmusic.feature.search.domain.SearchType
import com.lin0721.linmusic.feature.search.ui.DiscoveryUiState
import com.lin0721.linmusic.feature.search.ui.KugouSearchType
import com.lin0721.linmusic.feature.search.ui.SearchResultsUiState
import com.lin0721.linmusic.feature.search.ui.SearchViewModel
import java.io.File
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase4BViewModelsTest {

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
        SongCollectDelegate.clearCache()
    }

    @After
    fun tearDown() {
        vmStore.clear()
        scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        SongCollectDelegate.clearCache()
    }

    private fun kugouTrack(hash: String, name: String = "晴天"): UnifiedTrack = UnifiedTrack(
        id = TrackId.kugou(hash),
        name = name,
        artists = listOf(UnifiedArtist(id = ArtistId.kugou("3520"), name = "周杰伦")),
        artistNames = "周杰伦",
        album = UnifiedAlbum(id = AlbumId.kugou("9001"), name = "叶惠美", coverUrl = "http://img/alb.jpg"),
        durationMs = 269_000L,
        coverUrl = "http://img/track.jpg",
        kugouMeta = KugouTrackMeta(hash = hash, mixSongId = 889900L, albumAudioId = 1122L, albumId = "9001")
    )

    private fun kugouAccount(uid: String = "kg_1001") = StoredAccount(
        uid = 1001L,
        nickname = "酷狗用户",
        platform = MusicPlatform.KUGOU,
        platformUid = uid,
        credentials = mapOf("token" to "tok", "userid" to uid)
    )

    @Test
    fun `SongCollectDelegate quickLike 支持纯酷狗账号通过当前队列项路由酷狗红心且受总开关门控`() = runTest(scheduler) {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        val likeRepo = RecordingSongLikeRepository()
        val track = kugouTrack("HASH_LIKE_1", "七里香")
        val queueItem = QueueItem.fromUnified(track).copy(external = null)
        prefs.upsertPlatformAccount(kugouAccount())
        prefs.setCurrentPlatform(MusicPlatform.KUGOU)

        val delegate = SongCollectDelegate(
            userPlaylistRepository = FakeUserPlaylistRepository(),
            playlistRepository = FakePlaylistRepository(),
            songLikeRepository = likeRepo,
            createPlaylistAndAddSongUseCase = CreatePlaylistAndAddSongUseCase(
                FakeCreateRepository(), FakePlaylistRepository(), PlaylistMutationBus()
            ),
            userPreferences = prefs,
            resourceProvider = testResourceProvider,
            externalStore = externalStore,
            externalSongLikes = ExternalSongLikes(externalStore, prefs) { listOf(queueItem) },
            platformLibraryRouter = PlatformLibraryRouter(StubPlatformLibraryRepo(), StubPlatformLibraryRepo()) {
                settings.kugouPlatformEnabled.first()
            },
            platformPlaylistRouter = PlatformPlaylistRouter(
                StubPlatformPlaylistRepo(), StubPlatformPlaylistRepo(), externalStore
            ) { settings.kugouPlatformEnabled.first() },
            settingsPreferences = settings
        )

        val toasts = mutableListOf<String>()
        delegate.quickLike(queueItem.songId) { toasts += it }

        assertEquals(1, likeRepo.likeTrackCalls.size)
        val call = likeRepo.likeTrackCalls.single()
        assertEquals(TrackId.kugou("HASH_LIKE_1"), call.trackId)
        assertEquals("hash_like_1", call.kugouMeta?.hash?.lowercase())
        assertTrue(toasts.isEmpty())

        settings.saveKugouPlatformEnabled(false)
        delegate.quickLikeUnified(track) { toasts += it }
        assertEquals(1, likeRepo.likeTrackCalls.size)
        assertTrue(toasts.any { it.contains("登录") })
    }

    @Test
    fun `LibraryViewModel 加载酷狗曲库与最近播放，并在总开关关闭时隐藏与拦截建单`() = runTest(scheduler) {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        prefs.upsertPlatformAccount(kugouAccount())
        prefs.setCurrentPlatform(MusicPlatform.KUGOU)

        val kugouLib = FakeKugouLibraryRepo().apply {
            playlists = listOf(
                UnifiedUserPlaylist(
                    id = PlaylistId.kugou("3001"),
                    name = "酷狗红心",
                    trackCount = 12,
                    isLikedPlaylist = true,
                    listId = 3001L
                )
            )
            artists = listOf(UnifiedArtist(id = ArtistId.kugou("3520"), name = "周杰伦"))
            records = listOf(UnifiedUserRecordTrack(kugouTrack("HASH_REC").copy(coverUrl = "https://kg/rec.jpg"), playCount = 5))
        }
        val kugouPlaylistRepo = FakeKugouPlaylistRepo()
        val libraryRouter = PlatformLibraryRouter(StubPlatformLibraryRepo(), kugouLib) {
            settings.kugouPlatformEnabled.first()
        }
        val playlistRouter = PlatformPlaylistRouter(StubPlatformPlaylistRepo(), kugouPlaylistRepo, externalStore) {
            settings.kugouPlatformEnabled.first()
        }

        val vm = trackVm {
            LibraryViewModel(
                syncProfileAfterLoginUseCase = SyncProfileAfterLoginUseCase(prefs, FakeAuthRepository()),
                createRepository = FakeCreateRepository(),
                libraryRepository = StubLibraryRepo(),
                playlistRepository = FakePlaylistRepository(),
                artistRepository = StubArtistRepo(),
                userPlaylistRepository = FakeUserPlaylistRepository(),
                userArtistRepository = object : UserArtistRepository {
                    override fun getFavoriteArtists() = flowOf(Result.success(emptyList<ArtistInfo>()))
                },
                userPreferences = prefs,
                playerManager = FakePlaybackController(),
                libraryPreferences = InMemoryLibraryPreferences(),
                resourceProvider = testResourceProvider,
                playlistMutationBus = PlaylistMutationBus(),
                songDownloadManager = FakeSongDownloader(),
                recentRepository = StubRecentRepo(),
                playbackRepository = StubPlaybackRepo(),
                onlineState = AlwaysOnlineProvider(),
                updatePlaylistCoverUseCase = UpdatePlaylistCoverUseCase(StubCloudUploadApi(), NosUploadClient(), FakePlaylistRepository()),
                externalStore = externalStore,
                platformLibraryRouter = libraryRouter,
                platformPlaylistRouter = playlistRouter,
                settingsPreferences = settings
            )
        }
        advanceUntilIdle()

        val success = vm.uiState.value as LibraryUiState.Success
        assertTrue(success.allItems.any { it.entityId == "kugou:3001" && it.platform == MusicPlatform.KUGOU })
        assertTrue(success.allItems.any { it.entityId == KUGOU_RECENT_PLAYLIST_ID && it.platform == MusicPlatform.KUGOU })
        assertTrue(success.allItems.any { it.entityId == "kugou:3520" && it.platform == MusicPlatform.KUGOU })
        // 酷狗最近播放入口：同平台不带平台名，封面取最近一首听歌记录
        val recentItem = success.allItems.first { it.entityId == KUGOU_RECENT_PLAYLIST_ID }
        assertEquals("最近播放", recentItem.title)
        assertEquals("最近听歌记录", recentItem.subtitle)
        assertEquals("https://kg/rec.jpg", recentItem.coverUrl)
        assertEquals("歌单", success.allItems.first { it.entityId == "kugou:3001" }.subtitle.substringBefore(" · "))
        assertEquals("歌手", success.allItems.first { it.entityId == "kugou:3520" }.subtitle)

        // 切换当前平台时仅保留当前平台数据
        prefs.setCurrentPlatform(MusicPlatform.KUGOU)
        advanceUntilIdle()
        assertEquals(MusicPlatform.KUGOU, vm.browsePlatform.value)
        val kugouOnly = vm.uiState.value as LibraryUiState.Success
        assertTrue(kugouOnly.allItems.isNotEmpty())
        assertTrue(kugouOnly.allItems.all { it.platform == MusicPlatform.KUGOU })

        prefs.setCurrentPlatform(MusicPlatform.NETEASE)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is LibraryUiState.Loading)

        // 验证酷狗榜单 entityId 编解码往返一致
        val sampleRank = com.lin0721.linmusic.core.platform.repository.UnifiedRank(
            platform = MusicPlatform.KUGOU,
            id = "kugou:rank:6666",
            rawRankId = 6666L,
            rankCid = 88L,
            name = "酷狗飙升榜:TOP500",
            coverUrl = ""
        )
        val encodedRank = kugouRankEntityId(sampleRank)
        val decodedRank = parseKugouRankEntityId(encodedRank)
        assertEquals(6666L, decodedRank?.rawRankId)
        assertEquals(88L, decodedRank?.rankCid)
        assertEquals("酷狗飙升榜:TOP500", decodedRank?.name)
        assertEquals("kugou_daily", KUGOU_DAILY_RECOMMEND_ID)

        var createdId: String? = null
        vm.createPlaylistForPlatform(MusicPlatform.KUGOU, "新建酷狗单") { id, _ -> createdId = id }
        advanceUntilIdle()
        assertEquals("kugou:4002", createdId)
        assertEquals(listOf("新建酷狗单"), kugouPlaylistRepo.createdNames)

        settings.saveKugouPlatformEnabled(false)
        advanceUntilIdle()
        val afterClose = vm.uiState.value as? LibraryUiState.Success
        if (afterClose != null) {
            assertFalse(afterClose.allItems.any { it.platform == MusicPlatform.KUGOU })
        }

        val toasts = mutableListOf<String>()
        val collectJob = launch { vm.toastEvent.collect { toasts += it } }
        vm.createPlaylistForPlatform(MusicPlatform.KUGOU, "被门控单")
        advanceUntilIdle()
        collectJob.cancel()
        assertEquals(1, kugouPlaylistRepo.createdNames.size)
        assertTrue(toasts.any { it.contains("未启用") })
    }

    @Test
    fun `PlaylistViewModel 按 entityId 路由酷狗歌单与专辑并混排跨平台曲目与队列元数据`() = runTest(scheduler) {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        val playlistId = PlaylistId.kugou("3001")
        val neteaseExtra = UnifiedTrack(
            id = TrackId.netease(7788L),
            name = "跨平台网易单曲",
            artistNames = "陈奕迅",
            coverUrl = "http://img/ne.jpg"
        )
        externalStore.add(playlistId, listOf(neteaseExtra))

        val kugouPlaylistRepo = FakeKugouPlaylistRepo().apply {
            detail = UnifiedPlaylist(
                id = playlistId,
                name = "酷狗云歌单",
                tracks = listOf(kugouTrack("HASH_PL_1", "夜曲")),
                trackCount = 1
            )
        }
        val kugouAlbumRepo = FakeKugouAlbumRepo().apply {
            albumDetail = UnifiedAlbumDetail(
                id = AlbumId.kugou("9001"),
                name = "叶惠美",
                tracks = listOf(kugouTrack("HASH_ALB_1", "以父之名")),
                trackCount = 1
            )
        }
        val vm = buildPlaylistViewModel(prefs, settings, externalStore, kugouPlaylistRepo, kugouAlbumRepo)
        advanceUntilIdle()

        vm.loadEntity("kugou:3001", isAlbum = false)
        advanceUntilIdle()

        val plState = vm.uiState.value as PlaylistUiState.Success
        assertEquals(MusicPlatform.KUGOU, plState.platform)
        assertEquals("kugou:3001", plState.entityId)
        val trackNames = plState.playlist.tracks.map { it.name }
        assertTrue(trackNames.contains("夜曲"))
        assertTrue(trackNames.contains("跨平台网易单曲"))

        val controller = vm.playerManager as FakePlaybackController
        val targetDisplayTrack = plState.playlist.tracks.first { it.name == "夜曲" }
        vm.playSongInList(targetDisplayTrack, plState.playlist.tracks)
        val queueCall = controller.queueCalls.last()
        val queued = queueCall.items[queueCall.startIndex]
        assertEquals(TrackId.kugou("HASH_PL_1"), queued.trackId)
        assertEquals("HASH_PL_1", queued.kugouMeta?.hash)
        assertEquals(889900L, queued.kugouMeta?.mixSongId)
        assertEquals(7788L, queueCall.items.first { it.title == "跨平台网易单曲" }.songId)

        vm.loadEntity("kugou:9001", isAlbum = true)
        advanceUntilIdle()
        val albState = vm.uiState.value as PlaylistUiState.Success
        assertEquals(MusicPlatform.KUGOU, albState.platform)
        assertEquals("叶惠美", albState.playlist.name)
        assertEquals(1, kugouAlbumRepo.detailCalls)

        settings.saveKugouPlatformEnabled(false)
        val prevDetailCalls = kugouPlaylistRepo.detailCalls
        vm.loadEntity("kugou:3001", isAlbum = false)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is PlaylistUiState.Error)
        assertEquals(prevDetailCalls, kugouPlaylistRepo.detailCalls)
    }

    @Test
    fun `SearchViewModel 支持酷狗热搜联想搜索结果路由及播放队列元数据，并受总开关门控`() = runTest(scheduler) {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        val likeRepo = RecordingSongLikeRepository()
        val playlistRepo = FakePlaylistRepository()
        val player = FakePlaybackController()
        val kugouSearchRepo = FakeKugouSearchRepo(kugouTrack("HASH_SEARCH_1", "稻香"))
        val searchRouter = PlatformSearchRouter(StubPlatformSearchRepo(), kugouSearchRepo) {
            settings.kugouPlatformEnabled.first()
        }

        val vm = trackVm {
            SearchViewModel(
                repository = StubNeteaseSearchRepo(),
                historyPreferences = SearchHistoryPreferences(InMemoryPreferencesStore()),
                playerManager = player,
                userPreferences = prefs,
                resourceProvider = testResourceProvider,
                songCollectDelegate = SongCollectDelegate(
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
                ),
                loadLikedSongIdsUseCase = LoadLikedSongIdsUseCase(prefs, likeRepo),
                songLikeRepository = likeRepo,
                syncProfileAfterLoginUseCase = SyncProfileAfterLoginUseCase(prefs, FakeAuthRepository()),
                settingsPreferences = settings,
                platformSearchRouter = searchRouter
            )
        }
        advanceUntilIdle()

        vm.selectPlatform(MusicPlatform.KUGOU)
        advanceUntilIdle()
        val discovery = vm.kugouDiscoveryState.value as DiscoveryUiState.Success
        assertEquals(listOf("酷狗热歌"), discovery.hotSearches.map { it.keyword })

        vm.activateSearch()
        vm.updateQuery("稻")
        advanceUntilIdle()
        assertEquals(listOf("稻香"), vm.inputState.value.currentSuggestions.map { it.text })

        vm.searchWithKeyword("稻香")
        advanceUntilIdle()
        val songResults = vm.kugouResultsByType.getValue(KugouSearchType.SONG).value as SearchResultsUiState.Success
        val track = (songResults.items.single() as com.lin0721.linmusic.feature.search.domain.SearchResultItem.SongItem).track
        vm.playSong(track)
        val enqueued = player.queueCalls.last().items.single()
        assertEquals(TrackId.kugou("HASH_SEARCH_1"), enqueued.trackId)
        assertEquals("HASH_SEARCH_1", enqueued.kugouMeta?.hash)

        settings.saveKugouPlatformEnabled(false)
        advanceUntilIdle()
        assertEquals(MusicPlatform.NETEASE, vm.selectedPlatform.value)
        assertTrue(vm.kugouResultsByType.getValue(KugouSearchType.SONG).value is SearchResultsUiState.Idle)
        val callsBefore = kugouSearchRepo.searchCalls
        vm.selectPlatform(MusicPlatform.KUGOU)
        advanceUntilIdle()
        assertEquals(MusicPlatform.NETEASE, vm.selectedPlatform.value)
        assertEquals(callsBefore, kugouSearchRepo.searchCalls)
    }

    @Test
    fun `ArtistViewModel 加载酷狗歌手详情与关注路由，并受总开关门控`() = runTest(scheduler) {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        val likeRepo = RecordingSongLikeRepository()
        val playlistRepo = FakePlaylistRepository()
        prefs.upsertPlatformAccount(kugouAccount())
        prefs.setCurrentPlatform(MusicPlatform.KUGOU)

        val kugouArtistRepo = FakeKugouArtistRepo(kugouTrack("HASH_ART_1", "以父之名"))
        val artistRouter = PlatformArtistRouter(StubPlatformArtistRepo(), kugouArtistRepo) {
            settings.kugouPlatformEnabled.first()
        }
        val vm = trackVm {
            ArtistViewModel(
                songCollectDelegate = SongCollectDelegate(
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
                ),
                syncProfileAfterLoginUseCase = SyncProfileAfterLoginUseCase(prefs, FakeAuthRepository()),
                loadLikedSongIdsUseCase = LoadLikedSongIdsUseCase(prefs, likeRepo),
                artistRepository = StubArtistRepo(),
                songLikeRepository = likeRepo,
                playerManager = FakePlaybackController(),
                userPreferences = prefs,
                resourceProvider = testResourceProvider,
                searchRepository = StubNeteaseSearchRepo(),
                trackBatchActions = TrackBatchActionsDelegate(
                    playlistRepository = playlistRepo,
                    playerManager = FakePlaybackController(),
                    songDownloadManager = FakeSongDownloader(),
                    songLikeRepository = likeRepo,
                    createPlaylistAndAddSongUseCase = CreatePlaylistAndAddSongUseCase(
                        FakeCreateRepository(), playlistRepo, PlaylistMutationBus()
                    ),
                    userPlaylistRepository = FakeUserPlaylistRepository(),
                    userPreferences = prefs,
                    resourceProvider = testResourceProvider,
                    externalStore = externalStore,
                    settingsPreferences = settings
                ),
                platformArtistRouter = artistRouter,
                settingsPreferences = settings
            )
        }

        vm.loadArtistData("kugou:3520")
        advanceUntilIdle()

        val success = vm.uiState.value as ArtistUiState.Success
        assertEquals(MusicPlatform.KUGOU, success.platform)
        assertEquals("周杰伦", success.artist.name)
        assertFalse(success.isFollowed)
        assertEquals(1, success.topSongs.size)

        vm.toggleFollow(success.artist.id)
        advanceUntilIdle()
        val afterFollow = vm.uiState.value as ArtistUiState.Success
        assertTrue(afterFollow.isFollowed)
        assertEquals(listOf(ArtistId.kugou("3520") to true), kugouArtistRepo.subscribeCalls)

        settings.saveKugouPlatformEnabled(false)
        val detailCallsBefore = kugouArtistRepo.detailCalls
        vm.loadArtistData("kugou:3520")
        advanceUntilIdle()
        assertTrue(vm.uiState.value is ArtistUiState.Error)
        assertEquals(detailCallsBefore, kugouArtistRepo.detailCalls)
    }

    @Test
    fun `PlaylistViewModel 酷狗歌单加歌回填 fileid、混排重排保留外链并尊重 addtime 时序`() = runTest(scheduler) {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        val pid = PlaylistId.kugou("3001")

        val baseTrack1 = kugouTrack("HASH_BASE_1", "基础歌1").copy(
            kugouMeta = KugouTrackMeta(hash = "hash_base_1", playlistFileId = 101L, playlistAddTimeMs = 2_000_000_000_000L)
        )
        val baseTrack2 = kugouTrack("HASH_NEW_2", "新加酷狗歌").copy(
            kugouMeta = KugouTrackMeta(hash = "hash_new_2", playlistFileId = 202L, playlistAddTimeMs = 1_500_000_000_000L)
        )
        // 外链添加时间介于 baseTrack1 (2e12) 与 baseTrack2 (1.5e12) 之间，验证不会全部强制置顶
        val neteaseExtra = UnifiedTrack(
            id = TrackId.netease(9988L),
            name = "异源网易插曲",
            artistNames = "歌手N"
        )
        externalStore.add(pid, listOf(neteaseExtra), now = 1_800_000_000_000L)

        var currentTracks = listOf(baseTrack1)
        var currentVer = 10L
        val kugouPlaylistRepo = FakeKugouPlaylistRepo().apply {
            detailProvider = {
                UnifiedPlaylist(
                    id = pid,
                    name = "酷狗混排单",
                    tracks = currentTracks,
                    trackCount = currentTracks.size,
                    listVer = currentVer
                )
            }
        }
        val vm = buildPlaylistViewModel(prefs, settings, externalStore, kugouPlaylistRepo, FakeKugouAlbumRepo())
        advanceUntilIdle()

        vm.loadEntity("kugou:3001", isAlbum = false)
        advanceUntilIdle()

        val initial = vm.uiState.value as PlaylistUiState.Success
        assertEquals(listOf("基础歌1", "异源网易插曲"), initial.playlist.tracks.map { it.name })
        assertEquals(10L, initial.listVer)

        // 加歌后静默刷新详情回填 playlistFileId 与 listVer
        currentTracks = listOf(baseTrack1, baseTrack2)
        currentVer = 11L
        val newInputWithoutFileId = kugouTrack("HASH_NEW_2", "新加酷狗歌").copy(
            kugouMeta = KugouTrackMeta(hash = "hash_new_2", playlistFileId = null)
        )
        var addOk = false
        vm.addTrackToEntityPlaylist(
            entityId = "kugou:3001",
            track = newInputWithoutFileId.toDisplayTrack(),
            onComplete = { addOk = it },
            unifiedTrack = newInputWithoutFileId
        )
        advanceUntilIdle()
        assertTrue(addOk)

        val afterAdd = vm.uiState.value as PlaylistUiState.Success
        assertEquals(listOf("基础歌1", "异源网易插曲", "新加酷狗歌"), afterAdd.playlist.tracks.map { it.name })
        assertEquals(11L, afterAdd.listVer)
        assertEquals(3, afterAdd.playlist.trackCount)

        // 重排酷狗曲目顺序时携带回填的 fileid=202/101，且保留混排外链不被覆盖丢失
        val reorderedDisplay = listOf(
            afterAdd.playlist.tracks.first { it.name == "新加酷狗歌" },
            afterAdd.playlist.tracks.first { it.name == "基础歌1" }
        )
        var sortOk = false
        vm.updateTrackOrderByEntity("kugou:3001", reorderedDisplay) { sortOk = it }
        advanceUntilIdle()
        assertTrue(sortOk)
        val (submittedTracks, submittedVer) = kugouPlaylistRepo.sortCalls.single()
        assertEquals(11L, submittedVer)
        assertEquals(listOf(202L, 101L), submittedTracks.map { it.kugouMeta?.playlistFileId })

        val afterSort = vm.uiState.value as PlaylistUiState.Success
        assertTrue(afterSort.playlist.tracks.any { it.name == "异源网易插曲" })
        assertEquals(3, afterSort.playlist.trackCount)
    }

    @Test
    fun `PlaylistViewModel 酷狗歌单评论发布使用 KUGOU 身份且移除外链曲目不误扣远端数量`() = runTest(scheduler) {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        prefs.upsertPlatformAccount(kugouAccount("889900"))

        val pid = PlaylistId.kugou("3001")
        val neteaseExtra = UnifiedTrack(id = TrackId.netease(7766L), name = "混排网易歌")
        externalStore.add(pid, listOf(neteaseExtra), now = 1_900_000_000_000L)

        val kg1 = kugouTrack("HASH_R1", "酷狗1").copy(kugouMeta = KugouTrackMeta(hash = "hash_r1", playlistAddTimeMs = 1_000_000_000_000L))
        val kg2 = kugouTrack("HASH_R2", "酷狗2").copy(kugouMeta = KugouTrackMeta(hash = "hash_r2", playlistAddTimeMs = 900_000_000_000L))
        val kugouPlaylistRepo = FakeKugouPlaylistRepo().apply {
            detail = UnifiedPlaylist(id = pid, name = "酷狗歌单", tracks = listOf(kg1, kg2), trackCount = 2)
        }
        val recordingCommentRepo = object : PlatformCommentRepository {
            override fun getComments(target: com.lin0721.linmusic.core.platform.repository.CommentTarget, pageNo: Int, pageSize: Int, cursor: String, sortType: CommentSortType) =
                flowOf(Result.success(UnifiedCommentsPage(emptyList())))
            override fun getFloorComments(target: com.lin0721.linmusic.core.platform.repository.CommentTarget, parentCommentId: String, cursorOrTime: Long, pageNo: Int, limit: Int, kugouSpecialId: String?) =
                flowOf(Result.success(com.lin0721.linmusic.core.platform.repository.UnifiedFloorCommentsPage(emptyList())))
            override fun addComment(target: com.lin0721.linmusic.core.platform.repository.CommentTarget, content: String) =
                flowOf(Result.success<UnifiedComment?>(null))
            override fun replyComment(target: com.lin0721.linmusic.core.platform.repository.CommentTarget, commentId: String, content: String, pid: String, replyUserName: String?, replyContent: String?, kugouSpecialId: String?) =
                flowOf(Result.success<UnifiedComment?>(null))
            override fun deleteComment(target: com.lin0721.linmusic.core.platform.repository.CommentTarget, commentId: String, tid: String?, kugouSpecialId: String?) =
                flowOf(Result.success(Unit))
            override fun likeComment(target: com.lin0721.linmusic.core.platform.repository.CommentTarget, commentId: String, like: Boolean) =
                flowOf(Result.success(Unit))
        }

        val vm = buildPlaylistViewModel(prefs, settings, externalStore, kugouPlaylistRepo, FakeKugouAlbumRepo(), recordingCommentRepo)
        vm.loadEntity("kugou:3001", isAlbum = false)
        advanceUntilIdle()

        // 评论提交：乐观插入的 CommentItem.user.platform 必须是 KUGOU
        vm.loadEntityComments()
        advanceUntilIdle()
        vm.submitComment("酷狗歌单评论测试")
        advanceUntilIdle()
        val cState = vm.commentsState.value as com.lin0721.linmusic.core.comment.ui.CommentsState.Success
        assertEquals(MusicPlatform.KUGOU, cState.comments.first().user.platform)

        // 批量移除混排外链曲目后，kugouRemoteTrackCount 保持为 2，不被误减为 1
        val stateBeforeRemove = vm.uiState.value as PlaylistUiState.Success
        assertEquals(3, stateBeforeRemove.playlist.trackCount)
        vm.enterSelection(neteaseExtra.toDisplayTrack().id)
        vm.batchRemove()
        advanceUntilIdle()

        val stateAfterRemove = vm.uiState.value as PlaylistUiState.Success
        assertEquals(2, stateAfterRemove.playlist.tracks.size)
        assertEquals(2, stateAfterRemove.playlist.trackCount)
    }

    @Test
    fun `PlaylistViewModel 与 ArtistViewModel 在酷狗开关关闭和切号时实时联动清理或刷新`() = runTest(scheduler) {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val settings = SettingsPreferences(InMemoryPreferencesStore())
        val externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        prefs.upsertPlatformAccount(kugouAccount("1001"), activate = true)

        val pid = PlaylistId.kugou("3001")
        val kugouPlaylistRepo = FakeKugouPlaylistRepo().apply {
            detail = UnifiedPlaylist(id = pid, name = "酷狗云歌单", tracks = listOf(kugouTrack("HASH_L1", "七里香")), trackCount = 1)
        }
        val pVm = buildPlaylistViewModel(prefs, settings, externalStore, kugouPlaylistRepo, FakeKugouAlbumRepo())
        pVm.loadEntity("kugou:3001", isAlbum = false)
        advanceUntilIdle()
        assertTrue(pVm.uiState.value is PlaylistUiState.Success)
        val callsAfterLoad = kugouPlaylistRepo.detailCalls

        // 切换酷狗账号 -> 自动刷新当前正在浏览的酷狗歌单
        prefs.upsertPlatformAccount(kugouAccount("2002"), activate = true)
        advanceUntilIdle()
        assertEquals(callsAfterLoad + 1, kugouPlaylistRepo.detailCalls)

        // 关闭酷狗总开关 -> 正在浏览酷狗歌单时立即进入关闭错误态
        settings.saveKugouPlatformEnabled(false)
        advanceUntilIdle()
        val pError = pVm.uiState.value as PlaylistUiState.Error
        assertTrue(pError.message.contains("关闭"))

        // 验证 ArtistViewModel 切号更新关注状态、关开关清空并报错
        settings.saveKugouPlatformEnabled(true)
        val artistPrefs = UserPreferences(InMemoryPreferencesStore())
        val artistSettings = SettingsPreferences(InMemoryPreferencesStore())
        artistPrefs.upsertPlatformAccount(kugouAccount("1001"), activate = true)
        val likeRepo = RecordingSongLikeRepository()
        val playlistRepo = FakePlaylistRepository()
        val kugouArtistRepo = FakeKugouArtistRepo(kugouTrack("HASH_A1", "夜曲"))
        val artistRouter = PlatformArtistRouter(StubPlatformArtistRepo(), kugouArtistRepo) {
            artistSettings.kugouPlatformEnabled.first()
        }
        val aVm = trackVm {
            ArtistViewModel(
                songCollectDelegate = SongCollectDelegate(
                    userPlaylistRepository = FakeUserPlaylistRepository(),
                    playlistRepository = playlistRepo,
                    songLikeRepository = likeRepo,
                    createPlaylistAndAddSongUseCase = CreatePlaylistAndAddSongUseCase(
                        FakeCreateRepository(), playlistRepo, PlaylistMutationBus()
                    ),
                    userPreferences = artistPrefs,
                    resourceProvider = testResourceProvider,
                    externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore()),
                    externalSongLikes = ExternalSongLikes(PlaylistExternalTrackStore(InMemoryPreferencesStore()), artistPrefs) { emptyList() },
                    settingsPreferences = artistSettings
                ),
                syncProfileAfterLoginUseCase = SyncProfileAfterLoginUseCase(artistPrefs, FakeAuthRepository()),
                loadLikedSongIdsUseCase = LoadLikedSongIdsUseCase(artistPrefs, likeRepo),
                artistRepository = StubArtistRepo(),
                songLikeRepository = likeRepo,
                playerManager = FakePlaybackController(),
                userPreferences = artistPrefs,
                resourceProvider = testResourceProvider,
                searchRepository = StubNeteaseSearchRepo(),
                trackBatchActions = TrackBatchActionsDelegate(
                    playlistRepository = playlistRepo,
                    playerManager = FakePlaybackController(),
                    songDownloadManager = FakeSongDownloader(),
                    songLikeRepository = likeRepo,
                    createPlaylistAndAddSongUseCase = CreatePlaylistAndAddSongUseCase(
                        FakeCreateRepository(), playlistRepo, PlaylistMutationBus()
                    ),
                    userPlaylistRepository = FakeUserPlaylistRepository(),
                    userPreferences = artistPrefs,
                    resourceProvider = testResourceProvider,
                    externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore()),
                    settingsPreferences = artistSettings
                ),
                platformArtistRouter = artistRouter,
                settingsPreferences = artistSettings
            )
        }
        aVm.loadArtistData("kugou:3520")
        advanceUntilIdle()
        assertFalse((aVm.uiState.value as ArtistUiState.Success).isFollowed)
        assertTrue(aVm.unifiedTracks.value.isNotEmpty())

        kugouArtistRepo.followedResult = true
        artistPrefs.upsertPlatformAccount(kugouAccount("3003"), activate = true)
        advanceUntilIdle()
        assertTrue((aVm.uiState.value as ArtistUiState.Success).isFollowed)

        artistSettings.saveKugouPlatformEnabled(false)
        advanceUntilIdle()
        assertTrue(aVm.unifiedTracks.value.isEmpty())
        assertTrue((aVm.uiState.value as ArtistUiState.Error).message.contains("关闭"))
    }

    private fun buildPlaylistViewModel(
        prefs: UserPreferences,
        settings: SettingsPreferences,
        externalStore: PlaylistExternalTrackStore,
        kugouPlaylistRepo: PlatformPlaylistRepository,
        kugouAlbumRepo: PlatformAlbumRepository,
        kugouCommentRepo: PlatformCommentRepository = StubPlatformCommentRepo()
    ): PlaylistViewModel {
        val likeRepo = RecordingSongLikeRepository()
        val playlistRepo = FakePlaylistRepository()
        val player = FakePlaybackController()
        val createRepo = FakeCreateRepository()
        val createAndAdd = CreatePlaylistAndAddSongUseCase(createRepo, playlistRepo, PlaylistMutationBus())
        val playlistRouter = PlatformPlaylistRouter(StubPlatformPlaylistRepo(), kugouPlaylistRepo, externalStore) {
            settings.kugouPlatformEnabled.first()
        }
        val albumRouter = PlatformAlbumRouter(StubPlatformAlbumRepo(), kugouAlbumRepo) {
            settings.kugouPlatformEnabled.first()
        }
        val commentRouter = PlatformCommentRouter(
            netease = NeteasePlatformCommentRepository(StubCommentRepo()),
            kugou = kugouCommentRepo,
            qq = StubPlatformCommentRepo(),
            isKugouEnabled = { settings.kugouPlatformEnabled.first() }
        )
        val libraryRouter = PlatformLibraryRouter(StubPlatformLibraryRepo(), FakeKugouLibraryRepo()) {
            settings.kugouPlatformEnabled.first()
        }
        val recognizer = DownloadRecognizer(
            indexStore = LocalAudioIndexStore(File(System.getProperty("java.io.tmpdir"), "melodia-test-idx.json")),
            records = { emptyList() },
            addRecords = {},
            isAlive = { false }
        )
        return trackVm {
            PlaylistViewModel(
                songCollectDelegate = SongCollectDelegate(
                    userPlaylistRepository = FakeUserPlaylistRepository(),
                    playlistRepository = playlistRepo,
                    songLikeRepository = likeRepo,
                    createPlaylistAndAddSongUseCase = createAndAdd,
                    userPreferences = prefs,
                    resourceProvider = testResourceProvider,
                    externalStore = externalStore,
                    externalSongLikes = ExternalSongLikes(externalStore, prefs) { emptyList() },
                    platformLibraryRouter = libraryRouter,
                    platformPlaylistRouter = playlistRouter,
                    settingsPreferences = settings
                ),
                syncProfileAfterLoginUseCase = SyncProfileAfterLoginUseCase(prefs, FakeAuthRepository()),
                loadLikedSongIdsUseCase = LoadLikedSongIdsUseCase(prefs, likeRepo),
                homeRepository = StubHomeRepo(),
                libraryRepository = StubLibraryRepo(),
                commentRepository = StubCommentRepo(),
                playlistRepository = playlistRepo,
                songLikeRepository = likeRepo,
                playbackRepository = StubPlaybackRepo(),
                createPlaylistAndAddSongUseCase = createAndAdd,
                updatePlaylistCoverUseCase = UpdatePlaylistCoverUseCase(StubCloudUploadApi(), NosUploadClient(), playlistRepo),
                playerManager = player,
                userPreferences = prefs,
                resourceProvider = testResourceProvider,
                playlistMutationBus = PlaylistMutationBus(),
                searchRepository = StubNeteaseSearchRepo(),
                songDownloadManager = FakeSongDownloader(),
                offlinePlayability = OfflinePlayabilityHelper.alwaysOnline(),
                trackBatchActions = TrackBatchActionsDelegate(
                    playlistRepository = playlistRepo,
                    playerManager = player,
                    songDownloadManager = FakeSongDownloader(),
                    songLikeRepository = likeRepo,
                    createPlaylistAndAddSongUseCase = createAndAdd,
                    userPlaylistRepository = FakeUserPlaylistRepository(),
                    userPreferences = prefs,
                    resourceProvider = testResourceProvider,
                    externalStore = externalStore,
                    platformPlaylistRouter = playlistRouter,
                    platformLibraryRouter = libraryRouter,
                    settingsPreferences = settings
                ),
                externalStore = externalStore,
                pluginContent = PluginContent(ExtensionRegistry()),
                downloadRecognizer = recognizer,
                platformPlaylistRouter = playlistRouter,
                platformAlbumRouter = albumRouter,
                platformCommentRouter = commentRouter,
                platformLibraryRouter = libraryRouter,
                settingsPreferences = settings
            )
        }
    }
}

private object OfflinePlayabilityHelper {
    fun alwaysOnline() = com.lin0721.linmusic.core.offline.OfflinePlayability(
        onlineState = AlwaysOnlineProvider(),
        index = com.lin0721.linmusic.core.offline.CachedAudioIndex { emptySet() }
    )
}

private class AlwaysOnlineProvider : OnlineStateProvider {
    override val online: StateFlow<Boolean> = MutableStateFlow(true)
}

private class RecordingSongLikeRepository : SongLikeRepository {
    data class LikeTrackCall(
        val trackId: TrackId,
        val like: Boolean,
        val title: String,
        val kugouMeta: KugouTrackMeta?
    )

    private val likedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val likedTracks = MutableStateFlow<Set<TrackId>>(emptySet())
    val likeTrackCalls = mutableListOf<LikeTrackCall>()

    override val likedSongIds: StateFlow<Set<Long>> = likedIds
    override val likedTrackIds: StateFlow<Set<TrackId>> = likedTracks

    override fun getLikedSongIds(uid: Long): Flow<Result<List<Long>>> = flowOf(Result.success(emptyList()))
    override fun likeSong(songId: Long, like: Boolean): Flow<Result<Unit>> = flowOf(Result.success(Unit))
    override fun syncLikedSongIds(ids: Set<Long>) {
        likedIds.value = ids
    }

    override fun likeTrack(
        trackId: TrackId,
        like: Boolean,
        title: String,
        kugouMeta: KugouTrackMeta?,
        legacyExternalId: Long?
    ): Flow<Result<Unit>> {
        likeTrackCalls += LikeTrackCall(trackId, like, title, kugouMeta)
        likedTracks.value = if (like) likedTracks.value + trackId else likedTracks.value - trackId
        return flowOf(Result.success(Unit))
    }
}

private class InMemoryLibraryPreferences : LibraryPreferences {
    private val pinned = mutableMapOf<Long, Set<String>>()
    private val orders = mutableMapOf<Long, List<String>>()
    private var sortOrder: String? = null
    private var gridView = false

    override fun pinnedIds(uid: Long): Set<String> = pinned[uid].orEmpty()
    override fun setPinnedIds(uid: Long, ids: Set<String>) { pinned[uid] = ids }
    override fun customPlaylistOrder(uid: Long): List<String> = orders[uid].orEmpty()
    override fun setCustomPlaylistOrder(uid: Long, order: List<String>) { orders[uid] = order }
    override fun sortOrderName(): String? = sortOrder
    override fun setSortOrderName(name: String) { sortOrder = name }
    override fun isGridView(): Boolean = gridView
    override fun setGridView(isGrid: Boolean) { gridView = isGrid }
}

private class FakeKugouLibraryRepo : PlatformLibraryRepository {
    var playlists = emptyList<UnifiedUserPlaylist>()
    var artists = emptyList<UnifiedArtist>()
    var records = emptyList<UnifiedUserRecordTrack>()

    override fun getUserPlaylists(accountId: AccountId, limit: Int): Flow<Result<List<UnifiedUserPlaylist>>> =
        flowOf(Result.success(playlists))
    override fun getFavoriteArtists(platform: MusicPlatform): Flow<Result<List<UnifiedArtist>>> =
        flowOf(Result.success(artists))
    override fun getCollectedAlbums(platform: MusicPlatform, limit: Int): Flow<Result<List<UnifiedCollectedAlbum>>> =
        flowOf(Result.success(emptyList()))
    override fun getUserRecord(accountId: AccountId, type: Int): Flow<Result<List<UnifiedUserRecordTrack>>> =
        flowOf(Result.success(records))
    override fun getUserSubcount(platform: MusicPlatform): Flow<Result<UnifiedUserSubcount>> =
        flowOf(Result.success(UnifiedUserSubcount()))
    override fun updatePlaylistOrder(platform: MusicPlatform, playlistIds: List<PlaylistId>): Flow<Result<Unit>> =
        flowOf(Result.success(Unit))
    override fun setPlaylistTop(playlistId: PlaylistId, top: Boolean): Flow<Result<Unit>> =
        flowOf(Result.success(Unit))
}

private class StubPlatformLibraryRepo : PlatformLibraryRepository {
    override fun getUserPlaylists(accountId: AccountId, limit: Int) = flowOf(Result.success(emptyList<UnifiedUserPlaylist>()))
    override fun getFavoriteArtists(platform: MusicPlatform) = flowOf(Result.success(emptyList<UnifiedArtist>()))
    override fun getCollectedAlbums(platform: MusicPlatform, limit: Int) = flowOf(Result.success(emptyList<UnifiedCollectedAlbum>()))
    override fun getUserRecord(accountId: AccountId, type: Int) = flowOf(Result.success(emptyList<UnifiedUserRecordTrack>()))
    override fun getUserSubcount(platform: MusicPlatform) = flowOf(Result.success(UnifiedUserSubcount()))
    override fun updatePlaylistOrder(platform: MusicPlatform, playlistIds: List<PlaylistId>) = flowOf(Result.success(Unit))
    override fun setPlaylistTop(playlistId: PlaylistId, top: Boolean) = flowOf(Result.success(Unit))
}

private class FakeKugouPlaylistRepo : PlatformPlaylistRepository {
    var detail = UnifiedPlaylist(id = PlaylistId.kugou("3001"), name = "酷狗歌单")
    var detailProvider: (() -> UnifiedPlaylist)? = null
    var detailCalls = 0
    val createdNames = mutableListOf<String>()
    val addedCalls = mutableListOf<List<UnifiedTrack>>()
    val sortCalls = mutableListOf<Pair<List<UnifiedTrack>, Long>>()

    override fun getPlaylistDetail(playlistId: PlaylistId): Flow<Result<UnifiedPlaylist>> {
        detailCalls++
        return flowOf(Result.success(detailProvider?.invoke() ?: detail))
    }
    override fun loadMoreTracks(trackIds: List<TrackId>) = flowOf(Result.success(emptyList<UnifiedTrack>()))
    override fun loadAllTracks(detail: UnifiedPlaylist) = flowOf(Result.success(detail.tracks))
    override fun createPlaylist(platform: MusicPlatform, name: String, isPrivate: Boolean): Flow<Result<UnifiedPlaylist>> {
        createdNames += name
        return flowOf(Result.success(UnifiedPlaylist(id = PlaylistId.kugou("4002"), name = name)))
    }
    override fun subscribePlaylist(playlistId: PlaylistId, subscribe: Boolean) = flowOf(Result.success(Unit))
    override fun addTracksToPlaylist(playlistId: PlaylistId, tracks: List<UnifiedTrack>): Flow<Result<Unit>> {
        addedCalls += tracks
        return flowOf(Result.success(Unit))
    }
    override fun removeTracksFromPlaylist(playlistId: PlaylistId, tracks: List<UnifiedTrack>) = flowOf(Result.success(Unit))
    override fun renamePlaylist(playlistId: PlaylistId, name: String) = flowOf(Result.success(Unit))
    override fun updateDescription(playlistId: PlaylistId, description: String) = flowOf(Result.success(Unit))
    override fun deletePlaylist(playlistId: PlaylistId) = flowOf(Result.success(Unit))
    override fun updatePlaylistCover(playlistId: PlaylistId, imageBytes: ByteArray) = flowOf(Result.success(""))
    override fun sortPlaylistTracks(playlistId: PlaylistId, orderedTracks: List<UnifiedTrack>, listVer: Long): Flow<Result<Unit>> {
        sortCalls += orderedTracks to listVer
        return flowOf(Result.success(Unit))
    }
}

private class StubPlatformPlaylistRepo : PlatformPlaylistRepository {
    override fun getPlaylistDetail(playlistId: PlaylistId) = flowOf(Result.failure<UnifiedPlaylist>(UnsupportedOperationException()))
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

private class FakeKugouAlbumRepo : PlatformAlbumRepository {
    var albumDetail = UnifiedAlbumDetail(id = AlbumId.kugou("9001"), name = "专辑")
    var detailCalls = 0

    override fun getAlbumDetail(albumId: AlbumId): Flow<Result<UnifiedAlbumDetail>> {
        detailCalls++
        return flowOf(Result.success(albumDetail))
    }
    override fun getAlbumSongs(albumId: AlbumId, offset: Int, limit: Int) = flowOf(Result.success(albumDetail.tracks))
    override fun subscribeAlbum(albumId: AlbumId, subscribe: Boolean) = flowOf(Result.success(Unit))
}

private class StubPlatformAlbumRepo : PlatformAlbumRepository {
    override fun getAlbumDetail(albumId: AlbumId) = flowOf(Result.failure<UnifiedAlbumDetail>(UnsupportedOperationException()))
    override fun getAlbumSongs(albumId: AlbumId, offset: Int, limit: Int) = flowOf(Result.success(emptyList<UnifiedTrack>()))
    override fun subscribeAlbum(albumId: AlbumId, subscribe: Boolean) = flowOf(Result.success(Unit))
}

private class FakeKugouSearchRepo(private val sampleTrack: UnifiedTrack) : PlatformSearchRepository {
    var searchCalls = 0

    override fun search(
        platform: MusicPlatform,
        keyword: String,
        type: UnifiedSearchType,
        offset: Int,
        limit: Int
    ): Flow<Result<UnifiedSearchPageResult>> {
        searchCalls++
        return flowOf(
            Result.success(
                UnifiedSearchPageResult(
                    platform = MusicPlatform.KUGOU,
                    items = listOf(UnifiedSearchResultItem.SongItem(sampleTrack)),
                    totalCount = 1,
                    hasMore = false,
                    rawFetchedCount = 1
                )
            )
        )
    }

    override fun getHotSearches(platform: MusicPlatform): Flow<Result<List<UnifiedHotSearch>>> =
        flowOf(Result.success(listOf(UnifiedHotSearch(MusicPlatform.KUGOU, "酷狗热歌", score = 100))))

    override fun getSuggestions(platform: MusicPlatform, keyword: String): Flow<Result<List<UnifiedSearchSuggestion>>> =
        flowOf(Result.success(listOf(UnifiedSearchSuggestion.Keyword(MusicPlatform.KUGOU, "稻香"))))

    override fun getDefaultSearchKeyword(platform: MusicPlatform): Flow<Result<String>> =
        flowOf(Result.success("酷狗热歌"))
}

private class StubPlatformSearchRepo : PlatformSearchRepository {
    override fun search(platform: MusicPlatform, keyword: String, type: UnifiedSearchType, offset: Int, limit: Int) =
        flowOf(Result.success(UnifiedSearchPageResult(platform, emptyList(), 0, false, 0)))
    override fun getHotSearches(platform: MusicPlatform) = flowOf(Result.success(emptyList<UnifiedHotSearch>()))
    override fun getSuggestions(platform: MusicPlatform, keyword: String) = flowOf(Result.success(emptyList<UnifiedSearchSuggestion>()))
    override fun getDefaultSearchKeyword(platform: MusicPlatform) = flowOf(Result.success(""))
}

private class FakeKugouArtistRepo(private val topTrack: UnifiedTrack) : PlatformArtistRepository {
    var detailCalls = 0
    var followedResult = false
    val subscribeCalls = mutableListOf<Pair<ArtistId, Boolean>>()

    override fun getArtistDetail(artistId: ArtistId): Flow<Result<UnifiedArtistDetail>> {
        detailCalls++
        return flowOf(
            Result.success(
                UnifiedArtistDetail(
                    id = artistId,
                    name = "周杰伦",
                    avatarUrl = "http://img/jay.jpg",
                    albumSize = 15,
                    musicSize = 200
                )
            )
        )
    }

    override fun getArtistTopSongs(artistId: ArtistId): Flow<Result<List<UnifiedTrack>>> =
        flowOf(Result.success(listOf(topTrack)))

    override fun getArtistAllSongs(artistId: ArtistId, offset: Int, limit: Int, order: String): Flow<Result<UnifiedArtistSongsPage>> =
        flowOf(Result.success(UnifiedArtistSongsPage(listOf(topTrack), hasMore = false)))

    override fun getArtistAlbums(artistId: ArtistId, offset: Int, limit: Int, order: String): Flow<Result<UnifiedArtistAlbumsPage>> =
        flowOf(Result.success(UnifiedArtistAlbumsPage(listOf(UnifiedAlbum(AlbumId.kugou("9001"), "叶惠美")), hasMore = false)))

    override fun subscribeArtist(artistId: ArtistId, subscribe: Boolean): Flow<Result<Unit>> {
        subscribeCalls += artistId to subscribe
        return flowOf(Result.success(Unit))
    }

    override fun checkArtistFollowed(artistId: ArtistId): Flow<Result<Boolean>> = flowOf(Result.success(followedResult))
    override fun getArtistFansCount(artistId: ArtistId): Flow<Result<Long>> = flowOf(Result.success(999L))
    override fun getSimilarArtists(artistId: ArtistId): Flow<Result<List<UnifiedArtist>>> = flowOf(Result.success(emptyList()))
}

private class StubPlatformArtistRepo : PlatformArtistRepository {
    override fun getArtistDetail(artistId: ArtistId) = flowOf(Result.failure<UnifiedArtistDetail>(UnsupportedOperationException()))
    override fun getArtistTopSongs(artistId: ArtistId) = flowOf(Result.success(emptyList<UnifiedTrack>()))
    override fun getArtistAllSongs(artistId: ArtistId, offset: Int, limit: Int, order: String) = flowOf(Result.success(UnifiedArtistSongsPage(emptyList(), false)))
    override fun getArtistAlbums(artistId: ArtistId, offset: Int, limit: Int, order: String) = flowOf(Result.success(UnifiedArtistAlbumsPage(emptyList(), false)))
    override fun subscribeArtist(artistId: ArtistId, subscribe: Boolean) = flowOf(Result.success(Unit))
    override fun checkArtistFollowed(artistId: ArtistId) = flowOf(Result.success(false))
    override fun getArtistFansCount(artistId: ArtistId) = flowOf(Result.success(0L))
    override fun getSimilarArtists(artistId: ArtistId) = flowOf(Result.success(emptyList<UnifiedArtist>()))
}

private class StubLibraryRepo : LibraryRepository {
    override fun getUserRecord(uid: Long, type: Int) = flowOf(Result.success(emptyList<UserRecordTrack>()))
    override fun getCollectedAlbums(limit: Int) = flowOf(Result.success(emptyList<AlbumSubItem>()))
    override fun getUserSubcount() = flowOf(Result.success(UserSubcountResponse()))
}

private class StubArtistRepo : ArtistRepository {
    override fun getArtistDetail(artistId: Long) = flowOf(Result.failure<ArtistDetailInfo>(UnsupportedOperationException()))
    override fun getArtistAlbums(artistId: Long, limit: Int, offset: Int) = flowOf(Result.success(ArtistAlbumPage(emptyList(), false)))
    override fun getArtistFansCount(artistId: Long) = flowOf(Result.success(0L))
    override fun getArtistTopSongs(artistId: Long) = flowOf(Result.success(emptyList<Track>()))
    override fun subscribeArtist(artistId: Long, subscribe: Boolean) = flowOf(Result.success(Unit))
    override fun checkArtistFollowed(artistId: Long) = flowOf(Result.success(false))
    override fun getSimilarArtists(artistId: Long) = flowOf(Result.success(emptyList<ArtistInfo>()))
    override fun getArtistAllSongs(artistId: Long, offset: Int, limit: Int, order: String) = flowOf(Result.success(ArtistSongsPage(emptyList(), false)))
}

private class StubRecentRepo : RecentRepository {
    override fun getRecentSongs() = flowOf(Result.success(emptyList<RecentSong>()))
    override fun getRecentPlaylists() = flowOf(Result.success(emptyList<RecentPlaylist>()))
    override fun getRecentAlbums() = flowOf(Result.success(emptyList<RecentAlbum>()))
}

private class StubPlaybackRepo : PlaybackRepository {
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

private class StubHomeRepo : HomeRepository {
    override fun getHomeBlockPage(refresh: Boolean, cursor: String) = flowOf(Result.failure<HomeBlockPage>(UnsupportedOperationException()))
    override fun getPersonalizedPlaylists() = flowOf(Result.success(PersonalizedData()))
    override fun getToplistDetail() = flowOf(Result.success(emptyList<ToplistInfo>()))
    override fun getDailyRecommendSongs() = flowOf(Result.success(emptyList<DailySong>()))
    override fun dislikeDailySong(songId: Long) = flowOf(Result.success(Unit))
    override fun getHistoryRecommendDates() = flowOf(Result.success(emptyList<String>()))
    override fun getHistoryRecommendDetail(date: String) = flowOf(Result.success(emptyList<DailySong>()))
}

private class StubCommentRepo : CommentRepository {
    override fun getComments(songId: Long, limit: Int, offset: Int) = flowOf(Result.success(CommentsResponse()))
    override fun getComments(threadId: String, limit: Int, offset: Int) = flowOf(Result.success(CommentsResponse()))
    override fun likeComment(threadId: String, commentId: Long, like: Boolean) = flowOf(Result.success(Unit))
    override fun getCommentsV2(threadId: String, pageNo: Int, pageSize: Int, cursor: String, sortType: CommentSortType) =
        flowOf(Result.success(CommentsV2Data(comments = emptyList(), totalCount = 0, hasMore = false, cursor = "")))
    override fun getFloorComments(threadId: String, parentCommentId: Long, time: Long, limit: Int) =
        flowOf(Result.success(FloorCommentsData(comments = emptyList(), hasMore = false, totalCount = 0)))
    override fun addComment(threadId: String, content: String) = flowOf(Result.success<CommentItem?>(null))
    override fun replyComment(threadId: String, commentId: Long, content: String) = flowOf(Result.success<CommentItem?>(null))
    override fun deleteComment(threadId: String, commentId: Long) = flowOf(Result.success(Unit))
}

private class StubPlatformCommentRepo : PlatformCommentRepository {
    override fun getComments(target: com.lin0721.linmusic.core.platform.repository.CommentTarget, pageNo: Int, pageSize: Int, cursor: String, sortType: CommentSortType) =
        flowOf(Result.success(com.lin0721.linmusic.core.platform.repository.UnifiedCommentsPage(emptyList())))
    override fun getFloorComments(target: com.lin0721.linmusic.core.platform.repository.CommentTarget, parentCommentId: String, cursorOrTime: Long, pageNo: Int, limit: Int, kugouSpecialId: String?) =
        flowOf(Result.success(com.lin0721.linmusic.core.platform.repository.UnifiedFloorCommentsPage(emptyList())))
    override fun addComment(target: com.lin0721.linmusic.core.platform.repository.CommentTarget, content: String) = flowOf(Result.success<com.lin0721.linmusic.core.platform.UnifiedComment?>(null))
    override fun replyComment(target: com.lin0721.linmusic.core.platform.repository.CommentTarget, commentId: String, content: String, pid: String, replyUserName: String?, replyContent: String?, kugouSpecialId: String?) =
        flowOf(Result.success<com.lin0721.linmusic.core.platform.UnifiedComment?>(null))
    override fun deleteComment(target: com.lin0721.linmusic.core.platform.repository.CommentTarget, commentId: String, tid: String?, kugouSpecialId: String?) = flowOf(Result.success(Unit))
    override fun likeComment(target: com.lin0721.linmusic.core.platform.repository.CommentTarget, commentId: String, like: Boolean) = flowOf(Result.success(Unit))
}

private class StubNeteaseSearchRepo : SearchRepository {
    override fun getDefaultSearchKeyword() = flowOf(Result.success(""))
    override fun search(keyword: String, type: SearchType, offset: Int, limit: Int) =
        flowOf(Result.success(SearchPageResult(emptyList(), 0, false, 0)))
    override fun getHotSearches() = flowOf(Result.success(emptyList<HotSearch>()))
    override fun getPlaylistTags() = flowOf(Result.success(emptyList<PlaylistTag>()))
    override fun getSuggestions(keyword: String) = flowOf(Result.success(emptyList<SearchSuggestion>()))
    override fun getPlaylistsByCategory(category: String, cursor: Long, limit: Int) =
        flowOf(Result.success(PlaylistCategoryPage(emptyList(), false, 0L, 0)))
}

private class StubCloudUploadApi : CloudUploadApi {
    override suspend fun checkUpload(body: CloudUploadCheckRequest) = CloudUploadCheckResponse(code = 200)
    override suspend fun allocNosToken(body: NosTokenRequest) = NosTokenResponse(code = 200)
    override suspend fun registerCloudInfo(body: CloudInfoRequest) = CloudInfoResponse(code = 200)
    override suspend fun publishCloud(body: CloudPublishRequest) = com.lin0721.linmusic.feature.cloud.data.CloudActionResponse(code = 200)
}
