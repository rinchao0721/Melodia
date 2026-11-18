package com.lin0721.linmusic.core.player.external

import android.content.Context
import android.media.session.PlaybackState
import android.os.SystemClock
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.domain.LyricAlignment
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.WordInfo
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.isDisconnected
import io.github.proify.lyricon.provider.service.addConnectionListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class LyriconAdapter(private val context: Context) {
    companion object {
        private const val TAG = "LyriconAdapter"
        // 无行时长且无下一行时的兜底时长
        private const val FALLBACK_LINE_DURATION_MS = 4000L
        // 位置偏差容忍值：小于此值不重发，避免频繁跨进程调用
        private const val POSITION_DRIFT_TOLERANCE_MS = 300L
        // 断线重连的退避序列（毫秒），超过长度后固定用最后一档
        private val RECONNECT_DELAYS = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)
        // 守护巡检间隔：进程被杀重开、词幕重启等场景靠它兜底
        private const val WATCHDOG_INTERVAL_MS = 15_000L
    }

    private var provider: LyriconProvider? = null
    private var isEnabled = false
    private var lastPlaying: Boolean? = null
    private var lastPositionMs = 0L
    private var lastPositionAt = 0L

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var reconnectJob: Job? = null
    private var watchdogJob: Job? = null
    private var reconnectAttempt = 0
    // release 之后 adapter 仍可复用：服务被系统回收再拉起时靠它恢复注册
    private var released = false

    /**
     * 重新连上中心服务后回调。
     * 词幕断线期间可能已经丢掉歌曲与歌词，这里让调用方把当前状态整份补发一次。
     */
    var onReconnected: (() -> Unit)? = null

    fun setEnabled(enabled: Boolean) {
        ensureStarted()
        if (enabled == isEnabled) return
        isEnabled = enabled
        try {
            if (enabled) {
                val target = provider ?: createProvider()?.also { provider = it } ?: return
                target.register()
                startWatchdog()
            } else {
                stopWatchdog()
                reconnectJob?.cancel()
                reconnectJob = null
                reconnectAttempt = 0
                provider?.player?.setSong(null)
                // 清空驱动状态，下次开启时重新建立时间基准
                provider?.player?.setPlaybackState(null as PlaybackState?)
                provider?.unregister()
                lastPlaying = null
                lastPositionAt = 0L
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Lyricon 注册状态切换失败: enabled=$enabled", e)
        }
    }

    private fun createProvider(): LyriconProvider? = try {
        LyriconFactory.createProvider(context.applicationContext).apply {
            // 回调可能来自 Binder 线程，统一切回主线程再改重连状态
            service.addConnectionListener {
                onConnected {
                    AppLogger.i(TAG, "Lyricon 已连接")
                    scope.launch { onConnectedInternal() }
                }
                onReconnected {
                    AppLogger.i(TAG, "Lyricon 已重连")
                    scope.launch { onConnectedInternal() }
                }
                onDisconnected {
                    AppLogger.w(TAG, "Lyricon 连接断开，安排重连")
                    scope.launch { scheduleReconnect("连接断开") }
                }
                onConnectTimeout {
                    AppLogger.w(TAG, "Lyricon 连接超时，安排重连")
                    scope.launch { scheduleReconnect("连接超时") }
                }
            }
        }
    } catch (e: Exception) {
        AppLogger.w(TAG, "Lyricon Provider 创建失败", e)
        null
    }

    // 连上后重置退避，并把当前状态补一遍，避免断线期间状态丢失
    private fun onConnectedInternal() {
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null
        invalidatePositionBase()
        onReconnected?.invoke()
    }

    /** 断线或超时后按退避重试注册；进程重启、词幕重启都靠它自愈。 */
    private fun scheduleReconnect(reason: String) {
        if (!isEnabled) return
        if (reconnectJob?.isActive == true) return
        val delayMs = RECONNECT_DELAYS[reconnectAttempt.coerceAtMost(RECONNECT_DELAYS.lastIndex)]
        reconnectAttempt++
        AppLogger.i(TAG, "Lyricon $reason，${delayMs}ms 后重试注册")
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!isEnabled) return@launch
            try {
                val target = provider ?: createProvider()?.also { provider = it }
                if (target == null) {
                    scheduleReconnect("Provider 创建失败")
                    return@launch
                }
                target.register()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Lyricon 重连失败", e)
                scheduleReconnect("重连失败")
            }
        }
    }

    // 定期巡检连接状态：省电机制下重连协程可能被推迟，这里再兜一层
    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = scope.launch {
            while (isActive && isEnabled) {
                delay(WATCHDOG_INTERVAL_MS)
                if (!isEnabled) break
                val target = provider
                if (target == null) {
                    scheduleReconnect("Provider 缺失")
                    continue
                }
                val status = target.service.connectionStatus
                if (status.isDisconnected()) {
                    AppLogger.w(TAG, "Lyricon 巡检发现未连接（$status），重新注册")
                    scheduleReconnect("巡检未连接")
                }
            }
        }
    }

    private fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    fun updateSong(
        songId: String,
        title: String,
        artist: String,
        durationMs: Long,
        lines: List<LyricLine>,
        showTranslation: Boolean
    ) {
        ensureStarted()
        if (!isEnabled) return
        val player = provider?.player ?: return
        try {
            player.setDisplayTranslation(showTranslation)
            player.setDisplayRoma(true)
            player.setSong(
                Song(
                    id = songId,
                    name = title,
                    artist = artist,
                    duration = durationMs.coerceAtLeast(0L),
                    lyrics = lines.toRichLines(durationMs)
                )
            )
        } catch (e: Exception) {
            AppLogger.w(TAG, "Lyricon 更新曲目失败", e)
        }
    }

    fun setDisplayTranslation(show: Boolean) {
        if (!isEnabled) return
        try {
            provider?.player?.setDisplayTranslation(show)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Lyricon 更新翻译显示失败", e)
        }
    }

    /**
     * 同步播放状态与位置。
     *
     * 词幕中心服务支持 PlaybackState 驱动模式：拿到 (位置, 速度, 更新时间) 后
     * 会自己按 elapsedRealtime 外推，不再依赖提供端高频写共享内存。
     * 之前只调 setPosition，共享内存里永远是上一次 1s 轮询的值，
     * 状态栏歌词因此最多慢一整秒，这就是不同步的来源。
     *
     * @param force 切歌、跳转、连接建立后必须重发，否则会被漂移判断吞掉
     */
    fun syncPlaybackState(isPlaying: Boolean, positionMs: Long, force: Boolean = false) {
        ensureStarted()
        if (!isEnabled) return
        val player = provider?.player ?: return
        val position = positionMs.coerceAtLeast(0L)
        val now = SystemClock.elapsedRealtime()
        try {
            val playingChanged = lastPlaying != isPlaying
            if (!force && !playingChanged) {
                // 播放中位置会自己走；暂停时位置不该变。两者都只按偏差决定是否重发
                val expected = if (isPlaying) lastPositionMs + (now - lastPositionAt) else lastPositionMs
                if (abs(expected - position) <= POSITION_DRIFT_TOLERANCE_MS) return
            }
            player.setPlaybackState(
                PlaybackState.Builder()
                    .setState(
                        if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                        position,
                        1.0f,
                        now
                    )
                    .setActions(
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO
                    )
                    .build()
            )
            lastPlaying = isPlaying
            lastPositionMs = position
            lastPositionAt = now
        } catch (e: Exception) {
            AppLogger.w(TAG, "Lyricon 同步播放状态失败", e)
        }
    }

    /** 切歌或重新连接后清掉时间基准，保证下一次同步一定发出去。 */
    fun invalidatePositionBase() {
        lastPositionAt = 0L
        lastPlaying = null
    }

    fun release() {
        // 这里不能注销连接：词幕侧的活跃播放器是「先到先得，且只有当前活跃者不在播放时
        // 别的来源才能抢占」。同一 ProviderInfo 注销后再注册，中心会 reattach 到旧连接，
        // 旧会话仍记着 isPlaying=true 而它的远端桩已经拆掉，之后本进程的所有事件都被忽略，
        // 重启 App 和重启 SystemUI 都救不回来，只能重启系统。
        // 所以服务停止时只清时间基准、停重连，连接留给进程自然结束时的 Binder 死亡清理。
        lastPlaying = null
        lastPositionAt = 0L
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
    }

    /**
     * release 之后重新可用：重建协程作用域与 Provider，开关仍是开的话直接注册。
     * 服务被划掉又在通知栏点播放拉起时走的就是这条路。
     */
    private fun ensureStarted() {
        if (!released) return
        released = false
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val target = createProvider()
        provider = target
        if (!isEnabled) return
        startWatchdog()
        if (target == null) {
            scheduleReconnect("Provider 创建失败")
            return
        }
        try {
            target.register()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Lyricon 恢复注册失败", e)
            scheduleReconnect("恢复注册失败")
        }
    }

    private fun List<LyricLine>.toRichLines(songDurationMs: Long): List<RichLyricLine> =
        mapIndexedNotNull { index, line ->
            if (line.text.isBlank()) return@mapIndexedNotNull null
            val end = resolveLineEnd(line, getOrNull(index + 1)?.timeMs, songDurationMs)
            val background = line.backgroundLine?.takeIf { it.text.isNotBlank() }
            RichLyricLine(
                begin = line.timeMs,
                end = end,
                isAlignedRight = line.alignment == LyricAlignment.END,
                text = line.text,
                words = line.words.toLyricWords(line.timeMs),
                secondary = background?.text,
                secondaryWords = background?.let { it.words.toLyricWords(it.timeMs) },
                translation = line.translation?.takeIf { it.isNotBlank() },
                roma = line.roma?.takeIf { it.isNotBlank() }
            )
        }

    // SDK 会丢弃 end <= begin 的行，这里保证结束时间严格大于开始时间
    private fun resolveLineEnd(line: LyricLine, nextStartMs: Long?, songDurationMs: Long): Long {
        val candidate = when {
            line.durationMs > 0 -> line.timeMs + line.durationMs
            nextStartMs != null && nextStartMs > line.timeMs -> nextStartMs
            songDurationMs > line.timeMs -> songDurationMs
            else -> line.timeMs + FALLBACK_LINE_DURATION_MS
        }
        return if (candidate > line.timeMs) candidate else line.timeMs + FALLBACK_LINE_DURATION_MS
    }

    private fun List<WordInfo>.toLyricWords(lineStartMs: Long): List<LyricWord>? {
        if (isEmpty()) return null
        return map { word ->
            val begin = lineStartMs + word.startOffsetMs
            LyricWord(begin = begin, end = begin + word.durationMs.coerceAtLeast(0L), text = word.text)
        }
    }
}
