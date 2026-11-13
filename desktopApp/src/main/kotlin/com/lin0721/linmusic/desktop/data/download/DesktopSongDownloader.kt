package com.lin0721.linmusic.desktop.data.download

import com.lin0721.linmusic.core.download.BatchEnqueueResult
import com.lin0721.linmusic.core.download.DownloadQueueGate
import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.download.DownloadTrackInfo
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.download.data.DownloadApi
import com.lin0721.linmusic.core.download.data.DownloadUrlOutcome
import com.lin0721.linmusic.core.download.data.DownloadUrlResolver
import com.lin0721.linmusic.core.download.data.PLUGIN_NOT_BETTER
import com.lin0721.linmusic.core.download.data.notBetterThan
import com.lin0721.linmusic.core.download.data.SongDownloadUrlRequest
import com.lin0721.linmusic.core.download.recognition.DownloadRecognizer
import com.lin0721.linmusic.core.download.recognition.toRecognitionTarget
import com.lin0721.linmusic.core.download.skipsDownloadAt
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.plugin.PluginTrackQuery
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "DesktopSongDownloader"
private const val MAX_CONCURRENT_DOWNLOADS = 3
private const val MAX_ATTEMPTS = 3
private const val RETRY_DELAY_MS = 2_000L
private const val PARTIAL_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
private const val BUFFER_SIZE = 64 * 1024

private sealed interface Outcome {
    data object Success : Outcome
    data object Skipped : Outcome
    data class Failed(val reason: String) : Outcome
}

// 重试不会改变结果
private class NonRetryableException(message: String) : Exception(message)

// 批量收尾统计，全部结束时汇总提示一次
private class BatchProgress(val label: String, val total: Int) {
    val settled = AtomicInteger(0)
    val failed = AtomicInteger(0)
}

class DesktopSongDownloader(
    downloadApi: DownloadApi,
    private val playbackRepository: PlaybackRepository,
    private val settingsPreferences: SettingsPreferences,
    private val downloadPreferences: DesktopDownloadPreferences,
    private val tempDir: File,
    private val defaultDir: File,
    private val recognizer: DownloadRecognizer? = null,
    // 默认只走官方下载接口，不询问插件下载源
    private val urlResolver: DownloadUrlResolver = DownloadUrlResolver(
        official = { id, level -> downloadApi.getSongDownloadUrl(SongDownloadUrlRequest(id = id, level = level)) }
    )
) : SongDownloader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val sequence = AtomicLong(0)
    private val queueOrder = ConcurrentHashMap<String, Long>()
    private val gate = DownloadQueueGate(MAX_CONCURRENT_DOWNLOADS) { workIds ->
        workIds.associateWith { queueOrder[it] ?: Long.MAX_VALUE }
    }

    private val lock = Any()
    private val persistMutex = Mutex()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val batches = ConcurrentHashMap<String, BatchProgress>()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF
        scope.launch {
            restoreTasks()
            deleteStalePartials()
        }
    }

    override fun enqueueSingle(track: DownloadTrackInfo, level: String): UUID {
        val (task, isNew) = register(track, level, batchLabel = null)
        if (isNew) start(task.id, batch = null) else if (task.status == DownloadTaskStatus.PAUSED) resume(task.id)
        return UUID.fromString(task.id)
    }

    override suspend fun enqueueBatch(
        tracks: List<DownloadTrackInfo>,
        level: String,
        batchTag: String,
        batchLabel: String
    ): BatchEnqueueResult {
        val distinctTracks = tracks.distinctBy { it.songId }
        // 先把目录里已有的同一首补登记，再统一按记录判断跳过
        recognizer?.recognize(distinctTracks.map { it.toRecognitionTarget() })
        val downloadedIds = downloadPreferences.findVerifiedRecords(distinctTracks.map { it.songId })
            .filter { it.skipsDownloadAt(level) }
            .mapTo(HashSet()) { it.songId }
        val liveIds = _tasks.value.filter { it.isLive }.mapTo(HashSet()) { it.songId }
        val queuedIds = distinctTracks.mapNotNullTo(HashSet()) { it.songId.takeIf(liveIds::contains) }
        val pending = distinctTracks.filterNot { it.songId in downloadedIds || it.songId in queuedIds }

        val batch = BatchProgress(batchLabel, pending.size)
        pending.forEach { track ->
            val (task, isNew) = register(track, level, batchLabel)
            // 并发入队以先到者为准
            if (isNew) start(task.id, batch) else batch.settled.incrementAndGet()
        }
        return BatchEnqueueResult(
            enqueuedCount = pending.size,
            skippedCount = downloadedIds.size,
            queuedCount = distinctTracks.count { it.songId in queuedIds && it.songId !in downloadedIds }
        )
    }

    fun pause(taskId: String) {
        var paused = false
        _tasks.update { list ->
            list.map {
                if (it.id == taskId && it.isActive) it.copy(status = DownloadTaskStatus.PAUSED).also { paused = true } else it
            }
        }
        if (!paused) return
        jobs.remove(taskId)?.cancel()
        persistSoon()
    }

    // 继续暂停的任务或重试失败的任务
    fun resume(taskId: String) {
        var resumed = false
        _tasks.update { list ->
            list.map {
                if (it.id == taskId && (it.status == DownloadTaskStatus.PAUSED || it.status == DownloadTaskStatus.FAILED)) {
                    it.copy(status = DownloadTaskStatus.QUEUED, failureReason = null).also { resumed = true }
                } else {
                    it
                }
            }
        }
        if (!resumed) return
        persistSoon()
        start(taskId, batch = null)
    }

    // 取消并移除任务，未完成的同时清掉断点文件
    fun cancel(taskId: String) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        _tasks.update { list -> list.filterNot { it.id == taskId } }
        queueOrder.remove(taskId)
        batches.remove(taskId)
        persistSoon()
        if (task.status == DownloadTaskStatus.SUCCEEDED) return
        val job = jobs.remove(taskId)
        scope.launch {
            job?.cancelAndJoin()
            deletePartials(task.songId)
        }
    }

    fun clearFinished() = removeWhere { it.status == DownloadTaskStatus.SUCCEEDED }

    fun clearFailed() {
        val failed = _tasks.value.filter { it.status == DownloadTaskStatus.FAILED }
        removeWhere { it.status == DownloadTaskStatus.FAILED }
        scope.launch { failed.forEach { deletePartials(it.songId) } }
    }

    private fun removeWhere(predicate: (DownloadTask) -> Boolean) {
        _tasks.update { list -> list.filterNot(predicate) }
        persistSoon()
    }

    // 同一首歌已有未结束任务时不重复创建
    private fun register(track: DownloadTrackInfo, level: String, batchLabel: String?): Pair<DownloadTask, Boolean> {
        synchronized(lock) {
            _tasks.value.firstOrNull { it.songId == track.songId && it.isLive }?.let { return it to false }
            val task = DownloadTask(
                id = UUID.randomUUID().toString(),
                songId = track.songId,
                songName = track.songName,
                artistName = track.artistName,
                albumName = track.albumName,
                coverUrl = track.coverUrl,
                albumYear = track.albumYear,
                durationMs = track.durationMs,
                level = level,
                batchLabel = batchLabel,
                status = DownloadTaskStatus.QUEUED,
                createdAt = System.currentTimeMillis()
            )
            _tasks.update { list -> list.filterNot { it.songId == track.songId } + task }
            persistSoon()
            return task to true
        }
    }

    private fun start(taskId: String, batch: BatchProgress?) {
        queueOrder[taskId] = sequence.incrementAndGet()
        if (batch != null) batches[taskId] = batch
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val task = _tasks.value.firstOrNull { it.id == taskId } ?: return@launch
            try {
                val outcome = gate.withPermit(taskId) {
                    markDownloading(taskId)
                    runWithRetry(task.toTrackInfo(), task.level, task.batchLabel) { percent -> updateProgress(taskId, percent) }
                }
                settle(taskId, outcome)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "下载任务异常 songId=${task.songId}", e)
                settle(taskId, Outcome.Failed("下载异常"))
            } finally {
                jobs.remove(taskId, currentCoroutineContext()[Job])
            }
        }
        jobs[taskId] = job
        job.start()
    }

    private fun markDownloading(taskId: String) = updateTask(taskId) {
        if (it.status == DownloadTaskStatus.QUEUED) it.copy(status = DownloadTaskStatus.DOWNLOADING, progress = 0) else it
    }

    private fun updateProgress(taskId: String, percent: Int) = updateTask(taskId) {
        if (it.status == DownloadTaskStatus.DOWNLOADING && it.progress != percent) it.copy(progress = percent) else it
    }

    private fun updateTask(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == taskId) transform(it) else it } }
    }

    // 任务已被暂停或取消时忽略迟到的结果
    private fun settle(taskId: String, outcome: Outcome) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (task.status != DownloadTaskStatus.DOWNLOADING && task.status != DownloadTaskStatus.QUEUED) return
        val now = System.currentTimeMillis()
        updateTask(taskId) {
            when (outcome) {
                Outcome.Success -> it.copy(status = DownloadTaskStatus.SUCCEEDED, progress = 100, finishedAt = now)
                Outcome.Skipped -> it.copy(status = DownloadTaskStatus.SUCCEEDED, progress = 100, skipped = true, finishedAt = now)
                is Outcome.Failed -> it.copy(status = DownloadTaskStatus.FAILED, failureReason = outcome.reason, finishedAt = now)
            }
        }
        queueOrder.remove(taskId)
        persistSoon()
        report(outcome, task.toTrackInfo(), batches.remove(taskId))
    }

    // 串行保存，保证最后写入的是最新状态
    private fun persistSoon() {
        scope.launch {
            persistMutex.withLock {
                downloadPreferences.saveTasks(_tasks.value.filter { it.status != DownloadTaskStatus.SUCCEEDED })
            }
        }
    }

    // 未完成的任务恢复为暂停
    private suspend fun restoreTasks() {
        val saved = downloadPreferences.loadTasks().map {
            if (it.isActive) it.copy(status = DownloadTaskStatus.PAUSED, progress = 0) else it
        }
        synchronized(lock) {
            _tasks.update { current ->
                val liveSongIds = current.mapTo(HashSet()) { it.songId }
                current + saved.filter { it.songId !in liveSongIds }
            }
        }
    }

    private fun deletePartials(songId: Long) {
        tempDir.listFiles { file -> file.name.startsWith("dl_${songId}_") && file.name.endsWith(".part") }
            ?.forEach { it.delete() }
    }

    private fun report(outcome: Outcome, track: DownloadTrackInfo, batch: BatchProgress?) {
        if (outcome is Outcome.Failed) batch?.failed?.incrementAndGet()
        if (batch == null) {
            when (outcome) {
                Outcome.Success -> _messages.tryEmit("《${track.songName}》下载完成")
                Outcome.Skipped -> _messages.tryEmit("《${track.songName}》已下载过")
                is Outcome.Failed -> _messages.tryEmit("《${track.songName}》下载失败：${outcome.reason}")
            }
            return
        }
        if (batch.settled.incrementAndGet() >= batch.total) {
            val failed = batch.failed.get()
            val suffix = if (failed > 0) "，$failed 首失败" else ""
            _messages.tryEmit("「${batch.label}」下载完成$suffix")
        }
    }

    private suspend fun runWithRetry(
        track: DownloadTrackInfo,
        level: String,
        batchLabel: String?,
        onProgress: (Int) -> Unit
    ): Outcome {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return download(track, level, batchLabel, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NonRetryableException) {
                return Outcome.Failed(e.message ?: "下载失败")
            } catch (e: Exception) {
                AppLogger.w(TAG, "下载失败 songId=${track.songId} 第 $attempt 次", e)
                if (attempt >= MAX_ATTEMPTS) {
                    return Outcome.Failed(if (e is IOException) "网络异常，请检查网络后重试" else "下载异常")
                }
                delay(RETRY_DELAY_MS * attempt)
            }
        }
    }

    private suspend fun download(track: DownloadTrackInfo, level: String, batchLabel: String?, onProgress: (Int) -> Unit): Outcome {
        val songId = track.songId
        val existing = downloadPreferences.findVerifiedRecord(songId)
            ?: recognizer?.recognize(listOf(track.toRecognitionTarget()))?.firstOrNull()
        if (existing != null && existing.skipsDownloadAt(level)) return Outcome.Skipped

        val query = PluginTrackQuery(songId.toString(), track.songName, track.artistName, track.albumName, track.durationMs, MusicPlatform.NETEASE)
        val source = when (val outcome = urlResolver.resolve(songId, level, query)) {
            is DownloadUrlOutcome.Rejected -> throw NonRetryableException(outcome.reason)
            is DownloadUrlOutcome.Ready -> outcome.download
        }
        if (source.notBetterThan(existing)) throw NonRetryableException(PLUGIN_NOT_BETTER)
        if (source.fromPlugin) AppLogger.i(TAG, "官方下载链接不可用，改用插件下载源 songId=$songId")
        val extension = source.encodeType

        val directory = resolveDirectory(batchLabel)
        tempDir.mkdirs()
        val partial = File(tempDir, "dl_${songId}_${level}_${source.size}.part")
        val staged = File(tempDir, "dl_${songId}_${System.currentTimeMillis()}.$extension")
        try {
            downloadToFile(partial, source.url, source.size, source.headers, onProgress)
            moveFile(partial, staged)
            runCatching { writeTags(staged, track) }
                .onFailure { AppLogger.w(TAG, "写入标签失败，跳过 songId=$songId", it) }

            val fileSize = staged.length()
            val target = resolveTarget(directory, "${sanitizeFileName(track.artistName, track.songName, songId)}.$extension", songId)
            withContext(Dispatchers.IO) { moveFile(staged, target) }

            downloadPreferences.addRecord(
                DownloadRecord(
                    songId = songId,
                    mediaStoreUri = target.absolutePath,
                    quality = source.level,
                    downloadedAt = System.currentTimeMillis(),
                    fileSize = fileSize,
                    songName = track.songName,
                    artistName = track.artistName,
                    // 插件来源以实际音质为准，避免之后选更高音质时被判为已下载
                    requestedLevel = if (source.fromPlugin) source.level else level
                )
            )
            existing?.mediaStoreUri?.takeIf { it != target.absolutePath }?.let { File(it).delete() }
            return Outcome.Success
        } finally {
            staged.delete()
        }
    }

    private suspend fun resolveDirectory(batchLabel: String?): File {
        val root = settingsPreferences.downloadFolderUri.first()?.takeIf { it.isNotBlank() }?.let(::File) ?: defaultDir
        val directory = if (batchLabel != null) File(root, sanitizeName(batchLabel).ifBlank { "batch" }) else root
        // 并发任务可能同时创建同一目录，mkdirs 返回 false 不代表失败
        val usable = withContext(Dispatchers.IO) {
            directory.mkdirs()
            directory.isDirectory && directory.canWrite()
        }
        if (!usable) throw NonRetryableException("下载目录不可用，请到设置里重新选择")
        return directory
    }

    // 同名文件属于同一首歌时覆盖，被另一首歌占用时加时间戳
    private suspend fun resolveTarget(directory: File, displayName: String, songId: Long): File {
        val candidate = File(directory, displayName)
        if (!candidate.exists()) return candidate
        if (!downloadPreferences.isPathClaimedByOtherSong(candidate.absolutePath, songId)) return candidate
        val dot = displayName.lastIndexOf('.')
        val stamp = System.currentTimeMillis()
        val unique = if (dot > 0) "${displayName.substring(0, dot)}_$stamp${displayName.substring(dot)}" else "${displayName}_$stamp"
        return File(directory, unique)
    }

    // 有断点且服务端支持 Range 时续传，写满预期大小才算完成
    private suspend fun downloadToFile(
        file: File,
        url: String,
        expectedSize: Long,
        headers: Map<String, String>,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val existing = if (file.exists()) file.length() else 0L
        if (expectedSize > 0 && existing == expectedSize) return@withContext
        val resumeFrom = if (expectedSize > 0 && existing in 1 until expectedSize) existing else 0L
        val request = Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (name, value) -> header(name, value) }
                if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-")
            }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("响应体为空")
            val append = resumeFrom > 0 && response.code == 206 &&
                contentRangeStart(response.header("Content-Range")) == resumeFrom
            val start = if (append) resumeFrom else 0L
            val bodyLength = body.contentLength()
            val total = if (bodyLength > 0) start + bodyLength else expectedSize
            var written = start
            var lastPercent = -1
            FileOutputStream(file, append).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val percent = (written * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            // 插件来源大小未知且没有 Content-Length 时，只能靠这里拦住空响应
            if (written == 0L) throw IOException("下载内容为空")
            if (total > 0 && written != total) throw IOException("文件不完整 $written/$total")
        }
    }

    // 解析 "bytes 100-199/200" 的起始偏移
    private fun contentRangeStart(header: String?): Long? =
        header?.removePrefix("bytes")?.trim()?.substringBefore('-')?.toLongOrNull()

    private suspend fun writeTags(file: File, track: DownloadTrackInfo) {
        val rawLyrics = if (settingsPreferences.downloadLyricsEnabled.first()) {
            runCatching { playbackRepository.getRawLyrics(track.songId).first().getOrNull() }
                .onFailure { AppLogger.w(TAG, "获取歌词失败，跳过 songId=${track.songId}", it) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val cover = track.coverUrl?.let { fetchCoverBytes(it) }

        withContext(Dispatchers.IO) {
            val audio = AudioFileIO.read(file)
            val tag = audio.tagOrCreateAndSetDefault
            tag.setField(FieldKey.TITLE, track.songName)
            tag.setField(FieldKey.ARTIST, track.artistName)
            tag.writeSongId(track.songId)
            if (track.albumName.isNotBlank()) tag.setField(FieldKey.ALBUM, track.albumName)
            if (track.albumYear > 0) tag.setField(FieldKey.YEAR, track.albumYear.toString())
            if (rawLyrics != null) tag.setField(FieldKey.LYRICS, rawLyrics)
            if (cover != null) {
                val artwork = ArtworkFactory.getNew().apply {
                    binaryData = cover
                    mimeType = "image/jpeg"
                    pictureType = 3
                    description = "Front Cover"
                }
                tag.deleteArtworkField()
                tag.setField(artwork)
            }
            audio.commit()
        }
    }

    private suspend fun fetchCoverBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val sized = if (url.contains('?')) url else "$url?param=500y500"
            client.newCall(Request.Builder().url(sized).build()).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        }.getOrNull()
    }

    private fun moveFile(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            source.delete()
        }
    }

    private fun deleteStalePartials() {
        val threshold = System.currentTimeMillis() - PARTIAL_MAX_AGE_MS
        tempDir.listFiles { file -> file.name.startsWith("dl_") && file.name.endsWith(".part") }
            ?.filter { it.lastModified() < threshold }
            ?.forEach { it.delete() }
    }

    private fun sanitizeName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimEnd('.')

    private fun sanitizeFileName(artist: String, song: String, songId: Long): String =
        sanitizeName("$artist - $song").ifBlank { "song_$songId" }
}
