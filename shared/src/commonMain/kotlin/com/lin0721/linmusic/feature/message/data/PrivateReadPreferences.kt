package com.lin0721.linmusic.feature.message.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.message.domain.PrivateConversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private const val TAG = "PrivateReadPreferences"
private const val MAX_ENTRIES = 500

// 网易没有可用的私信已读接口，打开会话后在本地记下已读到的消息时间，用来抵消服务端未读数
class PrivateReadPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val KEY_READ = stringPreferencesKey("private_read_times")
        private val json = Json { ignoreUnknownKeys = true }

        private fun keyOf(selfUid: Long, peerUid: Long) = "$selfUid:$peerUid"
    }

    // 键为「自己 uid:对方 uid」，避免多账号互相串
    private val readTimes: Flow<Map<String, Long>> = dataStore.data
        .map { prefs -> decode(prefs[KEY_READ]) }
        .catch { e ->
            AppLogger.w(TAG, "私信已读记录读取失败", e)
            emit(emptyMap())
        }

    suspend fun snapshot(selfUid: Long): Map<Long, Long> {
        val prefix = "$selfUid:"
        return readTimes.first().mapNotNull { (key, time) ->
            if (!key.startsWith(prefix)) return@mapNotNull null
            key.removePrefix(prefix).toLongOrNull()?.let { it to time }
        }.toMap()
    }

    // 已读时间只增不减
    suspend fun markRead(selfUid: Long, peerUid: Long, readUpToMs: Long) {
        if (selfUid <= 0L || peerUid <= 0L || readUpToMs <= 0L) return
        try {
            dataStore.edit { prefs ->
                val current = decode(prefs[KEY_READ])
                val key = keyOf(selfUid, peerUid)
                if ((current[key] ?: 0L) >= readUpToMs) return@edit
                prefs[KEY_READ] = json.encodeToString(trim(current + (key to readUpToMs)))
            }
        } catch (e: IOException) {
            AppLogger.w(TAG, "私信已读记录写入失败", e)
        }
    }

    private fun trim(map: Map<String, Long>): Map<String, Long> {
        if (map.size <= MAX_ENTRIES) return map
        return map.entries.sortedByDescending { it.value }.take(MAX_ENTRIES).associate { it.key to it.value }
    }

    private fun decode(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, Long>>(raw) }
            .onFailure { AppLogger.w(TAG, "私信已读记录反序列化失败", it) }
            .getOrDefault(emptyMap())
    }
}

// 本地已读时间覆盖到最后一条消息时，该会话视为无未读
fun PrivateConversation.withLocalRead(readTimes: Map<Long, Long>): PrivateConversation {
    val readUpTo = readTimes[user.uid] ?: return this
    return if (unreadCount > 0 && lastTime > 0L && readUpTo >= lastTime) copy(unreadCount = 0) else this
}
