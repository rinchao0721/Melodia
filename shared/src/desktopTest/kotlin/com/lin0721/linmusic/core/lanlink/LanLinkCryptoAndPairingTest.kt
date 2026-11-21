package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.playlistexternal.likedExternalKey
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.util.concurrent.atomic.AtomicReference

class LanLinkCryptoAndPairingTest {

    @Test
    fun aesGcmSealOpenAndSessionCipherRejectTampering() {
        val salt = LanLinkCrypto.randomBytes(LanLinkCrypto.SALT_BYTES)
        val pairingKey = LanLinkCrypto.derivePairingKey("123456", salt)
        val deviceKey = LanLinkCrypto.randomBytes(LanLinkCrypto.AES_KEY_BYTES)

        val sealed = LanLinkCrypto.sealWithRandomIv(pairingKey, deviceKey)
        assertArrayEquals(deviceKey, LanLinkCrypto.openWithPrefixedIv(pairingKey, sealed))

        val tampered = sealed.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 0xFF).toByte() }
        assertThrows(GeneralSecurityException::class.java) {
            LanLinkCrypto.openWithPrefixedIv(pairingKey, tampered)
        }

        val sessionKey = LanLinkCrypto.deriveSessionKey(
            deviceKey = deviceKey,
            clientNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES),
            serverNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES)
        )
        val sender = LanLinkSessionCipher(sessionKey, isHost = true)
        val receiver = LanLinkSessionCipher(sessionKey, isHost = false)

        val frame1 = sender.encryptFrame("payload-1".encodeToByteArray())
        val frame2 = sender.encryptFrame("payload-2".encodeToByteArray())
        // 每帧 IV 必须唯一
        assertFalse(frame1.copyOfRange(0, LanLinkCrypto.GCM_IV_BYTES).contentEquals(frame2.copyOfRange(0, LanLinkCrypto.GCM_IV_BYTES)))
        assertEquals("payload-1", receiver.decryptFrame(frame1).decodeToString())
        assertEquals("payload-2", receiver.decryptFrame(frame2).decodeToString())

        // 重放已收过的帧被拒
        assertThrows(GeneralSecurityException::class.java) { receiver.decryptFrame(frame2) }
        // 把对端的帧反射回发送方无法解密
        assertThrows(GeneralSecurityException::class.java) { sender.decryptFrame(frame1) }
        // 反方向独立计数，可正常往返
        val reply = receiver.encryptFrame("reply".encodeToByteArray())
        assertEquals("reply", sender.decryptFrame(reply).decodeToString())
    }

    @Test
    fun pairingSessionManagerEnforcesTtlMaxAttemptsAndSingleUse() {
        var now = 10_000L
        val manager = PairingSessionManager(
            ttlMs = 120_000L,
            maxAttempts = 5,
            nowMs = { now },
            codeGenerator = { "042819" }
        )

        val created = manager.startNewCode()
        assertEquals("042819", created.code)
        assertEquals(6, created.code.length)

        // 连续 4 次错误后还剩 1 次机会，第 5 次错误直接作废
        for (expectedRemaining in 4 downTo 1) {
            val res = manager.verifyAttempt { it == "wrong" }
            assertEquals(PairingVerifyOutcome.InvalidCode(expectedRemaining), res)
        }
        assertEquals(PairingVerifyOutcome.LockedOut, manager.verifyAttempt { it == "wrong" })
        assertNull(manager.currentState())
        assertEquals(PairingVerifyOutcome.UnavailableOrExpired, manager.verifyAttempt { it == "042819" })

        // 超时 2 分钟失效
        manager.startNewCode()
        now += 120_000L
        assertNull(manager.currentState())
        assertEquals(PairingVerifyOutcome.UnavailableOrExpired, manager.verifyAttempt { it == "042819" })

        // 验证成功一次后即销毁，不可重用
        manager.startNewCode()
        assertEquals(PairingVerifyOutcome.Verified("042819"), manager.verifyAttempt { it == "042819" })
        assertEquals(PairingVerifyOutcome.UnavailableOrExpired, manager.verifyAttempt { it == "042819" })
    }

    @Test
    fun encryptedLoopbackSessionsSyncSameAccountAndBlockMismatchAccount(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("melodia-lanlink-loopback").toFile()
        try {
            val hostStore = PlaylistExternalTrackStore(PreferencesStores.get(File(tempDir, "host_ext.preferences_pb")))
            val clientStore = PlaylistExternalTrackStore(PreferencesStores.get(File(tempDir, "client_ext.preferences_pb")))
            val hostUsers = UserPreferences(PreferencesStores.get(File(tempDir, "host_user.preferences_pb")))
            val clientUsers = UserPreferences(PreferencesStores.get(File(tempDir, "client_user.preferences_pb")))

            hostUsers.upsertAccount(StoredAccount(uid = 1001L, nickname = "Host"))
            clientUsers.upsertAccount(StoredAccount(uid = 1001L, nickname = "Client"))

            val hostTrack = ExternalTrack(id = "track-host", name = "Host Song", artists = "A", platform = MusicPlatform.QQ)
            val clientLikeTrack = ExternalTrack(id = "track-like", name = "Liked Song", artists = "B", platform = MusicPlatform.KUWO)
            hostStore.add(playlistId = 555L, tracks = listOf(hostTrack))
            clientStore.add(playlistId = likedExternalKey(1001L), tracks = listOf(clientLikeTrack))

            val sessionKey = LanLinkCrypto.deriveSessionKey(
                deviceKey = LanLinkCrypto.randomBytes(LanLinkCrypto.AES_KEY_BYTES),
                clientNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES),
                serverNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES)
            )

            val hostSyncState = AtomicReference(LanLinkSyncState.IDLE)
            val clientSyncState = AtomicReference(LanLinkSyncState.IDLE)
            val clientStatusMsg = AtomicReference("")

            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                val serverSocketFuture = async(Dispatchers.IO) { server.accept() }
                val clientSocket = Socket(InetAddress.getLoopbackAddress(), server.localPort)
                val serverSocket = serverSocketFuture.await()

                val hostSession = LanLinkSession(
                    socket = serverSocket,
                    sessionKey = sessionKey,
                    isHost = true,
                    peerDeviceId = "phone-1",
                    peerDeviceName = "Phone",
                    store = hostStore,
                    userPreferences = hostUsers,
                    onUnpairedByPeer = {},
                    onSyncStateChanged = { state, _ -> hostSyncState.set(state) }
                )
                val clientSession = LanLinkSession(
                    socket = clientSocket,
                    sessionKey = sessionKey,
                    isHost = false,
                    peerDeviceId = "desktop-1",
                    peerDeviceName = "Desktop",
                    store = clientStore,
                    userPreferences = clientUsers,
                    onUnpairedByPeer = {},
                    onSyncStateChanged = { state, msg ->
                        clientSyncState.set(state)
                        clientStatusMsg.set(msg)
                    }
                )

                val hostJob = launch(Dispatchers.IO) { hostSession.run() }
                val clientJob = launch(Dispatchers.IO) { clientSession.run() }

                withTimeout(5_000L) {
                    while (!hostStore.contains(likedExternalKey(1001L), clientLikeTrack) ||
                        !clientStore.contains(555L, hostTrack)
                    ) {
                        delay(50)
                    }
                }
                assertEquals(LanLinkSyncState.SYNCED, hostSyncState.get())
                assertEquals(LanLinkSyncState.SYNCED, clientSyncState.get())

                // 切换手机端账号后，应立刻进入账号不一致提示且停止同步
                clientUsers.upsertAccount(StoredAccount(uid = 2002L, nickname = "Other"))
                withTimeout(5_000L) {
                    while (clientSyncState.get() != LanLinkSyncState.ACCOUNT_MISMATCH ||
                        hostSyncState.get() != LanLinkSyncState.ACCOUNT_MISMATCH
                    ) {
                        delay(50)
                    }
                }
                assertEquals("账号不一致，未同步", clientStatusMsg.get())

                val blockedTrack = ExternalTrack(id = "blocked", name = "Blocked", artists = "C", platform = MusicPlatform.KUGOU)
                clientStore.add(playlistId = 777L, tracks = listOf(blockedTrack))
                delay(1_400L)
                assertFalse(hostStore.contains(777L, blockedTrack))
                assertNotNull(hostStore.all.first())

                hostSession.close()
                clientSession.close()
                hostJob.cancel()
                clientJob.cancel()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
