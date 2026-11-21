package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LanLinkAndroidClient"
private const val TCP_CONNECT_TIMEOUT_MS = 4_000
private const val HANDSHAKE_TIMEOUT_MS = 12_000
internal const val WIFI_RECONNECT_DEBOUNCE_MS = 1_500L
private val RECONNECT_BACKOFF_MS = longArrayOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L)

// 平台锁抽象：Android 仅在 UDP 发现期间持有多播锁，结束后立即释放
interface LanDiscoveryLock {
    fun <T> withLock(block: () -> T): T
}

// Wi-Fi 就绪重连防抖器：网络切换抖动时延迟触发一次重连，断网时取消待触发任务
internal class LanLinkWifiReconnectDebouncer(
    private val scope: CoroutineScope,
    private val debounceMs: Long = WIFI_RECONNECT_DEBOUNCE_MS,
    private val onTriggerReconnect: () -> Unit
) {
    private val lock = Any()
    private var pendingJob: Job? = null

    fun onAvailable() {
        synchronized(lock) {
            pendingJob?.cancel()
            pendingJob = scope.launch {
                delay(debounceMs)
                onTriggerReconnect()
            }
        }
    }

    fun cancel() {
        synchronized(lock) {
            pendingJob?.cancel()
            pendingJob = null
        }
    }
}

// Android 端局域网联动客户端：UDP 发现桌面端、配对码配对、密钥双向认证与自动重连同步
class LanLinkAndroidClient(
    private val lanLinkPreferences: LanLinkPreferences,
    private val settingsPreferences: SettingsPreferences,
    private val playlistExternalTrackStore: PlaylistExternalTrackStore,
    private val userPreferences: UserPreferences,
    private val discoveryLock: LanDiscoveryLock,
    private val deviceNameProvider: () -> String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : LanLinkTransport {
    private val sessionLock = Any()

    @Volatile
    private var cachedLocalDeviceId: String = ""

    override val localDeviceId: String
        get() = cachedLocalDeviceId.ifBlank {
            runBlocking { lanLinkPreferences.ensureLocalDeviceId().also { cachedLocalDeviceId = it } }
        }

    override val localDeviceName: String
        get() = deviceNameProvider()

    override val isHostDevice: Boolean = false

    override val enabled: StateFlow<Boolean> = settingsPreferences.lanLinkEnabled
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val pairedDevices: StateFlow<List<PairedLanDevice>> = lanLinkPreferences.pairedDevices
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredLanDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredLanDevice>> = _discoveredDevices.asStateFlow()

    private val _connectionState = MutableStateFlow(LanLinkConnectionState.DISABLED)
    val connectionState: StateFlow<LanLinkConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<ConnectedPeerInfo?>(null)
    override val connectedDevice: StateFlow<ConnectedPeerInfo?> = _connectedDevice.asStateFlow()

    private val _sessionSequence = MutableStateFlow(0L)
    override val sessionSequence: StateFlow<Long> = _sessionSequence.asStateFlow()

    private val _syncState = MutableStateFlow(LanLinkSyncState.IDLE)
    val syncState: StateFlow<LanLinkSyncState> = _syncState.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow("已关闭")
    val syncStatusMessage: StateFlow<String> = _syncStatusMessage.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _incomingMessages = MutableSharedFlow<LanLinkIncomingMessage>(extraBufferCapacity = 64)
    override val incomingMessages: Flow<LanLinkIncomingMessage> = _incomingMessages.asSharedFlow()

    private val knownEndpoints = ConcurrentHashMap<String, Pair<String, Int>>()

    private var started = false
    private var lifecycleJob: Job? = null
    private var reconnectJob: Job? = null
    private val wifiDebouncer = LanLinkWifiReconnectDebouncer(scope) {
        if (enabled.value && pairedDevices.value.isNotEmpty()) {
            triggerReconnectNow()
        }
    }

    @Volatile
    private var activeSession: LanLinkSession? = null

    @Volatile
    private var activeSessionJob: Job? = null

    @Synchronized
    fun start() {
        if (started) return
        started = true
        lifecycleJob = scope.launch {
            cachedLocalDeviceId = lanLinkPreferences.ensureLocalDeviceId()
            combine(settingsPreferences.lanLinkEnabled, lanLinkPreferences.pairedDevices) { on, paired -> on to paired }
                .distinctUntilChanged()
                .collectLatest { (isEnabled, paired) ->
                    if (!isEnabled) {
                        shutdownConnectionLoop(sendBye = true, reason = "disabled")
                        return@collectLatest
                    }
                    if (paired.isEmpty()) {
                        val previous = synchronized(sessionLock) {
                            val s = activeSession
                            activeSession = null
                            activeSessionJob = null
                            _connectedDevice.value = null
                            _connectionState.value = LanLinkConnectionState.IDLE
                            _syncState.value = LanLinkSyncState.IDLE
                            _syncStatusMessage.value = "未配对桌面端设备"
                            s
                        }
                        previous?.close()
                        return@collectLatest
                    }
                    if (activeSession == null) {
                        ensureReconnectLoop()
                    }
                }
        }
    }

    @Synchronized
    fun stop() {
        started = false
        wifiDebouncer.cancel()
        lifecycleJob?.cancel()
        lifecycleJob = null
        scope.launch { shutdownConnectionLoop(sendBye = true, reason = "stop") }
    }

    // 显式关闭应用或停止服务前先发 Bye 再关闭会话
    suspend fun shutdownWithBye(reason: String = "app_exit") {
        synchronized(this) {
            started = false
            wifiDebouncer.cancel()
            lifecycleJob?.cancel()
            lifecycleJob = null
        }
        shutdownConnectionLoop(sendBye = true, reason = reason)
    }

    suspend fun discoverDevices(): List<DiscoveredLanDevice> {
        if (!enabled.value) return emptyList()
        _isDiscovering.value = true
        if (activeSession == null) {
            _connectionState.value = LanLinkConnectionState.DISCOVERING
        }
        return try {
            val localId = lanLinkPreferences.ensureLocalDeviceId().also { cachedLocalDeviceId = it }
            val found = withContext(Dispatchers.IO) {
                discoveryLock.withLock {
                    runUdpDiscovery(localId)
                }
            }
            found.forEach { knownEndpoints[it.deviceId] = it.hostAddress to it.tcpPort }
            _discoveredDevices.value = found
            found
        } catch (e: Exception) {
            AppLogger.d(TAG, "局域网发现结束: ${e.message}")
            emptyList()
        } finally {
            _isDiscovering.value = false
            if (activeSession == null) {
                _connectionState.value = if (enabled.value) LanLinkConnectionState.IDLE else LanLinkConnectionState.DISABLED
            }
        }
    }

    suspend fun pairWithDevice(device: DiscoveredLanDevice, code: String): Result<PairedLanDevice> {
        if (!enabled.value) return Result.failure(IllegalStateException("请先开启局域网联动"))
        val trimmedCode = code.trim()
        if (trimmedCode.length != 6 || trimmedCode.any { !it.isDigit() }) {
            return Result.failure(IllegalArgumentException("请输入 6 位数字配对码"))
        }

        return withContext(Dispatchers.IO) {
            val localId = lanLinkPreferences.ensureLocalDeviceId()
            val localName = deviceNameProvider()
            val clientNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES)
            val socket = Socket()
            var handedOff = false
            try {
                _connectionState.value = LanLinkConnectionState.CONNECTING
                _syncStatusMessage.value = "正在配对 ${device.deviceName}…"
                socket.connect(InetSocketAddress(device.hostAddress, device.tcpPort), TCP_CONNECT_TIMEOUT_MS)
                runCatching {
                    socket.keepAlive = true
                    socket.tcpNoDelay = true
                }
                socket.soTimeout = HANDSHAKE_TIMEOUT_MS

                writePlainMessage(
                    socket,
                    LanLinkMessage.PairHello(
                        clientDeviceId = localId,
                        clientDeviceName = localName,
                        clientNonceBase64 = LanLinkCrypto.toBase64(clientNonce)
                    )
                )

                val challengeFrame = LanLinkFrameCodec.readFrame(socket.getInputStream())
                    ?: return@withContext Result.failure(IOException("连接已断开"))
                val challengeMsg = LanLinkJson.decodeFromString<LanLinkMessage>(challengeFrame.decodeToString())
                if (challengeMsg is LanLinkMessage.PairGrant && !challengeMsg.success) {
                    return@withContext Result.failure(IllegalStateException(challengeMsg.error.ifBlank { "桌面端未开启配对" }))
                }
                if (challengeMsg !is LanLinkMessage.PairChallenge) {
                    return@withContext Result.failure(IllegalStateException("桌面端配对响应异常"))
                }

                val serverNonce = LanLinkCrypto.fromBase64(challengeMsg.serverNonceBase64)
                val salt = LanLinkCrypto.fromBase64(challengeMsg.saltBase64)
                val pairingKey = LanLinkCrypto.derivePairingKey(trimmedCode, salt)

                try {
                    val clientProof = LanLinkCrypto.pairingClientProof(
                        pairingKey = pairingKey,
                        clientNonce = clientNonce,
                        serverNonce = serverNonce,
                        clientDeviceId = localId,
                        serverDeviceId = challengeMsg.serverDeviceId
                    )
                    writePlainMessage(
                        socket,
                        LanLinkMessage.PairVerify(clientProofBase64 = LanLinkCrypto.toBase64(clientProof))
                    )

                    val grantFrame = LanLinkFrameCodec.readFrame(socket.getInputStream())
                        ?: return@withContext Result.failure(IOException("配对中断"))
                    val grant = LanLinkJson.decodeFromString<LanLinkMessage>(grantFrame.decodeToString()) as? LanLinkMessage.PairGrant
                        ?: return@withContext Result.failure(IllegalStateException("配对结果无效"))

                    if (!grant.success) {
                        return@withContext Result.failure(IllegalStateException(grant.error.ifBlank { "配对码校验失败" }))
                    }

                    val serverProof = LanLinkCrypto.fromBase64(grant.serverProofBase64)
                    val expectedServerProof = LanLinkCrypto.pairingServerProof(
                        pairingKey = pairingKey,
                        clientNonce = clientNonce,
                        serverNonce = serverNonce,
                        clientDeviceId = localId,
                        serverDeviceId = challengeMsg.serverDeviceId
                    )
                    if (!LanLinkCrypto.constantTimeEquals(expectedServerProof, serverProof)) {
                        return@withContext Result.failure(IllegalStateException("桌面端签名校验失败"))
                    }

                    val sealedKey = LanLinkCrypto.fromBase64(grant.encryptedDeviceKeyBase64)
                    val deviceKey = LanLinkCrypto.openWithPrefixedIv(pairingKey, sealedKey)
                    val peerName = challengeMsg.serverDeviceName.ifBlank { device.deviceName }
                    val paired = PairedLanDevice(
                        deviceId = challengeMsg.serverDeviceId,
                        deviceName = peerName,
                        deviceKeyBase64 = LanLinkCrypto.toBase64(deviceKey),
                        pairedAtMs = System.currentTimeMillis()
                    )
                    knownEndpoints[paired.deviceId] = device.hostAddress to device.tcpPort
                    lanLinkPreferences.savePairedDevice(paired)

                    val sessionKey = LanLinkCrypto.deriveSessionKey(deviceKey, clientNonce, serverNonce)
                    handedOff = true
                    launchAuthenticatedSession(socket, sessionKey, paired.deviceId, paired.deviceName)
                    _messages.tryEmit("已配对 ${paired.deviceName}")
                    Result.success(paired)
                } finally {
                    pairingKey.fill(0)
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                if (!handedOff) {
                    runCatching { socket.close() }
                    if (activeSession == null) {
                        _connectionState.value = if (enabled.value) LanLinkConnectionState.IDLE else LanLinkConnectionState.DISABLED
                        _syncStatusMessage.value = if (enabled.value) "配对未完成" else "已关闭"
                    }
                }
            }
        }
    }

    fun unpairDevice(deviceId: String) {
        scope.launch {
            val current = synchronized(sessionLock) {
                activeSession?.takeIf { it.peerDeviceId == deviceId }?.also {
                    activeSession = null
                    activeSessionJob = null
                }
            }
            if (current != null) {
                current.sendUnpairedAndClose()
            }
            knownEndpoints.remove(deviceId)
            lanLinkPreferences.removePairedDevice(deviceId)
            synchronized(sessionLock) {
                if (_connectedDevice.value?.deviceId == deviceId) {
                    _connectedDevice.value = null
                    _connectionState.value = if (enabled.value) LanLinkConnectionState.IDLE else LanLinkConnectionState.DISABLED
                    _syncState.value = LanLinkSyncState.IDLE
                    _syncStatusMessage.value = if (enabled.value) "未连接" else "已关闭"
                }
            }
            _messages.tryEmit("已取消配对")
        }
    }

    // Wi-Fi 就绪后防抖 1.5s 触发立即重连并重置退避
    fun onWifiAvailable() {
        if (!enabled.value || pairedDevices.value.isEmpty()) return
        wifiDebouncer.onAvailable()
    }

    // 网络断开后立即中止待触发重连并关闭活跃会话，避免等待 45s 心跳超时
    fun onNetworkLost() {
        wifiDebouncer.cancel()
        reconnectJob?.cancel()
        reconnectJob = null
        val previous = synchronized(sessionLock) {
            val s = activeSession
            activeSession = null
            activeSessionJob = null
            _connectedDevice.value = null
            _syncState.value = LanLinkSyncState.IDLE
            _connectionState.value = if (enabled.value) LanLinkConnectionState.IDLE else LanLinkConnectionState.DISABLED
            _syncStatusMessage.value = if (enabled.value) "网络已断开" else "已关闭"
            s
        }
        previous?.close()
    }

    fun triggerReconnectNow() {
        if (!enabled.value || activeSession != null) return
        ensureReconnectLoop(restart = true)
    }

    suspend fun connectToPairedDevice(deviceId: String): Boolean {
        if (!enabled.value) return false
        if (_connectedDevice.value?.deviceId == deviceId && activeSession != null) return true
        val paired = pairedDevices.value.firstOrNull { it.deviceId == deviceId }
            ?: lanLinkPreferences.getPairedDevice(deviceId)
            ?: return false

        val cached = knownEndpoints[deviceId]
        if (cached != null && connectAndAuthenticate(paired, cached.first, cached.second, fromReconnectLoop = false)) {
            return true
        }
        val discovered = discoverDevices()
        val candidate = discovered.firstOrNull { it.deviceId == deviceId } ?: return false
        return connectAndAuthenticate(paired, candidate.hostAddress, candidate.tcpPort, fromReconnectLoop = false)
    }

    override suspend fun requestConnectDevice(deviceId: String): Boolean = connectToPairedDevice(deviceId)

    private fun ensureReconnectLoop(restart: Boolean = false) {
        if (restart) reconnectJob?.cancel()
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var backoffIdx = 0
            while (isActive && enabled.value) {
                val paired = pairedDevices.value
                if (paired.isEmpty()) {
                    _connectionState.value = LanLinkConnectionState.IDLE
                    _syncStatusMessage.value = "未配对桌面端设备"
                    break
                }
                if (activeSession != null) {
                    activeSessionJob?.join()
                    if (!enabled.value) break
                    continue
                }

                val connected = tryConnectAnyPairedDevice(paired)
                if (connected) {
                    backoffIdx = 0
                    // 直接挂起等待当前会话协程结束，断线后立即进入下一轮重连
                    activeSessionJob?.join()
                    if (!enabled.value) break
                }

                val waitMs = RECONNECT_BACKOFF_MS[backoffIdx.coerceAtMost(RECONNECT_BACKOFF_MS.lastIndex)]
                if (backoffIdx < RECONNECT_BACKOFF_MS.lastIndex) backoffIdx++
                _connectionState.value = LanLinkConnectionState.IDLE
                _syncStatusMessage.value = "未找到已配对的桌面端，将自动重连"
                delay(waitMs)
            }
        }
    }

    private suspend fun tryConnectAnyPairedDevice(paired: List<PairedLanDevice>): Boolean {
        val pairedById = paired.associateBy { it.deviceId }
        // 先尝试缓存地址，连接不上再走 UDP 发现（桌面端使用临时 TCP 端口，重启后会变化）
        for (device in paired) {
            val cached = knownEndpoints[device.deviceId] ?: continue
            if (connectAndAuthenticate(device, cached.first, cached.second, fromReconnectLoop = true)) return true
        }

        val discovered = discoverDevices()
        for (candidate in discovered) {
            val pairedDevice = pairedById[candidate.deviceId] ?: continue
            if (connectAndAuthenticate(pairedDevice, candidate.hostAddress, candidate.tcpPort, fromReconnectLoop = true)) {
                return true
            }
        }
        return false
    }

    private suspend fun connectAndAuthenticate(
        paired: PairedLanDevice,
        host: String,
        port: Int,
        fromReconnectLoop: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val deviceKey = runCatching { LanLinkCrypto.fromBase64(paired.deviceKeyBase64) }.getOrNull()
        if (deviceKey == null || deviceKey.size != LanLinkCrypto.AES_KEY_BYTES) return@withContext false

        val localId = lanLinkPreferences.ensureLocalDeviceId().also { cachedLocalDeviceId = it }
        val clientNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES)
        val socket = Socket()
        var handedOff = false
        try {
            _connectionState.value = LanLinkConnectionState.CONNECTING
            _syncStatusMessage.value = "正在连接 ${paired.deviceName}…"
            socket.connect(InetSocketAddress(host, port), TCP_CONNECT_TIMEOUT_MS)
            runCatching {
                socket.keepAlive = true
                socket.tcpNoDelay = true
            }
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS

            writePlainMessage(
                socket,
                LanLinkMessage.AuthHello(
                    clientDeviceId = localId,
                    clientDeviceName = deviceNameProvider(),
                    clientNonceBase64 = LanLinkCrypto.toBase64(clientNonce)
                )
            )

            val challengeFrame = LanLinkFrameCodec.readFrame(socket.getInputStream()) ?: return@withContext false
            val challengeMsg = LanLinkJson.decodeFromString<LanLinkMessage>(challengeFrame.decodeToString())
            if (challengeMsg is LanLinkMessage.AuthResult && !challengeMsg.success) {
                if (challengeMsg.error.contains("未配对")) {
                    lanLinkPreferences.removePairedDevice(paired.deviceId)
                    knownEndpoints.remove(paired.deviceId)
                }
                return@withContext false
            }
            val challenge = challengeMsg as? LanLinkMessage.AuthChallenge ?: return@withContext false
            if (challenge.serverDeviceId != paired.deviceId) return@withContext false

            val serverNonce = LanLinkCrypto.fromBase64(challenge.serverNonceBase64)
            val serverProof = LanLinkCrypto.fromBase64(challenge.serverProofBase64)
            val expectedServerProof = LanLinkCrypto.authServerProof(
                deviceKey = deviceKey,
                clientNonce = clientNonce,
                serverNonce = serverNonce,
                clientDeviceId = localId,
                serverDeviceId = paired.deviceId
            )
            if (!LanLinkCrypto.constantTimeEquals(expectedServerProof, serverProof)) {
                return@withContext false
            }

            val clientProof = LanLinkCrypto.authClientProof(
                deviceKey = deviceKey,
                clientNonce = clientNonce,
                serverNonce = serverNonce,
                clientDeviceId = localId,
                serverDeviceId = paired.deviceId
            )
            writePlainMessage(socket, LanLinkMessage.AuthVerify(LanLinkCrypto.toBase64(clientProof)))

            val resultFrame = LanLinkFrameCodec.readFrame(socket.getInputStream()) ?: return@withContext false
            val result = LanLinkJson.decodeFromString<LanLinkMessage>(resultFrame.decodeToString()) as? LanLinkMessage.AuthResult
                ?: return@withContext false
            if (!result.success) {
                if (result.error.contains("未配对")) {
                    lanLinkPreferences.removePairedDevice(paired.deviceId)
                    knownEndpoints.remove(paired.deviceId)
                }
                return@withContext false
            }

            val peerName = challenge.serverDeviceName.ifBlank { paired.deviceName }
            if (peerName != paired.deviceName) {
                lanLinkPreferences.savePairedDevice(paired.copy(deviceName = peerName))
            }
            knownEndpoints[paired.deviceId] = host to port
            val sessionKey = LanLinkCrypto.deriveSessionKey(deviceKey, clientNonce, serverNonce)
            handedOff = true
            launchAuthenticatedSession(
                socket = socket,
                sessionKey = sessionKey,
                peerDeviceId = paired.deviceId,
                peerDeviceName = peerName,
                cancelReconnectLoop = !fromReconnectLoop
            )
            true
        } catch (_: Exception) {
            false
        } finally {
            if (!handedOff) runCatching { socket.close() }
        }
    }

    override suspend fun sendMessage(message: LanLinkMessage): Boolean {
        val session = activeSession ?: return false
        return runCatching {
            session.sendMessage(message)
            true
        }.onFailure {
            AppLogger.w(TAG, "发送局域网消息失败: ${it.message}")
            session.close()
            var cleared = false
            synchronized(sessionLock) {
                if (activeSession === session) {
                    activeSession = null
                    activeSessionJob = null
                    _connectedDevice.value = null
                    _syncState.value = LanLinkSyncState.IDLE
                    _connectionState.value = if (enabled.value) LanLinkConnectionState.IDLE else LanLinkConnectionState.DISABLED
                    _syncStatusMessage.value = if (enabled.value) "已断开，准备重连" else "已关闭"
                    cleared = true
                }
            }
            if (cleared && enabled.value && pairedDevices.value.isNotEmpty()) {
                ensureReconnectLoop()
            }
        }.getOrDefault(false)
    }

    override fun emitUserMessage(message: String) {
        _messages.tryEmit(message)
    }

    private fun launchAuthenticatedSession(
        socket: Socket,
        sessionKey: ByteArray,
        peerDeviceId: String,
        peerDeviceName: String,
        cancelReconnectLoop: Boolean = true
    ) {
        if (cancelReconnectLoop) {
            reconnectJob?.cancel()
            reconnectJob = null
        }
        val session = LanLinkSession(
            socket = socket,
            sessionKey = sessionKey,
            isHost = false,
            peerDeviceId = peerDeviceId,
            peerDeviceName = peerDeviceName,
            store = playlistExternalTrackStore,
            userPreferences = userPreferences,
            onUnpairedByPeer = {
                knownEndpoints.remove(peerDeviceId)
                lanLinkPreferences.removePairedDevice(peerDeviceId)
                _messages.tryEmit("$peerDeviceName 已取消配对")
            },
            onSyncStateChanged = { state, message ->
                _syncState.value = state
                _syncStatusMessage.value = message
            },
            onMessageReceived = { message ->
                _incomingMessages.emit(
                    LanLinkIncomingMessage(
                        peer = ConnectedPeerInfo(peerDeviceId, peerDeviceName),
                        message = message
                    )
                )
            },
            onByeByPeer = {
                _syncStatusMessage.value = "$peerDeviceName 已退出"
            }
        )

        val previous: LanLinkSession?
        val job: Job
        synchronized(sessionLock) {
            previous = activeSession
            activeSession = session
            _sessionSequence.value += 1L
            _connectedDevice.value = ConnectedPeerInfo(peerDeviceId, peerDeviceName)
            _connectionState.value = LanLinkConnectionState.CONNECTED
            _syncStatusMessage.value = "已连接，正在核对账号…"
            job = scope.launch(Dispatchers.IO) {
                try {
                    session.run()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 会话被新连接顶替或网络中断时 run 会抛 IO 异常，不能冒泡到无处理器的 scope
                    AppLogger.d(TAG, "连接已结束: ${e.message}")
                } finally {
                    var disconnected = false
                    synchronized(sessionLock) {
                        if (activeSession === session) {
                            activeSession = null
                            activeSessionJob = null
                            _connectedDevice.value = null
                            _syncState.value = LanLinkSyncState.IDLE
                            _connectionState.value = if (enabled.value) LanLinkConnectionState.IDLE else LanLinkConnectionState.DISABLED
                            _syncStatusMessage.value = if (enabled.value) "已断开，准备重连" else "已关闭"
                            disconnected = true
                        }
                    }
                    if (disconnected && enabled.value && pairedDevices.value.isNotEmpty()) {
                        ensureReconnectLoop()
                    }
                }
            }
            activeSessionJob = job
        }
        previous?.close()
    }

    private suspend fun shutdownConnectionLoop(
        sendBye: Boolean = false,
        reason: String = "normal_disconnect"
    ) {
        wifiDebouncer.cancel()
        reconnectJob?.cancel()
        reconnectJob = null
        val previous = synchronized(sessionLock) {
            val s = activeSession
            activeSession = null
            activeSessionJob = null
            _connectedDevice.value = null
            s
        }
        if (sendBye) {
            previous?.sendByeAndClose(reason)
        } else {
            previous?.close()
        }
        _discoveredDevices.value = emptyList()
        _isDiscovering.value = false
        _connectionState.value = LanLinkConnectionState.DISABLED
        _syncState.value = LanLinkSyncState.IDLE
        _syncStatusMessage.value = "已关闭"
    }

    private fun runUdpDiscovery(localDeviceId: String): List<DiscoveredLanDevice> {
        val results = LinkedHashMap<String, DiscoveredLanDevice>()
        val probeBytes = LanLinkJson.encodeToString(LanDiscoveryProbe(clientDeviceId = localDeviceId)).encodeToByteArray()
        val targets = collectBroadcastTargets()

        DatagramSocket().use { udp ->
            udp.broadcast = true
            udp.soTimeout = 350

            fun sendProbes() {
                for (target in targets) {
                    runCatching {
                        udp.send(DatagramPacket(probeBytes, probeBytes.size, target, LanLinkConstants.UDP_DISCOVERY_PORT))
                    }
                }
            }

            sendProbes()
            val deadline = System.currentTimeMillis() + LanLinkConstants.DISCOVERY_LISTEN_MS
            var resent = false
            val buffer = ByteArray(2048)

            while (System.currentTimeMillis() < deadline && enabled.value) {
                if (!resent && deadline - System.currentTimeMillis() < LanLinkConstants.DISCOVERY_LISTEN_MS - 300) {
                    resent = true
                    sendProbes()
                }
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    udp.receive(packet)
                    val text = packet.data.copyOfRange(packet.offset, packet.offset + packet.length).decodeToString()
                    val resp = runCatching { LanLinkJson.decodeFromString<LanDiscoveryResponse>(text) }.getOrNull()
                        ?: continue
                    if (resp.deviceId == localDeviceId || resp.tcpPort !in 1..65535) continue
                    val host = packet.address.hostAddress ?: continue
                    results[resp.deviceId] = DiscoveredLanDevice(
                        deviceId = resp.deviceId,
                        deviceName = resp.deviceName.ifBlank { host },
                        hostAddress = host,
                        tcpPort = resp.tcpPort,
                        protocolVersion = resp.protocolVersion,
                        lastSeenMs = System.currentTimeMillis()
                    )
                } catch (_: SocketTimeoutException) {
                    // 等待超时继续直到探测窗口结束
                }
            }
        }
        return results.values.toList()
    }

    private fun collectBroadcastTargets(): List<InetAddress> {
        val targets = LinkedHashSet<InetAddress>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.interfaceAddresses) {
                    addr.broadcast?.let { targets.add(it) }
                }
            }
        }
        runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }
        return targets.toList()
    }

    private fun writePlainMessage(socket: Socket, message: LanLinkMessage) {
        val bytes = LanLinkJson.encodeToString<LanLinkMessage>(message).encodeToByteArray()
        LanLinkFrameCodec.writeFrame(socket.getOutputStream(), bytes)
    }
}
