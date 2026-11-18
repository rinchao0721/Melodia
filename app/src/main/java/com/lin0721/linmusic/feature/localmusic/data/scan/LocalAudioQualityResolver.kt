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
import com.lin0721.linmusic.core.download.ContainerAudioMetrics
import com.lin0721.linmusic.core.download.isContainerMimeType
import com.lin0721.linmusic.core.download.readExtractorAudioMetrics
import com.lin0721.linmusic.core.download.recognition.estimateBitrateKbps
import com.lin0721.linmusic.core.download.recognition.inferAudioQuality
import com.lin0721.linmusic.core.download.recognition.isContainerAudioExtension
import com.lin0721.linmusic.core.download.recognition.isLosslessAudioExtension
import com.lin0721.linmusic.core.download.recognition.mapLosslessCodecToExtension
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CancellationException
import java.net.URLDecoder
import java.util.Locale

private const val TAG = "LocalAudioQuality"
private const val CD_SAMPLE_RATE_MAX = 48_000

private val MIME_EXTENSION_MAP = mapOf(
    "audio/flac" to "flac",
    "audio/x-flac" to "flac",
    "audio/alac" to "alac",
    "audio/wav" to "wav",
    "audio/x-wav" to "wav",
    "audio/wave" to "wav",
    "audio/vnd.wave" to "wav",
    "audio/mpeg" to "mp3",
    "audio/mp3" to "mp3",
    "audio/mp4" to "m4a",
    "audio/m4a" to "m4a",
    "audio/x-m4a" to "m4a",
    "video/mp4" to "mp4",
    "audio/3gpp" to "3gp",
    "audio/aac" to "aac",
    "audio/x-aac" to "aac",
    "audio/mp4a-latm" to "aac",
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
    durationMs: Long,
    codec: String? = null
): String? = inferAudioQuality(
    extension = extension,
    codec = codec,
    bitrateKbps = bitrateKbps,
    sampleRateHz = sampleRateHz,
    bitDepth = bitDepth,
    sizeBytes = sizeBytes,
    durationMs = durationMs
)

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

internal fun resolveLocalQualityLevel(
    extension: String,
    mimeType: String?,
    sizeBytes: Long,
    durationMs: Long,
    initialBitrateKbps: Int = 0,
    initialSampleRateHz: Int = 0,
    initialBitDepth: Int = 0,
    supportsRetrieverBitDepth: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    probeExtractor: () -> ContainerAudioMetrics = { ContainerAudioMetrics() },
    probeTagLib: () -> AudioProperties? = { null },
    probeRetriever: () -> LocalAudioMetrics = { LocalAudioMetrics() }
): String? {
    val ext = extension.trim().trimStart('.').lowercase(Locale.ROOT)
    val needContainerProbe = isContainerAudioExtension(ext) || isContainerMimeType(mimeType) || ext.isBlank()
    val extractor = if (needContainerProbe) probeExtractor() else ContainerAudioMetrics()
    val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    val codec = extractor.codec ?: normalizedMime?.takeIf { it == "audio/alac" }

    val isLossless = mapLosslessCodecToExtension(codec) != null || (codec == null && isLosslessAudioExtension(ext))
    var bitrateKbps = initialBitrateKbps.takeIf { it > 0 } ?: extractor.bitrateKbps
    var sampleRateHz = initialSampleRateHz.takeIf { it > 0 } ?: extractor.sampleRateHz
    var bitDepth = initialBitDepth.takeIf { it > 0 } ?: extractor.bitDepth

    val needTagLib = (isLossless && sampleRateHz <= 0) || bitrateKbps <= 0
    val tagLibProps = if (needTagLib) probeTagLib() else null
    if (bitrateKbps <= 0) bitrateKbps = tagLibProps?.bitrate?.takeIf { it > 0 } ?: 0
    if (sampleRateHz <= 0) sampleRateHz = tagLibProps?.sampleRate?.takeIf { it > 0 } ?: 0

    // TagLib Android 绑定未暴露位深；无损且采样率 <= 48kHz 时在 API 31+ 用 MediaMetadataRetriever 补查 24bit Hi-Res
    val shouldCheckRetriever = (isLossless && supportsRetrieverBitDepth && bitDepth <= 0 && sampleRateHz <= CD_SAMPLE_RATE_MAX) ||
        (bitrateKbps <= 0 && estimateBitrateKbps(sizeBytes, durationMs) <= 0)
    if (shouldCheckRetriever) {
        val mmr = probeRetriever()
        if (bitrateKbps <= 0) bitrateKbps = mmr.bitrateKbps
        if (sampleRateHz <= 0) sampleRateHz = mmr.sampleRateHz
        if (bitDepth <= 0) bitDepth = mmr.bitDepth
    }

    return inferLocalQualityLevel(ext, bitrateKbps, sampleRateHz, bitDepth, sizeBytes, durationMs, codec)
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
    return resolveLocalQualityLevel(
        extension = ext,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        initialBitrateKbps = mediaStoreBitrateKbps,
        probeExtractor = { readExtractorAudioMetrics(context, uri) },
        probeTagLib = { readTagLibAudioProperties(context, uri) },
        probeRetriever = { readRetrieverAudioMetrics(context, uri) }
    )
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
    return resolveLocalQualityLevel(
        extension = ext,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        initialBitrateKbps = retrieverBitrateKbps,
        initialSampleRateHz = retrieverSampleRateHz,
        initialBitDepth = retrieverBitDepth,
        // 导入路径在调用前已执行过 MediaMetadataRetriever，此处不再重复创建实例
        supportsRetrieverBitDepth = false,
        probeExtractor = { readExtractorAudioMetrics(context, uri) },
        probeTagLib = { readTagLibAudioProperties(context, uri) }
    )
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
