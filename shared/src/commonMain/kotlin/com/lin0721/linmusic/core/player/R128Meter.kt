package com.lin0721.linmusic.core.player

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

// 积分响度 (LUFS) 与线性采样峰值
data class LoudnessAnalysis(
    val integratedLufs: Double,
    val samplePeak: Double
)

// ITU-R BS.1770-4 / EBU R128 积分响度计，多段各自滤波后合并门限
class R128Meter {

    private val blockEnergies = ArrayList<Double>()
    private var maxSamplePeak = 0.0

    // 单段的 K 加权滤波状态与 400ms (75% 重叠) 块
    class Segment(val sampleRate: Int, val channels: Int) {

        private val b10: Double
        private val b11: Double
        private val b12: Double
        private val a11: Double
        private val a12: Double

        private val b20: Double
        private val b21: Double
        private val b22: Double
        private val a21: Double
        private val a22: Double

        private val weights: DoubleArray
        private val stepFrames: Int

        private val z11: DoubleArray
        private val z12: DoubleArray
        private val z21: DoubleArray
        private val z22: DoubleArray
        private val subSumSq: DoubleArray

        private val ring = DoubleArray(4)
        private var ringIndex = 0
        private var subBlocksFilled = 0
        private var framesInSub = 0

        private val blocks = ArrayList<Double>()
        var samplePeak: Double = 0.0
            private set

        init {
            require(sampleRate > 0) { "采样率必须大于 0: $sampleRate" }
            require(channels > 0) { "声道数必须大于 0: $channels" }

            // 高架滤波
            val f0Shelf = 1681.974450955533
            val qShelf = 0.7071752369554196
            val dbShelf = 3.999843853973347
            val k1 = tan(PI * f0Shelf / sampleRate)
            val vh = 10.0.pow(dbShelf / 20.0)
            val vb = vh.pow(0.4996667741545416)
            val a10 = 1.0 + k1 / qShelf + k1 * k1
            b10 = (vh + vb * k1 / qShelf + k1 * k1) / a10
            b11 = 2.0 * (k1 * k1 - vh) / a10
            b12 = (vh - vb * k1 / qShelf + k1 * k1) / a10
            a11 = 2.0 * (k1 * k1 - 1.0) / a10
            a12 = (1.0 - k1 / qShelf + k1 * k1) / a10

            // RLB 高通
            val f0Hp = 38.13547087602444
            val qHp = 0.5003270373238773
            val k2 = tan(PI * f0Hp / sampleRate)
            val a20 = 1.0 + k2 / qHp + k2 * k2
            b20 = 1.0
            b21 = -2.0
            b22 = 1.0
            a21 = 2.0 * (k2 * k2 - 1.0) / a20
            a22 = (1.0 - k2 / qHp + k2 * k2) / a20

            weights = channelWeights(channels)
            stepFrames = (sampleRate * 0.1).roundToInt().coerceAtLeast(1)

            z11 = DoubleArray(channels)
            z12 = DoubleArray(channels)
            z21 = DoubleArray(channels)
            z22 = DoubleArray(channels)
            subSumSq = DoubleArray(channels)
        }

        // 交错 float PCM，-1..1
        fun process(pcm: FloatArray, offset: Int = 0, sampleCount: Int = pcm.size - offset) {
            require(offset >= 0 && sampleCount >= 0 && offset + sampleCount <= pcm.size) {
                "无效的 PCM 切片 offset=$offset sampleCount=$sampleCount size=${pcm.size}"
            }
            val chCount = channels
            val frames = sampleCount / chCount
            if (frames <= 0) return

            val lb10 = b10
            val lb11 = b11
            val lb12 = b12
            val la11 = a11
            val la12 = a12
            val lb20 = b20
            val lb21 = b21
            val lb22 = b22
            val la21 = a21
            val la22 = a22

            var idx = offset
            var peak = samplePeak
            var inSub = framesInSub
            val step = stepFrames

            for (f in 0 until frames) {
                for (ch in 0 until chCount) {
                    val raw = pcm[idx++]
                    val x = if (raw.isFinite()) raw.toDouble() else 0.0
                    val ax = abs(x)
                    if (ax > peak) peak = ax

                    val y1 = lb10 * x + z11[ch]
                    z11[ch] = lb11 * x - la11 * y1 + z12[ch]
                    z12[ch] = lb12 * x - la12 * y1

                    val y2 = lb20 * y1 + z21[ch]
                    z21[ch] = lb21 * y1 - la21 * y2 + z22[ch]
                    z22[ch] = lb22 * y1 - la22 * y2

                    subSumSq[ch] += y2 * y2
                }
                inSub++
                if (inSub == step) {
                    var weighted = 0.0
                    for (ch in 0 until chCount) {
                        weighted += weights[ch] * (subSumSq[ch] / step)
                        subSumSq[ch] = 0.0
                    }
                    ring[ringIndex] = weighted
                    ringIndex = (ringIndex + 1) and 3
                    if (subBlocksFilled < 4) subBlocksFilled++
                    if (subBlocksFilled == 4) {
                        blocks += (ring[0] + ring[1] + ring[2] + ring[3]) * 0.25
                    }
                    inSub = 0
                }
            }

            framesInSub = inSub
            samplePeak = peak
        }

        internal fun blockEnergies(): List<Double> = blocks
    }

    fun addSegment(segment: Segment) {
        blockEnergies.addAll(segment.blockEnergies())
        if (segment.samplePeak > maxSamplePeak) maxSamplePeak = segment.samplePeak
    }

    fun addSegment(interleavedPcm: FloatArray, sampleRate: Int, channels: Int, sampleCount: Int = interleavedPcm.size) {
        val segment = Segment(sampleRate, channels)
        segment.process(interleavedPcm, 0, sampleCount)
        addSegment(segment)
    }

    // -70 LUFS 绝对门限与 -10 LU 相对门限，全低于门限返回 null
    fun analyze(): LoudnessAnalysis? {
        if (blockEnergies.isEmpty()) return null

        var absCount = 0
        var absSum = 0.0
        for (i in blockEnergies.indices) {
            val energy = blockEnergies[i]
            if (energy > ABSOLUTE_GATE_ENERGY) {
                absSum += energy
                absCount++
            }
        }
        if (absCount == 0) return null

        val ungatedLufs = LUFS_OFFSET + 10.0 * log10(absSum / absCount)
        val relativeThresholdEnergy = 10.0.pow((ungatedLufs + RELATIVE_GATE_LU - LUFS_OFFSET) / 10.0)

        var relCount = 0
        var relSum = 0.0
        for (i in blockEnergies.indices) {
            val energy = blockEnergies[i]
            if (energy > ABSOLUTE_GATE_ENERGY && energy > relativeThresholdEnergy) {
                relSum += energy
                relCount++
            }
        }
        if (relCount == 0) return null

        val integratedLufs = LUFS_OFFSET + 10.0 * log10(relSum / relCount)
        if (!integratedLufs.isFinite()) return null
        return LoudnessAnalysis(integratedLufs = integratedLufs, samplePeak = maxSamplePeak)
    }

    companion object {
        private const val LUFS_OFFSET = -0.691
        private const val ABSOLUTE_GATE_LUFS = -70.0
        private const val RELATIVE_GATE_LU = -10.0
        private val ABSOLUTE_GATE_ENERGY = 10.0.pow((ABSOLUTE_GATE_LUFS - LUFS_OFFSET) / 10.0)

        // 环绕声道 +1.5 dB，LFE 不计入
        private val SURROUND_WEIGHT = 10.0.pow(1.5 / 10.0)

        private fun channelWeights(channels: Int): DoubleArray = when (channels) {
            1, 2, 3 -> DoubleArray(channels) { 1.0 }
            4 -> doubleArrayOf(1.0, 1.0, SURROUND_WEIGHT, SURROUND_WEIGHT)
            5 -> doubleArrayOf(1.0, 1.0, 1.0, SURROUND_WEIGHT, SURROUND_WEIGHT)
            6 -> doubleArrayOf(1.0, 1.0, 1.0, 0.0, SURROUND_WEIGHT, SURROUND_WEIGHT)
            8 -> doubleArrayOf(1.0, 1.0, 1.0, 0.0, SURROUND_WEIGHT, SURROUND_WEIGHT, SURROUND_WEIGHT, SURROUND_WEIGHT)
            else -> DoubleArray(channels) { ch -> if (ch < 3) 1.0 else SURROUND_WEIGHT }
        }

        fun measure(interleavedPcm: FloatArray, sampleRate: Int, channels: Int): LoudnessAnalysis? {
            val meter = R128Meter()
            meter.addSegment(interleavedPcm, sampleRate, channels)
            return meter.analyze()
        }
    }
}
