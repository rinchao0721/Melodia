package com.lin0721.linmusic.feature.recent.ui

import com.lin0721.linmusic.core.model.SongPrivilege
import com.lin0721.linmusic.core.model.SongFileInfo
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.songquality.SongQualityCache
import com.lin0721.linmusic.feature.podcast.ui.FakePlaybackController
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import com.lin0721.linmusic.feature.recent.data.RecentRepository
import com.lin0721.linmusic.feature.recent.domain.RecentAlbum
import com.lin0721.linmusic.feature.recent.domain.RecentPlaylist
import com.lin0721.linmusic.feature.recent.domain.RecentSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
class RecentPlayViewModelQualityPrefetchTest {

    private val scheduler = TestCoroutineScheduler()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `列表进入 Success 前先完成缺失音质预查`() = runTest(scheduler) {
        val songs = listOf(
            RecentSong(Track(id = 1L, privilege = SongPrivilege(maxBrLevel = "lossless")), 100L, "10:00"),
            RecentSong(Track(id = 2L, sq = SongFileInfo(br = 999_000)), 90L, "09:50"),
            RecentSong(Track(id = 3L, hr = SongFileInfo(br = 1_500_000)), 80L, "09:40"),
            RecentSong(Track(id = 4L), 70L, "09:30")
        )
        val fetchedCalls = mutableListOf<List<Long>>()
        val qualityCache = SongQualityCache(
            enabledFlow = MutableStateFlow(true),
            fetcher = { ids ->
                fetchedCalls += ids
                mapOf(3L to "jymaster", 4L to "lossless")
            },
            scope = backgroundScope,
            batchDelayMs = 300
        )

        val vm = RecentPlayViewModel(
            recentRepository = FakeRecentRepository(songs),
            playerManager = FakePlaybackController(),
            resourceProvider = testResourceProvider,
            songQualityCache = qualityCache
        )

        val state = vm.uiState.first { it is RecentPlayUiState.Success } as RecentPlayUiState.Success
        assertEquals(4, state.songs.size)
        assertEquals(listOf(listOf(3L, 4L)), fetchedCalls)
        assertEquals("jymaster", qualityCache.levels.value[3L])
        assertEquals("lossless", qualityCache.levels.value[4L])
    }

    @Test
    fun `预查失败不阻塞列表进入 Success`() = runTest(scheduler) {
        val songs = listOf(RecentSong(Track(id = 9L), 100L, "10:00"))
        val qualityCache = SongQualityCache(
            enabledFlow = MutableStateFlow(true),
            fetcher = { error("接口异常") },
            scope = backgroundScope,
            batchDelayMs = 300
        )

        val vm = RecentPlayViewModel(
            recentRepository = FakeRecentRepository(songs),
            playerManager = FakePlaybackController(),
            resourceProvider = testResourceProvider,
            songQualityCache = qualityCache
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is RecentPlayUiState.Success)
        assertEquals(1, (state as RecentPlayUiState.Success).songs.size)
        assertTrue(qualityCache.levels.value.isEmpty())
    }

    private class FakeRecentRepository(private val songs: List<RecentSong>) : RecentRepository {
        override fun getRecentSongs(): Flow<Result<List<RecentSong>>> = flowOf(Result.success(songs))
        override fun getRecentPlaylists(): Flow<Result<List<RecentPlaylist>>> = flowOf(Result.success(emptyList()))
        override fun getRecentAlbums(): Flow<Result<List<RecentAlbum>>> = flowOf(Result.success(emptyList()))
    }
}
