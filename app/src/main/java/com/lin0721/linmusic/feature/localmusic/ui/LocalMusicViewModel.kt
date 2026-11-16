package com.lin0721.linmusic.feature.localmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
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
import com.lin0721.linmusic.feature.localmusic.data.LocalLibraryRepository
import com.lin0721.linmusic.feature.localmusic.data.LocalPlaylistRepository
import com.lin0721.linmusic.feature.localmusic.data.scan.ImportResult
import com.lin0721.linmusic.feature.localmusic.domain.LocalLibraryIndex
import com.lin0721.linmusic.feature.localmusic.domain.LocalPlaylist
import com.lin0721.linmusic.feature.localmusic.domain.resolvePlaylistTracks
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.domain.buildLocalLibraryIndex
import com.lin0721.linmusic.feature.localmusic.domain.queueSongId
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import android.net.Uri
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// 本地曲目操作菜单状态
sealed class LocalMusicMenuState {
    abstract val track: LocalTrack
    // 菜单里"播放"要按打开菜单时所在页面的列表排队
    abstract val queue: List<LocalTrack>
    // 从歌单页打开时带上歌单 id，菜单里多出"从歌单移除"
    abstract val playlistId: Long?
    data class Matched(
        override val track: LocalTrack,
        override val queue: List<LocalTrack>,
        override val playlistId: Long?,
        val fullTrack: Track
    ) : LocalMusicMenuState()
    data class Unmatched(
        override val track: LocalTrack,
        override val queue: List<LocalTrack>,
        override val playlistId: Long?,
        val coverUrl: String? = null
    ) : LocalMusicMenuState()
}

sealed class LocalMusicUiState {
    data object Loading : LocalMusicUiState()
    data class NeedsPermission(val permission: String) : LocalMusicUiState()
    data class Error(val message: String) : LocalMusicUiState()
    data class Success(
        val tracks: List<LocalTrack>,
        val availableStorageBytes: Long = 0L,
        val sortOrder: LocalMusicSortOrder = LocalMusicSortOrder.DATE_DESC,
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
    }
}

private const val PLAY_CONTEXT = "本地音乐"
private const val TAG = "LocalMusicViewModel"

class LocalMusicViewModel(
    private val repository: LocalLibraryRepository,
    private val playlistRepository: LocalPlaylistRepository,
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

    val likedSongIds: StateFlow<Set<Long>> = songLikeRepository.likedSongIds

    val collectState: StateFlow<PlaylistCollectState> = songCollectDelegate.state

    private val _uiState = MutableStateFlow<LocalMusicUiState>(LocalMusicUiState.Loading)
    val uiState: StateFlow<LocalMusicUiState> = _uiState.asStateFlow()

    // 曲库索引只随曲目列表变化重建，菜单/多选等界面状态变化不触发
    val library: StateFlow<LocalLibraryIndex> = _uiState
        .map { (it as? LocalMusicUiState.Success)?.tracks }
        .filterNotNull()
        .distinctUntilChanged()
        .map { buildLocalLibraryIndex(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalLibraryIndex.EMPTY)

    val playlists: StateFlow<List<LocalPlaylist>> = combine(playlistRepository.playlists, library) { records, index ->
        val tracksByUri = index.tracks.associateBy { it.uri.toString() }
        records.map { LocalPlaylist(it.id, it.name, it.updatedAt, resolvePlaylistTracks(it.trackUris, tracksByUri)) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _playlistPickerTracks = MutableStateFlow<List<LocalTrack>?>(null)
    val playlistPickerTracks: StateFlow<List<LocalTrack>?> = _playlistPickerTracks.asStateFlow()

    val playingMediaId: StateFlow<String?> = playerManager.currentTrack
        .map { it?.mediaId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPlaying: StateFlow<Boolean> = playerManager.playWhenReady

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent = _toastEvent.asSharedFlow()

    // 首次同步完成前库可能还是空的，此时保持加载态，避免先闪一下空列表
    private val syncFinished = MutableStateFlow(false)
    private var observeJob: Job? = null

    init {
        loadLikedSongIds()
    }

    fun loadLikedSongIds() {
        viewModelScope.launch {
            loadLikedSongIdsUseCase()
        }
    }

    fun handleLoginSuccess(cookies: String) {
        viewModelScope.launch {
            when (syncProfileAfterLoginUseCase.syncAccount(cookies)) {
                is com.lin0721.linmusic.core.auth.LoginSyncResult.Success -> {
                    _toastEvent.emit("登录成功，正在同步数据...")
                    loadLikedSongIds()
                }
                com.lin0721.linmusic.core.auth.LoginSyncResult.LimitReached -> {
                    _toastEvent.emit("最多只能登录 5 个账户")
                }
                com.lin0721.linmusic.core.auth.LoginSyncResult.Failed -> {
                    _toastEvent.emit("登录失败，会话无效或网络异常，请重试")
                }
            }
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

    // 进程重建会直接恢复到子页，每个本地页都要能触发加载
    fun ensureLoaded() {
        if (observeJob == null) checkPermissionAndLoad()
    }

    fun load() {
        observeTracks()
        viewModelScope.launch {
            runCatching { repository.sync() }
                .onFailure {
                    AppLogger.e(TAG, "本地音乐同步失败", it)
                    if (currentSuccess() == null) {
                        _uiState.value = LocalMusicUiState.Error(it.message ?: "扫描本地音乐失败")
                    }
                }
            syncFinished.value = true
        }
    }

    private fun observeTracks() {
        if (observeJob != null) return
        observeJob = viewModelScope.launch {
            combine(repository.tracks, syncFinished) { tracks, synced -> tracks to synced }
                .collect { (tracks, synced) ->
                    val current = currentSuccess()
                    _uiState.value = when {
                        current != null -> current.copy(tracks = tracks)
                        tracks.isNotEmpty() || synced -> LocalMusicUiState.Success(
                            tracks = tracks,
                            availableStorageBytes = repository.availableStorageBytes()
                        )
                        else -> LocalMusicUiState.Loading
                    }
                }
        }
    }

    fun setSortOrder(order: LocalMusicSortOrder) {
        val state = currentSuccess() ?: return
        _uiState.value = state.copy(sortOrder = order)
    }

    fun toggleSearch() {
        val state = currentSuccess() ?: return
        val next = !state.isSearchActive
        _uiState.value = state.copy(isSearchActive = next, searchQuery = if (next) state.searchQuery else "")
    }

    fun openSearch() {
        val state = currentSuccess() ?: return
        if (!state.isSearchActive) _uiState.value = state.copy(isSearchActive = true)
    }

    fun updateSearchQuery(query: String) {
        val state = currentSuccess() ?: return
        _uiState.value = state.copy(searchQuery = query)
    }

    // 随机只打乱本次队列，不改用户的播放模式
    fun playTracks(tracks: List<LocalTrack>, start: LocalTrack? = null, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val queue = if (shuffle) tracks.shuffled() else tracks
        val startIndex = if (shuffle || start == null) 0 else queue.indexOfFirst { it.uri == start.uri }.coerceAtLeast(0)
        playerManager.playQueue(queue.map(::toQueueItem), startIndex, PLAY_CONTEXT)
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

    private fun toQueueItem(track: LocalTrack): QueueItem = QueueItem(
        songId = track.queueSongId,
        title = track.title,
        artist = track.artist,
        coverUrl = "",
        localUri = track.uri.toString()
    )

    fun openTrackMenu(track: LocalTrack, queue: List<LocalTrack>, coverUrl: String? = null, playlistId: Long? = null) {
        val state = currentSuccess() ?: return
        val songId = track.songId
        if (songId == null) {
            _uiState.value = state.copy(menuState = LocalMusicMenuState.Unmatched(track, queue, playlistId, coverUrl))
            return
        }
        val initialTrack = Track(
            id = songId,
            name = track.title,
            ar = listOf(Artist(id = 0L, name = track.artist)),
            al = Album(id = 0L, name = track.album.orEmpty(), picUrl = coverUrl.orEmpty()),
            dt = track.durationMs
        )
        _uiState.value = state.copy(menuState = LocalMusicMenuState.Matched(track, queue, playlistId, initialTrack))
        viewModelScope.launch {
            playerRepository.getSongDetail(songId).collect { result ->
                val current = currentSuccess() ?: return@collect
                val currentMatched = current.menuState as? LocalMusicMenuState.Matched ?: return@collect
                if (currentMatched.track.uri != track.uri) return@collect
                result.onSuccess { fullTrack ->
                    _uiState.value = current.copy(menuState = currentMatched.copy(fullTrack = fullTrack))
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
                    _toastEvent.emit(if (like) "已添加到我喜欢的音乐" else "已从我喜欢的音乐中移除")
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun prepareCollectDialog(songId: Long) {
        viewModelScope.launch {
            songCollectDelegate.prepare(songId, songLikeRepository.likedSongIds.value) { _toastEvent.emit(it) }
        }
    }

    fun savePlaylistCollection(songId: Long, items: List<PlaylistCollectItem>) {
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

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runImport(emptyTip = "未找到有效音频文件", allSkippedTip = "所选歌曲已存在，已全部跳过") {
            repository.importFiles(uris)
        }
    }

    fun importFolder(treeUri: Uri) {
        runImport(emptyTip = "该文件夹下未找到音频文件", allSkippedTip = "文件夹中歌曲已全部存在，已跳过") {
            repository.importFolder(treeUri)
        }
    }

    private fun runImport(emptyTip: String, allSkippedTip: String, block: suspend () -> ImportResult) {
        viewModelScope.launch {
            _isImporting.value = true
            runCatching { block() }
                .onSuccess { result ->
                    val message = when {
                        result.addedCount > 0 && result.skippedCount > 0 ->
                            "成功导入 ${result.addedCount} 首歌曲，已跳过 ${result.skippedCount} 首重复歌曲"
                        result.addedCount > 0 -> "成功导入 ${result.addedCount} 首歌曲"
                        result.skippedCount > 0 -> allSkippedTip
                        else -> emptyTip
                    }
                    _toastEvent.emit(message)
                }
                .onFailure {
                    AppLogger.e(TAG, "导入本地音乐失败", it)
                    _toastEvent.emit("导入失败：${it.message ?: "未知错误"}")
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
            if (ok) closeTrackMenu()
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
            _uiState.value = refreshed.copy(selectedUris = emptySet(), isSelectionMode = false)
        }
    }

    fun toggleSelectionMode() {
        val state = currentSuccess() ?: return
        _uiState.value = state.copy(
            isSelectionMode = !state.isSelectionMode,
            selectedUris = emptySet()
        )
    }

    // 强制退出多选：离开页面或返回键使用，不像 toggleSelectionMode 那样依赖当前状态翻转
    fun exitSelectionMode() {
        val state = currentSuccess() ?: return
        if (state.isSelectionMode) _uiState.value = state.copy(isSelectionMode = false, selectedUris = emptySet())
    }

    fun toggleSelected(track: LocalTrack) {
        val state = currentSuccess() ?: return
        val key = track.uri.toString()
        val updated = if (key in state.selectedUris) state.selectedUris - key else state.selectedUris + key
        _uiState.value = state.copy(selectedUris = updated)
    }

    fun openPlaylistPicker(tracks: List<LocalTrack>) {
        if (tracks.isEmpty()) return
        closeTrackMenu()
        _playlistPickerTracks.value = tracks
    }

    fun closePlaylistPicker() {
        _playlistPickerTracks.value = null
    }

    fun createPlaylist(name: String, initialTracks: List<LocalTrack> = emptyList()) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val id = playlistRepository.create(trimmed)
                if (initialTracks.isNotEmpty()) playlistRepository.addTracks(id, initialTracks.map { it.uri.toString() })
            }.onSuccess {
                _toastEvent.emit(if (initialTracks.isEmpty()) "已创建「$trimmed」" else "已加入新歌单「$trimmed」")
                if (initialTracks.isNotEmpty()) finishPlaylistPick()
            }.onFailure {
                AppLogger.e(TAG, "新建本地歌单失败", it)
                _toastEvent.emit("新建歌单失败")
            }
        }
    }

    fun togglePlaylistMembership(playlist: LocalPlaylist, track: LocalTrack) {
        val uri = track.uri.toString()
        val contained = playlist.tracks.any { it.uri.toString() == uri }
        viewModelScope.launch {
            runCatching {
                if (contained) playlistRepository.removeTrack(playlist.id, uri) else playlistRepository.addTracks(playlist.id, listOf(uri))
            }.onSuccess {
                _toastEvent.emit(if (contained) "已从「${playlist.name}」移除" else "已加入「${playlist.name}」")
            }.onFailure {
                AppLogger.e(TAG, "修改本地歌单失败", it)
                _toastEvent.emit("操作失败")
            }
        }
    }

    fun addTracksToPlaylist(playlist: LocalPlaylist, tracks: List<LocalTrack>) {
        viewModelScope.launch {
            runCatching { playlistRepository.addTracks(playlist.id, tracks.map { it.uri.toString() }) }
                .onSuccess { added ->
                    _toastEvent.emit(if (added > 0) "已加入 $added 首到「${playlist.name}」" else "所选歌曲已在歌单中")
                    finishPlaylistPick()
                }
                .onFailure {
                    AppLogger.e(TAG, "批量加入本地歌单失败", it)
                    _toastEvent.emit("操作失败")
                }
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching { playlistRepository.rename(playlistId, trimmed) }
                .onFailure { _toastEvent.emit("重命名失败") }
        }
    }

    fun deletePlaylist(playlist: LocalPlaylist) {
        viewModelScope.launch {
            runCatching { playlistRepository.delete(playlist.id) }
                .onSuccess { _toastEvent.emit("已删除「${playlist.name}」") }
                .onFailure { _toastEvent.emit("删除歌单失败") }
        }
    }

    fun removeFromPlaylist(playlistId: Long, track: LocalTrack) {
        closeTrackMenu()
        viewModelScope.launch {
            runCatching { playlistRepository.removeTrack(playlistId, track.uri.toString()) }
                .onSuccess { _toastEvent.emit("已从歌单移除") }
                .onFailure { _toastEvent.emit("操作失败") }
        }
    }

    fun savePlaylistTracks(playlistId: Long, orderedTracks: List<LocalTrack>) {
        viewModelScope.launch {
            runCatching { playlistRepository.replaceTracks(playlistId, orderedTracks.map { it.uri.toString() }) }
                .onFailure {
                    AppLogger.e(TAG, "保存本地歌单失败", it)
                    _toastEvent.emit("保存失败")
                }
        }
    }

    private fun finishPlaylistPick() {
        _playlistPickerTracks.value = null
        exitSelectionMode()
    }

    private fun currentSuccess(): LocalMusicUiState.Success? = _uiState.value as? LocalMusicUiState.Success
}
