package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.preferences.PreferencesStores
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class LanLinkKeepaliveStabilityTest {

    @Test
    fun byeMessageSerializesWithDiscriminatorAndDefaultReason() {
        val msg = LanLinkMessage.Bye(reason = "app_exit")
        val encoded = LanLinkJson.encodeToString<LanLinkMessage>(msg)
        assertTrue(encoded.contains("\"type\":\"bye\""))
        assertEquals(msg, LanLinkJson.decodeFromString<LanLinkMessage>(encoded))

        val minimalJson = """{"type":"bye"}"""
        val decoded = LanLinkJson.decodeFromString<LanLinkMessage>(minimalJson) as LanLinkMessage.Bye
        assertEquals("", decoded.reason)
    }

    @Test
    fun sessionReceivingByeInvokesCallbackAndExitsCleanly(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("melodia-lanlink-bye").toFile()
        try {
            val hostStore = PlaylistExternalTrackStore(PreferencesStores.get(File(tempDir, "host_ext.preferences_pb")))
            val clientStore = PlaylistExternalTrackStore(PreferencesStores.get(File(tempDir, "client_ext.preferences_pb")))
            val hostUsers = UserPreferences(PreferencesStores.get(File(tempDir, "host_user.preferences_pb")))
            val clientUsers = UserPreferences(PreferencesStores.get(File(tempDir, "client_user.preferences_pb")))
            hostUsers.upsertAccount(StoredAccount(uid = 3001L, nickname = "Host"))
            clientUsers.upsertAccount(StoredAccount(uid = 3001L, nickname = "Client"))

            val deviceKey = LanLinkCrypto.randomBytes(LanLinkCrypto.AES_KEY_BYTES)
            val sessionKey = LanLinkCrypto.deriveSessionKey(
                deviceKey = deviceKey,
                clientNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES),
                serverNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES)
            )

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { listener ->
                val hostAccept = async(Dispatchers.IO) { listener.accept() }
                val clientSocket = withTimeout(5_000L) {
                    Socket(InetAddress.getByName("127.0.0.1"), listener.localPort)
                }
                val hostSocket = hostAccept.await()

                val peerByeReason = CompletableDeferred<String>()
                val hostSession = LanLinkSession(
                    socket = hostSocket,
                    sessionKey = sessionKey,
                    isHost = true,
                    peerDeviceId = "client-1",
                    peerDeviceName = "Pixel",
                    store = hostStore,
                    userPreferences = hostUsers,
                    onUnpairedByPeer = {},
                    onSyncStateChanged = { _, _ -> }
                )
                val clientSession = LanLinkSession(
                    socket = clientSocket,
                    sessionKey = sessionKey,
                    isHost = false,
                    peerDeviceId = "host-1",
                    peerDeviceName = "Desktop",
                    store = clientStore,
                    userPreferences = clientUsers,
                    onUnpairedByPeer = {},
                    onSyncStateChanged = { _, _ -> },
                    onByeByPeer = { reason -> peerByeReason.complete(reason) }
                )

                // 关闭阶段的写失败由调用方吞掉，与宿主和客户端的处理一致
                val hostJob = launch(Dispatchers.IO) { runCatching { hostSession.run() } }
                val clientJob = launch(Dispatchers.IO) { runCatching { clientSession.run() } }

                // 确保握手完成后由桌面端发送 Bye 并关闭会话
                clientSession.sendMessage(LanLinkMessage.Ping(1_000L))
                // 等握手帧被双方读完，否则带未读数据关闭会触发 RST 吞掉 Bye
                delay(500L)
                hostSession.sendByeAndClose("app_exit")

                assertEquals("app_exit", withTimeout(5_000L) { peerByeReason.await() })
                withTimeout(5_000L) {
                    hostJob.join()
                    clientJob.join()
                }
                assertTrue(hostSocket.isClosed)
                assertTrue(clientSocket.isClosed)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun sessionSendFailureImmediatelyClosesSocket(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("melodia-lanlink-sendfail").toFile()
        try {
            val hostStore = PlaylistExternalTrackStore(PreferencesStores.get(File(tempDir, "host_ext.preferences_pb")))
            val clientStore = PlaylistExternalTrackStore(PreferencesStores.get(File(tempDir, "client_ext.preferences_pb")))
            val hostUsers = UserPreferences(PreferencesStores.get(File(tempDir, "host_user.preferences_pb")))
            val clientUsers = UserPreferences(PreferencesStores.get(File(tempDir, "client_user.preferences_pb")))
            hostUsers.upsertAccount(StoredAccount(uid = 4001L, nickname = "Host"))
            clientUsers.upsertAccount(StoredAccount(uid = 4001L, nickname = "Client"))

            val deviceKey = LanLinkCrypto.randomBytes(LanLinkCrypto.AES_KEY_BYTES)
            val sessionKey = LanLinkCrypto.deriveSessionKey(
                deviceKey = deviceKey,
                clientNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES),
                serverNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES)
            )

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { listener ->
                val hostAccept = async(Dispatchers.IO) { listener.accept() }
                val clientSocket = withTimeout(5_000L) {
                    Socket(InetAddress.getByName("127.0.0.1"), listener.localPort)
                }
                val hostSocket = hostAccept.await()

                val hostSession = LanLinkSession(
                    socket = hostSocket,
                    sessionKey = sessionKey,
                    isHost = true,
                    peerDeviceId = "client-1",
                    peerDeviceName = "Pixel",
                    store = hostStore,
                    userPreferences = hostUsers,
                    onUnpairedByPeer = {},
                    onSyncStateChanged = { _, _ -> }
                )
                val clientSession = LanLinkSession(
                    socket = clientSocket,
                    sessionKey = sessionKey,
                    isHost = false,
                    peerDeviceId = "host-1",
                    peerDeviceName = "Desktop",
                    store = clientStore,
                    userPreferences = clientUsers,
                    onUnpairedByPeer = {},
                    onSyncStateChanged = { _, _ -> }
                )

                val hostJob = launch(Dispatchers.IO) { runCatching { hostSession.run() } }
                val clientJob = launch(Dispatchers.IO) { runCatching { clientSession.run() } }

                // 发送一帧确认双方已进入读循环，再主动关闭客户端输出流制造写失败
                clientSession.sendMessage(LanLinkMessage.Ping(123L))
                clientSocket.shutdownOutput()
                assertFalse(clientSocket.isClosed)

                val sendError = runCatching {
                    clientSession.sendMessage(LanLinkMessage.RemoteRequestSync)
                }.exceptionOrNull()
                assertTrue(sendError is IOException)
                assertTrue(clientSocket.isClosed)

                withTimeout(5_000L) {
                    clientJob.join()
                    hostJob.join()
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun wifiReconnectDebouncerCoalescesBurstsAndSupportsCancel() = runTest {
        var triggerCount = 0
        val debouncer = LanLinkWifiReconnectDebouncer(
            scope = backgroundScope,
            debounceMs = WIFI_RECONNECT_DEBOUNCE_MS,
            onTriggerReconnect = { triggerCount++ }
        )

        // 短时间多次网络就绪回调仅在最后一次之后 1.5s 触发一次重连
        debouncer.onAvailable()
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(0, triggerCount)

        debouncer.onAvailable()
        advanceTimeBy(1_400L)
        runCurrent()
        assertEquals(0, triggerCount)

        advanceTimeBy(150L)
        runCurrent()
        assertEquals(1, triggerCount)

        // 断网取消后，等待窗口结束不再触发重连
        debouncer.onAvailable()
        advanceTimeBy(800L)
        debouncer.cancel()
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(1, triggerCount)
    }

    @Test
    fun coordinatorResetsRemoteStateWhenSessionSequenceChanges() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val local = MinimalLocalController()
        val transport = SequenceFakeTransport()
        val volumeBridge = MinimalVolumeBridge()
        val coordinator = LanLinkRemoteCoordinator(
            localController = local,
            transport = transport,
            volumeBridge = volumeBridge,
            scope = backgroundScope,
            commandDispatcher = dispatcher,
            clockMs = { 10_000L }
        )
        coordinator.start()
        runCurrent()

        val peer = ConnectedPeerInfo("desktop-1", "MyDesktop")
        transport._connectedDevice.value = peer
        transport._sessionSequence.value = 1L
        runCurrent()

        assertTrue(coordinator.switchToRemote())
        runCurrent()
        assertEquals(PlaybackTarget.Remote(peer), coordinator.activeTarget.value)

        val remoteItem = QueueItem(songId = 505L, title = "以父之名", artist = "周杰伦", coverUrl = "https://img/5.jpg")
        transport.receive(peer, LanLinkMessage.RemoteQueueSync(queueVersion = 2L, items = listOf(remoteItem), currentIndex = 0))
        transport.receive(
            peer,
            LanLinkMessage.RemotePlaybackState(
                isPlaying = true,
                playWhenReady = true,
                nowPlaying = RemoteNowPlaying("505", "以父之名", "周杰伦", "https://img/5.jpg"),
                positionMs = 12_000L,
                sampledAtMs = 10_000L,
                durationMs = 300_000L,
                currentIndex = 0,
                queueVersion = 2L
            )
        )
        runCurrent()
        assertEquals("以父之名", coordinator.remoteController.nowPlaying.value?.title)

        // 会话序号切换代表重连替换旧会话，协调器应复位遥控态并回切本机
        transport._sessionSequence.value = 2L
        runCurrent()
        assertEquals(PlaybackTarget.Local, coordinator.activeTarget.value)
        assertNull(coordinator.remoteController.nowPlaying.value)
        assertTrue(coordinator.remoteController.queue.value.isEmpty())
    }

    private class SequenceFakeTransport : LanLinkTransport {
        override val localDeviceId: String = "phone-1"
        override val localDeviceName: String = "Pixel 9"
        override val isHostDevice: Boolean = false
        override val enabled: StateFlow<Boolean> = MutableStateFlow(true)
        val _connectedDevice = MutableStateFlow<ConnectedPeerInfo?>(null)
        override val connectedDevice: StateFlow<ConnectedPeerInfo?> = _connectedDevice.asStateFlow()
        override val pairedDevices: StateFlow<List<PairedLanDevice>> = MutableStateFlow(emptyList())
        val _sessionSequence = MutableStateFlow(0L)
        override val sessionSequence: StateFlow<Long> = _sessionSequence.asStateFlow()
        private val _incoming = MutableSharedFlow<LanLinkIncomingMessage>(extraBufferCapacity = 16)
        override val incomingMessages: Flow<LanLinkIncomingMessage> = _incoming.asSharedFlow()

        override suspend fun sendMessage(message: LanLinkMessage): Boolean = true
        override fun emitUserMessage(message: String) = Unit
        suspend fun receive(peer: ConnectedPeerInfo, message: LanLinkMessage) {
            _incoming.emit(LanLinkIncomingMessage(peer, message))
        }
    }

    private class MinimalVolumeBridge : RemoteVolumeBridge {
        override val volume: StateFlow<Int> = MutableStateFlow(50)
        override val lastAudibleVolume: StateFlow<Int> = MutableStateFlow(50)
        override fun setVolume(percent: Int) = Unit
    }

    private class MinimalLocalController : PlaybackController {
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
        override suspend fun initController() = Unit
        override fun playQueue(items: List<QueueItem>, startIndex: Int, playContext: String?, source: PlaySource?, startPositionMs: Long) = Unit
        override fun playAudio(songId: Long, url: String, title: String, artist: String, coverUrl: String, startPosition: Long, playContext: String?) = Unit
        override fun addToPlayNext(items: List<QueueItem>) = Unit
        override fun playNextNow(item: QueueItem, startPositionMs: Long) = Unit
        override fun appendToQueue(items: List<QueueItem>) = Unit
        override fun extendQueue(source: PlaySource, items: List<QueueItem>) = Unit
        override fun playNext() = Unit
        override fun playPrevious() = Unit
        override fun skipToPrevious() = Unit
        override fun playAtIndex(index: Int) = Unit
        override fun removeFromQueue(index: Int) = Unit
        override fun moveInQueue(from: Int, to: Int) = Unit
        override fun clearQueue() = Unit
        override fun stopAndClearQueue() = Unit
        override fun toggleShuffle() = Unit
        override fun toggleRepeat() = Unit
        override fun rotatePlayMode() = Unit
        override fun pause() = Unit
        override fun resume() = Unit
        override fun togglePlayPause() = Unit
        override fun seekTo(positionMs: Long) = Unit
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
}
