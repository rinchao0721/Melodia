package com.lin0721.linmusic.feature.search.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "SearchHistoryPreferences"
private const val MAX_HISTORY_SIZE = 20

private val Context.searchHistoryDataStore by preferencesDataStore(
    name = "search_history_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        AppLogger.e(TAG, "搜索历史数据损坏，已重置为默认值", ex)
        emptyPreferences()
    }
)

// 本地搜索历史持久化，有序（最近优先）、去重、限量
class SearchHistoryPreferences(private val context: Context) {

    companion object {
        private val KEY_HISTORY = stringPreferencesKey("search_history")
        private val json = Json { ignoreUnknownKeys = true }
    }

    val history: Flow<List<String>> = context.searchHistoryDataStore.data.map { prefs ->
        decode(prefs[KEY_HISTORY])
    }

    suspend fun addKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        context.searchHistoryDataStore.edit { prefs ->
            val current = decode(prefs[KEY_HISTORY])
            val updated = (listOf(trimmed) + current.filterNot { it == trimmed }).take(MAX_HISTORY_SIZE)
            prefs[KEY_HISTORY] = json.encodeToString(updated)
        }
    }

    suspend fun removeKeyword(keyword: String) {
        context.searchHistoryDataStore.edit { prefs ->
            val current = decode(prefs[KEY_HISTORY])
            prefs[KEY_HISTORY] = json.encodeToString(current.filterNot { it == keyword })
        }
    }

    suspend fun clear() {
        context.searchHistoryDataStore.edit { prefs -> prefs.remove(KEY_HISTORY) }
    }

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }
            .onFailure { AppLogger.w(TAG, "搜索历史反序列化失败", it) }
            .getOrDefault(emptyList())
    }
}
