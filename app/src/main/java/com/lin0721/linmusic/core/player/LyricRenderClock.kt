package com.lin0721.linmusic.core.player

import kotlin.math.abs
import kotlin.math.max

internal data class PlaybackPositionSample(val mediaId: String, val positionMs: Long)

/** 所有歌词行共用的绘制时钟。采样校正只调整推进速度，不把已显示的进度拉回。 */
internal class LyricRenderClock {
    private var position: Double? = null
    private var lastRaw = 0L
    private var lastFrameMs = 0L
    private var lastSampleMs = 0L
    private var wasPlaying = false
    private var pendingSeek: Long? = null
    private var seekTimeMs = 0L
    private var lastMediaId: String? = null

    fun reset(positionMs: Long, nowMs: Long, isPlaying: Boolean, awaitSeek: Boolean = false) {
        position = positionMs.toDouble()
        lastRaw = positionMs
        lastFrameMs = nowMs
        lastSampleMs = nowMs
        wasPlaying = isPlaying
        pendingSeek = positionMs.takeIf { awaitSeek }
        seekTimeMs = nowMs
    }

    fun sample(rawPositionMs: Long, nowMs: Long, isPlaying: Boolean, mediaId: String? = null): Long {
        val previous = position
        if (previous == null || mediaId != lastMediaId) {
            lastMediaId = mediaId
            reset(rawPositionMs, nowMs, isPlaying)
            return rawPositionMs
        }
        // 会话 seek 指令尚未到达播放服务时，忽略旧位置，避免刚跳转又闪回。
        val target = pendingSeek
        if (target != null && abs(rawPositionMs - target) > 200L && nowMs - seekTimeMs < 500L) {
            val elapsed = if (isPlaying && wasPlaying) (nowMs - lastFrameMs).coerceAtLeast(0L) else 0L
            position = previous + elapsed
            lastFrameMs = nowMs
            wasPlaying = isPlaying
            return position!!.toLong()
        }
        pendingSeek = null
        // 先识别真实跳转；恢复绘制时也必须允许循环或外部 seek 立即重置。
        if (rawPositionMs < lastRaw - 250L || rawPositionMs - lastRaw > max(2000L, nowMs - lastSampleMs + 2000L)) {
            reset(rawPositionMs, nowMs, isPlaying)
            return rawPositionMs
        }
        // 恢复播放/绘制只更新时间基准，不累计隐藏或暂停期间的帧间隔。
        // 平滑进度可能略领先原始采样，不能把已经亮起的下一句拉回再重播。
        if (isPlaying && (!wasPlaying || nowMs - lastFrameMs > 500L)) {
            reset(rawPositionMs, nowMs, isPlaying)
            position = max(previous, rawPositionMs.toDouble())
            return position!!.toLong()
        }
        val elapsed = if (isPlaying && wasPlaying) (nowMs - lastFrameMs).coerceAtLeast(0L) else 0L
        lastFrameMs = nowMs
        var next = previous + elapsed
        if (rawPositionMs != lastRaw) {
            lastRaw = rawPositionMs
            lastSampleMs = nowMs
            // 小幅时钟漂移渐进收敛；最大减速 10%，不能在新采样到来时回退或停顿。
            val correction = ((rawPositionMs - next) * 0.15)
                .coerceIn(-elapsed * 0.1, elapsed * 0.1)
            next += correction
        }
        if (!isPlaying) next = previous
        // 采样停滞时最多外推 500ms，避免缓冲/服务失联后无限推进。
        position = max(previous, next.coerceAtMost(lastRaw + 500.0))
        wasPlaying = isPlaying
        return position!!.toLong()
    }
}
