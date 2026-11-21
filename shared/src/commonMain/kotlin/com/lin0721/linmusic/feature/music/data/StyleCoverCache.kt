package com.lin0721.linmusic.feature.music.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "StyleCoverCache"

// 持久化缓存有效期（7 天）
internal const val STYLE_COVER_TTL_MS = 7L * 24 * 60 * 60 * 1000

private const val KEY_PREFIX = "c_"

// 曲风封面地址缓存：将曲风 id -> 封面 url 持久化到 DataStore，7 天过期后按需重新请求
class StyleCoverCache(
    private val dataStore: DataStore<Preferences>? = null,
    private val ttlMs: Long = STYLE_COVER_TTL_MS,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private data class CacheEntry(val url: String, val updatedAt: Long)

    private val loadMutex = Mutex()
    private val writeMutex = Mutex()
    private val entries = ConcurrentHashMap<Long, CacheEntry>()

    @Volatile
    private var loaded = false

    private fun isFresh(entry: CacheEntry, now: Long = nowMs()): Boolean =
        now - entry.updatedAt < ttlMs

    // 首次访问时从 DataStore 加载未过期条目，同时清理损坏或已过期条目
    suspend fun ensureLoaded(): Map<Long, String> {
        if (!loaded) {
            loadMutex.withLock {
                if (!loaded) {
                    loadPersisted()
                    loaded = true
                }
            }
        }
        return freshCovers()
    }

    fun getFresh(styleId: Long): String? {
        if (styleId <= 0L) return null
        val entry = entries[styleId] ?: return null
        return entry.url.takeIf { isFresh(entry) }
    }

    fun freshCovers(): Map<Long, String> {
        val now = nowMs()
        val result = LinkedHashMap<Long, String>()
        entries.forEach { (id, entry) ->
            if (isFresh(entry, now)) result[id] = entry.url
        }
        return result
    }

    suspend fun put(styleId: Long, url: String) {
        if (styleId <= 0L || url.isBlank()) return
        putAll(mapOf(styleId to url))
    }

    suspend fun putAll(covers: Map<Long, String>) {
        if (covers.isEmpty()) return
        val now = nowMs()
        val changed = writeMutex.withLock {
            val updates = LinkedHashMap<Long, CacheEntry>()
            covers.forEach { (id, url) ->
                if (id <= 0L || url.isBlank()) return@forEach
                val current = entries[id]
                // 地址一致且距离过期还远时跳过写盘，减少不必要的整文件重写
                if (current != null && current.url == url && now - current.updatedAt < ttlMs / 2) {
                    return@forEach
                }
                val entry = CacheEntry(url = url, updatedAt = now)
                entries[id] = entry
                updates[id] = entry
            }
            updates
        }
        if (changed.isEmpty()) return
        persistEntries(changed)
    }

    private suspend fun loadPersisted() {
        val store = dataStore ?: return
        val now = nowMs()
        val discarded = ArrayList<Preferences.Key<*>>()
        val restored = try {
            store.data.first().asMap().mapNotNull { (key, value) ->
                val rawId = key.name.removePrefix(KEY_PREFIX)
                val id = if (rawId != key.name) rawId.toLongOrNull() else null
                val entry = (value as? String)?.let(::parseEntry)
                when {
                    id == null || id <= 0L -> null
                    entry == null || !isFresh(entry, now) -> {
                        discarded += key
                        null
                    }
                    else -> id to entry
                }
            }.toMap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "读取曲风封面缓存失败", e)
            emptyMap()
        }
        pruneKeys(store, discarded)
        // 加载期间内存已写入的新条目优先
        restored.forEach { (id, entry) -> entries.putIfAbsent(id, entry) }
    }

    private suspend fun persistEntries(updates: Map<Long, CacheEntry>) {
        val store = dataStore ?: return
        try {
            store.edit { prefs ->
                updates.forEach { (id, entry) ->
                    prefs[stringPreferencesKey("$KEY_PREFIX$id")] = "${entry.url}|${entry.updatedAt}"
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "保存曲风封面缓存失败：${updates.size} 条", e)
        }
    }

    private suspend fun pruneKeys(store: DataStore<Preferences>, keys: List<Preferences.Key<*>>) {
        if (keys.isEmpty()) return
        try {
            store.edit { prefs -> keys.forEach { prefs.remove(it) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "清理过期曲风封面缓存失败：${keys.size} 条", e)
        }
    }

    private fun parseEntry(raw: String): CacheEntry? {
        val sep = raw.lastIndexOf('|')
        if (sep <= 0 || sep >= raw.lastIndex) return null
        val url = raw.substring(0, sep)
        val updatedAt = raw.substring(sep + 1).toLongOrNull() ?: return null
        if (url.isBlank() || updatedAt <= 0L) return null
        return CacheEntry(url = url, updatedAt = updatedAt)
    }
}
