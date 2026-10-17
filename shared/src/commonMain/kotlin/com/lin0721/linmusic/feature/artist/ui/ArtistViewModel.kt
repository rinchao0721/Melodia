package com.lin0721.linmusic.feature.artist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.feature.artist.data.ArtistRepository
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.preferences.ArtistAlbumFilterSettings
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.feature.artist.domain.ArtistAlbumSelection
import com.lin0721.linmusic.feature.playlist.domain.TrackBatchActionsDelegate
import com.lin0721.linmusic.feature.playlist.presentation.TrackSelection
import com.lin0721.linmusic.feature.playlist.ui.PlaylistImportState
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import com.lin0721.linmusic.feature.create.data.playlistPrivacyOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val TAG = "ArtistViewModel"

// 分页区块每页拉取数量
private const val ALBUMS_PAGE_SIZE = 20
private const val ALL_SONGS_PAGE_SIZE = 50

class ArtistViewModel(
    private val songCollectDelegate: SongCollectDelegate,
    private val syncProfileAfterLoginUseCase: SyncProfileAfterLoginUseCase,
    private val loadLikedSongIdsUseCase: LoadLikedSongIdsUseCase,
    private val artistRepository: ArtistRepository,
    private val songLikeRepository: SongLikeRepository,
    val playerManager: PlaybackController,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider,
    private val searchRepository: SearchRepository,
    private val trackBatchActions: TrackBatchActionsDelegate,
    private val settingsPreferences: SettingsPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArtistUiState>(ArtistUiState.Loading)
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()
    private val albumFilterArtistId = MutableStateFlow(0L)

    val albumDefaultMinTracks = settingsPreferences.artistAlbumMinTracks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ArtistAlbumFilterSettings.DEFAULT_MIN_TRACKS
    )
    @OptIn(ExperimentalCoroutinesApi::class)
    private val albumOverrideFlow = albumFilterArtistId.flatMapLatest(settingsPreferences::artistAlbumMinTracksOverride)
    val albumMinTracksOverride = albumOverrideFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )
    val albumMinTracks = combine(settingsPreferences.artistAlbumMinTracks, albumOverrideFlow) { default, override ->
        override ?: default
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArtistAlbumFilterSettings.DEFAULT_MIN_TRACKS)

    fun updateAlbumMinTracks(minimum: Int) {
        val artistId = currentArtistId
        if (artistId > 0) viewModelScope.launch { settingsPreferences.saveArtistAlbumMinTracksOverride(artistId, minimum) }
    }

    fun resetAlbumMinTracks() {
        val artistId = currentArtistId
        if (artistId > 0) viewModelScope.launch { settingsPreferences.clearArtistAlbumMinTracksOverride(artistId) }
    }

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    val userProfile = userPreferences.userProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val blockedArtistIds = userPreferences.blockedArtistIds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    fun toggleBlockArtist(artistId: Long) {
        viewModelScope.launch {
            userPreferences.toggleBlockArtist(artistId)
            val isBlocked = userPreferences.blockedArtistIds.first().contains(artistId)
            val msg = if (isBlocked) "已屏蔽该艺人所有歌曲" else "已取消屏蔽该艺人所有歌曲"
            _toastEvent.emit(msg)
        }
    }

    val likedSongIds: StateFlow<Set<Long>> = songLikeRepository.likedSongIds

    val collectState: StateFlow<PlaylistCollectState> = songCollectDelegate.state

    // 各分页区块当前已加载的偏移量，随 loadArtistData 重新加载而重置
    private var currentArtistId: Long = 0
    private var artistDataJob: Job? = null
    private var artistRequestGeneration = 0L
    private var albumOffset = 0
    private var albumsLoadJob: Job? = null
    private var allSongsOffset = 0

    init {
        loadLikedSongIds()
    }

    fun loadLikedSongIds() {
        viewModelScope.launch {
            loadLikedSongIdsUseCase()
        }
    }

    // 页面重新进入组合（如从详情页返回）时复用已有数据，仅未加载或失败时才请求
    fun loadArtistDataIfNeeded(artistId: Long) {
        if (currentArtistId == artistId && _uiState.value !is ArtistUiState.Error) return
        loadArtistData(artistId)
    }

    fun loadArtistData(artistId: Long) {
        artistDataJob?.cancel()
        albumsLoadJob?.cancel()
        val requestGeneration = ++artistRequestGeneration
        if (currentArtistId != artistId) {
            resetSongSearch()
            _selection.value = TrackSelection()
        }
        currentArtistId = artistId
        albumFilterArtistId.value = artistId
        albumOffset = 0
        allSongsOffset = 0
        _uiState.value = ArtistUiState.Loading
        artistDataJob = viewModelScope.launch {
            try {
                // 并行发起网络请求以提供极速的界面预加载
                val detailDeferred = async { artistRepository.getArtistDetail(artistId).first() }
                val fansDeferred = async { artistRepository.getArtistFansCount(artistId).first() }
                val followDeferred = async { artistRepository.checkArtistFollowed(artistId).first() }
                val topSongsDeferred = async { artistRepository.getArtistTopSongs(artistId).first() }
                val albumsDeferred = async { artistRepository.getArtistAlbums(artistId, limit = ALBUMS_PAGE_SIZE, offset = 0).first() }
                val similarDeferred = async { artistRepository.getSimilarArtists(artistId).first() }

                val detailResult = detailDeferred.await()
                val fansResult = fansDeferred.await()
                val followResult = followDeferred.await()
                val topSongsResult = topSongsDeferred.await()
                val albumsResult = albumsDeferred.await()
                val similarResult = similarDeferred.await()
                currentCoroutineContext().ensureActive()
                if (!isCurrentArtistRequest(artistId, requestGeneration)) return@launch

                if (detailResult.isSuccess && topSongsResult.isSuccess) {
                    val detail = detailResult.getOrThrow()
                    val fans = fansResult.getOrDefault(0L)
                    val isFollowed = followResult.getOrDefault(false)
                    val topSongs = topSongsResult.getOrThrow()
                    val albumsPage = albumsResult.getOrNull()
                    val similar = similarResult.getOrDefault(emptyList())

                    val albumProgress = albumsPage?.let { ArtistAlbumSelection.applyPage(emptyList(), 0, it) }
                    albumOffset = albumProgress?.nextOffset ?: 0

                    _uiState.value = ArtistUiState.Success(
                        artist = detail,
                        isFollowed = isFollowed,
                        fansCount = fans,
                        topSongs = topSongs,
                        albums = albumProgress?.albums ?: emptyList(),
                        albumsHasMore = albumProgress?.hasMore ?: albumsResult.isFailure,
                        similarArtists = similar,
                        albumsLoadError = albumProgress?.error ?: albumsResult.exceptionOrNull()?.toUserMessage(resourceProvider)
                    )
                } else {
                    val err = (detailResult.exceptionOrNull() ?: topSongsResult.exceptionOrNull())
                        ?.toUserMessage(resourceProvider)
                        ?: resourceProvider.getString(AppString.ErrorBizDefault)
                    _uiState.value = ArtistUiState.Error(err)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (!isCurrentArtistRequest(artistId, requestGeneration)) return@launch
                AppLogger.e(TAG, "歌手详情页加载最终失败 artistId=$artistId", e)
                _uiState.value = ArtistUiState.Error(e.toUserMessage(resourceProvider))
            }
        }
    }

    // 专辑 Tab 滚动到底追加下一页
    fun loadMoreAlbums(retry: Boolean = false) {
        val state = _uiState.value as? ArtistUiState.Success ?: return
        if (!state.albumsHasMore || state.albumsLoadingMore || (state.albumsLoadError != null && !retry)) return
        val artistId = currentArtistId
        val requestGeneration = artistRequestGeneration
        val requestedOffset = albumOffset
        _uiState.value = state.copy(albumsLoadingMore = true, albumsLoadError = null)
        albumsLoadJob = viewModelScope.launch {
            try {
                val page = artistRepository.getArtistAlbums(artistId, limit = ALBUMS_PAGE_SIZE, offset = requestedOffset)
                    .first().getOrThrow()
                currentCoroutineContext().ensureActive()
                if (!isCurrentArtistRequest(artistId, requestGeneration)) return@launch
                val latest = _uiState.value as? ArtistUiState.Success ?: return@launch
                val progress = ArtistAlbumSelection.applyPage(latest.albums, requestedOffset, page)
                albumOffset = progress.nextOffset
                _uiState.value = latest.copy(albums = progress.albums, albumsHasMore = progress.hasMore,
                    albumsLoadingMore = false, albumsLoadError = progress.error)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!isCurrentArtistRequest(artistId, requestGeneration)) return@launch
                AppLogger.w(TAG, "加载更多专辑失败 artistId=$artistId", error)
                val latest = _uiState.value as? ArtistUiState.Success ?: return@launch
                _uiState.value = latest.copy(albumsLoadingMore = false, albumsLoadError = error.toUserMessage(resourceProvider))
            }
        }
    }

    private fun isCurrentArtistRequest(artistId: Long, requestGeneration: Long): Boolean =
        currentArtistId == artistId && artistRequestGeneration == requestGeneration

    // 「音乐」Tab 首次切到「全部」子 Tab 时触发首页加载，此后不重复加载
    fun loadAllSongsIfNeeded() {
        val state = _uiState.value as? ArtistUiState.Success ?: return
        if (state.allSongsLoaded || state.allSongsLoadingMore) return
        _uiState.value = state.copy(allSongsLoadingMore = true)
        viewModelScope.launch {
            artistRepository.getArtistAllSongs(currentArtistId, offset = 0, limit = ALL_SONGS_PAGE_SIZE)
                .first()
                .onSuccess { page ->
                    allSongsOffset = page.songs.size
                    val latest = _uiState.value as? ArtistUiState.Success ?: return@onSuccess
                    _uiState.value = latest.copy(
                        allSongs = page.songs,
                        allSongsHasMore = page.hasMore,
                        allSongsLoadingMore = false,
                        allSongsLoaded = true
                    )
                }
                .onFailure { e ->
                    AppLogger.w(TAG, "加载全部歌曲失败 artistId=$currentArtistId", e)
                    val latest = _uiState.value as? ArtistUiState.Success ?: return@onFailure
                    _uiState.value = latest.copy(allSongsLoadingMore = false, allSongsLoaded = true)
                }
        }
    }

    // 「全部」子 Tab 滚动到底追加下一页
    fun loadMoreAllSongs() {
        val state = _uiState.value as? ArtistUiState.Success ?: return
        if (!state.allSongsHasMore || state.allSongsLoadingMore) return
        _uiState.value = state.copy(allSongsLoadingMore = true)
        viewModelScope.launch {
            artistRepository.getArtistAllSongs(currentArtistId, offset = allSongsOffset, limit = ALL_SONGS_PAGE_SIZE)
                .first()
                .onSuccess { page ->
                    allSongsOffset += page.songs.size
                    val latest = _uiState.value as? ArtistUiState.Success ?: return@onSuccess
                    _uiState.value = latest.copy(
                        allSongs = latest.allSongs + page.songs,
                        allSongsHasMore = page.hasMore,
                        allSongsLoadingMore = false
                    )
                }
                .onFailure { e ->
                    AppLogger.w(TAG, "加载更多全部歌曲失败 artistId=$currentArtistId", e)
                    val latest = _uiState.value as? ArtistUiState.Success ?: return@onFailure
                    _uiState.value = latest.copy(allSongsLoadingMore = false)
                }
        }
    }

    fun toggleFollow(artistId: Long) {
        val currentState = _uiState.value as? ArtistUiState.Success ?: return
        val targetSubscribe = !currentState.isFollowed
        viewModelScope.launch {
            artistRepository.subscribeArtist(artistId, targetSubscribe).collect { result ->
                result.onSuccess {
                    _uiState.value = currentState.copy(isFollowed = targetSubscribe)
                    val msg = if (targetSubscribe) "已关注歌手" else "已取消关注歌手"
                    _toastEvent.emit(msg)
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun playSongInList(track: Track, allTracks: List<Track>) {
        val artistName = (_uiState.value as? ArtistUiState.Success)?.artist?.name ?: "歌手热门歌曲"
        val queueItems = allTracks.map { t ->
            QueueItem(t.id, t.name, t.ar.joinToString { it.name }, t.al.picUrl)
        }
        val startIndex = allTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerManager.playQueue(queueItems, startIndex, artistName)
    }

    // 加入下一首播放
    fun addTrackToPlayNext(track: Track) {
        val queueItem = QueueItem(track.id, track.name, track.ar.joinToString("/") { it.name }, track.al.picUrl)
        playerManager.addToPlayNext(listOf(queueItem))
        viewModelScope.launch { _toastEvent.emit("已添加至下一首播放") }
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

    // 歌曲"喜欢"开关，供「更多操作」菜单调用（与红心图标的收藏弹层入口独立）
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

    // ---------- 歌曲搜索（仅「全部」子 Tab） ----------

    private val _songSearch = MutableStateFlow(ArtistSongSearchState())
    val songSearch: StateFlow<ArtistSongSearchState> = _songSearch.asStateFlow()

    private var songSearchJob: Job? = null
    private var songSearchOffset = 0

    private fun resetSongSearch() {
        songSearchJob?.cancel()
        songSearchOffset = 0
        _songSearch.value = ArtistSongSearchState()
    }

    fun updateSongSearchQuery(query: String) {
        songSearchJob?.cancel()
        songSearchOffset = 0
        if (query.isBlank()) {
            _songSearch.value = ArtistSongSearchState(query = query)
            return
        }
        _songSearch.value = ArtistSongSearchState(query = query, isLoading = true)
        songSearchJob = viewModelScope.launch {
            delay(ArtistSongSearch.DEBOUNCE_MS)
            fetchSongSearchPage(query, append = false)
        }
    }

    fun loadMoreSongSearch() {
        val state = _songSearch.value
        if (!state.active || !state.hasMore || state.isLoading || state.loadingMore) return
        _songSearch.value = state.copy(loadingMore = true, errorMessage = null)
        songSearchJob = viewModelScope.launch { fetchSongSearchPage(state.query, append = true) }
    }

    // 云搜索按原始条数翻页，过滤后结果过少时连翻几页，避免列表短到无法触发加载更多
    private suspend fun fetchSongSearchPage(query: String, append: Boolean) {
        val artist = (_uiState.value as? ArtistUiState.Success)?.artist ?: return
        val keyword = ArtistSongSearch.keyword(artist.name, query)
        val collected = mutableListOf<Track>()
        var hasMore = true
        var pages = 0
        while (hasMore && pages < ArtistSongSearch.MAX_PAGES_PER_FETCH && collected.size < ArtistSongSearch.MIN_RESULTS_PER_FETCH) {
            val page = searchRepository.search(keyword, SearchType.SONG, songSearchOffset, ArtistSongSearch.PAGE_SIZE)
                .first()
                .getOrElse { e ->
                    AppLogger.w(TAG, "歌手歌曲搜索失败 artistId=${artist.id}", e)
                    _songSearch.update {
                        if (it.query != query) it
                        else it.copy(isLoading = false, loadingMore = false, errorMessage = e.toUserMessage(resourceProvider))
                    }
                    return
                }
            songSearchOffset += page.rawFetchedCount
            val songs = page.items.filterIsInstance<SearchResultItem.SongItem>().map { it.track }
            collected += ArtistSongSearch.filterByArtist(songs, artist.id)
            hasMore = page.hasMore && page.rawFetchedCount > 0
            pages++
        }
        _songSearch.update {
            if (it.query != query) it
            else it.copy(
                results = if (append) (it.results + collected).distinctBy { track -> track.id } else collected.distinctBy { track -> track.id },
                isLoading = false,
                loadingMore = false,
                hasMore = hasMore,
                errorMessage = null
            )
        }
    }

    // ---------- 多选与批量操作 ----------

    private val _selection = MutableStateFlow(TrackSelection())
    val selection: StateFlow<TrackSelection> = _selection.asStateFlow()

    private val _importState = MutableStateFlow(PlaylistImportState())
    val importState: StateFlow<PlaylistImportState> = _importState.asStateFlow()

    fun enterSelection(firstTrackId: Long? = null) {
        _selection.update { if (it.active) it else it.enter(firstTrackId) }
    }

    fun toggleSelected(trackId: Long) {
        _selection.update { it.toggle(trackId) }
    }

    fun toggleSelectAll(visibleTrackIds: List<Long>) {
        _selection.update { it.toggleAll(visibleTrackIds) }
    }

    fun exitSelection() {
        _selection.value = TrackSelection()
    }

    // 热门、全部、搜索结果三处的歌曲合并去重后，按已选 id 取出
    private fun selectedTracks(): List<Track> {
        val state = _uiState.value as? ArtistUiState.Success ?: return emptyList()
        val pool = (state.topSongs + state.allSongs + _songSearch.value.results).distinctBy { it.id }
        return _selection.value.pick(pool)
    }

    private fun runBatch(needLogin: Boolean = false, block: suspend (List<Track>) -> Boolean) {
        val tracks = selectedTracks()
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            if (needLogin && userPreferences.userProfile.first() == null) {
                _toastEvent.emit("请先登录账号")
                return@launch
            }
            if (block(tracks)) exitSelection()
        }
    }

    fun prepareImportTargets() {
        viewModelScope.launch {
            _importState.update { it.copy(isLoading = true) }
            val items = trackBatchActions.loadTargetPlaylists(null) { _toastEvent.emit(it) }
            _importState.value = PlaylistImportState(items = items ?: _importState.value.items, isLoading = false)
        }
    }

    fun batchPlayNext() = runBatch { tracks ->
        trackBatchActions.playNext(tracks, emptySet()) { _toastEvent.emit(it) }
    }

    fun batchAddToPlaylist(targetPlaylistId: Long) = runBatch(needLogin = true) { tracks ->
        trackBatchActions.addToPlaylist(targetPlaylistId, tracks) { _toastEvent.emit(it) }
    }

    fun batchCreatePlaylistAndAdd(name: String, isPrivate: Boolean = false) = runBatch(needLogin = true) { tracks ->
        trackBatchActions.createPlaylistAndAdd(name, playlistPrivacyOf(isPrivate), tracks) { _toastEvent.emit(it) }
    }

    fun batchLike() = runBatch(needLogin = true) { tracks ->
        trackBatchActions.like(tracks) { _toastEvent.emit(it) } > 0
    }

    fun batchDownload(level: String) = runBatch { tracks ->
        val artist = (_uiState.value as? ArtistUiState.Success)?.artist ?: return@runBatch false
        trackBatchActions.download(tracks, level, "artist_${artist.id}", artist.name) { _toastEvent.emit(it) }
    }

    fun handleLoginSuccess(cookies: String) {
        viewModelScope.launch {
            if (syncProfileAfterLoginUseCase(cookies) == null) return@launch
            _toastEvent.emit("登录成功，正在同步数据...")
            // 同步红心列表并刷新歌手页，使关注态与红心态即时生效
            loadLikedSongIds()
            (uiState.value as? ArtistUiState.Success)?.let { successState ->
                loadArtistData(successState.artist.id)
            }
        }
    }
}
