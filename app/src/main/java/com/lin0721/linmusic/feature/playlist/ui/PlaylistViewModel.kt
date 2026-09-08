package com.lin0721.linmusic.feature.playlist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.comment.data.CommentRepository
import com.lin0721.linmusic.feature.playlist.domain.CreatePlaylistAndAddSongUseCase
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.playlist.domain.UpdatePlaylistCoverUseCase
import com.lin0721.linmusic.feature.home.data.HomeRepository
import com.lin0721.linmusic.feature.library.data.LibraryRepository
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.feature.home.data.DailySong
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationEvent
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage

import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.delay

// 歌单曲目分页补全每批数量，对齐服务端 song/detail 单次上限
private const val TRACK_PAGE_SIZE = 1000

class PlaylistViewModel(
    private val songCollectDelegate: SongCollectDelegate,
    private val syncProfileAfterLoginUseCase: SyncProfileAfterLoginUseCase,
    private val loadLikedSongIdsUseCase: LoadLikedSongIdsUseCase,
    private val homeRepository: HomeRepository,
    private val libraryRepository: LibraryRepository,
    private val commentRepository: CommentRepository,
    private val playlistRepository: PlaylistRepository,
    private val songLikeRepository: SongLikeRepository,
    private val playbackRepository: PlaybackRepository,
    private val userPlaylistRepository: UserPlaylistRepository,
    private val createPlaylistAndAddSongUseCase: CreatePlaylistAndAddSongUseCase,
    private val updatePlaylistCoverUseCase: UpdatePlaylistCoverUseCase,
    val playerManager: PlayerManager,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider,
    private val playlistMutationBus: PlaylistMutationBus,
    private val searchRepository: SearchRepository
) : ViewModel() {

    private var allRecommendedTracks = listOf<Track>()
    private var currentRecIndex = 0

    private var isAlbumMode = false
    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow<PlaylistUiState>(PlaylistUiState.Loading)
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    val userProfile = userPreferences.userProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _likedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val likedSongIds: StateFlow<Set<Long>> = _likedSongIds.asStateFlow()

    val collectState: StateFlow<PlaylistCollectState> = songCollectDelegate.state

    private val _commentsState = MutableStateFlow<CommentsState>(CommentsState.Loading)
    val commentsState: StateFlow<CommentsState> = _commentsState.asStateFlow()

    // 历史日推（每日推荐/听歌排行）浏览状态
    private val _historyRecommendState = MutableStateFlow(HistoryRecommendState())
    val historyRecommendState: StateFlow<HistoryRecommendState> = _historyRecommendState.asStateFlow()

    // "添加到歌单"批量导入目标选择状态
    private val _importState = MutableStateFlow(PlaylistImportState())
    val importState: StateFlow<PlaylistImportState> = _importState.asStateFlow()

    // 编辑歌单信息（名称/简介/封面）整体保存中的状态，供弹窗禁用保存按钮并显示进度
    private val _isSavingInfo = MutableStateFlow(false)
    val isSavingInfo: StateFlow<Boolean> = _isSavingInfo.asStateFlow()

    init {
        loadLikedSongIds()
    }

    fun loadLikedSongIds() {
        viewModelScope.launch {
            loadLikedSongIdsUseCase()?.let { _likedSongIds.value = it }
        }
    }

    fun loadPlaylist(id: Long, isAlbum: Boolean = false) {
        isAlbumMode = isAlbum
        _uiState.value = PlaylistUiState.Loading
        allRecommendedTracks = emptyList()
        loadJob?.cancel() // 取消之前的加载任务
        if (id == -2L) {
            _historyRecommendState.update { it.copy(selectedDate = "最近一周") }
            loadJob = viewModelScope.launch {
                loadUserRecord(1)
            }
            return
        }
        if (id == -1L) {
            _historyRecommendState.update { it.copy(selectedDate = "今天") }
            loadJob = viewModelScope.launch {
                homeRepository.getDailyRecommendSongs().collect { result ->
                    result.fold(
                        onSuccess = { dailySongs ->
                            val tracks = dailySongs.map { song ->
                                Track(
                                    id = song.id,
                                    name = song.name,
                                    ar = song.ar,
                                    al = song.al,
                                    fee = song.fee
                                )
                            }
                            val detail = PlaylistDetail(
                                id = -1L,
                                name = "每日推荐",
                                coverImgUrl = tracks.firstOrNull()?.al?.picUrl ?: "",
                                description = "为您量身定制的每日歌曲",
                                playCount = 0L,
                                tracks = tracks
                            )
                            _uiState.value = PlaylistUiState.Success(detail)
                            allRecommendedTracks = emptyList()
                        },
                        onFailure = { error ->
                            _uiState.value = PlaylistUiState.Error(error.toUserMessage(resourceProvider))
                        }
                    )
                }
            }
            return
        }
        loadJob = viewModelScope.launch {
            val flow = if (isAlbum) playlistRepository.getAlbumDetail(id) else playlistRepository.getPlaylistDetail(id)
            flow.collect { result ->
                result.fold(
                    onSuccess = { detail ->
                        _uiState.value = PlaylistUiState.Success(
                            detail,
                            isSubscribed = detail.subscribed,
                            hasMoreTracks = detail.trackIds.size > detail.tracks.size
                        )
                        val baseSong = detail.tracks.firstOrNull()

                        if (baseSong != null && !isAlbum) {
                            loadRecommendations(detail.tracks)
                        } else {
                            allRecommendedTracks = emptyList()
                        }
                        // 专辑详情接口不下发收藏状态，需额外核对已收藏专辑列表
                        if (isAlbum) {
                            checkAlbumSubscribed(detail.id)
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = PlaylistUiState.Error(error.toUserMessage(resourceProvider))
                    }
                )
            }
        }
    }

    private fun checkAlbumSubscribed(albumId: Long) {
        viewModelScope.launch {
            libraryRepository.getCollectedAlbums().collect { result ->
                result.onSuccess { albums ->
                    val subscribed = albums.any { it.id == albumId }
                    _uiState.update { state ->
                        if (state is PlaylistUiState.Success && state.playlist.id == albumId) {
                            state.copy(isSubscribed = subscribed)
                        } else state
                    }
                }
            }
        }
    }

    // 歌单曲目超过服务端截断阈值时，滚动到底部触发分批补全
    fun loadMoreTracks() {
        val current = _uiState.value as? PlaylistUiState.Success ?: return
        if (!current.hasMoreTracks || current.isLoadingMoreTracks) return
        val allIds = current.playlist.trackIds.map { it.id }
        val nextIds = allIds.drop(current.playlist.tracks.size).take(TRACK_PAGE_SIZE)
        if (nextIds.isEmpty()) {
            _uiState.update { state -> if (state is PlaylistUiState.Success) state.copy(hasMoreTracks = false) else state }
            return
        }
        val playlistId = current.playlist.id
        _uiState.update { state -> if (state is PlaylistUiState.Success) state.copy(isLoadingMoreTracks = true) else state }
        viewModelScope.launch {
            playlistRepository.loadMoreTracks(nextIds).collect { result ->
                result.fold(
                    onSuccess = { newTracks ->
                        _uiState.update { state ->
                            if (state is PlaylistUiState.Success && state.playlist.id == playlistId) {
                                val updatedTracks = state.playlist.tracks + newTracks
                                state.copy(
                                    playlist = state.playlist.copy(tracks = updatedTracks),
                                    hasMoreTracks = updatedTracks.size < allIds.size,
                                    isLoadingMoreTracks = false
                                )
                            } else state
                        }
                    },
                    onFailure = { e ->
                        _uiState.update { state -> if (state is PlaylistUiState.Success) state.copy(isLoadingMoreTracks = false) else state }
                        _toastEvent.emit(e.toUserMessage(resourceProvider))
                    }
                )
            }
        }
    }

    // 按批追加曲目直到 stopWhen 满足或者已加载全部；成功则把结果写回 uiState 并返回完整列表，失败返回 null 并提示 toast
    private suspend fun loadTracksUntil(stopWhen: (List<Track>) -> Boolean): List<Track>? {
        val current = _uiState.value as? PlaylistUiState.Success ?: return null
        val playlistId = current.playlist.id
        val allIds = current.playlist.trackIds.map { it.id }
        val loadedTracks = mutableListOf<Track>().apply { addAll(current.playlist.tracks) }
        var failure: Throwable? = null
        if (!stopWhen(loadedTracks)) {
            for (chunk in allIds.drop(loadedTracks.size).chunked(TRACK_PAGE_SIZE)) {
                val result = playlistRepository.loadMoreTracks(chunk).first()
                result.fold(
                    onSuccess = { loadedTracks.addAll(it) },
                    onFailure = { e -> failure = e }
                )
                if (failure != null || stopWhen(loadedTracks)) break
            }
        }
        if (failure != null) {
            _toastEvent.emit(failure.toUserMessage(resourceProvider))
            return null
        }
        _uiState.update { state ->
            if (state is PlaylistUiState.Success && state.playlist.id == playlistId) {
                state.copy(
                    playlist = state.playlist.copy(tracks = loadedTracks),
                    hasMoreTracks = loadedTracks.size < allIds.size,
                    isLoadingMoreTracks = false
                )
            } else state
        }
        return loadedTracks
    }

    // 补全歌单全部曲目后再执行回调：拖拽排序（全量覆盖会删掉未加载部分）、导入全部歌曲等场景都必须先拿到完整列表，
    // 否则只会处理已加载的这一批，超过1000首的歌单会悄悄漏掉后面的曲目
    fun ensureAllTracksLoaded(onReady: (List<Track>) -> Unit) {
        val current = _uiState.value as? PlaylistUiState.Success ?: return
        if (!current.hasMoreTracks) {
            onReady(current.playlist.tracks)
            return
        }
        if (current.isLoadingMoreTracks) return
        _uiState.update { state -> if (state is PlaylistUiState.Success) state.copy(isLoadingMoreTracks = true) else state }
        viewModelScope.launch {
            val result = loadTracksUntil { false }
            if (result != null) {
                onReady(result)
            } else {
                _uiState.update { state -> if (state is PlaylistUiState.Success) state.copy(isLoadingMoreTracks = false) else state }
            }
        }
    }

    // 补全到目标曲目出现为止再执行回调：定位当前播放歌曲用，靠前的歌不用像 ensureAllTracksLoaded 那样拉完
    fun ensureTrackLoaded(trackId: Long, onReady: (List<Track>) -> Unit = {}) {
        val current = _uiState.value as? PlaylistUiState.Success ?: return
        if (current.playlist.tracks.any { it.id == trackId }) {
            onReady(current.playlist.tracks)
            return
        }
        if (current.isLoadingMoreTracks) return
        _uiState.update { state -> if (state is PlaylistUiState.Success) state.copy(isLoadingMoreTracks = true) else state }
        viewModelScope.launch {
            val result = loadTracksUntil { tracks -> tracks.any { it.id == trackId } }
            if (result != null) {
                onReady(result)
            } else {
                _uiState.update { state -> if (state is PlaylistUiState.Success) state.copy(isLoadingMoreTracks = false) else state }
            }
        }
    }

    fun playSongInList(track: Track, allTracks: List<Track>) {
        val playlistName = (_uiState.value as? PlaylistUiState.Success)?.playlist?.name
        val queueItems = allTracks.map { t ->
            QueueItem(t.id, t.name, t.ar.joinToString { it.name }, t.al.picUrl)
        }
        val startIndex = allTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerManager.playQueue(queueItems, startIndex, playlistName)
    }

    fun addTrackToPlayNext(track: Track) {
        val queueItem = QueueItem(track.id, track.name, track.ar.joinToString("/") { it.name }, track.al.picUrl)
        playerManager.addToPlayNext(listOf(queueItem))
        viewModelScope.launch { _toastEvent.emit("已添加至下一首播放") }
    }

    fun addTracksToPlayNext(tracks: List<Track>) {
        val queueItems = tracks.map { t ->
            QueueItem(t.id, t.name, t.ar.joinToString("/") { it.name }, t.al.picUrl)
        }
        playerManager.addToPlayNext(queueItems)
        viewModelScope.launch { _toastEvent.emit("已添加 ${queueItems.size} 首歌曲至播放队列") }
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
            // 停留在“我喜欢的音乐”歌单时需重新拉取列表，使被取消红心的歌曲从当前页消失
            val successState = _uiState.value as? PlaylistUiState.Success
            val profile = userPreferences.userProfile.first()
            if (successState != null && profile != null && successState.playlist.id == profile.uid) {
                loadPlaylist(successState.playlist.id, isAlbumMode)
            }
        }
    }

    fun createPlaylistAndAddSong(name: String, songId: Long) {
        viewModelScope.launch {
            songCollectDelegate.createAndAdd(name, songId, _likedSongIds.value) { _toastEvent.emit(it) }
        }
    }

    fun handleLoginSuccess(cookies: String) {
        viewModelScope.launch {
            _toastEvent.emit("登录成功，正在同步数据...")
            if (syncProfileAfterLoginUseCase(cookies) != null) {
                loadLikedSongIds()
            }
        }
    }

    // 网易没有「给歌单、出推荐歌曲」的接口：playlist/detail/rcmd/get 一类返回的是歌单而非歌曲，
    // 心动模式则只认「我喜欢的音乐」这类红心歌单，其余歌单一律回 400「不支持该歌单类型」。
    // 因此改为从歌单里采样几首各取相似歌曲再合并，比只拿首曲更能代表整张歌单的风格
    fun loadRecommendations(tracks: List<Track>) {
        val seedIds = pickRecommendSeedIds(tracks)
        if (seedIds.isEmpty()) {
            allRecommendedTracks = emptyList()
            setRecommendedSongs(emptyList())
            return
        }
        viewModelScope.launch {
            val results = seedIds.map { seedId ->
                async {
                    runCatching { playbackRepository.getSimilarSongs(seedId).first() }
                        .getOrElse { Result.failure(it) }
                }
            }.awaitAll()

            // 单个种子失败不影响整体，只有全部落空才当作失败提示
            val succeeded = results.filter { it.isSuccess }
            if (succeeded.isEmpty()) {
                allRecommendedTracks = emptyList()
                setRecommendedSongs(emptyList())
                results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let {
                    _toastEvent.emit(it.toUserMessage(resourceProvider))
                }
                return@launch
            }

            val existingIds = tracks.mapTo(mutableSetOf()) { it.id }
            allRecommendedTracks = succeeded
                .flatMap { it.getOrDefault(emptyList()) }
                .distinctBy { it.id }
                .filterNot { it.id in existingIds }
            currentRecIndex = 0
            updateCurrentRecommendations()
        }
    }

    // 取首、中、尾三首当种子，兼顾歌单前后风格；短歌单去重后不足三首也照常工作
    private fun pickRecommendSeedIds(tracks: List<Track>): List<Long> {
        if (tracks.isEmpty()) return emptyList()
        return listOf(0, tracks.size / 2, tracks.lastIndex)
            .distinct()
            .map { tracks[it].id }
            .distinct()
    }

    private fun setRecommendedSongs(songs: List<Track>) {
        _uiState.update { state ->
            if (state is PlaylistUiState.Success) state.copy(recommendedSongs = songs) else state
        }
    }

    private fun updateCurrentRecommendations() {
        if (allRecommendedTracks.isEmpty()) {
            setRecommendedSongs(emptyList())
            return
        }
        val size = allRecommendedTracks.size
        val start = currentRecIndex % size
        val list = mutableListOf<Track>()
        for (i in 0 until 5) {
            val idx = (start + i) % size
            val track = allRecommendedTracks[idx]
            if (!list.contains(track)) {
                list.add(track)
            }
            if (list.size >= size) break
        }
        setRecommendedSongs(list)
    }

    fun refreshRecommendations() {
        if (allRecommendedTracks.isNotEmpty()) {
            currentRecIndex = (currentRecIndex + 5) % allRecommendedTracks.size
            updateCurrentRecommendations()
        }
    }

    fun addRecommendSongToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            playlistRepository.manipulatePlaylistTracks("add", playlistId, listOf(track.id)).collect { result ->
                result.onSuccess {
                    _toastEvent.emit("已添加到歌单")
                    allRecommendedTracks = allRecommendedTracks.filter { it.id != track.id }
                    updateCurrentRecommendations()

                    _uiState.update { state ->
                        if (state is PlaylistUiState.Success) {
                            val updatedTracks = state.playlist.tracks.toMutableList().apply {
                                if (none { it.id == track.id }) {
                                    add(track)
                                }
                            }
                            state.copy(playlist = state.playlist.copy(tracks = updatedTracks))
                        } else state
                    }
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 从当前歌单中删除一首歌，仅限歌单创建者可调用（由调用方按 UI 状态判断是否展示入口）
    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            playlistRepository.manipulatePlaylistTracks("del", playlistId, listOf(trackId)).collect { result ->
                result.onSuccess {
                    _toastEvent.emit("已从歌单中删除")
                    _uiState.update { state ->
                        if (state is PlaylistUiState.Success && state.playlist.id == playlistId) {
                            val updatedTracks = state.playlist.tracks.filter { it.id != trackId }
                            state.copy(playlist = state.playlist.copy(tracks = updatedTracks))
                        } else state
                    }
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 拉取当前用户自建的歌单，供"添加到歌单"批量导入选择目标；排除当前歌单本身与"我喜欢的音乐"
    fun prepareImportTargets(excludePlaylistId: Long) {
        viewModelScope.launch {
            val profile = userPreferences.userProfile.first()
            if (profile == null) {
                _toastEvent.emit("请先登录账号")
                return@launch
            }
            _importState.update { it.copy(isLoading = true) }
            userPlaylistRepository.getUserPlaylists(profile.uid).collect { result ->
                result.onSuccess { playlists ->
                    val items = playlists.filter {
                        it.userId == profile.uid && it.id != excludePlaylistId && it.id != profile.uid
                    }
                    _importState.value = PlaylistImportState(items = items, isLoading = false)
                }.onFailure { e ->
                    _importState.update { it.copy(isLoading = false) }
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 把当前歌单全部歌曲一次性导入到已有的目标歌单
    fun importAllTracksTo(targetPlaylistId: Long) {
        ensureAllTracksLoaded { tracks ->
            if (tracks.isEmpty()) return@ensureAllTracksLoaded
            viewModelScope.launch {
                playlistRepository.manipulatePlaylistTracks("add", targetPlaylistId, tracks.map { it.id }).collect { result ->
                    result.onSuccess {
                        _toastEvent.emit("已导入 ${tracks.size} 首歌曲")
                    }.onFailure { e ->
                        _toastEvent.emit(e.toUserMessage(resourceProvider))
                    }
                }
            }
        }
    }

    // 新建歌单并把当前歌单全部歌曲导入进去
    fun createPlaylistAndImportAll(name: String) {
        ensureAllTracksLoaded { tracks ->
            viewModelScope.launch {
                createPlaylistAndAddSongUseCase(name, tracks.map { it.id }).collect { result ->
                    result.onSuccess {
                        _toastEvent.emit("已创建歌单并导入 ${tracks.size} 首歌曲")
                    }.onFailure { e ->
                        _toastEvent.emit(e.toUserMessage(resourceProvider))
                    }
                }
            }
        }
    }

    fun toggleSubscribePlaylist() {
        val successState = _uiState.value as? PlaylistUiState.Success ?: return
        val id = successState.playlist.id
        val targetSubscribe = !successState.isSubscribed
        val resourceLabel = if (isAlbumMode) "专辑" else "歌单"
        viewModelScope.launch {
            val flow = if (isAlbumMode) {
                playlistRepository.subscribeAlbum(id, targetSubscribe)
            } else {
                playlistRepository.subscribePlaylist(id, targetSubscribe)
            }
            flow.collect { result ->
                result.onSuccess {
                    _uiState.update { state ->
                        if (state is PlaylistUiState.Success) state.copy(isSubscribed = targetSubscribe) else state
                    }
                    _toastEvent.emit(if (targetSubscribe) "已收藏$resourceLabel" else "已取消收藏$resourceLabel")
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 评论区 threadId：歌单为 A_PL_0_，专辑为 R_AL_3_，二者接口不通用
    private fun commentThreadId(id: Long): String = if (isAlbumMode) "R_AL_3_$id" else "A_PL_0_$id"

    fun loadPlaylistComments(playlistId: Long) {
        viewModelScope.launch {
            _commentsState.value = CommentsState.Loading
            val threadId = commentThreadId(playlistId)
            commentRepository.getComments(threadId, limit = 20).collect { result ->
                result.onSuccess { response ->
                    _commentsState.value = CommentsState.Success(
                        hotComments = response.hotComments,
                        comments = response.comments,
                        total = response.total
                    )
                }.onFailure { error ->
                    _commentsState.value = CommentsState.Error(error.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun likeComment(comment: CommentItem) {
        viewModelScope.launch {
            val profile = userProfile.value
            if (profile == null) {
                _toastEvent.emit("请先登录账号")
                return@launch
            }

            val currentState = _commentsState.value as? CommentsState.Success ?: return@launch
            
            val successState = _uiState.value as? PlaylistUiState.Success ?: return@launch
            val playlistId = successState.playlist.id
            val threadId = commentThreadId(playlistId)
            val targetLike = !comment.liked

            val updatedComments = currentState.comments.map {
                if (it.commentId == comment.commentId) {
                    it.copy(
                        liked = targetLike,
                        likedCount = it.likedCount + if (targetLike) 1 else -1
                    )
                } else it
            }
            val updatedHotComments = currentState.hotComments.map {
                if (it.commentId == comment.commentId) {
                    it.copy(
                        liked = targetLike,
                        likedCount = it.likedCount + if (targetLike) 1 else -1
                    )
                } else it
            }
            _commentsState.value = CommentsState.Success(
                hotComments = updatedHotComments,
                comments = updatedComments,
                total = currentState.total
            )

            commentRepository.likeComment(threadId, comment.commentId, targetLike).collect { result ->
                result.onFailure { e ->
                    _commentsState.value = currentState
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 加载历史日推可用日期
    fun loadHistoryDates() {
        viewModelScope.launch {
            _historyRecommendState.update { it.copy(datesLoading = true) }
            homeRepository.getHistoryRecommendDates().collect { result ->
                result.onSuccess { dates ->
                    _historyRecommendState.update { it.copy(dates = dates) }
                }.onFailure {
                    _toastEvent.emit(it.toUserMessage(resourceProvider))
                }
                _historyRecommendState.update { it.copy(datesLoading = false) }
            }
        }
    }

    // 加载指定日期的历史日推歌曲
    fun loadHistoryDetail(date: String) {
        if (date == "weekly" || date == "all") {
            val type = if (date == "weekly") 1 else 0
            loadUserRecord(type)
            return
        }
        viewModelScope.launch {
            _historyRecommendState.update { it.copy(selectedDate = date, songsLoading = true) }
            homeRepository.getHistoryRecommendDetail(date).collect { result ->
                result.onSuccess { songs ->
                    _historyRecommendState.update { it.copy(songs = songs) }
                    _uiState.update { state ->
                        if (state is PlaylistUiState.Success && state.playlist.id == -1L) {
                            val newTracks = songs.map { song ->
                                Track(
                                    id = song.id,
                                    name = song.name,
                                    ar = song.ar,
                                    al = song.al,
                                    fee = song.fee
                                )
                            }
                            state.copy(playlist = state.playlist.copy(tracks = newTracks))
                        } else state
                    }
                }.onFailure {
                    _toastEvent.emit(it.toUserMessage(resourceProvider))
                }
                _historyRecommendState.update { it.copy(songsLoading = false) }
            }
        }
    }

    fun loadUserRecord(type: Int) {
        viewModelScope.launch {
            val dateLabel = if (type == 1) "最近一周" else "所有时间"
            _historyRecommendState.update { it.copy(selectedDate = dateLabel, songsLoading = true) }
            val profile = userPreferences.userProfile.first()
            if (profile == null) {
                _toastEvent.emit("请先登录")
                _historyRecommendState.update { it.copy(songsLoading = false) }
                return@launch
            }
            libraryRepository.getUserRecord(profile.uid, type).collect { result ->
                result.fold(
                    onSuccess = { tracks ->
                        val detail = PlaylistDetail(
                            id = -2L,
                            name = "听歌排行的歌单",
                            coverImgUrl = tracks.firstOrNull()?.al?.picUrl ?: "",
                            description = "根据您的听歌记录统计",
                            playCount = 0L,
                            tracks = tracks
                        )
                        _uiState.value = PlaylistUiState.Success(detail)
                        _historyRecommendState.update { it.copy(songsLoading = false) }
                    },
                    onFailure = { error ->
                        _toastEvent.emit(error.toUserMessage(resourceProvider))
                        _historyRecommendState.update { it.copy(songsLoading = false) }
                    }
                )
            }
        }
    }

    fun playHistorySong(index: Int) {
        val songs = _historyRecommendState.value.songs
        if (songs.isEmpty()) return
        val queueItems = songs.map { song ->
            QueueItem(song.id, song.name, song.ar.joinToString { it.name }, song.al.picUrl)
        }
        playerManager.playQueue(queueItems, index.coerceIn(0, queueItems.size - 1), "历史日推")
    }

    fun toggleLikeSong(songId: Long, isLike: Boolean) {
        viewModelScope.launch {
            songLikeRepository.likeSong(songId, isLike).collect { result ->
                result.fold(
                    onSuccess = {
                        _toastEvent.emit(if (isLike) "已添加到我喜欢的音乐" else "已从我喜欢的音乐中移除")
                        val currentLiked = _likedSongIds.value.toMutableSet()
                        if (isLike) {
                            currentLiked.add(songId)
                        } else {
                            currentLiked.remove(songId)
                        }
                        _likedSongIds.value = currentLiked

                        val successState = _uiState.value as? PlaylistUiState.Success
                        if (successState != null) {
                            val profile = userPreferences.userProfile.first()
                            if (profile != null && successState.playlist.id == profile.uid) {
                                loadPlaylist(successState.playlist.id, isAlbumMode)
                            }
                        }
                    },
                    onFailure = { e ->
                        _toastEvent.emit(e.toUserMessage(resourceProvider))
                    }
                )
            }
        }
    }

    // 更新歌单信息（名称与简介）
    fun updatePlaylistInfo(
        id: Long,
        originalName: String,
        newName: String,
        originalDesc: String?,
        newDesc: String,
        coverBytes: ByteArray?,
        onComplete: (Boolean) -> Unit
    ) {
        if (_isSavingInfo.value) return
        val nameChanged = newName.isNotBlank() && newName != originalName
        val descChanged = newDesc != (originalDesc ?: "")

        if (!nameChanged && !descChanged && coverBytes == null) {
            onComplete(true)
            return
        }

        viewModelScope.launch {
            _isSavingInfo.value = true
            try {
            // 封面排在最前且失败即中止
            if (coverBytes != null) {
                val newCoverUrl = updatePlaylistCoverUseCase(id, coverBytes).getOrElse { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                    onComplete(false)
                    return@launch
                }
                _uiState.update { state ->
                    if (state is PlaylistUiState.Success && state.playlist.id == id) {
                        state.copy(playlist = state.playlist.copy(coverImgUrl = newCoverUrl))
                    } else state
                }
                playlistMutationBus.emit(PlaylistMutationEvent.CoverUpdated(id, newCoverUrl))

                if (!nameChanged && !descChanged) {
                    _toastEvent.emit("封面已更新")
                    onComplete(true)
                    return@launch
                }
            }

            val nameDeferred = if (nameChanged) {
                async { playlistRepository.renamePlaylist(id, newName).first() }
            } else null

            val descDeferred = if (descChanged) {
                async { playlistRepository.updateDescription(id, newDesc).first() }
            } else null

            val nameResult = nameDeferred?.await()
            val descResult = descDeferred?.await()

            val nameSuccess = nameResult == null || nameResult.isSuccess
            val descSuccess = descResult == null || descResult.isSuccess

            val currentState = _uiState.value as? PlaylistUiState.Success
            if (currentState != null && (nameSuccess || descSuccess)) {
                val updatedPlaylist = currentState.playlist.copy(
                    name = if (nameSuccess && nameChanged) newName else currentState.playlist.name,
                    description = if (descSuccess && descChanged) newDesc else currentState.playlist.description
                )
                _uiState.value = currentState.copy(playlist = updatedPlaylist)
            }

            if (nameSuccess && nameChanged) {
                playlistMutationBus.emit(PlaylistMutationEvent.Renamed(id, newName))
            }
            if (descSuccess && descChanged) {
                playlistMutationBus.emit(PlaylistMutationEvent.DescriptionUpdated(id, newDesc))
            }

            if (nameSuccess && descSuccess) {
                _toastEvent.emit("歌单信息已更新")
                onComplete(true)
            } else if (nameSuccess && !descSuccess) {
                val descMsg = descResult?.exceptionOrNull()?.toUserMessage(resourceProvider) ?: "未知错误"
                _toastEvent.emit("歌单名称已更新，简介修改失败: $descMsg")
                onComplete(false)
            } else if (!nameSuccess && descSuccess) {
                val nameMsg = nameResult?.exceptionOrNull()?.toUserMessage(resourceProvider) ?: "未知错误"
                _toastEvent.emit("简介已更新，歌单名称修改失败: $nameMsg")
                onComplete(false)
            } else {
                val err = nameResult?.exceptionOrNull() ?: descResult?.exceptionOrNull()
                _toastEvent.emit(err?.toUserMessage(resourceProvider) ?: "修改失败")
                onComplete(false)
            }
            } finally {
                _isSavingInfo.value = false
            }
        }
    }

    // 删除歌单
    fun deletePlaylist(id: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(id).collect { result ->
                result.fold(
                    onSuccess = {
                        playlistMutationBus.emit(PlaylistMutationEvent.Deleted(id))
                        _toastEvent.emit("歌单已删除")
                        onSuccess()
                    },
                    onFailure = { error ->
                        _toastEvent.emit(error.toUserMessage(resourceProvider))
                    }
                )
            }
        }
    }

    // ==================== 半屏添加音乐搜索与添加 ====================

    private val _addMusicSearchQuery = MutableStateFlow("")
    val addMusicSearchQuery: StateFlow<String> = _addMusicSearchQuery.asStateFlow()

    private val _addMusicSearchState = MutableStateFlow<AddMusicSearchState>(AddMusicSearchState.Idle)
    val addMusicSearchState: StateFlow<AddMusicSearchState> = _addMusicSearchState.asStateFlow()

    private var addMusicSearchJob: Job? = null

    fun updateAddMusicSearchQuery(query: String) {
        _addMusicSearchQuery.value = query
        addMusicSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _addMusicSearchState.value = AddMusicSearchState.Idle
            return
        }
        addMusicSearchJob = viewModelScope.launch {
            delay(300)
            _addMusicSearchState.value = AddMusicSearchState.Loading
            searchRepository.search(trimmed, SearchType.SONG, offset = 0, limit = 30).collect { result ->
                result.fold(
                    onSuccess = { pageResult ->
                        val tracks = pageResult.items.filterIsInstance<SearchResultItem.SongItem>().map { it.track }
                        _addMusicSearchState.value = AddMusicSearchState.Success(tracks)
                    },
                    onFailure = { error ->
                        _addMusicSearchState.value = AddMusicSearchState.Error(error.toUserMessage(resourceProvider))
                    }
                )
            }
        }
    }

    fun clearAddMusicSearch() {
        addMusicSearchJob?.cancel()
        _addMusicSearchQuery.value = ""
        _addMusicSearchState.value = AddMusicSearchState.Idle
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            playlistRepository.manipulatePlaylistTracks("add", playlistId, listOf(track.id)).collect { result ->
                result.onSuccess {
                    _toastEvent.emit("已添加到歌单")
                    _uiState.update { state ->
                        if (state is PlaylistUiState.Success && state.playlist.id == playlistId) {
                            val updatedTracks = state.playlist.tracks.toMutableList().apply {
                                if (none { it.id == track.id }) {
                                    add(track)
                                }
                            }
                            state.copy(playlist = state.playlist.copy(tracks = updatedTracks, trackCount = updatedTracks.size))
                        } else state
                    }
                    onComplete(true)
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                    onComplete(false)
                }
            }
        }
    }

    // ==================== 拖拽排序保存 ====================

    fun updateTrackOrder(playlistId: Long, newTracks: List<Track>, onComplete: (Boolean) -> Unit) {
        // op=update 是全量覆盖，空列表会把整个歌单清空
        if (newTracks.isEmpty()) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            playlistRepository.manipulatePlaylistTracks("update", playlistId, newTracks.map { it.id }).collect { result ->
                result.onSuccess {
                    _toastEvent.emit("歌曲顺序已更新")
                    _uiState.update { state ->
                        if (state is PlaylistUiState.Success && state.playlist.id == playlistId) {
                            state.copy(playlist = state.playlist.copy(tracks = newTracks))
                        } else state
                    }
                    onComplete(true)
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                    onComplete(false)
                }
            }
        }
    }
}

// 半屏添加音乐搜索状态
sealed interface AddMusicSearchState {
    data object Idle : AddMusicSearchState
    data object Loading : AddMusicSearchState
    data class Success(val tracks: List<Track>) : AddMusicSearchState
    data class Error(val message: String) : AddMusicSearchState
}
