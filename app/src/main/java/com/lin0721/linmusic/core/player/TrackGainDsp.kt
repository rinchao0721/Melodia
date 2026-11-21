package com.lin0721.linmusic.core.player

import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

// 曲目增益按斜坡平滑；增益 > 1 时零延迟限幅到 -1 dBFS（瞬时起音、100ms 释放）
internal class TrackGainDsp(
    private val rampMs: Long = LoudnessPolicy.RAMP_MS
) {
    companion object {
        const val LIMITER_THRESHOLD = 0.891f
        private const val RELEASE_MS = 100L
        private const val RELEASED_LEVEL = 0.999f
    }

    private var sampleRate = 0
    private var channels = 0

    // 应用线程写入指令，音频线程在版本号变化时取走
    private val commandLock = Any()
    @Volatile private var commandVersion = 0L
    private var appliedVersion = 0L
    private var cmdGain = 1f
    private var cmdSnap = true

    private var currentGain = 1f
    private var rampStartGain = 1f
    private var rampTargetGain = 1f
    private var rampIndex = 0
    private var rampFrames = 0

    private var limiterGain = 1f
    private var releaseCoeff = 1f

    fun setGainImmediate(gain: Float) = postCommand(gain, true)

    fun setGainSmooth(gain: Float) = postCommand(gain, false)

    private fun postCommand(gain: Float, snap: Boolean) {
        val safe = if (gain.isFinite() && gain >= 0f) gain else 1f
        synchronized(commandLock) {
            cmdGain = safe
            cmdSnap = snap
            commandVersion++
        }
    }

    // 输出重建时沿用当前增益，只更新格式相关参数
    fun configure(sampleRate: Int, channels: Int) {
        if (sampleRate <= 0 || channels <= 0) return
        this.sampleRate = sampleRate
        this.channels = channels
        releaseCoeff = 1f - exp(-1f / (sampleRate * RELEASE_MS / 1000f).coerceAtLeast(1f))
        syncCommands()
    }

    fun flush() {
        limiterGain = 1f
        syncCommands()
    }

    fun isPassThrough(): Boolean {
        syncCommands()
        return rampFrames == 0 && currentGain == 1f && limiterGain == 1f
    }

    fun currentGain(): Float {
        syncCommands()
        return currentGain
    }

    // input 与 output 可为同一数组
    fun process(input: FloatArray, frames: Int, output: FloatArray) {
        if (frames <= 0 || channels <= 0) return
        syncCommands()
        var pos = 0
        for (frame in 0 until frames) {
            val g = nextGain()
            var peak = 0f
            for (c in 0 until channels) {
                val v = input[pos + c] * g
                output[pos + c] = v
                val a = abs(v)
                if (a > peak) peak = a
            }
            val need = if (g > 1f && peak > LIMITER_THRESHOLD) LIMITER_THRESHOLD / peak else 1f
            limiterGain = if (need < limiterGain) {
                need
            } else {
                val released = limiterGain + (1f - limiterGain) * releaseCoeff
                if (released >= RELEASED_LEVEL && need == 1f) 1f else min(released, need)
            }
            if (limiterGain != 1f) {
                for (c in 0 until channels) output[pos + c] *= limiterGain
            }
            pos += channels
        }
    }

    private fun syncCommands() {
        if (appliedVersion == commandVersion) return
        val gain: Float
        val snap: Boolean
        synchronized(commandLock) {
            appliedVersion = commandVersion
            gain = cmdGain
            snap = cmdSnap
        }
        if (snap || sampleRate <= 0) {
            currentGain = gain
            rampStartGain = gain
            rampTargetGain = gain
            rampIndex = 0
            rampFrames = 0
        } else if (gain != rampTargetGain) {
            rampStartGain = currentGain
            rampTargetGain = gain
            rampFrames = ((sampleRate * rampMs) / 1000L).toInt().coerceAtLeast(1)
            rampIndex = 0
        }
    }

    private fun nextGain(): Float {
        if (rampFrames <= 0) return currentGain
        rampIndex++
        if (rampIndex >= rampFrames) {
            currentGain = rampTargetGain
            rampStartGain = rampTargetGain
            rampIndex = 0
            rampFrames = 0
        } else {
            currentGain = rampStartGain + (rampTargetGain - rampStartGain) * rampIndex / rampFrames
        }
        return currentGain
    }
}

internal object PcmGainCodec {
    fun toFloat(input: ByteBuffer, frames: Int, channels: Int, isFloat: Boolean, out: FloatArray) {
        val count = frames * channels
        if (isFloat) {
            for (i in 0 until count) out[i] = input.getFloat()
        } else {
            for (i in 0 until count) out[i] = input.getShort() / 32768f
        }
    }

    fun fromFloat(src: FloatArray, frames: Int, channels: Int, isFloat: Boolean, out: ByteBuffer) {
        val count = frames * channels
        if (isFloat) {
            for (i in 0 until count) out.putFloat(src[i])
        } else {
            for (i in 0 until count) out.putShort((src[i] * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
        }
    }
}

// 每台 ExoPlayer 一份，输出层重建时沿用
class TrackGainState(rampMs: Long = LoudnessPolicy.RAMP_MS) {
    internal val dsp = TrackGainDsp(rampMs)

    // 新歌起播前用
    fun setGainImmediate(gain: Float) = dsp.setGainImmediate(gain)

    // 播放中修正用
    fun setGainSmooth(gain: Float) = dsp.setGainSmooth(gain)
}
