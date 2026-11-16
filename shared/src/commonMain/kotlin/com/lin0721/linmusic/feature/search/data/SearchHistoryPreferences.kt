package com.lin0721.linmusic.feature.search.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "SearchHistoryPreferences"
private const val MAX_HISTORY_SIZE = 20

// 本地搜索历史持久化，有序（最近优先）、去重、限量；按账户 UID 隔离，未登录用独立匿名键
class SearchHistoryPreferences(
    private val dataStore: DataStore<Preferences>,
    private val userPreferences: UserPreferences? = null
) {

    companion object {
        private val KEY_HISTORY = stringPreferencesKey("search_history")
        private val json = Json { ignoreUnknownKeys = true }

        private fun keyFor(uid: Long?) =
            stringPreferencesKey(if (uid != null) "search_history_$uid" else "search_history_anon")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val history: Flow<List<String>> = if (userPreferences == null) {
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

    suspend fun addKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        val uid = userPreferences?.activeUid?.first()
        dataStore.edit { prefs ->
            val key = activeKeyInTransaction(prefs, uid)
            val current = decode(prefs[key])
            val updated = (listOf(trimmed) + current.filterNot { it == trimmed }).take(MAX_HISTORY_SIZE)
            prefs[key] = json.encodeToString(updated)
        }
    }

    suspend fun removeKeyword(keyword: String) {
        val uid = userPreferences?.activeUid?.first()
        dataStore.edit { prefs ->
            val key = activeKeyInTransaction(prefs, uid)
            val current = decode(prefs[key])
            prefs[key] = json.encodeToString(current.filterNot { it == keyword })
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
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        uid: Long?
    ): Preferences.Key<String> {
        if (userPreferences == null) return KEY_HISTORY
        if (uid != null) migrateLegacyInTransaction(prefs, uid)
        return keyFor(uid)
    }

    private fun migrateLegacyInTransaction(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        uid: Long
    ) {
        val legacy = prefs[KEY_HISTORY] ?: return
        val targetKey = keyFor(uid)
        if (!prefs.contains(targetKey)) {
            prefs[targetKey] = legacy
        }
        prefs.remove(KEY_HISTORY)
    }

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }
            .onFailure { AppLogger.w(TAG, "搜索历史反序列化失败", it) }
            .getOrDefault(emptyList())
    }
}
