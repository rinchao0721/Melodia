package com.lin0721.linmusic.desktop.player.mpv

import java.util.Locale

// 音量平衡滤镜：volume + 防削波 alimiter
internal object MpvGainFilter {

    const val LABEL = "mlgain"
    const val COMMAND_VOLUME = "volume"
    private const val LIMITER_SPEC = "alimiter=limit=0.891:attack=5:release=100:level=disabled"

    fun buildAfSpec(gainDb: Double): String =
        "@$LABEL:lavfi=[volume=${formatVolumeArg(gainDb)},$LIMITER_SPEC]"

    fun formatVolumeArg(gainDb: Double): String {
        val safe = if (gainDb.isFinite()) gainDb else 0.0
        return String.format(Locale.ROOT, "%.3fdB", safe)
    }

    fun rampProgress(elapsedMs: Double, rampMs: Long): Double =
        if (rampMs <= 0L) 1.0 else (elapsedMs / rampMs).coerceIn(0.0, 1.0)

    fun interpolateGainDb(fromDb: Double, toDb: Double, progress: Double): Double {
        val safeFrom = if (fromDb.isFinite()) fromDb else 0.0
        val safeTo = if (toDb.isFinite()) toDb else 0.0
        val t = progress.coerceIn(0.0, 1.0)
        return safeFrom + (safeTo - safeFrom) * t
    }
}
