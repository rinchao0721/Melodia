package com.lin0721.linmusic.feature.cloud.upload

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.cloud.data.CloudInfoRequest
import com.lin0721.linmusic.feature.cloud.data.CloudPublishRequest
import com.lin0721.linmusic.feature.cloud.data.CloudUploadApi
import com.lin0721.linmusic.feature.cloud.data.CloudUploadCheckRequest
import com.lin0721.linmusic.feature.cloud.data.NosTokenRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

private const val TAG = "CloudUploadRunner"
private const val NOS_BUCKET = "jd-musicrep-privatecloud-audio-public"
// 参考实现固定值：客户端不感知真实码率，统一按最高档申请（服务端会按文件实际码率处理）
private const val UPLOAD_BITRATE = "999000"
private const val PROGRESS_STEP = 0.01f

data class UploadBatchResult(val succeeded: Int, val failed: Int)

// 云盘上传串行队列，生命周期跟随应用而非页面
class CloudUploadRunner(
    private val manager: CloudUploadManager,
    private val api: CloudUploadApi,
    private val nos: NosTransport,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val lock = Any()
    private val pending = ArrayDeque<String>()
    private var job: Job? = null
    private var batchSucceeded = 0
    private var batchFailed = 0

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    // 队列清空时发出本批结果
    private val _batchFinished = MutableSharedFlow<UploadBatchResult>(extraBufferCapacity = 1)
    val batchFinished: SharedFlow<UploadBatchResult> = _batchFinished.asSharedFlow()

    // 返回实际入队数；同一来源仍在排队或上传中时跳过
    fun enqueue(sources: List<UploadSource>): Int = synchronized(lock) {
        val activeKeys = manager.state.value.filter { it.isActive }.mapTo(HashSet()) { it.source.key }
        val tasks = sources.distinctBy { it.key }
            .filter { it.key !in activeKeys }
            .map { UploadTask(source = it, fileName = it.fileName) }
        if (tasks.isEmpty()) return@synchronized 0
        manager.enqueue(tasks)
        tasks.forEach { pending.addLast(it.id) }
        ensureProcessingLocked()
        tasks.size
    }

    fun retry(taskId: String) = synchronized(lock) {
        val task = manager.taskById(taskId) ?: return@synchronized
        if (task.status != UploadStatus.FAILED) return@synchronized
        manager.retryTask(taskId)
        pending.addLast(taskId)
        ensureProcessingLocked()
    }

    // 退出登录时中断并清空
    fun clear() = synchronized(lock) {
        job?.cancel()
        job = null
        pending.clear()
        batchSucceeded = 0
        batchFailed = 0
        manager.clearAll()
        _running.value = false
    }

    private fun ensureProcessingLocked() {
        if (job != null) return
        _running.value = true
        job = scope.launch {
            while (true) {
                val id = synchronized(lock) {
                    pending.removeFirstOrNull() ?: run {
                        finishBatchLocked()
                        null
                    }
                } ?: break
                val task = manager.taskById(id) ?: continue
                val ok = runPipeline(task)
                synchronized(lock) { if (ok) batchSucceeded++ else batchFailed++ }
            }
        }
    }

    private fun finishBatchLocked() {
        job = null
        _running.value = false
        val result = UploadBatchResult(batchSucceeded, batchFailed)
        batchSucceeded = 0
        batchFailed = 0
        if (result.succeeded + result.failed > 0) _batchFinished.tryEmit(result)
    }

    // 每一步成功即写回 manager，重试时可从已缓存的阶段继续
    private suspend fun runPipeline(initialTask: UploadTask): Boolean {
        var task = initialTask
        fun apply(transform: (UploadTask) -> UploadTask) {
            manager.updateTask(task.id, transform)
            task = manager.taskById(task.id) ?: task
        }

        return try {
            val source = task.source
            if (task.md5 == null || task.fileSize == null) {
                apply { it.copy(status = UploadStatus.CHECKING) }
                val fileSize = source.size()
                val md5 = source.openStream().use(::streamMd5)
                val metadata = source.readMetadata()
                apply { it.copy(md5 = md5, fileSize = fileSize, metadata = metadata) }
            }

            val md5 = requireNotNull(task.md5)
            val fileSize = requireNotNull(task.fileSize)
            if (fileSize <= 0L) throw IOException("文件为空或无法读取")
            val ext = fileExtension(task.fileName)

            if (task.checkedSongId == null) {
                apply { it.copy(status = UploadStatus.CHECKING) }
                val checkResp = api.checkUpload(
                    CloudUploadCheckRequest(bitrate = UPLOAD_BITRATE, length = fileSize, md5 = md5)
                )
                if (!checkResp.isSuccess) throw IOException("查重失败 code=${checkResp.code}")
                apply { it.copy(checkedSongId = checkResp.songId, needUpload = checkResp.needUpload) }
            }

            val checkedSongId = requireNotNull(task.checkedSongId)
            val needUpload = task.needUpload ?: true

            // token 不缓存，每次重新申请
            apply { it.copy(status = UploadStatus.ALLOCATING_TOKEN, progress = 0f) }
            val tokenResp = api.allocNosToken(NosTokenRequest(ext = ext, filename = task.fileName, md5 = md5))
            val tokenResult = tokenResp.result ?: throw IOException("申请上传凭证失败")
            val bucket = tokenResult.bucket.ifBlank { NOS_BUCKET }

            if (needUpload) {
                val host = nos.fetchUploadHosts(bucket).firstOrNull() ?: throw IOException("获取上传服务器地址失败")
                apply { it.copy(status = UploadStatus.UPLOADING) }
                nos.uploadBytes(
                    host = host,
                    bucket = bucket,
                    objectKey = tokenResult.objectKey,
                    token = tokenResult.token,
                    md5 = md5,
                    mimeType = source.mimeType,
                    contentLength = fileSize,
                    openStream = source::openStream,
                    onProgress = { progress ->
                        if (progress >= 1f || progress - task.progress >= PROGRESS_STEP) {
                            apply { it.copy(progress = progress) }
                        }
                    }
                )
            } else {
                AppLogger.d(TAG, "文件已存在于服务器，跳过字节上传 file=${task.fileName}")
            }

            apply { it.copy(status = UploadStatus.REGISTERING, progress = 1f) }
            val metadata = task.metadata
            val infoResp = api.registerCloudInfo(
                CloudInfoRequest(
                    md5 = md5,
                    songid = checkedSongId,
                    filename = task.fileName,
                    song = metadata?.title ?: task.fileName.substringBeforeLast('.', task.fileName),
                    album = metadata?.album ?: "未知专辑",
                    artist = metadata?.artist ?: "未知艺术家",
                    bitrate = UPLOAD_BITRATE,
                    resourceId = tokenResult.resourceId
                )
            )
            if (!infoResp.isSuccess) throw IOException("登记歌曲信息失败 code=${infoResp.code}")

            apply { it.copy(status = UploadStatus.PUBLISHING) }
            val pubResp = api.publishCloud(CloudPublishRequest(songid = infoResp.songId))
            if (!pubResp.isSuccess) throw IOException("发布失败 code=${pubResp.code}")

            apply { it.copy(status = UploadStatus.SUCCESS, progress = 1f) }
            manager.notifyPublished()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "上传失败 file=${task.fileName}", e)
            apply { it.copy(status = UploadStatus.FAILED, errorMessage = e.message ?: "上传失败") }
            false
        }
    }
}
