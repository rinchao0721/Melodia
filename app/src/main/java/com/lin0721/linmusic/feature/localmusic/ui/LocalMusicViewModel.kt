package com.lin0721.linmusic.feature.localmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.localmusic.LocalMusicRepository
import com.lin0721.linmusic.core.localmusic.LocalTrack
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.feature.player.data.PlayerRepository
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import android.net.Uri
import com.lin0721.linmusic.core.localmusic.LocalMusicImporter
import com.lin0721.linmusic.core.localmusic.LocalTrackSource
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// 本地曲目操作菜单状态
sealed class LocalMusicMenuState {
    abstract val track: LocalTrack
    data class Matched(override val track: LocalTrack, val fullTrack: Track) : LocalMusicMenuState()
    data class Unmatched(override val track: LocalTrack, val coverUrl: String? = null) : LocalMusicMenuState()
}

sealed class LocalMusicUiState {
    data object Loading : LocalMusicUiState()
    data class NeedsPermission(val permission: String) : LocalMusicUiState()
    data class Error(val message: String) : LocalMusicUiState()
    data class Success(
        val tracks: List<LocalTrack>,
        val availableStorageBytes: Long = 0L,
        val groupMode: LocalMusicGroupMode = LocalMusicGroupMode.SONGS,
        val sortOrder: LocalMusicSortOrder = LocalMusicSortOrder.DATE_DESC,
        val selectedGroupKey: String? = null,
        val isSearchActive: Boolean = false,
        val searchQuery: String = "",
        val selectedUris: Set<String> = emptySet(),
        val isSelectionMode: Boolean = false,
        val menuState: LocalMusicMenuState? = null,
        val detailTrack: LocalTrack? = null
    ) : LocalMusicUiState() {
        val totalSizeBytes: Long get() = tracks.sumOf { it.sizeBytes }

        val filteredTracks: List<LocalTrack> get() {
            val matched = if (searchQuery.isBlank()) {
                tracks
            } else {
                tracks.filter {
                    it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true)
                }
            }
            return sortTracks(matched, sortOrder)
        }

        val groups: List<LocalMusicGroup> get() = groupTracks(filteredTracks, groupMode)

        // 当前可播放曲目列表
        val currentTrackList: List<LocalTrack>? get() = when {
            groupMode == LocalMusicGroupMode.SONGS -> filteredTracks
            selectedGroupKey != null -> groups.firstOrNull { it.key == selectedGroupKey }?.tracks
            else -> null
        }
    }
}

private const val PLAY_CONTEXT = "本地音乐"

class LocalMusicViewModel(
    private val repository: LocalMusicRepository,
    private val importer: LocalMusicImporter,
    private val playerManager: PlayerManager,
    private val playerRepository: PlayerRepository,
    private val songCollectDelegate: SongCollectDelegate,
    private val loadLikedSongIdsUseCase: LoadLikedSongIdsUseCase,
    private val songLikeRepository: SongLikeRepository,
    private val syncProfileAfterLoginUseCase: SyncProfileAfterLoginUseCase,
    private val resourceProvider: ResourceProvider,
    userPreferences: UserPreferences
) : ViewModel() {

    val userProfile: StateFlow<UserProfile?> = userPreferences.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _likedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val likedSongIds: StateFlow<Set<Long>> = _likedSongIds.asStateFlow()

    val collectState: StateFlow<PlaylistCollectState> = songCollectDelegate.state

    private val _uiState = MutableStateFlow<LocalMusicUiState>(LocalMusicUiState.Loading)
    val uiState: StateFlow<LocalMusicUiState> = _uiState.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent = _toastEvent.asSharedFlow()

    init {
        loadLikedSongIds()
    }

    fun loadLikedSongIds() {
        viewModelScope.launch {
            loadLikedSongIdsUseCase()?.let { _likedSongIds.value = it }
        }
    }

    fun handleLoginSuccess(cookies: String) {
        viewModelScope.launch {
            if (syncProfileAfterLoginUseCase(cookies) == null) return@launch
            _toastEvent.emit("登录成功，正在同步数据...")
            loadLikedSongIds()
        }
    }

    // 检查权限并加载
    fun checkPermissionAndLoad() {
        if (!repository.hasPermission()) {
            _uiState.value = LocalMusicUiState.NeedsPermission(repository.requiredPermission())
            return
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = LocalMusicUiState.Loading
            runCatching { repository.scan() }
                .onSuccess { tracks ->
                    _uiState.value = LocalMusicUiState.Success(
                        tracks = tracks,
                        availableStorageBytes = repository.availableStorageBytes()
                    )
                }
                .onFailure { _uiState.value = LocalMusicUiState.Error(it.message ?: "扫描本地音乐失败") }
        }
    }

    fun setSortOrder(order: LocalMusicSortOrder) {
        val state = currentSuccess() ?: return
        _uiState.value = state.copy(sortOrder = order)
    }

    fun setGroupMode(mode: LocalMusicGroupMode) {
        val state = currentSuccess() ?: return
        _uiState.value = state.copy(groupMode = mode, selectedGroupKey = null)
    }

    fun selectGroup(key: String) {
        val state = currentSuccess() ?: return
        _uiState.value = state.copy(selectedGroupKey = key)
    }

    // 退出分组详情返回分组列表
    fun handleBackFromGroupDetail(): Boolean {
        val state = currentSuccess() ?: return false
        if (state.selectedGroupKey == null) return false
        _uiState.value = state.copy(selectedGroupKey = null)
        return true
    }

    fun toggleSearch() {
        val state = currentSuccess() ?: return
        val next = !state.isSearchActive
        _uiState.value = state.copy(isSearchActive = next, searchQuery = if (next) state.searchQuery else "")
    }

    fun updateSearchQuery(query: String) {
        val state = currentSuccess() ?: return
        _uiState.value = state.copy(searchQuery = query)
    }

    // 播放当前全部曲目
    fun playAll() {
        val state = currentSuccess() ?: return
        val list = state.currentTrackList?.takeIf { it.isNotEmpty() } ?: return
        playerManager.playQueue(list.map(::toQueueItem), 0, PLAY_CONTEXT)
    }

    // 播放指定曲目
    fun playTrack(track: LocalTrack) {
        val state = currentSuccess() ?: return
        val list = state.currentTrackList ?: listOf(track)
        val startIndex = list.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
        playerManager.playQueue(list.map(::toQueueItem), startIndex, PLAY_CONTEXT)
        closeTrackMenu()
    }

    fun playNext(track: LocalTrack) {
        playerManager.addToPlayNext(listOf(toQueueItem(track)))
        viewModelScope.launch { _toastEvent.emit("已加入下一首播放") }
        closeTrackMenu()
    }

    fun addTrackToPlayNext(track: Track) {
        playerManager.addToPlayNext(
            listOf(
                QueueItem(
                    songId = track.id,
                    title = track.name,
                    artist = track.ar.joinToString("/") { it.name },
                    coverUrl = track.al.picUrl
                )
            )
        )
        viewModelScope.launch { _toastEvent.emit("已加入下一首播放") }
        closeTrackMenu()
    }

    private fun toQueueItem(track: LocalTrack): QueueItem = if (track.songId != null) {
        QueueItem(songId = track.songId, title = track.title, artist = track.artist, coverUrl = "")
    } else {
        // 未关联歌曲使用负数 ID 作为内部占位标识
        QueueItem(
            songId = -track.mediaStoreId,
            title = track.title,
            artist = track.artist,
            coverUrl = "",
            localUri = track.uri.toString()
        )
    }

    // 打开曲目操作菜单
    fun openTrackMenu(track: LocalTrack, coverUrl: String? = null) {
        val state = currentSuccess() ?: return
        val songId = track.songId
        if (songId == null) {
            _uiState.value = state.copy(menuState = LocalMusicMenuState.Unmatched(track, coverUrl))
            return
        }
        val initialTrack = Track(
            id = songId,
            name = track.title,
            ar = listOf(Artist(id = 0L, name = track.artist)),
            al = Album(id = 0L, name = track.album.orEmpty(), picUrl = coverUrl.orEmpty()),
            dt = track.durationMs
        )
        _uiState.value = state.copy(menuState = LocalMusicMenuState.Matched(track, initialTrack))
        viewModelScope.launch {
            playerRepository.getSongDetail(songId).collect { result ->
                val current = currentSuccess() ?: return@collect
                val currentMatched = current.menuState as? LocalMusicMenuState.Matched ?: return@collect
                if (currentMatched.track.uri != track.uri) return@collect
                result.onSuccess { fullTrack ->
                    _uiState.value = current.copy(menuState = LocalMusicMenuState.Matched(track, fullTrack))
                }
            }
        }
    }

    fun closeTrackMenu() {
        val state = currentSuccess() ?: return
        _uiState.value = state.copy(menuState = null)
    }

    fun openDetail(track: LocalTrack) {
        val state = currentSuccess() ?: return
        _uiState.value = state.copy(menuState = null, detailTrack = track)
    }

    fun closeDetail() {
        val state = currentSuccess() ?: return
        _uiState.value = state.copy(detailTrack = null)
    }

    fun toggleLikeSong(songId: Long, like: Boolean) {
        viewModelScope.launch {
            songLikeRepository.likeSong(songId, like).collect { result ->
                result.onSuccess {
                    val currentLiked = _likedSongIds.value.toMutableSet()
                    if (like) currentLiked.add(songId) else currentLiked.remove(songId)
                    _likedSongIds.value = currentLiked
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun prepareCollectDialog(songId: Long) {
        viewModelScope.launch {
            songCollectDelegate.prepare(songId, _likedSongIds.value) { _toastEvent.emit(it) }
        }
    }

    fun savePlaylistCollection(songId: Long, items: List<PlaylistCollectItem>) {
        viewModelScope.launch {
            songCollectDelegate.save(
                songId = songId,
                items = items,
                likedSongIds = _likedSongIds.value,
                onToast = { _toastEvent.emit(it) },
                onLikedChanged = { _likedSongIds.value = it }
            )
        }
    }

    fun createPlaylistAndAddSong(name: String, songId: Long) {
        viewModelScope.launch {
            songCollectDelegate.createAndAdd(name, songId, _likedSongIds.value) { _toastEvent.emit(it) }
        }
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            runCatching {
                val existing = repository.getAllExistingUris()
                importer.importFiles(uris, existing)
            }.onSuccess { result ->
                val message = when {
                    result.addedCount > 0 && result.skippedCount > 0 ->
                        "成功导入 ${result.addedCount} 首歌曲，已跳过 ${result.skippedCount} 首重复歌曲"
                    result.addedCount > 0 ->
                        "成功导入 ${result.addedCount} 首歌曲"
                    result.skippedCount > 0 ->
                        "所选歌曲已存在，已全部跳过"
                    else -> "未找到有效音频文件"
                }
                _toastEvent.emit(message)
                load()
            }.onFailure {
                AppLogger.e("LocalMusicViewModel", "导入音频文件失败", it)
                _toastEvent.emit("导入失败：${it.message ?: "未知错误"}")
            }
            _isImporting.value = false
        }
    }

    fun importFolder(treeUri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            runCatching {
                val existing = repository.getAllExistingUris()
                importer.importFolder(treeUri, existing)
            }.onSuccess { result ->
                val message = when {
                    result.addedCount > 0 && result.skippedCount > 0 ->
                        "成功导入 ${result.addedCount} 首歌曲，已跳过 ${result.skippedCount} 首重复歌曲"
                    result.addedCount > 0 ->
                        "成功导入 ${result.addedCount} 首歌曲"
                    result.skippedCount > 0 ->
                        "文件夹中歌曲已全部存在，已跳过"
                    else -> "该文件夹下未找到音频文件"
                }
                _toastEvent.emit(message)
                load()
            }.onFailure {
                AppLogger.e("LocalMusicViewModel", "导入文件夹失败", it)
                _toastEvent.emit("导入文件夹失败：${it.message ?: "未知错误"}")
            }
            _isImporting.value = false
        }
    }

    fun deleteTrack(track: LocalTrack) {
        viewModelScope.launch {
            val ok = repository.delete(track)
            val tip = if (ok) {
                if (track.source == LocalTrackSource.IMPORTED) "已从本地音乐移除" else "已删除"
            } else {
                "删除失败"
            }
            _toastEvent.emit(tip)
            if (ok) {
                val state = currentSuccess() ?: return@launch
                _uiState.value = state.copy(
                    tracks = state.tracks.filterNot { it.uri == track.uri },
                    menuState = null
                )
            }
        }
    }

    fun deleteSelected() {
        val state = currentSuccess() ?: return
        val targetUris = state.selectedUris
        viewModelScope.launch {
            val targets = state.tracks.filter { it.uri.toString() in targetUris }
            val successCount = targets.count { repository.delete(it) }
            _toastEvent.emit("已处理 $successCount 首")
            val refreshed = currentSuccess() ?: return@launch
            _uiState.value = refreshed.copy(
                tracks = refreshed.tracks.filterNot { it.uri.toString() in targetUris },
                selectedUris = emptySet(),
                isSelectionMode = false
            )
        }
    }

    fun toggleSelectionMode() {
        val state = currentSuccess() ?: return
        _uiState.value = state.copy(
            isSelectionMode = !state.isSelectionMode,
            selectedUris = emptySet()
        )
    }

    fun toggleSelected(track: LocalTrack) {
        val state = currentSuccess() ?: return
        val key = track.uri.toString()
        val updated = if (key in state.selectedUris) state.selectedUris - key else state.selectedUris + key
        _uiState.value = state.copy(selectedUris = updated)
    }

    private fun currentSuccess(): LocalMusicUiState.Success? = _uiState.value as? LocalMusicUiState.Success
}
