package com.lin0721.linmusic.feature.podcast.ui

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.feature.podcast.data.PodcastProgressPreferences
import com.lin0721.linmusic.feature.podcast.data.PodcastSeenPreferences
import com.lin0721.linmusic.feature.podcast.data.RADIO_PROGRAM_PAGE_SIZE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val PAGE = RADIO_PROGRAM_PAGE_SIZE

@OptIn(ExperimentalCoroutinesApi::class)
class RadioDetailViewModelTest {

    private val repository = FakePodcastRepository()
    private val controller = FakePlaybackController()
    private val userPreferences = UserPreferences(InMemoryPreferencesStore())
    private val progress = PodcastProgressPreferences(InMemoryPreferencesStore())
    private val seen = PodcastSeenPreferences(InMemoryPreferencesStore())
    private val toasts = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): RadioDetailViewModel {
        val vm = RadioDetailViewModel(
            repository, controller, userPreferences, progress, seen, testResourceProvider
        )
        CoroutineScope(UnconfinedTestDispatcher()).launch { vm.toastEvent.collect { toasts += it } }
        return vm
    }

    private fun logIn() = runBlocking { userPreferences.saveUserProfile(UserProfile(1, "用户", "")) }

    private fun RadioDetailViewModel.success() = uiState.value as RadioDetailUiState.Success

    @Test
    fun `进入详情页即把该电台最新一期记为已看`() {
        repository.radioPrograms = { _, _, _ ->
            Result.success(listOf(testProgram(1, createTimeMs = 9_000), testProgram(2, createTimeMs = 5_000)))
        }
        val vm = viewModel()

        vm.load(7)

        assertEquals(9_000L, runBlocking { seen.seen.first() }[7])
    }

    @Test
    fun `同一电台重复进入不重拉`() {
        val vm = viewModel()

        vm.load(7)
        vm.load(7)

        assertEquals(1, repository.count("detail:7"))
    }

    @Test
    fun `详情失败即整页失败`() {
        repository.radioDetail = { failure() }
        val vm = viewModel()

        vm.load(7)

        assertTrue(vm.uiState.value is RadioDetailUiState.Error)
    }

    @Test
    fun `切换排序重新从第一页拉取`() {
        repository.radioPrograms = { _, _, asc ->
            Result.success(if (asc) listOf(testProgram(1)) else listOf(testProgram(2)))
        }
        val vm = viewModel()
        vm.load(7)
        assertEquals(listOf(2L), vm.success().programs.map { it.id })

        vm.setSortAscending(true)

        assertEquals(1, repository.count("programs:7:0:true"))
        assertEquals(true, vm.success().sortAscending)
        assertEquals(listOf(1L), vm.success().programs.map { it.id })
        assertEquals(false, vm.success().isReloadingPrograms)
    }

    @Test
    fun `切换排序失败时退回原排序并提示`() {
        repository.radioPrograms = { _, _, asc ->
            if (asc) failure("网络错误") else Result.success(listOf(testProgram(2)))
        }
        val vm = viewModel()
        vm.load(7)

        vm.setSortAscending(true)

        assertEquals(false, vm.success().sortAscending)
        assertEquals(listOf(2L), vm.success().programs.map { it.id })
        assertEquals(1, toasts.size)
    }

    @Test
    fun `翻页沿用当前排序`() {
        repository.radioPrograms = { _, offset, asc ->
            val base = if (offset == 0) 0 else 10_000
            Result.success(List(PAGE) { testProgram((base + it + if (asc) 100_000 else 0).toLong() + 1) })
        }
        val vm = viewModel()
        vm.load(7)
        vm.setSortAscending(true)

        vm.loadMore()

        assertEquals(1, repository.count("programs:7:$PAGE:true"))
        assertEquals(PAGE * 2, vm.success().programs.size)
    }

    @Test
    fun `翻页失败保留已有内容并可重试`() {
        var failNext = false
        repository.radioPrograms = { _, offset, _ ->
            if (offset > 0 && failNext) {
                failNext = false
                failure()
            } else {
                Result.success(List(PAGE) { testProgram((offset + it).toLong() + 1) })
            }
        }
        val vm = viewModel()
        vm.load(7)

        failNext = true
        vm.loadMore()

        assertEquals(PAGE, vm.success().programs.size)
        assertTrue(vm.success().hasMore)
        assertEquals("失败", vm.success().loadMoreError)

        vm.loadMore()

        assertEquals(PAGE * 2, vm.success().programs.size)
        assertNull(vm.success().loadMoreError)
        assertTrue(vm.success().hasMore)
    }

    @Test
    fun `翻页返回空列表视为到底`() {
        repository.radioPrograms = { _, offset, _ ->
            Result.success(if (offset == 0) List(PAGE) { testProgram(it.toLong() + 1) } else emptyList())
        }
        val vm = viewModel()
        vm.load(7)

        vm.loadMore()

        assertEquals(false, vm.success().hasMore)
        assertNull(vm.success().loadMoreError)
    }

    @Test
    fun `播放某期时从未听完的进度续播`() {
        repository.radioPrograms = { _, _, _ -> Result.success(listOf(testProgram(1), testProgram(2))) }
        runBlocking { progress.upsert(testProgress(20, positionMs = 88_000)) }
        val vm = viewModel()
        vm.load(7)

        vm.playAt(1)

        val call = controller.queueCalls.single()
        assertEquals(1, call.startIndex)
        assertEquals(88_000L, call.startPositionMs)
    }

    @Test
    fun `主播放键优先继续最近收听且未听完的一期`() {
        repository.radioPrograms = { _, _, _ ->
            Result.success(listOf(testProgram(1), testProgram(2), testProgram(3)))
        }
        runBlocking {
            progress.upsert(testProgress(20, positionMs = 50_000, updatedAtMs = 1))
            progress.upsert(testProgress(30, positionMs = 60_000, updatedAtMs = 2))
        }
        val vm = viewModel()
        vm.load(7)

        vm.playPrimary()

        assertEquals(2, controller.queueCalls.single().startIndex)
        assertEquals(60_000L, controller.queueCalls.single().startPositionMs)
    }

    @Test
    fun `没有进度时主播放键播第一期`() {
        repository.radioPrograms = { _, _, _ -> Result.success(listOf(testProgram(1), testProgram(2))) }
        val vm = viewModel()
        vm.load(7)

        vm.playPrimary()

        assertNull(vm.success().resumeProgram)
        assertEquals(0, controller.queueCalls.single().startIndex)
        assertEquals(0L, controller.queueCalls.single().startPositionMs)
    }

    @Test
    fun `未登录订阅被拦住且不调用接口`() {
        val vm = viewModel()
        vm.load(7)

        vm.toggleSubscribe()

        assertEquals(0, repository.count("subscribe:"))
        assertEquals(listOf("登录后才能订阅电台"), toasts)
    }

    @Test
    fun `登录后订阅成功更新状态`() {
        logIn()
        val vm = viewModel()
        vm.load(7)

        vm.toggleSubscribe()

        assertEquals(1, repository.count("subscribe:7:true"))
        assertEquals(true, vm.success().detail.subscribed)
        assertEquals(false, vm.success().isSubscribing)
        assertEquals(listOf("已订阅"), toasts)
    }

    @Test
    fun `订阅失败保持原状态`() {
        logIn()
        repository.setSubscribed = { _, _ -> failure() }
        val vm = viewModel()
        vm.load(7)

        vm.toggleSubscribe()

        assertEquals(false, vm.success().detail.subscribed)
        assertEquals(false, vm.success().isSubscribing)
    }
}
