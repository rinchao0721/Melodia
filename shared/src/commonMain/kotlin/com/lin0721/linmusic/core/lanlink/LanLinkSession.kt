package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.net.Socket
import java.security.GeneralSecurityException

private const val TAG = "LanLinkSession"
private const val BYE_SEND_TIMEOUT_MS = 800L

// 管理认证后单条加密长连接的心跳、账号校验与歌单外部曲目/红心双向同步
@OptIn(FlowPreview::class)
internal class LanLinkSession(
    private val socket: Socket,
    sessionKey: ByteArray,
    isHost: Boolean,
    val peerDeviceId: String,
    val peerDeviceName: String,
    private val store: PlaylistExternalTrackStore,
    private val userPreferences: UserPreferences,
    private val onUnpairedByPeer: suspend () -> Unit,
    private val onSyncStateChanged: (LanLinkSyncState, String) -> Unit,
    private val onMessageReceived: (suspend (LanLinkMessage) -> Unit)? = null,
    private val onByeByPeer: (suspend (String) -> Unit)? = null
) {
    private val cipher = LanLinkSessionCipher(sessionKey, isHost)
    private val sendMutex = Mutex()
    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()

    @Volatile
    private var localUid: Long? = null

    @Volatile
    private var peerUid: Long? = null

    @Volatile
    private var peerAccountKnown: Boolean = false

    @Volatile
    private var lastSyncedPairUid: Long? = null

    @Volatile
    private var closed: Boolean = false

    suspend fun run() {
        try {
            socket.soTimeout = LanLinkConstants.HEARTBEAT_TIMEOUT_MS.toInt()
            val initialUid = userPreferences.activeUid.first()
            localUid = initialUid
            sendEncrypted(LanLinkMessage.AccountExchange(initialUid))
            updateSyncStatus(initialPushIfNeeded = false)

            coroutineScope {
                val heartbeatJob = launch(Dispatchers.IO) {
                    while (!closed && !socket.isClosed) {
                        delay(LanLinkConstants.HEARTBEAT_INTERVAL_MS)
                        runCatching { sendEncrypted(LanLinkMessage.Ping(System.currentTimeMillis())) }
                            .onFailure {
                                close()
                                return@launch
                            }
                    }
                }

                val uidJob = launch(Dispatchers.IO) {
                    userPreferences.activeUid
                        .distinctUntilChanged()
                        .drop(1)
                        .collect { uid ->
                            localUid = uid
                            lastSyncedPairUid = null
                            runCatching { sendEncrypted(LanLinkMessage.AccountExchange(uid)) }
                            updateSyncStatus(initialPushIfNeeded = true)
                        }
                }

                val snapshotJob = launch(Dispatchers.IO) {
                    // 跳过订阅首帧，首次同步由 AccountExchange 匹配触发；本地后续变更防抖 1s 推送
                    store.snapshotFlow
                        .drop(1)
                        .debounce(LanLinkConstants.SYNC_DEBOUNCE_MS)
                        .collect { snapshot ->
                            if (canSync()) {
                                runCatching {
                                    sendEncrypted(LanLinkMessage.SyncSnapshot(snapshot, System.currentTimeMillis()))
                                }
                            }
                        }
                }

                try {
                    withContext(Dispatchers.IO) {
                        readLoop()
                    }
                } finally {
                    // 读循环结束后必须取消永不终止的流收集子协程，否则 coroutineScope 永久挂起导致会话对象泄漏
                    closed = true
                    heartbeatJob.cancel()
                    uidJob.cancel()
                    snapshotJob.cancel()
                }
            }
        } finally {
            close()
        }
    }

    suspend fun sendUnpairedAndClose(reason: String = "对方已取消配对") {
        runCatching {
            if (!closed && !socket.isClosed) {
                sendEncrypted(LanLinkMessage.UnpairedNotice(reason))
            }
        }
        close()
    }

    // 正常关闭前尽力向对端发送 Bye，超时或失败均立即关闭底层连接
    suspend fun sendByeAndClose(reason: String = "normal_disconnect") {
        withTimeoutOrNull(BYE_SEND_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!closed && !socket.isClosed) {
                        sendEncrypted(LanLinkMessage.Bye(reason))
                    }
                }
            }
        }
        close()
    }

    // 遥控协调器在主线程调度，Android 主线程写 socket 会抛 NetworkOnMainThreadException
    suspend fun sendMessage(message: LanLinkMessage) = withContext(Dispatchers.IO) {
        sendEncrypted(message)
    }

    fun close() {
        closed = true
        runCatching { socket.close() }
    }

    private suspend fun readLoop() {
        while (!closed && !socket.isClosed) {
            val frame = try {
                LanLinkFrameCodec.readFrame(input)
            } catch (_: IOException) {
                break
            } ?: break

            val plaintext = try {
                cipher.decryptFrame(frame)
            } catch (e: GeneralSecurityException) {
                AppLogger.w(TAG, "收到无法通过 AEAD 校验的帧，断开连接", e)
                break
            }

            val message = try {
                LanLinkJson.decodeFromString<LanLinkMessage>(plaintext.decodeToString())
            } catch (e: Exception) {
                AppLogger.w(TAG, "收到非法 JSON 控制消息，忽略", e)
                continue
            }

            when (message) {
                is LanLinkMessage.AccountExchange -> {
                    peerUid = message.uid
                    peerAccountKnown = true
                    if (localUid == null || peerUid != localUid) {
                        lastSyncedPairUid = null
                    }
                    updateSyncStatus(initialPushIfNeeded = true)
                }

                is LanLinkMessage.SyncSnapshot -> {
                    if (canSync()) {
                        store.applyMergedRemoteSnapshot(message.snapshot)
                    }
                }

                is LanLinkMessage.Ping -> {
                    runCatching { sendEncrypted(LanLinkMessage.Pong(message.timestampMs)) }
                        .onFailure { break }
                }

                is LanLinkMessage.Pong -> Unit

                is LanLinkMessage.Bye -> {
                    AppLogger.i(TAG, "收到对端断开通知: ${message.reason}")
                    onByeByPeer?.invoke(message.reason)
                    break
                }

                is LanLinkMessage.UnpairedNotice -> {
                    onUnpairedByPeer()
                    break
                }

                is LanLinkMessage.ErrorMessage -> {
                    AppLogger.w(TAG, "对端返回错误: ${message.code} - ${message.message}")
                    break
                }

                else -> {
                    onMessageReceived?.invoke(message)
                }
            }
        }
    }

    private fun canSync(): Boolean {
        val currentLocal = localUid ?: return false
        return peerAccountKnown && peerUid == currentLocal
    }

    private suspend fun updateSyncStatus(initialPushIfNeeded: Boolean) {
        val currentLocal = localUid
        val currentPeer = peerUid
        when {
            !peerAccountKnown -> {
                onSyncStateChanged(LanLinkSyncState.IDLE, "已连接，正在核对账号…")
            }
            currentLocal == null || currentPeer == null -> {
                onSyncStateChanged(LanLinkSyncState.NOT_LOGGED_IN, "未登录网易云账号，暂不同步")
            }
            currentLocal != currentPeer -> {
                onSyncStateChanged(LanLinkSyncState.ACCOUNT_MISMATCH, "账号不一致，未同步")
            }
            else -> {
                onSyncStateChanged(LanLinkSyncState.SYNCED, "已连接 · 同步正常")
                if (initialPushIfNeeded && lastSyncedPairUid != currentLocal) {
                    lastSyncedPairUid = currentLocal
                    val snapshot = store.snapshot()
                    runCatching {
                        sendEncrypted(LanLinkMessage.SyncSnapshot(snapshot, System.currentTimeMillis()))
                    }
                }
            }
        }
    }

    private suspend fun sendEncrypted(message: LanLinkMessage) {
        try {
            val encoded = LanLinkJson.encodeToString<LanLinkMessage>(message).encodeToByteArray()
            // 加密与写出同在锁内，保证帧序号与线上顺序一致，否则对端按递增校验会拒收
            sendMutex.withLock {
                if (closed || socket.isClosed) throw IOException("会话已关闭")
                LanLinkFrameCodec.writeFrame(output, cipher.encryptFrame(encoded))
            }
        } catch (t: Throwable) {
            if (t !is CancellationException) {
                close()
            }
            throw t
        }
    }
}
