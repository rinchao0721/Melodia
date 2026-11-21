package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.preferences.PreferencesStores
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.Collections

class LanLinkSessionLifecycleTest {

    @Test
    fun sessionRunExitsCleanlyOnSocketCloseAndSupportsReconnect(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("melodia-lanlink-lifecycle").toFile()
        try {
            val hostStore = PlaylistExternalTrackStore(PreferencesStores.get(File(tempDir, "host_ext.preferences_pb")))
            val clientStore = PlaylistExternalTrackStore(PreferencesStores.get(File(tempDir, "client_ext.preferences_pb")))
            val hostUsers = UserPreferences(PreferencesStores.get(File(tempDir, "host_user.preferences_pb")))
            val clientUsers = UserPreferences(PreferencesStores.get(File(tempDir, "client_user.preferences_pb")))
            hostUsers.upsertAccount(StoredAccount(uid = 2001L, nickname = "Host"))
            clientUsers.upsertAccount(StoredAccount(uid = 2001L, nickname = "Client"))

            val deviceKey = LanLinkCrypto.randomBytes(LanLinkCrypto.AES_KEY_BYTES)
            val serverSocket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
            serverSocket.use { listener ->
                // 第一轮连接：收发遥控控制消息后断开，双方 run() 必须在有限时间内干净返回（子协程不泄漏）
                run {
                    val hostAccept = async(Dispatchers.IO) { listener.accept() }
                    val clientSocket = withTimeout(5_000L) {
                        Socket(InetAddress.getByName("127.0.0.1"), listener.localPort)
                    }
                    val hostSocket = hostAccept.await()

                    val sessionKey = LanLinkCrypto.deriveSessionKey(
                        deviceKey = deviceKey,
                        clientNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES),
                        serverNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES)
                    )
                    val hostReceived = CompletableDeferred<LanLinkMessage>()
                    val clientReceived = CompletableDeferred<LanLinkMessage>()

                    val hostSession = LanLinkSession(
                        socket = hostSocket,
                        sessionKey = sessionKey,
                        isHost = true,
                        peerDeviceId = "client-1",
                        peerDeviceName = "Pixel",
                        store = hostStore,
                        userPreferences = hostUsers,
                        onUnpairedByPeer = {},
                        onSyncStateChanged = { _, _ -> },
                        onMessageReceived = { hostReceived.complete(it) }
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
                        onMessageReceived = { clientReceived.complete(it) }
                    )

                    val hostJob = launch(Dispatchers.IO) { hostSession.run() }
                    val clientJob = launch(Dispatchers.IO) { clientSession.run() }

                    val hello = LanLinkMessage.RemotePlaybackHello(
                        deviceId = "client-1",
                        deviceName = "Pixel",
                        isPlaying = false,
                        playWhenReady = false,
                        hasTrack = false,
                        lastActiveAtMs = 1_000L,
                        isHost = false
                    )
                    clientSession.sendMessage(hello)
                    assertEquals(hello, withTimeout(5_000L) { hostReceived.await() })

                    val activeNotice = LanLinkMessage.RemoteActiveDevice(
                        activeDeviceId = "host-1",
                        activeDeviceName = "Desktop",
                        epoch = 1L,
                        timestampMs = 1_100L
                    )
                    hostSession.sendMessage(activeNotice)
                    assertEquals(activeNotice, withTimeout(5_000L) { clientReceived.await() })

                    // 客户端主动断开，双方会话应立即退出 coroutineScope
                    clientSession.close()
                    withTimeout(5_000L) {
                        clientJob.join()
                        hostJob.join()
                    }
                }

                // 第二轮重连：同一端口重建加密会话并再次正常收发后关闭
                run {
                    val hostAccept = async(Dispatchers.IO) { listener.accept() }
                    val clientSocket = withTimeout(5_000L) {
                        Socket(InetAddress.getByName("127.0.0.1"), listener.localPort)
                    }
                    val hostSocket = hostAccept.await()

                    val reconnectKey = LanLinkCrypto.deriveSessionKey(
                        deviceKey = deviceKey,
                        clientNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES),
                        serverNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES)
                    )
                    val hostMessages = Collections.synchronizedList(mutableListOf<LanLinkMessage>())
                    val secondMessageReceived = CompletableDeferred<LanLinkMessage>()

                    val hostSession = LanLinkSession(
                        socket = hostSocket,
                        sessionKey = reconnectKey,
                        isHost = true,
                        peerDeviceId = "client-1",
                        peerDeviceName = "Pixel",
                        store = hostStore,
                        userPreferences = hostUsers,
                        onUnpairedByPeer = {},
                        onSyncStateChanged = { _, _ -> },
                        onMessageReceived = {
                            hostMessages += it
                            secondMessageReceived.complete(it)
                        }
                    )
                    val clientSession = LanLinkSession(
                        socket = clientSocket,
                        sessionKey = reconnectKey,
                        isHost = false,
                        peerDeviceId = "host-1",
                        peerDeviceName = "Desktop",
                        store = clientStore,
                        userPreferences = clientUsers,
                        onUnpairedByPeer = {},
                        onSyncStateChanged = { _, _ -> }
                    )

                    val hostJob = launch(Dispatchers.IO) { hostSession.run() }
                    val clientJob = launch(Dispatchers.IO) { clientSession.run() }

                    val cmd = LanLinkMessage.RemoteCommand(RemoteCommandAction.TOGGLE_PLAY_PAUSE)
                    clientSession.sendMessage(cmd)
                    assertEquals(cmd, withTimeout(5_000L) { secondMessageReceived.await() })
                    assertTrue(hostMessages.contains(cmd))

                    hostSession.close()
                    withTimeout(5_000L) {
                        hostJob.join()
                        clientJob.join()
                    }
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
