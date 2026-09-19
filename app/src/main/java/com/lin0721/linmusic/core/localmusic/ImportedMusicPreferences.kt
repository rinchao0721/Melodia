package com.lin0721.linmusic.core.localmusic

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.importedMusicDataStore by preferencesDataStore(name = "imported_music_prefs")

@Serializable
data class ImportedTrackRecord(
    val uriString: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedMs: Long
)

class ImportedMusicPreferences(private val context: Context) {

    companion object {
        private val KEY_RECORDS = stringPreferencesKey("imported_track_records")
        private val json = Json { ignoreUnknownKeys = true }
    }

    val records: Flow<List<ImportedTrackRecord>> = context.importedMusicDataStore.data.map { prefs ->
        decodeRecords(prefs[KEY_RECORDS])
    }

    suspend fun addRecords(newRecords: List<ImportedTrackRecord>): Int {
        if (newRecords.isEmpty()) return 0
        var addedCount = 0
        context.importedMusicDataStore.edit { prefs ->
            val existing = decodeRecords(prefs[KEY_RECORDS])
            val existingUris = existing.map { it.uriString }.toSet()
            val toAdd = newRecords.filter { it.uriString !in existingUris }
            addedCount = toAdd.size
            if (addedCount > 0) {
                prefs[KEY_RECORDS] = json.encodeToString(existing + toAdd)
            }
        }
        return addedCount
    }

    suspend fun removeRecords(uriStrings: Set<String>) {
        if (uriStrings.isEmpty()) return
        context.importedMusicDataStore.edit { prefs ->
            val current = decodeRecords(prefs[KEY_RECORDS])
            val updated = current.filterNot { it.uriString in uriStrings }
            prefs[KEY_RECORDS] = json.encodeToString(updated)
        }
    }

    private fun decodeRecords(raw: String?): List<ImportedTrackRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ImportedTrackRecord>>(raw) }.getOrDefault(emptyList())
    }
}
