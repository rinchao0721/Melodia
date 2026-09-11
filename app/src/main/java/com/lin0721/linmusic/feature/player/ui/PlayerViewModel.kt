package com.lin0721.linmusic.feature.player.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.feature.artist.data.ArtistRepository
import com.lin0721.linmusic.core.comment.data.CommentRepository
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.feature.player.data.PlayerRepository
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.player.domain.SongWikiData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage

// 当前播放歌曲的详情聚合状态：歌词/歌曲详情/歌手资料等异步分别到达，各自保留独立的 loading/nullable 语义
data class PlayerSongDetailState(
    val songDetail: Track? = null,
    val lyrics: List<LyricLine> = emptyList(),
    val isLyricsLoading: Boolean = false,
    val songWiki: SongWikiData? = null,
    val isSongWikiLoading: Boolean = false,
    val similarArtists: List<ArtistInfo> = emptyList(),
    val isSimilarArtistsLoading: Boolean = false,
    val artistDetail: ArtistDetailInfo? = null,
    val isArtistDetailLoading: Boolean = false,
    val artistFansCount: Long? = null,
    val artistAlbums: List<ArtistAlbum> = emptyList(),
    val isArtistAlbumsLoading: Boolean = false,
    val isLiked: Boolean = false,
    val isArtistFollowed: Boolean = false
)

private const val TAG = "PlayerViewModel"

class PlayerViewModel(
    private val loadLikedSongIdsUseCase: LoadLikedSongIdsUseCase,
    private val context: Context,
    private val playerRepository: PlayerRepository,
    private val playbackRepository: PlaybackRepository,
    private val artistRepository: ArtistRepository,
    private val commentRepository: CommentRepository,
    private val songLikeRepository: SongLikeRepository,
    private val songCollectDelegate: SongCollectDelegate,
    val playerManager: PlayerManager,
    private val userPreferences: UserPreferences,
    private val settingsPreferences: SettingsPreferences,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    // 监听 WiFi 下的播放音质设置
    val wifiQuality = settingsPreferences.wifiQuality.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "lossless"
    )

    // 监听移动网络下的播放音质设置
    val mobileQuality = settingsPreferences.mobileQuality.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "standard"
    )

    // 判断当前是否连接 WiFi
    fun isWifiConnected(): Boolean {
        return kotlin.runCatching {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        }.onFailure { AppLogger.w(TAG, "Wi-Fi 状态检测异常", it) }.getOrDefault(false)
    }

    // 根据网络状态动态获取并组合成当前的活动播放音质 Flow
    val activeQuality: StateFlow<String> = settingsPreferences.wifiQuality
        .combine(settingsPreferences.mobileQuality) { wifi, mobile ->
            if (isWifiConnected()) wifi else mobile
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "standard"
        )

    // 更新当前环境的音质设置并重新加载当前歌曲播放
    fun updateQuality(quality: String) {
        viewModelScope.launch {
            if (isWifiConnected()) {
                settingsPreferences.saveWifiQuality(quality)
            } else {
                settingsPreferences.saveMobileQuality(quality)
            }
            playerManager.reloadCurrentTrack()
        }
    }

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    private val _songDetailState = MutableStateFlow(PlayerSongDetailState())
    val songDetailState: StateFlow<PlayerSongDetailState> = _songDetailState.asStateFlow()

    private val _commentsState = MutableStateFlow<CommentsState>(CommentsState.Loading)
    val commentsState: StateFlow<CommentsState> = _commentsState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val likedSongIds = mutableSetOf<Long>()
    private var likedListLoaded = false

    val collectState: StateFlow<PlaylistCollectState> = songCollectDelegate.state

    private var currentSongId: Long = -1L

    init {
        loadLikedSongIds()
        observeTrackChanges()
        observePosition()
    }

    private fun loadLikedSongIds() {
        viewModelScope.launch {
            val ids = loadLikedSongIdsUseCase() ?: return@launch
            likedSongIds.clear()
            likedSongIds.addAll(ids)
            likedListLoaded = true
            if (currentSongId != -1L) {
                _songDetailState.update { it.copy(isLiked = currentSongId in likedSongIds) }
            }
        }
    }

    fun toggleLike() {
        val songId = currentSongId
        if (songId == -1L) return

        val newLiked = !_songDetailState.value.isLiked
        _songDetailState.update { it.copy(isLiked = newLiked) }

        viewModelScope.launch {
            songLikeRepository.likeSong(songId, newLiked).collect { result ->
                result.onSuccess {
                    if (newLiked) likedSongIds.add(songId) else likedSongIds.remove(songId)
                }.onFailure {
                    // 回滚
                    _songDetailState.update { it.copy(isLiked = !newLiked) }
                    _toastEvent.emit(it.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 打开"收藏到歌单"面板前先拉取歌单勾选状态
    fun prepareCollectDialog(songId: Long) {
        viewModelScope.launch {
            songCollectDelegate.prepare(songId, likedSongIds) { _toastEvent.emit(it) }
        }
    }

    fun savePlaylistCollection(songId: Long, items: List<PlaylistCollectItem>) {
        viewModelScope.launch {
            songCollectDelegate.save(
                songId = songId,
                items = items,
                likedSongIds = likedSongIds,
                onToast = { _toastEvent.emit(it) },
                onLikedChanged = { newLiked ->
                    likedSongIds.clear()
                    likedSongIds.addAll(newLiked)
                    _songDetailState.update { it.copy(isLiked = currentSongId in likedSongIds) }
                }
            )
        }
    }

    fun createPlaylistAndAddSong(name: String, songId: Long) {
        viewModelScope.launch {
            songCollectDelegate.createAndAdd(name, songId, likedSongIds) { _toastEvent.emit(it) }
        }
    }

    private fun observeTrackChanges() {
        viewModelScope.launch {
            playerManager.currentTrack
                .map { it?.mediaId?.toLongOrNull() ?: -1L }
                .distinctUntilChanged()
                .collectLatest { songId ->
                    if (songId != -1L && songId != currentSongId) {
                        currentSongId = songId
                        clearState()
                        _songDetailState.update { it.copy(isLiked = songId in likedSongIds) }
                        // 全部挂在同一棵子协程树下并发拉取：下一首切歌到达时 collectLatest
                        // 会把这整棵树一起取消，不需要每个加载函数各自手写 songId 比对防止过期数据写回
                        coroutineScope {
                            launch { loadLyrics(songId) }
                            launch { loadSongDetail(songId) }
                            launch { loadSongWiki(songId) }
                            launch { loadComments(songId) }
                        }
                    }
                }
        }
    }

    private fun clearState() {
        _songDetailState.value = PlayerSongDetailState()
        _currentLyricIndex.value = -1
        _commentsState.value = CommentsState.Loading
    }

    private fun observePosition() {
        viewModelScope.launch {
            playerManager.currentPosition.collectLatest { positionMs ->
                val lines = _songDetailState.value.lyrics
                if (lines.isEmpty()) return@collectLatest
                _currentLyricIndex.value = findLyricIndex(lines, positionMs)
            }
        }
    }

    private suspend fun loadLyrics(songId: Long) {
        _songDetailState.update { it.copy(isLyricsLoading = true) }
        playbackRepository.getLyrics(songId).collect { result ->
            result.onSuccess { lines ->
                _songDetailState.update { it.copy(lyrics = lines) }
            }.onFailure {
                _songDetailState.update { it.copy(lyrics = emptyList()) }
            }
        }
        _songDetailState.update { it.copy(isLyricsLoading = false) }
    }

    private suspend fun loadSongDetail(songId: Long) {
        playerRepository.getSongDetail(songId).collect { result ->
            result.onSuccess { track ->
                _songDetailState.update { it.copy(songDetail = track) }
                val primaryArtistId = track.ar.firstOrNull()?.id
                if (primaryArtistId != null && primaryArtistId > 0) {
                    coroutineScope {
                        launch { loadSimilarArtists(primaryArtistId) }
                        launch { loadArtistDetail(primaryArtistId) }
                        launch { loadArtistAlbums(primaryArtistId) }
                    }
                }
            }
        }
    }

    // 异步加载歌曲详情与音乐百科信息
    private suspend fun loadSongWiki(songId: Long) {
        _songDetailState.update { it.copy(isSongWikiLoading = true) }
        playerRepository.getSongWiki(songId).collect { result ->
            _songDetailState.update { it.copy(songWiki = result.getOrNull()) }
        }
        _songDetailState.update { it.copy(isSongWikiLoading = false) }
    }

    private suspend fun loadSimilarArtists(artistId: Long) {
        _songDetailState.update { it.copy(isSimilarArtistsLoading = true) }
        artistRepository.getSimilarArtists(artistId).collect { result ->
            result.onSuccess { artists ->
                _songDetailState.update { it.copy(similarArtists = artists) }
            }.onFailure {
                _songDetailState.update { it.copy(similarArtists = emptyList()) }
            }
        }
        _songDetailState.update { it.copy(isSimilarArtistsLoading = false) }
    }

    private suspend fun loadArtistDetail(artistId: Long) = coroutineScope {
        // 异步加载歌手粉丝数量作为每月听众数
        launch { loadArtistFansCount(artistId) }
        // 异步加载当前用户是否关注了该歌手
        launch { loadArtistFollowState(artistId) }
        _songDetailState.update { it.copy(isArtistDetailLoading = true) }
        artistRepository.getArtistDetail(artistId).collect { result ->
            result.onSuccess { detail ->
                _songDetailState.update { it.copy(artistDetail = detail) }
            }.onFailure {
                _songDetailState.update { it.copy(artistDetail = null) }
            }
        }
        _songDetailState.update { it.copy(isArtistDetailLoading = false) }
    }

    // 异步加载歌手关注状态
    private suspend fun loadArtistFollowState(artistId: Long) {
        artistRepository.checkArtistFollowed(artistId).collect { result ->
            result.onSuccess { followed ->
                _songDetailState.update { it.copy(isArtistFollowed = followed) }
            }.onFailure {
                _songDetailState.update { it.copy(isArtistFollowed = false) }
            }
        }
    }

    // 异步获取歌手粉丝数
    private suspend fun loadArtistFansCount(artistId: Long) {
        artistRepository.getArtistFansCount(artistId).collect { result ->
            result.onSuccess { count ->
                _songDetailState.update { it.copy(artistFansCount = count) }
            }.onFailure {
                _songDetailState.update { it.copy(artistFansCount = null) }
            }
        }
    }

    private suspend fun loadArtistAlbums(artistId: Long) {
        _songDetailState.update { it.copy(isArtistAlbumsLoading = true) }
        artistRepository.getArtistAlbums(artistId).collect { result ->
            result.onSuccess { page ->
                _songDetailState.update { it.copy(artistAlbums = page.albums) }
            }.onFailure {
                _songDetailState.update { it.copy(artistAlbums = emptyList()) }
            }
        }
        _songDetailState.update { it.copy(isArtistAlbumsLoading = false) }
    }

    private fun findLyricIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    private suspend fun loadComments(songId: Long) {
        _commentsState.value = CommentsState.Loading
        commentRepository.getComments(songId, limit = 20).collect { result ->
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

    fun retryComments() {
        val songId = currentSongId
        if (songId != -1L) {
            viewModelScope.launch { loadComments(songId) }
        }
    }

    fun likeComment(comment: CommentItem) {
        viewModelScope.launch {
            val profile = userPreferences.userProfile.first()
            if (profile == null) {
                _toastEvent.emit("请先登录账号")
                return@launch
            }

            val currentState = _commentsState.value as? CommentsState.Success ?: return@launch
            
            val songId = currentSongId
            if (songId == -1L) return@launch
            val threadId = "R_SO_4_$songId"
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

    // 切换歌手的关注状态
    fun toggleArtistFollow() {
        val songDetail = _songDetailState.value.songDetail ?: return
        val artistId = songDetail.ar.firstOrNull()?.id ?: return
        if (artistId <= 0) return

        val targetFollow = !_songDetailState.value.isArtistFollowed
        viewModelScope.launch {
            artistRepository.subscribeArtist(artistId, targetFollow).collect { result ->
                result.onSuccess {
                    _songDetailState.update { it.copy(isArtistFollowed = targetFollow) }
                }
            }
        }
    }

    fun seekToTime(timeMs: Long) {
        playerManager.seekTo(timeMs)
    }

    val sleepTimerRemaining: StateFlow<Long> = playerManager.sleepTimerRemaining

    fun setSleepTimer(minutes: Int) {
        playerManager.setSleepTimer(minutes)
    }

    // 开启相似歌曲漫游逻辑
    fun startSimilarSongsRoaming(songId: Long, currentTitle: String, currentArtist: String, currentCoverUrl: String) {
        viewModelScope.launch {
            playbackRepository.getSimilarSongs(songId).collect { result ->
                result.onSuccess { simiSongs ->
                    if (simiSongs.isNotEmpty()) {
                        val currentItem = QueueItem(songId, currentTitle, currentArtist, currentCoverUrl)
                        val simiItems = simiSongs.map { track ->
                            QueueItem(
                                songId = track.id,
                                title = track.name,
                                artist = track.ar.joinToString("/") { it.name },
                                coverUrl = track.al.picUrl
                            )
                        }
                        val roamingQueue = listOf(currentItem) + simiItems
                        playerManager.playQueue(roamingQueue, 0, playContext = "similar_roaming")
                        _toastEvent.emit("已开启相似歌曲漫游")
                    } else {
                        _toastEvent.emit("未找到相关相似歌曲")
                    }
                }.onFailure {
                    _toastEvent.emit(it.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 开启心动模式，以当前播放歌曲为种子
    fun startIntelligenceMode(songId: Long, currentTitle: String, currentArtist: String, currentCoverUrl: String) {
        viewModelScope.launch {
            playbackRepository.getIntelligenceSongs(songId, 0).collect { result ->
                result.onSuccess { tracks ->
                    if (tracks.isNotEmpty()) {
                        val currentItem = QueueItem(songId, currentTitle, currentArtist, currentCoverUrl)
                        val items = listOf(currentItem) + tracks.map { track ->
                            QueueItem(
                                songId = track.id,
                                title = track.name,
                                artist = track.ar.joinToString("/") { it.name },
                                coverUrl = track.al.picUrl
                            )
                        }
                        playerManager.playQueue(items, 0, playContext = PlayerManager.CONTEXT_INTELLIGENCE)
                        _toastEvent.emit("已开启心动模式")
                    } else {
                        _toastEvent.emit("获取心动推荐失败")
                    }
                }.onFailure {
                    _toastEvent.emit(it.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 插播一首相似歌曲到下一首位置
    fun insertSimilarSongs(songId: Long) {
        viewModelScope.launch {
            playbackRepository.getSimilarSongs(songId).collect { result ->
                result.onSuccess { simiSongs ->
                    val firstSong = simiSongs.firstOrNull()
                    if (firstSong != null) {
                        val simiItem = QueueItem(
                            songId = firstSong.id,
                            title = firstSong.name,
                            artist = firstSong.ar.joinToString("/") { it.name },
                            coverUrl = firstSong.al.picUrl
                        )
                        playerManager.addToPlayNext(listOf(simiItem))
                        _toastEvent.emit("已成功插播相似歌曲《${firstSong.name}》到下一首")
                    } else {
                        _toastEvent.emit("暂无相似歌曲可插播")
                    }
                }.onFailure {
                    _toastEvent.emit(it.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 清空播放队列
    fun clearQueue() {
        playerManager.clearQueue()
    }
}
