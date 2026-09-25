package com.lin0721.linmusic.feature.recognition.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.recognition.domain.RecognitionHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "RecognitionHistoryPreferences"
internal const val MAX_RECOGNITION_HISTORY = 100

private val Context.recognitionHistoryDataStore by preferencesDataStore(
    name = "recognition_history_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        AppLogger.e(TAG, "识别历史数据损坏，已重置为默认值", ex)
        emptyPreferences()
    }
)

// 听歌识曲历史，最近优先，同一首歌多次识别各记一条，限量 100 条
class RecognitionHistoryPreferences(private val context: Context) {

    companion object {
        private val KEY_HISTORY = stringPreferencesKey("recognition_history")
        private val json = Json { ignoreUnknownKeys = true }
    }

    val history: Flow<List<RecognitionHistoryEntry>> = context.recognitionHistoryDataStore.data.map { prefs ->
        decode(prefs[KEY_HISTORY])
    }

    suspend fun add(entry: RecognitionHistoryEntry) {
        context.recognitionHistoryDataStore.edit { prefs ->
            val updated = prependCapped(decode(prefs[KEY_HISTORY]), entry, MAX_RECOGNITION_HISTORY)
            prefs[KEY_HISTORY] = json.encodeToString(updated)
        }
    }

    suspend fun remove(id: String) {
        context.recognitionHistoryDataStore.edit { prefs ->
            val current = decode(prefs[KEY_HISTORY])
            prefs[KEY_HISTORY] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    suspend fun clear() {
        context.recognitionHistoryDataStore.edit { prefs -> prefs.remove(KEY_HISTORY) }
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
