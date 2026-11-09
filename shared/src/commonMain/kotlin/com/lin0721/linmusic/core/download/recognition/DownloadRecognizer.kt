package com.lin0721.linmusic.core.download.recognition

import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "DownloadRecognizer"

// 把本地已有文件补登记为下载记录；平台只提供记录读写与文件存活校验
class DownloadRecognizer(
    private val indexStore: LocalAudioIndexStore,
    private val records: suspend () -> List<DownloadRecord>,
    private val addRecords: suspend (List<DownloadRecord>) -> Unit,
    private val isAlive: suspend (uri: String) -> Boolean,
    private val extraEntries: suspend () -> List<LocalAudioEntry> = { emptyList() },
    private val now: () -> Long = System::currentTimeMillis
) {
    // 串行执行，避免并发识别把同一文件登记给两首歌
    private val mutex = Mutex()

    // 返回本次新增的记录；已有下载记录的歌曲不处理
    suspend fun recognize(targets: List<RecognitionTarget>): List<DownloadRecord> {
        if (targets.isEmpty()) return emptyList()
        return mutex.withLock {
            runCatching { recognizeLocked(targets) }
                .onFailure { AppLogger.w(TAG, "识别本地已下载文件失败", it) }
                .getOrDefault(emptyList())
        }
    }

    private suspend fun recognizeLocked(targets: List<RecognitionTarget>): List<DownloadRecord> {
        val existing = records()
        val recordedIds = existing.mapTo(HashSet()) { it.songId }
        val pending = targets.filter { it.songId > 0 && it.songId !in recordedIds }
        if (pending.isEmpty()) return emptyList()
        val entries = indexStore.entries() + extraEntries()
        if (entries.isEmpty()) return emptyList()

        val matches = DownloadedFileMatcher.match(pending, entries, existing.mapTo(HashSet()) { it.mediaStoreUri })
        if (matches.isEmpty()) return emptyList()

        val targetById = pending.associateBy { it.songId }
        val stale = HashSet<String>()
        val added = ArrayList<DownloadRecord>()
        for ((songId, entry) in matches) {
            if (!isAlive(entry.uri)) {
                stale += entry.uri
                continue
            }
            val target = targetById.getValue(songId)
            val record = DownloadRecord(
                songId = songId,
                mediaStoreUri = entry.uri,
                quality = entry.quality,
                downloadedAt = now(),
                fileSize = entry.sizeBytes,
                songName = target.title,
                artistName = target.artists.joinToString("/"),
                recognized = true
            )
            added += record
        }
        if (added.isNotEmpty()) addRecords(added)
        indexStore.remove(stale)
        if (added.isNotEmpty()) AppLogger.i(TAG, "补登记 ${added.size} 首本地已下载歌曲")
        return added
    }
}
