package com.lin0721.linmusic.feature.localmusic.data.metadata

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import com.kyant.taglib.Metadata
import com.kyant.taglib.TagLib
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CancellationException

private const val TAG = "WavTagReader"

internal fun readWavTags(
    context: Context,
    uri: Uri,
    fileName: String? = null,
    mimeType: String? = null
): WavMetadata? {
    if (!isWavSource(context, uri, fileName, mimeType)) return null
    return readWavMetadata(context, uri, pictures = false)?.let { wavMetadataFromProperties(it.propertyMap) }
}

internal fun readWavPicture(context: Context, uri: Uri): ByteArray? {
    if (!isWavSource(context, uri)) return null
    val pictures = readWavMetadata(context, uri, pictures = true)?.pictures ?: return null
    return pictures.firstOrNull { it.pictureType == "Front Cover" && it.data.isNotEmpty() }?.data
        ?: pictures.firstOrNull { it.data.isNotEmpty() }?.data
}

private fun isWavSource(context: Context, uri: Uri, fileName: String? = null, mimeType: String? = null): Boolean {
    // 扫描器/导入器已有文件名和 MIME 时直接使用，避免每首曲目重复查询 provider。
    if (fileName != null || mimeType != null) return isWavAudio(fileName, mimeType)
    if (isWavAudio(Uri.decode(uri.lastPathSegment), null)) return true
    val resolver = context.contentResolver
    if (isWavAudio(null, runCatching { resolver.getType(uri) }
            .onFailure { if (it is CancellationException) throw it }.getOrNull())) return true
    val displayName = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    return isWavAudio(displayName, null)
}

private fun readWavMetadata(context: Context, uri: Uri, pictures: Boolean): Metadata? = try {
    // 仅使用已有读取授权，不能为了补标签而要求写权限或修改原文件。
    context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
        // SAF 可能返回管道；TagLib 需要 seek，不能支持时保留原系统结果。
        Os.lseek(descriptor.fileDescriptor, 0L, OsConstants.SEEK_CUR)
        val nativeFd = descriptor.dup().detachFd()
        try {
            // TagLib 接管并关闭传入的副本，原 descriptor 由 use 关闭，不能重复关闭 nativeFd。
            TagLib.getMetadata(nativeFd, readPictures = pictures)
        } catch (error: LinkageError) {
            // JNI 未能加载/解析时尚未接管 fd，这条失败路径需自行回收副本。
            runCatching { ParcelFileDescriptor.adoptFd(nativeFd).close() }
            throw error
        }
    }
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    AppLogger.w(TAG, "补读 WAV 内嵌标签失败 uri=$uri", error)
    null
} catch (error: LinkageError) {
    AppLogger.w(TAG, "WAV 标签读取库不可用 uri=$uri", error)
    null
}
