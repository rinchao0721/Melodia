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
    val playerManager: PlayerManager,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider,
    private val playlistMutationBus: PlaylistMutationBus
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
                        _uiState.value = PlaylistUiState.Success(detail, isSubscribed = detail.subscribed)
                        val baseSong = detail.tracks.firstOrNull()

                        if (baseSong != null && !isAlbum) {
                            loadRecommendations(detail.id, baseSong.id, detail.tracks)
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

    fun loadRecommendations(playlistId: Long, baseSongId: Long, existingTracks: List<Track>) {
        viewModelScope.launch {
            playbackRepository.getIntelligenceSongs(baseSongId, playlistId).collect { result ->
                result.onSuccess { recommendedList ->
                    val filteredList = recommendedList.filter { recTrack ->
                        existingTracks.none { it.id == recTrack.id }
                    }
                    allRecommendedTracks = filteredList
                    currentRecIndex = 0
                    updateCurrentRecommendations()
                }.onFailure { e ->
                    allRecommendedTracks = emptyList()
                    setRecommendedSongs(emptyList())
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
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
        val tracks = (_uiState.value as? PlaylistUiState.Success)?.playlist?.tracks.orEmpty()
        if (tracks.isEmpty()) return
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

    // 新建歌单并把当前歌单全部歌曲导入进去
    fun createPlaylistAndImportAll(name: String) {
        val tracks = (_uiState.value as? PlaylistUiState.Success)?.playlist?.tracks.orEmpty()
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
        onComplete: (Boolean) -> Unit
    ) {
        val nameChanged = newName.isNotBlank() && newName != originalName
        val descChanged = newDesc != (originalDesc ?: "")

        if (!nameChanged && !descChanged) {
            onComplete(true)
            return
        }

        viewModelScope.launch {
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
}
