package com.lin0721.linmusic.feature.recognition.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.math.min
import kotlin.math.sqrt

// 指纹库要求的输入格式：8 kHz 单声道 Float32
internal const val RECOGNITION_SAMPLE_RATE = 8000

internal object PcmMath {

    // 归一化参数沿用 SPlayer-Next：弱信号放大到目标 RMS，增益封顶避免把底噪放大成指纹
    private const val TARGET_RMS = 0.1f
    private const val MAX_GAIN = 50f

    fun rms(samples: FloatArray, from: Int = 0, to: Int = samples.size): Float {
        val end = to.coerceAtMost(samples.size)
        val start = from.coerceIn(0, end)
        val count = end - start
        if (count <= 0) return 0f
        var energy = 0.0
        for (i in start until end) {
            val s = samples[i].toDouble()
            energy += s * s
        }
        return sqrt(energy / count).toFloat()
    }

    // 只放大不衰减，全零输入原样返回
    fun normalize(samples: FloatArray): FloatArray {
        val current = rms(samples)
        if (current <= 0f) return samples
        val gain = min(MAX_GAIN, TARGET_RMS / current)
        if (gain <= 1f) return samples
        return FloatArray(samples.size) { samples[it] * gain }
    }

    // WebView 侧按小端 Float32Array 解读
    fun toLittleEndianBase64(samples: FloatArray): String {
        val buffer = ByteBuffer.allocate(samples.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(samples)
        return Base64.getEncoder().encodeToString(buffer.array())
    }
}

// 按区间平均把任意采样率抽到目标采样率，跨块保留余量。
// 区间边界用整数有理数计算：浮点累加在 44.1k 这类非整数倍时会让分块与整段结果分叉
internal class Downsampler(private val sourceRate: Int, private val targetRate: Int) {

    init {
        require(sourceRate > 0 && targetRate > 0) { "采样率必须为正: $sourceRate -> $targetRate" }
        require(sourceRate >= targetRate) { "只支持降采样: $sourceRate -> $targetRate" }
    }

    private val passthrough = sourceRate == targetRate
    private var carry = FloatArray(0)
    // carry[0] 在整段输入中的绝对下标
    private var carryStart = 0L
    private var outputIndex = 0L

    fun process(input: FloatArray, length: Int = input.size): FloatArray {
        val valid = length.coerceIn(0, input.size)
        if (passthrough) return input.copyOf(valid)

        val merged = FloatArray(carry.size + valid)
        carry.copyInto(merged)
        input.copyInto(merged, carry.size, 0, valid)
        val mergedEnd = carryStart + merged.size

        val output = FloatArray((merged.size.toLong() * targetRate / sourceRate).toInt() + 1)
        var count = 0
        while (true) {
            val from = windowStart(outputIndex)
            val to = windowStart(outputIndex + 1).let { next ->
                // 非整数倍时区间右端向上取整，与相邻区间共享边界样本
                if (next * targetRate == (outputIndex + 1) * sourceRate) next else next + 1
            }
            if (to > mergedEnd) break
            var sum = 0f
            for (i in (from - carryStart).toInt() until (to - carryStart).toInt()) sum += merged[i]
            output[count++] = sum / (to - from)
            outputIndex++
        }

        val nextFrom = windowStart(outputIndex)
        carry = merged.copyOfRange((nextFrom - carryStart).toInt().coerceIn(0, merged.size), merged.size)
        carryStart = nextFrom
        return output.copyOf(count)
    }

    private fun windowStart(index: Long): Long = index * sourceRate / targetRate
}
