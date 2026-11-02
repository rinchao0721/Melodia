package com.lin0721.linmusic.feature.cloud.upload

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// 上传队列的唯一状态源。CloudUploadRunner 是唯一的写入方，各端进度展示只读订阅 state
class CloudUploadManager {

    private val _state = MutableStateFlow<List<UploadTask>>(emptyList())
    val state: StateFlow<List<UploadTask>> = _state.asStateFlow()

    // 某个文件成功发布，供云盘页订阅后立即刷新列表第一页
    private val _publishedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val publishedEvent: SharedFlow<Unit> = _publishedEvent.asSharedFlow()

    fun enqueue(tasks: List<UploadTask>) {
        _state.update { it + tasks }
    }

    fun updateTask(id: String, transform: (UploadTask) -> UploadTask) {
        _state.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    // 重试保留 md5/checkedSongId 等缓存字段，只重置执行状态
    fun retryTask(id: String) {
        updateTask(id) { it.copy(status = UploadStatus.QUEUED, errorMessage = null, progress = 0f) }
    }

    fun taskById(id: String): UploadTask? = _state.value.find { it.id == id }

    fun notifyPublished() {
        _publishedEvent.tryEmit(Unit)
    }

    fun clearFinished() {
        _state.update { list -> list.filterNot { it.status == UploadStatus.SUCCESS } }
    }

    fun clearAll() {
        _state.value = emptyList()
    }

    fun hasActiveTasks(): Boolean = _state.value.any { it.isActive }
}
