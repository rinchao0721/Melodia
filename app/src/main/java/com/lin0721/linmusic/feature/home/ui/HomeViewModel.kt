package com.lin0721.linmusic.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.api.AccountInfoResponse
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.home.data.DailySong
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.feature.home.data.HomeRepository
import com.lin0721.linmusic.feature.recent.data.RecentRepository
import com.lin0721.linmusic.feature.home.data.PersonalizedData
import com.lin0721.linmusic.feature.home.domain.HomeCard
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.feature.home.domain.ToplistInfo
import com.lin0721.linmusic.R
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.QueueItem

private const val TAG = "HomeViewModel"

// 首页 ViewModel
class HomeViewModel(
    private val syncProfileAfterLoginUseCase: SyncProfileAfterLoginUseCase,
    private val playbackRepository: PlaybackRepository,
    private val homeRepository: HomeRepository,
    private val recentRepository: RecentRepository,
    val playerManager: PlayerManager,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val userProfile: StateFlow<UserProfile?> = userPreferences.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadHomeData()
        viewModelScope.launch {
            playerManager.initController()
        }
    }

    fun loadHomeData(refresh: Boolean = false) {
        _uiState.value = HomeUiState.Loading

        viewModelScope.launch {
            val result = fetchHomeFeedData(refresh)
            _uiState.value = result.fold(
                onSuccess = { HomeUiState.Success(it) },
                onFailure = { HomeUiState.Error(it.toUserMessage(resourceProvider)) }
            )
        }
    }

    // 下拉刷新：保留当前已渲染内容不被清空，成功后整体替换，失败仅提示、不打断原有展示
    fun refreshHomeData() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true

        viewModelScope.launch {
            fetchHomeFeedData(refresh = true).fold(
                onSuccess = { _uiState.value = HomeUiState.Success(it) },
                onFailure = { _toastEvent.emit(it.toUserMessage(resourceProvider)) }
            )
            _isRefreshing.value = false
        }
    }

    // 货架序列是整页主数据源，它失败才算整页失败；其余几项各自兜底不影响渲染
    private suspend fun fetchHomeFeedData(refresh: Boolean): Result<HomeFeedData> = try {
        coroutineScope {
            val blockPageDeferred = async {
                homeRepository.getHomeBlockPage(refresh = refresh).first()
            }

            val playlistsDeferred = async {
                runCatching { homeRepository.getPersonalizedPlaylists().first() }
                    .getOrDefault(Result.success(PersonalizedData()))
            }

            val recentDeferred = async {
                runCatching { recentRepository.getRecentPlaylists().first() }
                    .getOrDefault(Result.success(emptyList()))
            }

            val dailySongsDeferred = async {
                runCatching { homeRepository.getDailyRecommendSongs().first() }
                    .getOrDefault(Result.success(emptyList()))
            }

            val toplistDeferred = async {
                runCatching { homeRepository.getToplistDetail().first() }
                    .getOrDefault(Result.success(emptyList<ToplistInfo>()))
            }

            val blockPageResult = blockPageDeferred.await()
            val playlistsResult = playlistsDeferred.await()
            val recentResult = recentDeferred.await()
            val dailySongsResult = dailySongsDeferred.await()
            val toplistResult = toplistDeferred.await()

            val blockPage = blockPageResult.getOrNull()
            if (blockPage != null) {
                Result.success(
                    HomeFeedData(
                        shelves = blockPage.shelves,
                        recommendPlaylists = playlistsResult.getOrNull()?.playlists.orEmpty(),
                        recentPlaylists = recentResult.getOrDefault(emptyList()),
                        dailySongs = dailySongsResult.getOrDefault(emptyList()),
                        toplistItems = toplistResult.getOrDefault(emptyList()),
                        nextCursor = blockPage.nextCursor,
                        hasMore = blockPage.hasMore
                    )
                )
            } else {
                Result.failure(
                    blockPageResult.exceptionOrNull()
                        ?: RuntimeException(resourceProvider.getString(R.string.app_error_biz_default))
                )
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // 滚到底时追加下一页货架。服务端目前只有两页，翻完 hasMore 即为 false
    fun loadMoreShelves() {
        val current = _uiState.value as? HomeUiState.Success ?: return
        val cursor = current.data.nextCursor ?: return
        if (current.data.isLoadingMore) return

        _uiState.value = HomeUiState.Success(current.data.copy(isLoadingMore = true))

        viewModelScope.launch {
            val result = runCatching { homeRepository.getHomeBlockPage(cursor = cursor).first() }
                .getOrNull()
                ?.getOrNull()

            val latest = _uiState.value as? HomeUiState.Success ?: return@launch
            _uiState.value = if (result == null) {
                // 追加失败不打断已渲染内容，仅收起加载态并停止继续翻页
                AppLogger.w(TAG, "翻页失败，停止继续加载货架")
                HomeUiState.Success(latest.data.copy(isLoadingMore = false, hasMore = false, nextCursor = null))
            } else {
                HomeUiState.Success(
                    latest.data.copy(
                        shelves = latest.data.shelves + result.shelves,
                        nextCursor = result.nextCursor,
                        hasMore = result.hasMore,
                        isLoadingMore = false
                    )
                )
            }
        }
    }

    fun playDailySong(index: Int = 0) {
        val state = uiState.value
        if (state is HomeUiState.Success && state.data.dailySongs.isNotEmpty()) {
            val queueItems = state.data.dailySongs.map { song ->
                QueueItem(song.id, song.name, song.ar.joinToString { it.name }, song.al.picUrl)
            }
            playerManager.playQueue(queueItems, index.coerceIn(0, queueItems.size - 1), "每日推荐")
        } else {
            viewModelScope.launch { _toastEvent.emit("每日推荐暂无歌曲") }
        }
    }

    // 播放货架里的单曲，队列取同一货架内的全部歌曲卡片，播完能自动接续下一首
    fun playShelfSong(songs: List<HomeCard.Song>, song: HomeCard.Song) {
        val queueItems = songs.map { QueueItem(it.id, it.title, "", it.coverUrl) }
        val startIndex = songs.indexOf(song).coerceAtLeast(0)
        playerManager.playQueue(queueItems, startIndex, playContext = "home_shelf")
    }

    fun playShelfVoice(voices: List<HomeCard.Voice>, voice: HomeCard.Voice) {
        val queueItems = voices.map { QueueItem(it.songId, it.title, it.caption, it.coverUrl) }
        val startIndex = voices.indexOf(voice).coerceAtLeast(0)
        playerManager.playQueue(queueItems, startIndex, playContext = "home_voice")
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun handleLoginSuccess(cookies: String) {
        viewModelScope.launch {
            val profile = syncProfileAfterLoginUseCase(cookies) ?: return@launch
            _toastEvent.emit("登录成功，欢迎回来，${profile.nickname}！")
            loadHomeData()
        }
    }

    fun logout() {
        viewModelScope.launch {
            userPreferences.clearUserProfile()
            _toastEvent.emit("已退出登录")
            loadHomeData()
        }
    }

    // 开启相似歌曲漫游
    fun startRoaming() {
        val current = playerManager.currentTrack.value
        if (current != null) {
            val songId = current.mediaId?.toLongOrNull() ?: return
            val title = current.mediaMetadata.title?.toString() ?: ""
            val artist = current.mediaMetadata.artist?.toString() ?: ""
            val coverUrl = current.mediaMetadata.artworkUri?.toString() ?: ""
            viewModelScope.launch {
                playbackRepository.getSimilarSongs(songId).collect { result ->
                    result.onSuccess { simiSongs ->
                        if (simiSongs.isNotEmpty()) {
                            val currentItem = QueueItem(songId, title, artist, coverUrl)
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
        } else {
            val state = uiState.value
            if (state is HomeUiState.Success && state.data.dailySongs.isNotEmpty()) {
                val firstSong = state.data.dailySongs.first()
                viewModelScope.launch {
                    playbackRepository.getSimilarSongs(firstSong.id).collect { result ->
                        result.onSuccess { simiSongs ->
                            val currentItem = QueueItem(firstSong.id, firstSong.name, firstSong.ar.joinToString("/") { it.name }, firstSong.al.picUrl)
                            val simiItems = simiSongs.map { track ->
                                QueueItem(track.id, track.name, track.ar.joinToString("/") { it.name }, track.al.picUrl)
                            }
                            val roamingQueue = listOf(currentItem) + simiItems
                            playerManager.playQueue(roamingQueue, 0, playContext = "similar_roaming")
                            _toastEvent.emit("已为您开启《${firstSong.name}》的漫游")
                        }.onFailure {
                            _toastEvent.emit(it.toUserMessage(resourceProvider))
                        }
                    }
                }
            } else {
                viewModelScope.launch { _toastEvent.emit("播放列表中没有歌曲且无法获取今日推荐") }
            }
        }
    }

    // 开启心动模式
    fun startIntelligenceMode() {
        val current = playerManager.currentTrack.value
        if (current != null) {
            val songId = current.mediaId?.toLongOrNull() ?: return
            viewModelScope.launch {
                playbackRepository.getIntelligenceSongs(songId, 0).collect { result ->
                    result.onSuccess { tracks ->
                        if (tracks.isNotEmpty()) {
                            val currentItem = QueueItem(
                                songId,
                                current.mediaMetadata.title?.toString() ?: "",
                                current.mediaMetadata.artist?.toString() ?: "",
                                current.mediaMetadata.artworkUri?.toString() ?: ""
                            )
                            val items = listOf(currentItem) + tracks.map { track ->
                                QueueItem(track.id, track.name, track.ar.joinToString("/") { it.name }, track.al.picUrl)
                            }
                            playerManager.playQueue(items, 0, playContext = "intelligence")
                            _toastEvent.emit("已开启心动模式")
                        } else {
                            _toastEvent.emit("获取心动推荐失败")
                        }
                    }.onFailure {
                        _toastEvent.emit(it.toUserMessage(resourceProvider))
                    }
                }
            }
        } else {
                                    val state = uiState.value
                                        if (state is HomeUiState.Success && state.data.dailySongs.isNotEmpty()) {
                                            val firstSong = state.data.dailySongs.first()
                                            viewModelScope.launch {
                                                playbackRepository.getIntelligenceSongs(firstSong.id, 0).collect { result ->
                                                    result.onSuccess { tracks ->
                                                        val currentItem = QueueItem(firstSong.id, firstSong.name, firstSong.ar.joinToString("/") { it.name }, firstSong.al.picUrl)
                                                        val items = listOf(currentItem) + tracks.map { track ->
                                                            QueueItem(track.id, track.name, track.ar.joinToString("/") { it.name }, track.al.picUrl)
                            }
                            playerManager.playQueue(items, 0, playContext = "intelligence")
                            _toastEvent.emit("已从《${firstSong.name}》开启心动模式")
                        }.onFailure {
                            _toastEvent.emit(it.toUserMessage(resourceProvider))
                        }
                    }
                }
            } else {
                viewModelScope.launch { _toastEvent.emit("请先播放一首歌曲") }
            }
        }
    }
}
