package com.lin0721.linmusic.feature.localmusic.data.batch

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.model.Album
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val Context.batchAlbumMatchDataStore by preferencesDataStore(name = "batch_album_matches")

/** Only settled outcomes are persisted. An interrupted search keeps the previous safe outcome. */
@Serializable
enum class AlbumBatchStatus { UNPROCESSED, REVIEW, MATCHED, NO_MATCH, ERROR }

@Serializable
data class AlbumBatchRecord(
    val localAlbumKey: String,
    val fingerprint: String,
    val status: AlbumBatchStatus = AlbumBatchStatus.UNPROCESSED,
    val candidates: List<Album> = emptyList(),
    val selected: Album? = null,
    val reason: String = "",
    val linkedSongs: Int = 0,
    val totalSongs: Int = 0,
    val updatedAtMs: Long = 0L,
)

/** Album reports never modify Room, song associations, or the separate full online-track cache. */
class BatchAlbumMatchStore(private val context: Context) {
    val recordsFlow: Flow<Map<String, AlbumBatchRecord>> = context.batchAlbumMatchDataStore.data
        .map { prefs ->
            AlbumBatchRecordCodec.decodeEntries(prefs.asMap().mapNotNull { (key, value) ->
                (value as? String)?.let { key.name to it }
            }.toMap())
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    suspend fun put(record: AlbumBatchRecord) {
        // Validate/encode before editing; an invalid replacement cannot erase the previous report.
        val encoded = AlbumBatchRecordCodec.encode(record)
        context.batchAlbumMatchDataStore.edit { prefs ->
            prefs[stringPreferencesKey(AlbumBatchRecordCodec.keyFor(record.localAlbumKey))] = encoded
        }
    }

    suspend fun remove(localAlbumKey: String) {
        context.batchAlbumMatchDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(AlbumBatchRecordCodec.keyFor(localAlbumKey)))
        }
    }
}

/** The caller supplies the complete local album metadata and tracks in a stable array order. */
object AlbumBatchFingerprint {
    fun of(metadata: JsonElement): String {
        val canonical = canonicalize(metadata).toString().toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(canonical)
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun canonicalize(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.toSortedMap().mapValues { (_, child) -> canonicalize(child) })
        is JsonArray -> JsonArray(value.map(::canonicalize))
        else -> value
    }
}

internal object AlbumBatchRecordCodec {
    private const val PREFIX = "album_report_"
    private val json = Json { ignoreUnknownKeys = true }

    fun keyFor(localAlbumKey: String): String = buildString(PREFIX.length + localAlbumKey.length * 4) {
        append(PREFIX)
        localAlbumKey.forEach { append(it.code.toString(16).padStart(4, '0')) }
    }

    fun encode(record: AlbumBatchRecord): String {
        require(isValid(record)) { "Invalid album report; previous result must be retained" }
        return json.encodeToString(record)
    }

    fun decodeEntries(entries: Map<String, String>): Map<String, AlbumBatchRecord> = buildMap {
        entries.forEach { (key, encoded) ->
            if (!key.startsWith(PREFIX)) return@forEach
            val record = runCatching { json.decodeFromString<AlbumBatchRecord>(encoded) }.getOrNull()
                ?: return@forEach
            if (isValid(record) && keyFor(record.localAlbumKey) == key) put(record.localAlbumKey, record)
        }
    }

    private fun isValid(record: AlbumBatchRecord): Boolean = record.localAlbumKey.isNotBlank() &&
        record.fingerprint.isNotBlank() && record.totalSongs >= 0 && record.linkedSongs in 0..record.totalSongs &&
        record.updatedAtMs >= 0L && record.candidates.all { it.id > 0L } &&
        (record.selected == null || record.selected.id > 0L)
}
