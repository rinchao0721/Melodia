package com.lin0721.linmusic.feature.localmusic.data.scan

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.kyant.taglib.AudioProperties
import com.kyant.taglib.TagLib
import com.lin0721.linmusic.core.download.recognition.estimateBitrateKbps
import com.lin0721.linmusic.core.download.recognition.guessAudioQuality
import com.lin0721.linmusic.core.download.recognition.isLosslessAudioExtension
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CancellationException
import java.net.URLDecoder
import java.util.Locale

private const val TAG = "LocalAudioQuality"
private const val CD_SAMPLE_RATE_MAX = 48_000

private val MIME_EXTENSION_MAP = mapOf(
    "audio/flac" to "flac",
    "audio/x-flac" to "flac",
    "audio/wav" to "wav",
    "audio/x-wav" to "wav",
    "audio/wave" to "wav",
    "audio/vnd.wave" to "wav",
    "audio/mpeg" to "mp3",
    "audio/mp3" to "mp3",
    "audio/mp4" to "m4a",
    "audio/m4a" to "m4a",
    "audio/x-m4a" to "m4a",
    "audio/aac" to "aac",
    "audio/x-aac" to "aac",
    "audio/ogg" to "ogg",
    "application/ogg" to "ogg",
    "audio/opus" to "opus",
    "audio/aiff" to "aiff",
    "audio/x-aiff" to "aiff",
    "audio/ape" to "ape",
    "audio/x-ape" to "ape",
    "audio/x-wavpack" to "wv",
    "audio/x-ms-wma" to "wma"
)

internal data class LocalAudioMetrics(
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0
)

internal fun resolveAudioExtension(
    pathOrName: String?,
    mimeType: String? = null,
    uriLastSegment: String? = null
): String {
    extensionFromFileName(pathOrName)?.let { return it }
    extensionFromFileName(uriLastSegment)?.let { return it }
    val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    return MIME_EXTENSION_MAP[normalizedMime].orEmpty()
}

private fun extensionFromFileName(raw: String?): String? {
    val fileName = raw?.trim()?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotEmpty() }
        ?: return null
    val dot = fileName.lastIndexOf('.')
    if (dot < 0 || dot == fileName.lastIndex) return null
    return fileName.substring(dot + 1).trim().lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }
}

private fun decodePercentSafe(value: String): String =
    runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)

internal fun inferLocalQualityLevel(
    extension: String,
    bitrateKbps: Int,
    sampleRateHz: Int,
    bitDepth: Int,
    sizeBytes: Long,
    durationMs: Long
): String? {
    val ext = extension.trim().trimStart('.').lowercase(Locale.ROOT)
    val effectiveBitrate = bitrateKbps.takeIf { it > 0 } ?: estimateBitrateKbps(sizeBytes, durationMs)
    if (ext.isBlank()) {
        if (effectiveBitrate <= 0 && sampleRateHz <= 0 && bitDepth <= 0) return null
        return guessAudioQuality("", effectiveBitrate, sampleRateHz, bitDepth)
    }
    if (isLosslessAudioExtension(ext)) {
        return guessAudioQuality(ext, effectiveBitrate, sampleRateHz, bitDepth)
    }
    if (effectiveBitrate <= 0) return null
    return guessAudioQuality(ext, effectiveBitrate, sampleRateHz, bitDepth)
}

internal fun fallbackLocalQualityLevel(
    pathOrName: String?,
    uriString: String?,
    sizeBytes: Long,
    durationMs: Long,
    mimeType: String? = null
): String? {
    val lastSegment = uriString
        ?.substringBefore('?')
        ?.substringBefore('#')
        ?.substringAfterLast('/')
        ?.let(::decodePercentSafe)
    val ext = resolveAudioExtension(pathOrName, mimeType, lastSegment)
    return inferLocalQualityLevel(ext, 0, 0, 0, sizeBytes, durationMs)
}

internal fun resolveMediaStoreQualityLevel(
    context: Context,
    uri: Uri,
    path: String?,
    displayName: String?,
    mimeType: String?,
    sizeBytes: Long,
    durationMs: Long,
    mediaStoreBitrateKbps: Int
): String? {
    val ext = resolveAudioExtension(path ?: displayName, mimeType, Uri.decode(uri.lastPathSegment))
    val isLossless = isLosslessAudioExtension(ext)
    val needProbe = isLossless || mediaStoreBitrateKbps <= 0
    val tagLibProps = if (needProbe) readTagLibAudioProperties(context, uri) else null

    var bitrateKbps = tagLibProps?.bitrate?.takeIf { it > 0 } ?: mediaStoreBitrateKbps
    var sampleRateHz = tagLibProps?.sampleRate?.takeIf { it > 0 } ?: 0
    var bitDepth = 0

    // TagLib Android 绑定未暴露位深；无损且采样率 <= 48kHz 时在 API 31+ 用 MediaMetadataRetriever 补查 24bit Hi-Res
    val shouldCheckRetriever = (isLossless && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sampleRateHz <= CD_SAMPLE_RATE_MAX) ||
        (bitrateKbps <= 0 && estimateBitrateKbps(sizeBytes, durationMs) <= 0)
    if (shouldCheckRetriever) {
        val mmr = readRetrieverAudioMetrics(context, uri)
        if (bitrateKbps <= 0) bitrateKbps = mmr.bitrateKbps
        if (sampleRateHz <= 0) sampleRateHz = mmr.sampleRateHz
        bitDepth = mmr.bitDepth
    }

    return inferLocalQualityLevel(ext, bitrateKbps, sampleRateHz, bitDepth, sizeBytes, durationMs)
}

internal fun resolveImportedQualityLevel(
    context: Context,
    uri: Uri,
    displayName: String?,
    mimeType: String?,
    retrieverBitrateKbps: Int,
    retrieverSampleRateHz: Int,
    retrieverBitDepth: Int,
    sizeBytes: Long,
    durationMs: Long
): String? {
    val ext = resolveAudioExtension(displayName, mimeType, Uri.decode(uri.lastPathSegment))
    val isLossless = isLosslessAudioExtension(ext)
    var bitrateKbps = retrieverBitrateKbps
    var sampleRateHz = retrieverSampleRateHz

    if (bitrateKbps <= 0 || (isLossless && sampleRateHz <= 0)) {
        val props = readTagLibAudioProperties(context, uri)
        if (bitrateKbps <= 0) bitrateKbps = props?.bitrate?.takeIf { it > 0 } ?: 0
        if (sampleRateHz <= 0) sampleRateHz = props?.sampleRate?.takeIf { it > 0 } ?: 0
    }

    return inferLocalQualityLevel(ext, bitrateKbps, sampleRateHz, retrieverBitDepth, sizeBytes, durationMs)
}

internal fun readRetrieverAudioMetrics(context: Context, uri: Uri): LocalAudioMetrics {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val bitrateKbps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            ?.toIntOrNull()?.takeIf { it > 0 }?.let { it / 1000 } ?: 0
        val sampleRateHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.takeIf { it > 0 } ?: 0
        } else {
            0
        }
        val bitDepth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?.takeIf { it > 0 } ?: 0
        } else {
            0
        }
        LocalAudioMetrics(bitrateKbps, sampleRateHz, bitDepth)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LocalAudioMetrics()
    } finally {
        runCatching { retriever.release() }
    }
}

internal fun readTagLibAudioProperties(context: Context, uri: Uri): AudioProperties? = try {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
        // TagLib 需要可 seek 的 fd；管道等不可 seek 时跳过以免底层失败
        Os.lseek(descriptor.fileDescriptor, 0L, OsConstants.SEEK_CUR)
        val nativeFd = descriptor.dup().detachFd()
        try {
            TagLib.getAudioProperties(nativeFd)
        } catch (error: LinkageError) {
            runCatching { ParcelFileDescriptor.adoptFd(nativeFd).close() }
            throw error
        }
    }
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    AppLogger.w(TAG, "读取本地音频属性失败 uri=$uri", error)
    null
} catch (error: LinkageError) {
    AppLogger.w(TAG, "音频属性读取库不可用 uri=$uri", error)
    null
}
