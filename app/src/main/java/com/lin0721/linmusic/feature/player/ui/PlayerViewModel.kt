package com.lin0721.linmusic.feature.player.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.preferences.FullPlayerCardLayout
import com.lin0721.linmusic.core.preferences.FullPlayerCardSetting
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.download.DownloadTrackInfo
import com.lin0721.linmusic.core.download.SongDownloadManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.model.CommentUser
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.comment.domain.CommentsSectionController
import com.lin0721.linmusic.core.comment.domain.CommentComposerState
import com.lin0721.linmusic.core.comment.domain.CommentFloorState
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage

// 单个艺人的完整状态数据
data class ArtistCardItem(
    val artistId: Long,
    val artistName: String,
    val artistDetail: ArtistDetailInfo? = null,
    val fansCount: Long? = null,
    val isFollowed: Boolean = false,
    val isLoading: Boolean = false
)

// 当前播放歌曲的详情聚合状态：歌词/歌曲详情/歌手资料等异步分别到达，各自保留独立的 loading/nullable 语义
data class PlayerSongDetailState(
    val songDetail: Track? = null,
    val lyrics: List<LyricLine> = emptyList(),
    val isLyricsLoading: Boolean = false,
    val songWiki: SongWikiData? = null,
    val isSongWikiLoading: Boolean = false,
    val similarArtists: List<ArtistInfo> = emptyList(),
    val isSimilarArtistsLoading: Boolean = false,
    val artistAlbums: List<ArtistAlbum> = emptyList(),
    val isArtistAlbumsLoading: Boolean = false,
    val isLiked: Boolean = false,
    val artists: List<ArtistCardItem> = emptyList(),
    val selectedArtistIndex: Int = 0
) {
    val currentArtistItem: ArtistCardItem?
        get() = artists.getOrNull(selectedArtistIndex) ?: artists.firstOrNull()

    val artistDetail: ArtistDetailInfo?
        get() = currentArtistItem?.artistDetail
    val isArtistDetailLoading: Boolean
        get() = currentArtistItem?.isLoading ?: false
    val artistFansCount: Long?
        get() = currentArtistItem?.fansCount
    val isArtistFollowed: Boolean
        get() = currentArtistItem?.isFollowed ?: false
}

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
    private val resourceProvider: ResourceProvider,
    private val songDownloadManager: SongDownloadManager
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

    // 下载当前播放歌曲
    fun downloadCurrentSong(
        songId: Long,
        songName: String,
        artistName: String,
        albumName: String,
        coverUrl: String?,
        albumYear: Int,
        level: String
    ) {
        viewModelScope.launch {
            if (songId <= 0) {
                _toastEvent.emit("歌曲信息不完整，无法下载")
                return@launch
            }
            songDownloadManager.enqueueSingle(
                DownloadTrackInfo(songId, songName, artistName, albumName, coverUrl, albumYear), level
            )
            _toastEvent.emit("已加入下载队列")
        }
    }

    // 全屏播放页信息卡片顺序与显隐
    val fullPlayerCardLayout: StateFlow<List<FullPlayerCardSetting>> = settingsPreferences.fullPlayerCardLayout.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FullPlayerCardLayout.DEFAULT
    )

    fun saveFullPlayerCardLayout(layout: List<FullPlayerCardSetting>) {
        viewModelScope.launch {
            settingsPreferences.saveFullPlayerCardLayout(layout)
        }
    }

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

    private val commentsController = CommentsSectionController(
        scope = viewModelScope,
        repository = commentRepository,
        onToast = { message -> _toastEvent.emit(message) },
        resourceProvider = resourceProvider
    )
    val commentsState: StateFlow<CommentsState> = commentsController.commentsState
    val composerState: StateFlow<com.lin0721.linmusic.core.comment.domain.CommentComposerState> = commentsController.composerState
    val floorState: StateFlow<com.lin0721.linmusic.core.comment.domain.CommentFloorState> = commentsController.floorState

    val userProfile = userPreferences.userProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    val collectState: StateFlow<PlaylistCollectState> = songCollectDelegate.state

    private var currentSongId: Long = -1L

    init {
        loadLikedSongIds()
        observeLikedState()
        observeTrackChanges()
        observePosition()
    }

    private fun observeLikedState() {
        viewModelScope.launch {
            combine(
                playerManager.currentTrack.map { it?.mediaId?.toLongOrNull() ?: -1L },
                songLikeRepository.likedSongIds
            ) { songId, likedIds ->
                songId > 0L && songId in likedIds
            }.distinctUntilChanged()
                .collect { isLiked ->
                    _songDetailState.update { it.copy(isLiked = isLiked) }
                }
        }
    }

    private fun loadLikedSongIds() {
        viewModelScope.launch {
            userPreferences.userProfile.collect { profile ->
                if (profile != null) {
                    loadLikedSongIdsUseCase()
                }
            }
        }
    }

    fun toggleLike() {
        val songId = currentSongId
        if (songId <= 0L) return

        val newLiked = !_songDetailState.value.isLiked
        viewModelScope.launch {
            songLikeRepository.likeSong(songId, newLiked).collect { result ->
                result.onFailure {
                    _toastEvent.emit(it.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 打开"收藏到歌单"面板前先拉取歌单勾选状态
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

    private fun observeTrackChanges() {
        viewModelScope.launch {
            playerManager.currentTrack
                .map { it?.mediaId?.toLongOrNull() ?: -1L }
                .distinctUntilChanged()
                .collectLatest { songId ->
                    if (songId != -1L && songId != currentSongId) {
                        currentSongId = songId
                        val isLiked = songId > 0L && songId in songLikeRepository.likedSongIds.value
                        clearState(isLiked)
                        // 全部挂在同一棵子协程树下并发拉取：下一首切歌到达时 collectLatest
                        // 会把这整棵树一起取消，不需要每个加载函数各自手写 songId 比对防止过期数据写回
                        coroutineScope {
                            launch { loadLyrics(songId) }
                            launch { loadSongDetail(songId) }
                            launch { loadSongWiki(songId) }
                            launch { commentsController.load("R_SO_4_$songId") }
                        }
                    }
                }
        }
    }

    private fun clearState(isLiked: Boolean = false) {
        _songDetailState.value = PlayerSongDetailState(isLiked = isLiked)
        _currentLyricIndex.value = -1
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
                val validArtists = track.ar.filter { it.id > 0 }
                if (validArtists.isNotEmpty()) {
                    val initialItems = validArtists.map { ar ->
                        ArtistCardItem(
                            artistId = ar.id,
                            artistName = ar.name,
                            isLoading = true
                        )
                    }
                    _songDetailState.update {
                        it.copy(
                            artists = initialItems,
                            selectedArtistIndex = 0
                        )
                    }
                    coroutineScope {
                        // 并发异步加载全部艺人的卡片详情与关注状态
                        validArtists.forEach { ar ->
                            launch { loadSingleArtistCard(ar.id) }
                        }
                        // 优先加载首位聚焦艺人的更多专辑与类似推荐
                        val firstId = validArtists.first().id
                        launch { loadArtistAlbums(firstId) }
                        launch { loadSimilarArtists(firstId) }
                    }
                } else {
                    _songDetailState.update {
                        it.copy(
                            artists = emptyList(),
                            selectedArtistIndex = 0,
                            artistAlbums = emptyList(),
                            similarArtists = emptyList()
                        )
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

    // 并发拉取单个艺人的详细资料、粉丝量与关注状态
    private suspend fun loadSingleArtistCard(artistId: Long) = coroutineScope {
        var detail: ArtistDetailInfo? = null
        var fans: Long? = null
        var followed = false

        val detailJob = launch {
            artistRepository.getArtistDetail(artistId).collect { res ->
                detail = res.getOrNull()
            }
        }
        val fansJob = launch {
            artistRepository.getArtistFansCount(artistId).collect { res ->
                fans = res.getOrNull()
            }
        }
        val followJob = launch {
            artistRepository.checkArtistFollowed(artistId).collect { res ->
                followed = res.getOrDefault(false)
            }
        }
        joinAll(detailJob, fansJob, followJob)

        _songDetailState.update { state ->
            val updated = state.artists.map { item ->
                if (item.artistId == artistId) {
                    item.copy(
                        artistDetail = detail,
                        fansCount = fans,
                        isFollowed = followed,
                        isLoading = false
                    )
                } else item
            }
            state.copy(artists = updated)
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

    fun retryComments() {
        commentsController.retry()
    }

    fun likeComment(comment: CommentItem) {
        viewModelScope.launch {
            if (userProfile.value == null) {
                _toastEvent.emit("请先登录账号")
                return@launch
            }
            commentsController.like(comment)
        }
    }

    fun changeCommentSort(sortType: CommentSortType) = commentsController.changeSort(sortType)
    fun loadMoreComments() = commentsController.loadMore()
    fun submitComment(content: String) {
        val profile = userProfile.value ?: return
        commentsController.submitComment(content, CommentUser(userId = profile.uid, nickname = profile.nickname, avatarUrl = profile.avatarUrl))
    }

    fun submitCommentReply(parentCommentId: Long, content: String) {
        val profile = userProfile.value ?: return
        commentsController.submitReply(parentCommentId, content, CommentUser(userId = profile.uid, nickname = profile.nickname, avatarUrl = profile.avatarUrl))
    }
    fun deleteCommentItem(comment: CommentItem) = commentsController.deleteComment(comment)
    fun openCommentFloor(comment: CommentItem) = commentsController.openFloor(comment)
    fun loadMoreCommentFloor() = commentsController.loadMoreFloor()
    fun closeCommentFloor() = commentsController.closeFloor()

    private var artistRelatedJob: Job? = null

    // 切换聚焦选中的艺人，并联动刷新下方的更多专辑与类似艺人
    fun selectArtist(index: Int) {
        val state = _songDetailState.value
        if (index < 0 || index >= state.artists.size || index == state.selectedArtistIndex) return
        _songDetailState.update { it.copy(selectedArtistIndex = index) }
        val targetArtist = state.artists[index]
        artistRelatedJob?.cancel()
        artistRelatedJob = viewModelScope.launch {
            launch { loadArtistAlbums(targetArtist.artistId) }
            launch { loadSimilarArtists(targetArtist.artistId) }
        }
    }

    // 切换指定歌手的关注状态（未传则针对当前选中的艺人）
    fun toggleArtistFollow(targetArtistId: Long? = null) {
        val state = _songDetailState.value
        val artistId = targetArtistId ?: state.currentArtistItem?.artistId ?: return
        if (artistId <= 0) return

        val targetItem = state.artists.find { it.artistId == artistId } ?: state.currentArtistItem ?: return
        val targetFollow = !targetItem.isFollowed
        viewModelScope.launch {
            artistRepository.subscribeArtist(artistId, targetFollow).collect { result ->
                result.onSuccess {
                    _songDetailState.update { currState ->
                        val updated = currState.artists.map { item ->
                            if (item.artistId == artistId) item.copy(isFollowed = targetFollow) else item
                        }
                        currState.copy(artists = updated)
                    }
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
