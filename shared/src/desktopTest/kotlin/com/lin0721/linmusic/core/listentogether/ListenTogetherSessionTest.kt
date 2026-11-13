package com.lin0721.linmusic.core.listentogether

import com.lin0721.linmusic.core.listentogether.data.ListenTogetherRepository
import com.lin0721.linmusic.core.listentogether.domain.ListenTogetherRoom
import com.lin0721.linmusic.core.listentogether.domain.PlayCommand
import com.lin0721.linmusic.core.listentogether.domain.RoomCheck
import com.lin0721.linmusic.core.listentogether.domain.RoomCommand
import com.lin0721.linmusic.core.listentogether.domain.RoomPlayback
import com.lin0721.linmusic.core.listentogether.domain.RoomStatus
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.podcast.ui.FakePlaybackController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class TestController : PlaybackController by FakePlaybackController() {
    val nowPlayingFlow = MutableStateFlow<NowPlaying?>(null)
    val playWhenReadyFlow = MutableStateFlow(false)

    // 置 false 模拟起播缓冲
    val isPlayingFlow = MutableStateFlow(true)
    val positionFlow = MutableStateFlow(0L)
    val queueFlow = MutableStateFlow<List<QueueItem>>(emptyList())
    val actions = mutableListOf<String>()

    override val nowPlaying: StateFlow<NowPlaying?> = nowPlayingFlow
    override val playWhenReady: StateFlow<Boolean> = playWhenReadyFlow
    override val isPlaying: StateFlow<Boolean> = isPlayingFlow
    override val currentPosition: StateFlow<Long> = positionFlow
    override val queue: StateFlow<List<QueueItem>> = queueFlow

    override fun pause() { actions += "pause" }
    override fun resume() { actions += "resume" }
    override fun seekTo(positionMs: Long) { actions += "seek:$positionMs" }
    override fun appendToQueue(items: List<QueueItem>) {
        queueFlow.value = queueFlow.value + items
    }
    override fun playAtIndex(index: Int) { actions += "play:${queueFlow.value[index].songId}" }
    override fun playQueue(items: List<QueueItem>, startIndex: Int, playContext: String?, source: PlaySource?, startPositionMs: Long) {
        queueFlow.value = items
        actions += "play:${items[startIndex].songId}" + if (startPositionMs > 0L) "@$startPositionMs" else ""
    }
}

internal class FakeRepository : ListenTogetherRepository {
    var status: Result<RoomStatus> = Result.success(RoomStatus(true, null))
    var playback: Result<RoomPlayback?> = Result.success(null)
    var acceptCommand = true
    var checkJoinable = true
    val commands = mutableListOf<PlayCommand>()
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
    override fun reportCommand(roomId: String, command: PlayCommand): Flow<Result<Boolean>> {
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
            scope = backgroundScope,
            playerDispatcher = dispatcher,
            now = { testScheduler.currentTime }
        )
    }

    // 模拟播放推进：播放中每秒进度加一秒
    private fun TestScope.tickPosition(controller: TestController) {
        backgroundScope.launch {
            while (true) {
                delay(1_000L)
                if (controller.playWhenReadyFlow.value) controller.positionFlow.value += 1_000L
            }
        }
    }

    private fun roomCommand(song: Long, seq: Long, playing: Boolean = true, progress: Long = 0L, from: Long? = 1L) =
        RoomPlayback(listOf(song), RoomCommand("GOTO", song, progress, playing, seq, from))

    @Test
    fun `房主建房后只上报当前歌曲，不推送本地队列`() = runTest {
        val repo = FakeRepository()
        val controller = TestController().apply {
            queueFlow.value = listOf(item(1), item(2))
            nowPlayingFlow.value = NowPlaying("1", "歌1", "手", null)
            playWhenReadyFlow.value = true
        }
        val session = session(repo, controller)

        assertTrue(session.createRoom(listOf(1L, 2L)).isSuccess)
        runCurrent()

        assertTrue(repo.playlists.isEmpty())
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
    fun `客机收到新歌曲指令后追加并从指定进度播放`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(roomCommand(5L, seq = 100L, progress = 2_000L)) }
        val controller = TestController().apply { playWhenReadyFlow.value = true }
        val session = session(repo, controller, selfId = 2L)

        assertTrue(session.joinRoom("r1", 1L).isSuccess)
        runCurrent()

        assertEquals(listOf("play:5@2000"), controller.actions)
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
    fun `自己发出的指令回声不跟随`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(roomCommand(5L, seq = 100L, from = 2L)) }
        val controller = TestController()
        val session = session(repo, controller, selfId = 2L)
        session.joinRoom("r1", 1L)
        runCurrent()

        assertTrue(controller.actions.isEmpty())
        session.stop()
    }

    @Test
    fun `房主开启成员控制时跟随成员切歌`() = runTest {
        val repo = FakeRepository()
        val controller = TestController().apply {
            queueFlow.value = listOf(item(1L))
            nowPlayingFlow.value = NowPlaying("1", "歌1", "手", null)
            playWhenReadyFlow.value = true
        }
        val session = session(repo, controller, selfId = 1L)
        session.createRoom(listOf(1L))
        runCurrent()

        repo.playback = Result.success(roomCommand(5L, seq = 200L, from = 2L))
        advanceTimeBy(3_100L)

        assertTrue(controller.actions.contains("play:5"))
        session.stop()
    }

    @Test
    fun `房主关闭成员控制时不跟随并重报自身状态`() = runTest {
        val repo = FakeRepository()
        val controller = TestController().apply {
            queueFlow.value = listOf(item(1L))
            nowPlayingFlow.value = NowPlaying("1", "歌1", "手", null)
            playWhenReadyFlow.value = true
        }
        val session = session(repo, controller, selfId = 1L)
        session.createRoom(listOf(1L))
        runCurrent()
        session.setAllowMemberControl(false)
        val before = repo.commands.size

        repo.playback = Result.success(roomCommand(5L, seq = 200L, from = 2L))
        advanceTimeBy(3_100L)

        assertFalse(controller.actions.contains("play:5"))
        val override = repo.commands.drop(before).last()
        assertEquals("GOTO", override.type.wire)
        assertEquals(1L, override.songId)
        session.stop()
    }

    @Test
    fun `成员跟随后不回报，之后本地切歌会上报`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(roomCommand(5L, seq = 100L)) }
        val controller = TestController().apply { playWhenReadyFlow.value = true }
        tickPosition(controller)
        val session = session(repo, controller, selfId = 2L)
        session.joinRoom("r1", 1L)
        runCurrent()
        controller.nowPlayingFlow.value = NowPlaying("5", "歌5", "手", null)
        advanceTimeBy(6_100L)
        assertTrue(repo.commands.isEmpty())

        controller.nowPlayingFlow.value = NowPlaying("9", "歌9", "手", null)
        runCurrent()

        assertEquals(9L, repo.commands.last().songId)
        assertEquals(5L, repo.commands.last().formerSongId)
        session.stop()
    }

    @Test
    fun `跟随切歌后缓冲期间进度停滞不误报拖动`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(roomCommand(5L, seq = 100L, progress = 60_000L)) }
        val controller = TestController().apply { playWhenReadyFlow.value = true }
        val session = session(repo, controller, selfId = 2L)
        session.joinRoom("r1", 1L)
        runCurrent()
        controller.nowPlayingFlow.value = NowPlaying("5", "歌5", "手", null)
        controller.positionFlow.value = 60_000L
        controller.isPlayingFlow.value = false
        advanceTimeBy(9_100L)
        controller.isPlayingFlow.value = true
        tickPosition(controller)
        advanceTimeBy(6_100L)

        assertTrue(repo.commands.isEmpty())
        session.stop()
    }

    @Test
    fun `房间没有队列时客机仍跟随房主指令且不提前上报`() = runTest {
        val repo = FakeRepository().apply {
            playback = Result.success(RoomPlayback(emptyList(), RoomCommand("GOTO", 5L, 3_000L, true, 100L, 1L)))
        }
        val controller = TestController().apply {
            nowPlayingFlow.value = NowPlaying("9", "歌9", "手", null)
            playWhenReadyFlow.value = true
        }
        val session = session(repo, controller, selfId = 2L)
        session.joinRoom("r1", 1L)
        runCurrent()

        assertEquals(listOf("play:5@3000"), controller.actions)
        assertTrue(repo.commands.none { it.songId == 9L })
        assertTrue(session.state.value.roomSongIds.isEmpty())
        session.stop()
    }

    @Test
    fun `成员进房时不用本机歌曲覆盖房间`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(roomCommand(5L, seq = 100L)) }
        val controller = TestController().apply {
            nowPlayingFlow.value = NowPlaying("9", "歌9", "手", null)
            playWhenReadyFlow.value = true
        }
        val session = session(repo, controller, selfId = 2L)
        session.joinRoom("r1", 1L)
        runCurrent()

        assertTrue(repo.commands.none { it.songId == 9L })
        session.stop()
    }

    @Test
    fun `点歌追加到房间队列末尾并去重`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(RoomPlayback(listOf(1L, 2L), null)) }
        val session = session(repo, TestController(), selfId = 2L)
        session.joinRoom("r1", 1L)
        runCurrent()

        assertEquals(1, session.addToRoomQueue(listOf(2L, 3L)).getOrNull())
        assertEquals(listOf(1L, 2L, 3L), repo.playlists.last())
        assertEquals(0, session.addToRoomQueue(listOf(1L)).getOrNull())
        session.stop()
    }

    @Test
    fun `他人新增的房间曲目追加到本地队列末尾`() = runTest {
        val repo = FakeRepository().apply { playback = Result.success(RoomPlayback(listOf(1L, 2L), null)) }
        val controller = TestController().apply { queueFlow.value = listOf(item(1L)) }
        val session = session(repo, controller, selfId = 2L)
        session.joinRoom("r1", 1L)
        runCurrent()
        assertEquals(listOf(1L), controller.queueFlow.value.map { it.songId })

        repo.playback = Result.success(RoomPlayback(listOf(1L, 2L, 3L), null))
        advanceTimeBy(3_100L)

        assertEquals(listOf(1L, 3L), controller.queueFlow.value.map { it.songId })
        session.stop()
    }

    @Test
    fun `成员退出后接回时跳过该房间，再次加入后可接回`() = runTest {
        val room = com.lin0721.linmusic.core.listentogether.domain.ListenTogetherRoom("r1", 1L, emptyList())
        val repo = FakeRepository().apply { status = Result.success(RoomStatus(true, room)) }
        val session = session(repo, TestController(), selfId = 2L)
        session.joinRoom("r1", 1L)
        runCurrent()
        session.leave()

        assertFalse(session.resumeExisting())
        assertFalse(session.state.value.active)

        session.joinRoom("r1", 1L)
        runCurrent()
        session.stop()
        assertTrue(session.resumeExisting())
        session.stop()
    }

    @Test
    fun `房主本地队列变化不改动房间队列`() = runTest {
        val repo = FakeRepository()
        val controller = TestController().apply {
            queueFlow.value = listOf(item(1L))
            nowPlayingFlow.value = NowPlaying("1", "歌1", "手", null)
            playWhenReadyFlow.value = true
        }
        val session = session(repo, controller, selfId = 1L)
        session.createRoom(listOf(1L))
        runCurrent()

        controller.queueFlow.value = listOf(item(1L), item(8L), item(9L))
        advanceTimeBy(6_100L)

        assertTrue(repo.playlists.isEmpty())
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
