package com.lin0721.linmusic.desktop.data.cloud

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.cloud.data.CloudRepository
import com.lin0721.linmusic.feature.cloud.domain.CloudQuota
import com.lin0721.linmusic.feature.cloud.domain.CloudSong
import com.lin0721.linmusic.feature.cloud.upload.CloudUploadManager
import com.lin0721.linmusic.feature.cloud.upload.CloudUploadRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "CloudLibrary"
private const val PAGE_SIZE = 100
private const val REFRESH_DEBOUNCE_MS = 1_500L

data class CloudLibraryState(
    val songs: List<CloudSong> = emptyList(),
    val quota: CloudQuota? = null,
    val loading: Boolean = false,
    // 已拉完全部分页
    val complete: Boolean = false,
    val failed: Boolean = false
) {
    val loaded: Boolean get() = quota != null
}

// 当前账号的云盘歌曲，侧栏入口与云盘页共用一份
class CloudLibrary(
    private val repository: CloudRepository,
    private val userPreferences: UserPreferences,
    uploadManager: CloudUploadManager,
    private val uploadRunner: CloudUploadRunner,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(CloudLibraryState())
    val state: StateFlow<CloudLibraryState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var uid: Long? = null
    // 有页面要看过云盘后，换号登录也自动加载
    private var wanted = false

    init {
        scope.launch {
            userPreferences.userProfile.map { it?.uid }.distinctUntilChanged().collect { newUid ->
                val previous = uid
                uid = newUid
                loadJob?.cancel()
                loadJob = null
                _state.value = CloudLibraryState()
                // 退出或切换账号时丢弃未完成的上传
                if (previous != null) uploadRunner.clear()
                if (wanted && newUid != null) refresh()
            }
        }
        scope.launch {
            @OptIn(FlowPreview::class)
            uploadManager.publishedEvent.debounce(REFRESH_DEBOUNCE_MS).collect {
                if (_state.value.loaded) refresh()
            }
        }
    }

    fun ensureLoaded() {
        wanted = true
        val current = _state.value
        if (uid != null && !current.loaded && !current.loading) refresh()
    }

    // 逐页拉取直到取完，边拉边更新，便于页面尽早显示
    fun refresh() {
        if (uid == null) return
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update { it.copy(loading = true, failed = false) }
            val songs = mutableListOf<CloudSong>()
            while (true) {
                val result = repository.getCloudSongs(limit = PAGE_SIZE, offset = songs.size).first()
                val page = result.getOrElse { e ->
                    AppLogger.w(TAG, "拉取云盘歌曲失败 offset=${songs.size}", e)
                    _state.update { it.copy(loading = false, failed = !it.loaded) }
                    return@launch
                }
                songs += page.songs
                val complete = !page.hasMore || page.songs.isEmpty()
                _state.value = CloudLibraryState(
                    songs = songs.distinctBy { it.songId },
                    quota = page.quota,
                    loading = !complete,
                    complete = complete
                )
                if (complete) break
            }
        }
    }

    suspend fun delete(song: CloudSong): Result<Unit> {
        val result = repository.deleteCloudSong(song.songId).first()
        result.onSuccess {
            _state.update { state ->
                state.copy(
                    songs = state.songs.filterNot { it.songId == song.songId },
                    quota = state.quota?.let { quota ->
                        quota.copy(
                            usedBytes = (quota.usedBytes - song.fileSizeBytes).coerceAtLeast(0),
                            totalCount = (quota.totalCount - 1).coerceAtLeast(0)
                        )
                    }
                )
            }
        }
        return result
    }

    suspend fun match(song: CloudSong, target: Track): Result<Unit> {
        val userId = uid ?: return Result.failure(IllegalStateException("未登录"))
        val result = repository.matchCloudSong(userId = userId, songId = song.songId, adjustSongId = target.id).first()
        result.onSuccess {
            _state.update { state ->
                state.copy(songs = state.songs.map {
                    if (it.songId != song.songId) {
                        it
                    } else {
                        it.copy(
                            name = target.name,
                            artist = target.ar.joinToString(" / ") { artist -> artist.name },
                            album = target.al.name.ifBlank { it.album },
                            coverUrl = target.al.picUrl.ifBlank { null },
                            durationMs = if (target.dt > 0) target.dt else it.durationMs,
                            isUnmatched = false,
                            playSongId = target.id
                        )
                    }
                })
            }
        }
        return result
    }
}
