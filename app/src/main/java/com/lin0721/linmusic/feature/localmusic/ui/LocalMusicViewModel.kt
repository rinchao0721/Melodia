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
import com.lin0721.linmusic.feature.localmusic.data.PreferredLocalAudioRepository
import com.lin0721.linmusic.feature.localmusic.data.batch.LocalMatchCoverRepository
import com.lin0721.linmusic.feature.localmusic.data.scan.ImportResult
import com.lin0721.linmusic.feature.localmusic.domain.LocalLibraryIndex
import com.lin0721.linmusic.feature.localmusic.domain.LocalPlaylist
import com.lin0721.linmusic.feature.localmusic.domain.resolvePlaylistTracks
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.domain.buildLocalLibraryIndex
import com.lin0721.linmusic.feature.localmusic.domain.queueSongId
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import com.lin0721.linmusic.feature.localmusic.domain.LocalMatchTitle
import com.lin0721.linmusic.feature.localmusic.domain.LocalMatchMetadata
import com.lin0721.linmusic.feature.localmusic.domain.LocalAudioCandidate
import com.lin0721.linmusic.feature.localmusic.domain.BatchOnlineMatchPolicy
import com.lin0721.linmusic.feature.localmusic.domain.PreferredLocalAudioMatcher
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import android.net.Uri
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.first
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

data class LocalOnlineAssociationState(
    val track: LocalTrack,
    val query: String,
    val results: List<Track> = emptyList(),
    val searching: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val searchedQuery: String = "",
    val nextOffset: Int = 0,
    val hasMore: Boolean = false
)

data class LocalAssociationCompletion(val localUri: String, val sequence: Long)

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
    private val preferredAudio: PreferredLocalAudioRepository,
    private val searchRepository: SearchRepository,
    private val matchCovers: LocalMatchCoverRepository,
    userPreferences: UserPreferences
) : ViewModel() {

    val userProfile: StateFlow<UserProfile?> = userPreferences.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val likedSongIds: StateFlow<Set<Long>> = songLikeRepository.likedSongIds

    val collectState: StateFlow<PlaylistCollectState> = songCollectDelegate.state

    private val _uiState = MutableStateFlow<LocalMusicUiState>(LocalMusicUiState.Loading)
    val uiState: StateFlow<LocalMusicUiState> = _uiState.asStateFlow()
    private val _onlineAssociation = MutableStateFlow<LocalOnlineAssociationState?>(null)
    val onlineAssociation: StateFlow<LocalOnlineAssociationState?> = _onlineAssociation.asStateFlow()
    private var associationSearchJob: Job? = null
    private var associationRequestGeneration = 0L
    private var associationSaveGeneration = 0L

    private fun cancelAssociationSearch(): Long {
        associationSearchJob?.cancel()
        return ++associationRequestGeneration
    }
    private val _associationSavingUri = MutableStateFlow<String?>(null)
    val associationSavingUri: StateFlow<String?> = _associationSavingUri.asStateFlow()
    private val _associationCompletion = MutableStateFlow<LocalAssociationCompletion?>(null)
    val associationCompletion: StateFlow<LocalAssociationCompletion?> = _associationCompletion.asStateFlow()

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
            combine(preferredAudio.associatedTracks, syncFinished) { tracks, synced -> tracks to synced }
                .collect { (tracks, synced) ->
                    val current = currentSuccess()
                    _uiState.value = when {
                        current != null -> current.copy(tracks = tracks,
                            menuState = current.menuState?.takeIf { menu ->
                                tracks.firstOrNull { it.uri == menu.track.uri }?.let { it.songId == menu.track.songId } == true
                            })
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
        songId = (currentSuccess()?.tracks?.firstOrNull { it.uri == track.uri } ?: track).queueSongId,
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
                if (currentMatched.track.uri != track.uri || currentMatched.track.songId != songId) return@collect
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

    fun openOnlineAssociation(track: LocalTrack, initialCandidates: List<Track> = emptyList()) {
        if (track.source == LocalTrackSource.MELODIA_DOWNLOAD || _associationSavingUri.value != null) return
        closeTrackMenu()
        val requestGeneration = cancelAssociationSearch()
        val metadata = matchMetadata(track)
        val artist = metadata.artist.takeIf(PreferredLocalAudioMatcher::hasKnownArtist).orEmpty()
        val query = "${LocalMatchTitle.forSearch(metadata.title)} $artist".trim()
        val candidates = initialCandidates.filter { it.id > 0L }.distinctBy { it.id }
        _onlineAssociation.value = LocalOnlineAssociationState(
            track = track,
            query = query,
            results = rankAssociationCandidates(candidates, track),
            searchedQuery = if (candidates.isNotEmpty()) query else ""
        )
        if (candidates.isEmpty()) searchOnlineAssociation()
        else associationSearchJob = viewModelScope.launch {
            val covered = matchCovers.fillCovers(candidates)
            val current = _onlineAssociation.value ?: return@launch
            if (associationRequestGeneration == requestGeneration && current.track.uri == track.uri && current.query == query && !current.saving) {
                _onlineAssociation.value = current.copy(results = rankAssociationCandidates(covered, track))
            }
        }
    }

    fun closeOnlineAssociation() {
        if (_onlineAssociation.value?.saving == true) return
        cancelAssociationSearch()
        _onlineAssociation.value = null
    }

    fun updateOnlineAssociationQuery(query: String) {
        val state = _onlineAssociation.value ?: return
        if (state.saving) return
        cancelAssociationSearch()
        _onlineAssociation.value = state.copy(query = query, searching = false, results = emptyList(),
            error = null, hasMore = false, nextOffset = 0, searchedQuery = "")
    }

    fun searchOnlineAssociation(loadMore: Boolean = false) {
        val state = _onlineAssociation.value ?: return
        val query = state.query.trim()
        if (state.saving || query.isBlank() || (loadMore && (!state.hasMore || state.searching || state.searchedQuery != query))) return
        val requestGeneration = cancelAssociationSearch()
        val offset = if (loadMore) state.nextOffset else 0
        _onlineAssociation.value = state.copy(searching = true, error = null, searchedQuery = query,
            results = if (loadMore) state.results else emptyList(), hasMore = if (loadMore) state.hasMore else false)
        associationSearchJob = viewModelScope.launch {
            try {
                searchRepository.search(query, SearchType.SONG, offset = offset, limit = 30).collect { result ->
                    val current = _onlineAssociation.value ?: return@collect
                    if (associationRequestGeneration != requestGeneration || current.track.uri != state.track.uri ||
                        current.searchedQuery != query || current.saving) return@collect
                    result.onSuccess { page ->
                        val songs = page.items.filterIsInstance<SearchResultItem.SongItem>()
                            .map { it.track }.filter { it.id > 0L }
                        // Make the candidates usable immediately; cover loading does not delay a choice.
                        _onlineAssociation.value = current.copy(
                            searching = false,
                            results = rankAssociationCandidates((if (loadMore) current.results + songs else songs).distinctBy { it.id }, current.track),
                            nextOffset = offset + page.rawFetchedCount,
                            hasMore = page.hasMore && page.rawFetchedCount > 0,
                            error = null
                        )
                        val covers = matchCovers.fillCovers(songs).associateBy { it.id }
                        // A cover request may finish after the user changed the query or started saving.
                        val latest = _onlineAssociation.value ?: return@onSuccess
                        if (associationRequestGeneration != requestGeneration || latest.track.uri != state.track.uri ||
                            latest.searchedQuery != query || latest.saving) return@onSuccess
                        _onlineAssociation.value = latest.copy(
                            results = latest.results.map { covers[it.id] ?: it }
                        )
                    }.onFailure { error ->
                        _onlineAssociation.value = current.copy(searching = false, error = error.toUserMessage(resourceProvider))
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val current = _onlineAssociation.value ?: return@launch
                if (associationRequestGeneration == requestGeneration && current.track.uri == state.track.uri &&
                    current.searchedQuery == query && !current.saving) {
                    _onlineAssociation.value = current.copy(searching = false, error = error.toUserMessage(resourceProvider))
                }
            }
        }
    }

    private fun matchMetadata(local: LocalTrack): LocalAudioCandidate = LocalMatchMetadata.resolve(
        LocalAudioCandidate(local.uri.toString(), local.title, local.artist, local.durationMs,
            album = local.album, fileName = local.path?.substringAfterLast('/')?.substringAfterLast('\\')))

    private fun rankAssociationCandidates(candidates: List<Track>, local: LocalTrack): List<Track> {
        return BatchOnlineMatchPolicy.rankCandidates(matchMetadata(local), candidates)
    }

    fun confirmOnlineAssociation(onlineTrack: Track) {
        val state = _onlineAssociation.value ?: return
        linkOnlineAssociation(state.track, onlineTrack)
    }

    /** A visible candidate tap is an explicit user choice, including replacing a previous link. */
    fun linkOnlineAssociation(track: LocalTrack, onlineTrack: Track, closeOnSuccess: Boolean = true,
                              onLinked: suspend () -> Unit = {}) {
        if (track.source == LocalTrackSource.MELODIA_DOWNLOAD || _associationSavingUri.value != null) return
        val saveGeneration = ++associationSaveGeneration
        _associationSavingUri.value = track.uri.toString()
        _onlineAssociation.value = _onlineAssociation.value?.let { state ->
            if (state.track.uri == track.uri) state.copy(saving = true, error = null) else state
        }
        cancelAssociationSearch()
        viewModelScope.launch {
            var committed = false
            try {
                val result = com.lin0721.linmusic.feature.localmusic.domain.commitManualAssociation(
                    link = { preferredAudio.linkLocalTrack(track, onlineTrack) },
                    updateReport = onLinked
                )
                if (result is com.lin0721.linmusic.feature.localmusic.domain.ManualAssociationCommitResult.Saved) {
                    committed = true
                    if (_onlineAssociation.value?.track?.uri == track.uri) {
                        _onlineAssociation.value = if (closeOnSuccess) null else _onlineAssociation.value?.copy(saving = false)
                    }
                    // The next item may open as soon as completion is observed. Toast delivery must
                    // not keep the old save gate locked, or that next item silently fails to open.
                    if (associationSaveGeneration == saveGeneration) _associationSavingUri.value = null
                    _associationCompletion.value = LocalAssociationCompletion(track.uri.toString(),
                        (_associationCompletion.value?.sequence ?: 0L) + 1L)
                    _toastEvent.emit(if (result.reportError != null) "关联已保存，匹配报告更新失败；可刷新记录恢复"
                        else if (preferredAudio.enabled.first()) "已关联「${onlineTrack.name}」"
                        else "已关联；请在播放与下载设置中开启「优先使用本地音频」")
                } else {
                    failOnlineAssociation(track, "本地音频无法读取，请检查文件与访问权限后重试")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (!committed) failOnlineAssociation(track, error.toUserMessage(resourceProvider))
                else AppLogger.w(TAG, "关联已保存，完成通知失败", error)
            } finally {
                if (associationSaveGeneration == saveGeneration) _associationSavingUri.value = null
            }
        }
    }

    private suspend fun failOnlineAssociation(track: LocalTrack, message: String) {
        val current = _onlineAssociation.value
        if (current?.track?.uri == track.uri) _onlineAssociation.value = current.copy(saving = false, error = message)
        _toastEvent.emit("关联失败：$message")
    }

    fun unlinkOnlineAssociation(track: LocalTrack) {
        if (track.source == LocalTrackSource.MELODIA_DOWNLOAD) return
        closeTrackMenu()
        viewModelScope.launch {
            try {
                preferredAudio.unlinkLocalTrack(track)
                _toastEvent.emit("已解除网易云关联；原文件保留，并停用原在线歌曲的自动匹配")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _toastEvent.emit(error.toUserMessage(resourceProvider)) }
        }
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
