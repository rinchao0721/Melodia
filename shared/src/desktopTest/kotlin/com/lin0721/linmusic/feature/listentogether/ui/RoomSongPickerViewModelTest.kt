package com.lin0721.linmusic.feature.listentogether.ui

import com.lin0721.linmusic.core.listentogether.FakeRepository
import com.lin0721.linmusic.core.listentogether.ListenTogetherSession
import com.lin0721.linmusic.core.listentogether.RoomTrackResolver
import com.lin0721.linmusic.core.listentogether.TestController
import com.lin0721.linmusic.core.listentogether.data.RoomSongSource
import com.lin0721.linmusic.core.listentogether.domain.RoomPlayback
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomSongPickerViewModelTest {

    private fun item(id: Long) = QueueItem(id, "歌$id", "手", "")

    private val searches = mutableListOf<String>()

    private val source = object : RoomSongSource {
        override suspend fun liked(limit: Int) = listOf(item(11L), item(12L))
        override suspend fun search(keyword: String, limit: Int): List<QueueItem> {
            searches += keyword
            return listOf(item(21L), item(-5L))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.build(repo: FakeRepository): Pair<ListenTogetherSession, RoomSongPickerViewModel> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val session = ListenTogetherSession(
            repo, TestController(), RoomTrackResolver { ids -> ids.map(::item) }, { 2L }, backgroundScope, dispatcher
        ) { testScheduler.currentTime }
        return session to RoomSongPickerViewModel(source, session, testResourceProvider)
    }

    @Test
    fun `打开时列出喜欢的音乐，输入后防抖搜索并过滤非网易云曲目`() = runTest {
        val (_, viewModel) = build(FakeRepository())
        viewModel.open()
        runCurrent()
        assertEquals("我喜欢的音乐", viewModel.state.value.title)
        assertEquals(listOf(11L, 12L), viewModel.state.value.tracks.map { it.songId })

        viewModel.setQuery("y")
        viewModel.setQuery("yo")
        advanceTimeBy(500L)
        assertEquals(listOf("yo"), searches)
        assertEquals(listOf(21L), viewModel.state.value.tracks.map { it.songId })
    }

    @Test
    fun `添加歌曲上报房间队列并标记已添加`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(RoomPlayback(listOf(1L), null)) }
        val (session, viewModel) = build(repo)
        session.joinRoom("r1", 1L)
        runCurrent()

        viewModel.add(item(21L))
        runCurrent()

        assertTrue(21L in viewModel.state.value.added)
        assertEquals(listOf(1L, 21L), repo.playlists.last())
        session.stop()
    }

    @Test
    fun `面板打开期间他人点的歌及时标为已在队列`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(RoomPlayback(listOf(1L), null)) }
        val (session, viewModel) = build(repo)
        session.joinRoom("r1", 1L)
        runCurrent()
        assertEquals(setOf(1L), viewModel.roomSongIds.value)

        repo.playback = Result.success(RoomPlayback(listOf(1L, 21L), null))
        advanceTimeBy(3_100L)

        assertEquals(setOf(1L, 21L), viewModel.roomSongIds.value)
        session.stop()
    }

    @Test
    fun `不在房间时添加失败并撤销已添加标记`() = runTest {
        val (_, viewModel) = build(FakeRepository())
        viewModel.add(item(21L))
        runCurrent()

        assertTrue(viewModel.state.value.added.isEmpty())
    }
}
