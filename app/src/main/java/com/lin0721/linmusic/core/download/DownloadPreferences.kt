package com.lin0721.linmusic.core.download

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.platform.TrackId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "DownloadPreferences"

private val Context.downloadDataStore by preferencesDataStore(name = "download_prefs")

// 判断是否为默认下载目录 Uri
fun isDefaultDownloadDirectoryUri(uriString: String): Boolean =
    runCatching { Uri.parse(uriString).authority == MediaStore.AUTHORITY }.getOrDefault(false)

// 下载记录持久化管理
class DownloadPreferences(private val context: Context) {

    companion object {
        private val KEY_RECORDS = stringPreferencesKey("download_records")
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val verifiedUris = ConcurrentHashMap.newKeySet<String>()

    // 共享已反序列化的下载记录，避免列表多行订阅时重复解析 JSON
    val records: SharedFlow<List<DownloadRecord>> = context.downloadDataStore.data
        .map { prefs -> decodeRecords(prefs[KEY_RECORDS]) }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    // 按歌曲 ID 索引的内存快照，供列表行 O(1) 读取
    var recordsByIdSnapshot by mutableStateOf<Map<Long, DownloadRecord>>(emptyMap())
        private set

    // 按跨平台 TrackId 索引的内存快照
    var recordsByTrackIdSnapshot by mutableStateOf<Map<TrackId, DownloadRecord>>(emptyMap())
        private set

    init {
        scope.launch {
            records.collect { list ->
                val byId = list.associateBy { it.songId }
                val byTrackId = list.mapNotNull { record ->
                    if (record.trackId != null || record.songId >= 0L) record.effectiveTrackId to record else null
                }.toMap()
                Snapshot.withMutableSnapshot {
                    recordsByIdSnapshot = byId
                    recordsByTrackIdSnapshot = byTrackId
                }
            }
        }
    }

    fun isUriVerifiedCached(uriString: String): Boolean = uriString in verifiedUris

    suspend fun isDownloaded(songId: Long): Boolean = downloadedQualityFor(songId).first() != null

    // 响应式查询下载记录，文件不存在时自动清理失效记录
    fun downloadedRecordFor(songId: Long): Flow<DownloadRecord?> = records
        .map { list -> list.firstOrNull { it.songId == songId } }
        .distinctUntilChanged()
        .map { record -> record?.let { verifyOrPurge(it) } }

    // 响应式查询已下载音质
    fun downloadedQualityFor(songId: Long): Flow<String?> = downloadedRecordFor(songId).map { it?.quality }

    // 一次性查询校验通过的下载记录
    suspend fun findVerifiedRecord(songId: Long): DownloadRecord? =
        records.first().firstOrNull { it.songId == songId }?.let { verifyOrPurge(it) }

    // 按 TrackId 查询校验通过的下载记录
    suspend fun findVerifiedRecord(trackId: TrackId): DownloadRecord? =
        records.first().firstOrNull { it.storageKey == trackId.canonical }?.let { verifyOrPurge(it) }

    // 批量查询校验通过的下载记录，失效记录一次性清理
    suspend fun findVerifiedRecords(songIds: Collection<Long>): List<DownloadRecord> {
        val idSet = songIds.toHashSet()
        val candidates = records.first().filter { it.songId in idSet }
        return verifyAndPurgeCandidates(candidates)
    }

    // 按 TrackId 批量查询校验通过的下载记录
    suspend fun findVerifiedRecordsForTracks(trackIds: Collection<TrackId>): List<DownloadRecord> {
        val keySet = trackIds.mapTo(HashSet()) { it.canonical }
        return findVerifiedRecordsForKeys(keySet)
    }

    // 按 storageKey 批量查询校验通过的下载记录
    suspend fun findVerifiedRecordsForKeys(keys: Collection<String>): List<DownloadRecord> {
        val keySet = keys.toHashSet()
        if (keySet.isEmpty()) return emptyList()
        val candidates = records.first().filter { it.storageKey in keySet }
        return verifyAndPurgeCandidates(candidates)
    }

    private suspend fun verifyAndPurgeCandidates(candidates: List<DownloadRecord>): List<DownloadRecord> {
        if (candidates.isEmpty()) return emptyList()
        val (alive, stale) = candidates.partition { fileExists(it.mediaStoreUri) }
        if (stale.isNotEmpty()) removeRecordsByKeys(stale.mapTo(HashSet()) { it.storageKey })
        return alive
    }

    // 下载记录中是否有其他歌曲占用该文件，避免覆盖同名的另一首歌
    suspend fun isUriClaimedByOtherSong(uriString: String, songId: Long): Boolean =
        records.first().any { it.mediaStoreUri == uriString && it.songId != songId }

    // 按 TrackId 判断文件是否被其他曲目占用
    suspend fun isUriClaimedByOtherTrack(uriString: String, trackId: TrackId): Boolean =
        records.first().any { it.mediaStoreUri == uriString && it.storageKey != trackId.canonical }

    // 校验文件存在性并清理失效记录
    private suspend fun verifyOrPurge(record: DownloadRecord): DownloadRecord? =
        if (record.mediaStoreUri in verifiedUris || fileExists(record.mediaStoreUri)) {
            verifiedUris.add(record.mediaStoreUri)
            record
        } else {
            verifiedUris.remove(record.mediaStoreUri)
            removeRecordsByKeys(setOf(record.storageKey))
            null
        }

    // 检查目标 Uri 文件是否存在
    suspend fun fileExists(uriString: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { true } ?: false
        }.getOrDefault(false)
    }

    // 删除下载文件；非本应用创建的 MediaStore 文件无删除权限时返回 false
    suspend fun deleteFile(uriString: String): Boolean = withContext(Dispatchers.IO) {
        verifiedUris.remove(uriString)
        runCatching {
            val uri = Uri.parse(uriString)
            if (isDefaultDownloadDirectoryUri(uriString)) {
                context.contentResolver.delete(uri, null, null) > 0
            } else {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            }
        }.onFailure { AppLogger.w(TAG, "删除下载文件失败 uri=$uriString", it) }.getOrDefault(false)
    }

    // 删除已下载歌曲：文件删除成功或文件已不存在时一并移除记录
    suspend fun deleteDownload(record: DownloadRecord): Boolean {
        val deleted = deleteFile(record.mediaStoreUri) || !fileExists(record.mediaStoreUri)
        if (deleted) removeRecordsByKeys(setOf(record.storageKey))
        return deleted
    }

    // 添加或更新下载记录（按 storageKey 去重）
    suspend fun addRecord(record: DownloadRecord) {
        verifiedUris.add(record.mediaStoreUri)
        context.downloadDataStore.edit { prefs ->
            val updated = decodeRecords(prefs[KEY_RECORDS]).filterNot { it.storageKey == record.storageKey } + record
            prefs[KEY_RECORDS] = json.encodeToString(updated)
        }
    }

    // 批量添加或更新下载记录（按 storageKey 去重）
    suspend fun addRecords(newRecords: List<DownloadRecord>) {
        if (newRecords.isEmpty()) return
        newRecords.forEach { verifiedUris.add(it.mediaStoreUri) }
        val keys = newRecords.mapTo(HashSet()) { it.storageKey }
        context.downloadDataStore.edit { prefs ->
            val updated = decodeRecords(prefs[KEY_RECORDS]).filterNot { it.storageKey in keys } + newRecords
            prefs[KEY_RECORDS] = json.encodeToString(updated)
        }
    }

    suspend fun removeRecord(songId: Long) = removeRecords(setOf(songId))

    private suspend fun removeRecords(songIds: Set<Long>) {
        context.downloadDataStore.edit { prefs ->
            val updated = decodeRecords(prefs[KEY_RECORDS]).filterNot { it.songId in songIds }
            prefs[KEY_RECORDS] = json.encodeToString(updated)
        }
    }

    private suspend fun removeRecordsByKeys(keys: Set<String>) {
        context.downloadDataStore.edit { prefs ->
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
