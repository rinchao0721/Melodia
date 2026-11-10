package com.lin0721.linmusic.core.download.recognition

import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.log.AppLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "DownloadRecognizer"
// 整单识别分批持锁，播放路径的单曲查询最多等一批
private const val BATCH_SIZE = 50
private const val SINGLE_TIMEOUT_MS = 1_500L

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

    // 归一化后的索引，仅在锁内读写；索引写入后版本变化即重建
    private var prepared: PreparedEntries = PreparedEntries.EMPTY
    private var preparedVersion = -1L

    // 返回本次新增的记录；已有下载记录的歌曲不处理
    suspend fun recognize(targets: List<RecognitionTarget>): List<DownloadRecord> {
        if (targets.isEmpty()) return emptyList()
        val added = ArrayList<DownloadRecord>()
        for (batch in targets.chunked(BATCH_SIZE)) {
            added += mutex.withLock {
                try {
                    // 播放入口在主线程调用，比对放到后台
                    withContext(Dispatchers.Default) { recognizeLocked(batch) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "识别本地已下载文件失败", e)
                    emptyList()
                }
            }
        }
        if (added.isNotEmpty()) AppLogger.i(TAG, "补登记 ${added.size} 首本地已下载歌曲")
        return added
    }

    // 播放路径用：超时视为未命中，不让整单识别拖慢起播
    suspend fun recognizeSingle(target: RecognitionTarget, timeoutMs: Long = SINGLE_TIMEOUT_MS): DownloadRecord? =
        withTimeoutOrNull(timeoutMs) { recognize(listOf(target)).firstOrNull() }

    private suspend fun recognizeLocked(targets: List<RecognitionTarget>): List<DownloadRecord> {
        val existing = records()
        val recordedIds = existing.mapTo(HashSet()) { it.songId }
        val pending = targets.filter { it.songId > 0 && it.songId !in recordedIds }
        if (pending.isEmpty()) return emptyList()

        val sources = listOf(preparedIndex(), PreparedEntries(extraEntries()))
        val matches = DownloadedFileMatcher.matchPrepared(pending, sources, existing.mapTo(HashSet()) { it.mediaStoreUri })
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
            added += DownloadRecord(
                songId = songId,
                mediaStoreUri = entry.uri,
                quality = entry.quality,
                downloadedAt = now(),
                fileSize = entry.sizeBytes,
                songName = target.title,
                artistName = target.artists.joinToString("/"),
                recognized = true
            )
        }
        if (added.isNotEmpty()) addRecords(added)
        indexStore.remove(stale)
        return added
    }

    private suspend fun preparedIndex(): PreparedEntries {
        val snapshot = indexStore.snapshot()
        if (snapshot.version != preparedVersion) {
            prepared = PreparedEntries(snapshot.entries)
            preparedVersion = snapshot.version
        }
        return prepared
    }
}
