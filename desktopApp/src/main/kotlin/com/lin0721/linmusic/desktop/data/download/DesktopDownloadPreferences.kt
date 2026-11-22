package com.lin0721.linmusic.desktop.data.download

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.platform.TrackId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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

    // 按歌曲 id 映射已下载音质，供列表行覆盖线上最高音质徽标
    val downloadedQualities: Flow<Map<Long, String>> = records
        .map { list -> list.associate { it.songId to it.quality } }
        .distinctUntilChanged()

    suspend fun findVerifiedRecord(songId: Long): DownloadRecord? =
        findVerifiedRecords(listOf(songId)).firstOrNull()

    suspend fun findVerifiedRecord(trackId: TrackId): DownloadRecord? =
        findVerifiedRecordsForTracks(listOf(trackId)).firstOrNull()

    // 文件被删除的记录一并清理
    suspend fun findVerifiedRecords(songIds: Collection<Long>): List<DownloadRecord> {
        val idSet = songIds.toHashSet()
        val candidates = records.first().filter { it.songId in idSet }
        return verifyAndPurgeCandidates(candidates)
    }

    // 按 TrackId 批量查询校验通过的下载记录，失效记录按 storageKey 清理
    suspend fun findVerifiedRecordsForTracks(trackIds: Collection<TrackId>): List<DownloadRecord> {
        val keySet = trackIds.mapTo(HashSet()) { it.canonical }
        if (keySet.isEmpty()) return emptyList()
        val candidates = records.first().filter { it.storageKey in keySet }
        return verifyAndPurgeCandidates(candidates)
    }

    private suspend fun verifyAndPurgeCandidates(candidates: List<DownloadRecord>): List<DownloadRecord> {
        if (candidates.isEmpty()) return emptyList()
        val (alive, stale) = withContext(Dispatchers.IO) { candidates.partition { File(it.mediaStoreUri).isFile } }
        if (stale.isNotEmpty()) removeRecordsByKeys(stale.mapTo(HashSet()) { it.storageKey })
        return alive
    }

    suspend fun isPathClaimedByOtherSong(path: String, songId: Long): Boolean =
        records.first().any { it.mediaStoreUri == path && it.songId != songId }

    suspend fun isPathClaimedByOtherTrack(path: String, trackId: TrackId): Boolean =
        records.first().any { it.mediaStoreUri == path && it.storageKey != trackId.canonical }

    suspend fun addRecord(record: DownloadRecord) {
        dataStore.edit { prefs ->
            val updated = decodeRecords(prefs[KEY_RECORDS]).filterNot { it.storageKey == record.storageKey } + record
            prefs[KEY_RECORDS] = json.encodeToString(updated)
        }
    }

    suspend fun addRecords(newRecords: List<DownloadRecord>) {
        if (newRecords.isEmpty()) return
        val keys = newRecords.mapTo(HashSet()) { it.storageKey }
        dataStore.edit { prefs ->
            val updated = decodeRecords(prefs[KEY_RECORDS]).filterNot { it.storageKey in keys } + newRecords
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

    private suspend fun removeRecordsByKeys(keys: Set<String>) {
        dataStore.edit { prefs ->
            val updated = decodeRecords(prefs[KEY_RECORDS]).filterNot { it.storageKey in keys }
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
