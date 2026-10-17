package com.lin0721.linmusic.feature.localmusic.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.ContextCompat
import com.lin0721.linmusic.core.download.DownloadPreferences
import com.lin0721.linmusic.core.download.isDefaultDownloadDirectoryUri
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.localmusic.data.db.LocalTrackDao
import com.lin0721.linmusic.feature.localmusic.data.db.LocalTrackEntity
import com.lin0721.linmusic.feature.localmusic.data.db.toDomain
import com.lin0721.linmusic.feature.localmusic.data.legacy.LegacyImportedMusicStore
import com.lin0721.linmusic.feature.localmusic.data.scan.ImportResult
import com.lin0721.linmusic.feature.localmusic.data.scan.LocalMusicImporter
import com.lin0721.linmusic.feature.localmusic.data.scan.MediaStoreScanner
import com.lin0721.linmusic.feature.localmusic.domain.AuthorizedFolder
import com.lin0721.linmusic.feature.localmusic.domain.LocalFolder
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.domain.folderPath
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "LocalLibraryRepository"
private const val MEDIA_SCAN_TIMEOUT_MS = 5_000L

// Room 为唯一数据源，sync 增量写回 MediaStore 与导入文件的变化
class LocalLibraryRepository(
    private val context: Context,
    private val dao: LocalTrackDao,
    private val scanner: MediaStoreScanner,
    private val importer: LocalMusicImporter,
    private val downloadPreferences: DownloadPreferences,
    private val legacyImportedStore: LegacyImportedMusicStore,
    private val settings: LocalMusicSettings,
    private val coverArtCache: LocalCoverArtCache
) {

    private val syncMutex = Mutex()

    private val allTracks: Flow<List<LocalTrack>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    // 库内保留全部曲目，过滤只作用在读取侧，改设置无需重新扫描
    val tracks: Flow<List<LocalTrack>> = combine(allTracks, settings.scanFilter) { list, filter ->
        list.filter(filter::accepts)
    }

    val hiddenCount: Flow<Int> = combine(allTracks, settings.scanFilter) { list, filter ->
        list.count { !filter.accepts(it) }
    }

    val folders: Flow<List<LocalFolder>> = combine(allTracks, settings.scanFilter) { list, filter ->
        list.groupBy { it.folderPath }
            .mapNotNull { (path, items) ->
                path?.let { LocalFolder(path = it, trackCount = items.size, excluded = it in filter.excludedFolders) }
            }
            .sortedBy { it.name.lowercase() }
    }

    fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, requiredPermission()) == PackageManager.PERMISSION_GRANTED

    fun availableStorageBytes(): Long = runCatching {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrDefault(0L)

    suspend fun sync() = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            migrateLegacyImports()
            val existing = dao.getAll()
            val refreshedImports = existing.mapNotNull {
                ensureActive()
                importer.refreshWavTags(it)
            }
            // SAF 的授权不依赖全库权限；全库扫描失败也不能丢失已成功读取的导入标签。
            if (!hasPermission()) {
                if (refreshedImports.isNotEmpty()) dao.upsert(refreshedImports)
                return@withLock
            }
            val rawScanned = scanner.scan()
            if (rawScanned == null) {
                if (refreshedImports.isNotEmpty()) dao.upsert(refreshedImports)
                return@withLock
            }

            val downloadRecords = downloadPreferences.records.first()
                .filter { isDefaultDownloadDirectoryUri(it.mediaStoreUri) }
                .associateBy { it.mediaStoreUri }
            val scanned = rawScanned.map { entity ->
                val record = downloadRecords[entity.uri] ?: return@map entity
                // 标题/歌手以文件标签为准，下载记录只补空缺，否则用户改完标签会被记录里的旧名盖回
                entity.copy(
                    songId = record.songId,
                    title = entity.title.ifBlank { record.songName },
                    artist = entity.artist.ifBlank { record.artistName },
                    source = LocalTrackSource.MELODIA_DOWNLOAD.name
                )
            }

            val diff = computeLocalLibrarySyncDiff(
                existing = existing,
                scanned = scanned,
                isImportedAlive = { isReadable(Uri.parse(it.uri)) }
            )
            val scannedUris = scanned.mapTo(HashSet()) { it.uri }
            val importedUpdates = refreshedImports.filter { it.uri !in scannedUris && it.uri !in diff.deleteUris }
            dao.applySync(diff.upserts + importedUpdates, diff.deleteUris)
        }
    }

    suspend fun importFiles(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        val distinct = uris.distinct()
        if (distinct.isEmpty()) return@withContext ImportResult(0, 0, 0)
        val knownUris = dao.getAllUris().toHashSet()
        val parsed = importer.parseFiles(distinct, knownUris)
        val added = dao.insertIgnoringExisting(parsed).count { it != -1L }
        ImportResult(addedCount = added, skippedCount = distinct.size - added, totalFound = distinct.size)
    }

    suspend fun importFolder(treeUri: Uri): ImportResult = importFiles(importer.collectFolder(treeUri))

    // 导入条目只移出曲库并释放授权，不删文件；其余条目删除文件本身
    suspend fun delete(track: LocalTrack): Boolean = withContext(Dispatchers.IO) {
        val uriString = track.uri.toString()
        if (track.source == LocalTrackSource.IMPORTED) {
            dao.deleteByUris(listOf(uriString))
            runCatching {
                context.contentResolver.releasePersistableUriPermission(track.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return@withContext true
        }

        val deleted = runCatching { context.contentResolver.delete(track.uri, null, null) > 0 }
            .onFailure { AppLogger.w(TAG, "删除本地文件失败 uri=$uriString", it) }
            .getOrDefault(false)
        if (deleted) {
            dao.deleteByUris(listOf(uriString))
            track.songId?.let { downloadPreferences.removeRecord(it) }
        }
        deleted
    }

    suspend fun listAuthorizedFolders(): List<AuthorizedFolder> = withContext(Dispatchers.IO) {
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
            .map { permission ->
                val treeUri = permission.uri
                AuthorizedFolder(
                    treeUri = treeUri.toString(),
                    name = DocumentFile.fromTreeUri(context, treeUri)?.name
                        ?: Uri.decode(treeUri.lastPathSegment.orEmpty()).substringAfterLast(':'),
                    importedCount = dao.countImportedWithPrefix(importedPrefixOf(treeUri))
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    // 移除授权时一并移出该文件夹下导入的曲目，释放授权后这些文件已不可读
    suspend fun removeAuthorizedFolder(treeUri: String) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(treeUri)
        dao.deleteImportedWithPrefix(importedPrefixOf(uri))
        runCatching {
            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { AppLogger.w(TAG, "释放文件夹授权失败 uri=$treeUri", it) }
    }

    // 文件夹导入的条目 uri 形如 {treeUri}/document/{docId}
    private fun importedPrefixOf(treeUri: Uri): String = "$treeUri/document/"

    // 写入成功后才清空旧数据，失败保留下次重试
    private suspend fun migrateLegacyImports() {
        val legacy = legacyImportedStore.readAll()
        if (legacy.isEmpty()) return
        runCatching {
            dao.insertIgnoringExisting(
                legacy.map { record ->
                    LocalTrackEntity(
                        uri = record.uriString,
                        mediaStoreId = null,
                        songId = null,
                        title = record.title,
                        artist = record.artist,
                        album = record.album,
                        durationMs = record.durationMs,
                        sizeBytes = record.sizeBytes,
                        path = null,
                        dateAddedMs = record.dateAddedMs,
                        dateModifiedMs = record.dateAddedMs,
                        source = LocalTrackSource.IMPORTED.name
                    )
                }
            )
            legacyImportedStore.clear()
        }.onFailure { AppLogger.e(TAG, "旧版导入记录迁移失败", it) }
    }

    private fun isReadable(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    }.getOrDefault(false)

    // 导入条目直接重读元数据，MediaStore 条目等系统重扫后再同步
    suspend fun refreshAfterTagEdit(track: LocalTrack) = withContext(Dispatchers.IO) {
        coverArtCache.invalidate(track.uri)
        if (track.source == LocalTrackSource.IMPORTED) {
            val entity = importer.parseMetadata(track.uri) ?: return@withContext
            val existing = dao.getByUri(track.uri.toString())
            dao.upsert(listOf(existing?.let { entity.copy(dateAddedMs = it.dateAddedMs) } ?: entity))
            return@withContext
        }
        val path = track.path ?: return@withContext
        // 系统扫描回调偶有不回的情况，超时后照常同步
        withTimeoutOrNull(MEDIA_SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
        sync()
    }
}
