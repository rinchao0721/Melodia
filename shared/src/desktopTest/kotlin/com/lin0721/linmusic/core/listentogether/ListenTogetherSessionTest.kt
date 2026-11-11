package com.lin0721.linmusic.core.listentogether

import com.lin0721.linmusic.core.listentogether.data.ListenTogetherRepository
import com.lin0721.linmusic.core.listentogether.domain.HostCommand
import com.lin0721.linmusic.core.listentogether.domain.ListenTogetherRoom
import com.lin0721.linmusic.core.listentogether.domain.RoomCheck
import com.lin0721.linmusic.core.listentogether.domain.RoomCommand
import com.lin0721.linmusic.core.listentogether.domain.RoomPlayback
import com.lin0721.linmusic.core.listentogether.domain.RoomStatus
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.podcast.ui.FakePlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

private class TestController : PlaybackController by FakePlaybackController() {
    val nowPlayingFlow = MutableStateFlow<NowPlaying?>(null)
    val playWhenReadyFlow = MutableStateFlow(false)
    val positionFlow = MutableStateFlow(0L)
    val queueFlow = MutableStateFlow<List<QueueItem>>(emptyList())
    val actions = mutableListOf<String>()

    override val nowPlaying: StateFlow<NowPlaying?> = nowPlayingFlow
    override val playWhenReady: StateFlow<Boolean> = playWhenReadyFlow
    override val currentPosition: StateFlow<Long> = positionFlow
    override val queue: StateFlow<List<QueueItem>> = queueFlow

    override fun pause() { actions += "pause" }
    override fun resume() { actions += "resume" }
    override fun seekTo(positionMs: Long) { actions += "seek:$positionMs" }
    override fun appendToQueue(items: List<QueueItem>) {
        queueFlow.value = queueFlow.value + items
    }
    override fun playAtIndex(index: Int) { actions += "play:${queueFlow.value[index].songId}" }
}

private class FakeRepository : ListenTogetherRepository {
    var status: Result<RoomStatus> = Result.success(RoomStatus(true, null))
    var playback: Result<RoomPlayback?> = Result.success(null)
    var acceptCommand = true
    var checkJoinable = true
    val commands = mutableListOf<HostCommand>()
    val playlists = mutableListOf<List<Long>>()
    var endCalls = 0
    var playbackCalls = 0

    override fun status(): Flow<Result<RoomStatus>> = flowOf(status)
    override fun checkRoom(roomId: String) = flowOf(Result.success(RoomCheck(checkJoinable, "房间已满")))
    override fun createRoom(songIds: List<Long>) = flowOf(Result.success(ListenTogetherRoom("r1", 1L, emptyList())))
    override fun acceptInvitation(roomId: String, inviterId: Long) =
        flowOf(Result.success(ListenTogetherRoom(roomId, inviterId, emptyList())))
    override fun playback(roomId: String): Flow<Result<RoomPlayback?>> {
        playbackCalls++
        return flowOf(playback)
    }
    override fun reportCommand(roomId: String, command: HostCommand): Flow<Result<Boolean>> {
        commands += command
        return flowOf(Result.success(acceptCommand))
    }
    override fun reportPlaylist(roomId: String, userId: Long, version: Long, songIds: List<Long>, anchorIndex: Int): Flow<Result<Boolean>> {
        playlists += songIds
        return flowOf(Result.success(true))
    }
    override fun heartbeat(roomId: String, songId: Long, playing: Boolean, progressMs: Long) = flowOf(Result.success<Int?>(30))
    override fun endRoom(roomId: String): Flow<Result<Boolean>> {
        endCalls++
        return flowOf(Result.success(true))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListenTogetherSessionTest {

    private fun item(id: Long) = QueueItem(id, "歌$id", "手", "")

    private fun TestScope.session(repo: FakeRepository, controller: TestController, selfId: Long = 1L): ListenTogetherSession {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return ListenTogetherSession(
            repository = repo,
            controller = controller,
            resolver = RoomTrackResolver { ids -> ids.map(::item) },
            selfUserId = { selfId },
            scope = CoroutineScope(dispatcher),
            playerDispatcher = dispatcher
        )
    }

    private fun roomCommand(song: Long, seq: Long, playing: Boolean = true, progress: Long = 0L) =
        RoomPlayback(listOf(song), RoomCommand("GOTO", song, progress, playing, seq))

    @Test
    fun `房主建房后推送队列并上报当前歌曲`() = runTest {
        val repo = FakeRepository()
        val controller = TestController().apply {
            queueFlow.value = listOf(item(1), item(2))
            nowPlayingFlow.value = NowPlaying("1", "歌1", "手", null)
            playWhenReadyFlow.value = true
        }
        val session = session(repo, controller)

        assertTrue(session.createRoom(listOf(1L, 2L)).isSuccess)
        runCurrent()

        assertEquals(listOf(listOf(1L, 2L)), repo.playlists)
        assertEquals("GOTO", repo.commands.first().type.wire)
        assertEquals(1L, repo.commands.first().songId)
        session.stop()
    }

    @Test
    fun `房主指令被拒绝时不记为已上报并冷却重试`() = runTest {
        val repo = FakeRepository().apply { acceptCommand = false }
        val controller = TestController().apply {
            queueFlow.value = listOf(item(1))
            nowPlayingFlow.value = NowPlaying("1", "歌1", "手", null)
        }
        val session = session(repo, controller)
        session.createRoom(listOf(1L))
        runCurrent()
        val firstRound = repo.commands.size

        advanceTimeBy(3_100L)
        assertEquals(firstRound, repo.commands.size)
        advanceTimeBy(15_000L)
        assertTrue(repo.commands.size > firstRound)
        session.stop()
    }

    @Test
    fun `客机收到新歌曲指令后追加并播放`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(roomCommand(5L, seq = 100L, progress = 2_000L)) }
        val controller = TestController().apply { playWhenReadyFlow.value = true }
        val session = session(repo, controller, selfId = 2L)

        assertTrue(session.joinRoom("r1", 1L).isSuccess)
        runCurrent()

        assertEquals(listOf("play:5", "seek:2000"), controller.actions)
        session.stop()
    }

    @Test
    fun `同一序号的指令只应用一次`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(roomCommand(5L, seq = 100L)) }
        val controller = TestController()
        val session = session(repo, controller, selfId = 2L)
        session.joinRoom("r1", 1L)
        runCurrent()
        advanceTimeBy(10_000L)

        assertEquals(1, controller.actions.count { it == "play:5" })
        session.stop()
    }

    @Test
    fun `房间不可加入时返回失败且不启动`() = runTest {
        val repo = FakeRepository().apply { checkJoinable = false }
        val session = session(repo, TestController())

        assertTrue(session.joinRoom("r1", 1L).isFailure)
        assertFalse(session.state.value.active)
    }

    @Test
    fun `服务端不在房间时退出并提示`() = runTest {
        val repo = FakeRepository().apply { status = Result.success(RoomStatus(false, null)) }
        val session = session(repo, TestController(), selfId = 2L)
        session.joinRoom("r1", 1L)
        runCurrent()

        assertFalse(session.state.value.active)
        assertEquals("房间已结束", session.state.value.message)
    }

    @Test
    fun `连续失败达到上限后退出并停止轮询`() = runTest {
        val repo = FakeRepository().apply { playback = Result.failure(java.io.IOException("断网")) }
        val session = session(repo, TestController(), selfId = 2L)
        session.joinRoom("r1", 1L)
        advanceTimeBy(30_000L)

        assertFalse(session.state.value.active)
        val calls = repo.playbackCalls
        advanceTimeBy(30_000L)
        assertEquals(calls, repo.playbackCalls)
    }

    @Test
    fun `房主离开调用结束接口，客机离开不调用`() = runTest {
        val hostRepo = FakeRepository()
        val host = session(hostRepo, TestController())
        host.createRoom(listOf(1L))
        host.leave()
        assertEquals(1, hostRepo.endCalls)

        val guestRepo = FakeRepository()
        val guest = session(guestRepo, TestController(), selfId = 2L)
        guest.joinRoom("r1", 1L)
        guest.leave()
        assertEquals(0, guestRepo.endCalls)
        assertFalse(guest.state.value.active)
    }
}
