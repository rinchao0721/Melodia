package com.lin0721.linmusic.desktop.data.download

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "DesktopDownloadPrefs"

// 桌面端下载记录，mediaStoreUri 字段存文件绝对路径
class DesktopDownloadPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        const val STORE_NAME = "download_prefs"

        private val KEY_RECORDS = stringPreferencesKey("download_records")
        private val KEY_TASKS = stringPreferencesKey("download_tasks")
        private val json = Json { ignoreUnknownKeys = true }
    }

    val records: Flow<List<DownloadRecord>> = dataStore.data.map { decodeRecords(it[KEY_RECORDS]) }

    suspend fun findVerifiedRecord(songId: Long): DownloadRecord? =
        findVerifiedRecords(listOf(songId)).firstOrNull()

    // 文件被删除的记录一并清理
    suspend fun findVerifiedRecords(songIds: Collection<Long>): List<DownloadRecord> {
        val idSet = songIds.toHashSet()
        val candidates = records.first().filter { it.songId in idSet }
        if (candidates.isEmpty()) return emptyList()
        val (alive, stale) = withContext(Dispatchers.IO) { candidates.partition { File(it.mediaStoreUri).isFile } }
        if (stale.isNotEmpty()) removeRecords(stale.mapTo(HashSet()) { it.songId })
        return alive
    }

    suspend fun isPathClaimedByOtherSong(path: String, songId: Long): Boolean =
        records.first().any { it.mediaStoreUri == path && it.songId != songId }

    suspend fun addRecord(record: DownloadRecord) {
        dataStore.edit { prefs ->
            val updated = decodeRecords(prefs[KEY_RECORDS]).filterNot { it.songId == record.songId } + record
            prefs[KEY_RECORDS] = json.encodeToString(updated)
        }
    }

    suspend fun addRecords(newRecords: List<DownloadRecord>) {
        if (newRecords.isEmpty()) return
        val ids = newRecords.mapTo(HashSet()) { it.songId }
        dataStore.edit { prefs ->
            val updated = decodeRecords(prefs[KEY_RECORDS]).filterNot { it.songId in ids } + newRecords
            prefs[KEY_RECORDS] = json.encodeToString(updated)
        }
    }

    suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) { File(path).isFile }

    suspend fun loadTasks(): List<DownloadTask> {
        val raw = dataStore.data.first()[KEY_TASKS]
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<DownloadTask>>(raw) }
            .onFailure { AppLogger.w(TAG, "下载任务反序列化失败", it) }
            .getOrDefault(emptyList())
    }

    suspend fun saveTasks(tasks: List<DownloadTask>) {
        dataStore.edit { prefs -> prefs[KEY_TASKS] = json.encodeToString(tasks) }
    }

    private suspend fun removeRecords(songIds: Set<Long>) {
        dataStore.edit { prefs ->
            val updated = decodeRecords(prefs[KEY_RECORDS]).filterNot { it.songId in songIds }
            prefs[KEY_RECORDS] = json.encodeToString(updated)
        }
    }

    private fun decodeRecords(raw: String?): List<DownloadRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<DownloadRecord>>(raw) }
            .onFailure { AppLogger.w(TAG, "下载记录反序列化失败", it) }
            .getOrDefault(emptyList())
    }
}
