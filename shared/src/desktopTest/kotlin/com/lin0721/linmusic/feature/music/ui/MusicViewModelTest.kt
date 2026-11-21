package com.lin0721.linmusic.feature.music.ui

import com.lin0721.linmusic.feature.music.data.MusicRepository
import com.lin0721.linmusic.feature.music.data.STYLE_COVER_TTL_MS
import com.lin0721.linmusic.feature.music.data.StyleCoverCache
import com.lin0721.linmusic.feature.music.domain.MusicStyle
import com.lin0721.linmusic.feature.music.domain.StyleAlbumItem
import com.lin0721.linmusic.feature.music.domain.StyleArtistItem
import com.lin0721.linmusic.feature.music.domain.StyleHead
import com.lin0721.linmusic.feature.music.domain.StylePlaylistItem
import com.lin0721.linmusic.feature.music.domain.StylePortrait
import com.lin0721.linmusic.feature.music.domain.StylePreference
import com.lin0721.linmusic.feature.music.domain.StyleSongPage
import com.lin0721.linmusic.feature.music.domain.StyleSort
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class MusicViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun testStyle(id: Long, name: String = "曲风$id") = MusicStyle(
        id = id,
        name = name,
        enName = "Style$id",
        colorHex = "23303B"
    )

    private fun testHead(
        id: Long,
        coverUrl: String? = "http://cover/$id.jpg",
        portrait: StylePortrait? = null
    ) = StyleHead(
        id = id,
        name = "曲风$id",
        enName = "Style$id",
        desc = "",
        coverUrl = coverUrl,
        colorHex = "23303B",
        songNum = "100",
        artistNum = "10",
        favouriteSong = null,
        portrait = portrait
    )

    private class FakeMusicRepository : MusicRepository {
        var styleList: suspend () -> Result<List<MusicStyle>> = { Result.success(emptyList()) }
        var stylePreferences: suspend () -> Result<List<StylePreference>> = { Result.success(emptyList()) }
        var styleHead: suspend (Long) -> Result<StyleHead> = { id -> Result.failure(IllegalStateException("未配置:$id")) }

        val headCalls = mutableListOf<Long>()

        fun headCallCount(tagId: Long): Int = headCalls.count { it == tagId }

        override fun getStyleList(): Flow<Result<List<MusicStyle>>> = flow { emit(styleList()) }

        override fun getStylePreferences(): Flow<Result<List<StylePreference>>> = flow { emit(stylePreferences()) }

        override fun getStyleHead(tagId: Long): Flow<Result<StyleHead>> = flow {
            headCalls += tagId
            emit(styleHead(tagId))
        }

        override fun getStylePlaylists(tagId: Long): Flow<Result<List<StylePlaylistItem>>> =
            flow { emit(Result.success(emptyList())) }

        override fun getStyleSongs(tagId: Long, cursor: Int, sort: StyleSort): Flow<Result<StyleSongPage>> =
            flow { emit(Result.success(StyleSongPage(emptyList(), 0, false))) }

        override fun getStyleAlbums(tagId: Long, sort: StyleSort): Flow<Result<List<StyleAlbumItem>>> =
            flow { emit(Result.success(emptyList())) }

        override fun getStyleArtists(tagId: Long): Flow<Result<List<StyleArtistItem>>> =
            flow { emit(Result.success(emptyList())) }
    }

    @Test
    fun `按需请求曲风封面并持久化，重启后命中缓存直接展示不再请求`() = runTest {
        val store = InMemoryPreferencesStore()
        val repo1 = FakeMusicRepository().apply {
            styleList = { Result.success(listOf(testStyle(100L), testStyle(101L))) }
            styleHead = { id -> Result.success(testHead(id, coverUrl = "http://c/$id.jpg")) }
        }
        val vm1 = MusicViewModel(repo1, testResourceProvider, StyleCoverCache(dataStore = store))

        vm1.loadIfNeeded()
        val initial = vm1.uiState.value as MusicUiState.Success
        assertTrue(initial.data.styleCovers.isEmpty())

        vm1.requestStyleCover(100L)
        val afterFetch = vm1.uiState.value as MusicUiState.Success
        assertEquals("http://c/100.jpg", afterFetch.data.styleCovers[100L])
        assertEquals(1, repo1.headCallCount(100L))
        assertEquals(0, repo1.headCallCount(101L))

        // 重启后换新 ViewModel，命中 DataStore 缓存直接展示且不再发请求
        val repo2 = FakeMusicRepository().apply {
            styleList = { Result.success(listOf(testStyle(100L), testStyle(101L))) }
            styleHead = { id -> Result.success(testHead(id, coverUrl = "http://new/$id.jpg")) }
        }
        val vm2 = MusicViewModel(repo2, testResourceProvider, StyleCoverCache(dataStore = store))
        vm2.loadIfNeeded()

        val restored = vm2.uiState.value as MusicUiState.Success
        assertEquals("http://c/100.jpg", restored.data.styleCovers[100L])
        vm2.requestStyleCover(100L)
        assertEquals(0, repo2.headCallCount(100L))
    }

    @Test
    fun `偏好封面写入缓存并保留占比最高曲风画像`() = runTest {
        val store = InMemoryPreferencesStore()
        val portrait = StylePortrait("画像内容", "统计说明")
        val repo1 = FakeMusicRepository().apply {
            styleList = { Result.success(listOf(testStyle(10L), testStyle(20L))) }
            stylePreferences = {
                Result.success(
                    listOf(
                        StylePreference(10L, "流行", ratio = 70, colorHex = "23303B"),
                        StylePreference(20L, "摇滚", ratio = 30, colorHex = "AA2233")
                    )
                )
            }
            styleHead = { id ->
                Result.success(testHead(id, coverUrl = "http://pref/$id.jpg", portrait = if (id == 10L) portrait else null))
            }
        }
        val vm1 = MusicViewModel(repo1, testResourceProvider, StyleCoverCache(dataStore = store))

        vm1.loadIfNeeded()

        val data1 = (vm1.uiState.value as MusicUiState.Success).data
        assertEquals("http://pref/10.jpg", data1.styleCovers[10L])
        assertEquals("http://pref/20.jpg", data1.styleCovers[20L])
        assertEquals(portrait, data1.portrait)

        // 重启后（无偏好列表）仅加载全部曲风时也能直接命中此前写入的偏好封面缓存
        val repo2 = FakeMusicRepository().apply {
            styleList = { Result.success(listOf(testStyle(10L), testStyle(20L))) }
        }
        val vm2 = MusicViewModel(repo2, testResourceProvider, StyleCoverCache(dataStore = store))
        vm2.loadIfNeeded()

        val data2 = (vm2.uiState.value as MusicUiState.Success).data
        assertEquals("http://pref/10.jpg", data2.styleCovers[10L])
        assertEquals("http://pref/20.jpg", data2.styleCovers[20L])
        vm2.requestStyleCover(10L)
        assertEquals(0, repo2.headCallCount(10L))
    }

    @Test
    fun `缓存超过7天过期后重启失效并按需重拉`() = runTest {
        var now = 1_000_000L
        val store = InMemoryPreferencesStore()
        val repo1 = FakeMusicRepository().apply {
            styleList = { Result.success(listOf(testStyle(100L))) }
            styleHead = { Result.success(testHead(100L, coverUrl = "http://old/100.jpg")) }
        }
        val vm1 = MusicViewModel(repo1, testResourceProvider, StyleCoverCache(dataStore = store, nowMs = { now }))
        vm1.loadIfNeeded()
        vm1.requestStyleCover(100L)
        assertEquals("http://old/100.jpg", (vm1.uiState.value as MusicUiState.Success).data.styleCovers[100L])

        // 7 天后重启
        now += STYLE_COVER_TTL_MS + 1_000L
        val repo2 = FakeMusicRepository().apply {
            styleList = { Result.success(listOf(testStyle(100L))) }
            styleHead = { Result.success(testHead(100L, coverUrl = "http://fresh/100.jpg")) }
        }
        val vm2 = MusicViewModel(repo2, testResourceProvider, StyleCoverCache(dataStore = store, nowMs = { now }))
        vm2.loadIfNeeded()

        val beforeRequest = (vm2.uiState.value as MusicUiState.Success).data
        assertNull(beforeRequest.styleCovers[100L])

        vm2.requestStyleCover(100L)
        val afterRequest = (vm2.uiState.value as MusicUiState.Success).data
        assertEquals("http://fresh/100.jpg", afterRequest.styleCovers[100L])
        assertEquals(1, repo2.headCallCount(100L))
    }

    @Test
    fun `请求中与已有封面的曲风不重复发起请求`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val deferred = CompletableDeferred<Result<StyleHead>>()
        val repo = FakeMusicRepository().apply {
            styleList = { Result.success(listOf(testStyle(100L))) }
            styleHead = { deferred.await() }
        }
        val vm = MusicViewModel(repo, testResourceProvider, StyleCoverCache())
        vm.loadIfNeeded()
        runCurrent()

        // 连续触发同一曲风，在途只发一次请求
        vm.requestStyleCover(100L)
        vm.requestStyleCover(100L)
        runCurrent()
        assertEquals(1, repo.headCallCount(100L))

        deferred.complete(Result.success(testHead(100L, coverUrl = "http://c/100.jpg")))
        runCurrent()
        assertEquals("http://c/100.jpg", (vm.uiState.value as MusicUiState.Success).data.styleCovers[100L])

        // 已有封面后再触发也不会请求
        vm.requestStyleCover(100L)
        runCurrent()
        assertEquals(1, repo.headCallCount(100L))
    }

    @Test
    fun `单个曲风失败或无封面不影响页面且本次会话不再重试`() = runTest {
        val repo = FakeMusicRepository().apply {
            styleList = { Result.success(listOf(testStyle(200L), testStyle(201L), testStyle(202L), testStyle(203L))) }
            styleHead = { id ->
                when (id) {
                    200L -> Result.failure(IllegalStateException("限流"))
                    201L -> throw RuntimeException("网络异常")
                    202L -> Result.success(testHead(202L, coverUrl = null))
                    else -> Result.success(testHead(id, coverUrl = "http://c/$id.jpg"))
                }
            }
        }
        val vm = MusicViewModel(repo, testResourceProvider, StyleCoverCache())
        vm.loadIfNeeded()

        listOf(200L, 201L, 202L, 203L).forEach { id ->
            vm.requestStyleCover(id)
            vm.requestStyleCover(id)
        }

        val state = vm.uiState.value as MusicUiState.Success
        assertFalse(200L in state.data.styleCovers)
        assertFalse(201L in state.data.styleCovers)
        assertFalse(202L in state.data.styleCovers)
        assertEquals("http://c/203.jpg", state.data.styleCovers[203L])
        assertEquals(1, repo.headCallCount(200L))
        assertEquals(1, repo.headCallCount(201L))
        assertEquals(1, repo.headCallCount(202L))
        assertEquals(1, repo.headCallCount(203L))
    }

    @Test
    fun `并发请求曲风封面不超过3个`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val gates = (1L..6L).associateWith { CompletableDeferred<Result<StyleHead>>() }
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)

        val repo = FakeMusicRepository().apply {
            styleList = { Result.success((1L..6L).map { testStyle(it) }) }
            styleHead = { id ->
                val cur = active.incrementAndGet()
                maxActive.updateAndGet { prev -> maxOf(prev, cur) }
                try {
                    gates.getValue(id).await()
                } finally {
                    active.decrementAndGet()
                }
            }
        }
        val vm = MusicViewModel(repo, testResourceProvider, StyleCoverCache())
        vm.loadIfNeeded()
        runCurrent()

        (1L..6L).forEach(vm::requestStyleCover)
        runCurrent()

        assertEquals(3, active.get())
        assertEquals(3, maxActive.get())
        assertEquals(3, repo.headCalls.size)

        // 放行全部请求，验证剩余 3 个随后发出且并发仍不超过 3
        (1L..6L).forEach { id ->
            gates.getValue(id).complete(Result.success(testHead(id, coverUrl = "http://c/$id.jpg")))
            runCurrent()
        }

        assertEquals(0, active.get())
        assertTrue(maxActive.get() <= 3)
        assertEquals(6, (vm.uiState.value as MusicUiState.Success).data.styleCovers.size)
    }
}
