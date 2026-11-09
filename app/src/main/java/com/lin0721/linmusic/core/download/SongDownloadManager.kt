package com.lin0721.linmusic.core.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.lin0721.linmusic.core.download.recognition.DownloadRecognizer
import com.lin0721.linmusic.core.download.recognition.toRecognitionTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

// 下载管理面板中的任务状态
enum class DownloadTaskStatus { WAITING, RUNNING, PAUSED, SUCCEEDED, FAILED }

// 合并 WorkManager 实时状态与持久化状态；WorkManager 清理已结束任务后以持久化状态为准，
// 返回 null 表示该任务不展示（已取消或未实际入队）
internal fun resolveTaskStatus(
    workState: WorkInfo.State?,
    persisted: PersistedTaskState?,
    started: Boolean = true
): DownloadTaskStatus? =
    when (workState) {
        // 已被 WorkManager 启动但还在闸门前排队的任务仍算等待中
        WorkInfo.State.RUNNING -> if (started) DownloadTaskStatus.RUNNING else DownloadTaskStatus.WAITING
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadTaskStatus.WAITING
        WorkInfo.State.SUCCEEDED -> DownloadTaskStatus.SUCCEEDED
        WorkInfo.State.FAILED -> DownloadTaskStatus.FAILED
        WorkInfo.State.CANCELLED -> if (persisted == PersistedTaskState.PAUSED) DownloadTaskStatus.PAUSED else null
        null -> when (persisted) {
            PersistedTaskState.PAUSED -> DownloadTaskStatus.PAUSED
            PersistedTaskState.SUCCEEDED -> DownloadTaskStatus.SUCCEEDED
            PersistedTaskState.FAILED -> DownloadTaskStatus.FAILED
            null -> null
        }
    }

// 下载管理面板中的一条任务
data class DownloadTask(
    val meta: DownloadTaskMeta,
    val status: DownloadTaskStatus,
    val progress: Int,
    val failureReason: String?,
    val skipped: Boolean
) {
    val isActive: Boolean get() = status == DownloadTaskStatus.WAITING || status == DownloadTaskStatus.RUNNING

    // 未结束：进行中或已暂停
    val isUnfinished: Boolean get() = isActive || status == DownloadTaskStatus.PAUSED
}

// 歌曲下载任务调度管理器
class SongDownloadManager(
    private val context: Context,
    private val downloadPreferences: DownloadPreferences,
    private val taskStore: DownloadTaskStore,
    private val downloadRecognizer: DownloadRecognizer
) : SongDownloader {

    companion object {
        private const val TAG_DOWNLOAD = "song_download"
        private const val TAG_STREAM_CACHE = "stream_cache"
        private const val TAG_SONG_PREFIX = "song_id:"
        // 任务元数据先于 WorkManager 落库，清理时给刚创建的记录留出宽限
        private const val META_GRACE_MS = 60_000L
        // 长期未继续的断点文件按此时长清理
        private const val PARTIAL_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private const val CANCEL_SETTLE_MS = 1_000L
        private fun uniqueWorkName(songId: Long) = "song_download_$songId"
    }

    private val workManager get() = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { deleteStalePartials() }
    }

    suspend fun isDownloaded(songId: Long): Boolean = downloadPreferences.isDownloaded(songId)

    override fun enqueueSingle(track: DownloadTrackInfo, level: String): UUID {
        val request = buildRequest(track, level)
        scope.launch {
            // 先写元数据再入队，面板不会出现缺少歌名的任务
            taskStore.add(listOf(metaFor(request.id, track, level, batchTag = null, batchLabel = null)))
            workManager.enqueueUniqueWork(uniqueWorkName(track.songId), ExistingWorkPolicy.REPLACE, request)
        }
        return request.id
    }

    // 边听边存入队
    fun enqueueStreamCache(track: DownloadTrackInfo, level: String): UUID {
        val request = buildRequest(track, level, batchTag = TAG_STREAM_CACHE, batchLabel = "边听边存")
        workManager.enqueueUniqueWork(uniqueWorkName(track.songId), ExistingWorkPolicy.KEEP, request)
        return request.id
    }

    // 批量下载入队：跳过已下载同等或更高音质的歌曲，以及已在队列中的歌曲
    override suspend fun enqueueBatch(
        tracks: List<DownloadTrackInfo>,
        level: String,
        batchTag: String,
        batchLabel: String
    ): BatchEnqueueResult {
        val distinctTracks = tracks.distinctBy { it.songId }
        // 先把目录里已有的同一首补登记，再统一按记录判断跳过
        downloadRecognizer.recognize(distinctTracks.map { it.toRecognitionTarget() })
        val downloadedIds = downloadPreferences.findVerifiedRecords(distinctTracks.map { it.songId })
            .filter { it.skipsDownloadAt(level) }
            .mapTo(HashSet()) { it.songId }
        val infos = currentWorkInfos()
        val queuedIds = infos.filterNot { it.state.isFinished }.mapNotNullTo(HashSet()) { songIdOf(it) }
        val pending = distinctTracks.filterNot { it.songId in downloadedIds || it.songId in queuedIds }

        val requests = pending.map { track -> track to buildRequest(track, level, batchTag, batchLabel) }
        taskStore.retainExisting(infos.mapTo(HashSet()) { it.id.toString() }, System.currentTimeMillis() - META_GRACE_MS)
        taskStore.add(requests.map { (track, request) -> metaFor(request.id, track, level, batchTag, batchLabel) })
        requests.forEach { (track, request) ->
            // 未带歌曲标签的旧版本任务仍由 KEEP 兜底，不会被打断
            workManager.enqueueUniqueWork(uniqueWorkName(track.songId), ExistingWorkPolicy.KEEP, request)
        }
        return BatchEnqueueResult(
            enqueuedCount = pending.size,
            skippedCount = downloadedIds.size,
            queuedCount = distinctTracks.count { it.songId in queuedIds && it.songId !in downloadedIds }
        )
    }

    fun cancel(songId: Long) {
        workManager.cancelUniqueWork(uniqueWorkName(songId))
    }

    // 观察下载管理面板的任务列表，排除边听边存与已取消的任务
    fun observeTasks(): Flow<List<DownloadTask>> =
        combine(taskStore.tasks, workManager.getWorkInfosByTagFlow(TAG_DOWNLOAD)) { metas, infos ->
            val infoById = infos.associateBy { it.id.toString() }
            metas.sortedBy { it.queueOrder }.mapNotNull { meta ->
                val info = infoById[meta.workId]
                val started = info?.progress?.getBoolean(SongDownloadWorker.KEY_PROGRESS_STARTED, false) == true
                val status = resolveTaskStatus(info?.state, meta.state, started) ?: return@mapNotNull null
                DownloadTask(
                    meta = meta,
                    status = status,
                    progress = info?.progress?.getInt(SongDownloadWorker.KEY_PROGRESS_PERCENT, 0) ?: 0,
                    failureReason = info?.outputData?.getString(SongDownloadWorker.KEY_REASON) ?: meta.failureReason,
                    skipped = info?.outputData?.getBoolean(SongDownloadWorker.KEY_SKIPPED, false) == true || meta.skipped
                )
            }
        }

    // 重新下载失败的任务，沿用原音质与所属批次
    suspend fun retry(tasks: List<DownloadTask>) =
        restart(tasks.filter { it.status == DownloadTaskStatus.FAILED })

    // 继续已暂停的任务，已下载的部分由断点续传接上
    suspend fun resume(tasks: List<DownloadTask>) =
        restart(tasks.filter { it.status == DownloadTaskStatus.PAUSED })

    // 暂停进行中的任务：先记录暂停状态再取消，Worker 会保留断点文件
    suspend fun pause(tasks: List<DownloadTask>) {
        val active = tasks.filter { it.isActive }
        if (active.isEmpty()) return
        taskStore.markState(active.mapTo(HashSet()) { it.meta.workId }, PersistedTaskState.PAUSED)
        active.forEach { workManager.cancelWorkById(UUID.fromString(it.meta.workId)) }
    }

    // 取消未结束的任务，并删除断点文件
    suspend fun cancel(tasks: List<DownloadTask>) {
        val unfinished = tasks.filter { it.isUnfinished }
        if (unfinished.isEmpty()) return
        taskStore.remove(unfinished.mapTo(HashSet()) { it.meta.workId })
        unfinished.forEach { workManager.cancelWorkById(UUID.fromString(it.meta.workId)) }
        // 等待 Worker 响应取消后再删，避免其仍在写入；放在管理器作用域，面板关闭也不影响清理
        scope.launch {
            delay(CANCEL_SETTLE_MS)
            unfinished.forEach { task -> SongDownloadWorker.partialFilesOf(context, task.meta.songId).forEach { it.delete() } }
        }
    }

    // 优先下载：移到排队最前，按传入顺序排列；正在下载的不受影响
    suspend fun prioritize(tasks: List<DownloadTask>) {
        val targets = tasks.filter { it.isUnfinished }
        if (targets.isEmpty()) return
        val front = taskStore.tasks.first().minOfOrNull { it.queueOrder } ?: System.currentTimeMillis()
        taskStore.setOrders(
            targets.mapIndexed { index, task -> task.meta.workId to front - targets.size + index }.toMap()
        )
    }

    // 按拖动后的顺序重排未结束的任务，沿用它们原有的排队位置区间
    suspend fun reorder(orderedWorkIds: List<String>) {
        if (orderedWorkIds.size < 2) return
        val ids = orderedWorkIds.toHashSet()
        val slots = taskStore.tasks.first().filter { it.workId in ids }.map { it.queueOrder }.sorted()
        if (slots.size != orderedWorkIds.size) return
        // 原位置可能重复（同一毫秒创建），按首个位置依次递增保证严格有序
        val base = slots.first()
        taskStore.setOrders(orderedWorkIds.mapIndexed { index, id -> id to base + index }.toMap())
    }

    // 从列表移除已结束的任务，不影响已下载的文件
    suspend fun dismiss(tasks: List<DownloadTask>) {
        taskStore.remove(tasks.filterNot { it.isUnfinished }.mapTo(HashSet()) { it.meta.workId })
    }

    private suspend fun restart(tasks: List<DownloadTask>) {
        if (tasks.isEmpty()) return
        val requests = tasks.map { task ->
            val meta = task.meta
            meta to buildRequest(meta.toTrackInfo(), meta.level, meta.batchTag, meta.batchLabel)
        }
        val now = System.currentTimeMillis()
        taskStore.add(
            requests.map { (meta, request) ->
                // 继续或重试沿用原排队位置，不排到队尾
                meta.copy(
                    workId = request.id.toString(),
                    createdAt = now,
                    state = null,
                    failureReason = null,
                    skipped = false,
                    finishedAt = 0,
                    sortOrder = meta.queueOrder
                )
            }
        )
        requests.forEach { (meta, request) ->
            workManager.enqueueUniqueWork(uniqueWorkName(meta.songId), ExistingWorkPolicy.REPLACE, request)
        }
    }

    // 清理长期未继续的断点文件
    private fun deleteStalePartials() {
        val threshold = System.currentTimeMillis() - PARTIAL_MAX_AGE_MS
        context.cacheDir.listFiles { file -> file.name.startsWith("dl_") && file.name.endsWith(".part") }
            ?.filter { it.lastModified() < threshold }
            ?.forEach { it.delete() }
    }

    fun observeBatch(batchTag: String): Flow<List<WorkInfo>> = workManager.getWorkInfosByTagFlow(batchTag)

    fun observeSingle(songId: Long): Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(songId))

    private suspend fun currentWorkInfos(): List<WorkInfo> = withContext(Dispatchers.IO) {
        runCatching { workManager.getWorkInfosByTag(TAG_DOWNLOAD).get() }.getOrDefault(emptyList())
    }

    private fun songIdOf(info: WorkInfo): Long? =
        info.tags.firstOrNull { it.startsWith(TAG_SONG_PREFIX) }?.removePrefix(TAG_SONG_PREFIX)?.toLongOrNull()

    private fun metaFor(
        workId: UUID,
        track: DownloadTrackInfo,
        level: String,
        batchTag: String?,
        batchLabel: String?
    ) = DownloadTaskMeta(
        workId = workId.toString(),
        songId = track.songId,
        songName = track.songName,
        artistName = track.artistName,
        albumName = track.albumName,
        coverUrl = track.coverUrl,
        albumYear = track.albumYear,
        level = level,
        batchTag = batchTag,
        batchLabel = batchLabel,
        createdAt = System.currentTimeMillis()
    )

    private fun buildRequest(
        track: DownloadTrackInfo,
        level: String,
        batchTag: String? = null,
        batchLabel: String? = null
    ) = OneTimeWorkRequestBuilder<SongDownloadWorker>()
        .setInputData(
            SongDownloadWorker.buildInputData(
                track.songId, track.songName, track.artistName, level,
                track.albumName, track.coverUrl, track.albumYear, batchTag, batchLabel, track.durationMs
            )
        )
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .addTag(TAG_DOWNLOAD)
        .addTag("$TAG_SONG_PREFIX${track.songId}")
        .apply { batchTag?.let { addTag(it) } }
        .build()
}
