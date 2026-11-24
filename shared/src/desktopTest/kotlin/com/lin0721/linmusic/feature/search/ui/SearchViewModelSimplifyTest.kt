package com.lin0721.linmusic.feature.search.ui

import com.lin0721.linmusic.core.auth.FakeAuthRepository
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.platform.repository.PlatformSearchRepository
import com.lin0721.linmusic.core.platform.repository.PlatformSearchRouter
import com.lin0721.linmusic.core.platform.repository.UnifiedHotSearch
import com.lin0721.linmusic.core.platform.repository.UnifiedSearchPageResult
import com.lin0721.linmusic.core.platform.repository.UnifiedSearchSuggestion
import com.lin0721.linmusic.core.platform.repository.UnifiedSearchType
import com.lin0721.linmusic.core.playlistexternal.ExternalSongLikes
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.playlist.domain.CreatePlaylistAndAddSongUseCase
import com.lin0721.linmusic.feature.playlist.domain.FakeCreateRepository
import com.lin0721.linmusic.feature.playlist.domain.FakePlaylistRepository
import com.lin0721.linmusic.feature.playlist.domain.FakeSongLikeRepository
import com.lin0721.linmusic.feature.playlist.domain.FakeUserPlaylistRepository
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.podcast.ui.FakePlaybackController
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import com.lin0721.linmusic.feature.search.data.SearchHistoryPreferences
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.HotSearch
import com.lin0721.linmusic.feature.search.domain.PlaylistCategoryPage
import com.lin0721.linmusic.feature.search.domain.PlaylistTag
import com.lin0721.linmusic.feature.search.domain.SearchPageResult
import com.lin0721.linmusic.feature.search.domain.SearchSuggestion
import com.lin0721.linmusic.feature.search.domain.SearchType
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelSimplifyTest {

    private val scheduler = TestCoroutineScheduler()
    private val repository = CountingSearchRepository()
    private val settings = SettingsPreferences(InMemoryPreferencesStore())

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        userPreferences: UserPreferences = UserPreferences(InMemoryPreferencesStore()),
        platformSearchRouter: PlatformSearchRouter? = null
    ): SearchViewModel {
        val likes = FakeSongLikeRepository()
        val playlistRepository = FakePlaylistRepository()
        val externalStore = PlaylistExternalTrackStore(InMemoryPreferencesStore())
        return SearchViewModel(
            repository = repository,
            historyPreferences = SearchHistoryPreferences(InMemoryPreferencesStore()),
            playerManager = FakePlaybackController(),
            userPreferences = userPreferences,
            resourceProvider = testResourceProvider,
            songCollectDelegate = SongCollectDelegate(
                userPlaylistRepository = FakeUserPlaylistRepository(),
                playlistRepository = playlistRepository,
                songLikeRepository = likes,
                createPlaylistAndAddSongUseCase = CreatePlaylistAndAddSongUseCase(
                    FakeCreateRepository(), playlistRepository, PlaylistMutationBus()
                ),
                userPreferences = userPreferences,
                resourceProvider = testResourceProvider,
                externalStore = externalStore,
                externalSongLikes = ExternalSongLikes(externalStore, userPreferences) { emptyList() }
            ),
            loadLikedSongIdsUseCase = LoadLikedSongIdsUseCase(userPreferences, likes),
            songLikeRepository = likes,
            syncProfileAfterLoginUseCase = SyncProfileAfterLoginUseCase(userPreferences, FakeAuthRepository()),
            settingsPreferences = settings,
            platformSearchRouter = platformSearchRouter
        )
    }

    private fun kugouRouter() = PlatformSearchRouter(StubPlatformSearchRepo(), StubPlatformSearchRepo())

    private suspend fun prefsOnKugou(): UserPreferences {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        prefs.upsertPlatformAccount(
            StoredAccount(uid = 9L, platform = MusicPlatform.KUGOU, platformUid = "9", credentials = mapOf("token" to "t")),
            activate = true
        )
        prefs.setCurrentPlatform(MusicPlatform.KUGOU)
        return prefs
    }

    @Test
    fun `默认标签取当前平台`() = runTest(scheduler) {
        val vm = viewModel(prefsOnKugou(), kugouRouter())
        advanceUntilIdle()

        assertEquals(MusicPlatform.KUGOU, vm.selectedPlatform.value)
        assertTrue(MusicPlatform.NETEASE in vm.searchPlatforms.value)
        assertEquals(listOf(MusicPlatform.KUGOU, MusicPlatform.NETEASE), vm.searchPlatforms.value)
    }

    @Test
    fun `当前平台不在可用标签内时回落网易`() = runTest(scheduler) {
        val vm = viewModel(prefsOnKugou(), platformSearchRouter = null)
        advanceUntilIdle()

        assertEquals(MusicPlatform.NETEASE, vm.selectedPlatform.value)
    }

    @Test
    fun `手动选标签后保持，当前平台变化后重新跟随`() = runTest(scheduler) {
        val prefs = UserPreferences(InMemoryPreferencesStore())
        val vm = viewModel(prefs, kugouRouter())
        advanceUntilIdle()
        assertEquals(MusicPlatform.NETEASE, vm.selectedPlatform.value)

        vm.selectPlatform(MusicPlatform.KUGOU)
        advanceUntilIdle()
        assertEquals(MusicPlatform.KUGOU, vm.selectedPlatform.value)

        prefs.setCurrentPlatform(MusicPlatform.KUGOU)
        advanceUntilIdle()
        prefs.setCurrentPlatform(MusicPlatform.NETEASE)
        advanceUntilIdle()
        assertEquals(MusicPlatform.NETEASE, vm.selectedPlatform.value)
    }

    @Test
    fun `关闭歌单分类后不请求分类接口，重新打开后恢复`() = runTest(scheduler) {
        settings.savePlaylistTagsEnabled(false)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(0, repository.tagsCalls)
        assertTrue((vm.discoveryState.value as DiscoveryUiState.Success).playlistTags.isEmpty())

        settings.savePlaylistTagsEnabled(true)
        advanceUntilIdle()

        assertEquals(1, repository.tagsCalls)
        assertEquals(listOf("华语"), (vm.discoveryState.value as DiscoveryUiState.Success).playlistTags.map { it.name })
    }

    @Test
    fun `关闭联想后输入不请求联想，重新打开后按当前输入补拉`() = runTest(scheduler) {
        settings.saveSearchSuggestionEnabled(false)
        val vm = viewModel()
        advanceUntilIdle()

        vm.activateSearch()
        vm.updateQuery("周")
        advanceUntilIdle()

        assertEquals(0, repository.suggestionCalls)
        assertTrue(vm.inputState.value.currentSuggestions.isEmpty())

        settings.saveSearchSuggestionEnabled(true)
        advanceUntilIdle()

        assertEquals(1, repository.suggestionCalls)
        assertEquals(listOf("周杰伦"), vm.inputState.value.currentSuggestions.map { it.text })
    }

    @Test
    fun `输入中关闭联想会清掉已有联想`() = runTest(scheduler) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.activateSearch()
        vm.updateQuery("周")
        advanceUntilIdle()
        assertEquals(listOf("周杰伦"), vm.inputState.value.currentSuggestions.map { it.text })

        settings.saveSearchSuggestionEnabled(false)
        advanceUntilIdle()

        assertTrue(vm.inputState.value.currentSuggestions.isEmpty())
    }

    @Test
    fun `关闭热搜后不请求热搜接口，重新打开后恢复`() = runTest(scheduler) {
        settings.saveHotSearchEnabled(false)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(0, repository.hotCalls)
        assertTrue((vm.discoveryState.value as DiscoveryUiState.Success).hotSearches.isEmpty())

        settings.saveHotSearchEnabled(true)
        advanceUntilIdle()

        assertEquals(1, repository.hotCalls)
        assertEquals(listOf("周杰伦"), (vm.discoveryState.value as DiscoveryUiState.Success).hotSearches.map { it.keyword })
    }

    @Test
    fun `热搜和歌单分类都关闭时，即使推荐词接口失败也保持空白成功态`() = runTest(scheduler) {
        repository.failKeyword = true
        settings.saveHotSearchEnabled(false)
        settings.savePlaylistTagsEnabled(false)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(0, repository.hotCalls)
        assertEquals(0, repository.tagsCalls)
        val state = vm.discoveryState.value
        assertTrue(state is DiscoveryUiState.Success)
        val success = state as DiscoveryUiState.Success
        assertTrue(success.hotSearches.isEmpty())
        assertTrue(success.playlistTags.isEmpty())
    }
}

private class CountingSearchRepository : SearchRepository {
    var hotCalls = 0
    var tagsCalls = 0
    var suggestionCalls = 0
    var failKeyword = false

    override fun getDefaultSearchKeyword(): Flow<Result<String>> =
        flowOf(if (failKeyword) Result.failure(IllegalStateException("网络异常")) else Result.success(""))

    override fun search(keyword: String, type: SearchType, offset: Int, limit: Int): Flow<Result<SearchPageResult>> =
        flowOf(Result.failure(IllegalStateException("未实现")))

    override fun getHotSearches(): Flow<Result<List<HotSearch>>> {
        hotCalls++
        return flowOf(Result.success(listOf(HotSearch(keyword = "周杰伦", score = 100))))
    }

    override fun getPlaylistTags(): Flow<Result<List<PlaylistTag>>> {
        tagsCalls++
        return flowOf(Result.success(listOf(PlaylistTag("华语"))))
    }

    override fun getSuggestions(keyword: String): Flow<Result<List<SearchSuggestion>>> {
        suggestionCalls++
        return flowOf(Result.success(listOf(SearchSuggestion.Keyword("周杰伦"))))
    }

    override fun getPlaylistsByCategory(category: String, cursor: Long, limit: Int): Flow<Result<PlaylistCategoryPage>> =
        flowOf(Result.failure(IllegalStateException("未实现")))
}

private class StubPlatformSearchRepo : PlatformSearchRepository {
    override fun search(platform: MusicPlatform, keyword: String, type: UnifiedSearchType, offset: Int, limit: Int) =
        flowOf(Result.success(UnifiedSearchPageResult(platform, emptyList(), 0, false, 0)))
    override fun getHotSearches(platform: MusicPlatform) = flowOf(Result.success(emptyList<UnifiedHotSearch>()))
    override fun getSuggestions(platform: MusicPlatform, keyword: String) =
        flowOf(Result.success(emptyList<UnifiedSearchSuggestion>()))
    override fun getDefaultSearchKeyword(platform: MusicPlatform) = flowOf(Result.success(""))
}
