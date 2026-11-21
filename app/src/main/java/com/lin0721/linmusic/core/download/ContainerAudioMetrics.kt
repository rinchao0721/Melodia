package com.lin0721.linmusic.core.download

import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CancellationException
import java.util.Locale

private const val TAG = "ContainerAudioMetrics"
private const val ALAC_CONFIG_SIZE = 24
private val ALAC_VALID_BIT_DEPTHS = setOf(8, 16, 20, 24, 32)
private val CONTAINER_MIMES = setOf(
    "audio/mp4",
    "audio/m4a",
    "audio/x-m4a",
    "video/mp4",
    "audio/3gpp"
)

internal data class ContainerAudioMetrics(
    val codec: String? = null,
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0
)

internal fun isContainerMimeType(mimeType: String?): Boolean {
    val normalized = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) ?: return false
    return normalized in CONTAINER_MIMES
}

internal fun readExtractorAudioMetrics(context: Context, uri: Uri): ContainerAudioMetrics {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(context, uri, null)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            if (!mime.startsWith("audio/", ignoreCase = true)) continue
            val sampleRateHz = format.intOrZero(MediaFormat.KEY_SAMPLE_RATE)
            val bitrateKbps = format.intOrZero(MediaFormat.KEY_BIT_RATE).let { if (it > 0) it / 1000 else 0 }
            val bitDepth = extractFormatBitDepth(format, mime, sampleRateHz)
            return ContainerAudioMetrics(
                codec = mime,
                bitrateKbps = bitrateKbps,
                sampleRateHz = sampleRateHz,
                bitDepth = bitDepth
            )
        }
        ContainerAudioMetrics()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.w(TAG, "MediaExtractor 读取音频轨失败 uri=$uri", e)
        ContainerAudioMetrics()
    } catch (e: LinkageError) {
        AppLogger.w(TAG, "MediaExtractor 不可用 uri=$uri", e)
        ContainerAudioMetrics()
    } finally {
        runCatching { extractor.release() }
    }
}

private fun MediaFormat.intOrZero(key: String): Int =
    runCatching { if (containsKey(key)) getInteger(key).takeIf { it > 0 } ?: 0 else 0 }.getOrDefault(0)

private fun extractFormatBitDepth(format: MediaFormat, mime: String, sampleRateHz: Int): Int {
    val pcmEncoding = format.intOrZero(MediaFormat.KEY_PCM_ENCODING)
    val fromPcm = when {
        pcmEncoding == AudioFormat.ENCODING_PCM_8BIT -> 8
        pcmEncoding == AudioFormat.ENCODING_PCM_16BIT -> 16
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && pcmEncoding == AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && pcmEncoding == AudioFormat.ENCODING_PCM_32BIT -> 32
        pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT -> 32
        else -> 0
    }
    if (fromPcm > 0) return fromPcm

    val fromKey = format.intOrZero("bits-per-sample")
    if (fromKey > 0) return fromKey

    if (mime.equals("audio/alac", ignoreCase = true) && format.containsKey("csd-0")) {
        val csdBytes = runCatching {
            val buffer = format.getByteBuffer("csd-0")?.duplicate() ?: return@runCatching null
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        }.getOrNull()
        if (csdBytes != null) {
            return parseAlacCsdBitDepth(csdBytes, sampleRateHz)
        }
    }
    return 0
}

// 从 MP4 ALAC magic cookie（24/28/36 字节三种常见封装）解析位深
internal fun parseAlacCsdBitDepth(csd: ByteArray, expectedSampleRateHz: Int = 0): Int {
    val offset = when {
        csd.size == ALAC_CONFIG_SIZE -> 0
        csd.size == ALAC_CONFIG_SIZE + 4 && csd[0] == 0.toByte() && csd[1] == 0.toByte() &&
            csd[2] == 0.toByte() && csd[3] == 0.toByte() -> 4
        csd.size == ALAC_CONFIG_SIZE + 12 && csd[4] == 'a'.code.toByte() && csd[5] == 'l'.code.toByte() &&
            csd[6] == 'a'.code.toByte() && csd[7] == 'c'.code.toByte() -> 12
        else -> return 0
    }
    val compatibleVersion = csd[offset + 4].toInt() and 0xFF
    if (compatibleVersion != 0) return 0
    val bitDepth = csd[offset + 5].toInt() and 0xFF
    if (bitDepth !in ALAC_VALID_BIT_DEPTHS) return 0
    val sampleRate = ((csd[offset + 20].toInt() and 0xFF) shl 24) or
        ((csd[offset + 21].toInt() and 0xFF) shl 16) or
        ((csd[offset + 22].toInt() and 0xFF) shl 8) or
        (csd[offset + 23].toInt() and 0xFF)
    if (expectedSampleRateHz > 0 && sampleRate != expectedSampleRateHz) return 0
    if (expectedSampleRateHz <= 0 && sampleRate <= 0) return 0
    return bitDepth
}
