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
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val TAG = "ArtistViewModel"

// 分页区块每页拉取数量
private const val ALBUMS_PAGE_SIZE = 20
private const val MVS_PAGE_SIZE = 20
private const val ALL_SONGS_PAGE_SIZE = 50

class ArtistViewModel(
    private val songCollectDelegate: SongCollectDelegate,
    private val syncProfileAfterLoginUseCase: SyncProfileAfterLoginUseCase,
    private val loadLikedSongIdsUseCase: LoadLikedSongIdsUseCase,
    private val artistRepository: ArtistRepository,
    private val songLikeRepository: SongLikeRepository,
    val playerManager: PlayerManager,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArtistUiState>(ArtistUiState.Loading)
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

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
    private var albumOffset = 0
    private var mvOffset = 0
    private var allSongsOffset = 0

    init {
        loadLikedSongIds()
    }

    fun loadLikedSongIds() {
        viewModelScope.launch {
            loadLikedSongIdsUseCase()
        }
    }

    fun loadArtistData(artistId: Long) {
        currentArtistId = artistId
        albumOffset = 0
        mvOffset = 0
        allSongsOffset = 0
        _uiState.value = ArtistUiState.Loading
        viewModelScope.launch {
            try {
                // 并行发起网络请求以提供极速的界面预加载
                val detailDeferred = async { artistRepository.getArtistDetail(artistId).first() }
                val fansDeferred = async { artistRepository.getArtistFansCount(artistId).first() }
                val followDeferred = async { artistRepository.checkArtistFollowed(artistId).first() }
                val topSongsDeferred = async { artistRepository.getArtistTopSongs(artistId).first() }
                val albumsDeferred = async { artistRepository.getArtistAlbums(artistId, limit = ALBUMS_PAGE_SIZE, offset = 0).first() }
                val similarDeferred = async { artistRepository.getSimilarArtists(artistId).first() }
                val mvsDeferred = async { artistRepository.getArtistMvs(artistId, limit = MVS_PAGE_SIZE, offset = 0).first() }

                val detailResult = detailDeferred.await()
                val fansResult = fansDeferred.await()
                val followResult = followDeferred.await()
                val topSongsResult = topSongsDeferred.await()
                val albumsResult = albumsDeferred.await()
                val similarResult = similarDeferred.await()
                val mvsResult = mvsDeferred.await()

                if (detailResult.isSuccess && topSongsResult.isSuccess) {
                    val detail = detailResult.getOrThrow()
                    val fans = fansResult.getOrDefault(0L)
                    val isFollowed = followResult.getOrDefault(false)
                    val topSongs = topSongsResult.getOrThrow()
                    val albumsPage = albumsResult.getOrNull()
                    val similar = similarResult.getOrDefault(emptyList())
                    val mvsPage = mvsResult.getOrNull()

                    albumOffset = albumsPage?.albums?.size ?: 0
                    mvOffset = mvsPage?.mvs?.size ?: 0

                    _uiState.value = ArtistUiState.Success(
                        artist = detail,
                        isFollowed = isFollowed,
                        fansCount = fans,
                        topSongs = topSongs,
                        albums = albumsPage?.albums ?: emptyList(),
                        albumsHasMore = albumsPage?.hasMore ?: false,
                        similarArtists = similar,
                        mvs = mvsPage?.mvs ?: emptyList(),
                        mvsHasMore = mvsPage?.hasMore ?: false
                    )
                } else {
                    val err = (detailResult.exceptionOrNull() ?: topSongsResult.exceptionOrNull())
                        ?.toUserMessage(resourceProvider)
                        ?: resourceProvider.getString(AppString.ErrorBizDefault)
                    _uiState.value = ArtistUiState.Error(err)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "歌手详情页加载最终失败 artistId=$artistId", e)
                _uiState.value = ArtistUiState.Error(e.toUserMessage(resourceProvider))
            }
        }
    }

    // 专辑 Tab 滚动到底追加下一页
    fun loadMoreAlbums() {
        val state = _uiState.value as? ArtistUiState.Success ?: return
        if (!state.albumsHasMore || state.albumsLoadingMore) return
        _uiState.value = state.copy(albumsLoadingMore = true)
        viewModelScope.launch {
            artistRepository.getArtistAlbums(currentArtistId, limit = ALBUMS_PAGE_SIZE, offset = albumOffset)
                .first()
                .onSuccess { page ->
                    albumOffset += page.albums.size
                    val latest = _uiState.value as? ArtistUiState.Success ?: return@onSuccess
                    _uiState.value = latest.copy(
                        albums = latest.albums + page.albums,
                        albumsHasMore = page.hasMore,
                        albumsLoadingMore = false
                    )
                }
                .onFailure { e ->
                    AppLogger.w(TAG, "加载更多专辑失败 artistId=$currentArtistId", e)
                    val latest = _uiState.value as? ArtistUiState.Success ?: return@onFailure
                    _uiState.value = latest.copy(albumsLoadingMore = false)
                }
        }
    }

    // MV Tab 滚动到底追加下一页
    fun loadMoreMvs() {
        val state = _uiState.value as? ArtistUiState.Success ?: return
        if (!state.mvsHasMore || state.mvsLoadingMore) return
        _uiState.value = state.copy(mvsLoadingMore = true)
        viewModelScope.launch {
            artistRepository.getArtistMvs(currentArtistId, limit = MVS_PAGE_SIZE, offset = mvOffset)
                .first()
                .onSuccess { page ->
                    mvOffset += page.mvs.size
                    val latest = _uiState.value as? ArtistUiState.Success ?: return@onSuccess
                    _uiState.value = latest.copy(
                        mvs = latest.mvs + page.mvs,
                        mvsHasMore = page.hasMore,
                        mvsLoadingMore = false
                    )
                }
                .onFailure { e ->
                    AppLogger.w(TAG, "加载更多 MV 失败 artistId=$currentArtistId", e)
                    val latest = _uiState.value as? ArtistUiState.Success ?: return@onFailure
                    _uiState.value = latest.copy(mvsLoadingMore = false)
                }
        }
    }

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
