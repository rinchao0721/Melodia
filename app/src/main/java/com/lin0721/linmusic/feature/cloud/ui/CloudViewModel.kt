package com.lin0721.linmusic.feature.cloud.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.feature.cloud.data.CloudRepository
import com.lin0721.linmusic.feature.cloud.domain.CloudSong
import com.lin0721.linmusic.feature.cloud.upload.ACTION_CLOUD_UPLOAD_ENQUEUE
import com.lin0721.linmusic.feature.cloud.upload.ACTION_CLOUD_UPLOAD_RETRY
import com.lin0721.linmusic.feature.cloud.upload.CloudUploadManager
import com.lin0721.linmusic.feature.cloud.upload.CloudUploadService
import com.lin0721.linmusic.feature.cloud.upload.EXTRA_CLOUD_UPLOAD_TASK_ID
import com.lin0721.linmusic.feature.cloud.upload.EXTRA_CLOUD_UPLOAD_URIS
import com.lin0721.linmusic.feature.cloud.upload.UploadTask
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "CloudViewModel"
private const val PAGE_SIZE = 30
private const val MATCH_SEARCH_DEBOUNCE_MS = 400L

class CloudViewModel(
    private val cloudRepository: CloudRepository,
    private val searchRepository: SearchRepository,
    private val songCollectDelegate: SongCollectDelegate,
    private val loadLikedSongIdsUseCase: LoadLikedSongIdsUseCase,
    private val songLikeRepository: SongLikeRepository,
    private val uploadManager: CloudUploadManager,
    private val userPreferences: UserPreferences,
    val playerManager: PlayerManager,
    private val resourceProvider: ResourceProvider,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<CloudUiState>(CloudUiState.Loading)
    val uiState: StateFlow<CloudUiState> = _uiState.asStateFlow()

    private val _overlay = MutableStateFlow<CloudOverlay>(CloudOverlay.Hidden)
    val overlay: StateFlow<CloudOverlay> = _overlay.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    val likedSongIds: StateFlow<Set<Long>> = songLikeRepository.likedSongIds

    val collectState: StateFlow<PlaylistCollectState> = songCollectDelegate.state
    val uploadTasks: StateFlow<List<UploadTask>> = uploadManager.state

    private var matchSearchJob: Job? = null

    init {
        load()
        loadLikedSongIds()
        // 每个文件发布成功就立刻刷一次列表第一页，不等整批上传结束
        viewModelScope.launch {
            uploadManager.publishedEvent.collect { load() }
        }
    }

    // ======================= 上传 =======================

    fun startUpload(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val intent = Intent(context, CloudUploadService::class.java).apply {
            action = ACTION_CLOUD_UPLOAD_ENQUEUE
            putParcelableArrayListExtra(EXTRA_CLOUD_UPLOAD_URIS, ArrayList(uris))
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun retryUpload(taskId: String) {
        val intent = Intent(context, CloudUploadService::class.java).apply {
            action = ACTION_CLOUD_UPLOAD_RETRY
            putExtra(EXTRA_CLOUD_UPLOAD_TASK_ID, taskId)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private fun loadLikedSongIds() {
        viewModelScope.launch {
            loadLikedSongIdsUseCase()
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = CloudUiState.Loading
            cloudRepository.getCloudSongs(limit = PAGE_SIZE, offset = 0).first()
                .onSuccess { page ->
                    _uiState.value = CloudUiState.Success(
                        songs = page.songs,
                        quota = page.quota,
                        hasMore = page.hasMore
                    )
                }
                .onFailure { e ->
                    _uiState.value = CloudUiState.Error(e.toUserMessage(resourceProvider))
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value as? CloudUiState.Success ?: return
        if (!state.hasMore || state.isLoadingMore) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true)
            cloudRepository.getCloudSongs(limit = PAGE_SIZE, offset = state.songs.size).first()
                .onSuccess { page ->
                    _uiState.value = state.copy(
                        songs = state.songs + page.songs,
                        quota = page.quota,
                        hasMore = page.hasMore,
                        isLoadingMore = false
                    )
                }
                .onFailure { e ->
                    AppLogger.w(TAG, "加载更多失败", e)
                    _uiState.value = state.copy(isLoadingMore = false)
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
        }
    }

    // 以当前已加载的云盘歌曲为播放队列，从点击项开始播
    fun playSong(song: CloudSong) {
        val state = _uiState.value as? CloudUiState.Success ?: return
        val queueItems = state.songs.map { item ->
            QueueItem(
                songId = item.songId,
                title = item.name,
                artist = item.artist,
                coverUrl = item.coverUrl.orEmpty()
            )
        }
        val startIndex = state.songs.indexOfFirst { it.songId == song.songId }.coerceAtLeast(0)
        playerManager.playQueue(queueItems, startIndex, "我的云盘")
    }

    // ======================= 三点菜单 / 删除 =======================

    fun openOptions(song: CloudSong) {
        _overlay.value = CloudOverlay.Options(song)
    }

    fun dismissOverlay() {
        matchSearchJob?.cancel()
        _overlay.value = CloudOverlay.Hidden
    }

    // 喜欢是即时切换，不像添加到歌单/重新匹配那样需要跳到下一个浮层，直接收起面板
    fun toggleLike() {
        val song = (_overlay.value as? CloudOverlay.Options)?.song ?: return
        val isLike = song.songId !in songLikeRepository.likedSongIds.value
        _overlay.value = CloudOverlay.Hidden

        viewModelScope.launch {
            songLikeRepository.likeSong(song.songId, isLike).first()
                .onSuccess {
                    _toastEvent.emit(if (isLike) "已添加到我喜欢的音乐" else "已从我喜欢的音乐中移除")
                }
                .onFailure { e ->
                    AppLogger.w(TAG, "切换喜欢状态失败", e)
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
        }
    }

    fun requestDelete() {
        val song = (_overlay.value as? CloudOverlay.Options)?.song ?: return
        _overlay.value = CloudOverlay.DeleteConfirm(song)
    }

    fun confirmDelete() {
        val current = _overlay.value as? CloudOverlay.DeleteConfirm ?: return
        if (current.isDeleting) return

        viewModelScope.launch {
            _overlay.value = current.copy(isDeleting = true)
            cloudRepository.deleteCloudSong(current.song.songId).first()
                .onSuccess {
                    removeSongFromList(current.song)
                    _overlay.value = CloudOverlay.Hidden
                    _toastEvent.emit("已删除")
                }
                .onFailure { e ->
                    AppLogger.w(TAG, "删除云盘歌曲失败", e)
                    _overlay.value = current.copy(isDeleting = false)
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
        }
    }

    private fun removeSongFromList(song: CloudSong) {
        val state = _uiState.value as? CloudUiState.Success ?: return
        _uiState.value = state.copy(
            songs = state.songs.filterNot { it.songId == song.songId },
            quota = state.quota.copy(
                usedBytes = (state.quota.usedBytes - song.fileSizeBytes).coerceAtLeast(0),
                totalCount = (state.quota.totalCount - 1).coerceAtLeast(0)
            )
        )
    }

    // ======================= 重新匹配 =======================

    fun openMatch() {
        val song = (_overlay.value as? CloudOverlay.Options)?.song ?: return
        _overlay.value = CloudOverlay.Match(song = song)
    }

    fun updateMatchQuery(query: String) {
        val current = _overlay.value as? CloudOverlay.Match ?: return
        _overlay.value = current.copy(query = query, confirmTarget = null)
        matchSearchJob?.cancel()

        if (query.isBlank()) {
            _overlay.value = (_overlay.value as? CloudOverlay.Match)?.copy(results = emptyList(), isSearching = false) ?: return
            return
        }

        matchSearchJob = viewModelScope.launch {
            delay(MATCH_SEARCH_DEBOUNCE_MS)
            _overlay.value = (_overlay.value as? CloudOverlay.Match)?.copy(isSearching = true) ?: return@launch
            searchRepository.search(query, SearchType.SONG, offset = 0, limit = 20).first()
                .onSuccess { result ->
                    val tracks = result.items.filterIsInstance<SearchResultItem.SongItem>().map { it.track }
                    _overlay.value = (_overlay.value as? CloudOverlay.Match)?.copy(
                        results = tracks,
                        isSearching = false
                    ) ?: return@onSuccess
                }
                .onFailure { e ->
                    AppLogger.w(TAG, "重新匹配搜索失败", e)
                    _overlay.value = (_overlay.value as? CloudOverlay.Match)?.copy(isSearching = false) ?: return@onFailure
                }
        }
    }

    fun selectMatchCandidate(track: Track) {
        val current = _overlay.value as? CloudOverlay.Match ?: return
        _overlay.value = current.copy(confirmTarget = track)
    }

    fun cancelMatchConfirm() {
        val current = _overlay.value as? CloudOverlay.Match ?: return
        _overlay.value = current.copy(confirmTarget = null)
    }

    fun confirmMatch() {
        val current = _overlay.value as? CloudOverlay.Match ?: return
        val target = current.confirmTarget ?: return
        if (current.isMatching) return

        viewModelScope.launch {
            val uid = userPreferences.userProfile.first()?.uid ?: return@launch
            _overlay.value = current.copy(isMatching = true)
            cloudRepository.matchCloudSong(userId = uid, songId = current.song.songId, adjustSongId = target.id).first()
                .onSuccess {
                    updateSongAfterMatch(current.song, target)
                    _overlay.value = CloudOverlay.Hidden
                    _toastEvent.emit("匹配成功")
                }
                .onFailure { e ->
                    AppLogger.w(TAG, "重新匹配失败", e)
                    _overlay.value = current.copy(isMatching = false)
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
        }
    }

    // ======================= 添加到歌单 =======================

    fun openAddToPlaylist() {
        val song = (_overlay.value as? CloudOverlay.Options)?.song ?: return
        _overlay.value = CloudOverlay.AddToPlaylist(song)
        viewModelScope.launch {
            songCollectDelegate.prepare(song.songId, songLikeRepository.likedSongIds.value) { _toastEvent.emit(it) }
        }
    }

    fun saveAddToPlaylist(songId: Long, items: List<PlaylistCollectItem>) {
        viewModelScope.launch {
            songCollectDelegate.save(
                songId = songId,
                items = items,
                likedSongIds = songLikeRepository.likedSongIds.value,
                onToast = { _toastEvent.emit(it) },
                onLikedChanged = { newLiked ->
                    songLikeRepository.syncLikedSongIds(newLiked)
                }
            )
        }
    }

    fun createPlaylistAndAddSong(name: String, songId: Long) {
        viewModelScope.launch {
            songCollectDelegate.createAndAdd(name, songId, songLikeRepository.likedSongIds.value) { _toastEvent.emit(it) }
        }
    }

    // 匹配接口不回显新数据，直接用选中的候选曲目信息更新本地这一行
    private fun updateSongAfterMatch(song: CloudSong, target: Track) {
        val state = _uiState.value as? CloudUiState.Success ?: return
        _uiState.value = state.copy(
            songs = state.songs.map {
                if (it.songId == song.songId) {
                    it.copy(
                        name = target.name,
                        artist = target.ar.joinToString(" / ") { artist -> artist.name },
                        coverUrl = target.al.picUrl.ifBlank { null },
                        isUnmatched = false
                    )
                } else {
                    it
                }
            }
        )
    }
}
