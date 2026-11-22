package com.lin0721.linmusic.feature.artist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.LoginSyncResult
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.platform.ArtistId
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.repository.PlatformArtistRouter
import com.lin0721.linmusic.core.platform.toDeterministicLegacyId
import com.lin0721.linmusic.core.platform.toDisplayArtistAlbum
import com.lin0721.linmusic.core.platform.toDisplayArtistDetailInfo
import com.lin0721.linmusic.core.platform.toDisplayArtistInfo
import com.lin0721.linmusic.core.platform.toDisplayTrack
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.songquality.SongQualityCache
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.ui.components.CollectPromptEvent
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.feature.artist.data.ArtistRepository
import com.lin0721.linmusic.feature.create.data.playlistPrivacyOf
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.playlist.domain.TrackBatchActionsDelegate
import com.lin0721.linmusic.feature.playlist.presentation.TrackSelection
import com.lin0721.linmusic.feature.playlist.ui.PlaylistImportState
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
    private val songQualityCache: SongQualityCache? = null,
    private val platformArtistRouter: PlatformArtistRouter? = null,
    private val settingsPreferences: SettingsPreferences? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArtistUiState>(ArtistUiState.Loading)
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _unifiedTracks = MutableStateFlow<Map<Long, UnifiedTrack>>(emptyMap())
    val unifiedTracks: StateFlow<Map<Long, UnifiedTrack>> = _unifiedTracks.asStateFlow()

    val isKugouEnabled: StateFlow<Boolean> = (settingsPreferences?.kugouPlatformEnabled ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val kugouAccount: StateFlow<StoredAccount?> = userPreferences.activeAccountOf(MusicPlatform.KUGOU)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
    val likedTrackIds: StateFlow<Set<TrackId>> = songLikeRepository.likedTrackIds

    val collectState: StateFlow<PlaylistCollectState> = songCollectDelegate.state
    val collectPromptEvent: SharedFlow<CollectPromptEvent> = songCollectDelegate.collectPromptEvent

    // 各分页区块当前已加载的偏移量，随 loadArtistData 重新加载而重置
    private var currentArtistId: Long = 0
    private var currentEntityId: ArtistId? = null
    private var albumOffset = 0
    private var allSongsOffset = 0

    init {
        loadLikedSongIds()
    }

    fun loadLikedSongIds() {
        viewModelScope.launch {
            loadLikedSongIdsUseCase()
        }
    }

    private fun registerUnifiedTracks(list: List<UnifiedTrack>, reset: Boolean = false): List<Track> {
        val mapped = ArrayList<Track>(list.size)
        val additions = LinkedHashMap<Long, UnifiedTrack>(list.size)
        for (u in list) {
            val d = u.toDisplayTrack()
            mapped.add(d)
            additions[d.id] = u
        }
        _unifiedTracks.update { prev ->
            if (reset) additions else prev + additions
        }
        return mapped
    }

    private fun Track.toQueueItem(): QueueItem =
        _unifiedTracks.value[id]?.let(QueueItem::fromUnified)
            ?: QueueItem(id, name, ar.joinToString("/") { it.name }, al.picUrl)

    // 页面重新进入组合（如从详情页返回）时复用已有数据，仅未加载或失败时才请求
    fun loadArtistDataIfNeeded(artistId: Long) {
        if (currentEntityId == ArtistId.netease(artistId) && currentArtistId == artistId && _uiState.value !is ArtistUiState.Error) return
        loadArtistData(artistId)
    }

    fun loadArtistDataIfNeeded(entityId: String) {
        val parsed = ArtistId.parseOrNull(entityId)
        if (parsed == null) {
            val fallback = entityId.toLongOrNull()
            if (fallback != null && fallback > 0L) loadArtistDataIfNeeded(fallback)
            else _uiState.value = ArtistUiState.Error("无效歌手标识: $entityId")
            return
        }
        if (parsed.platform == MusicPlatform.NETEASE) {
            parsed.neteaseIdOrNull()?.let { loadArtistDataIfNeeded(it); return }
        }
        if (currentEntityId == parsed && _uiState.value !is ArtistUiState.Error) return
        loadArtistData(entityId)
    }

    fun loadArtistData(entityId: String) {
        val parsed = ArtistId.parseOrNull(entityId)
        if (parsed == null) {
            val fallback = entityId.toLongOrNull()
            if (fallback != null && fallback > 0L) loadArtistData(fallback)
            else _uiState.value = ArtistUiState.Error("无效歌手标识: $entityId")
            return
        }
        if (parsed.platform == MusicPlatform.NETEASE) {
            parsed.neteaseIdOrNull()?.let { loadArtistData(it); return }
        }
        if (parsed.platform != MusicPlatform.KUGOU) {
            _uiState.value = ArtistUiState.Error("暂不支持该平台歌手详情")
            return
        }
        val router = platformArtistRouter
        if (router == null) {
            _uiState.value = ArtistUiState.Error("歌手路由未就绪")
            return
        }
        val legacyId = parsed.toDeterministicLegacyId()
        if (currentEntityId != parsed) {
            resetSongSearch()
            _selection.value = TrackSelection()
        }
        currentEntityId = parsed
        currentArtistId = legacyId
        albumOffset = 0
        allSongsOffset = 0
        _unifiedTracks.value = emptyMap()
        _uiState.value = ArtistUiState.Loading

        viewModelScope.launch {
            if (!(settingsPreferences?.kugouPlatformEnabled?.first() ?: true)) {
                _uiState.value = ArtistUiState.Error("酷狗音乐平台已关闭")
                return@launch
            }
            try {
                val detailDeferred = async { router.getArtistDetail(parsed).first() }
                val topSongsDeferred = async { router.getArtistTopSongs(parsed).first() }
                val albumsDeferred = async { router.getArtistAlbums(parsed, offset = 0, limit = ALBUMS_PAGE_SIZE).first() }
                val followDeferred = async { router.checkArtistFollowed(parsed).first() }
                val similarDeferred = async { router.getSimilarArtists(parsed).first() }

                val detailResult = detailDeferred.await()
                val topSongsResult = topSongsDeferred.await()
                val albumsResult = albumsDeferred.await()
                val followResult = followDeferred.await()
                val similarResult = similarDeferred.await()

                if (detailResult.isSuccess) {
                    val detail = detailResult.getOrThrow()
                    val topUnified = topSongsResult.getOrDefault(emptyList())
                    val topSongs = registerUnifiedTracks(topUnified, reset = true)
                    val albumsPage = albumsResult.getOrNull()
                    val albums = albumsPage?.albums?.map { it.toDisplayArtistAlbum() }.orEmpty()
                    val albumEntityIds = albumsPage?.albums
                        ?.mapNotNull { ua -> ua.id?.let { ua.toDisplayArtistAlbum().id to it.canonical } }
                        ?.toMap()
                        .orEmpty()
                    val similarUnified = similarResult.getOrDefault(emptyList())
                    val similar = similarUnified.map { it.toDisplayArtistInfo() }
                    val similarIds = similarUnified
                        .mapNotNull { ua -> ua.id?.let { ua.toDisplayArtistInfo().id to it.canonical } }
                        .toMap()

                    albumOffset = albums.size
                    _uiState.value = ArtistUiState.Success(
                        artist = detail.toDisplayArtistDetailInfo(legacyIdOverride = legacyId),
                        isFollowed = followResult.getOrDefault(detail.isFollowed),
                        fansCount = detail.fansCount,
                        topSongs = topSongs,
                        albums = albums,
                        albumsHasMore = albumsPage?.hasMore ?: false,
                        similarArtists = similar,
                        artistEntityId = parsed.canonical,
                        platform = MusicPlatform.KUGOU,
                        albumEntityIds = albumEntityIds,
                        similarArtistEntityIds = similarIds
                    )
                } else {
                    val err = (detailResult.exceptionOrNull() ?: topSongsResult.exceptionOrNull())
                        ?.toUserMessage(resourceProvider)
                        ?: resourceProvider.getString(AppString.ErrorBizDefault)
                    _uiState.value = ArtistUiState.Error(err)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e(TAG, "酷狗歌手详情加载失败 entityId=${parsed.canonical}", e)
                _uiState.value = ArtistUiState.Error(e.toUserMessage(resourceProvider))
            }
        }
    }

    fun loadArtistData(artistId: Long) {
        val neteaseEntity = ArtistId.netease(artistId)
        if (currentEntityId != neteaseEntity) {
            resetSongSearch()
            _selection.value = TrackSelection()
        }
        currentEntityId = neteaseEntity
        currentArtistId = artistId
        albumOffset = 0
        allSongsOffset = 0
        _unifiedTracks.value = emptyMap()
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

                val detailResult = detailDeferred.await()
                val fansResult = fansDeferred.await()
                val followResult = followDeferred.await()
                val topSongsResult = topSongsDeferred.await()
                val albumsResult = albumsDeferred.await()
                val similarResult = similarDeferred.await()

                if (detailResult.isSuccess && topSongsResult.isSuccess) {
                    val detail = detailResult.getOrThrow()
                    val fans = fansResult.getOrDefault(0L)
                    val isFollowed = followResult.getOrDefault(false)
                    val topSongs = topSongsResult.getOrThrow()
                    val albumsPage = albumsResult.getOrNull()
                    val similar = similarResult.getOrDefault(emptyList())

                    albumOffset = albumsPage?.albums?.size ?: 0
                    songQualityCache?.prefetch(topSongs)

                    _uiState.value = ArtistUiState.Success(
                        artist = detail,
                        isFollowed = isFollowed,
                        fansCount = fans,
                        topSongs = topSongs,
                        albums = albumsPage?.albums ?: emptyList(),
                        albumsHasMore = albumsPage?.hasMore ?: false,
                        similarArtists = similar,
                        artistEntityId = neteaseEntity.canonical,
                        platform = MusicPlatform.NETEASE
                    )
                } else {
                    val err = (detailResult.exceptionOrNull() ?: topSongsResult.exceptionOrNull())
                        ?.toUserMessage(resourceProvider)
                        ?: resourceProvider.getString(AppString.ErrorBizDefault)
                    _uiState.value = ArtistUiState.Error(err)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
        val entityId = currentEntityId
        if (entityId != null && entityId.platform == MusicPlatform.KUGOU && platformArtistRouter != null) {
            viewModelScope.launch {
                platformArtistRouter.getArtistAlbums(entityId, offset = albumOffset, limit = ALBUMS_PAGE_SIZE)
                    .first()
                    .onSuccess { page ->
                        val mapped = page.albums.map { it.toDisplayArtistAlbum() }
                        val newEntityIds = page.albums
                            .mapNotNull { ua -> ua.id?.let { ua.toDisplayArtistAlbum().id to it.canonical } }
                            .toMap()
                        albumOffset += mapped.size
                        val latest = _uiState.value as? ArtistUiState.Success ?: return@onSuccess
                        _uiState.value = latest.copy(
                            albums = latest.albums + mapped,
                            albumsHasMore = page.hasMore,
                            albumsLoadingMore = false,
                            albumEntityIds = latest.albumEntityIds + newEntityIds
                        )
                    }
                    .onFailure { e ->
                        AppLogger.w(TAG, "加载更多酷狗专辑失败 artistId=${entityId.canonical}", e)
                        val latest = _uiState.value as? ArtistUiState.Success ?: return@onFailure
                        _uiState.value = latest.copy(albumsLoadingMore = false)
                    }
            }
            return
        }
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

    // 「音乐」Tab 首次切到「全部」子 Tab 时触发首页加载，此后不重复加载
    fun loadAllSongsIfNeeded() {
        val state = _uiState.value as? ArtistUiState.Success ?: return
        if (state.allSongsLoaded || state.allSongsLoadingMore) return
        _uiState.value = state.copy(allSongsLoadingMore = true)
        val entityId = currentEntityId
        if (entityId != null && entityId.platform == MusicPlatform.KUGOU && platformArtistRouter != null) {
            viewModelScope.launch {
                platformArtistRouter.getArtistAllSongs(entityId, offset = 0, limit = ALL_SONGS_PAGE_SIZE)
                    .first()
                    .onSuccess { page ->
                        val songs = registerUnifiedTracks(page.songs)
                        allSongsOffset = songs.size
                        val latest = _uiState.value as? ArtistUiState.Success ?: return@onSuccess
                        _uiState.value = latest.copy(
                            allSongs = songs,
                            allSongsHasMore = page.hasMore,
                            allSongsLoadingMore = false,
                            allSongsLoaded = true
                        )
                    }
                    .onFailure { e ->
                        AppLogger.w(TAG, "加载全部酷狗歌曲失败 artistId=${entityId.canonical}", e)
                        val latest = _uiState.value as? ArtistUiState.Success ?: return@onFailure
                        _uiState.value = latest.copy(allSongsLoadingMore = false, allSongsLoaded = true)
                    }
            }
            return
        }
        viewModelScope.launch {
            artistRepository.getArtistAllSongs(currentArtistId, offset = 0, limit = ALL_SONGS_PAGE_SIZE)
                .first()
                .onSuccess { page ->
                    songQualityCache?.prefetch(page.songs)
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
        val entityId = currentEntityId
        if (entityId != null && entityId.platform == MusicPlatform.KUGOU && platformArtistRouter != null) {
            viewModelScope.launch {
                platformArtistRouter.getArtistAllSongs(entityId, offset = allSongsOffset, limit = ALL_SONGS_PAGE_SIZE)
                    .first()
                    .onSuccess { page ->
                        val songs = registerUnifiedTracks(page.songs)
                        allSongsOffset += songs.size
                        val latest = _uiState.value as? ArtistUiState.Success ?: return@onSuccess
                        _uiState.value = latest.copy(
                            allSongs = (latest.allSongs + songs).distinctBy { it.id },
                            allSongsHasMore = page.hasMore,
                            allSongsLoadingMore = false
                        )
                    }
                    .onFailure { e ->
                        AppLogger.w(TAG, "加载更多酷狗全部歌曲失败 artistId=${entityId.canonical}", e)
                        val latest = _uiState.value as? ArtistUiState.Success ?: return@onFailure
                        _uiState.value = latest.copy(allSongsLoadingMore = false)
                    }
            }
            return
        }
        viewModelScope.launch {
            artistRepository.getArtistAllSongs(currentArtistId, offset = allSongsOffset, limit = ALL_SONGS_PAGE_SIZE)
                .first()
                .onSuccess { page ->
                    songQualityCache?.prefetch(page.songs)
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
        val entityId = currentEntityId
        if (entityId != null && entityId.platform == MusicPlatform.KUGOU && platformArtistRouter != null) {
            viewModelScope.launch {
                if (userPreferences.activeAccountOf(MusicPlatform.KUGOU).first() == null) {
                    _toastEvent.emit("请先登录酷狗账号")
                    return@launch
                }
                platformArtistRouter.subscribeArtist(entityId, targetSubscribe).collect { result ->
                    result.onSuccess {
                        val latest = _uiState.value as? ArtistUiState.Success ?: currentState
                        _uiState.value = latest.copy(isFollowed = targetSubscribe)
                        _toastEvent.emit(if (targetSubscribe) "已关注歌手" else "已取消关注歌手")
                    }.onFailure { e ->
                        _toastEvent.emit(e.toUserMessage(resourceProvider))
                    }
                }
            }
            return
        }
        viewModelScope.launch {
            artistRepository.subscribeArtist(artistId, targetSubscribe).collect { result ->
                result.onSuccess {
                    val latest = _uiState.value as? ArtistUiState.Success ?: currentState
                    _uiState.value = latest.copy(isFollowed = targetSubscribe)
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
            _unifiedTracks.value[t.id]?.let(QueueItem::fromUnified)
                ?: QueueItem(t.id, t.name, t.ar.joinToString { it.name }, t.al.picUrl)
        }
        val startIndex = allTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerManager.playQueue(queueItems, startIndex, artistName)
    }

    // 加入下一首播放
    fun addTrackToPlayNext(track: Track) {
        playerManager.addToPlayNext(listOf(track.toQueueItem()))
        viewModelScope.launch { _toastEvent.emit("已添加至下一首播放") }
    }

    // 一键收藏到“已点赞的歌曲”
    fun quickLike(songId: Long) {
        viewModelScope.launch {
            val unified = _unifiedTracks.value[songId]
            if (unified != null) {
                songCollectDelegate.quickLikeUnified(unified) { _toastEvent.emit(it) }
            } else {
                songCollectDelegate.quickLike(songId) { _toastEvent.emit(it) }
            }
        }
    }

    fun prepareCollectDialog(songId: Long) {
        viewModelScope.launch {
            val unified = _unifiedTracks.value[songId]
            if (unified != null) {
                songCollectDelegate.prepareUnified(unified, songLikeRepository.likedSongIds.value) { _toastEvent.emit(it) }
            } else {
                songCollectDelegate.prepare(songId, songLikeRepository.likedSongIds.value) { _toastEvent.emit(it) }
            }
        }
    }

    fun savePlaylistCollection(songId: Long, items: List<PlaylistCollectItem>, silent: Boolean = false) {
        viewModelScope.launch {
            val unified = _unifiedTracks.value[songId]
            if (unified != null || items.any { it.platform == MusicPlatform.KUGOU }) {
                val targetUnified = unified ?: UnifiedTrack(id = TrackId.netease(songId), name = "")
                songCollectDelegate.saveUnified(
                    track = targetUnified,
                    items = items,
                    likedSongIds = songLikeRepository.likedSongIds.value,
                    onToast = { _toastEvent.emit(it) },
                    onLikedChanged = { newLiked -> songLikeRepository.syncLikedSongIds(newLiked) },
                    silent = silent
                )
            } else {
                songCollectDelegate.save(
                    songId = songId,
                    items = items,
                    silent = silent,
                    likedSongIds = songLikeRepository.likedSongIds.value,
                    onToast = { _toastEvent.emit(it) },
                    onLikedChanged = { newLiked ->
                        songLikeRepository.syncLikedSongIds(newLiked)
                    }
                )
            }
        }
    }

    fun createPlaylistAndAddSong(name: String, songId: Long) {
        val defaultPlatform = _unifiedTracks.value[songId]?.platform
            ?.takeIf { it == MusicPlatform.KUGOU } ?: MusicPlatform.NETEASE
        createPlaylistAndAddSongForPlatform(defaultPlatform, name, songId)
    }

    fun createPlaylistAndAddSongForPlatform(platform: MusicPlatform, name: String, songId: Long) {
        viewModelScope.launch {
            val unified = _unifiedTracks.value[songId]
            if (unified != null || platform == MusicPlatform.KUGOU) {
                val targetUnified = unified ?: UnifiedTrack(id = TrackId.netease(songId), name = "")
                songCollectDelegate.createAndAddUnified(
                    name = name,
                    track = targetUnified,
                    targetPlatform = platform,
                    likedSongIds = songLikeRepository.likedSongIds.value
                ) { _toastEvent.emit(it) }
            } else {
                songCollectDelegate.createAndAdd(name, songId, songLikeRepository.likedSongIds.value) { _toastEvent.emit(it) }
            }
        }
    }

    // 歌曲"喜欢"开关，供「更多操作」菜单调用（与红心图标的收藏弹层入口独立）
    fun toggleLikeSong(songId: Long, like: Boolean) {
        viewModelScope.launch {
            val unified = _unifiedTracks.value[songId]
            if (unified != null && unified.platform == MusicPlatform.KUGOU) {
                songLikeRepository.likeTrack(
                    trackId = unified.id,
                    like = like,
                    title = unified.name,
                    kugouMeta = unified.kugouMeta,
                    legacyExternalId = songId
                ).collect { result ->
                    result.onSuccess {
                        _toastEvent.emit(if (like) "已添加到我喜欢的音乐" else "已从我喜欢的音乐中移除")
                    }.onFailure { e ->
                        _toastEvent.emit(e.toUserMessage(resourceProvider))
                    }
                }
                return@launch
            }
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
        val state = _uiState.value as? ArtistUiState.Success
        if (state?.platform == MusicPlatform.KUGOU) {
            val q = query.trim().lowercase()
            val matches = (state.allSongs + state.topSongs)
                .distinctBy { it.id }
                .filter { track ->
                    track.name.lowercase().contains(q) ||
                        track.al.name.lowercase().contains(q) ||
                        track.ar.any { it.name.lowercase().contains(q) }
                }
            _songSearch.value = ArtistSongSearchState(
                query = query,
                results = matches,
                isLoading = false,
                hasMore = false
            )
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
        if ((_uiState.value as? ArtistUiState.Success)?.platform == MusicPlatform.KUGOU) return
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
        songQualityCache?.prefetch(collected)
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
            val hasNetease = userPreferences.userProfile.first() != null
            val hasKugou = isKugouEnabled.value && userPreferences.activeAccountOf(MusicPlatform.KUGOU).first() != null
            if (needLogin && !hasNetease && !hasKugou) {
                _toastEvent.emit("请先登录账号")
                return@launch
            }
            if (block(tracks)) exitSelection()
        }
    }

    fun prepareImportTargets() {
        viewModelScope.launch {
            _importState.update { it.copy(isLoading = true) }
            val neteaseItems = if (userPreferences.userProfile.first() != null) {
                trackBatchActions.loadTargetPlaylists(null) { _toastEvent.emit(it) }
            } else {
                null
            }
            val unifiedItems = trackBatchActions.loadTargetUnifiedPlaylists(null) {
                if (neteaseItems == null) _toastEvent.emit(it)
            }
            _importState.value = PlaylistImportState(
                items = neteaseItems ?: _importState.value.items,
                unifiedItems = unifiedItems ?: _importState.value.unifiedItems,
                isLoading = false
            )
        }
    }

    fun batchPlayNext() = runBatch { tracks ->
        trackBatchActions.playNext(tracks, emptySet(), unifiedTracks = _unifiedTracks.value) { _toastEvent.emit(it) }
    }

    fun batchAddToPlaylist(targetPlaylistId: Long) = runBatch(needLogin = true) { tracks ->
        trackBatchActions.addToPlaylist(targetPlaylistId, tracks, unifiedTracks = _unifiedTracks.value) { _toastEvent.emit(it) }
    }

    fun batchAddToUnifiedPlaylist(targetPlaylistId: PlaylistId) = runBatch(needLogin = true) { tracks ->
        trackBatchActions.addToUnifiedPlaylist(
            targetPlaylistId = targetPlaylistId,
            tracks = tracks,
            unifiedTracks = _unifiedTracks.value
        ) { _toastEvent.emit(it) }
    }

    fun batchCreatePlaylistAndAdd(
        name: String,
        isPrivate: Boolean = false,
        targetPlatform: MusicPlatform = MusicPlatform.NETEASE
    ) = runBatch(needLogin = true) { tracks ->
        trackBatchActions.createPlaylistAndAdd(
            name = name,
            privacy = playlistPrivacyOf(isPrivate),
            tracks = tracks,
            unifiedTracks = _unifiedTracks.value,
            targetPlatform = targetPlatform
        ) { _toastEvent.emit(it) }
    }

    fun batchLike() = runBatch(needLogin = true) { tracks ->
        trackBatchActions.like(tracks, unifiedTracks = _unifiedTracks.value) { _toastEvent.emit(it) } > 0
    }

    fun batchDownload(level: String) = runBatch { tracks ->
        val artist = (_uiState.value as? ArtistUiState.Success)?.artist ?: return@runBatch false
        trackBatchActions.download(tracks, level, "artist_${artist.id}", artist.name) { _toastEvent.emit(it) }
    }

    fun handleLoginSuccess(cookies: String) {
        viewModelScope.launch {
            when (val result = syncProfileAfterLoginUseCase.syncAccount(cookies)) {
                is LoginSyncResult.Success -> {
                    _toastEvent.emit("登录成功，正在同步数据...")
                    // 同步红心列表并刷新歌手页，使关注态与红心态即时生效
                    loadLikedSongIds()
                    currentEntityId?.let { loadArtistData(it.canonical) }
                        ?: (uiState.value as? ArtistUiState.Success)?.let { successState ->
                            loadArtistData(successState.artist.id)
                        }
                }
                LoginSyncResult.LimitReached -> _toastEvent.emit("最多只能登录 5 个账户")
                LoginSyncResult.Failed -> _toastEvent.emit("登录失败，会话无效或网络异常，请重试")
            }
        }
    }
}
