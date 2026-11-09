package com.lin0721.linmusic.core.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import com.kyant.taglib.AudioProperties
import com.kyant.taglib.TagLib
import com.lin0721.linmusic.core.download.recognition.LocalAudioEntry
import com.lin0721.linmusic.core.download.recognition.LocalAudioIndexStore
import com.lin0721.linmusic.core.download.recognition.SONG_ID_TAG
import com.lin0721.linmusic.core.download.recognition.estimateBitrateKbps
import com.lin0721.linmusic.core.download.recognition.guessAudioQuality
import com.lin0721.linmusic.core.download.recognition.parseDownloadFileName
import com.lin0721.linmusic.core.download.recognition.parseSongIdTag
import com.lin0721.linmusic.core.log.AppLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DownloadFolderScanner"
private const val MAX_DEPTH = 8
private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "ape", "wv", "aiff", "aif")

// 扫描用户授权目录内的音频文件，建立识别已下载歌曲用的索引
class DownloadFolderScanner(
    private val context: Context,
    private val indexStore: LocalAudioIndexStore
) {

    // 离开设置页不中断扫描
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    // 后台扫描，完成后回调扫描到的音频数量，目录不可读时为 null；已在扫描时忽略
    fun launchScan(treeUri: Uri, onResult: (Int?) -> Unit) {
        if (!_scanning.compareAndSet(expect = false, update = true)) return
        scope.launch {
            val count = runCatching { scan(treeUri) }
                .onFailure { AppLogger.w(TAG, "扫描目录失败 uri=$treeUri", it) }
                .getOrNull()
            onResult(count)
        }
    }

    private suspend fun scan(treeUri: Uri): Int? {
        return try {
            // 一并保留写权限，已下载列表里删除识别出的文件时需要
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }.onFailure { AppLogger.w(TAG, "持久化目录读取授权失败 uri=$treeUri", it) }
            val entries = withContext(Dispatchers.IO) { collect(treeUri) } ?: return null
            indexStore.replaceRoot(treeUri.toString(), entries)
            AppLogger.i(TAG, "扫描完成 uri=$treeUri count=${entries.size}")
            entries.size
        } finally {
            _scanning.value = false
        }
    }

    private suspend fun collect(treeUri: Uri): List<LocalAudioEntry>? {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
            .onFailure { AppLogger.w(TAG, "无效的目录 uri=$treeUri", it) }
            .getOrNull() ?: return null
        val result = ArrayList<LocalAudioEntry>()
        val pending = ArrayDeque<Pair<String, Int>>().apply { add(rootId to 0) }
        var rootReadable = false
        while (pending.isNotEmpty()) {
            val (parentId, depth) = pending.removeFirst()
            val children = queryChildren(treeUri, parentId)
            if (children == null) {
                if (parentId == rootId) return null
                continue
            }
            if (parentId == rootId) rootReadable = true
            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (depth < MAX_DEPTH) pending.add(child.documentId to depth + 1)
                    continue
                }
                if (!child.isAudio()) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.documentId)
                readEntry(uri, child)?.let(result::add)
            }
        }
        return if (rootReadable) result else null
    }

    private class Child(val documentId: String, val name: String, val mimeType: String, val size: Long) {
        val extension: String get() = name.substringAfterLast('.', "").lowercase()

        fun isAudio(): Boolean = mimeType.startsWith("audio/") || extension in AUDIO_EXTENSIONS
    }

    private fun queryChildren(treeUri: Uri, parentId: String): List<Child>? = runCatching {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    add(
                        Child(
                            documentId = id,
                            name = cursor.getString(1).orEmpty(),
                            mimeType = cursor.getString(2).orEmpty(),
                            size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                        )
                    )
                }
            }
        }
    }.onFailure { AppLogger.w(TAG, "列出目录失败 parent=$parentId", it) }.getOrNull()

    private fun readEntry(uri: Uri, child: Child): LocalAudioEntry? {
        val (tags, properties) = readTags(uri)
        val fromName = parseDownloadFileName(child.name)
        val title = tags["TITLE"]?.firstOrNull()?.takeIf { it.isNotBlank() } ?: fromName?.first ?: return null
        val artist = tags["ARTIST"]?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.joinToString("/")
            ?: fromName?.second ?: return null
        val durationMs = properties?.length?.toLong()?.takeIf { it > 0 } ?: 0L
        val bitrate = properties?.bitrate?.takeIf { it > 0 } ?: estimateBitrateKbps(child.size, durationMs)
        return LocalAudioEntry(
            uri = uri.toString(),
            title = title,
            artist = artist,
            durationMs = durationMs,
            sizeBytes = child.size,
            quality = guessAudioQuality(child.extension, bitrate, properties?.sampleRate ?: 0),
            songIdTag = parseSongIdTag(tags[SONG_ID_TAG]?.firstOrNull())
        )
    }

    // 读不到时返回空标签，交给文件名兜底
    private fun readTags(uri: Uri): Pair<Map<String, Array<String>>, AudioProperties?> = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val tags = withNativeFd(descriptor) { fd -> TagLib.getMetadata(fd, readPictures = false)?.propertyMap }
            val properties = withNativeFd(descriptor) { fd -> TagLib.getAudioProperties(fd) }
            tags.orEmpty() to properties
        } ?: (emptyMap<String, Array<String>>() to null)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        AppLogger.w(TAG, "读取音频标签失败 uri=$uri", error)
        emptyMap<String, Array<String>>() to null
    } catch (error: LinkageError) {
        AppLogger.w(TAG, "标签读取库不可用 uri=$uri", error)
        emptyMap<String, Array<String>>() to null
    }

    // TagLib 接管并关闭传入的 fd 副本；JNI 未加载时需自行回收
    private fun <T> withNativeFd(descriptor: ParcelFileDescriptor, block: (Int) -> T): T {
        val fd = descriptor.dup().detachFd()
        try {
            return block(fd)
        } catch (error: LinkageError) {
            runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
            throw error
        }
    }
}
