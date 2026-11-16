package com.lin0721.linmusic.feature.message.ui

import com.lin0721.linmusic.core.listentogether.domain.RoomCheck
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.message.FakeListenTogetherRepository
import com.lin0721.linmusic.feature.message.FakeMessageRepository
import com.lin0721.linmusic.feature.message.FakePlayerRepository
import com.lin0721.linmusic.feature.message.FakeProfileRepository
import com.lin0721.linmusic.feature.message.MutableFakePlaybackController
import com.lin0721.linmusic.feature.message.createTestReadPreferences
import com.lin0721.linmusic.feature.message.createTestUserPreferences
import com.lin0721.linmusic.feature.message.domain.ListenTogetherInviteMessage
import com.lin0721.linmusic.feature.message.domain.MessagePage
import com.lin0721.linmusic.feature.message.domain.MessageUser
import com.lin0721.linmusic.feature.message.loginAs
import com.lin0721.linmusic.feature.message.testMessageUser
import com.lin0721.linmusic.feature.message.testPrivateMessage
import com.lin0721.linmusic.feature.podcast.ui.testResourceProvider
import com.lin0721.linmusic.feature.profile.domain.ProfileUserInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivateChatViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.build(
        messageRepository: FakeMessageRepository = FakeMessageRepository(),
        profileRepository: FakeProfileRepository = FakeProfileRepository(),
        playerRepository: FakePlayerRepository = FakePlayerRepository(),
        playbackController: MutableFakePlaybackController = MutableFakePlaybackController(),
        unreadMonitor: PrivateUnreadMonitor? = null,
        listenTogetherRepository: FakeListenTogetherRepository = FakeListenTogetherRepository()
    ): Triple<PrivateChatViewModel, com.lin0721.linmusic.core.auth.UserPreferences, MutableFakePlaybackController> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val userPreferences = createTestUserPreferences()
        val vm = PrivateChatViewModel(
            messageRepository = messageRepository,
            userPreferences = userPreferences,
            resourceProvider = testResourceProvider,
            playerRepository = playerRepository,
            playbackController = playbackController,
            profileRepository = profileRepository,
            unreadMonitor = unreadMonitor
                ?: PrivateUnreadMonitor(messageRepository, userPreferences, createTestReadPreferences(), backgroundScope),
            readPreferences = createTestReadPreferences(),
            listenTogetherRepository = listenTogetherRepository
        )
        return Triple(vm, userPreferences, playbackController)
    }

    @Test
    fun `聊天分页去重并按时间与id升序排列，进入会话同时刷新未读监控`() = runTest {
        val repo = FakeMessageRepository()
        val msgCalls = ArrayDeque<Result<MessagePage<com.lin0721.linmusic.feature.message.domain.PrivateMessage>>>(
            listOf(
                Result.success(
                    MessagePage(
                        items = listOf(
                            testPrivateMessage(id = 3L, time = 3000L, fromUserId = 10L, toUserId = 20L),
                            testPrivateMessage(id = 2L, time = 2000L, fromUserId = 20L, toUserId = 10L)
                        ),
                        hasMore = true,
                        nextCursor = 2000L
                    )
                ),
                Result.success(
                    MessagePage(
                        items = listOf(
                            // 包含重复 id=2 与更早消息 id=1、同时间戳 id=4
                            testPrivateMessage(id = 2L, time = 2000L, fromUserId = 20L, toUserId = 10L),
                            testPrivateMessage(id = 4L, time = 1000L, fromUserId = 10L, toUserId = 20L),
                            testPrivateMessage(id = 1L, time = 1000L, fromUserId = 20L, toUserId = 10L)
                        ),
                        hasMore = false,
                        nextCursor = 1000L
                    )
                )
            )
        )
        repo.historyHandler = { _, _, _ -> msgCalls.removeFirst() }
        val userPreferences = createTestUserPreferences().apply { loginAs(10L) }
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val monitor = PrivateUnreadMonitor(repo, userPreferences, createTestReadPreferences(), backgroundScope, pollIntervalMs = 60_000L)
        val vm = PrivateChatViewModel(
            messageRepository = repo,
            userPreferences = userPreferences,
            resourceProvider = testResourceProvider,
            playerRepository = FakePlayerRepository(),
            playbackController = MutableFakePlaybackController(),
            profileRepository = FakeProfileRepository(),
            unreadMonitor = monitor,
            readPreferences = createTestReadPreferences(),
            listenTogetherRepository = FakeListenTogetherRepository()
        )

        vm.open(userId = 20L)
        runCurrent()

        assertEquals(ChatState.Ready, vm.state.value)
        assertEquals(listOf(2L, 3L), vm.messages.value.map { it.id })
        assertEquals(0L, repo.historyCalls.first().cursor)
        assertTrue(repo.conversationCalls.isNotEmpty())

        vm.loadMore()
        runCurrent()

        assertEquals(listOf(0L, 2000L), repo.historyCalls.map { it.cursor })
        assertEquals(listOf(1L, 4L, 2L, 3L), vm.messages.value.map { it.id })
        assertFalse(vm.hasMore.value)
        assertEquals("好友20", vm.peerUser.value?.nickname)
    }

    @Test
    fun `连续同发送者消息按5分钟间隔分组与显示时间分隔`() {
        val self = 10L
        val peer = 20L
        val base = 1_000_000L
        val messages = listOf(
            testPrivateMessage(id = 1L, time = base, fromUserId = peer, toUserId = self),
            testPrivateMessage(id = 2L, time = base + 60_000L, fromUserId = peer, toUserId = self),
            testPrivateMessage(id = 3L, time = base + 120_000L, fromUserId = self, toUserId = peer),
            testPrivateMessage(id = 4L, time = base + 120_000L + PRIVATE_CHAT_TIME_GAP_MS, fromUserId = self, toUserId = peer)
        )

        val items = groupPrivateMessages(messages, isMine = { it.toUserId == peer })

        assertEquals(4, items.size)
        // 第1条：组头 + 时间分隔，非组尾
        assertTrue(items[0].showTimeDivider)
        assertTrue(items[0].isGroupStart)
        assertFalse(items[0].isGroupEnd)
        assertFalse(items[0].isMine)
        // 第2条：同发送者组尾
        assertFalse(items[1].showTimeDivider)
        assertFalse(items[1].isGroupStart)
        assertTrue(items[1].isGroupEnd)
        // 第3条：切换为自己发送，独立成组（第4条触发了时间分隔）
        assertFalse(items[2].showTimeDivider)
        assertTrue(items[2].isGroupStart)
        assertTrue(items[2].isGroupEnd)
        assertTrue(items[2].isMine)
        // 第4条：>= 5分钟触发时间分隔，重新成为组头与组尾
        assertTrue(items[3].showTimeDivider)
        assertTrue(items[3].isGroupStart)
        assertTrue(items[3].isGroupEnd)
    }

    @Test
    fun `历史消息缺对方资料时从ProfileRepository兜底补全`() = runTest {
        val repo = FakeMessageRepository()
        repo.historyHandler = { _, _, _ ->
            Result.success(
                MessagePage(
                    items = listOf(testPrivateMessage(id = 1L, time = 100L, fromUser = null, toUser = null)),
                    hasMore = false,
                    nextCursor = 100L
                )
            )
        }
        val profileRepo = FakeProfileRepository().apply {
            userDetailHandler = { uid ->
                Result.success(
                    ProfileUserInfo(
                        uid = uid,
                        nickname = "补全昵称",
                        avatarUrl = "http://avatar/$uid",
                        signature = "",
                        level = 1,
                        followsCount = 0,
                        followedsCount = 0,
                        playlistCount = 0,
                        isFollowedByMe = false
                    )
                )
            }
        }
        val (vm, prefs, _) = build(messageRepository = repo, profileRepository = profileRepo)
        prefs.loginAs(10L)

        vm.open(20L)
        advanceUntilIdle()

        assertEquals(listOf(20L), profileRepo.detailCalls)
        assertEquals(MessageUser(20L, "补全昵称", "http://avatar/20"), vm.peerUser.value)

        // 切换到另一位同样缺少资料的好友时也应重新触发资料补全
        vm.open(30L)
        advanceUntilIdle()

        assertEquals(listOf(20L, 30L), profileRepo.detailCalls)
        assertEquals(MessageUser(30L, "补全昵称", "http://avatar/30"), vm.peerUser.value)
    }

    @Test
    fun `按会话隔离草稿，发送文本后清空草稿并重拉最新一页`() = runTest {
        val repo = FakeMessageRepository()
        val (vm, prefs, _) = build(messageRepository = repo)
        prefs.loginAs(10L)

        vm.open(20L)
        advanceUntilIdle()
        vm.updateDraft("给20的草稿")

        vm.open(30L)
        advanceUntilIdle()
        assertEquals("", vm.draft.value)
        vm.updateDraft("给30的草稿")

        vm.open(20L)
        advanceUntilIdle()
        assertEquals("给20的草稿", vm.draft.value)

        val historyCountBefore = repo.historyCalls.size
        vm.sendDraft()
        advanceUntilIdle()

        assertEquals(listOf(FakeMessageRepository.SendTextCall(20L, "给20的草稿")), repo.sendTextCalls)
        assertEquals("", vm.draft.value)
        assertEquals(historyCountBefore + 1, repo.historyCalls.size)
        assertEquals(0L, repo.historyCalls.last().cursor)
    }

    @Test
    fun `分享当前歌曲仅允许网易云曲目，排除本地与外部平台与播客`() = runTest {
        val repo = FakeMessageRepository()
        val (vm, prefs, player) = build(messageRepository = repo)
        prefs.loginAs(10L)
        vm.open(20L)
        advanceUntilIdle()

        // 1. 本地曲目（负数）不可分享
        player.mutableNowPlaying.value = NowPlaying("-10", "本地歌", "歌手", null)
        runCurrent()
        assertNull(vm.shareableNowPlaying.value)

        // 2. 外部平台曲目不可分享
        player.mutableQueueItem.value = QueueItem(
            songId = 555L,
            title = "外部歌",
            artist = "歌手",
            coverUrl = "",
            external = ExternalTrack("x1", "外部歌", "歌手", platform = MusicPlatform.QQ)
        )
        player.mutableNowPlaying.value = NowPlaying("555", "外部歌", "歌手", null)
        runCurrent()
        assertNull(vm.shareableNowPlaying.value)

        // 3. 播客不可分享
        player.mutableQueueItem.value = QueueItem(songId = 556L, title = "播客", artist = "主播", coverUrl = "", radioId = 99L)
        player.mutableNowPlaying.value = NowPlaying("556", "播客", "主播", null)
        runCurrent()
        assertNull(vm.shareableNowPlaying.value)

        // 4. 正常网易云曲目可分享
        player.mutableQueueItem.value = QueueItem(songId = 777L, title = "晴天", artist = "周杰伦", coverUrl = "http://c")
        player.mutableNowPlaying.value = NowPlaying("777", "晴天", "周杰伦", "http://c")
        runCurrent()
        assertEquals("777", vm.shareableNowPlaying.value?.mediaId)

        vm.sendCurrentSong()
        advanceUntilIdle()
        assertEquals(
            listOf(FakeMessageRepository.SendSongCall(20L, 777L, "分享单曲：晴天")),
            repo.sendSongCalls
        )
    }

    @Test
    fun `点击歌曲卡片调用PlayerRepository并交给PlaybackController播放`() = runTest {
        val playerRepo = FakePlayerRepository().apply {
            songDetailHandler = { id ->
                Result.success(
                    Track(
                        id = id,
                        name = "稻香",
                        ar = listOf(Artist(name = "周杰伦")),
                        al = Album(picUrl = "http://pic")
                    )
                )
            }
        }
        val (vm, prefs, controller) = build(playerRepository = playerRepo)
        prefs.loginAs(10L)

        vm.playSong(888L)
        advanceUntilIdle()

        assertEquals(1, controller.queueCalls.size)
        val call = controller.queueCalls.single()
        assertEquals("私信", call.playContext)
        assertEquals(listOf(QueueItem(888L, "稻香", "周杰伦", "http://pic")), call.items)

        playerRepo.songDetailHandler = { Result.failure(IllegalStateException("fail")) }
        val toastDeferred = async { vm.toastEvent.first() }
        runCurrent()
        vm.playSong(889L)
        advanceUntilIdle()
        assertEquals("这首歌取不到详情，暂时播不了", toastDeferred.await())
    }

    @Test
    fun `未登录打开会话进入Error状态`() = runTest {
        val (vm, _, _) = build()
        vm.open(20L)
        advanceUntilIdle()

        assertEquals(ChatState.Error("请先登录"), vm.state.value)
    }

    @Test
    fun `历史里对方有昵称但缺头像时用用户详情补齐头像且保留昵称`() = runTest {
        val repo = FakeMessageRepository()
        repo.historyHandler = { _, _, _ ->
            Result.success(
                MessagePage(
                    items = listOf(
                        testPrivateMessage(
                            id = 1L,
                            time = 100L,
                            fromUserId = 20L,
                            toUserId = 10L,
                            fromUser = MessageUser(20L, "Keshimusic", ""),
                            toUser = MessageUser(10L, "我", "http://a/10.jpg")
                        )
                    ),
                    hasMore = false,
                    nextCursor = 100L
                )
            )
        }
        val profileRepo = FakeProfileRepository().apply {
            userDetailHandler = { uid ->
                Result.success(
                    ProfileUserInfo(
                        uid = uid,
                        nickname = "详情昵称",
                        avatarUrl = "http://avatar/$uid",
                        signature = "",
                        level = 1,
                        followsCount = 0,
                        followedsCount = 0,
                        playlistCount = 0,
                        isFollowedByMe = false
                    )
                )
            }
        }
        val (vm, prefs, _) = build(messageRepository = repo, profileRepository = profileRepo)
        prefs.loginAs(10L)

        vm.open(20L)
        advanceUntilIdle()

        assertEquals(listOf(20L), profileRepo.detailCalls)
        assertEquals(MessageUser(20L, "Keshimusic", "http://avatar/20"), vm.peerUser.value)
    }

    @Test
    fun `其他入口向当前会话对象发出私信后聊天页重拉最新一页，发给别人不重拉`() = runTest {
        val repo = FakeMessageRepository()
        var historyCalls = 0
        repo.historyHandler = { _, _, _ ->
            historyCalls++
            Result.success(
                MessagePage(
                    items = listOf(testPrivateMessage(id = historyCalls.toLong(), time = historyCalls * 1000L)),
                    hasMore = false,
                    nextCursor = 0L
                )
            )
        }
        val prefsForMonitor = createTestUserPreferences()
        val monitor = PrivateUnreadMonitor(FakeMessageRepository(), prefsForMonitor, createTestReadPreferences(), backgroundScope)
        val (vm, prefs, _) = build(messageRepository = repo, unreadMonitor = monitor)
        prefs.loginAs(10L)

        vm.open(20L)
        advanceUntilIdle()
        assertEquals(1, historyCalls)

        monitor.notifyMessageSent(30L)
        advanceUntilIdle()
        assertEquals(1, historyCalls)

        monitor.notifyMessageSent(20L)
        advanceUntilIdle()
        assertEquals(2, historyCalls)
        assertEquals(2, vm.messages.value.size)
    }

    @Test
    fun `checkInvite 按 roomId 缓存 Joinable 和 Ended，Checking 防重复请求，Failed 允许重查`() = runTest {
        val listenRepo = FakeListenTogetherRepository()
        val deferred = CompletableDeferred<Result<RoomCheck>>()
        listenRepo.checkRoomHandler = { roomId ->
            when (roomId) {
                "room_joinable" -> deferred.await()
                "room_ended" -> Result.success(RoomCheck(joinable = false, message = "房间已结束"))
                "room_fail" -> Result.failure(IllegalStateException("网络异常"))
                else -> Result.success(RoomCheck(joinable = true, message = null))
            }
        }
        val (vm, _, _) = build(listenTogetherRepository = listenRepo)

        // 空串忽略
        vm.checkInvite("   ")
        advanceUntilIdle()
        assertTrue(listenRepo.checkRoomCalls.isEmpty())

        // Checking 中再次调用不会重复请求
        vm.checkInvite("room_joinable")
        assertEquals(InviteStatus.Checking, vm.inviteStatus.value["room_joinable"])
        vm.checkInvite("room_joinable")
        runCurrent()
        assertEquals(listOf("room_joinable"), listenRepo.checkRoomCalls)

        // 返回 Joinable 后再次调用复用缓存
        deferred.complete(Result.success(RoomCheck(joinable = true, message = null)))
        advanceUntilIdle()
        assertEquals(InviteStatus.Joinable, vm.inviteStatus.value["room_joinable"])
        vm.checkInvite("room_joinable")
        advanceUntilIdle()
        assertEquals(1, listenRepo.checkRoomCalls.count { it == "room_joinable" })

        // Ended 同样复用缓存
        vm.checkInvite("room_ended")
        advanceUntilIdle()
        assertEquals(InviteStatus.Ended, vm.inviteStatus.value["room_ended"])
        vm.checkInvite("room_ended")
        advanceUntilIdle()
        assertEquals(1, listenRepo.checkRoomCalls.count { it == "room_ended" })

        // Failed 允许重查并可恢复为 Joinable
        vm.checkInvite("room_fail")
        advanceUntilIdle()
        assertEquals(InviteStatus.Failed, vm.inviteStatus.value["room_fail"])
        listenRepo.checkRoomHandler = { Result.success(RoomCheck(joinable = true, message = null)) }
        vm.checkInvite("room_fail")
        advanceUntilIdle()
        assertEquals(InviteStatus.Joinable, vm.inviteStatus.value["room_fail"])
        assertEquals(2, listenRepo.checkRoomCalls.count { it == "room_fail" })
    }

    @Test
    fun `服务端占位消息转为系统提示并断开气泡分组，紧跟邀请时提示一起听已结束`() {
        val self = 10L
        val peer = 20L
        val placeholder = "当前版本无法显示该信息，请在应用市场下载最新版app"
        val invite = testPrivateMessage(id = 2L, time = 2_000L, fromUserId = peer, toUserId = self)
            .copy(listenInvite = ListenTogetherInviteMessage("room1", peer, "夜曲"))
        val messages = listOf(
            testPrivateMessage(id = 1L, time = 1_000L, fromUserId = peer, toUserId = self, text = placeholder),
            invite,
            testPrivateMessage(id = 3L, time = 3_000L, fromUserId = peer, toUserId = self, text = placeholder),
            testPrivateMessage(id = 4L, time = 4_000L, fromUserId = peer, toUserId = self)
        )

        val items = groupPrivateMessages(messages, isMine = { it.toUserId == peer })

        assertEquals("暂不支持显示该消息", items[0].systemNotice)
        assertNull(items[1].systemNotice)
        assertTrue(items[1].isGroupStart)
        assertTrue(items[1].isGroupEnd)
        assertEquals("一起听已结束", items[2].systemNotice)
        assertNull(items[3].systemNotice)
        assertTrue(items[3].isGroupStart)
    }

    @Test
    fun `recheckJoinableInvites 只重查进行中的房间，结束后变为 Ended，查询失败保持 Joinable`() = runTest {
        val listenRepo = FakeListenTogetherRepository()
        val (vm, _, _) = build(listenTogetherRepository = listenRepo)
        vm.checkInvite("room_a")
        advanceUntilIdle()
        listenRepo.checkRoomHandler = { Result.success(RoomCheck(joinable = false, message = null)) }
        vm.checkInvite("room_b")
        advanceUntilIdle()
        assertEquals(InviteStatus.Joinable, vm.inviteStatus.value["room_a"])
        assertEquals(InviteStatus.Ended, vm.inviteStatus.value["room_b"])

        listenRepo.checkRoomHandler = { Result.failure(IllegalStateException("网络异常")) }
        vm.recheckJoinableInvites()
        advanceUntilIdle()
        assertEquals(InviteStatus.Joinable, vm.inviteStatus.value["room_a"])
        assertEquals(1, listenRepo.checkRoomCalls.count { it == "room_b" })

        listenRepo.checkRoomHandler = { Result.success(RoomCheck(joinable = false, message = null)) }
        vm.recheckJoinableInvites()
        advanceUntilIdle()
        assertEquals(InviteStatus.Ended, vm.inviteStatus.value["room_a"])
        assertEquals(3, listenRepo.checkRoomCalls.count { it == "room_a" })
    }
}
