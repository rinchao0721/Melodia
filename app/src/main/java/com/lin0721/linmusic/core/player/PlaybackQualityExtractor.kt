package com.lin0721.linmusic.core.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.lin0721.linmusic.core.model.StreamAudioInfo

private val FLAC_MAGIC = byteArrayOf(0x66, 0x4C, 0x61, 0x43) // "fLaC"
private const val FLAC_METADATA_BLOCK_STREAMINFO = 0
private const val FLAC_STREAMINFO_LENGTH = 34
private const val FLAC_HEADER_AND_STREAMINFO_MIN_SIZE = 4 + 4 + FLAC_STREAMINFO_LENGTH
private const val FLAC_STREAMINFO_OFFSET = 8
private const val FLAC_MIN_BITS_PER_SAMPLE = 4
private const val FLAC_MAX_BITS_PER_SAMPLE = 32

// 从 Tracks 中提取当前选中的音轨 Format
@OptIn(UnstableApi::class)
fun extractSelectedAudioFormat(tracks: Tracks): Format? {
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        for (index in 0 until group.length) {
            if (group.isTrackSelected(index)) {
                return group.getTrackFormat(index)
            }
        }
    }
    return null
}

// 报错时选轨可能失败导致 isTrackSelected 为 false，遍历所有音轨作为 MIME 兜底来源
@OptIn(UnstableApi::class)
fun extractAudioMimeForError(tracks: Tracks): String? {
    var fallback: String? = null
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        for (index in 0 until group.length) {
            val mime = group.getTrackFormat(index).sampleMimeType?.takeIf { it.isNotBlank() } ?: continue
            if (group.isTrackSelected(index)) return mime
            if (fallback == null) fallback = mime
        }
    }
    return fallback
}

// 有音轨但一条都没选中、且全部不受支持时返回其 MIME；选中或任一受支持都视为可播
@OptIn(UnstableApi::class)
fun findUnplayableAudioMime(tracks: Tracks): String? {
    var mime: String? = null
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        for (index in 0 until group.length) {
            if (group.isTrackSelected(index) || group.isTrackSupported(index)) return null
            if (mime == null) mime = group.getTrackFormat(index).sampleMimeType?.takeIf { it.isNotBlank() }
        }
    }
    return mime
}

// 从 Tracks 提取当前选中音轨的流参数，没有任何有效字段时返回 null
@OptIn(UnstableApi::class)
fun extractStreamAudioInfo(tracks: Tracks): StreamAudioInfo? {
    val format = extractSelectedAudioFormat(tracks) ?: return null
    val info = mapFormatToStreamAudioInfo(format)
    return info.takeIf {
        it.codec != null ||
            it.sampleRateHz != null ||
            it.bitDepth != null ||
            it.bitrateBps != null ||
            it.channels != null
    }
}

// 将 Media3 Format 映射为跨平台 StreamAudioInfo；FLAC 未提供 pcmEncoding 时尝试解析 STREAMINFO 位深
@OptIn(UnstableApi::class)
fun mapFormatToStreamAudioInfo(format: Format): StreamAudioInfo {
    val codec = format.sampleMimeType?.takeIf { it.isNotBlank() }
        ?: format.codecs?.takeIf { it.isNotBlank() }
    val sampleRateHz = format.sampleRate.takeIf { it > 0 }
    val channels = format.channelCount.takeIf { it > 0 }
    val bitrateBps = format.averageBitrate.takeIf { it > 0 }
        ?: format.bitrate.takeIf { it > 0 }
        ?: format.peakBitrate.takeIf { it > 0 }
    val bitDepth = pcmEncodingToBitDepth(format.pcmEncoding)
        ?: if (isFlacCodec(codec)) parseFlacStreamInfoBitDepth(format.initializationData) else null

    return StreamAudioInfo(
        codec = codec,
        sampleRateHz = sampleRateHz,
        bitDepth = bitDepth,
        bitrateBps = bitrateBps,
        channels = channels
    )
}

// 将 Media3 PcmEncoding 常量映射为位深（bit），无效或未设置时返回 null
@OptIn(UnstableApi::class)
fun pcmEncodingToBitDepth(pcmEncoding: Int): Int? = when (pcmEncoding) {
    C.ENCODING_PCM_8BIT -> 8
    C.ENCODING_PCM_16BIT,
    C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
    C.ENCODING_PCM_24BIT,
    C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
    C.ENCODING_PCM_32BIT,
    C.ENCODING_PCM_32BIT_BIG_ENDIAN,
    C.ENCODING_PCM_FLOAT,
    C.ENCODING_PCM_FLOAT_BIG_ENDIAN -> 32
    C.ENCODING_PCM_DOUBLE,
    C.ENCODING_PCM_DOUBLE_BIG_ENDIAN -> 64
    else -> null
}

// 解析 Media3 FLAC initializationData（"fLaC" + 4 字节块头 + 34 字节 STREAMINFO）中的 bitsPerSample；格式不符返回 null
fun parseFlacStreamInfoBitDepth(initializationData: List<ByteArray>): Int? {
    for (data in initializationData) {
        if (data.size < FLAC_HEADER_AND_STREAMINFO_MIN_SIZE) continue
        if (data[0] != FLAC_MAGIC[0] ||
            data[1] != FLAC_MAGIC[1] ||
            data[2] != FLAC_MAGIC[2] ||
            data[3] != FLAC_MAGIC[3]
        ) {
            continue
        }
        val blockType = data[4].toInt() and 0x7F
        val blockLength = ((data[5].toInt() and 0xFF) shl 16) or
            ((data[6].toInt() and 0xFF) shl 8) or
            (data[7].toInt() and 0xFF)
        if (blockType != FLAC_METADATA_BLOCK_STREAMINFO || blockLength != FLAC_STREAMINFO_LENGTH) {
            continue
        }
        // STREAMINFO 偏移 10 字节起为 64bit：sampleRate(20) + (channels-1)(3) + (bitsPerSample-1)(5) + totalSamples(36)
        val b12 = data[FLAC_STREAMINFO_OFFSET + 12].toInt() and 0xFF
        val b13 = data[FLAC_STREAMINFO_OFFSET + 13].toInt() and 0xFF
        val bitsPerSample = (((b12 and 0x01) shl 4) or ((b13 and 0xF0) ushr 4)) + 1
        if (bitsPerSample in FLAC_MIN_BITS_PER_SAMPLE..FLAC_MAX_BITS_PER_SAMPLE) {
            return bitsPerSample
        }
    }
    return null
}

private fun isFlacCodec(codec: String?): Boolean {
    val normalized = codec?.trim()?.lowercase()?.removePrefix("audio/") ?: return false
    return normalized == "flac" || normalized == "x-flac"
}
