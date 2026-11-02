package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.CrossfadePolicy
import com.lin0721.linmusic.core.player.LoudnessPolicy
import com.lin0721.linmusic.desktop.player.mpv.MpvEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MpvEnginePair"
private const val RAMP_STEP_MS = 20L

// 等渐出引擎读完的最长时间，录制读完才完整
private const val OUTGOING_DRAIN_TIMEOUT_MS = 2_500L
private const val OUTGOING_DRAIN_POLL_MS = 50L

private const val DELETE_ATTEMPTS = 30
private const val DELETE_RETRY_MS = 100L

// tag 为歌曲 id
internal class RecordRequest(val tag: Long, val path: String)

// 备用引擎预载下一首，交叉淡化时与主引擎换角色，事件只转发主引擎的
// onRecordingFinished 在无拖动自然播完后回调，临时文件此时可能仍被 mpv 占用
internal class MpvEnginePair(
    private val listener: MpvEngine.Listener,
    private val scope: CoroutineScope,
    private val engineOptions: Map<String, String> = emptyMap(),
    private val onRecordingFinished: (tag: Long, path: String) -> Unit = { _, _ -> }
) {

    private inner class Slot : MpvEngine.Listener {
        lateinit var engine: MpvEngine

        @Volatile var position = 0L
        @Volatile var duration = 0L
        @Volatile var loaded = false
        @Volatile var ended = false

        // 拖动过的录制不完整
        @Volatile var record: RecordRequest? = null
        @Volatile var seeked = false

        // 音量平衡的曲目增益，已换算为 mpv 音量系数
        @Volatile var gain = 1.0

        private val isActive: Boolean get() = this === active

        fun resetProgress() {
            position = 0L
            duration = 0L
            loaded = false
            ended = false
        }

        // 停引擎后 mpv 异步释放文件，删除需要重试直到释放
        fun discardRecord() {
            val request = record ?: return
            record = null
            engine.stop()
            deleteWhenReleased(File(request.path))
        }

        override fun onPositionChanged(positionMs: Long) {
            position = positionMs
            if (isActive) listener.onPositionChanged(positionMs)
        }

        override fun onDurationChanged(durationMs: Long) {
            duration = durationMs
            if (isActive) listener.onDurationChanged(durationMs)
        }

        override fun onPauseChanged(paused: Boolean) {
            if (isActive) listener.onPauseChanged(paused)
        }

        override fun onFileLoaded() {
            loaded = true
            if (isActive) listener.onFileLoaded()
        }

        override fun onEnded(isError: Boolean) {
            ended = true
            val request = record
            if (request != null) {
                if (!isError && !seeked) {
                    record = null
                    onRecordingFinished(request.tag, request.path)
                } else {
                    discardRecord()
                }
            }
            if (isActive) listener.onEnded(isError)
        }
    }

    // 初始化期间事件线程可能先于赋值回调，视为非主引擎丢弃
    @Volatile private var active: Slot? = null
    private var spare: Slot? = null
    private var spareArmed = false
    private var spareUnavailable = false
    private var rampJob: Job? = null
    private var gainJob: Job? = null
    private var userVolume = 100.0
    private var deviceName: String? = null

    private val activeSlot: Slot get() = checkNotNull(active)

    init {
        active = newSlot()
    }

    val isFading: Boolean get() = rampJob != null

    // 仅测试用
    internal fun volumes(): Pair<Double?, Double?> = activeSlot.engine.volume() to spare?.engine?.volume()

    val spareReady: Boolean get() = spareArmed && spare?.let { it.loaded && it.duration > 0L } == true

    private fun newSlot(): Slot {
        val slot = Slot()
        slot.engine = MpvEngine(slot, engineOptions)
        return slot
    }

    // 从中途起播的录制必然不完整，不应传 record
    fun load(url: String, startMs: Long, record: RecordRequest? = null, gainDb: Double = 0.0) {
        reset()
        gainJob?.cancel()
        gainJob = null
        val slot = activeSlot
        slot.discardRecord()
        slot.resetProgress()
        slot.record = record
        slot.seeked = false
        slot.gain = LoudnessPolicy.mpvVolumeFactor(gainDb)
        slot.engine.setVolume(userVolume * slot.gain)
        slot.engine.load(url, startMs, recordPath = record?.path)
    }

    // 播放中更新主引擎的曲目增益并平滑过渡；交叉淡化进行中由淡入曲线逐步跟上
    fun setTrackGain(gainDb: Double) {
        val slot = activeSlot
        val target = LoudnessPolicy.mpvVolumeFactor(gainDb)
        gainJob?.cancel()
        if (rampJob != null) {
            slot.gain = target
            gainJob = null
            return
        }
        val from = slot.gain
        gainJob = scope.launch {
            val startedAt = System.nanoTime()
            while (true) {
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0
                val progress = (elapsedMs / LoudnessPolicy.RAMP_MS).coerceIn(0.0, 1.0)
                slot.gain = from + (target - from) * progress
                if (rampJob == null && slot === active) slot.engine.setVolume(userVolume * slot.gain)
                if (progress >= 1.0) break
                delay(RAMP_STEP_MS)
            }
            gainJob = null
        }
    }

    // 主引擎已载入文件的标签值
    fun activeMetadata(key: String): String? = activeSlot.engine.metadataValue(key)

    fun setPaused(paused: Boolean) {
        // 淡化期间暂停：直接收尾，避免恢复后音量跳变
        if (paused) reset()
        activeSlot.engine.setPaused(paused)
    }

    fun seekTo(positionMs: Long) {
        activeSlot.seeked = true
        activeSlot.engine.seekTo(positionMs)
    }

    fun stop() {
        reset()
        activeSlot.discardRecord()
        activeSlot.engine.stop()
    }

    fun setVolume(percent: Int) {
        userVolume = percent.toDouble()
        if (rampJob == null) activeSlot.engine.setVolume(userVolume * activeSlot.gain)
    }

    fun audioDevices(): List<AudioDevice> = activeSlot.engine.audioDevices()

    fun setAudioDevice(name: String): Boolean {
        val ok = activeSlot.engine.setAudioDevice(name)
        if (ok) {
            deviceName = name
            spare?.engine?.setAudioDevice(name)
        }
        return ok
    }

    // 静音预载并停在 startMs，失败返回 false；从中途起播的录制必然不完整，不应传 record
    fun prepareSpare(url: String, record: RecordRequest? = null, startMs: Long = 0L, gainDb: Double = 0.0): Boolean {
        if (spareUnavailable || rampJob != null) return false
        val slot = spare ?: try {
            newSlot().also { created ->
                spare = created
                deviceName?.let { created.engine.setAudioDevice(it) }
            }
        } catch (e: LinkageError) {
            return markSpareUnavailable(e)
        } catch (e: IllegalStateException) {
            return markSpareUnavailable(e)
        }
        slot.discardRecord()
        slot.resetProgress()
        slot.record = record
        slot.seeked = false
        slot.gain = LoudnessPolicy.mpvVolumeFactor(gainDb)
        slot.engine.setVolume(0.0)
        slot.engine.load(url, startMs, paused = true, recordPath = record?.path)
        spareArmed = true
        return true
    }

    private fun deleteWhenReleased(file: File) {
        scope.launch(Dispatchers.IO) {
            repeat(DELETE_ATTEMPTS) {
                if (!file.exists() || file.delete()) return@launch
                delay(DELETE_RETRY_MS)
            }
        }
    }

    private fun markSpareUnavailable(e: Throwable): Boolean {
        AppLogger.w(TAG, "备用引擎创建失败，淡入淡出不可用", e)
        spareUnavailable = true
        return false
    }

    fun discardSpare() {
        spareArmed = false
        spare?.discardRecord()
        spare?.engine?.stop()
    }

    // 备用引擎升为主引擎并开始交叉，返回新主引擎的进度与时长；未就绪返回 null
    fun promote(fadeMs: Long): Pair<Long, Long>? {
        val incoming = spare?.takeIf { spareArmed && it.loaded } ?: return null
        val outgoing = activeSlot
        spare = outgoing
        active = incoming
        spareArmed = false
        gainJob?.cancel()
        gainJob = null
        incoming.engine.setPaused(false)
        rampJob = scope.launch { ramp(outgoing, incoming, fadeMs) }
        return incoming.position to incoming.duration
    }

    // 等功率交叉；录制缓存时渐出引擎多等它读完再停止
    private suspend fun ramp(outgoing: Slot, incoming: Slot, fadeMs: Long) {
        val startedAt = System.nanoTime()
        while (true) {
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0
            val progress = (elapsedMs / fadeMs).coerceIn(0.0, 1.0).toFloat()
            outgoing.engine.setVolume(userVolume * outgoing.gain * CrossfadePolicy.fadeOutGain(progress, 1f))
            incoming.engine.setVolume(userVolume * incoming.gain * CrossfadePolicy.fadeInGain(progress))
            if (progress >= 1f) break
            delay(RAMP_STEP_MS)
        }
        incoming.engine.setVolume(userVolume * incoming.gain)
        if (outgoing.record != null && !outgoing.seeked) {
            withTimeoutOrNull(OUTGOING_DRAIN_TIMEOUT_MS) {
                while (!outgoing.ended) delay(OUTGOING_DRAIN_POLL_MS)
            }
        }
        outgoing.discardRecord()
        outgoing.engine.stop()
        rampJob = null
    }

    private fun reset() {
        rampJob?.cancel()
        rampJob = null
        spareArmed = false
        spare?.discardRecord()
        spare?.engine?.stop()
        activeSlot.engine.setVolume(userVolume * activeSlot.gain)
    }

    fun release() {
        reset()
        activeSlot.engine.release()
        spare?.engine?.release()
    }
}
