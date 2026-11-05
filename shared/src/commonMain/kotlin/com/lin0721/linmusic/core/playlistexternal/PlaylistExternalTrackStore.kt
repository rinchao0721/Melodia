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

@Serializable
data class PlaylistExternalEntry(val track: ExternalTrack, val addedAt: Long)

// 混排进网易云歌单的外部平台歌曲，只存本机不同步云端；每个歌单内最近加入的在前
class PlaylistExternalTrackStore(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val KEY_ENTRIES = stringPreferencesKey("playlist_external_entries")
        private val json = Json { ignoreUnknownKeys = true }
    }

    val all: Flow<Map<Long, List<PlaylistExternalEntry>>> = dataStore.data.map { prefs ->
        decode(prefs[KEY_ENTRIES])
    }

    fun entriesOf(playlistId: Long): Flow<List<PlaylistExternalEntry>> =
        all.map { it[playlistId].orEmpty() }.distinctUntilChanged()

    suspend fun contains(playlistId: Long, track: ExternalTrack): Boolean =
        all.first()[playlistId].orEmpty().any { it.track.resolveKey == track.resolveKey }

    // 返回实际新增的数量，已在歌单里的按 resolveKey 去重跳过
    suspend fun add(playlistId: Long, tracks: List<ExternalTrack>, now: Long = System.currentTimeMillis()): Int {
        var added = 0
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_ENTRIES])
            val updated = prependEntries(current[playlistId].orEmpty(), tracks, now)
            added = updated.size - current[playlistId].orEmpty().size
            if (added > 0) prefs[KEY_ENTRIES] = json.encodeToString(current + (playlistId to updated))
        }
        return added
    }

    // 歌单里外部歌曲以 queueSongId 作为曲目 id 展示，删除也按它定位
    suspend fun remove(playlistId: Long, queueSongIds: Set<Long>) {
        if (queueSongIds.isEmpty()) return
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_ENTRIES])
            val entries = current[playlistId] ?: return@edit
            val remaining = entries.filterNot { it.track.queueSongId in queueSongIds }
            if (remaining.size == entries.size) return@edit
            val updated = if (remaining.isEmpty()) current - playlistId else current + (playlistId to remaining)
            prefs[KEY_ENTRIES] = json.encodeToString(updated)
        }
    }

    suspend fun clearPlaylist(playlistId: Long) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_ENTRIES])
            if (playlistId in current) prefs[KEY_ENTRIES] = json.encodeToString(current - playlistId)
        }
    }

    private fun decode(raw: String?): Map<Long, List<PlaylistExternalEntry>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<Long, List<PlaylistExternalEntry>>>(raw)
        } catch (e: Exception) {
            AppLogger.w(TAG, "歌单外部歌曲反序列化失败", e)
            emptyMap()
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
