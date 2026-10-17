package com.lin0721.linmusic.feature.localmusic.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

private val Context.localAlbumAssociationDataStore by preferencesDataStore(name = "local_album_associations")

/** An album scope and online track cache; neither a cached scope nor a preview implies human review. */
@Serializable
data class LocalAlbumAssociation(
    val localAlbumKey: String,
    val album: Album,
    val tracks: List<Track>,
    // Optional for backward compatibility. Old automatic/manual scopes cannot reliably be distinguished.
    val reviewedLocalFingerprint: String? = null,
)

class LocalAlbumAssociationStore(private val context: Context) {
    val associations: Flow<Map<String, LocalAlbumAssociation>> = context.localAlbumAssociationDataStore.data
        .map { prefs ->
            LocalAlbumAssociationCodec.decodeEntries(prefs.asMap().mapNotNull { (key, value) ->
                (value as? String)?.let { key.name to it }
            }.toMap())
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    suspend fun put(binding: LocalAlbumAssociation) {
        val encoded = LocalAlbumAssociationCodec.encode(binding)
        context.localAlbumAssociationDataStore.edit { prefs ->
            prefs[stringPreferencesKey(LocalAlbumAssociationCodec.keyFor(binding.localAlbumKey))] = encoded
        }
    }

    suspend fun remove(localAlbumKey: String) {
        context.localAlbumAssociationDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(LocalAlbumAssociationCodec.keyFor(localAlbumKey)))
        }
    }

    /** Commit review only if the exact scope being reviewed is still the stored scope. */
    suspend fun markReviewed(expected: LocalAlbumAssociation, fingerprint: String): Boolean {
        val key = stringPreferencesKey(LocalAlbumAssociationCodec.keyFor(expected.localAlbumKey))
        var saved = false
        context.localAlbumAssociationDataStore.edit { prefs ->
            val current = prefs[key]?.let { encoded ->
                LocalAlbumAssociationCodec.decodeEntries(mapOf(key.name to encoded))[expected.localAlbumKey]
            }
            if (current != null && current == expected) {
                prefs[key] = LocalAlbumAssociationCodec.encode(current.copy(reviewedLocalFingerprint = fingerprint))
                saved = true
            }
        }
        return saved
    }
}

/** Pure codec: one malformed or mismatched entry cannot hide otherwise valid album bindings. */
internal object LocalAlbumAssociationCodec {
    private const val PREFIX = "album_"
    private val json = Json { ignoreUnknownKeys = true }

    // Encode UTF-16 code units so every Kotlin String, including unusual folder characters, stays distinct.
    fun keyFor(localAlbumKey: String): String = buildString(PREFIX.length + localAlbumKey.length * 4) {
        append(PREFIX)
        localAlbumKey.forEach { append(it.code.toString(16).padStart(4, '0')) }
    }

    fun encode(binding: LocalAlbumAssociation): String {
        require(isValid(binding)) { "A valid album and a complete, unique track cache are required" }
        return json.encodeToString(binding)
    }

    fun decodeEntries(entries: Map<String, String>): Map<String, LocalAlbumAssociation> = buildMap {
        entries.forEach { (key, encoded) ->
            if (!key.startsWith(PREFIX)) return@forEach
            // Corrupt optional review metadata invalidates only the confirmation, not a useful old scope.
            val payload = runCatching { json.parseToJsonElement(encoded) as? JsonObject }.getOrNull()
                ?: return@forEach
            val review = payload["reviewedLocalFingerprint"] as? JsonPrimitive
            val compatible = if (review?.isString == true && isFingerprint(review.content)) payload
                else JsonObject(payload - "reviewedLocalFingerprint")
            val binding = runCatching { json.decodeFromJsonElement<LocalAlbumAssociation>(compatible) }.getOrNull()
                ?: return@forEach
            if (isValid(binding) && keyFor(binding.localAlbumKey) == key) put(binding.localAlbumKey, binding)
        }
    }

    private fun isValid(binding: LocalAlbumAssociation): Boolean = binding.localAlbumKey.isNotBlank() &&
        binding.album.id > 0L && binding.tracks.isNotEmpty() &&
        binding.tracks.all { it.id > 0L } && binding.tracks.map { it.id }.distinct().size == binding.tracks.size &&
        (binding.reviewedLocalFingerprint == null || isFingerprint(binding.reviewedLocalFingerprint))

    private fun isFingerprint(value: String): Boolean = value.matches(Regex("[0-9a-f]{64}"))
}
