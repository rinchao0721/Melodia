package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanLinkRemoteCoordinatorTest {

    private class FakeTransport(
        override val localDeviceId: String = "desktop-1",
        override val localDeviceName: String = "MyDesktop",
        override val isHostDevice: Boolean = true
    ) : LanLinkTransport {
        val _enabled = MutableStateFlow(true)
        override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

        val _connectedDevice = MutableStateFlow<ConnectedPeerInfo?>(null)
        override val connectedDevice: StateFlow<ConnectedPeerInfo?> = _connectedDevice.asStateFlow()

        val _pairedDevices = MutableStateFlow<List<PairedLanDevice>>(emptyList())
        override val pairedDevices: StateFlow<List<PairedLanDevice>> = _pairedDevices.asStateFlow()

        private val _incoming = MutableSharedFlow<LanLinkIncomingMessage>(extraBufferCapacity = 32)
        override val incomingMessages: Flow<LanLinkIncomingMessage> = _incoming.asSharedFlow()

        val sentMessages = mutableListOf<LanLinkMessage>()
        val userMessages = mutableListOf<String>()

        override suspend fun sendMessage(message: LanLinkMessage): Boolean {
            sentMessages += message
            return true
        }

        override fun emitUserMessage(message: String) {
            userMessages += message
        }

        suspend fun receive(peer: ConnectedPeerInfo, message: LanLinkMessage) {
            _incoming.emit(LanLinkIncomingMessage(peer, message))
        }
    }

    private class FakeVolumeBridge : RemoteVolumeBridge {
        private val _volume = MutableStateFlow(60)
        override val volume: StateFlow<Int> = _volume.asStateFlow()

        private val _lastAudible = MutableStateFlow(60)
        override val lastAudibleVolume: StateFlow<Int> = _lastAudible.asStateFlow()

        override fun setVolume(percent: Int) {
            val clamped = percent.coerceIn(0, 100)
            _volume.value = clamped
            if (clamped > 0) _lastAudible.value = clamped
        }
    }

    private class FakePlaybackController : PlaybackController {
        override val isPlaying = MutableStateFlow(false)
        override val playWhenReady = MutableStateFlow(false)
        override val nowPlaying = MutableStateFlow<NowPlaying?>(null)
        override val currentPosition = MutableStateFlow(0L)
        override val duration = MutableStateFlow(0L)
        override val sleepTimerRemaining = MutableStateFlow(0L)
        override val playContext = MutableStateFlow<String?>(null)
        override val playSource = MutableStateFlow<PlaySource?>(null)
        override val currentIndex = MutableStateFlow(-1)
        override val playMode = MutableStateFlow(PlayMode.LIST_LOOP)
        override val queue = MutableStateFlow<List<QueueItem>>(emptyList())
        override val currentQueueItem = MutableStateFlow<QueueItem?>(null)
        override val previousQueueItem = MutableStateFlow<QueueItem?>(null)
        override val nextQueueItem = MutableStateFlow<QueueItem?>(null)

        var pauseCount = 0
        var resumeCount = 0
        var nextCount = 0
        var lastSeekMs: Long? = null
        var lastPlayQueue: List<QueueItem>? = null
        var lastPlayAtIndex: Int? = null
        var lastPlayNextNow: Pair<QueueItem, Long>? = null

        override suspend fun initController() = Unit

        override fun playQueue(
            items: List<QueueItem>,
            startIndex: Int,
            playContext: String?,
            source: PlaySource?,
            startPositionMs: Long
        ) {
            lastPlayQueue = items
            queue.value = items
            currentIndex.value = startIndex
            currentQueueItem.value = items.getOrNull(startIndex)
            this.playContext.value = playContext
            this.playSource.value = source
            isPlaying.value = true
            playWhenReady.value = true
            nowPlaying.value = items.getOrNull(startIndex)?.let {
                NowPlaying(it.songId.toString(), it.title, it.artist, it.coverUrl)
            }
        }

        override fun playAudio(
            songId: Long,
            url: String,
            title: String,
            artist: String,
            coverUrl: String,
            startPosition: Long,
            playContext: String?
        ) = Unit

        override fun addToPlayNext(items: List<QueueItem>) = Unit
        override fun appendToQueue(items: List<QueueItem>) = Unit
        override fun extendQueue(source: PlaySource, items: List<QueueItem>) = Unit
        override fun playNext() { nextCount++ }
        override fun playPrevious() = Unit
        override fun skipToPrevious() = Unit
        override fun playAtIndex(index: Int) { lastPlayAtIndex = index }
        override fun removeFromQueue(index: Int) = Unit
        override fun moveInQueue(from: Int, to: Int) = Unit
        override fun clearQueue() = Unit
        override fun stopAndClearQueue() = Unit
        override fun toggleShuffle() = Unit
        override fun toggleRepeat() = Unit
        override fun rotatePlayMode() = Unit
        override fun pause() {
            pauseCount++
            isPlaying.value = false
            playWhenReady.value = false
        }

        override fun resume() {
            resumeCount++
            isPlaying.value = true
            playWhenReady.value = true
        }

        override fun togglePlayPause() {
            if (playWhenReady.value) pause() else resume()
        }

        override fun seekTo(positionMs: Long) {
            lastSeekMs = positionMs
            currentPosition.value = positionMs
        }

        override fun playNextNow(item: QueueItem, startPositionMs: Long) {
            lastPlayNextNow = item to startPositionMs
            queue.value = listOf(item)
            currentIndex.value = 0
            currentQueueItem.value = item
            currentPosition.value = startPositionMs
            isPlaying.value = true
            playWhenReady.value = true
            nowPlaying.value = NowPlaying(item.songId.toString(), item.title, item.artist, item.coverUrl)
        }

        override fun reloadCurrentTrack() = Unit
        override fun setSleepTimer(minutes: Int) = Unit
        override fun setPositionUpdateInterval(intervalMs: Long) = Unit
        override fun cancelPendingSkip(): Boolean = false
        override fun disableRoaming() = Unit
        override fun disableIntelligence() = Unit
        override fun startPersonalFm(items: List<QueueItem>) = Unit
        override fun disablePersonalFm() = Unit
        override fun trashCurrentFmSong() = Unit
        override fun switchPersonalFmMode(mode: PersonalFmMode) = Unit
    }

    @Test
    fun switchToRemoteAndBackAndDisconnectFallback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val track = QueueItem(songId = 101L, title = "晴天", artist = "周杰伦", coverUrl = "https://img/1.jpg")
        val local = FakePlaybackController().apply {
            queue.value = listOf(track)
            currentIndex.value = 0
            currentQueueItem.value = track
            nowPlaying.value = NowPlaying("101", "晴天", "周杰伦", "https://img/1.jpg")
            currentPosition.value = 42_000L
            isPlaying.value = true
            playWhenReady.value = true
        }
        val transport = FakeTransport()
        val volumeBridge = FakeVolumeBridge()
        var clock = 10_000L
        val coordinator = LanLinkRemoteCoordinator(
            localController = local,
            transport = transport,
            volumeBridge = volumeBridge,
            scope = backgroundScope,
            commandDispatcher = dispatcher,
            clockMs = { clock }
        )
        val emittedMessages = mutableListOf<String>()
        coordinator.start()
        backgroundScope.launch(dispatcher) {
            coordinator.messages.collect { emittedMessages += it }
        }
        runCurrent()

        // 未连接对端时切换失败并提示
        assertFalse(coordinator.switchToRemote())
        assertEquals(PlaybackTarget.Local, coordinator.activeTarget.value)

        // 连接后切到远端：暂停本机、发送 RemoteHandoffPlay（剥离 localUri）与 RemoteRequestSync
        val peer = ConnectedPeerInfo("phone-1", "Pixel 9")
        transport._connectedDevice.value = peer
        runCurrent()
        assertTrue(transport.sentMessages.any { it is LanLinkMessage.RemotePlaybackHello })
        transport.sentMessages.clear()

        assertTrue(coordinator.switchToRemote())
        runCurrent()
        assertEquals(PlaybackTarget.Remote(peer), coordinator.activeTarget.value)
        assertEquals(1, local.pauseCount)
        assertEquals(0, local.resumeCount)
        val handoff = transport.sentMessages.filterIsInstance<LanLinkMessage.RemoteHandoffPlay>().single()
        assertEquals("phone-1", handoff.targetDeviceId)
        assertEquals(101L, handoff.item.songId)
        assertEquals(42_000L, handoff.startPositionMs)
        assertTrue(transport.sentMessages.any { it == LanLinkMessage.RemoteRequestSync })

        // 对端回报无曲目时切回本机：应发送 RemoteActiveDevice(local) 且不调用 playNextNow
        transport.sentMessages.clear()
        assertTrue(coordinator.switchToLocal())
        runCurrent()
        assertEquals(PlaybackTarget.Local, coordinator.activeTarget.value)
        assertNull(local.lastPlayNextNow)
        assertTrue(
            transport.sentMessages.any {
                it is LanLinkMessage.RemoteActiveDevice && it.activeDeviceId == transport.localDeviceId
            }
        )

        // 对端有曲目时切回本机：应在本地接力起播并广播 RemoteActiveDevice
        clock = 20_000L
        assertTrue(coordinator.switchToRemote())
        runCurrent()
        val remoteItem = QueueItem(songId = 202L, title = "七里香", artist = "周杰伦", coverUrl = "https://img/2.jpg")
        transport.receive(
            peer,
            LanLinkMessage.RemoteQueueSync(queueVersion = 5L, items = listOf(remoteItem), currentIndex = 0)
        )
        transport.receive(
            peer,
            LanLinkMessage.RemotePlaybackState(
                isPlaying = true,
                playWhenReady = true,
                nowPlaying = RemoteNowPlaying("202", "七里香", "周杰伦", "https://img/2.jpg"),
                positionMs = 15_000L,
                sampledAtMs = clock,
                durationMs = 250_000L,
                currentIndex = 0,
                queueVersion = 5L
            )
        )
        runCurrent()
        transport.sentMessages.clear()
        assertTrue(coordinator.switchToLocal())
        runCurrent()
        assertEquals(PlaybackTarget.Local, coordinator.activeTarget.value)
        assertEquals(remoteItem.copy(localUri = null), local.lastPlayNextNow?.first)
        assertEquals(15_000L, local.lastPlayNextNow?.second)
        assertTrue(
            transport.sentMessages.any {
                it is LanLinkMessage.RemoteActiveDevice && it.activeDeviceId == transport.localDeviceId
            }
        )

        // 在远端控制期间断线：自动切回本机并提示「连接已断开」
        clock = 30_000L
        assertTrue(coordinator.switchToRemote())
        runCurrent()
        emittedMessages.clear()

        transport._connectedDevice.value = null
        runCurrent()
        assertEquals(PlaybackTarget.Local, coordinator.activeTarget.value)
        assertTrue(emittedMessages.contains("连接已断开"))
    }

    @Test
    fun playbackHelloTieBreakingActiveDeviceAndHandoffPlay() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val local = FakePlaybackController().apply {
            isPlaying.value = true
            playWhenReady.value = true
        }
        // 客户端（isHostDevice = false）在与桌面端（isHost = true）同时播放时应让位
        val transport = FakeTransport(localDeviceId = "phone-z", localDeviceName = "Pixel 9", isHostDevice = false)
        val volumeBridge = FakeVolumeBridge()
        var clock = 5_000L
        val coordinator = LanLinkRemoteCoordinator(
            localController = local,
            transport = transport,
            volumeBridge = volumeBridge,
            scope = backgroundScope,
            commandDispatcher = dispatcher,
            clockMs = { clock }
        )
        coordinator.start()
        runCurrent()

        val peer = ConnectedPeerInfo("desktop-a", "MyDesktop")
        transport._connectedDevice.value = peer
        runCurrent()
        transport.sentMessages.clear()

        // 对端也是播放中且对端为 Host：本机让位、暂停本机并切到 Remote(peer)
        transport.receive(
            peer,
            LanLinkMessage.RemotePlaybackHello(
                deviceId = peer.deviceId,
                deviceName = peer.deviceName,
                isPlaying = true,
                playWhenReady = true,
                hasTrack = true,
                lastActiveAtMs = 4_000L,
                isHost = true
            )
        )
        runCurrent()
        assertEquals(PlaybackTarget.Remote(peer), coordinator.activeTarget.value)
        assertEquals(1, local.pauseCount)
        assertTrue(transport.sentMessages.any { it == LanLinkMessage.RemoteRequestSync })

        // 收到面向本机的 RemoteHandoffPlay：切回本机并从指定进度接力播放
        val handoffItem = QueueItem(songId = 303L, title = "夜曲", artist = "周杰伦", coverUrl = "https://img/3.jpg")
        clock = 6_000L
        transport.receive(
            peer,
            LanLinkMessage.RemoteHandoffPlay(
                targetDeviceId = transport.localDeviceId,
                item = handoffItem,
                startPositionMs = 33_000L,
                epoch = 2L,
                timestampMs = clock
            )
        )
        runCurrent()
        assertEquals(PlaybackTarget.Local, coordinator.activeTarget.value)
        assertEquals(handoffItem to 33_000L, local.lastPlayNextNow)

        // 过时 epoch 的 RemoteActiveDevice 应被忽略
        transport.receive(
            peer,
            LanLinkMessage.RemoteActiveDevice(
                activeDeviceId = peer.deviceId,
                activeDeviceName = peer.deviceName,
                epoch = 1L,
                timestampMs = clock
            )
        )
        runCurrent()
        assertEquals(PlaybackTarget.Local, coordinator.activeTarget.value)

        // 更新的 epoch 宣布对端为活跃设备：暂停本机并切到 Remote(peer)
        clock = 7_000L
        transport.receive(
            peer,
            LanLinkMessage.RemoteActiveDevice(
                activeDeviceId = peer.deviceId,
                activeDeviceName = peer.deviceName,
                epoch = 3L,
                timestampMs = clock
            )
        )
        runCurrent()
        assertEquals(PlaybackTarget.Remote(peer), coordinator.activeTarget.value)
        assertFalse(local.playWhenReady.value)
    }

    @Test
    fun localDirectPlayTakeoverAndLocalOnlyTrackGating() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val local = FakePlaybackController()
        val transport = FakeTransport()
        val volumeBridge = FakeVolumeBridge()
        var clock = 1_000L
        val coordinator = LanLinkRemoteCoordinator(
            localController = local,
            transport = transport,
            volumeBridge = volumeBridge,
            scope = backgroundScope,
            commandDispatcher = dispatcher,
            clockMs = { clock }
        )
        val emittedMessages = mutableListOf<String>()
        coordinator.start()
        backgroundScope.launch(dispatcher) {
            coordinator.messages.collect { emittedMessages += it }
        }
        runCurrent()

        val peer = ConnectedPeerInfo("phone-1", "Pixel 9")
        transport._connectedDevice.value = peer
        runCurrent()

        // 无曲目时切到远端
        assertTrue(coordinator.switchToRemote())
        runCurrent()
        assertEquals(PlaybackTarget.Remote(peer), coordinator.activeTarget.value)

        // 超过抑制窗口后，本机直接从暂停起播应自动抢占为活跃设备并通知对端
        clock = 5_000L
        transport.sentMessages.clear()
        local.resume()
        runCurrent()
        assertEquals(PlaybackTarget.Local, coordinator.activeTarget.value)
        assertTrue(
            transport.sentMessages.any {
                it is LanLinkMessage.RemoteActiveDevice && it.activeDeviceId == transport.localDeviceId
            }
        )

        // 本机为本地文件曲目时拒绝切换到远程设备并提示
        val localOnly = QueueItem(
            songId = -1L,
            title = "Local.flac",
            artist = "Unknown",
            coverUrl = "",
            localUri = "file:///music/local.flac"
        )
        local.queue.value = listOf(localOnly)
        local.currentIndex.value = 0
        local.currentQueueItem.value = localOnly
        local.nowPlaying.value = NowPlaying("-1", "Local.flac", "Unknown", null)
        emittedMessages.clear()
        assertFalse(coordinator.switchToRemote())
        runCurrent()
        assertEquals(PlaybackTarget.Local, coordinator.activeTarget.value)
        assertTrue(emittedMessages.contains("本地歌曲无法在远程设备播放"))

        local.pause()
        runCurrent()
    }

    @Test
    fun incomingCommandsAndQueueSanitization() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val local = FakePlaybackController()
        val transport = FakeTransport(localDeviceName = "DesktopHost")
        val volumeBridge = FakeVolumeBridge()
        val clock = 1_000L
        val coordinator = LanLinkRemoteCoordinator(
            localController = local,
            transport = transport,
            volumeBridge = volumeBridge,
            scope = backgroundScope,
            commandDispatcher = dispatcher,
            clockMs = { clock }
        )
        coordinator.start()
        runCurrent()

        val peer = ConnectedPeerInfo("phone-2", "Galaxy S25")
        transport._connectedDevice.value = peer
        runCurrent()

        // 收到遥控命令与设置音量
        transport.receive(peer, LanLinkMessage.RemoteCommand(RemoteCommandAction.SET_VOLUME, volumePercent = 45))
        transport.receive(peer, LanLinkMessage.RemoteCommand(RemoteCommandAction.SEEK, positionMs = 3_400L))
        transport.receive(peer, LanLinkMessage.RemoteCommand(RemoteCommandAction.PLAY_AT_INDEX, index = 1))
        runCurrent()
        assertEquals(45, volumeBridge.volume.value)
        assertEquals(3_400L, local.lastSeekMs)
        assertEquals(1, local.lastPlayAtIndex)

        // 本机队列变化应剥离 localUri 后同步给对端
        transport.sentMessages.clear()
        local.queue.value = listOf(
            QueueItem(100L, "Song 1", "Artist 1", "cover", localUri = "file:///tmp/1.mp3")
        )
        runCurrent()
        val qSync = transport.sentMessages.filterIsInstance<LanLinkMessage.RemoteQueueSync>().last()
        assertNull(qSync.items.single().localUri)
    }

    @Test
    fun stateSnapshotIsThrottledDuringPlayback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val local = FakePlaybackController().apply {
            nowPlaying.value = NowPlaying("1", "Track", "Artist", null)
            duration.value = 100_000L
            isPlaying.value = true
            playWhenReady.value = true
        }
        val transport = FakeTransport()
        val volumeBridge = FakeVolumeBridge()
        var clock = 1_000L
        val coordinator = LanLinkRemoteCoordinator(
            localController = local,
            transport = transport,
            volumeBridge = volumeBridge,
            scope = backgroundScope,
            commandDispatcher = dispatcher,
            clockMs = { clock }
        )
        coordinator.start()
        runCurrent()

        val peer = ConnectedPeerInfo("phone-3", "Phone")
        transport._connectedDevice.value = peer
        runCurrent()
        transport.sentMessages.clear()

        // 300ms 后的小幅位置推进不应触发额外状态推送（受 ~1s 节流保护）
        clock = 1_300L
        local.currentPosition.value = 300L
        runCurrent()
        assertEquals(0, transport.sentMessages.filterIsInstance<LanLinkMessage.RemotePlaybackState>().size)

        // 超过 1s 节流阈值后应推送状态
        clock = 2_100L
        local.currentPosition.value = 1_100L
        runCurrent()
        assertEquals(1, transport.sentMessages.filterIsInstance<LanLinkMessage.RemotePlaybackState>().size)

        // 暂停本地播放使内部心跳协程退出
        local.pause()
        runCurrent()
    }
}
