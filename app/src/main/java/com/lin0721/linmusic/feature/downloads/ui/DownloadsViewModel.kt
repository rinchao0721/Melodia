package com.lin0721.linmusic.feature.downloads.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.download.DownloadPreferences
import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.download.DownloadTask
import com.lin0721.linmusic.core.download.SongDownloadManager
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// 侧边栏「下载管理」页：下载任务概览与已下载歌曲
class DownloadsViewModel(
    private val downloadPreferences: DownloadPreferences,
    private val songDownloadManager: SongDownloadManager,
    private val playerManager: PlaybackController
) : ViewModel() {

    // 已下载歌曲，按下载时间倒序；文件已被删除的记录在校验时清理，null 表示仍在加载
    @OptIn(ExperimentalCoroutinesApi::class)
    val downloads: StateFlow<List<DownloadRecord>?> = downloadPreferences.records
        .mapLatest { records ->
            downloadPreferences.findVerifiedRecords(records.map { it.songId })
                .sortedByDescending { it.downloadedAt }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tasks: StateFlow<List<DownloadTask>> = songDownloadManager.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    // 删除已下载歌曲及其本地文件
    fun delete(record: DownloadRecord) {
        viewModelScope.launch {
            val deleted = downloadPreferences.deleteDownload(record)
            _toastEvent.emit(if (deleted) "已删除「${record.displayName()}」" else "删除失败，可在系统文件管理中删除")
        }
    }

    // 以已下载歌曲为播放队列，从点击项开始播；已下载歌曲由播放器优先走本地文件
    fun play(record: DownloadRecord? = null) {
        val records = downloads.value.orEmpty()
        if (records.isEmpty()) return
        val queue = records.map { it.toQueueItem() }
        val startIndex = record?.let { target -> records.indexOfFirst { it.songId == target.songId } }
            ?.coerceAtLeast(0) ?: 0
        playerManager.playQueue(queue, startIndex, PLAY_CONTEXT)
    }

    private fun DownloadRecord.toQueueItem() = QueueItem(
        songId = songId,
        title = displayName(),
        artist = artistName,
        coverUrl = ""
    )

    companion object {
        private const val PLAY_CONTEXT = "已下载"
    }
}

// 早期版本的下载记录没有歌名，兜底展示
fun DownloadRecord.displayName(): String = songName.ifBlank { "未知歌曲 $songId" }
