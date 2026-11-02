package com.lin0721.linmusic.desktop.player.mpv

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.player.AudioDevice
import com.lin0721.linmusic.desktop.player.parseAudioDevices
import com.sun.jna.Pointer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong

private const val TAG = "MpvEngine"

private const val OBSERVE_TIME_POS = 1L
private const val OBSERVE_DURATION = 2L
private const val OBSERVE_PAUSE = 3L

// 纯音频播放内核：只负责“播这个地址/暂停/跳转/音量”，队列与取地址由上层决定
// 仅测试用：初始化前写入的额外选项
class MpvEngine(private val listener: Listener, extraOptions: Map<String, String> = emptyMap()) {

    interface Listener {
        fun onPositionChanged(positionMs: Long)
        fun onDurationChanged(durationMs: Long)
        fun onPauseChanged(paused: Boolean)
        fun onFileLoaded()

        // 自然播完或解码失败；主动 stop/替换文件不回调
        fun onEnded(isError: Boolean)
    }

    private val mpv: LibMpv = LibMpv.load()
    private val ctx: Pointer = mpv.mpv_create() ?: throw IllegalStateException("mpv_create 返回空句柄")
    private val running = AtomicBoolean(true)
    private val eventThread: Thread

    init {
        setOption("vid", "no")
        setOption("video", "no")
        setOption("terminal", "no")
        setOption("idle", "yes")
        setOption("audio-display", "no")
        setOption("keep-open", "no")
        // 在线播放的读取缓存默认前向 150MiB、后向 50MiB，两个实例并存时占用可观；单曲文件通常远小于此
        setOption("demuxer-max-bytes", "32MiB")
        setOption("demuxer-max-back-bytes", "16MiB")
        extraOptions.forEach { (name, value) -> setOption(name, value) }
        check(mpv.mpv_initialize(ctx) >= 0) { "mpv_initialize 失败" }
        mpv.mpv_observe_property(ctx, OBSERVE_TIME_POS, "time-pos", MpvConst.FORMAT_DOUBLE)
        mpv.mpv_observe_property(ctx, OBSERVE_DURATION, "duration", MpvConst.FORMAT_DOUBLE)
        mpv.mpv_observe_property(ctx, OBSERVE_PAUSE, "pause", MpvConst.FORMAT_FLAG)
        eventThread = Thread(::eventLoop, "mpv-events").apply {
            isDaemon = true
            start()
        }
    }

    // paused 为真时载入后停在开头，用于预载
    // recordPath 非空时把读到的流录成该文件（容器由扩展名决定），空串关闭录制
    fun load(url: String, startMs: Long, paused: Boolean = false, recordPath: String? = null) {
        setProperty("stream-record", recordPath.orEmpty())
        // start 为全局选项，对下一个载入的文件生效
        setProperty("start", if (startMs > 0) "+${startMs / 1000.0}" else "none")
        setProperty("pause", if (paused) "yes" else "no")
        command("loadfile", url, "replace")
    }

    // 分析用：start 按 mpv 语法给出，如 "+12.5" 或 "25%"
    fun loadSegment(url: String, start: String) {
        setProperty("stream-record", "")
        setProperty("start", start)
        setProperty("pause", "no")
        command("loadfile", url, "replace")
    }

    // 已载入文件的标签值，mpv 按键名不区分大小写匹配
    fun metadataValue(key: String): String? = getProperty("metadata/by-key/$key")

    fun setPaused(paused: Boolean) = setProperty("pause", if (paused) "yes" else "no")

    fun seekTo(positionMs: Long) = command("seek", (positionMs.coerceAtLeast(0) / 1000.0).toString(), "absolute")

    fun setVolume(percent: Int) = setVolume(percent.toDouble())

    // 淡入淡出需要小数步进，整数档位在低音量下有台阶感
    fun setVolume(percent: Double) = setProperty("volume", "%.3f".format(java.util.Locale.ROOT, percent.coerceIn(0.0, 100.0)))

    // 仅测试用
    internal fun volume(): Double? = getProperty("volume")?.toDoubleOrNull()

    // node 类型属性按字符串读取得到 JSON；读不到返回 null
    fun audioDevices(): List<AudioDevice> = getProperty("audio-device-list")?.let(::parseAudioDevices).orEmpty()

    // 切换失败（设备不存在等）返回 false，原设备保持不变
    fun setAudioDevice(name: String): Boolean {
        if (!running.get()) return false
        val code = mpv.mpv_set_property_string(ctx, "audio-device", name)
        if (code < 0) AppLogger.w(TAG, "切换输出设备失败 $name：${mpv.mpv_error_string(code)}")
        return code >= 0
    }

    fun stop() = command("stop")

    fun release() {
        if (!running.compareAndSet(true, false)) return
        mpv.mpv_wakeup(ctx)
        eventThread.join(1000)
        mpv.mpv_terminate_destroy(ctx)
    }

    private fun eventLoop() {
        while (running.get()) {
            val event = mpv.mpv_wait_event(ctx, 0.5) ?: continue
            try {
                when (event.getInt(MpvConst.EVENT_OFFSET_ID)) {
                    MpvConst.EVENT_NONE -> Unit
                    MpvConst.EVENT_SHUTDOWN -> running.set(false)
                    MpvConst.EVENT_FILE_LOADED -> listener.onFileLoaded()
                    MpvConst.EVENT_END_FILE -> handleEndFile(event)
                    MpvConst.EVENT_PROPERTY_CHANGE -> handlePropertyChange(event)
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "处理 mpv 事件异常", e)
            }
        }
    }

    private fun handleEndFile(event: Pointer) {
        val data = event.getPointer(MpvConst.EVENT_OFFSET_DATA) ?: return
        when (data.getInt(0)) {
            MpvConst.END_FILE_REASON_EOF -> listener.onEnded(isError = false)
            MpvConst.END_FILE_REASON_ERROR -> listener.onEnded(isError = true)
        }
    }

    private fun handlePropertyChange(event: Pointer) {
        val property = event.getPointer(MpvConst.EVENT_OFFSET_DATA) ?: return
        val value = property.getPointer(MpvConst.PROPERTY_OFFSET_DATA)
        val format = property.getInt(MpvConst.PROPERTY_OFFSET_FORMAT)
        when (event.getLong(MpvConst.EVENT_OFFSET_USERDATA)) {
            OBSERVE_TIME_POS -> if (format == MpvConst.FORMAT_DOUBLE && value != null) {
                listener.onPositionChanged((value.getDouble(0) * 1000).roundToLong())
            }
            OBSERVE_DURATION -> if (format == MpvConst.FORMAT_DOUBLE && value != null) {
                listener.onDurationChanged((value.getDouble(0) * 1000).roundToLong())
            }
            OBSERVE_PAUSE -> if (format == MpvConst.FORMAT_FLAG && value != null) {
                listener.onPauseChanged(value.getInt(0) != 0)
            }
        }
    }

    private fun setOption(name: String, value: String) {
        val code = mpv.mpv_set_option_string(ctx, name, value)
        if (code < 0) AppLogger.w(TAG, "设置选项失败 $name=$value：${mpv.mpv_error_string(code)}")
    }

    private fun setProperty(name: String, value: String) {
        if (!running.get()) return
        val code = mpv.mpv_set_property_string(ctx, name, value)
        if (code < 0) AppLogger.w(TAG, "设置属性失败 $name=$value：${mpv.mpv_error_string(code)}")
    }

    private fun getProperty(name: String): String? {
        if (!running.get()) return null
        val pointer = mpv.mpv_get_property_string(ctx, name) ?: return null
        return try {
            pointer.getString(0, "UTF-8")
        } finally {
            mpv.mpv_free(pointer)
        }
    }

    private fun command(vararg args: String) {
        if (!running.get()) return
        val code = mpv.mpv_command(ctx, arrayOf(*args, null))
        if (code < 0) AppLogger.w(TAG, "命令失败 ${args.joinToString(" ")}：${mpv.mpv_error_string(code)}")
    }
}
