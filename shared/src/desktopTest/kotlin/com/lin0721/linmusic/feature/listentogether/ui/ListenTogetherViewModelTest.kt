package com.lin0721.linmusic.feature.listentogether.ui

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.listentogether.FakeRepository
import com.lin0721.linmusic.core.listentogether.ListenTogetherSession
import com.lin0721.linmusic.core.listentogether.RoomTrackResolver
import com.lin0721.linmusic.core.listentogether.TestController
import com.lin0721.linmusic.core.listentogether.domain.RoomPlayback
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import kotlinx.coroutines.CoroutineScope
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
class ListenTogetherViewModelTest {

    private val resolveCalls = mutableListOf<List<Long>>()
    private var resolveFails = false

    private fun item(id: Long) = QueueItem(id, "歌$id", "手", "")

    private val resolver = RoomTrackResolver { ids ->
        resolveCalls += ids
        if (resolveFails) throw IllegalStateException("详情接口失败")
        ids.reversed().map(::item)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.build(repo: FakeRepository, controller: TestController, selfId: Long): Pair<ListenTogetherSession, ListenTogetherViewModel> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val session = ListenTogetherSession(repo, controller, resolver, { selfId }, CoroutineScope(dispatcher), dispatcher)
        val viewModel = ListenTogetherViewModel(session, controller, resolver, UserPreferences(InMemoryPreferencesStore()), testResourceProvider)
        return session to viewModel
    }

    @Test
    fun `房间队列只在 id 列表变化时解析且保持房间顺序`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(RoomPlayback(listOf(1L, 2L), null)) }
        val (session, viewModel) = build(repo, TestController(), selfId = 2L)
        session.joinRoom("r1", 1L)
        runCurrent()
        advanceTimeBy(10_000L)
        assertEquals(1, resolveCalls.size)
        assertEquals(listOf(1L, 2L), viewModel.roomTracks.value.map { it.songId })

        repo.playback = Result.success(RoomPlayback(listOf(1L, 2L, 3L), null))
        advanceTimeBy(3_100L)
        assertEquals(2, resolveCalls.size)
        assertEquals(listOf(1L, 2L, 3L), viewModel.roomTracks.value.map { it.songId })
        session.stop()
        runCurrent()
        assertTrue(viewModel.roomTracks.value.isEmpty())
    }

    @Test
    fun `解析失败时队列为空且不崩溃`() = runTest {
        resolveFails = true
        val repo = FakeRepository().apply { playback = Result.success(RoomPlayback(listOf(1L), null)) }
        val (session, viewModel) = build(repo, TestController(), selfId = 2L)
        session.joinRoom("r1", 1L)
        runCurrent()
        assertTrue(viewModel.roomTracks.value.isEmpty())
        assertTrue(session.state.value.active)
        session.stop()
    }

    @Test
    fun `只有房主点击房间队列会切歌`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(RoomPlayback(listOf(1L, 2L), null)) }
        val guestController = TestController()
        val (guest, guestVm) = build(repo, guestController, selfId = 2L)
        guest.joinRoom("r1", 1L)
        runCurrent()
        guestVm.playRoomTrack(1)
        assertTrue(guestController.actions.none { it.startsWith("queue:") })
        guest.stop()

        val hostController = TestController().apply { queueFlow.value = listOf(item(1L), item(2L)) }
        val (host, hostVm) = build(repo, hostController, selfId = 1L)
        host.createRoom(listOf(1L, 2L))
        runCurrent()
        hostVm.playRoomTrack(1)
        assertTrue(hostController.actions.contains("queue:2@1"))
        host.stop()
    }
}
