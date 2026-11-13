package com.lin0721.linmusic.desktop.data.download

import com.lin0721.linmusic.core.download.recognition.LocalAudioEntry
import com.lin0721.linmusic.core.download.recognition.LocalAudioIndexStore
import com.lin0721.linmusic.core.download.recognition.estimateBitrateKbps
import com.lin0721.linmusic.core.download.recognition.guessAudioQuality
import com.lin0721.linmusic.core.download.recognition.parseDownloadFileName
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.data.local.isAudioFile
import com.lin0721.linmusic.desktop.data.local.normalizePath
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

private const val TAG = "DownloadFolderScanner"
private const val MAX_WALK_DEPTH = 8

// 扫描目录内的音频文件，建立识别已下载歌曲用的索引
class DesktopDownloadFolderScanner(private val indexStore: LocalAudioIndexStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    // 后台扫描，完成后回调扫描到的音频数量，目录不可读时为 null；已在扫描时忽略
    fun launchScan(folder: File, onResult: (Int?) -> Unit) {
        if (!_scanning.compareAndSet(expect = false, update = true)) return
        scope.launch {
            val count = try {
                runCatching { scan(folder) }
                    .onFailure { AppLogger.w(TAG, "扫描目录失败 ${folder.path}", it) }
                    .getOrNull()
            } finally {
                _scanning.value = false
            }
            onResult(count)
        }
    }

    private suspend fun scan(folder: File): Int? {
        if (!folder.isDirectory || !folder.canRead()) return null
        val root = normalizePath(folder.path)
        val entries = ArrayList<LocalAudioEntry>()
        val files = folder.walkTopDown()
            .maxDepth(MAX_WALK_DEPTH)
            .onFail { dir, e -> AppLogger.w(TAG, "无法读取文件夹 ${dir.path}", e) }
            .filter { it.isFile && isAudioFile(it) }
        for (file in files) {
            currentCoroutineContext().ensureActive()
            readEntry(file)?.let(entries::add)
        }
        indexStore.replaceRoot(root, entries)
        AppLogger.i(TAG, "扫描完成 $root count=${entries.size}")
        return entries.size
    }

    private fun readEntry(file: File): LocalAudioEntry? {
        val audio = runCatching { AudioFileIO.read(file) }
            .onFailure { AppLogger.w(TAG, "读取音频标签失败 ${file.path}", it) }
            .getOrNull()
        val tag = audio?.tag
        val header = audio?.audioHeader
        val fromName = parseDownloadFileName(file.name)
        val title = runCatching { tag?.getFirst(FieldKey.TITLE) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: fromName?.first ?: return null
        val artist = runCatching { tag?.getAll(FieldKey.ARTIST) }.getOrNull()
            ?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.joinToString("/")
            ?: fromName?.second ?: return null
        val durationMs = header?.let { (it.preciseTrackLength * 1000).toLong() }?.takeIf { it > 0 } ?: 0L
        val size = file.length()
        val bitrate = header?.bitRateAsNumber?.toInt()?.takeIf { it > 0 } ?: estimateBitrateKbps(size, durationMs)
        return LocalAudioEntry(
            uri = normalizePath(file.path),
            title = title,
            artist = artist,
            durationMs = durationMs,
            sizeBytes = size,
            quality = guessAudioQuality(file.extension, bitrate, header?.sampleRateAsNumber ?: 0, header?.bitsPerSample ?: 0),
            songIdTag = runCatching { tag?.readSongId() }.getOrNull()
        )
    }
}
