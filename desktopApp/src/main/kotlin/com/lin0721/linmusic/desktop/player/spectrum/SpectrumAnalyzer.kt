package com.lin0721.linmusic.desktop.player.spectrum

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal const val SPECTRUM_BANDS = 64
internal const val SPECTRUM_WAVEFORM_SIZE = 1024

private const val FFT_SIZE = 2048
private const val FRAMES_PER_SECOND = 30
private const val MIN_FREQUENCY = 20.0
private const val MAX_FREQUENCY = 16_000.0
private const val FLOOR_DB = -70f
private const val ATTACK = 0.7f
private const val RELEASE = 0.25f

// 低于这个值按零处理，否则衰减段永远到不了零
private const val ZERO_LEVEL = 0.002f

internal class SpectrumResult(val timeMs: Long, val bands: FloatArray, val waveform: FloatArray)

// 连续喂入单声道采样，每 1/30 秒产出一帧；非线程安全
internal class SpectrumAnalyzer(val sampleRate: Int) {
    private val hop = (sampleRate / FRAMES_PER_SECOND).coerceAtLeast(1)
    private val ring = FloatArray(FFT_SIZE)
    private var writeIndex = 0
    private var totalSamples = 0L
    private var sinceFrame = 0

    private val window = FloatArray(FFT_SIZE) { (0.5 - 0.5 * cos(2 * PI * it / (FFT_SIZE - 1))).toFloat() }
    private val cosTable = FloatArray(FFT_SIZE / 2) { cos(2 * PI * it / FFT_SIZE).toFloat() }
    private val sinTable = FloatArray(FFT_SIZE / 2) { (-sin(2 * PI * it / FFT_SIZE)).toFloat() }
    private val reversed = IntArray(FFT_SIZE).also { table ->
        val bits = Integer.numberOfTrailingZeros(FFT_SIZE)
        for (index in table.indices) table[index] = Integer.reverse(index) ushr (Int.SIZE_BITS - bits)
    }
    private val real = FloatArray(FFT_SIZE)
    private val imaginary = FloatArray(FFT_SIZE)

    private val bandBins = bandEdges(sampleRate)
    private val levels = FloatArray(SPECTRUM_BANDS)

    fun process(samples: FloatArray): List<SpectrumResult> {
        val results = ArrayList<SpectrumResult>(1)
        for (sample in samples) {
            ring[writeIndex] = if (sample.isFinite()) sample else 0f
            writeIndex = (writeIndex + 1) % FFT_SIZE
            totalSamples++
            sinceFrame++
            if (sinceFrame >= hop && totalSamples >= FFT_SIZE) {
                sinceFrame = 0
                results += frame()
            }
        }
        return results
    }

    private fun frame(): SpectrumResult {
        for (index in 0 until FFT_SIZE) {
            val target = reversed[index]
            real[target] = ring[(writeIndex + index) % FFT_SIZE] * window[index]
            imaginary[target] = 0f
        }
        transform()
        // Hann 窗的相干增益是 0.5，乘 4/N 后满幅正弦的峰值为 1
        val scale = 4f / FFT_SIZE
        for (band in 0 until SPECTRUM_BANDS) {
            val (low, high) = bandBins[band]
            var peak = 0f
            for (bin in low until high) peak = max(peak, sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin]))
            val previous = levels[band]
            val target = toLevel(peak * scale)
            val next = previous + (target - previous) * if (target > previous) ATTACK else RELEASE
            levels[band] = if (next < ZERO_LEVEL) 0f else next
        }
        val waveform = FloatArray(SPECTRUM_WAVEFORM_SIZE) { ring[(writeIndex + FFT_SIZE - SPECTRUM_WAVEFORM_SIZE + it) % FFT_SIZE] }
        return SpectrumResult(totalSamples * 1000 / sampleRate, levels.copyOf(), waveform)
    }

    // 基 2 迭代 FFT，输入已按位反转排列
    private fun transform() {
        var size = 2
        while (size <= FFT_SIZE) {
            val half = size / 2
            val step = FFT_SIZE / size
            var start = 0
            while (start < FFT_SIZE) {
                for (offset in 0 until half) {
                    val wr = cosTable[offset * step]
                    val wi = sinTable[offset * step]
                    val upper = start + offset
                    val lower = upper + half
                    val tr = real[lower] * wr - imaginary[lower] * wi
                    val ti = real[lower] * wi + imaginary[lower] * wr
                    real[lower] = real[upper] - tr
                    imaginary[lower] = imaginary[upper] - ti
                    real[upper] += tr
                    imaginary[upper] += ti
                }
                start += size
            }
            size *= 2
        }
    }

    private fun toLevel(amplitude: Float): Float {
        if (amplitude <= 0f) return 0f
        val db = 20f * log10(amplitude)
        return ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)
    }

    private companion object {
        // 低频频段比一个频点还窄，至少占一个频点
        fun bandEdges(sampleRate: Int): List<Pair<Int, Int>> {
            val binHz = sampleRate.toDouble() / FFT_SIZE
            val maxBin = FFT_SIZE / 2
            val ratio = (MAX_FREQUENCY / MIN_FREQUENCY).pow(1.0 / SPECTRUM_BANDS)
            return List(SPECTRUM_BANDS) { band ->
                val low = floor(MIN_FREQUENCY * ratio.pow(band) / binHz).toInt().coerceIn(1, maxBin - 1)
                val high = ceil(MIN_FREQUENCY * ratio.pow(band + 1) / binHz).toInt().coerceIn(low + 1, maxBin)
                low to high
            }
        }
    }
}
