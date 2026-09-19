package com.lin0721.linmusic.core.download

import android.content.ContentValues
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lin0721.linmusic.core.download.data.DownloadApi
import com.lin0721.linmusic.core.download.data.SongDownloadUrlRequest
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.os.ParcelFileDescriptor
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import java.io.File
import android.content.Context

private const val TAG = "SongDownloadWorker"
private const val MAX_ATTEMPTS = 3

private val LOSSLESS_AND_ABOVE = setOf("lossless", "hires", "jyeffect", "sky", "jymaster")
private val COMPRESSED_ENCODE_TYPES = setOf("mp3", "aac", "m4a")

// 歌曲下载后台任务
class SongDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val downloadApi: DownloadApi,
    private val downloadPreferences: DownloadPreferences,
    private val settingsPreferences: SettingsPreferences,
    private val notificationHelper: DownloadNotificationHelper,
    private val playbackRepository: PlaybackRepository,
    private val downloadClient: OkHttpClient
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SONG_ID = "song_id"
        const val KEY_SONG_NAME = "song_name"
        const val KEY_ARTIST_NAME = "artist_name"
        const val KEY_ALBUM_NAME = "album_name"
        const val KEY_COVER_URL = "cover_url"
        const val KEY_ALBUM_YEAR = "album_year"
        const val KEY_LEVEL = "level"
        const val KEY_ERROR = "error"
        const val KEY_BATCH_TAG = "batch_tag"
        const val KEY_BATCH_LABEL = "batch_label"
        const val KEY_PROGRESS_SONG_NAME = "progress_song_name"
        const val KEY_PROGRESS_PERCENT = "progress_percent"

        fun buildInputData(
            songId: Long,
            songName: String,
            artistName: String,
            level: String,
            albumName: String = "",
            coverUrl: String? = null,
            albumYear: Int = 0,
            batchTag: String? = null,
            batchLabel: String? = null
        ) = workDataOf(
            KEY_SONG_ID to songId,
            KEY_SONG_NAME to songName,
            KEY_ARTIST_NAME to artistName,
            KEY_ALBUM_NAME to albumName,
            KEY_COVER_URL to coverUrl,
            KEY_ALBUM_YEAR to albumYear,
            KEY_LEVEL to level,
            KEY_BATCH_TAG to batchTag,
            KEY_BATCH_LABEL to batchLabel
        )
    }

    private val songId: Long get() = inputData.getLong(KEY_SONG_ID, -1)
    private val songName: String get() = inputData.getString(KEY_SONG_NAME) ?: ""
    private val artistName: String get() = inputData.getString(KEY_ARTIST_NAME) ?: ""
    private val albumName: String get() = inputData.getString(KEY_ALBUM_NAME) ?: ""
    private val coverUrl: String? get() = inputData.getString(KEY_COVER_URL)
    private val albumYear: Int get() = inputData.getInt(KEY_ALBUM_YEAR, 0)
    private val level: String get() = inputData.getString(KEY_LEVEL) ?: "standard"
    private val batchTag: String? get() = inputData.getString(KEY_BATCH_TAG)
    private val batchLabel: String get() = inputData.getString(KEY_BATCH_LABEL) ?: "歌曲"

    override suspend fun doWork(): Result {
        val songId = this.songId
        if (songId <= 0) {
            AppLogger.e(TAG, "下载任务参数缺失 songId=$songId")
            return Result.failure(workDataOf(KEY_ERROR to "参数缺失"))
        }

        if (batchTag != "stream_cache") {
            setForegroundAsync(buildForegroundInfo(0))
            publishProgress(0)
        }

        var localTemp: File? = null
        // 失败或取消时的清理回调
        var cleanup: (() -> Unit)? = null
        try {
            val response = downloadApi.getSongDownloadUrl(SongDownloadUrlRequest(id = songId, level = level))
            val item = response.data
            val url = item?.url
            if (!response.isSuccess || url.isNullOrBlank()) {
                onTerminalFailure("获取下载链接失败")
                return Result.failure(workDataOf(KEY_ERROR to "获取下载链接失败，code=${response.code}"))
            }
            if (item.freeTrialInfo != null) {
                onTerminalFailure("该音质仅支持试听，需要 VIP/购买后才能完整下载")
                return Result.failure(workDataOf(KEY_ERROR to "仅试听版本"))
            }

            val actualEncodeType = (item.type ?: item.encodeType)?.lowercase()
            // 校验是否因权限不足被静默降级为压缩格式
            if (level in LOSSLESS_AND_ABOVE && actualEncodeType in COMPRESSED_ENCODE_TYPES) {
                onTerminalFailure("需要更高会员等级才能下载该音质")
                return Result.failure(workDataOf(KEY_ERROR to "音质权限不足"))
            }
            val extension = (actualEncodeType ?: "mp3")
            val displayName = "${sanitizeFileName("$artistName - $songName")}.$extension"
            val mimeType = mimeTypeFor(extension)

            val tempFile = File(applicationContext.cacheDir, "dl_${songId}_${System.currentTimeMillis()}.$extension")
            localTemp = tempFile
            val downloadedSize = downloadToFile(tempFile, url)
            if (downloadedSize == null) {
                onTerminalFailure("下载中断")
                return Result.failure(workDataOf(KEY_ERROR to "下载中断"))
            }

            // 写入音频元数据与封面
            runCatching { writeTags(tempFile, extension) }
                .onFailure { AppLogger.w(TAG, "写入 ID3/Vorbis 标签失败，跳过 songId=$songId", it) }
            val finalFileSize = tempFile.length()

            // 按需下载歌词
            val lrcText = if (settingsPreferences.downloadLyricsEnabled.first()) {
                runCatching { fetchLyricsAsLrc(songId) }
                    .onFailure { AppLogger.w(TAG, "获取歌词失败，跳过 songId=$songId", it) }
                    .getOrNull()
            } else {
                null
            }

            val customFolderUri = settingsPreferences.downloadFolderUri.first()
            val finalUri: Uri? = if (customFolderUri != null) {
                val directory = resolveCustomDirectory(customFolderUri)
                if (directory == null) {
                    onTerminalFailure("自定义下载目录不可用，请到设置里重新选择")
                    return Result.failure(workDataOf(KEY_ERROR to "自定义下载目录不可用"))
                }
                val finalName = uniqueNameIn(directory, displayName)
                val doc = runCatching { directory.createFile(mimeType, finalName) }.getOrNull()
                if (doc == null) {
                    onTerminalFailure("创建本地文件失败")
                    return Result.failure(workDataOf(KEY_ERROR to "SAF createFile 失败"))
                }
                cleanup = { doc.delete() }
                if (copyFileToUri(tempFile, doc.uri)) {
                    if (lrcText != null) writeLrcToDirectory(directory, finalName, lrcText)
                    doc.uri
                } else {
                    null
                }
            } else {
                val uri = insertPendingMediaStoreEntry(displayName, mimeType)
                if (uri == null) {
                    onTerminalFailure("创建本地文件失败")
                    return Result.failure(workDataOf(KEY_ERROR to "MediaStore insert 失败"))
                }
                cleanup = { applicationContext.contentResolver.delete(uri, null, null) }
                if (copyFileToUri(tempFile, uri)) {
                    finalizePendingMediaStoreEntry(uri)
                    if (lrcText != null) writeLrcToMediaStore(displayName, lrcText)
                    uri
                } else {
                    null
                }
            }

            if (finalUri == null) {
                cleanup?.invoke()
                onTerminalFailure("下载中断")
                return Result.failure(workDataOf(KEY_ERROR to "下载中断"))
            }

            // 记录实际下发的音质档位
            downloadPreferences.addRecord(
                DownloadRecord(
                    songId = songId,
                    mediaStoreUri = finalUri.toString(),
                    quality = item.level ?: level,
                    downloadedAt = System.currentTimeMillis(),
                    fileSize = finalFileSize,
                    songName = songName,
                    artistName = artistName
                )
            )

            onTerminalSuccess()
            return Result.success()
        } catch (e: CancellationException) {
            // 任务取消时清理文件
            cleanup?.invoke()
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "下载异常 songId=$songId", e)
            cleanup?.invoke()
            return if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                onTerminalFailure(e.message ?: "下载异常")
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "下载异常")))
            }
        } finally {
            localTemp?.delete()
        }
    }

    // 发送完成通知
    private fun onTerminalSuccess() {
        val tag = batchTag
        if (tag == "stream_cache") return
        if (tag == null) {
            notificationHelper.showSuccess(songId, songName)
            return
        }
        val settledExcludingSelf = batchSettledCount(tag)
        val (_, total) = batchCounts(tag)
        if (settledExcludingSelf + 1 >= total) {
            notificationHelper.showBatchSummary(tag, batchLabel, total)
        }
    }

    private fun onTerminalFailure(reason: String) {
        val tag = batchTag
        if (tag == "stream_cache") {
            AppLogger.w(TAG, "边听边存后台保存失败: $reason songId=$songId")
            return
        }
        if (tag == null) {
            notificationHelper.showFailed(songId, songName, reason)
            return
        }
        val settledExcludingSelf = batchSettledCount(tag)
        val (_, total) = batchCounts(tag)
        if (settledExcludingSelf + 1 >= total) {
            notificationHelper.showBatchSummary(tag, batchLabel, total)
        }
    }

    // 异步上报下载进度
    private fun publishProgress(progress: Int) {
        setProgressAsync(workDataOf(KEY_PROGRESS_SONG_NAME to songName, KEY_PROGRESS_PERCENT to progress))
    }

    private fun buildForegroundInfo(progress: Int): ForegroundInfo {
        val tag = batchTag
        val notificationId: Int
        val notification: android.app.Notification
        if (tag != null) {
            val (settled, total) = batchCounts(tag)
            notificationId = notificationHelper.batchNotificationIdFor(tag)
            notification = notificationHelper.buildBatchProgressNotification(batchLabel, settled, total)
        } else {
            notificationId = notificationHelper.notificationIdFor(songId)
            notification = notificationHelper.buildProgressNotification(songName, progress)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun batchSettledCount(tag: String): Int = batchCounts(tag).first

    // 获取批次完成数与总数
    private fun batchCounts(tag: String): Pair<Int, Int> {
        val infos = runCatching {
            WorkManager.getInstance(applicationContext).getWorkInfosByTag(tag).get()
        }.getOrNull() ?: return 0 to 1
        val total = infos.size
        val settled = infos.count { it.state.isFinished }
        return settled to total
    }

    private fun relativePath(): String {
        val folder = batchTag?.let { sanitizeFileName(batchLabel) }
        return if (folder != null) "Music/Melodia/$folder/" else "Music/Melodia/"
    }

    private fun ensureMusicDirectoryExists(folderName: String?) {
        runCatching {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val targetDir = if (folderName != null) File(musicDir, "Melodia/$folderName") else File(musicDir, "Melodia")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
        }.onFailure { AppLogger.w(TAG, "创建物理目录失败", it) }
    }

    private fun insertPendingMediaStoreEntry(displayName: String, mimeType: String): Uri? {
        val folder = batchTag?.let { sanitizeFileName(batchLabel) }
        ensureMusicDirectoryExists(folder)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath())
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        return applicationContext.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun finalizePendingMediaStoreEntry(uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
        applicationContext.contentResolver.update(uri, values, null, null)
    }

    // 解析自定义存储目录
    private fun resolveCustomDirectory(customFolderUri: String): DocumentFile? {
        val root = runCatching {
            DocumentFile.fromTreeUri(applicationContext, Uri.parse(customFolderUri))
        }.getOrNull() ?: return null
        if (!root.exists() || !root.canWrite()) return null
        val tag = batchTag ?: return root
        val folderName = sanitizeFileName(batchLabel)
        return root.findFile(folderName)?.takeIf { it.isDirectory }
            ?: root.createDirectory(folderName)
    }

    // 避免文件名冲突
    private fun uniqueNameIn(directory: DocumentFile, displayName: String): String {
        if (directory.findFile(displayName) == null) return displayName
        val dot = displayName.lastIndexOf('.')
        return if (dot > 0) "${displayName.substring(0, dot)}_${System.currentTimeMillis()}${displayName.substring(dot)}"
        else "${displayName}_${System.currentTimeMillis()}"
    }

    // 写入音频元数据与封面
    private fun writeTags(file: File, extension: String) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { pfd ->
            val fd = pfd.dup().detachFd()
            val metadata = TagLib.getMetadata(fd, readPictures = false)
            val propertyMap = HashMap<String, Array<String>>(metadata?.propertyMap ?: emptyMap())
            propertyMap["TITLE"] = arrayOf(songName)
            propertyMap["ARTIST"] = arrayOf(artistName)
            if (albumName.isNotBlank()) {
                propertyMap["ALBUM"] = arrayOf(albumName)
            }
            if (albumYear > 0) {
                propertyMap["DATE"] = arrayOf(albumYear.toString())
            }
            TagLib.savePropertyMap(pfd.dup().detachFd(), propertyMap)
        }

        coverUrl?.let { url -> fetchCoverBytes(url) }?.let { coverBytes ->
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { pfd ->
                val picture = Picture(
                    data = coverBytes,
                    description = "Front Cover",
                    pictureType = "Front Cover",
                    mimeType = "image/jpeg"
                )
                TagLib.savePictures(pfd.dup().detachFd(), arrayOf(picture))
            }
        }
    }

    // 拉取并转换为标准 LRC 格式
    private suspend fun fetchLyricsAsLrc(songId: Long): String? {
        val lines = playbackRepository.getLyrics(songId).first().getOrNull()
        if (lines.isNullOrEmpty()) return null
        return lines.sortedBy { it.timeMs }.joinToString("\n") { line ->
            val totalCentis = line.timeMs / 10
            val minutes = totalCentis / 6000
            val seconds = (totalCentis / 100) % 60
            val centis = totalCentis % 100
            "[%02d:%02d.%02d]%s".format(minutes, seconds, centis, line.text)
        }
    }

    private fun lrcFileNameFor(audioDisplayName: String): String {
        val dot = audioDisplayName.lastIndexOf('.')
        return if (dot > 0) "${audioDisplayName.substring(0, dot)}.lrc" else "$audioDisplayName.lrc"
    }

    // 写入歌词文件到自定义 SAF 目录，使用通用 MIME 避免系统追加 .txt 后缀
    private fun writeLrcToDirectory(directory: DocumentFile, audioFileName: String, lrcText: String) {
        runCatching {
            val lrcName = lrcFileNameFor(audioFileName)
            val doc = directory.findFile(lrcName) ?: directory.createFile("application/octet-stream", lrcName) ?: return
            applicationContext.contentResolver.openOutputStream(doc.uri)?.use {
                it.write(lrcText.toByteArray(Charsets.UTF_8))
            }
        }.onFailure { AppLogger.w(TAG, "写入歌词文件失败 songId=$songId", it) }
    }

    // 写入歌词文件到 MediaStore
    private fun writeLrcToMediaStore(audioDisplayName: String, lrcText: String) {
        runCatching {
            val lrcName = lrcFileNameFor(audioDisplayName)
            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, lrcName)
                put(MediaStore.Files.FileColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath())
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
            }
            val uri = applicationContext.contentResolver.insert(
                MediaStore.Files.getContentUri("external"), values
            ) ?: return
            applicationContext.contentResolver.openOutputStream(uri)?.use {
                it.write(lrcText.toByteArray(Charsets.UTF_8))
            }
            val clearPending = ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) }
            applicationContext.contentResolver.update(uri, clearPending, null, null)
        }.onFailure { AppLogger.w(TAG, "写入歌词文件失败 songId=$songId", it) }
    }

    // 下载内嵌封面
    private fun fetchCoverBytes(url: String): ByteArray? = runCatching {
        val sized = if (url.contains('?')) url else "$url?param=500y500"
        val request = Request.Builder().url(sized).build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.bytes()
        }
    }.getOrNull()

    // 流式下载到临时文件
    private suspend fun downloadToFile(file: File, url: String): Long? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLogger.e(TAG, "下载 HTTP 失败 code=${response.code} songId=$songId")
                return@withContext null
            }
            val body = response.body ?: return@withContext null
            val total = body.contentLength()
            var written = 0L
            var lastProgress = -1
            file.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val progress = (written * 100 / total).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                if (batchTag == null) {
                                    setForegroundAsync(buildForegroundInfo(progress))
                                }
                                publishProgress(progress)
                            }
                        }
                    }
                }
            }
            if (total > 0 && written != total) null else written
        }
    }

    // 拷贝文件至目标 Uri
    private suspend fun copyFileToUri(file: File, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val output = applicationContext.contentResolver.openOutputStream(uri) ?: return@runCatching false
            output.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            true
        }.getOrDefault(false)
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "song_$songId" }

    private fun mimeTypeFor(extension: String): String = when (extension) {
        "flac" -> "audio/flac"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> "audio/*"
    }
}
