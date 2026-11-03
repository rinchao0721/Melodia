package com.lin0721.linmusic.core.player.usb

// 时钟源 GET RANGE 返回的一个子区间；step 为 0 时只有端点两个值
internal data class SampleRateRange(val min: Int, val max: Int, val step: Int) {

    fun contains(rate: Int): Boolean {
        if (rate < min || rate > max) return false
        if (rate == min || rate == max) return true
        return step > 0 && (rate - min) % step == 0
    }
}

internal object Uac2SampleRates {

    // 格式：wNumSubRanges(2) 后接 n 组 dMIN/dMAX/dRES(各 4 字节)，长度不足的尾部忽略
    fun parse(bytes: ByteArray): List<SampleRateRange> {
        if (bytes.size < 2) return emptyList()
        val count = u16(bytes, 0)
        return (0 until count).mapNotNull { index ->
            val base = 2 + index * 12
            if (base + 12 > bytes.size) return@mapNotNull null
            val min = u32(bytes, base)
            val max = u32(bytes, base + 4)
            val step = u32(bytes, base + 8)
            if (min <= 0 || max < min) null else SampleRateRange(min, max, step)
        }
    }

    private fun u16(bytes: ByteArray, index: Int): Int =
        (bytes[index].toInt() and 0xFF) or ((bytes[index + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, index: Int): Int = u16(bytes, index) or (u16(bytes, index + 2) shl 16)
}

// 一台 DAC 的输出能力，接管一次后缓存，释放后仍可用来判断格式
internal class UsbAudioCapabilities(
    val outputs: List<Uac2OutputFormat>,
    val rates: List<SampleRateRange>,
    val packetsPerSecond: Int
) {
    fun formatFor(sampleRate: Int, channels: Int): Uac2OutputFormat? =
        Uac2FormatSelector.select(outputs, rates, sampleRate, channels, packetsPerSecond)
}

internal object Uac2FormatSelector {

    // 采样率与声道都要原样支持，否则返回 null 交给系统输出；位深取最高的，PCM 补零扩展无损
    fun select(
        outputs: List<Uac2OutputFormat>,
        rates: List<SampleRateRange>,
        sampleRate: Int,
        channels: Int,
        packetsPerSecond: Int
    ): Uac2OutputFormat? {
        if (sampleRate <= 0 || channels <= 0 || packetsPerSecond <= 0) return null
        if (rates.none { it.contains(sampleRate) }) return null
        return outputs
            .filter { it.channels == channels && fitsPacket(it, sampleRate, packetsPerSecond) }
            .sortedWith(compareByDescending<Uac2OutputFormat> { it.bitResolution }.thenBy { it.subslotBytes })
            .firstOrNull()
    }

    // 每包最多可能发出的帧数：名义值向上取整，有反馈时速率可能略高，再留一帧
    fun fitsPacket(format: Uac2OutputFormat, sampleRate: Int, packetsPerSecond: Int): Boolean {
        val perPacket = packetsPerSecond shr (format.dataInterval - 1).coerceIn(0, 3)
        if (perPacket <= 0) return false
        var frames = (sampleRate + perPacket - 1) / perPacket
        if (format.feedbackEndpoint != 0 || format.syncType == SYNC_ASYNC) frames += 1
        return frames.toLong() * format.subslotBytes * format.channels <= format.dataMaxPacket
    }
}
