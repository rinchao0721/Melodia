package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val TAG = "LoudnessStore"
private const val MAX_ENTRIES = 2000

enum class LoudnessSource {
    NETEASE,
    TAG,
    MEASURED_FULL,
    MEASURED_SAMPLED
}

data class LoudnessEntry(
    val lufs: Double,
    val peak: Double? = null,
    val source: LoudnessSource
)

@Serializable
private data class StoredLoudnessEntry(
    val id: Long,
    val lufs: Double,
    val peak: Double? = null,
    val source: LoudnessSource
)

@Serializable
private data class LoudnessFile(val entries: List<StoredLoudnessEntry> = emptyList())

// 曲目响度与峰值的持久缓存，按最近使用淘汰；file 为空时只存内存
class LoudnessStore(private val file: File?) {

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val entries = object : LinkedHashMap<Long, LoudnessEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, LoudnessEntry>?): Boolean = size > MAX_ENTRIES
    }
    private var loaded = false

    suspend fun get(songId: Long): LoudnessEntry? = lock.withLock {
        ensureLoaded()
        entries[songId]
    }

    suspend fun put(songId: Long, entry: LoudnessEntry) {
        lock.withLock {
            ensureLoaded()
            entries[songId] = entry
            persist()
        }
    }

    suspend fun put(songId: Long, lufs: Double, peak: Double?, source: LoudnessSource) {
        put(songId, LoudnessEntry(lufs = lufs, peak = peak, source = source))
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val source = file ?: return
        val stored = withContext(Dispatchers.IO) {
            try {
                if (source.isFile) json.decodeFromString(LoudnessFile.serializer(), source.readText()) else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "读取响度缓存失败，按空缓存处理", e)
                null
            }
        }
        stored?.entries?.forEach {
            entries[it.id] = LoudnessEntry(lufs = it.lufs, peak = it.peak, source = it.source)
        }
    }

    private suspend fun persist() {
        val target = file ?: return
        val snapshot = LoudnessFile(
            entries.map { (id, entry) ->
                StoredLoudnessEntry(id = id, lufs = entry.lufs, peak = entry.peak, source = entry.source)
            }
        )
        withContext(Dispatchers.IO) {
            try {
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, "${target.name}.tmp")
                temp.writeText(json.encodeToString(LoudnessFile.serializer(), snapshot))
                try {
                    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (e: AtomicMoveNotSupportedException) {
                    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "写入响度缓存失败", e)
            }
        }
    }
}
