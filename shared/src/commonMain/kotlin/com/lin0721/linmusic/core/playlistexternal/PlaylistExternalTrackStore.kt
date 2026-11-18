package com.lin0721.linmusic.core.playlistexternal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.source.ExternalTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "PlaylistExternalTrackStore"
internal const val PLAYLIST_EXTERNAL_TOMBSTONE_TTL_MS = 30L * 24 * 60 * 60 * 1000

@Serializable
data class PlaylistExternalEntry(val track: ExternalTrack, val addedAt: Long)

@Serializable
data class PlaylistExternalTombstone(
    val playlistId: Long,
    val resolveKey: String,
    val removedAt: Long
)

@Serializable
data class PlaylistExternalSnapshot(
    val entries: Map<Long, List<PlaylistExternalEntry>> = emptyMap(),
    val tombstones: List<PlaylistExternalTombstone> = emptyList()
)

// 混排进网易云歌单的外部平台歌曲，只存本机不同步云端；每个歌单内最近加入的在前
class PlaylistExternalTrackStore(
    private val dataStore: DataStore<Preferences>,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        private val KEY_ENTRIES = stringPreferencesKey("playlist_external_entries")
        private val KEY_TOMBSTONES = stringPreferencesKey("playlist_external_tombstones")
        private val json = Json { ignoreUnknownKeys = true }
    }

    val all: Flow<Map<Long, List<PlaylistExternalEntry>>> = dataStore.data.map { prefs ->
        decodeEntries(prefs[KEY_ENTRIES])
    }

    val snapshotFlow: Flow<PlaylistExternalSnapshot> = dataStore.data.map { prefs ->
        val entries = decodeEntries(prefs[KEY_ENTRIES])
        val tombstones = pruneExpiredTombstones(decodeTombstones(prefs[KEY_TOMBSTONES]), nowMs())
        PlaylistExternalSnapshot(entries = entries, tombstones = tombstones)
    }.distinctUntilChanged()

    suspend fun snapshot(): PlaylistExternalSnapshot = snapshotFlow.first()

    fun entriesOf(playlistId: Long): Flow<List<PlaylistExternalEntry>> =
        all.map { it[playlistId].orEmpty() }.distinctUntilChanged()

    suspend fun contains(playlistId: Long, track: ExternalTrack): Boolean =
        all.first()[playlistId].orEmpty().any { it.track.resolveKey == track.resolveKey }

    // 返回实际新增的数量，已在歌单里的按 resolveKey 去重跳过，并清除同名墓碑
    suspend fun add(playlistId: Long, tracks: List<ExternalTrack>, now: Long = nowMs()): Int {
        var added = 0
        dataStore.edit { prefs ->
            val current = decodeEntries(prefs[KEY_ENTRIES])
            val currentList = current[playlistId].orEmpty()
            val updated = prependEntries(currentList, tracks, now)
            added = updated.size - currentList.size
            if (added > 0) {
                prefs[KEY_ENTRIES] = json.encodeToString(current + (playlistId to updated))
                val addedKeys = tracks.mapTo(HashSet()) { it.resolveKey }
                val currentTombstones = decodeTombstones(prefs[KEY_TOMBSTONES])
                val updatedTombstones = pruneExpiredTombstones(
                    currentTombstones.filterNot { it.playlistId == playlistId && it.resolveKey in addedKeys },
                    now
                )
                writeTombstonesIfNeeded(prefs, currentTombstones, updatedTombstones)
            }
        }
        return added
    }

    // 歌单里外部歌曲以 queueSongId 作为曲目 id 展示，删除也按它定位，并记录墓碑
    suspend fun remove(playlistId: Long, queueSongIds: Set<Long>) {
        if (queueSongIds.isEmpty()) return
        val now = nowMs()
        dataStore.edit { prefs ->
            val current = decodeEntries(prefs[KEY_ENTRIES])
            val entries = current[playlistId] ?: return@edit
            val removedEntries = entries.filter { it.track.queueSongId in queueSongIds }
            if (removedEntries.isEmpty()) return@edit
            val remaining = entries.filterNot { it.track.queueSongId in queueSongIds }
            val updated = if (remaining.isEmpty()) current - playlistId else current + (playlistId to remaining)
            prefs[KEY_ENTRIES] = json.encodeToString(updated)

            val currentTombstones = decodeTombstones(prefs[KEY_TOMBSTONES])
            val newTombstones = removedEntries.map {
                PlaylistExternalTombstone(playlistId = playlistId, resolveKey = it.track.resolveKey, removedAt = now)
            }
            val updatedTombstones = mergeTombstones(currentTombstones, newTombstones, now)
            prefs[KEY_TOMBSTONES] = json.encodeToString(updatedTombstones)
        }
    }

    suspend fun clearPlaylist(playlistId: Long) {
        val now = nowMs()
        dataStore.edit { prefs ->
            val current = decodeEntries(prefs[KEY_ENTRIES])
            val entries = current[playlistId] ?: return@edit
            prefs[KEY_ENTRIES] = json.encodeToString(current - playlistId)

            val currentTombstones = decodeTombstones(prefs[KEY_TOMBSTONES])
            val newTombstones = entries.map {
                PlaylistExternalTombstone(playlistId = playlistId, resolveKey = it.track.resolveKey, removedAt = now)
            }
            val updatedTombstones = mergeTombstones(currentTombstones, newTombstones, now)
            prefs[KEY_TOMBSTONES] = json.encodeToString(updatedTombstones)
        }
    }

    // 与远端快照做 LWW 合并；无有效变化时不写盘并返回 false，避免触发回推死循环
    suspend fun applyMergedRemoteSnapshot(
        remote: PlaylistExternalSnapshot,
        now: Long = nowMs()
    ): Boolean {
        var changed = false
        dataStore.edit { prefs ->
            val localRawEntries = decodeEntries(prefs[KEY_ENTRIES])
            val localRawTombstones = decodeTombstones(prefs[KEY_TOMBSTONES])
            val localNormalized = PlaylistExternalSnapshot(
                entries = localRawEntries,
                tombstones = pruneExpiredTombstones(localRawTombstones, now)
            )
            val merged = mergePlaylistExternalSnapshots(localNormalized, remote, now)
            if (hasSameSemanticState(localNormalized, merged)) {
                changed = false
                return@edit
            }
            prefs[KEY_ENTRIES] = json.encodeToString(merged.entries)
            if (merged.tombstones.isEmpty()) {
                prefs.remove(KEY_TOMBSTONES)
            } else {
                prefs[KEY_TOMBSTONES] = json.encodeToString(merged.tombstones)
            }
            changed = true
        }
        return changed
    }

    private fun writeTombstonesIfNeeded(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        before: List<PlaylistExternalTombstone>,
        after: List<PlaylistExternalTombstone>
    ) {
        if (before == after) return
        if (after.isEmpty()) {
            prefs.remove(KEY_TOMBSTONES)
        } else {
            prefs[KEY_TOMBSTONES] = json.encodeToString(after)
        }
    }

    private fun decodeEntries(raw: String?): Map<Long, List<PlaylistExternalEntry>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<Long, List<PlaylistExternalEntry>>>(raw)
        } catch (e: Exception) {
            AppLogger.w(TAG, "歌单外部歌曲反序列化失败", e)
            emptyMap()
        }
    }

    private fun decodeTombstones(raw: String?): List<PlaylistExternalTombstone> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<PlaylistExternalTombstone>>(raw)
        } catch (e: Exception) {
            AppLogger.w(TAG, "歌单外部歌曲墓碑反序列化失败", e)
            emptyList()
        }
    }
}

internal fun prependEntries(
    current: List<PlaylistExternalEntry>,
    tracks: List<ExternalTrack>,
    now: Long
): List<PlaylistExternalEntry> {
    val existing = current.mapTo(HashSet()) { it.track.resolveKey }
    val fresh = tracks.distinctBy { it.resolveKey }
        .filter { existing.add(it.resolveKey) }
        .map { PlaylistExternalEntry(it, now) }
    return fresh + current
}

// 按 (playlistId, resolveKey) 对双端条目与有效墓碑做 LWW 合并
fun mergePlaylistExternalSnapshots(
    local: PlaylistExternalSnapshot,
    remote: PlaylistExternalSnapshot,
    now: Long
): PlaylistExternalSnapshot {
    val tombstoneByKey = HashMap<Pair<Long, String>, PlaylistExternalTombstone>()
    for (tombstone in local.tombstones + remote.tombstones) {
        if (now - tombstone.removedAt > PLAYLIST_EXTERNAL_TOMBSTONE_TTL_MS) continue
        val key = tombstone.playlistId to tombstone.resolveKey
        val existing = tombstoneByKey[key]
        if (existing == null || tombstone.removedAt > existing.removedAt) {
            tombstoneByKey[key] = tombstone
        }
    }

    val entryByKey = HashMap<Pair<Long, String>, PlaylistExternalEntry>()
    for ((playlistId, entries) in local.entries) {
        for (entry in entries) {
            val key = playlistId to entry.track.resolveKey
            val existing = entryByKey[key]
            if (existing == null || entry.addedAt > existing.addedAt) {
                entryByKey[key] = entry
            }
        }
    }
    for ((playlistId, entries) in remote.entries) {
        for (entry in entries) {
            val key = playlistId to entry.track.resolveKey
            val existing = entryByKey[key]
            if (existing == null || entry.addedAt > existing.addedAt) {
                entryByKey[key] = entry
            }
        }
    }

    val mergedEntries = LinkedHashMap<Long, MutableList<PlaylistExternalEntry>>()
    val activeTombstones = ArrayList<PlaylistExternalTombstone>()

    for ((key, entry) in entryByKey) {
        val tombstone = tombstoneByKey[key]
        // 同一时刻或更晚删除胜出，否则新增/重新加入胜出并清掉旧墓碑
        if (tombstone != null && tombstone.removedAt >= entry.addedAt) {
            activeTombstones.add(tombstone)
        } else {
            mergedEntries.getOrPut(key.first) { ArrayList() }.add(entry)
        }
    }
    for ((key, tombstone) in tombstoneByKey) {
        if (key !in entryByKey) {
            activeTombstones.add(tombstone)
        }
    }

    val sortedEntries = mergedEntries.entries
        .sortedBy { it.key }
        .associate { (playlistId, list) ->
            playlistId to list.sortedWith(
                compareByDescending<PlaylistExternalEntry> { it.addedAt }
                    .thenBy { it.track.resolveKey }
            )
        }
    val sortedTombstones = activeTombstones.sortedWith(
        compareBy<PlaylistExternalTombstone> { it.playlistId }.thenBy { it.resolveKey }
    )
    return PlaylistExternalSnapshot(entries = sortedEntries, tombstones = sortedTombstones)
}

private fun pruneExpiredTombstones(
    tombstones: List<PlaylistExternalTombstone>,
    now: Long
): List<PlaylistExternalTombstone> {
    if (tombstones.isEmpty()) return emptyList()
    val deduped = HashMap<Pair<Long, String>, PlaylistExternalTombstone>()
    for (tombstone in tombstones) {
        if (now - tombstone.removedAt > PLAYLIST_EXTERNAL_TOMBSTONE_TTL_MS) continue
        val key = tombstone.playlistId to tombstone.resolveKey
        val existing = deduped[key]
        if (existing == null || tombstone.removedAt > existing.removedAt) {
            deduped[key] = tombstone
        }
    }
    return deduped.values.sortedWith(
        compareBy<PlaylistExternalTombstone> { it.playlistId }.thenBy { it.resolveKey }
    )
}

private fun mergeTombstones(
    existing: List<PlaylistExternalTombstone>,
    added: List<PlaylistExternalTombstone>,
    now: Long
): List<PlaylistExternalTombstone> = pruneExpiredTombstones(existing + added, now)

private fun hasSameSemanticState(
    a: PlaylistExternalSnapshot,
    b: PlaylistExternalSnapshot
): Boolean {
    val aEntries = a.entries.filterValues { it.isNotEmpty() }
    val bEntries = b.entries.filterValues { it.isNotEmpty() }
    if (aEntries.keys != bEntries.keys) return false
    for (playlistId in aEntries.keys) {
        val left = aEntries[playlistId].orEmpty().associateBy { it.track.resolveKey }
        val right = bEntries[playlistId].orEmpty().associateBy { it.track.resolveKey }
        if (left != right) return false
    }
    return a.tombstones.toSet() == b.tombstones.toSet()
}

