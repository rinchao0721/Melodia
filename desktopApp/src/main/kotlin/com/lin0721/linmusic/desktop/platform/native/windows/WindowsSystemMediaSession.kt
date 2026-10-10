package com.lin0721.linmusic.desktop.platform.native.windows

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.platform.native.SystemMediaSession
import com.lin0721.linmusic.desktop.ui.sizedCoverUrl
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import com.sun.jna.WString
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformImpl

private const val TAG = "SmtcSession"

private const val INIT_TIMEOUT_SECONDS = 5L
private const val COVER_SIZE_PX = 300
private const val TIMELINE_TICK_MS = 1_000L

// 播放中定期校准系统卡片进度，其余时机按变化立即同步
private const val TIMELINE_SYNC_INTERVAL_MS = 5_000L

// 实际进度与按时间推算的进度偏差超过该值视为跳转
private const val SEEK_DETECT_THRESHOLD_MS = 1_500L

private data class Metadata(val title: String, val artist: String, val album: String, val cover: String)

// 系统媒体传输控制会话；DLL 调用统一在单独线程执行，保证 WinRT 套间一致
@PlatformImpl(DesktopPlatform.WINDOWS)
class WindowsSystemMediaSession : SystemMediaSession {

    private var library: SmtcLibrary? = null
    private var executor: ExecutorService? = null

    // 回调对象须保持强引用，否则被回收后原生侧回调会崩溃
    private var callback: SmtcLibrary.CommandCallback? = null

    private val commands = MutableSharedFlow<Pair<Int, Long>>(extraBufferCapacity = 16)

    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available.asStateFlow()

    @Volatile private var enabled = true

    override fun start(): Boolean {
        if (library != null) return true
        val lib = try {
            SmtcLibrary.load()
        } catch (e: LinkageError) {
            AppLogger.w(TAG, "melodia_smtc 加载失败，改用全局媒体键", e)
            return false
        }
        val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "smtc").apply { isDaemon = true } }
        val cb = object : SmtcLibrary.CommandCallback {
            override fun invoke(command: Int, value: Long) {
                commands.tryEmit(command to value)
            }
        }
        val result = try {
            worker.submit<Int> { lib.smtc_init(cb) }.get(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            AppLogger.w(TAG, "SMTC 初始化异常", e)
            -1
        }
        if (result != 0) {
            AppLogger.w(TAG, "SMTC 初始化失败 code=$result，改用全局媒体键")
            worker.shutdownNow()
            return false
        }
        library = lib
        executor = worker
        callback = cb
        _available.value = true
        return true
    }

    override fun setEnabled(value: Boolean) {
        enabled = value
        call { it.smtc_set_enabled(if (value) 1 else 0) }
    }

    override fun shutdown() {
        val worker = executor ?: return
        call { it.smtc_shutdown() }
        worker.shutdown()
        runCatching { worker.awaitTermination(1, TimeUnit.SECONDS) }
        library = null
        executor = null
        callback = null
        _available.value = false
    }

    // 同步播放状态到系统卡片并响应卡片按钮，随调用方协程取消而结束
    override suspend fun bind(controller: PlaybackController, playerViewModel: PlayerViewModel) = coroutineScope {
        if (library == null) return@coroutineScope

        launch {
            combine(controller.nowPlaying, playerViewModel.songDetailState) { track, detail ->
                track?.let {
                    val album = detail.songDetail?.takeIf { song -> song.id == it.songId }?.al?.name.orEmpty()
                    Metadata(it.title, it.artist, album, sizedCoverUrl(it.artworkUri, COVER_SIZE_PX).orEmpty())
                }
            }.distinctUntilChanged().collect { meta ->
                if (meta == null) {
                    call { it.smtc_set_status(SmtcStatus.STOPPED) }
                } else {
                    call { it.smtc_set_metadata(WString(meta.title), WString(meta.artist), WString(meta.album), WString(meta.cover)) }
                }
            }
        }

        launch {
            combine(controller.nowPlaying, controller.playWhenReady) { track, playing ->
                when {
                    track == null -> SmtcStatus.STOPPED
                    playing -> SmtcStatus.PLAYING
                    else -> SmtcStatus.PAUSED
                }
            }.distinctUntilChanged().collect { status -> call { it.smtc_set_status(status) } }
        }

        launch {
            controller.playMode.collect { mode -> pushMode(mode) }
        }

        launch { syncTimeline(controller) }

        launch {
            commands.collect { (command, value) -> handleCommand(controller, command, value) }
        }
    }

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
            call { it.smtc_set_timeline(position, duration) }
        }
    }

    private fun handleCommand(controller: PlaybackController, command: Int, value: Long) {
        if (!enabled) return
        when (command) {
            SmtcCommand.PLAY -> controller.resume()
            SmtcCommand.PAUSE -> controller.pause()
            SmtcCommand.NEXT -> controller.playNext()
            SmtcCommand.PREVIOUS -> controller.skipToPrevious()
            SmtcCommand.SEEK -> controller.seekTo(value)
            SmtcCommand.SHUFFLE -> {
                if ((value != 0L) != (controller.playMode.value == PlayMode.SHUFFLE)) controller.toggleShuffle()
            }
            SmtcCommand.REPEAT -> {
                // Melodia 没有不循环模式，“不循环”按列表循环处理
                val wantSingle = value == SmtcRepeat.TRACK.toLong()
                if (wantSingle != (controller.playMode.value == PlayMode.SINGLE_LOOP)) controller.toggleRepeat()
            }
        }
        // 模式请求可能未改变状态，主动回写避免系统卡片显示与实际不符
        if (command == SmtcCommand.SHUFFLE || command == SmtcCommand.REPEAT) pushMode(controller.playMode.value)
    }

    private fun pushMode(mode: PlayMode) {
        val shuffle = if (mode == PlayMode.SHUFFLE) 1 else 0
        val repeat = if (mode == PlayMode.SINGLE_LOOP) SmtcRepeat.TRACK else SmtcRepeat.LIST
        call {
            it.smtc_set_shuffle(shuffle)
            it.smtc_set_repeat(repeat)
        }
    }

    private fun call(block: (SmtcLibrary) -> Unit) {
        val lib = library ?: return
        val worker = executor ?: return
        runCatching {
            worker.execute {
                runCatching { block(lib) }.onFailure { AppLogger.w(TAG, "SMTC 调用失败", it) }
            }
        }
    }
}
