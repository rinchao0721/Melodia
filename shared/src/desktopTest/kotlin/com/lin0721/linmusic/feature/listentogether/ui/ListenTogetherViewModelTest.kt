package com.lin0721.linmusic.feature.listentogether.ui

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.listentogether.FakeRepository
import com.lin0721.linmusic.core.listentogether.ListenTogetherSession
import com.lin0721.linmusic.core.listentogether.RoomTrackResolver
import com.lin0721.linmusic.core.listentogether.TestController
import com.lin0721.linmusic.core.listentogether.domain.ListenTogetherInvite
import com.lin0721.linmusic.core.listentogether.domain.ListenTogetherRoom
import com.lin0721.linmusic.core.listentogether.domain.RoomCheck
import com.lin0721.linmusic.core.listentogether.domain.RoomPlayback
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.message.loginAs
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // 会话合并新增曲目时只解析新增的单首，界面解析的是整份房间队列
    private fun roomListResolves() = resolveCalls.count { it.size > 1 }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.build(
        repo: FakeRepository,
        controller: TestController,
        selfId: Long,
        userPreferences: UserPreferences = UserPreferences(InMemoryPreferencesStore())
    ): Pair<ListenTogetherSession, ListenTogetherViewModel> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val session = ListenTogetherSession(repo, controller, resolver, { selfId }, backgroundScope, dispatcher) { testScheduler.currentTime }
        val viewModel = ListenTogetherViewModel(session, controller, resolver, userPreferences, testResourceProvider)
        return session to viewModel
    }

    @Test
    fun `房间队列只在 id 列表变化时解析且保持房间顺序`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(RoomPlayback(listOf(1L, 2L), null)) }
        val (session, viewModel) = build(repo, TestController(), selfId = 2L)
        session.joinRoom("r1", 1L)
        runCurrent()
        advanceTimeBy(10_000L)
        assertEquals(1, roomListResolves())
        assertEquals(listOf(1L, 2L), viewModel.roomTracks.value.map { it.songId })

        repo.playback = Result.success(RoomPlayback(listOf(1L, 2L, 3L), null))
        advanceTimeBy(3_100L)
        assertEquals(2, roomListResolves())
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
    fun `房主与成员点击房间队列都会切歌`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(RoomPlayback(listOf(1L, 2L), null)) }
        val guestController = TestController()
        val (guest, guestVm) = build(repo, guestController, selfId = 2L)
        guest.joinRoom("r1", 1L)
        runCurrent()
        guestVm.playRoomTrack(1)
        guest.stop()
        assertTrue(guestController.actions.contains("play:2"))

        val hostController = TestController().apply { queueFlow.value = listOf(item(1L), item(2L)) }
        val (host, hostVm) = build(repo, hostController, selfId = 1L)
        host.createRoom(listOf(1L, 2L))
        runCurrent()
        hostVm.playRoomTrack(1)
        assertTrue(hostController.actions.contains("play:2"))
        host.stop()
    }

    @Test
    fun `startRoom 与 currentInviteText 生成带当前播放歌名的邀请文本`() = runTest {
        val repo = FakeRepository()
        val controller = TestController().apply {
            nowPlayingFlow.value = NowPlaying("100", "晴天", "周杰伦", null)
        }
        val prefs = UserPreferences(InMemoryPreferencesStore()).apply { loginAs(88L) }
        val (session, viewModel) = build(repo, controller, selfId = 88L, userPreferences = prefs)

        var createdText = ""
        viewModel.startRoom { createdText = it }
        runCurrent()
        assertEquals("邀请你一起听「晴天」【Melodia 一起听】roomId=r1;inviterId=88", createdText)
        assertEquals(createdText, viewModel.currentInviteText())

        controller.nowPlayingFlow.value = null
        assertEquals("邀请你一起听【Melodia 一起听】roomId=r1;inviterId=88", viewModel.currentInviteText())
        session.stop()
    }

    @Test
    fun `joinInvite 覆盖未入房成功失败、同房直接Joined、异房发NeedLeaveCurrent与leaveAndJoin`() = runTest {
        val repo = FakeRepository()
        val controller = TestController()
        val (session, viewModel) = build(repo, controller, selfId = 2L)
        val events = mutableListOf<JoinInviteEvent>()
        val collectJob = backgroundScope.launch { viewModel.joinEvents.collect { events += it } }
        runCurrent()

        // 1. 不在房间：加入失败 -> Failed
        repo.checkRoomHandler = { Result.success(RoomCheck(false, "房间已结束")) }
        viewModel.joinInvite("r1", 10L)
        runCurrent()
        assertEquals(listOf<JoinInviteEvent>(JoinInviteEvent.Failed("房间已结束")), events)
        assertFalse(session.state.value.active)

        // 2. 不在房间：加入成功 -> Joined
        repo.checkRoomHandler = { Result.success(RoomCheck(true, null)) }
        viewModel.joinInvite("r1", 10L)
        runCurrent()
        assertEquals(JoinInviteEvent.Joined(10L), events.last())
        assertEquals("r1", session.state.value.roomId)

        // 3. 已在同一 roomId -> 直接 Joined，不再请求接口
        val joinsBeforeSame = repo.joinCalls
        viewModel.joinInvite("r1", 10L)
        runCurrent()
        assertEquals(JoinInviteEvent.Joined(10L), events.last())
        assertEquals(joinsBeforeSame, repo.joinCalls)

        // 4. 在别的房间 -> NeedLeaveCurrent
        viewModel.joinInvite("r2", 20L)
        runCurrent()
        assertEquals(JoinInviteEvent.NeedLeaveCurrent("r2", 20L), events.last())
        assertEquals("r1", session.state.value.roomId)

        // 5. leaveAndJoin 先退出再加入新房间
        viewModel.leaveAndJoin("r2", 20L)
        runCurrent()
        assertEquals(JoinInviteEvent.Joined(20L), events.last())
        assertEquals("r2", session.state.value.roomId)

        session.stop()
        collectJob.cancel()
    }

    @Test
    fun `joinInvite 尊重 busy 防重入，joinByInvite 保持原有提示文案并触发事件`() = runTest {
        val repo = FakeRepository()
        val deferred = CompletableDeferred<Result<ListenTogetherRoom>>()
        repo.acceptHandler = { _, _ -> deferred.await() }
        val (session, viewModel) = build(repo, TestController(), selfId = 2L)
        val events = mutableListOf<JoinInviteEvent>()
        val messages = mutableListOf<String>()
        val j1 = backgroundScope.launch { viewModel.joinEvents.collect { events += it } }
        val j2 = backgroundScope.launch { viewModel.messages.collect { messages += it } }
        runCurrent()

        // 无效文案保持提示
        viewModel.joinByInvite("无效邀请")
        runCurrent()
        assertEquals(listOf("未识别到邀请内容，请粘贴完整的邀请文案"), messages)

        // 合法文案加入中时 busy 拦截第二次调用
        val inviteText = ListenTogetherInvite.format("r9", 99L, "七里香")
        viewModel.joinByInvite(inviteText)
        runCurrent()
        assertTrue(viewModel.busy.value)
        viewModel.joinInvite("r10", 100L)
        runCurrent()
        assertEquals(1, repo.joinCalls)

        deferred.complete(Result.success(ListenTogetherRoom("r9", 99L, emptyList())))
        runCurrent()
        assertFalse(viewModel.busy.value)
        assertEquals(JoinInviteEvent.Joined(99L), events.single())
        assertEquals("已加入，将跟随房主播放", messages.last())

        session.stop()
        j1.cancel()
        j2.cancel()
    }
}
