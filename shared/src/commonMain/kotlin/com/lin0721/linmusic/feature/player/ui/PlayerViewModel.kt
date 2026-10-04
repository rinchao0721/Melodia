package com.lin0721.linmusic.feature.player.ui

import com.lin0721.linmusic.core.player.LyricsResolver
import com.lin0721.linmusic.core.player.LyricsSource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.preferences.FullPlayerCardLayout
import com.lin0721.linmusic.core.preferences.FullPlayerCardSetting
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.download.DownloadTrackInfo
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricPlaybackState
import com.lin0721.linmusic.core.player.domain.LyricTimeline
import com.lin0721.linmusic.feature.artist.data.ArtistRepository
import com.lin0721.linmusic.core.comment.data.CommentRepository
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.feature.player.data.PlayerRepository
import com.lin0721.linmusic.core.player.PlaybackController
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
import com.lin0721.linmusic.feature.library.domain.LibraryCollectionMutationBus
import com.lin0721.linmusic.feature.library.domain.LibraryCollectionMutationEvent

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
    val lyricsSource: LyricsSource? = null,
    val isLyricsLoading: Boolean = false,
    val songWiki: SongWikiData? = null,
    val isSongWikiLoading: Boolean = false,
    val chorusStartMs: Long? = null,
    val similarArtists: List<ArtistInfo> = emptyList(),
    val isSimilarArtistsLoading: Boolean = false,
    val artistAlbums: List<ArtistAlbum> = emptyList(),
    val isArtistAlbumsLoading: Boolean = false,
    val isLiked: Boolean = false,
    val artists: List<ArtistCardItem> = emptyList(),
    val selectedArtistIndex: Int = 0,
    // 未匹配网易云的本地歌曲，没有评论/百科等在线数据
    val isLocalOnly: Boolean = false
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

class PlayerViewModel(
    private val loadLikedSongIdsUseCase: LoadLikedSongIdsUseCase,
    private val networkStateProvider: NetworkStateProvider,
    private val playerRepository: PlayerRepository,
    private val playbackRepository: PlaybackRepository,
    private val artistRepository: ArtistRepository,
    private val commentRepository: CommentRepository,
    private val songLikeRepository: SongLikeRepository,
    private val songCollectDelegate: SongCollectDelegate,
    val playerManager: PlaybackController,
    private val userPreferences: UserPreferences,
    private val settingsPreferences: SettingsPreferences,
    private val resourceProvider: ResourceProvider,
    private val songDownloadManager: SongDownloader,
    private val lyricsResolver: LyricsResolver,
    private val libraryCollectionMutationBus: LibraryCollectionMutationBus
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
    fun isWifiConnected(): Boolean = networkStateProvider.isWifiConnected()

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

    // 播放页小歌词显隐
    val showMiniLyric: StateFlow<Boolean> = settingsPreferences.showMiniLyric.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun toggleMiniLyric(show: Boolean) {
        viewModelScope.launch {
            settingsPreferences.saveShowMiniLyric(show)
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

    private val _primaryLyricIndex = MutableStateFlow(-1)
    val primaryLyricIndex: StateFlow<Int> = _primaryLyricIndex.asStateFlow()
    val currentLyricIndex: StateFlow<Int> = primaryLyricIndex

    // 需要同时高亮的行集合。对唱与背景和声的时间区间会重叠，单个 index 表达不了，
    // 供全屏歌词与播放页歌词卡按 index in activeLyricIndices 判定。
    private val _activeLyricIndices = MutableStateFlow<Set<Int>>(emptySet())
    val activeLyricIndices: StateFlow<Set<Int>> = _activeLyricIndices.asStateFlow()

    // 有状态的歌词播放进度。advance() 的 displayIndices 需要「旧行在新行加入前继续保持显示」，
    // 这个判断依赖上一次的状态，所以必须跨进度回调持有；无状态的 activeIndices 表达不了。
    private var lyricPlaybackState = LyricPlaybackState()

    // 上一次进度回调的位置，用来识别跳转（拖动进度条、点歌词行、切歌续播）
    private var lastLyricPositionMs: Long? = null

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
                playerManager.nowPlaying.map { it?.songId ?: -1L },
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
            playerManager.nowPlaying
                .map { it?.songId ?: -1L }
                .distinctUntilChanged()
                .collectLatest { songId ->
                    if (songId != -1L && songId != currentSongId) {
                        currentSongId = songId
                        val isLiked = songId > 0L && songId in songLikeRepository.likedSongIds.value
                        val isLocalOnly = songId <= 0L
                        clearState(isLiked, isLocalOnly)
                        // 全部挂在同一棵子协程树下并发拉取：下一首切歌到达时 collectLatest
                        // 会把这整棵树一起取消，不需要每个加载函数各自手写 songId 比对防止过期数据写回
                        coroutineScope {
                            launch { loadLyrics(songId) }
                            // 负数占位 id 请求网易接口必然失败，歌词由 LyricsResolver 另行处理
                            if (!isLocalOnly) {
                                launch { loadSongDetail(songId) }
                                launch { loadSongWiki(songId) }
                                launch { loadChorus(songId) }
                                launch { commentsController.load("R_SO_4_$songId") }
                            }
                        }
                    }
                }
        }
    }

    private fun clearState(isLiked: Boolean = false, isLocalOnly: Boolean = false) {
        _songDetailState.value = PlayerSongDetailState(isLiked = isLiked, isLocalOnly = isLocalOnly)
        resetLyricPlayback()
    }

    // 作废累积的歌词播放状态。换歌、换歌词、清空状态时都要调用，
    // 否则上一套歌词的暂留行会挂在新歌词上
    private fun resetLyricPlayback() {
        lyricPlaybackState = LyricPlaybackState()
        lastLyricPositionMs = null
        _primaryLyricIndex.value = -1
        _activeLyricIndices.value = emptySet()
    }

    private fun updateLyricPlayback(positionMs: Long, isSeek: Boolean = false) {
        val lines = _songDetailState.value.lyrics
        if (lines.isEmpty()) return
        val previousPosition = lastLyricPositionMs
        // 进度回退、或前进远超一个轮询周期，都视为跳转：此时不做旧行暂留，
        // 直接对齐目标位置，否则拖动进度条会看到一路残留的高亮。
        // 这里不读 playerManager 的轮询间隔 —— PlaybackController 只暴露了 setter；
        // 而需要这个判断的场合（全屏播放页）间隔固定 50ms，2000ms 已远大于它。
        val discontinuity = previousPosition != null && (
            positionMs < previousPosition - LyricSeekBackwardToleranceMs ||
                positionMs - previousPosition > LyricSeekForwardThresholdMs
            )
        lyricPlaybackState = LyricTimeline.advance(
            lines = lines,
            positionMs = positionMs,
            previous = lyricPlaybackState,
            isSeek = isSeek || discontinuity
        )
        lastLyricPositionMs = positionMs
        _activeLyricIndices.value = lyricPlaybackState.displayIndices
        // 与原版一致：锚点跟随过渡显示状态，重叠旧行暂留时不跳到后面的行；
        // 手动跳转由 advance(isSeek = true) 立即重置高亮集合与锚点。
        _primaryLyricIndex.value = lyricPlaybackState.primaryIndex
    }

    private fun observePosition() {
        viewModelScope.launch {
            playerManager.currentPosition.collectLatest { positionMs ->
                updateLyricPlayback(positionMs)
            }
        }
    }

    private suspend fun loadLyrics(songId: Long) {
        settingsPreferences.amllLyricsEnabled.distinctUntilChanged().collectLatest {
            reloadLyrics(songId)
        }
    }

    private suspend fun reloadLyrics(songId: Long) {
        _songDetailState.update { it.copy(isLyricsLoading = true) }
        lyricsResolver.lyricsWithSourceFor(songId).collect { result ->
            result.onSuccess { resolved ->
                _songDetailState.update { it.copy(lyrics = resolved.lines, lyricsSource = resolved.source) }
                // 换了一套歌词，上一套累积的暂留行必须作废；并按当前位置立即对齐一次，
                // 不等下一次进度回调，否则进入播放页时会短暂停留在上一首的残留行
                resetLyricPlayback()
                updateLyricPlayback(playerManager.currentPosition.value, isSeek = true)
            }.onFailure {
                _songDetailState.update { it.copy(lyrics = emptyList(), lyricsSource = null) }
                resetLyricPlayback()
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

    // 本地歌曲 id 为负
    private suspend fun loadChorus(songId: Long) {
        if (songId <= 0L) return
        playerRepository.getChorusStartTime(songId).collect { result ->
            _songDetailState.update { it.copy(chorusStartMs = result.getOrNull()) }
        }
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
                    val ownerUid = userPreferences.userProfile.first()?.uid ?: return@onSuccess
                    libraryCollectionMutationBus.emit(
                        ownerUid,
                        LibraryCollectionMutationEvent.ArtistChanged(
                            id = artistId,
                            isCollected = targetFollow,
                            name = targetItem.artistName,
                            coverUrl = targetItem.artistDetail?.avatar
                                ?.ifBlank { targetItem.artistDetail.cover }
                                .orEmpty(),
                            updateTime = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    fun seekToTime(timeMs: Long) {
        playerManager.seekTo(timeMs)
        // 立刻按目标位置对齐，不等下一次进度回调。isSeek 会跳过旧行暂留，
        // 否则拖动进度条或点歌词行之后，原位置那一串行还会继续亮着
        updateLyricPlayback(timeMs, isSeek = true)
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
                        playerManager.playQueue(items, 0, playContext = PlaybackController.CONTEXT_INTELLIGENCE)
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

// 进度小幅回退不算跳转：播放器采样与四舍五入会带来 1~2ms 级别的抖动
private const val LyricSeekBackwardToleranceMs = 250L

// 进度前进超过该值即判定为跳转。全屏播放页的进度轮询间隔是 50ms，
// 3 个周期才 150ms，取 2000ms 是留足余量避免把正常推进误判为跳转
private const val LyricSeekForwardThresholdMs = 2000L
