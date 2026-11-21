package com.lin0721.linmusic.core.songquality

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.data.PlaybackApi
import com.lin0721.linmusic.core.player.data.SongDetailRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SongQualityCache"

// 合并同一屏行登记的等待时间
private const val BATCH_DELAY_MS = 300L

// 对齐服务端 song/detail 单次上限
private const val MAX_BATCH_SIZE = 1000

// 持久化缓存有效期（7 天）
internal const val SONG_QUALITY_TTL_MS = 7L * 24 * 60 * 60 * 1000

// 预查最长阻塞时间，超时后列表照常展示
internal const val PREFETCH_TIMEOUT_MS = 3_000L

private const val KEY_PREFIX = "q_"

// 超过 4 倍有效期仍未刷新的条目在启动时删除
private const val PRUNE_TTL_MULTIPLIER = 4

// 缺失接口自带 maxBrLevel 的线上曲目需要预查
fun Track.needsQualityPrefetch(): Boolean =
    id > 0 && privilege?.maxBrLevel.isNullOrBlank()

fun tracksNeedingQualityPrefetch(tracks: Collection<Track>): List<Long> =
    tracks.asSequence()
        .filter { it.needsQualityPrefetch() }
        .map { it.id }
        .distinct()
        .toList()

// 歌曲最高音质缓存：列表加载前批量预查，行渲染兜底补查，结果持久化到本地并带 7 天过期刷新；开关关闭时不发请求
class SongQualityCache(
    enabledFlow: Flow<Boolean>,
    private val fetcher: suspend (List<Long>) -> Map<Long, String>,
    private val scope: CoroutineScope,
    private val batchDelayMs: Long = BATCH_DELAY_MS,
    private val dataStore: DataStore<Preferences>? = null,
    private val ttlMs: Long = SONG_QUALITY_TTL_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val isOnline: () -> Boolean = { true },
    private val prefetchTimeoutMs: Long = PREFETCH_TIMEOUT_MS
) {
    private data class CacheEntry(val level: String, val updatedAt: Long)

    private val enabledReady = CompletableDeferred<Unit>()
    val enabled: StateFlow<Boolean> = enabledFlow
        .onEach { enabledReady.complete(Unit) }
        .onCompletion { enabledReady.complete(Unit) }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val entries = ConcurrentHashMap<Long, CacheEntry>()
    private val _levels = MutableStateFlow<Map<Long, String>>(emptyMap())
    val levels: StateFlow<Map<Long, String>> = _levels.asStateFlow()

    private val stateMutex = Mutex()
    private val queried = HashSet<Long>()
    private val inFlight = HashSet<Long>()
    private val requests = Channel<Long>(Channel.UNLIMITED)
    private val loadJob: Job

    init {
        loadJob = scope.launch { loadPersisted() }
        scope.launch { consume() }
    }

    private fun isFresh(songId: Long, now: Long = nowMs()): Boolean {
        val entry = entries[songId] ?: return false
        return now - entry.updatedAt < ttlMs
    }

    fun request(songId: Long) {
        if (songId <= 0 || !enabled.value || !isOnline() || isFresh(songId)) return
        requests.trySend(songId)
    }

    // 列表展示前批量预查缺失最高音质的曲目：未命中的同步等待补查，已过期缓存直接复用并后台刷新；失败或取消不抛业务异常
    suspend fun prefetch(tracks: Collection<Track>) {
        val known = tracks.mapNotNull { track ->
            track.privilege?.maxBrLevel?.takeIf { track.id > 0 && it.isNotBlank() }?.let { track.id to it }
        }.toMap()
        // 接口自带的等级也写入缓存，播放队列等不带 privilege 的地方直接命中
        if (known.isNotEmpty()) seed(known)
        val ids = tracksNeedingQualityPrefetch(tracks)
        if (ids.isNotEmpty()) prefetchIds(ids)
    }

    suspend fun seed(levels: Map<Long, String>) {
        loadJob.join()
        val now = nowMs()
        val updates = stateMutex.withLock {
            val changed = LinkedHashMap<Long, CacheEntry>()
            levels.forEach { (id, level) ->
                if (id <= 0 || level.isBlank()) return@forEach
                val current = entries[id]
                // 同等级且未过半有效期时跳过，避免每次进列表都整文件重写
                if (current != null && current.level == level && now - current.updatedAt < ttlMs / 2) return@forEach
                val entry = CacheEntry(level, now)
                entries[id] = entry
                changed[id] = entry
            }
            changed
        }
        if (updates.isEmpty()) return
        _levels.update { current -> current + updates.mapValues { it.value.level } }
        persistEntries(updates)
    }

    suspend fun prefetchIds(ids: Collection<Long>) {
        val candidates = ids.asSequence().filter { it > 0 }.distinct().toList()
        if (candidates.isEmpty()) return
        loadJob.join()
        enabledReady.await()
        if (!enabled.value || !isOnline()) return

        val now = nowMs()
        val missing = ArrayList<Long>()
        val stale = ArrayList<Long>()

        stateMutex.withLock {
            for (id in candidates) {
                if (isFresh(id, now)) continue
                if (entries.containsKey(id)) {
                    if (id !in queried && id !in inFlight) stale += id
                } else if (id !in queried && id !in inFlight) {
                    inFlight += id
                    queried += id
                    missing += id
                }
            }
        }

        // 过期缓存先沿用已有徽标不阻塞展示，再排队后台静默刷新
        stale.forEach { requests.trySend(it) }

        if (missing.isEmpty()) return

        val chunks = missing.chunked(MAX_BATCH_SIZE)
        var processedCount = 0
        try {
            withTimeoutOrNull(prefetchTimeoutMs) {
                for (chunk in chunks) {
                    if (!enabled.value || !isOnline()) break
                    processedCount += chunk.size
                    fetchAndStore(chunk)
                }
            }
        } finally {
            if (processedCount < missing.size) {
                val remaining = missing.drop(processedCount)
                withContext(NonCancellable) { releaseFailedOrCancelled(remaining) }
            }
        }
    }

    private suspend fun loadPersisted() {
        val store = dataStore ?: return
        val now = nowMs()
        val discarded = ArrayList<Preferences.Key<*>>()
        val loaded = try {
            store.data.first().asMap().mapNotNull { (key, value) ->
                val rawId = key.name.removePrefix(KEY_PREFIX)
                val id = if (rawId != key.name) rawId.toLongOrNull() else null
                val entry = (value as? String)?.let(::parseEntry)
                when {
                    id == null || id <= 0 -> null
                    entry == null || now - entry.updatedAt >= ttlMs * PRUNE_TTL_MULTIPLIER -> {
                        discarded += key
                        null
                    }
                    else -> id to entry
                }
            }.toMap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "读取最高音质缓存失败", e)
            emptyMap()
        }
        pruneKeys(store, discarded)
        if (loaded.isEmpty()) return
        loaded.forEach { (id, entry) -> entries.putIfAbsent(id, entry) }
        // 加载期间内存已写入的新等级优先
        _levels.update { current -> loaded.mapValues { it.value.level } + current }
    }

    private suspend fun consume() {
        loadJob.join()
        while (true) {
            val first = requests.receive()
            if (!canQueryInBackground(first)) continue
            delay(batchDelayMs)
            val batch = LinkedHashSet<Long>()
            if (canQueryInBackground(first)) batch += first
            while (true) {
                val next = requests.tryReceive().getOrNull() ?: break
                if (canQueryInBackground(next)) batch += next
            }
            if (batch.isEmpty()) continue
            for (chunk in batch.chunked(MAX_BATCH_SIZE)) {
                if (!enabled.value || !isOnline()) break
                val claimed = claimForFetch(chunk)
                if (claimed.isEmpty()) continue
                fetchAndStore(claimed)
            }
        }
    }

    private suspend fun canQueryInBackground(songId: Long): Boolean {
        if (songId <= 0 || isFresh(songId)) return false
        return stateMutex.withLock { songId !in queried && songId !in inFlight }
    }

    private suspend fun claimForFetch(ids: List<Long>): List<Long> {
        val now = nowMs()
        return stateMutex.withLock {
            ids.filter { id ->
                id > 0 && !isFresh(id, now) && id !in queried && id !in inFlight
            }.also { claimed ->
                inFlight += claimed
                queried += claimed
            }
        }
    }

    private suspend fun fetchAndStore(chunk: List<Long>) {
        val result = try {
            fetcher(chunk)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { releaseFailedOrCancelled(chunk) }
            throw e
        } catch (e: Exception) {
            releaseFailedOrCancelled(chunk)
            AppLogger.w(TAG, "补查最高音质失败：${chunk.size} 首", e)
            return
        }

        val now = nowMs()
        val valid = LinkedHashMap<Long, CacheEntry>(result.size)
        result.forEach { (id, level) ->
            if (id > 0 && level.isNotBlank()) valid[id] = CacheEntry(level, now)
        }
        stateMutex.withLock {
            inFlight -= chunk.toSet()
            valid.forEach { (id, entry) -> entries[id] = entry }
        }
        if (valid.isNotEmpty()) {
            _levels.update { current -> current + valid.mapValues { it.value.level } }
            persistEntries(valid)
        }
    }

    private suspend fun releaseFailedOrCancelled(ids: List<Long>) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        stateMutex.withLock {
            inFlight -= idSet
            queried -= idSet
        }
    }

    private suspend fun persistEntries(updates: Map<Long, CacheEntry>) {
        val store = dataStore ?: return
        if (updates.isEmpty()) return
        try {
            store.edit { prefs ->
                updates.forEach { (id, entry) ->
                    prefs[stringPreferencesKey("$KEY_PREFIX$id")] = "${entry.level}|${entry.updatedAt}"
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "保存最高音质缓存失败：${updates.size} 首", e)
        }
    }

    // 每首歌一个 key 且整文件重写，长期不访问的条目要清掉
    private suspend fun pruneKeys(store: DataStore<Preferences>, keys: List<Preferences.Key<*>>) {
        if (keys.isEmpty()) return
        try {
            store.edit { prefs -> keys.forEach { prefs.remove(it) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "清理过期最高音质缓存失败：${keys.size} 条", e)
        }
    }

    private fun parseEntry(raw: String): CacheEntry? {
        val sep = raw.lastIndexOf('|')
        if (sep <= 0 || sep >= raw.lastIndex) return null
        val level = raw.substring(0, sep)
        val updatedAt = raw.substring(sep + 1).toLongOrNull() ?: return null
        if (level.isBlank() || updatedAt <= 0L) return null
        return CacheEntry(level = level, updatedAt = updatedAt)
    }
}

// song/detail 取最高音质：优先顶层 privileges，缺失时回退 songs 内嵌的 privilege
suspend fun fetchSongMaxQuality(api: PlaybackApi, songIds: List<Long>): Map<Long, String> {
    val c = songIds.joinToString(prefix = "[", postfix = "]") { """{"id":$it}""" }
    val response = api.getSongDetail(SongDetailRequest(c = c))
    if (!response.isSuccess) error("song/detail code=${response.code}")
    val result = HashMap<Long, String>()
    response.songs.forEach { track ->
        val level = track.privilege?.maxBrLevel
        if (track.id > 0 && !level.isNullOrBlank()) result[track.id] = level
    }
    response.privileges.forEach { privilege ->
        val level = privilege.maxBrLevel
        if (privilege.id > 0 && !level.isNullOrBlank()) result[privilege.id] = level
    }
    return result
}
