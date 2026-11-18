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
private val RECONNECT_BACKOFF_MS = longArrayOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L)

// 平台锁抽象：Android 仅在 UDP 发现期间持有多播锁，结束后立即释放
interface LanDiscoveryLock {
    fun <T> withLock(block: () -> T): T
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
) {
    val enabled: StateFlow<Boolean> = settingsPreferences.lanLinkEnabled
        .stateIn(scope, SharingStarted.Eagerly, false)

    val pairedDevices: StateFlow<List<PairedLanDevice>> = lanLinkPreferences.pairedDevices
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredLanDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredLanDevice>> = _discoveredDevices.asStateFlow()

    private val _connectionState = MutableStateFlow(LanLinkConnectionState.DISABLED)
    val connectionState: StateFlow<LanLinkConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<ConnectedPeerInfo?>(null)
    val connectedDevice: StateFlow<ConnectedPeerInfo?> = _connectedDevice.asStateFlow()

    private val _syncState = MutableStateFlow(LanLinkSyncState.IDLE)
    val syncState: StateFlow<LanLinkSyncState> = _syncState.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow("已关闭")
    val syncStatusMessage: StateFlow<String> = _syncStatusMessage.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val knownEndpoints = ConcurrentHashMap<String, Pair<String, Int>>()

    private var started = false
    private var lifecycleJob: Job? = null
    private var reconnectJob: Job? = null

    @Volatile
    private var activeSession: LanLinkSession? = null

    @Synchronized
    fun start() {
        if (started) return
        started = true
        lifecycleJob = scope.launch {
            combine(settingsPreferences.lanLinkEnabled, lanLinkPreferences.pairedDevices) { on, paired -> on to paired }
                .distinctUntilChanged()
                .collectLatest { (isEnabled, paired) ->
                    if (!isEnabled) {
                        shutdownConnectionLoop()
                        return@collectLatest
                    }
                    if (paired.isEmpty()) {
                        activeSession?.close()
                        activeSession = null
                        _connectedDevice.value = null
                        _connectionState.value = LanLinkConnectionState.IDLE
                        _syncState.value = LanLinkSyncState.IDLE
                        _syncStatusMessage.value = "未配对桌面端设备"
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
        lifecycleJob?.cancel()
        lifecycleJob = null
        scope.launch { shutdownConnectionLoop() }
    }

    suspend fun discoverDevices(): List<DiscoveredLanDevice> {
        if (!enabled.value) return emptyList()
        _isDiscovering.value = true
        if (activeSession == null) {
            _connectionState.value = LanLinkConnectionState.DISCOVERING
        }
        return try {
            val localId = lanLinkPreferences.ensureLocalDeviceId()
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
            val current = activeSession
            if (current != null && current.peerDeviceId == deviceId) {
                current.sendUnpairedAndClose()
                if (activeSession === current) activeSession = null
            }
            knownEndpoints.remove(deviceId)
            lanLinkPreferences.removePairedDevice(deviceId)
            if (_connectedDevice.value?.deviceId == deviceId) {
                _connectedDevice.value = null
                _connectionState.value = if (enabled.value) LanLinkConnectionState.IDLE else LanLinkConnectionState.DISABLED
                _syncState.value = LanLinkSyncState.IDLE
                _syncStatusMessage.value = if (enabled.value) "未连接" else "已关闭"
            }
            _messages.tryEmit("已取消配对")
        }
    }

    fun triggerReconnectNow() {
        if (!enabled.value || activeSession != null) return
        ensureReconnectLoop(restart = true)
    }

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
                if (activeSession != null) break

                val connected = tryConnectAnyPairedDevice(paired)
                if (connected) {
                    backoffIdx = 0
                    // 会话协程阻塞直到断线，随后进入下一次退避重连
                    while (isActive && enabled.value && activeSession != null) {
                        delay(1_000L)
                    }
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
            if (connectAndAuthenticate(device, cached.first, cached.second)) return true
        }

        val discovered = discoverDevices()
        for (candidate in discovered) {
            val pairedDevice = pairedById[candidate.deviceId] ?: continue
            if (connectAndAuthenticate(pairedDevice, candidate.hostAddress, candidate.tcpPort)) {
                return true
            }
        }
        return false
    }

    private suspend fun connectAndAuthenticate(
        paired: PairedLanDevice,
        host: String,
        port: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val deviceKey = runCatching { LanLinkCrypto.fromBase64(paired.deviceKeyBase64) }.getOrNull()
        if (deviceKey == null || deviceKey.size != LanLinkCrypto.AES_KEY_BYTES) return@withContext false

        val localId = lanLinkPreferences.ensureLocalDeviceId()
        val clientNonce = LanLinkCrypto.randomBytes(LanLinkCrypto.NONCE_BYTES)
        val socket = Socket()
        var handedOff = false
        try {
            _connectionState.value = LanLinkConnectionState.CONNECTING
            _syncStatusMessage.value = "正在连接 ${paired.deviceName}…"
            socket.connect(InetSocketAddress(host, port), TCP_CONNECT_TIMEOUT_MS)
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
            launchAuthenticatedSession(socket, sessionKey, paired.deviceId, peerName)
            true
        } catch (_: Exception) {
            false
        } finally {
            if (!handedOff) runCatching { socket.close() }
        }
    }

    private fun launchAuthenticatedSession(
        socket: Socket,
        sessionKey: ByteArray,
        peerDeviceId: String,
        peerDeviceName: String
    ) {
        reconnectJob?.cancel()
        reconnectJob = null
        activeSession?.close()
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
            }
        )
        activeSession = session
        _connectedDevice.value = ConnectedPeerInfo(peerDeviceId, peerDeviceName)
        _connectionState.value = LanLinkConnectionState.CONNECTED
        _syncStatusMessage.value = "已连接，正在核对账号…"

        scope.launch(Dispatchers.IO) {
            try {
                session.run()
            } finally {
                if (activeSession === session) {
                    activeSession = null
                    _connectedDevice.value = null
                    _syncState.value = LanLinkSyncState.IDLE
                    _connectionState.value = if (enabled.value) LanLinkConnectionState.IDLE else LanLinkConnectionState.DISABLED
                    _syncStatusMessage.value = if (enabled.value) "已断开，准备重连" else "已关闭"
                    if (enabled.value && pairedDevices.value.isNotEmpty()) {
                        ensureReconnectLoop()
                    }
                }
            }
        }
    }

    private suspend fun shutdownConnectionLoop() {
        reconnectJob?.cancel()
        reconnectJob = null
        activeSession?.close()
        activeSession = null
        _connectedDevice.value = null
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
