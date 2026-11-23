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
    private val onByeByPeer: (suspend (String) -> Unit)? = null,
    val peerProtocolVersion: Int = LanLinkConstants.PROTOCOL_VERSION_V1
) {
    val negotiatedProtocolVersion: Int = minOf(
        LanLinkConstants.PROTOCOL_VERSION,
        peerProtocolVersion.coerceAtLeast(LanLinkConstants.PROTOCOL_VERSION_V1)
    )

    private val cipher = LanLinkSessionCipher(sessionKey, isHost)
    private val sendMutex = Mutex()
    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()

    @Volatile
    private var localUid: Long? = null

    @Volatile
    private var peerUid: Long? = null

    @Volatile
    private var localAccounts: Map<String, String> = emptyMap()

    @Volatile
    private var peerAccounts: Map<String, String> = emptyMap()

    @Volatile
    private var peerAccountKnown: Boolean = false

    @Volatile
    private var lastSyncedSignature: String? = null

    @Volatile
    private var closed: Boolean = false

    suspend fun run() {
        try {
            socket.soTimeout = LanLinkConstants.HEARTBEAT_TIMEOUT_MS.toInt()
            val initialUid = userPreferences.activeUid.first()
            val initialAccounts = userPreferences.activeAccountsByPlatform.first()
                .map { (platform, account) -> platform.key to account.accountId.canonical }
                .toMap()
            localUid = initialUid
            localAccounts = initialAccounts
            sendEncrypted(LanLinkMessage.AccountExchange(uid = initialUid, activeAccounts = initialAccounts))
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
                    userPreferences.activeAccountsByPlatform
                        .distinctUntilChanged()
                        .drop(1)
                        .collect { accounts ->
                            val mapped = accounts
                                .map { (platform, account) -> platform.key to account.accountId.canonical }
                                .toMap()
                            val uid = accounts[com.lin0721.linmusic.core.source.MusicPlatform.NETEASE]?.uid
                            localUid = uid
                            localAccounts = mapped
                            lastSyncedSignature = null
                            runCatching {
                                sendEncrypted(LanLinkMessage.AccountExchange(uid = uid, activeAccounts = mapped))
                            }
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
                    peerAccounts = message.activeAccounts
                    peerAccountKnown = true
                    if (!canSync()) {
                        lastSyncedSignature = null
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
        if (!peerAccountKnown) return false
        val currentLocal = localUid
        if (currentLocal != null && peerUid == currentLocal) return true
        if (negotiatedProtocolVersion >= LanLinkConstants.PROTOCOL_VERSION) {
            return localAccounts.any { (platformKey, canonical) ->
                peerAccounts[platformKey] == canonical
            }
        }
        return false
    }

    private fun syncSignature(): String {
        val matched = localAccounts.entries
            .filter { (k, v) -> peerAccounts[k] == v }
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
        return if (matched.isNotEmpty()) matched else "uid:${localUid ?: ""}"
    }

    private suspend fun updateSyncStatus(initialPushIfNeeded: Boolean) {
        val currentLocal = localUid
        val currentPeer = peerUid
        val hasAnyLocal = currentLocal != null || localAccounts.isNotEmpty()
        val hasAnyPeer = currentPeer != null || peerAccounts.isNotEmpty()
        when {
            !peerAccountKnown -> {
                onSyncStateChanged(LanLinkSyncState.IDLE, "已连接，正在核对账号…")
            }
            !hasAnyLocal || !hasAnyPeer -> {
                onSyncStateChanged(LanLinkSyncState.NOT_LOGGED_IN, "未登录账号，暂不同步")
            }
            !canSync() -> {
                onSyncStateChanged(LanLinkSyncState.ACCOUNT_MISMATCH, "账号不一致，未同步")
            }
            else -> {
                onSyncStateChanged(LanLinkSyncState.SYNCED, "已连接 · 同步正常")
                val signature = syncSignature()
                if (initialPushIfNeeded && lastSyncedSignature != signature) {
                    lastSyncedSignature = signature
                    val snapshot = store.snapshot()
                    runCatching {
                        sendEncrypted(LanLinkMessage.SyncSnapshot(snapshot, System.currentTimeMillis()))
                    }
                }
            }
        }
    }

    private suspend fun sendEncrypted(message: LanLinkMessage) {
        val outbound = message.projectForProtocol(negotiatedProtocolVersion) ?: return
        try {
            val encoded = LanLinkJson.encodeToString<LanLinkMessage>(outbound).encodeToByteArray()
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
