package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "LanLinkDesktopHost"
private const val HANDSHAKE_TIMEOUT_MS = 15_000

data class PairingDisplayState(
    val code: String,
    val remainingSeconds: Int,
    val remainingAttempts: Int
)

// 桌面端局域网联动宿主：监听 UDP 发现端口和临时 TCP 端口，处理配对、认证和加密同步会话
class LanLinkDesktopHost(
    private val lanLinkPreferences: LanLinkPreferences,
    private val settingsPreferences: SettingsPreferences,
    private val playlistExternalTrackStore: PlaylistExternalTrackStore,
    private val userPreferences: UserPreferences,
    private val deviceNameProvider: () -> String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : LanLinkTransport {
    private val pairingManager = PairingSessionManager()
    private val sessionLock = Any()

    @Volatile
    private var cachedLocalDeviceId: String = ""

    override val localDeviceId: String
        get() = cachedLocalDeviceId.ifBlank {
            runBlocking { lanLinkPreferences.ensureLocalDeviceId().also { cachedLocalDeviceId = it } }
        }

    override val localDeviceName: String
        get() = deviceNameProvider()

    override val isHostDevice: Boolean = true

    override val enabled: StateFlow<Boolean> = settingsPreferences.lanLinkEnabled
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val pairedDevices: StateFlow<List<PairedLanDevice>> = lanLinkPreferences.pairedDevices
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _pairingDisplay = MutableStateFlow<PairingDisplayState?>(null)
    val pairingDisplay: StateFlow<PairingDisplayState?> = _pairingDisplay.asStateFlow()

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

    private var started = false
    private var lifecycleJob: Job? = null
    private var serverJob: Job? = null
    private var pairingTickerJob: Job? = null

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var udpSocket: DatagramSocket? = null

    @Volatile
    private var activeSession: LanLinkSession? = null

    @Synchronized
    fun start() {
        if (started) return
        started = true
        lifecycleJob = scope.launch {
            settingsPreferences.lanLinkEnabled
                .distinctUntilChanged()
                .collect { isEnabled ->
                    if (isEnabled) startHost() else stopHost(sendBye = true, reason = "disabled")
                }
        }
    }

    @Synchronized
    fun stop() {
        started = false
        lifecycleJob?.cancel()
        lifecycleJob = null
        scope.launch { stopHost(sendBye = true, reason = "stop") }
    }

    // 应用退出前先向对端发送 Bye 再关闭连接，让对端立即复位状态
    suspend fun stopAndNotify(reason: String = "app_exit") {
        synchronized(this) {
            started = false
            lifecycleJob?.cancel()
            lifecycleJob = null
        }
        stopHost(sendBye = true, reason = reason)
    }

    fun generatePairingCode() {
        if (!enabled.value) {
            _messages.tryEmit("请先开启局域网联动")
            return
        }
        pairingManager.startNewCode()
        refreshPairingDisplay()
        pairingTickerJob?.cancel()
        pairingTickerJob = scope.launch {
            while (isActive) {
                delay(1_000)
                if (!refreshPairingDisplay()) break
            }
        }
    }

    fun cancelPairingCode() {
        pairingTickerJob?.cancel()
        pairingTickerJob = null
        pairingManager.cancelCode()
        _pairingDisplay.value = null
    }

    fun unpairDevice(deviceId: String) {
        scope.launch {
            val current = synchronized(sessionLock) {
                activeSession?.takeIf { it.peerDeviceId == deviceId }?.also { activeSession = null }
            }
            if (current != null) {
                current.sendUnpairedAndClose()
            }
            lanLinkPreferences.removePairedDevice(deviceId)
            synchronized(sessionLock) {
                if (_connectedDevice.value?.deviceId == deviceId) {
                    _connectedDevice.value = null
                    _connectionState.value = if (enabled.value) LanLinkConnectionState.IDLE else LanLinkConnectionState.DISABLED
                    _syncState.value = LanLinkSyncState.IDLE
                    _syncStatusMessage.value = if (enabled.value) "等待手机连接" else "已关闭"
                }
            }
            _messages.tryEmit("已取消配对")
        }
    }

    private suspend fun startHost() {
        stopHostInternal(sendBye = false)
        val localDeviceId = lanLinkPreferences.ensureLocalDeviceId().also { cachedLocalDeviceId = it }
        val tcp = try {
            ServerSocket(0)
        } catch (e: IOException) {
            AppLogger.w(TAG, "启动局域网联动 TCP 监听失败", e)
            _connectionState.value = LanLinkConnectionState.IDLE
            _syncStatusMessage.value = "端口监听失败"
            return
        }
        val udp = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(LanLinkConstants.UDP_DISCOVERY_PORT))
            }
        } catch (e: IOException) {
            AppLogger.w(TAG, "启动局域网联动 UDP 发现失败", e)
            runCatching { tcp.close() }
            _connectionState.value = LanLinkConnectionState.IDLE
            _syncStatusMessage.value = "发现端口 ${LanLinkConstants.UDP_DISCOVERY_PORT} 被占用"
            return
        }

        serverSocket = tcp
        udpSocket = udp
        _connectionState.value = LanLinkConnectionState.IDLE
        _syncState.value = LanLinkSyncState.IDLE
        _syncStatusMessage.value = "等待手机连接"

        val tcpPort = tcp.localPort
        serverJob = scope.launch {
            launch(Dispatchers.IO) { runUdpDiscoveryLoop(udp, localDeviceId, tcpPort) }
            launch(Dispatchers.IO) { runTcpAcceptLoop(tcp, localDeviceId) }
        }
    }

    private suspend fun stopHost(sendBye: Boolean = true, reason: String = "normal_disconnect") {
        stopHostInternal(sendBye = sendBye, reason = reason)
        _connectionState.value = LanLinkConnectionState.DISABLED
        _syncState.value = LanLinkSyncState.IDLE
        _syncStatusMessage.value = "已关闭"
    }

    private suspend fun stopHostInternal(sendBye: Boolean = false, reason: String = "normal_disconnect") {
        cancelPairingCode()
        serverJob?.cancel()
        serverJob = null
        val previous = synchronized(sessionLock) {
            val s = activeSession
            activeSession = null
            _connectedDevice.value = null
            s
        }
        if (sendBye) {
            previous?.sendByeAndClose(reason)
        } else {
            previous?.close()
        }
        runCatching { udpSocket?.close() }
        udpSocket = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun runUdpDiscoveryLoop(udp: DatagramSocket, localDeviceId: String, tcpPort: Int) {
        val buffer = ByteArray(2048)
        while (!udp.isClosed) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                udp.receive(packet)
                val payload = packet.data.copyOfRange(packet.offset, packet.offset + packet.length).decodeToString()
                val probe = runCatching { LanLinkJson.decodeFromString<LanDiscoveryProbe>(payload) }.getOrNull()
                    ?: continue
                if (probe.clientDeviceId == localDeviceId) continue

                val response = LanDiscoveryResponse(
                    deviceId = localDeviceId,
                    deviceName = deviceNameProvider(),
                    tcpPort = tcpPort,
                    protocolVersion = LanLinkConstants.PROTOCOL_VERSION
                )
                val bytes = LanLinkJson.encodeToString(response).encodeToByteArray()
                udp.send(DatagramPacket(bytes, bytes.size, packet.address, packet.port))
            } catch (_: IOException) {
                if (!udp.isClosed) continue
                break
            } catch (_: Exception) {
                if (udp.isClosed) break
            }
        }
    }

    private fun runTcpAcceptLoop(tcp: ServerSocket, localDeviceId: String) {
        while (!tcp.isClosed) {
            val client = try {
                tcp.accept()
            } catch (_: IOException) {
                break
            }
            runCatching {
                client.keepAlive = true
                client.tcpNoDelay = true
            }
            scope.launch(Dispatchers.IO) {
                handleClientSocket(client, localDeviceId)
            }
        }
    }

    private suspend fun handleClientSocket(socket: Socket, localDeviceId: String) {
        var startedSession: LanLinkSession? = null
        try {
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val firstFrame = LanLinkFrameCodec.readFrame(socket.getInputStream()) ?: return
            val firstMsg = LanLinkJson.decodeFromString<LanLinkMessage>(firstFrame.decodeToString())

            val sessionContext = when (firstMsg) {
                is LanLinkMessage.PairHello -> handlePairingHandshake(socket, localDeviceId, firstMsg)
                is LanLinkMessage.AuthHello -> handleAuthHandshake(socket, localDeviceId, firstMsg)
                else -> null
            } ?: return

            startedSession = sessionContext
            // 先把新会话置为 activeSession，再关闭旧会话，避免旧会话 finally 误清当前连接状态
            val previous = synchronized(sessionLock) {
                val prev = activeSession
                activeSession = sessionContext
                _sessionSequence.value += 1L
                _connectedDevice.value = ConnectedPeerInfo(sessionContext.peerDeviceId, sessionContext.peerDeviceName)
                _connectionState.value = LanLinkConnectionState.CONNECTED
                _syncStatusMessage.value = "已连接，正在核对账号…"
                prev
            }
            previous?.close()

            sessionContext.run()
        } catch (e: Exception) {
            if (!socket.isClosed) AppLogger.d(TAG, "连接已结束: ${e.message}")
        } finally {
            if (startedSession == null) runCatching { socket.close() }
            if (startedSession != null) {
                var cleared = false
                synchronized(sessionLock) {
                    if (activeSession === startedSession) {
                        activeSession = null
                        _connectedDevice.value = null
                        _syncState.value = LanLinkSyncState.IDLE
                        _connectionState.value = if (enabled.value) LanLinkConnectionState.IDLE else LanLinkConnectionState.DISABLED
                        _syncStatusMessage.value = if (enabled.value) "等待手机连接" else "已关闭"
                        cleared = true
                    }
                }
                if (cleared) startedSession.close()
            }
        }
    }

    private suspend fun handlePairingHandshake(
        socket: Socket,
        localDeviceId: String,
        hello: LanLinkMessage.PairHello
    ): LanLinkSession? {
        val currentPairing = pairingManager.currentState()
        if (currentPairing == null) {
            writePlainMessage(socket, LanLinkMessage.PairGrant(success = false, error = "桌面端未开启配对或配对码已失效"))
            return null
        }

        val clientNonce = runCatching { LanLinkCrypto.fromBase64(hello.clientNonceBase64) }.getOrNull()
        if (clientNonce == null || clientNonce.size < 16) {
            writePlainMessage(socket, LanLinkMessage.PairGrant(success = false, error = "无效的握手参数"))
            return null
        }

        val serverNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES)
        val salt = LanLinkCrypto.randomBytes(LanLinkCrypto.SALT_BYTES)
        val serverName = deviceNameProvider()

        writePlainMessage(
            socket,
            LanLinkMessage.PairChallenge(
                serverDeviceId = localDeviceId,
                serverDeviceName = serverName,
                serverNonceBase64 = LanLinkCrypto.toBase64(serverNonce),
                saltBase64 = LanLinkCrypto.toBase64(salt)
            )
        )

        val verifyFrame = LanLinkFrameCodec.readFrame(socket.getInputStream()) ?: return null
        val verifyMsg = LanLinkJson.decodeFromString<LanLinkMessage>(verifyFrame.decodeToString()) as? LanLinkMessage.PairVerify
            ?: return null
        val clientProof = runCatching { LanLinkCrypto.fromBase64(verifyMsg.clientProofBase64) }.getOrNull() ?: return null

        var derivedPairingKey: ByteArray? = null
        val outcome = pairingManager.verifyAttempt { code ->
            val key = LanLinkCrypto.derivePairingKey(code, salt)
            val expectedProof = LanLinkCrypto.pairingClientProof(
                pairingKey = key,
                clientNonce = clientNonce,
                serverNonce = serverNonce,
                clientDeviceId = hello.clientDeviceId,
                serverDeviceId = localDeviceId
            )
            val matched = LanLinkCrypto.constantTimeEquals(expectedProof, clientProof)
            if (matched) derivedPairingKey = key else key.fill(0)
            matched
        }
        refreshPairingDisplay()

        return when (outcome) {
            is PairingVerifyOutcome.Verified -> {
                val pairingKey = derivedPairingKey ?: return null
                try {
                    val serverProof = LanLinkCrypto.pairingServerProof(
                        pairingKey = pairingKey,
                        clientNonce = clientNonce,
                        serverNonce = serverNonce,
                        clientDeviceId = hello.clientDeviceId,
                        serverDeviceId = localDeviceId
                    )
                    val deviceKey = LanLinkCrypto.randomBytes(LanLinkCrypto.AES_KEY_BYTES)
                    val sealedDeviceKey = LanLinkCrypto.sealWithRandomIv(pairingKey, deviceKey)

                    writePlainMessage(
                        socket,
                        LanLinkMessage.PairGrant(
                            success = true,
                            serverProofBase64 = LanLinkCrypto.toBase64(serverProof),
                            encryptedDeviceKeyBase64 = LanLinkCrypto.toBase64(sealedDeviceKey)
                        )
                    )

                    val peer = PairedLanDevice(
                        deviceId = hello.clientDeviceId,
                        deviceName = hello.clientDeviceName.ifBlank { "Android 设备" },
                        deviceKeyBase64 = LanLinkCrypto.toBase64(deviceKey),
                        pairedAtMs = System.currentTimeMillis()
                    )
                    lanLinkPreferences.savePairedDevice(peer)
                    cancelPairingCode()
                    _messages.tryEmit("已与 ${peer.deviceName} 完成配对")

                    val sessionKey = LanLinkCrypto.deriveSessionKey(deviceKey, clientNonce, serverNonce)
                    createSession(socket, sessionKey, peer.deviceId, peer.deviceName)
                } finally {
                    pairingKey.fill(0)
                }
            }

            is PairingVerifyOutcome.InvalidCode -> {
                writePlainMessage(
                    socket,
                    LanLinkMessage.PairGrant(success = false, error = "配对码错误，还剩 ${outcome.remainingAttempts} 次机会")
                )
                null
            }

            PairingVerifyOutcome.LockedOut -> {
                cancelPairingCode()
                _messages.tryEmit("配对码错误次数过多，已作废")
                writePlainMessage(socket, LanLinkMessage.PairGrant(success = false, error = "错误次数过多，配对码已作废"))
                null
            }

            PairingVerifyOutcome.UnavailableOrExpired -> {
                cancelPairingCode()
                writePlainMessage(socket, LanLinkMessage.PairGrant(success = false, error = "配对码已失效或过期"))
                null
            }
        }
    }

    private suspend fun handleAuthHandshake(
        socket: Socket,
        localDeviceId: String,
        hello: LanLinkMessage.AuthHello
    ): LanLinkSession? {
        val paired = lanLinkPreferences.getPairedDevice(hello.clientDeviceId)
        if (paired == null) {
            writePlainMessage(socket, LanLinkMessage.AuthResult(success = false, error = "设备未配对"))
            return null
        }

        val clientNonce = runCatching { LanLinkCrypto.fromBase64(hello.clientNonceBase64) }.getOrNull()
        val deviceKey = runCatching { LanLinkCrypto.fromBase64(paired.deviceKeyBase64) }.getOrNull()
        if (clientNonce == null || clientNonce.size < 16 || deviceKey == null || deviceKey.size != LanLinkCrypto.AES_KEY_BYTES) {
            writePlainMessage(socket, LanLinkMessage.AuthResult(success = false, error = "认证凭据无效"))
            return null
        }

        val serverNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES)
        val serverProof = LanLinkCrypto.authServerProof(
            deviceKey = deviceKey,
            clientNonce = clientNonce,
            serverNonce = serverNonce,
            clientDeviceId = hello.clientDeviceId,
            serverDeviceId = localDeviceId
        )

        writePlainMessage(
            socket,
            LanLinkMessage.AuthChallenge(
                serverDeviceId = localDeviceId,
                serverDeviceName = deviceNameProvider(),
                serverNonceBase64 = LanLinkCrypto.toBase64(serverNonce),
                serverProofBase64 = LanLinkCrypto.toBase64(serverProof)
            )
        )

        val verifyFrame = LanLinkFrameCodec.readFrame(socket.getInputStream()) ?: return null
        val verifyMsg = LanLinkJson.decodeFromString<LanLinkMessage>(verifyFrame.decodeToString()) as? LanLinkMessage.AuthVerify
            ?: return null
        val clientProof = runCatching { LanLinkCrypto.fromBase64(verifyMsg.clientProofBase64) }.getOrNull() ?: return null

        val expectedClientProof = LanLinkCrypto.authClientProof(
            deviceKey = deviceKey,
            clientNonce = clientNonce,
            serverNonce = serverNonce,
            clientDeviceId = hello.clientDeviceId,
            serverDeviceId = localDeviceId
        )
        if (!LanLinkCrypto.constantTimeEquals(expectedClientProof, clientProof)) {
            writePlainMessage(socket, LanLinkMessage.AuthResult(success = false, error = "设备密钥校验失败"))
            return null
        }

        writePlainMessage(socket, LanLinkMessage.AuthResult(success = true))
        val peerName = hello.clientDeviceName.ifBlank { paired.deviceName }
        if (peerName != paired.deviceName) {
            lanLinkPreferences.savePairedDevice(paired.copy(deviceName = peerName))
        }
        val sessionKey = LanLinkCrypto.deriveSessionKey(deviceKey, clientNonce, serverNonce)
        return createSession(socket, sessionKey, paired.deviceId, peerName)
    }

    override suspend fun sendMessage(message: LanLinkMessage): Boolean {
        val session = activeSession ?: return false
        return runCatching {
            session.sendMessage(message)
            true
        }.onFailure {
            AppLogger.w(TAG, "发送局域网消息失败: ${it.message}")
            session.close()
            synchronized(sessionLock) {
                if (activeSession === session) {
                    activeSession = null
                    _connectedDevice.value = null
                    _syncState.value = LanLinkSyncState.IDLE
                    _connectionState.value = if (enabled.value) LanLinkConnectionState.IDLE else LanLinkConnectionState.DISABLED
                    _syncStatusMessage.value = if (enabled.value) "等待手机连接" else "已关闭"
                }
            }
        }.getOrDefault(false)
    }

    override fun emitUserMessage(message: String) {
        _messages.tryEmit(message)
    }

    private fun createSession(
        socket: Socket,
        sessionKey: ByteArray,
        peerDeviceId: String,
        peerDeviceName: String
    ): LanLinkSession = LanLinkSession(
        socket = socket,
        sessionKey = sessionKey,
        isHost = true,
        peerDeviceId = peerDeviceId,
        peerDeviceName = peerDeviceName,
        store = playlistExternalTrackStore,
        userPreferences = userPreferences,
        onUnpairedByPeer = {
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
            _syncStatusMessage.value = "$peerDeviceName 已断开连接"
        }
    )

    private fun refreshPairingDisplay(): Boolean {
        val current = pairingManager.currentState()
        if (current == null) {
            _pairingDisplay.value = null
            return false
        }
        val remainingSec = ((current.expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toInt()
        _pairingDisplay.value = PairingDisplayState(
            code = current.code,
            remainingSeconds = remainingSec,
            remainingAttempts = current.remainingAttempts
        )
        return true
    }

    private fun writePlainMessage(socket: Socket, message: LanLinkMessage) {
        val bytes = LanLinkJson.encodeToString<LanLinkMessage>(message).encodeToByteArray()
        LanLinkFrameCodec.writeFrame(socket.getOutputStream(), bytes)
    }
}
