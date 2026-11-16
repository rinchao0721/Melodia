package com.lin0721.linmusic.feature.recognition.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.recognition.domain.RecognitionCandidate
import com.lin0721.linmusic.feature.recognition.domain.RecognitionHistoryEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private const val TAG = "RecognitionHistoryPreferences"
const val MAX_RECOGNITION_HISTORY = 100

// 听歌识曲历史，最近优先，同一首歌多次识别各记一条，限量 100 条；按账户 UID 隔离
class RecognitionHistoryPreferences(
    private val dataStore: DataStore<Preferences>,
    private val userPreferences: UserPreferences? = null
) {

    companion object {
        private val KEY_HISTORY = stringPreferencesKey("recognition_history")
        private val json = Json { ignoreUnknownKeys = true }

        private fun keyFor(uid: Long?) =
            stringPreferencesKey(if (uid != null) "recognition_history_$uid" else "recognition_history_anon")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val history: Flow<List<RecognitionHistoryEntry>> = if (userPreferences == null) {
        dataStore.data.map { prefs -> decode(prefs[KEY_HISTORY]) }
    } else {
        userPreferences.activeUid
            .distinctUntilChanged()
            .flatMapLatest { uid ->
                flow {
                    if (uid != null) migrateLegacyToUidIfNeeded(uid)
                    val key = keyFor(uid)
                    emitAll(dataStore.data.map { prefs -> decode(prefs[key]) })
                }
            }
    }

    suspend fun add(entry: RecognitionHistoryEntry) {
        val uid = userPreferences?.activeUid?.first()
        dataStore.edit { prefs ->
            val key = activeKeyInTransaction(prefs, uid)
            val updated = prependCapped(decode(prefs[key]), entry, MAX_RECOGNITION_HISTORY)
            prefs[key] = json.encodeToString(updated)
        }
    }

    suspend fun record(candidate: RecognitionCandidate, recognizedAt: Long = System.currentTimeMillis()) {
        add(
            RecognitionHistoryEntry(
                id = UUID.randomUUID().toString(),
                songId = candidate.songId,
                title = candidate.title,
                artists = candidate.artists,
                coverUrl = candidate.coverUrl,
                startTimeMs = candidate.startTimeMs,
                recognizedAt = recognizedAt
            )
        )
    }

    suspend fun remove(id: String) {
        val uid = userPreferences?.activeUid?.first()
        dataStore.edit { prefs ->
            val key = activeKeyInTransaction(prefs, uid)
            val current = decode(prefs[key])
            prefs[key] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    suspend fun clear() {
        val uid = userPreferences?.activeUid?.first()
        dataStore.edit { prefs ->
            val key = activeKeyInTransaction(prefs, uid)
            prefs.remove(key)
        }
    }

    private suspend fun migrateLegacyToUidIfNeeded(uid: Long) {
        val current = dataStore.data.first()
        if (!current.contains(KEY_HISTORY)) return
        dataStore.edit { prefs ->
            migrateLegacyInTransaction(prefs, uid)
        }
    }

    private fun activeKeyInTransaction(
        prefs: MutablePreferences,
        uid: Long?
    ): Preferences.Key<String> {
        if (userPreferences == null) return KEY_HISTORY
        if (uid != null) migrateLegacyInTransaction(prefs, uid)
        return keyFor(uid)
    }

    private fun migrateLegacyInTransaction(prefs: MutablePreferences, uid: Long) {
        val legacy = prefs[KEY_HISTORY] ?: return
        val targetKey = keyFor(uid)
        if (!prefs.contains(targetKey)) {
            prefs[targetKey] = legacy
        }
        prefs.remove(KEY_HISTORY)
    }

    private fun decode(raw: String?): List<RecognitionHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<RecognitionHistoryEntry>>(raw) }
            .onFailure { AppLogger.w(TAG, "识别历史反序列化失败", it) }
            .getOrDefault(emptyList())
    }
}

internal fun prependCapped(
    current: List<RecognitionHistoryEntry>,
    entry: RecognitionHistoryEntry,
    max: Int
): List<RecognitionHistoryEntry> {
    if (max <= 0) return emptyList()
    return (listOf(entry) + current.filterNot { it.id == entry.id }).take(max)
}
