package com.lin0721.linmusic.desktop.platform.native.linux

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformImpl
import com.lin0721.linmusic.desktop.platform.native.SystemMediaSession
import com.lin0721.linmusic.desktop.platform.native.linux.mpris.MprisLoopStatus
import com.lin0721.linmusic.desktop.platform.native.linux.mpris.MprisMetadata
import com.lin0721.linmusic.desktop.platform.native.linux.mpris.MprisPlaybackStatus
import com.lin0721.linmusic.desktop.platform.native.linux.mpris.MprisPlayer
import com.lin0721.linmusic.desktop.platform.native.linux.mpris.MprisRoot
import com.lin0721.linmusic.desktop.ui.sizedCoverUrl
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val TAG = "MprisSession"

private const val INIT_TIMEOUT_SECONDS = 5L
private const val COVER_SIZE_PX = 300
private const val TIMELINE_TICK_MS = 1_000L

// 播放中定期校准进度，其余时机按变化立即同步（与 Windows 侧 SMTC 保持一致）
private const val TIMELINE_SYNC_INTERVAL_MS = 5_000L

// 实际进度与按时间推算的进度偏差超过该值视为跳转
private const val SEEK_DETECT_THRESHOLD_MS = 1_500L

// 元数据与播放状态的快照，供 D-Bus 线程读取
private class MprisState {
    @Volatile var playbackStatus: String = MprisPlaybackStatus.STOPPED
    @Volatile var loopStatus: String = MprisLoopStatus.PLAYLIST
    @Volatile var shuffle: Boolean = false
    @Volatile var volume: Double = 1.0
    @Volatile var positionMicros: Long = 0
    @Volatile var metadata: Map<String, Variant<*>> = MprisMetadata.empty()
}

// Linux 系统媒体控制：实现 MPRIS 2，使桌面环境的媒体卡片与媒体键可操控播放。
//
// 与 Windows 的 SMTC 实现职责一致（同步元数据/状态/时间轴，并响应外部指令），
// 差异在于 Windows 走 WinRT 桥接 DLL，Linux 通过 D-Bus 导出对象。
@PlatformImpl(DesktopPlatform.LINUX)
class LinuxSystemMediaSession : SystemMediaSession {

    private var connection: DBusConnection? = null

    // D-Bus 调用统一在单独线程执行，避免阻塞 UI 或协程调度器
    private var executor: ExecutorService? = null

    private val state = MprisState()

    // 外部指令（媒体键 / 桌面卡片）投递到协程侧执行
    private val commands = MutableSharedFlow<(PlaybackController) -> Unit>(extraBufferCapacity = 16)

    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available.asStateFlow()

    @Volatile private var enabled = true

    override fun start(): Boolean {
        if (connection != null) return true

        val worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "mpris").apply { isDaemon = true }
        }
        val ok = try {
            worker.submit<Boolean> {
                val conn = DBusConnectionBuilder.forSessionBus().build()
                // 先导出对象再抢名字：KDE 的 Mpris2SourceModel 监听 NameOwnerChanged，
                // 名称一出现就立即 GetAll；若此时对象尚未导出，首次抓取会失败，
                // 该播放器会被直接丢弃且不再重试（媒体中心永远不显示）
                conn.exportObject(MprisPlayer.OBJECT_PATH, MprisObject())
                conn.requestBusName(MprisPlayer.BUS_NAME)
                connection = conn
                true
            }.get(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            AppLogger.w(TAG, "MPRIS 初始化失败，系统媒体控制不可用", e)
            false
        }

        if (ok != true) {
            runCatching { worker.shutdownNow() }
            return false
        }
        executor = worker
        _available.value = true
        AppLogger.i(TAG, "MPRIS 已就绪：${MprisPlayer.BUS_NAME}")
        return true
    }

    override fun setEnabled(value: Boolean) {
        enabled = value
        // 禁用时切为 Stopped，桌面端不再显示"播放中"
        if (!value) state.playbackStatus = MprisPlaybackStatus.STOPPED
        push()
    }

    override fun shutdown() {
        val worker = executor ?: return
        val conn = connection
        connection = null
        executor = null
        _available.value = false
        runCatching {
            worker.execute {
                runCatching {
                    conn?.unExportObject(MprisPlayer.OBJECT_PATH)
                    conn?.releaseBusName(MprisPlayer.BUS_NAME)
                    conn?.disconnect()
                }
            }
        }
        worker.shutdown()
        runCatching { worker.awaitTermination(1, TimeUnit.SECONDS) }
    }

    override suspend fun bind(controller: PlaybackController, playerViewModel: PlayerViewModel) = coroutineScope {
        if (connection == null) return@coroutineScope

        // 元数据（专辑名需从歌曲详情取）
        launch {
            combine(controller.nowPlaying, playerViewModel.songDetailState) { track, detail ->
                track?.let { playing ->
                    val album = detail.songDetail?.takeIf { it.id == playing.songId }?.al?.name.orEmpty()
                    MprisMetadata.build(
                        songId = playing.songId ?: 0L,
                        title = playing.title,
                        artist = playing.artist,
                        album = album,
                        coverUrl = sizedCoverUrl(playing.artworkUri, COVER_SIZE_PX),
                        durationMs = controller.duration.value,
                    )
                }
            }.distinctUntilChanged().collect { meta ->
                if (meta != null) state.metadata = meta
                push()
            }
        }

        // 播放状态
        launch {
            combine(controller.nowPlaying, controller.playWhenReady) { track, playing ->
                when {
                    track == null -> MprisPlaybackStatus.STOPPED
                    playing -> MprisPlaybackStatus.PLAYING
                    else -> MprisPlaybackStatus.PAUSED
                }
            }.distinctUntilChanged().collect { status ->
                state.playbackStatus = status
                push()
            }
        }

        // 循环与随机模式
        launch {
            controller.playMode.collect { mode ->
                state.shuffle = mode == PlayMode.SHUFFLE
                state.loopStatus =
                    if (mode == PlayMode.SINGLE_LOOP) MprisLoopStatus.TRACK else MprisLoopStatus.PLAYLIST
                push()
            }
        }

        launch { syncTimeline(controller) }

        launch { commands.collect { action -> if (enabled) action(controller) } }
    }

    // 定期同步进度；仅在变化或发生跳转时推送，避免无谓的 D-Bus 流量
    private suspend fun syncTimeline(controller: PlaybackController) {
        var lastPosition = -1L
        var lastDuration = -1L
        var lastPlaying = false
        var lastSentAt = 0L
        while (true) {
            delay(TIMELINE_TICK_MS)
            val position = controller.currentPosition.value
            val duration = controller.duration.value
            val playing = controller.playWhenReady.value
            val now = System.currentTimeMillis()
            val expected = if (lastPlaying) lastPosition + (now - lastSentAt) else lastPosition
            val needSync = duration != lastDuration ||
                playing != lastPlaying ||
                abs(position - expected) > SEEK_DETECT_THRESHOLD_MS ||
                (playing && now - lastSentAt >= TIMELINE_SYNC_INTERVAL_MS)
            if (!needSync) continue
            lastPosition = position
            lastDuration = duration
            lastPlaying = playing
            lastSentAt = now
            state.positionMicros = position * 1000
            push()
        }
    }

    // 主动推送属性变更信号，桌面端据此刷新卡片
    private fun push() {
        val conn = connection ?: return
        val worker = executor ?: return
        runCatching {
            worker.execute {
                runCatching { emitPropertiesChanged(conn, state) }
                    .onFailure { AppLogger.w(TAG, "MPRIS 属性推送失败", it) }
            }
        }
    }

    private fun emitPropertiesChanged(conn: DBusConnection, snapshot: MprisState) {
        val changed = mapOf(
            MprisPlayer.PROP_PLAYBACK_STATUS to Variant(snapshot.playbackStatus),
            MprisPlayer.PROP_METADATA to Variant(snapshot.metadata, "a{sv}"),
            MprisPlayer.PROP_LOOP_STATUS to Variant(snapshot.loopStatus),
            MprisPlayer.PROP_SHUFFLE to Variant(snapshot.shuffle),
        )
        conn.sendMessage(MprisPlayer.PropertiesChanged(MprisPlayer.OBJECT_PATH, changed))
    }

    // MPRIS 的 LoopStatus/Shuffle 组合映射到播放器的三种模式；
    // 控制器只暴露 toggle 接口，这里按当前模式选择正确的切换动作。
    private fun setLoopStatus(controller: PlaybackController, status: String) {
        // 本项目没有"不循环"模式，"None" 按最接近的列表循环处理
        val target = if (status == MprisLoopStatus.TRACK) PlayMode.SINGLE_LOOP else PlayMode.LIST_LOOP
        switchPlayMode(controller, target)
    }

    private fun setShuffle(controller: PlaybackController, enabled: Boolean) {
        switchPlayMode(controller, if (enabled) PlayMode.SHUFFLE else PlayMode.LIST_LOOP)
    }

    private fun switchPlayMode(controller: PlaybackController, target: PlayMode) {
        val current = controller.playMode.value
        if (current == target) return
        when (target) {
            PlayMode.SINGLE_LOOP -> controller.toggleRepeat()
            PlayMode.LIST_LOOP -> if (current == PlayMode.SHUFFLE) controller.toggleShuffle() else controller.toggleRepeat()
            PlayMode.SHUFFLE -> controller.toggleShuffle()
        }
    }

    // 导出的 MPRIS 对象：属性读自 state，方法调用投递到 commands
    private inner class MprisObject : MprisPlayer, MprisRoot {

        override fun isRemote(): Boolean = false

        // 两个接口都带默认实现，须显式 override 消歧
        override fun getObjectPath(): String = MprisPlayer.OBJECT_PATH

        // CanQuit / CanRaise 为 false：仅实现空操作满足规范
        override fun Raise() = Unit

        override fun Quit() = Unit

        override fun Next() = emit { it.playNext() }
        override fun Previous() = emit { it.skipToPrevious() }
        override fun Pause() = emit { it.pause() }
        override fun Play() = emit { it.resume() }
        override fun Stop() = emit { it.pause() }
        override fun PlayPause() = emit { it.togglePlayPause() }

        override fun Seek(offsetMicros: Long) = emit { controller ->
            val target = (controller.currentPosition.value + offsetMicros / 1000).coerceAtLeast(0)
            controller.seekTo(target)
        }

        override fun SetPosition(trackId: String, positionMicros: Long) = emit { controller ->
            controller.seekTo((positionMicros / 1000).coerceAtLeast(0))
        }

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(iface: String, prop: String): A = when (iface) {
            MprisPlayer.PLAYER_INTERFACE -> when (prop) {
                MprisPlayer.PROP_PLAYBACK_STATUS -> state.playbackStatus as A
                MprisPlayer.PROP_POSITION -> state.positionMicros as A
                MprisPlayer.PROP_RATE -> 1.0 as A
                MprisPlayer.PROP_VOLUME -> state.volume as A
                // Metadata 是 a{sv}，须显式包装：bare Map 无法被 dbus-java 序列化
                MprisPlayer.PROP_METADATA -> Variant(state.metadata, "a{sv}") as A
                MprisPlayer.PROP_LOOP_STATUS -> state.loopStatus as A
                MprisPlayer.PROP_SHUFFLE -> state.shuffle as A
                MprisPlayer.PROP_CAN_SEEK,
                MprisPlayer.PROP_CAN_PLAY,
                MprisPlayer.PROP_CAN_PAUSE,
                MprisPlayer.PROP_CAN_STOP,
                MprisPlayer.PROP_CAN_GO_NEXT,
                MprisPlayer.PROP_CAN_GO_PREVIOUS,
                MprisPlayer.PROP_CAN_CONTROL -> true as A
                MprisPlayer.PROP_MINIMUM_RATE,
                MprisPlayer.PROP_MAXIMUM_RATE -> 1.0 as A
                else -> null as A
            }
            MprisRoot.ROOT_INTERFACE -> when (prop) {
                MprisRoot.PROP_IDENTITY -> MprisRoot.IDENTITY as A
                MprisRoot.PROP_DESKTOP_ENTRY -> MprisRoot.DESKTOP_ENTRY as A
                MprisRoot.PROP_CAN_QUIT,
                MprisRoot.PROP_CAN_RAISE,
                MprisRoot.PROP_HAS_TRACK_LIST,
                MprisRoot.PROP_FULLSCREEN,
                MprisRoot.PROP_CAN_SET_FULLSCREEN -> false as A
                MprisRoot.PROP_SUPPORTED_URI_SCHEMES,
                MprisRoot.PROP_SUPPORTED_MIME_TYPES -> emptyList<String>() as A
                else -> null as A
            }
            else -> null as A
        }

        override fun GetAll(iface: String): Map<String, Variant<*>> = when (iface) {
            MprisPlayer.PLAYER_INTERFACE -> mapOf(
                MprisPlayer.PROP_PLAYBACK_STATUS to Variant(state.playbackStatus),
                MprisPlayer.PROP_POSITION to Variant(state.positionMicros),
                MprisPlayer.PROP_RATE to Variant(1.0),
                MprisPlayer.PROP_VOLUME to Variant(state.volume),
                MprisPlayer.PROP_METADATA to Variant(state.metadata, "a{sv}"),
                MprisPlayer.PROP_LOOP_STATUS to Variant(state.loopStatus),
                MprisPlayer.PROP_SHUFFLE to Variant(state.shuffle),
                MprisPlayer.PROP_CAN_SEEK to Variant(true),
                MprisPlayer.PROP_CAN_PLAY to Variant(true),
                MprisPlayer.PROP_CAN_PAUSE to Variant(true),
                MprisPlayer.PROP_CAN_STOP to Variant(true),
                MprisPlayer.PROP_CAN_GO_NEXT to Variant(true),
                MprisPlayer.PROP_CAN_GO_PREVIOUS to Variant(true),
                MprisPlayer.PROP_CAN_CONTROL to Variant(true),
                MprisPlayer.PROP_MINIMUM_RATE to Variant(1.0),
                MprisPlayer.PROP_MAXIMUM_RATE to Variant(1.0),
            )
            MprisRoot.ROOT_INTERFACE -> mapOf(
                MprisRoot.PROP_CAN_QUIT to Variant(false),
                MprisRoot.PROP_CAN_RAISE to Variant(false),
                MprisRoot.PROP_HAS_TRACK_LIST to Variant(false),
                MprisRoot.PROP_IDENTITY to Variant(MprisRoot.IDENTITY),
                MprisRoot.PROP_DESKTOP_ENTRY to Variant(MprisRoot.DESKTOP_ENTRY),
                MprisRoot.PROP_FULLSCREEN to Variant(false),
                MprisRoot.PROP_CAN_SET_FULLSCREEN to Variant(false),
                // 空数组须显式标注 as，否则 dbus-java 无法推断元素类型
                MprisRoot.PROP_SUPPORTED_URI_SCHEMES to Variant(emptyList<String>(), "as"),
                MprisRoot.PROP_SUPPORTED_MIME_TYPES to Variant(emptyList<String>(), "as"),
            )
            else -> emptyMap()
        }

        override fun <A : Any?> Set(iface: String, prop: String, value: A) {
            if (iface != MprisPlayer.PLAYER_INTERFACE) return
            when (prop) {
                MprisPlayer.PROP_VOLUME -> if (value is Double) state.volume = value
                MprisPlayer.PROP_LOOP_STATUS -> if (value is String) emit { setLoopStatus(it, value) }
                MprisPlayer.PROP_SHUFFLE -> if (value is Boolean) emit { setShuffle(it, value) }
            }
        }

        private fun emit(action: (PlaybackController) -> Unit) {
            commands.tryEmit(action)
        }
    }
}
