package com.lin0721.linmusic.core.download

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.TrackId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "DownloadTaskStore"

// 已结束任务最多保留的条数，超出后丢弃最早结束的
private const val MAX_FINISHED_TASKS = 500

private val Context.downloadTaskDataStore by preferencesDataStore(name = "download_tasks")

// 持久化的任务状态：WorkManager 会定期清理已结束的任务，结束与暂停状态需自行保存
@Serializable
enum class PersistedTaskState { PAUSED, SUCCEEDED, FAILED }

// 下载任务元数据：WorkInfo 不携带入参，歌名、封面、歌单等展示信息在入队时单独保存
@Serializable
data class DownloadTaskMeta(
    val workId: String,
    val songId: Long,
    val songName: String,
    val artistName: String,
    val albumName: String = "",
    val coverUrl: String? = null,
    val albumYear: Int = 0,
    val level: String,
    val batchTag: String? = null,
    val batchLabel: String? = null,
    val createdAt: Long,
    val state: PersistedTaskState? = null,
    val failureReason: String? = null,
    val skipped: Boolean = false,
    val finishedAt: Long = 0,
    // 用户调整过的排队顺序，0 表示按创建时间排队
    val sortOrder: Long = 0,
    // P2 跨平台歌曲标识；老数据缺失时按 songId 推导网易云 TrackId
    val trackId: TrackId? = null,
    // 播客节目标识与所属电台 id
    val isPodcast: Boolean = false,
    val radioId: Long = 0L,
    // P2 酷狗补充元数据
    val kugouMeta: KugouTrackMeta? = null
) {
    // 排队顺序，越小越先下载
    val queueOrder: Long get() = if (sortOrder != 0L) sortOrder else createdAt

    val effectiveTrackId: TrackId
        get() = trackId ?: TrackId.netease(songId.coerceAtLeast(0L))

    fun toTrackInfo() = DownloadTrackInfo(
        songId = songId,
        songName = songName,
        artistName = artistName,
        albumName = albumName,
        coverUrl = coverUrl,
        albumYear = albumYear,
        trackId = trackId,
        kugouMeta = kugouMeta,
        isPodcast = isPodcast,
        radioId = radioId
    )
}

// 用户主动发起的下载任务列表持久化，供下载管理面板展示、暂停与重试
class DownloadTaskStore(private val context: Context) {

    companion object {
        private val KEY_TASKS = stringPreferencesKey("download_tasks")
        private val json = Json { ignoreUnknownKeys = true }
    }

    val tasks: Flow<List<DownloadTaskMeta>> = context.downloadTaskDataStore.data.map { prefs ->
        decode(prefs[KEY_TASKS])
    }

    // 追加任务；同一首歌只保留最新一条，避免重试或重新下载后列表出现重复
    suspend fun add(metas: List<DownloadTaskMeta>) {
        if (metas.isEmpty()) return
        val incomingTrackIds = metas.mapTo(HashSet()) { it.effectiveTrackId }
        update { list -> trimFinished(list.filterNot { it.effectiveTrackId in incomingTrackIds } + metas) }
    }

    suspend fun remove(workIds: Set<String>) {
        if (workIds.isEmpty()) return
        update { list -> list.filterNot { it.workId in workIds } }
    }

    // 记录任务的暂停或结束状态
    suspend fun markState(
        workIds: Set<String>,
        state: PersistedTaskState,
        failureReason: String? = null,
        skipped: Boolean = false
    ) {
        if (workIds.isEmpty()) return
        val now = System.currentTimeMillis()
        update { list ->
            trimFinished(
                list.map { meta ->
                    if (meta.workId !in workIds) meta
                    else meta.copy(
                        state = state,
                        failureReason = failureReason,
                        skipped = skipped,
                        finishedAt = if (state == PersistedTaskState.PAUSED) 0 else now
                    )
                }
            )
        }
    }

    // 更新排队顺序
    suspend fun setOrders(orders: Map<String, Long>) {
        if (orders.isEmpty()) return
        update { list -> list.map { meta -> orders[meta.workId]?.let { meta.copy(sortOrder = it) } ?: meta } }
    }

    // 清理既不在 WorkManager 中、也没有保存状态的任务（KEEP 未入队的重复记录等）；
    // 刚入队的记录可能还未落库，按创建时间留出宽限
    suspend fun retainExisting(existingWorkIds: Set<String>, graceBefore: Long) {
        update { list ->
            list.filter { it.workId in existingWorkIds || it.state != null || it.createdAt >= graceBefore }
        }
    }

    private fun trimFinished(list: List<DownloadTaskMeta>): List<DownloadTaskMeta> {
        val finished = list.filter { it.state == PersistedTaskState.SUCCEEDED || it.state == PersistedTaskState.FAILED }
        if (finished.size <= MAX_FINISHED_TASKS) return list
        val dropped = finished.sortedBy { it.finishedAt }
            .take(finished.size - MAX_FINISHED_TASKS)
            .mapTo(HashSet()) { it.workId }
        return list.filterNot { it.workId in dropped }
    }

    private suspend fun update(transform: (List<DownloadTaskMeta>) -> List<DownloadTaskMeta>) {
        context.downloadTaskDataStore.edit { prefs ->
            prefs[KEY_TASKS] = json.encodeToString(transform(decode(prefs[KEY_TASKS])))
        }
    }

    private fun decode(raw: String?): List<DownloadTaskMeta> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<DownloadTaskMeta>>(raw) }
            .onFailure { AppLogger.w(TAG, "下载任务反序列化失败", it) }
            .getOrDefault(emptyList())
    }
}
