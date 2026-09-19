package com.lin0721.linmusic.core.download

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "DownloadPreferences"

private val Context.downloadDataStore by preferencesDataStore(name = "download_prefs")

// 下载记录实体
@Serializable
data class DownloadRecord(
    val songId: Long,
    val mediaStoreUri: String,
    val quality: String,
    val downloadedAt: Long,
    val fileSize: Long,
    val songName: String = "",
    val artistName: String = ""
)

// 判断是否为默认下载目录 Uri
fun isDefaultDownloadDirectoryUri(uriString: String): Boolean =
    runCatching { Uri.parse(uriString).authority == MediaStore.AUTHORITY }.getOrDefault(false)

// 下载记录持久化管理
class DownloadPreferences(private val context: Context) {

    companion object {
        private val KEY_RECORDS = stringPreferencesKey("download_records")
        private val json = Json { ignoreUnknownKeys = true }
    }

    val records: Flow<List<DownloadRecord>> = context.downloadDataStore.data.map { prefs ->
        decodeRecords(prefs[KEY_RECORDS])
    }

    suspend fun isDownloaded(songId: Long): Boolean = downloadedQualityFor(songId).first() != null

    // 响应式查询已下载音质，文件不存在时自动清理失效记录
    fun downloadedQualityFor(songId: Long): Flow<String?> = records
        .map { list -> list.firstOrNull { it.songId == songId } }
        .distinctUntilChanged()
        .map { record -> record?.let { verifyOrPurge(it) }?.quality }

    // 一次性查询校验通过的下载记录
    suspend fun findVerifiedRecord(songId: Long): DownloadRecord? =
        records.first().firstOrNull { it.songId == songId }?.let { verifyOrPurge(it) }

    // 校验文件存在性并清理失效记录
    private suspend fun verifyOrPurge(record: DownloadRecord): DownloadRecord? {
        if (!isDefaultDownloadDirectoryUri(record.mediaStoreUri)) {
            removeRecord(record.songId)
            return null
        }
        return if (fileExists(record.mediaStoreUri)) record else {
            removeRecord(record.songId)
            null
        }
    }

    // 检查目标 Uri 文件是否存在
    private suspend fun fileExists(uriString: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { true } ?: false
        }.getOrDefault(false)
    }

    // 添加或更新下载记录
    suspend fun addRecord(record: DownloadRecord) {
        context.downloadDataStore.edit { prefs ->
            val updated = decodeRecords(prefs[KEY_RECORDS]).filterNot { it.songId == record.songId } + record
            prefs[KEY_RECORDS] = json.encodeToString(updated)
        }
    }

    suspend fun removeRecord(songId: Long) {
        context.downloadDataStore.edit { prefs ->
            val updated = decodeRecords(prefs[KEY_RECORDS]).filterNot { it.songId == songId }
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
