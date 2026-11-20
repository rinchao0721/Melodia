package com.lin0721.linmusic.core.lanlink

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

private const val TAG = "LanLinkRemote"
private const val POSITION_JUMP_THRESHOLD_MS = 1_500L
private const val SUPPRESSION_WINDOW_MS = 500L

// 当前生效的播放控制目标
sealed interface PlaybackTarget {
    data object Local : PlaybackTarget
    data class Remote(val peer: ConnectedPeerInfo) : PlaybackTarget
}

// 各平台音量读写桥接：桌面对应 mpv 百分比，Android 对应系统媒体音量
interface RemoteVolumeBridge {
    val volume: StateFlow<Int>
    val lastAudibleVolume: StateFlow<Int>
    fun setVolume(percent: Int)
}

// 联动播放模式及修改时间戳，用于跨端 Last-Write-Wins 同步
data class LanLinkPlaybackModeState(
    val mode: LanLinkPlaybackMode = LanLinkPlaybackMode.SINGLE_DEVICE,
    val updatedAtMs: Long = 0L
)

// 联动播放模式持久化抽象，避免协调器直接耦合 DataStore 实现
interface LanLinkPlaybackModeStore {
    val state: Flow<LanLinkPlaybackModeState>
    suspend fun applySynced(mode: LanLinkPlaybackMode, updatedAtMs: Long)
}

// 默认内存模式存储，供测试或未注入偏好存储时使用
class InMemoryLanLinkPlaybackModeStore(
    initial: LanLinkPlaybackModeState = LanLinkPlaybackModeState()
) : LanLinkPlaybackModeStore {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<LanLinkPlaybackModeState> = _state.asStateFlow()

    fun setFromUser(mode: LanLinkPlaybackMode, updatedAtMs: Long) {
        val current = _state.value
        val effectiveTs = maxOf(updatedAtMs, current.updatedAtMs + 1L)
        _state.value = LanLinkPlaybackModeState(mode, effectiveTs)
    }

    override suspend fun applySynced(mode: LanLinkPlaybackMode, updatedAtMs: Long) {
        val current = _state.value
        if (updatedAtMs >= current.updatedAtMs) {
            _state.value = LanLinkPlaybackModeState(mode, updatedAtMs)
        }
    }
}

// 局域网双向遥控协调器：实现单一活跃播放设备协商 / 各自独立播放切换、切端接力与同步回退
class LanLinkRemoteCoordinator(
    private val localController: PlaybackController,
    private val transport: LanLinkTransport,
    private val volumeBridge: RemoteVolumeBridge,
    private val scope: CoroutineScope,
    private val commandDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val playbackModeStore: LanLinkPlaybackModeStore = InMemoryLanLinkPlaybackModeStore()
) {
    private val _activeTarget = MutableStateFlow<PlaybackTarget>(PlaybackTarget.Local)
    val activeTarget: StateFlow<PlaybackTarget> = _activeTarget.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // 串行化发往传输层的控制指令与状态推送，避免并发乱序
    private val outboundChannel = Channel<LanLinkMessage>(capacity = Channel.UNLIMITED)

    val remoteController: RemotePlaybackController = RemotePlaybackController(
        sendCommand = { msg -> enqueueOutbound(msg) },
        onUserMessage = { msg -> _messages.tryEmit(msg) },
        scope = scope,
        clockMs = clockMs
    )

    private val queueVersionCounter = AtomicLong(0L)
    private val modeMutex = Mutex()

    @Volatile
    private var started = false

    @Volatile
    private var currentQueueVersion = 0L

    @Volatile
    private var lastPushedPositionMs = 0L

    @Volatile
    private var lastPushedAtMs = 0L

    @Volatile
    private var lastPushedIsPlaying = false

    @Volatile
    private var lastPushedSignature: StateSignature? = null

    @Volatile
    private var currentEpoch = 0L

    @Volatile
    private var lastDeclaredActiveDeviceId: String? = null

    @Volatile
    private var peerIsHost = false

    @Volatile
    private var lastLocalActiveAtMs = 0L

    @Volatile
    private var ignoreLocalPlayUntilMs = 0L

    @Volatile
    private var latestModeState = LanLinkPlaybackModeState()

    @Volatile
    private var modeInitialized = false

    @Volatile
    private var lastRemoteAppliedModeState: LanLinkPlaybackModeState? = null

    private var heartbeatJob: Job? = null

    private data class StateSignature(
        val isPlaying: Boolean,
        val playWhenReady: Boolean,
        val mediaId: String?,
        val title: String?,
        val artist: String?,
        val artworkUri: String?,
        val durationMs: Long,
        val playMode: Any,
        val playContext: String?,
        val playSource: Any?,
        val currentIndex: Int,
        val volumePercent: Int,
        val queueVersion: Long
    )

    @Synchronized
    fun start() {
        if (started) return
        started = true

        scope.launch {
            for (msg in outboundChannel) {
                transport.sendMessage(msg)
            }
        }

        // 监听联动播放模式变化：仅将本地主动修改同步给已连接对端，远端回写不回传以避免循环
        scope.launch {
            playbackModeStore.state.collect { incoming ->
                var syncMessage: LanLinkMessage.RemotePlaybackModeSync? = null
                var transition: Pair<LanLinkPlaybackMode, LanLinkPlaybackMode>? = null
                modeMutex.withLock {
                    if (!modeInitialized) {
                        latestModeState = incoming
                        modeInitialized = true
                        return@withLock
                    }
                    if (incoming == lastRemoteAppliedModeState ||
                        incoming == latestModeState ||
                        incoming.updatedAtMs < latestModeState.updatedAtMs
                    ) {
                        return@withLock
                    }
                    val oldMode = latestModeState.mode
                    latestModeState = incoming
                    if (transport.enabled.value && transport.connectedDevice.value != null) {
                        syncMessage = LanLinkMessage.RemotePlaybackModeSync(
                            mode = incoming.mode,
                            updatedAtMs = incoming.updatedAtMs
                        )
                    }
                    if (oldMode != incoming.mode) {
                        transition = oldMode to incoming.mode
                    }
                }
                syncMessage?.let { enqueueOutbound(it) }
                transition?.let { (oldMode, newMode) ->
                    handleRuntimeModeTransition(oldMode, newMode, transport.connectedDevice.value)
                }
            }
        }

        // 监听连接状态：连上时先清理残留远程态并握手，断开时立即复位遥控态并回退本机
        scope.launch {
            var hadPeer = false
            combine(
                transport.enabled,
                transport.connectedDevice,
                transport.sessionSequence
            ) { enabled, peer, seq ->
                Triple(if (enabled) peer else null, seq, enabled)
            }.distinctUntilChanged().collect { (peer, _, _) ->
                if (peer == null) {
                    stopHeartbeat()
                    currentEpoch = 0L
                    lastDeclaredActiveDeviceId = null
                    peerIsHost = false
                    if (hadPeer && _activeTarget.value is PlaybackTarget.Remote) {
                        _activeTarget.value = PlaybackTarget.Local
                        _messages.tryEmit("连接已断开")
                    } else if (_activeTarget.value is PlaybackTarget.Remote) {
                        _activeTarget.value = PlaybackTarget.Local
                    }
                    remoteController.reset()
                    hadPeer = false
                } else {
                    hadPeer = true
                    stopHeartbeat()
                    currentEpoch = 0L
                    lastDeclaredActiveDeviceId = null
                    peerIsHost = false
                    if (_activeTarget.value is PlaybackTarget.Remote) {
                        _activeTarget.value = PlaybackTarget.Local
                    }
                    remoteController.reset()
                    pushQueueSnapshot()
                    pushStateSnapshot(force = true)
                    sendPlaybackHello()
                    if (localController.isPlaying.value) {
                        startHeartbeat()
                    }
                }
            }
        }

        // 监听本机直接起播：单一设备模式下抢占活跃设备；独立模式下不抢占对端、不广播 RemoteActiveDevice
        scope.launch {
            var previousPlaying: Boolean? = null
            combine(localController.isPlaying, localController.playWhenReady) { playing, ready ->
                playing || ready
            }.distinctUntilChanged().collect { nowPlayingOrReady ->
                val prev = previousPlaying
                previousPlaying = nowPlayingOrReady
                if (nowPlayingOrReady) {
                    lastLocalActiveAtMs = clockMs()
                }
                if (prev == false && nowPlayingOrReady && clockMs() >= ignoreLocalPlayUntilMs) {
                    val mode = ensureModeInitialized().mode
                    if (mode == LanLinkPlaybackMode.INDEPENDENT) {
                        if (_activeTarget.value is PlaybackTarget.Remote) {
                            _activeTarget.value = PlaybackTarget.Local
                        }
                    } else if (
                        transport.enabled.value &&
                        transport.connectedDevice.value != null &&
                        (_activeTarget.value is PlaybackTarget.Remote ||
                            lastDeclaredActiveDeviceId != transport.localDeviceId)
                    ) {
                        declareLocalActiveAndSync()
                    }
                }
            }
        }

        // 监听本机队列变化：仅在队列实际改变时递增版本号并推送给已连接对端
        scope.launch {
            combine(
                localController.queue,
                localController.currentIndex,
                localController.playContext,
                localController.playSource
            ) { items, index, ctx, source ->
                Quad(items, index, ctx, source)
            }.distinctUntilChanged().collect { (items, index, ctx, source) ->
                val version = queueVersionCounter.incrementAndGet()
                currentQueueVersion = version
                if (transport.enabled.value && transport.connectedDevice.value != null) {
                    enqueueOutbound(
                        LanLinkMessage.RemoteQueueSync(
                            queueVersion = version,
                            items = items.map { it.copy(localUri = null) },
                            currentIndex = index,
                            playContext = ctx,
                            playSource = source
                        )
                    )
                    pushStateSnapshot(force = true)
                }
            }
        }

        // 监听本机播放状态与音量变化：非位置字段变化立即推，播放中位置按 ~1s 节流或发生跳变时推
        scope.launch {
            combine(
                combine(
                    localController.isPlaying,
                    localController.playWhenReady,
                    localController.nowPlaying,
                    localController.duration,
                    localController.playMode
                ) { playing, ready, track, dur, mode ->
                    PlaybackMetaPart(playing, ready, track, dur, mode)
                },
                combine(
                    localController.playContext,
                    localController.playSource,
                    localController.currentIndex,
                    volumeBridge.volume,
                    localController.currentPosition
                ) { ctx, source, index, vol, pos ->
                    PlaybackTailPart(ctx, source, index, vol, pos)
                }
            ) { meta, tail ->
                meta to tail
            }.collect { (meta, tail) ->
                if (meta.isPlaying && transport.enabled.value && transport.connectedDevice.value != null) {
                    startHeartbeat()
                } else {
                    stopHeartbeat()
                }
                if (!transport.enabled.value || transport.connectedDevice.value == null) return@collect

                val sig = StateSignature(
                    isPlaying = meta.isPlaying,
                    playWhenReady = meta.playWhenReady,
                    mediaId = meta.track?.mediaId,
                    title = meta.track?.title,
                    artist = meta.track?.artist,
                    artworkUri = meta.track?.artworkUri,
                    durationMs = meta.duration,
                    playMode = meta.playMode,
                    playContext = tail.playContext,
                    playSource = tail.playSource,
                    currentIndex = tail.currentIndex,
                    volumePercent = tail.volumePercent,
                    queueVersion = currentQueueVersion
                )
                val now = clockMs()
                val signatureChanged = sig != lastPushedSignature
                val jumped = isPositionJumped(tail.positionMs, meta.isPlaying, now)
                val throttleElapsed = meta.isPlaying && (now - lastPushedAtMs) >= LanLinkConstants.REMOTE_STATE_THROTTLE_MS
                if (signatureChanged || jumped || throttleElapsed) {
                    pushStateSnapshot(force = true)
                }
            }
        }

        // 接收来自对端的状态推送与遥控指令
        scope.launch {
            transport.incomingMessages.collect { incoming ->
                handleIncomingMessage(incoming.peer, incoming.message)
            }
        }
    }

    fun emitMessage(message: String) {
        _messages.tryEmit(message)
    }

    private fun currentMode(): LanLinkPlaybackMode =
        if (modeInitialized) {
            latestModeState.mode
        } else {
            (playbackModeStore.state as? StateFlow<LanLinkPlaybackModeState>)?.value?.mode
                ?: latestModeState.mode
        }

    private suspend fun ensureModeInitialized(): LanLinkPlaybackModeState {
        return modeMutex.withLock {
            if (!modeInitialized) {
                latestModeState = playbackModeStore.state.first()
                modeInitialized = true
            }
            latestModeState
        }
    }

    fun switchToRemote(): Boolean {
        val peer = transport.connectedDevice.value
        if (!transport.enabled.value || peer == null) {
            _messages.tryEmit("请先连接局域网联动设备")
            return false
        }
        val currentTarget = _activeTarget.value
        if (currentTarget is PlaybackTarget.Remote && currentTarget.peer.deviceId == peer.deviceId) {
            return true
        }
        val mode = currentMode()
        val currentNowPlaying = localController.nowPlaying.value
        val currentItem = localController.currentQueueItem.value
        if (currentNowPlaying == null && currentItem == null) {
            _activeTarget.value = PlaybackTarget.Remote(peer)
            if (mode == LanLinkPlaybackMode.SINGLE_DEVICE) {
                lastDeclaredActiveDeviceId = peer.deviceId
                enqueueOutbound(
                    LanLinkMessage.RemoteActiveDevice(
                        activeDeviceId = peer.deviceId,
                        activeDeviceName = peer.deviceName,
                        epoch = nextEpoch(),
                        timestampMs = clockMs()
                    )
                )
            }
            enqueueOutbound(LanLinkMessage.RemoteRequestSync)
            return true
        }
        if (currentItem == null || RemotePlaybackController.isLocalOnlyQueueItem(currentItem)) {
            _messages.tryEmit("本地歌曲无法在远程设备播放")
            return false
        }
        val posMs = runCatching { localController.currentPositionNow() }
            .getOrDefault(localController.currentPosition.value)
            .coerceAtLeast(0L)
        ignoreLocalPlayUntilMs = clockMs() + SUPPRESSION_WINDOW_MS
        scope.launch(commandDispatcher) {
            localController.pause()
        }
        if (mode == LanLinkPlaybackMode.SINGLE_DEVICE) {
            lastDeclaredActiveDeviceId = peer.deviceId
        }
        _activeTarget.value = PlaybackTarget.Remote(peer)
        enqueueOutbound(
            LanLinkMessage.RemoteHandoffPlay(
                targetDeviceId = peer.deviceId,
                item = currentItem.copy(localUri = null),
                startPositionMs = posMs,
                epoch = nextEpoch(),
                timestampMs = clockMs()
            )
        )
        enqueueOutbound(LanLinkMessage.RemoteRequestSync)
        return true
    }

    fun switchToLocal(): Boolean {
        val currentTarget = _activeTarget.value
        if (currentTarget is PlaybackTarget.Local) return true
        val peer = (currentTarget as? PlaybackTarget.Remote)?.peer ?: transport.connectedDevice.value
        val mode = currentMode()
        val remoteNowPlaying = remoteController.nowPlaying.value
        val remoteItem = remoteController.currentQueueItem.value
        if (remoteNowPlaying == null && remoteItem == null) {
            _activeTarget.value = PlaybackTarget.Local
            if (mode == LanLinkPlaybackMode.SINGLE_DEVICE) {
                lastDeclaredActiveDeviceId = transport.localDeviceId
                if (transport.enabled.value && peer != null) {
                    enqueueOutbound(
                        LanLinkMessage.RemoteActiveDevice(
                            activeDeviceId = transport.localDeviceId,
                            activeDeviceName = transport.localDeviceName,
                            epoch = nextEpoch(),
                            timestampMs = clockMs()
                        )
                    )
                }
            }
            return true
        }
        if (remoteItem == null || RemotePlaybackController.isLocalOnlyQueueItem(remoteItem)) {
            _messages.tryEmit("本地歌曲无法在远程设备播放")
            return false
        }
        val posMs = runCatching { remoteController.currentPositionNow() }
            .getOrDefault(remoteController.currentPosition.value)
            .coerceAtLeast(0L)
        ignoreLocalPlayUntilMs = clockMs() + SUPPRESSION_WINDOW_MS
        lastLocalActiveAtMs = clockMs()
        _activeTarget.value = PlaybackTarget.Local
        if (transport.enabled.value && peer != null) {
            if (mode == LanLinkPlaybackMode.SINGLE_DEVICE) {
                lastDeclaredActiveDeviceId = transport.localDeviceId
                enqueueOutbound(
                    LanLinkMessage.RemoteActiveDevice(
                        activeDeviceId = transport.localDeviceId,
                        activeDeviceName = transport.localDeviceName,
                        epoch = nextEpoch(),
                        timestampMs = clockMs()
                    )
                )
            } else if (remoteController.isPlaying.value || remoteController.playWhenReady.value) {
                // 独立播放模式下切回本机仅暂停远端接力播放，不发送会抢占控制目标的 RemoteActiveDevice
                enqueueOutbound(LanLinkMessage.RemoteCommand(RemoteCommandAction.PAUSE))
            }
        }
        scope.launch(commandDispatcher) {
            localController.playNextNow(remoteItem, posMs)
            pushQueueSnapshot()
            pushStateSnapshot(force = true)
        }
        return true
    }

    private fun nextEpoch(): Long {
        currentEpoch += 1L
        return currentEpoch
    }

    private suspend fun sendPlaybackHello() {
        if (!transport.enabled.value || transport.connectedDevice.value == null) return
        val modeState = ensureModeInitialized()
        enqueueOutbound(
            LanLinkMessage.RemotePlaybackHello(
                deviceId = transport.localDeviceId,
                deviceName = transport.localDeviceName,
                isPlaying = localController.isPlaying.value,
                playWhenReady = localController.playWhenReady.value,
                hasTrack = localController.nowPlaying.value != null || localController.currentQueueItem.value != null,
                lastActiveAtMs = lastLocalActiveAtMs,
                isHost = transport.isHostDevice,
                playbackMode = modeState.mode,
                playbackModeUpdatedAtMs = modeState.updatedAtMs
            )
        )
    }

    private fun declareLocalActiveAndSync() {
        lastDeclaredActiveDeviceId = transport.localDeviceId
        _activeTarget.value = PlaybackTarget.Local
        enqueueOutbound(
            LanLinkMessage.RemoteActiveDevice(
                activeDeviceId = transport.localDeviceId,
                activeDeviceName = transport.localDeviceName,
                epoch = nextEpoch(),
                timestampMs = clockMs()
            )
        )
        pushQueueSnapshot()
        pushStateSnapshot(force = true)
    }

    private suspend fun reconcileRemoteMode(
        peer: ConnectedPeerInfo,
        remoteMode: LanLinkPlaybackMode,
        remoteUpdatedAtMs: Long,
        fromHello: Boolean
    ) {
        var transition: Pair<LanLinkPlaybackMode, LanLinkPlaybackMode>? = null
        modeMutex.withLock {
            if (!modeInitialized) {
                latestModeState = playbackModeStore.state.first()
                modeInitialized = true
            }
            val local = latestModeState
            val shouldApply = when {
                remoteUpdatedAtMs > local.updatedAtMs -> true
                remoteUpdatedAtMs < local.updatedAtMs -> false
                remoteMode == local.mode -> false
                else -> peer.deviceId > transport.localDeviceId
            }
            if (!shouldApply) return
            val nextState = LanLinkPlaybackModeState(remoteMode, remoteUpdatedAtMs)
            val oldMode = local.mode
            lastRemoteAppliedModeState = nextState
            latestModeState = nextState
            playbackModeStore.applySynced(remoteMode, remoteUpdatedAtMs)
            if (oldMode != remoteMode) {
                transition = oldMode to remoteMode
            }
        }
        transition?.let { (oldMode, newMode) ->
            // 握手阶段若切入单一设备模式，紧随其后的 handlePlaybackHello 会立即完成仲裁，无需重复发 Hello
            if (!fromHello || newMode == LanLinkPlaybackMode.INDEPENDENT) {
                handleRuntimeModeTransition(oldMode, newMode, peer)
            }
        }
    }

    private suspend fun handleRuntimeModeTransition(
        oldMode: LanLinkPlaybackMode,
        newMode: LanLinkPlaybackMode,
        peer: ConnectedPeerInfo?
    ) {
        if (oldMode == newMode) return
        when (newMode) {
            LanLinkPlaybackMode.INDEPENDENT -> {
                // 单一播放设备 -> 各自独立播放：两端均恢复为本机控制目标，互不暂停
                lastDeclaredActiveDeviceId = null
                if (_activeTarget.value !is PlaybackTarget.Local) {
                    _activeTarget.value = PlaybackTarget.Local
                }
            }
            LanLinkPlaybackMode.SINGLE_DEVICE -> {
                // 各自独立播放 -> 单一播放设备：按连接握手规则立即重新仲裁一次
                if (!transport.enabled.value || peer == null) return
                currentEpoch = 0L
                lastDeclaredActiveDeviceId = null
                pushQueueSnapshot()
                pushStateSnapshot(force = true)
                sendPlaybackHello()
            }
        }
    }

    private suspend fun handleIncomingMessage(peer: ConnectedPeerInfo, message: LanLinkMessage) {
        when (message) {
            is LanLinkMessage.RemotePlaybackState -> {
                remoteController.onPlaybackState(message)
            }

            is LanLinkMessage.RemoteQueueSync -> {
                remoteController.onQueueSync(message)
            }

            is LanLinkMessage.RemotePlaybackHello -> {
                handlePlaybackHello(peer, message)
            }

            is LanLinkMessage.RemotePlaybackModeSync -> {
                reconcileRemoteMode(peer, message.mode, message.updatedAtMs, fromHello = false)
            }

            is LanLinkMessage.RemoteActiveDevice -> {
                handleRemoteActiveDevice(peer, message)
            }

            is LanLinkMessage.RemoteHandoffPlay -> {
                handleRemoteHandoffPlay(message)
            }

            is LanLinkMessage.RemoteRequestSync -> {
                pushQueueSnapshot()
                pushStateSnapshot(force = true)
            }

            is LanLinkMessage.RemoteCommand -> {
                executeRemoteCommand(message)
            }

            is LanLinkMessage.RemotePlayQueue -> {
                withContext(commandDispatcher) {
                    localController.playQueue(
                        items = message.items,
                        startIndex = message.startIndex,
                        playContext = message.playContext,
                        source = message.source,
                        startPositionMs = message.startPositionMs
                    )
                }
            }

            is LanLinkMessage.RemotePlayAudio -> {
                withContext(commandDispatcher) {
                    localController.playAudio(
                        songId = message.songId,
                        url = message.url,
                        title = message.title,
                        artist = message.artist,
                        coverUrl = message.coverUrl,
                        startPosition = message.startPosition,
                        playContext = message.playContext
                    )
                }
            }

            is LanLinkMessage.RemoteAddToPlayNext -> {
                withContext(commandDispatcher) {
                    localController.addToPlayNext(message.items)
                }
            }

            is LanLinkMessage.RemotePlayNextNow -> {
                withContext(commandDispatcher) {
                    localController.playNextNow(message.item, message.startPositionMs)
                }
            }

            is LanLinkMessage.RemoteAppendToQueue -> {
                withContext(commandDispatcher) {
                    localController.appendToQueue(message.items)
                }
            }

            is LanLinkMessage.RemoteExtendQueue -> {
                withContext(commandDispatcher) {
                    localController.extendQueue(message.source, message.items)
                }
            }

            is LanLinkMessage.RemoteStartPersonalFm -> {
                withContext(commandDispatcher) {
                    localController.startPersonalFm(message.items)
                }
            }

            else -> Unit
        }
    }

    private suspend fun handlePlaybackHello(peer: ConnectedPeerInfo, msg: LanLinkMessage.RemotePlaybackHello) {
        peerIsHost = msg.isHost
        reconcileRemoteMode(peer, msg.playbackMode, msg.playbackModeUpdatedAtMs, fromHello = true)
        if (ensureModeInitialized().mode == LanLinkPlaybackMode.INDEPENDENT) {
            return
        }
        val localPlaying = localController.isPlaying.value || localController.playWhenReady.value
        val peerPlaying = msg.isPlaying || msg.playWhenReady
        when {
            localPlaying && !peerPlaying -> {
                lastDeclaredActiveDeviceId = transport.localDeviceId
                _activeTarget.value = PlaybackTarget.Local
                pushQueueSnapshot()
                pushStateSnapshot(force = true)
            }
            !localPlaying && peerPlaying -> {
                lastDeclaredActiveDeviceId = peer.deviceId
                _activeTarget.value = PlaybackTarget.Remote(peer)
                enqueueOutbound(LanLinkMessage.RemoteRequestSync)
            }
            localPlaying && peerPlaying -> {
                val localWins = if (transport.isHostDevice != msg.isHost) {
                    transport.isHostDevice
                } else {
                    transport.localDeviceId >= msg.deviceId
                }
                if (localWins) {
                    lastDeclaredActiveDeviceId = transport.localDeviceId
                    _activeTarget.value = PlaybackTarget.Local
                    enqueueOutbound(
                        LanLinkMessage.RemoteActiveDevice(
                            activeDeviceId = transport.localDeviceId,
                            activeDeviceName = transport.localDeviceName,
                            epoch = nextEpoch(),
                            timestampMs = clockMs()
                        )
                    )
                    pushQueueSnapshot()
                    pushStateSnapshot(force = true)
                } else {
                    ignoreLocalPlayUntilMs = clockMs() + SUPPRESSION_WINDOW_MS
                    withContext(commandDispatcher) {
                        localController.pause()
                    }
                    lastDeclaredActiveDeviceId = peer.deviceId
                    _activeTarget.value = PlaybackTarget.Remote(peer)
                    enqueueOutbound(LanLinkMessage.RemoteRequestSync)
                }
            }
            else -> {
                if (lastDeclaredActiveDeviceId == null) {
                    _activeTarget.value = PlaybackTarget.Local
                }
            }
        }
    }

    private suspend fun handleRemoteActiveDevice(peer: ConnectedPeerInfo, msg: LanLinkMessage.RemoteActiveDevice) {
        if (ensureModeInitialized().mode == LanLinkPlaybackMode.INDEPENDENT) return
        if (msg.epoch < currentEpoch) return
        if (msg.epoch == currentEpoch &&
            lastDeclaredActiveDeviceId != null &&
            msg.activeDeviceId != lastDeclaredActiveDeviceId
        ) {
            val localWins = if (transport.isHostDevice != peerIsHost) {
                transport.isHostDevice
            } else {
                transport.localDeviceId >= peer.deviceId
            }
            if (lastDeclaredActiveDeviceId == transport.localDeviceId && localWins) {
                return
            }
        }
        currentEpoch = maxOf(currentEpoch, msg.epoch)
        lastDeclaredActiveDeviceId = msg.activeDeviceId
        if (msg.activeDeviceId == transport.localDeviceId) {
            _activeTarget.value = PlaybackTarget.Local
            pushQueueSnapshot()
            pushStateSnapshot(force = true)
        } else {
            if (localController.isPlaying.value || localController.playWhenReady.value) {
                ignoreLocalPlayUntilMs = clockMs() + SUPPRESSION_WINDOW_MS
                withContext(commandDispatcher) {
                    localController.pause()
                }
            }
            _activeTarget.value = PlaybackTarget.Remote(peer)
            enqueueOutbound(LanLinkMessage.RemoteRequestSync)
        }
    }

    private suspend fun handleRemoteHandoffPlay(msg: LanLinkMessage.RemoteHandoffPlay) {
        if (msg.targetDeviceId != transport.localDeviceId) return
        val isIndependent = ensureModeInitialized().mode == LanLinkPlaybackMode.INDEPENDENT
        if (!isIndependent && msg.epoch < currentEpoch) return
        currentEpoch = maxOf(currentEpoch, msg.epoch)
        if (!isIndependent) {
            lastDeclaredActiveDeviceId = transport.localDeviceId
        }
        lastLocalActiveAtMs = clockMs()
        ignoreLocalPlayUntilMs = clockMs() + SUPPRESSION_WINDOW_MS
        _activeTarget.value = PlaybackTarget.Local
        withContext(commandDispatcher) {
            localController.playNextNow(msg.item, msg.startPositionMs)
        }
        pushQueueSnapshot()
        pushStateSnapshot(force = true)
    }

    private suspend fun executeRemoteCommand(cmd: LanLinkMessage.RemoteCommand) {
        withContext(commandDispatcher) {
            runCatching {
                when (cmd.action) {
                    RemoteCommandAction.PLAY -> localController.resume()
                    RemoteCommandAction.PAUSE -> localController.pause()
                    RemoteCommandAction.TOGGLE_PLAY_PAUSE -> localController.togglePlayPause()
                    RemoteCommandAction.NEXT -> localController.playNext()
                    RemoteCommandAction.PREVIOUS -> localController.playPrevious()
                    RemoteCommandAction.SKIP_TO_PREVIOUS -> localController.skipToPrevious()
                    RemoteCommandAction.PLAY_AT_INDEX -> if (cmd.index >= 0) localController.playAtIndex(cmd.index)
                    RemoteCommandAction.REMOVE_FROM_QUEUE -> if (cmd.index >= 0) localController.removeFromQueue(cmd.index)
                    RemoteCommandAction.MOVE_IN_QUEUE -> if (cmd.fromIndex >= 0 && cmd.toIndex >= 0) {
                        localController.moveInQueue(cmd.fromIndex, cmd.toIndex)
                    }
                    RemoteCommandAction.CLEAR_QUEUE -> localController.clearQueue()
                    RemoteCommandAction.STOP_AND_CLEAR_QUEUE -> localController.stopAndClearQueue()
                    RemoteCommandAction.SEEK -> localController.seekTo(cmd.positionMs.coerceAtLeast(0L))
                    RemoteCommandAction.SET_VOLUME -> if (cmd.volumePercent in 0..100) {
                        volumeBridge.setVolume(cmd.volumePercent)
                    }
                    RemoteCommandAction.TOGGLE_SHUFFLE -> localController.toggleShuffle()
                    RemoteCommandAction.TOGGLE_REPEAT -> localController.toggleRepeat()
                    RemoteCommandAction.ROTATE_PLAY_MODE -> localController.rotatePlayMode()
                    RemoteCommandAction.RELOAD_CURRENT -> localController.reloadCurrentTrack()
                    RemoteCommandAction.CANCEL_PENDING_SKIP -> {
                        localController.cancelPendingSkip()
                    }
                    RemoteCommandAction.DISABLE_ROAMING -> localController.disableRoaming()
                    RemoteCommandAction.DISABLE_INTELLIGENCE -> localController.disableIntelligence()
                    RemoteCommandAction.DISABLE_PERSONAL_FM -> localController.disablePersonalFm()
                    RemoteCommandAction.TRASH_FM -> localController.trashCurrentFmSong()
                    RemoteCommandAction.SWITCH_FM_MODE -> cmd.personalFmMode?.let {
                        localController.switchPersonalFmMode(it)
                    }
                }
            }.onFailure {
                AppLogger.w(TAG, "执行远端遥控指令失败: ${cmd.action}", it)
            }
        }
        if (cmd.action == RemoteCommandAction.SEEK || cmd.action == RemoteCommandAction.SET_VOLUME) {
            pushStateSnapshot(force = true)
        }
    }

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (isActive && localController.isPlaying.value &&
                transport.enabled.value && transport.connectedDevice.value != null
            ) {
                delay(LanLinkConstants.REMOTE_STATE_THROTTLE_MS)
                if (localController.isPlaying.value &&
                    transport.enabled.value && transport.connectedDevice.value != null
                ) {
                    pushStateSnapshot(force = false)
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun isPositionJumped(currentPosMs: Long, isPlaying: Boolean, nowMs: Long): Boolean {
        if (lastPushedAtMs <= 0L) return true
        val elapsed = (nowMs - lastPushedAtMs).coerceAtLeast(0L)
        val expected = if (lastPushedIsPlaying) lastPushedPositionMs + elapsed else lastPushedPositionMs
        if (isPlaying != lastPushedIsPlaying) return true
        return abs(currentPosMs - expected) > POSITION_JUMP_THRESHOLD_MS
    }

    private fun pushQueueSnapshot() {
        if (!transport.enabled.value || transport.connectedDevice.value == null) return
        val version = currentQueueVersion.takeIf { it > 0L } ?: queueVersionCounter.incrementAndGet().also {
            currentQueueVersion = it
        }
        enqueueOutbound(
            LanLinkMessage.RemoteQueueSync(
                queueVersion = version,
                items = localController.queue.value.map { it.copy(localUri = null) },
                currentIndex = localController.currentIndex.value,
                playContext = localController.playContext.value,
                playSource = localController.playSource.value
            )
        )
    }

    private fun pushStateSnapshot(force: Boolean) {
        if (!transport.enabled.value || transport.connectedDevice.value == null) return
        val now = clockMs()
        if (!force && (now - lastPushedAtMs) < LanLinkConstants.REMOTE_STATE_THROTTLE_MS) return

        val playing = localController.isPlaying.value
        val ready = localController.playWhenReady.value
        val track = localController.nowPlaying.value
        val pos = runCatching { localController.currentPositionNow() }.getOrDefault(localController.currentPosition.value)
        val dur = localController.duration.value
        val mode = localController.playMode.value
        val ctx = localController.playContext.value
        val source = localController.playSource.value
        val index = localController.currentIndex.value
        val vol = volumeBridge.volume.value.coerceIn(0, 100)
        val qVer = currentQueueVersion

        lastPushedAtMs = now
        lastPushedPositionMs = pos
        lastPushedIsPlaying = playing
        lastPushedSignature = StateSignature(
            isPlaying = playing,
            playWhenReady = ready,
            mediaId = track?.mediaId,
            title = track?.title,
            artist = track?.artist,
            artworkUri = track?.artworkUri,
            durationMs = dur,
            playMode = mode,
            playContext = ctx,
            playSource = source,
            currentIndex = index,
            volumePercent = vol,
            queueVersion = qVer
        )

        enqueueOutbound(
            LanLinkMessage.RemotePlaybackState(
                isPlaying = playing,
                playWhenReady = ready,
                nowPlaying = track?.let(RemoteNowPlaying::from),
                positionMs = pos,
                sampledAtMs = now,
                durationMs = dur,
                playMode = mode,
                playContext = ctx,
                playSource = source,
                currentIndex = index,
                volumePercent = vol,
                queueVersion = qVer
            )
        )
    }

    private fun enqueueOutbound(message: LanLinkMessage) {
        outboundChannel.trySend(message)
    }

    private data class Quad(
        val items: List<QueueItem>,
        val index: Int,
        val playContext: String?,
        val playSource: com.lin0721.linmusic.core.player.PlaySource?
    )

    private data class PlaybackMetaPart(
        val isPlaying: Boolean,
        val playWhenReady: Boolean,
        val track: com.lin0721.linmusic.core.player.NowPlaying?,
        val duration: Long,
        val playMode: com.lin0721.linmusic.core.player.PlayMode
    )

    private data class PlaybackTailPart(
        val playContext: String?,
        val playSource: com.lin0721.linmusic.core.player.PlaySource?,
        val currentIndex: Int,
        val volumePercent: Int,
        val positionMs: Long
    )
}
