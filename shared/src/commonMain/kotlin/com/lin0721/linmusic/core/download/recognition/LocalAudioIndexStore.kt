package com.lin0721.linmusic.core.download.recognition

import com.lin0721.linmusic.core.log.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "LocalAudioIndexStore"

@Serializable
private data class IndexFile(val entries: List<LocalAudioEntry> = emptyList())

// 索引内容与其版本号，版本随每次写入递增，供调用方判断缓存是否失效
class IndexSnapshot(val version: Long, val entries: List<LocalAudioEntry>)

// 已扫描本地音频的索引，以 JSON 文件持久化
class LocalAudioIndexStore(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cache: List<LocalAudioEntry>? = null
    private var version = 0L

    suspend fun entries(): List<LocalAudioEntry> = mutex.withLock { loadLocked() }

    suspend fun snapshot(): IndexSnapshot = mutex.withLock { IndexSnapshot(version, loadLocked()) }

    // 用新扫描结果整体替换该目录下的条目
    suspend fun replaceRoot(root: String, scanned: List<LocalAudioEntry>) = mutex.withLock {
        val updated = loadLocked().filterNot { it.root == root } + scanned.map { it.copy(root = root) }
        saveLocked(updated)
    }

    // 移除已失效的文件
    suspend fun remove(uris: Set<String>) = mutex.withLock {
        if (uris.isEmpty()) return@withLock
        val current = loadLocked()
        val updated = current.filterNot { it.uri in uris }
        if (updated.size != current.size) saveLocked(updated)
    }

    private suspend fun loadLocked(): List<LocalAudioEntry> {
        cache?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                if (file.isFile) json.decodeFromString<IndexFile>(file.readText()).entries else emptyList()
            }.onFailure { AppLogger.w(TAG, "读取本地音频索引失败", it) }.getOrDefault(emptyList())
        }
        cache = loaded
        return loaded
    }

    private suspend fun saveLocked(entries: List<LocalAudioEntry>) {
        cache = entries
        version++
        withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeText(json.encodeToString(IndexFile(entries)))
                if (!temp.renameTo(file)) {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                }
            }.onFailure { AppLogger.w(TAG, "写入本地音频索引失败", it) }
        }
    }
}
