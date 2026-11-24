package com.lin0721.linmusic.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.AccountEvents
import com.lin0721.linmusic.core.auth.AccountSwitcher
import com.lin0721.linmusic.core.auth.AuthRepository
import com.lin0721.linmusic.core.auth.LoginSyncResult
import com.lin0721.linmusic.core.auth.StoredAccount
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
import com.lin0721.linmusic.feature.home.domain.pickHotlist
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.OnlineStateProvider
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.ui.components.CollectPromptEvent
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.feature.playlist.domain.CollectionActionsDelegate
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.podcast.ui.PodcastPlayerController
import com.lin0721.linmusic.feature.podcast.ui.PodcastPlayerState
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
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import com.lin0721.linmusic.core.player.PersonalFmMode
import com.lin0721.linmusic.core.player.LocalRecentPlaylist
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.RecentFmTrack
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.repository.PlatformAlbumRouter
import com.lin0721.linmusic.core.platform.repository.PlatformDiscoverRouter
import com.lin0721.linmusic.core.platform.repository.PlatformLibraryRepository
import com.lin0721.linmusic.core.platform.repository.PlatformPlaylistRouter
import com.lin0721.linmusic.core.platform.repository.UnifiedRank
import com.lin0721.linmusic.core.platform.repository.UnifiedStyleRecommend
import com.lin0721.linmusic.feature.home.domain.HomeCardKind
import com.lin0721.linmusic.feature.home.domain.HomeCardTarget
import com.lin0721.linmusic.feature.home.domain.HomeSection
import com.lin0721.linmusic.feature.home.domain.HomeSectionCard
import com.lin0721.linmusic.feature.playlist.ui.KUGOU_DAILY_RECOMMEND_ID
import com.lin0721.linmusic.feature.playlist.ui.kugouRankEntityId
import com.lin0721.linmusic.core.kugou.runCatchingCancellable
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.flow.combine

private const val TAG = "HomeViewModel"

// 首页 ViewModel
class HomeViewModel(
    private val syncProfileAfterLoginUseCase: SyncProfileAfterLoginUseCase,
    private val accountSwitcher: AccountSwitcher,
    private val playbackRepository: PlaybackRepository,
    private val homeRepository: HomeRepository,
    private val recentRepository: RecentRepository,
    val playerManager: PlaybackController,
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val resourceProvider: ResourceProvider,
    private val loadLikedSongIdsUseCase: LoadLikedSongIdsUseCase,
    private val songLikeRepository: SongLikeRepository,
    private val songCollectDelegate: SongCollectDelegate,
    private val podcastPlayer: PodcastPlayerController,
    private val playlistRepository: PlaylistRepository,
    private val onlineState: OnlineStateProvider,
    private val userPlaylistRepository: UserPlaylistRepository,
    private val songDownloadManager: SongDownloader,
    private val playlistMutationBus: PlaylistMutationBus,
    private val settingsPreferences: SettingsPreferences,
    private val playbackPreferences: PlaybackPreferences,
    private val platformDiscoverRouter: PlatformDiscoverRouter? = null,
    private val platformPlaylistRouter: PlatformPlaylistRouter? = null,
    private val platformAlbumRouter: PlatformAlbumRouter? = null,
    private val platformLibraryRepository: PlatformLibraryRepository? = null
) : ViewModel() {

    private var loadHomeJob: Job? = null

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val userProfile: StateFlow<UserProfile?> = userPreferences.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val effectiveBrowsePlatformFlow = combine(
        userPreferences.currentPlatform,
        settingsPreferences.kugouPlatformEnabled
    ) { platform, kugouEnabled ->
        if (platform == MusicPlatform.KUGOU && !kugouEnabled) MusicPlatform.NETEASE else platform
    }.distinctUntilChanged()

    // 当前首页浏览平台，受 kugouPlatformEnabled 总开关门控
    val browsePlatform: StateFlow<MusicPlatform> = effectiveBrowsePlatformFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, MusicPlatform.NETEASE)

    val isKugouEnabled: StateFlow<Boolean> = settingsPreferences.kugouPlatformEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val kugouAccount: StateFlow<StoredAccount?> = userPreferences.activeAccountOf(MusicPlatform.KUGOU)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 数据照常加载，开关只决定是否显示，重新打开无需刷新
    val showRecentPlay: StateFlow<Boolean> = settingsPreferences.homeRecentPlayEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showForYou: StateFlow<Boolean> = settingsPreferences.homeForYouEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showSearchButton: StateFlow<Boolean> = settingsPreferences.homeSearchButtonEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // 平台无关的首页区块流，供桌面端与 Android 端统一渲染
    val sections: StateFlow<List<HomeSection>> = combine(
        _uiState,
        showRecentPlay
    ) { state, recentEnabled ->
        val data = (state as? HomeUiState.Success)?.data ?: return@combine emptyList()
        buildHomeSections(data, recentEnabled)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val personalFmMode: StateFlow<PersonalFmMode> = settingsPreferences.personalFmMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, PersonalFmMode.DEFAULT)
    val recentFmTracks: StateFlow<List<RecentFmTrack>> = playbackPreferences.recentFmTracks
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 榜单接口只返回前几首的歌名，热歌榜卡片要的榜首封面借歌单详情补，拿不到时为空串
    @OptIn(ExperimentalCoroutinesApi::class)
    val hotlistTopCoverUrl: StateFlow<String> = _uiState
        .mapNotNull { (it as? HomeUiState.Success)?.data?.toplistItems?.pickHotlist()?.id }
        .distinctUntilChanged()
        .mapLatest { id ->
            runCatching { playlistRepository.getPlaylistDetail(id).first() }
                .getOrNull()?.getOrNull()
                ?.tracks?.firstOrNull()?.al?.picUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { if ('?' in it) it else "$it?param=300y300" }
                .orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // mini 栏"收藏到歌单"弹层，跟全屏播放页共用同一套 SongCollectDelegate 机制，各自持一份独立实例；
    // 用 StateFlow 而不是普通 Set，mini 栏爱心图标要跟着这份数据变化重组
    val likedSongIds: StateFlow<Set<Long>> = songLikeRepository.likedSongIds
    val collectState: StateFlow<PlaylistCollectState> = songCollectDelegate.state
    val collectPromptEvent: SharedFlow<CollectPromptEvent> = songCollectDelegate.collectPromptEvent

    // 迷你条的播客订阅按钮，状态与全屏播放页共用
    val podcastState: StateFlow<PodcastPlayerState> = podcastPlayer.state

    // 用户已创建与已收藏的歌单 ID 集合，供首页推荐卡片和最近播放判断是否展示“收藏歌单”
    private val _subscribedPlaylistIds = MutableStateFlow<Set<Long>>(emptySet())
    val subscribedPlaylistIds: StateFlow<Set<Long>> = _subscribedPlaylistIds.asStateFlow()

    fun toggleSubscribe() = podcastPlayer.toggleSubscribe { _toastEvent.emit(it) }

    init {
        viewModelScope.launch {
            var lastLoadedPlatform: MusicPlatform? = null
            effectiveBrowsePlatformFlow.collect { platform ->
                if (lastLoadedPlatform != platform) {
                    lastLoadedPlatform = platform
                    loadHomeData(refresh = false)
                }
            }
        }
        viewModelScope.launch {
            playerManager.initController()
        }
        viewModelScope.launch {
            playbackRepository.playlistRecorded.collect { refreshRecentPlaylists() }
        }
        // 酷狗本机最近播放记录变化时只替换该区块
        viewModelScope.launch {
            combine(
                playbackPreferences.recentPlaylists,
                userPreferences.activeAccountOf(MusicPlatform.KUGOU)
            ) { all, account -> kugouRecentOf(all, account?.accountId?.canonical) }
                .distinctUntilChanged()
                .collect { latest ->
                    val current = _uiState.value as? HomeUiState.Success ?: return@collect
                    if (current.data.browsePlatform != MusicPlatform.KUGOU) return@collect
                    if (current.data.kugouRecentPlaylists != latest) {
                        _uiState.value = HomeUiState.Success(current.data.copy(kugouRecentPlaylists = latest))
                    }
                }
        }
        // 离线转在线后重新拉取，替换离线时展示的缓存；首页尚未加载成功时走完整加载
        viewModelScope.launch {
            var wasOnline = onlineState.isOnline()
            onlineState.online.collect { online ->
                if (online && !wasOnline) {
                    if (_uiState.value is HomeUiState.Success) refreshHomeData() else loadHomeData()
                }
                wasOnline = online
            }
        }
        viewModelScope.launch {
            userProfile.collectLatest { profile ->
                if (profile != null) {
                    loadLikedSongIdsUseCase()
                    userPlaylistRepository.getUserPlaylists(profile.uid).collect { result ->
                        result.getOrNull()?.let { list ->
                            _subscribedPlaylistIds.value = list.map { it.id }.toSet()
                        }
                    }
                } else {
                    _subscribedPlaylistIds.value = emptySet()
                }
            }
        }
        viewModelScope.launch {
            AccountEvents.switched.collect {
                if (resolveEffectiveBrowsePlatform() == MusicPlatform.NETEASE) {
                    loadHomeData(refresh = true)
                }
            }
        }
        viewModelScope.launch {
            AccountEvents.platformSwitched.collect { event ->
                if (event.platform == MusicPlatform.KUGOU && resolveEffectiveBrowsePlatform() == MusicPlatform.KUGOU) {
                    loadHomeData(refresh = true)
                }
            }
        }
    }

    private suspend fun resolveEffectiveBrowsePlatform(): MusicPlatform {
        val raw = userPreferences.currentPlatform.first()
        val kugouEnabled = settingsPreferences.kugouPlatformEnabled.first()
        return if (raw == MusicPlatform.KUGOU && kugouEnabled) MusicPlatform.KUGOU else MusicPlatform.NETEASE
    }

    // 一键收藏到“已点赞的歌曲”
    fun quickLike(songId: Long) {
        viewModelScope.launch {
            songCollectDelegate.quickLike(songId) { _toastEvent.emit(it) }
        }
    }

    fun prepareCollectDialog(songId: Long) {
        viewModelScope.launch {
            songCollectDelegate.prepare(songId, songLikeRepository.likedSongIds.value) { _toastEvent.emit(it) }
        }
    }

    fun savePlaylistCollection(songId: Long, items: List<PlaylistCollectItem>, silent: Boolean = false) {
        viewModelScope.launch {
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

    fun createPlaylistAndAddSong(name: String, songId: Long) {
        viewModelScope.launch {
            songCollectDelegate.createAndAdd(name, songId, songLikeRepository.likedSongIds.value) { _toastEvent.emit(it) }
        }
    }

    fun loadHomeData(refresh: Boolean = false) {
        loadHomeJob?.cancel()
        _uiState.value = HomeUiState.Loading

        loadHomeJob = viewModelScope.launch {
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

    // 仅替换最近播放一块；失败或首页尚未加载完时保持原状
    private suspend fun refreshRecentPlaylists() {
        val current = _uiState.value as? HomeUiState.Success ?: return
        if (current.data.browsePlatform != MusicPlatform.NETEASE) return
        val latest = runCatching { recentRepository.getRecentPlaylists().first() }
            .getOrNull()?.getOrNull() ?: return
        val after = _uiState.value as? HomeUiState.Success ?: return
        if (after.data.browsePlatform == MusicPlatform.NETEASE) {
            _uiState.value = HomeUiState.Success(after.data.copy(recentPlaylists = latest))
        }
    }

    // 货架序列是整页主数据源，它失败才算整页失败；其余几项各自兜底不影响渲染
    private suspend fun fetchHomeFeedData(refresh: Boolean): Result<HomeFeedData> = try {
        val targetPlatform = resolveEffectiveBrowsePlatform()
        val discoverRouter = platformDiscoverRouter
        if (targetPlatform == MusicPlatform.KUGOU && discoverRouter != null) {
            coroutineScope {
                val feedDeferred = async {
                    discoverRouter.getDiscoverFeed(MusicPlatform.KUGOU, refresh).first()
                }
                val recentPlaylistsDeferred = async {
                    val accountKey = userPreferences.activeAccountOf(MusicPlatform.KUGOU)
                        .first()?.accountId?.canonical
                    runCatchingCancellable {
                        kugouRecentOf(playbackPreferences.recentPlaylists.first(), accountKey)
                    }.getOrDefault(emptyList())
                }
                val feedResult = feedDeferred.await()
                val recentPlaylists = recentPlaylistsDeferred.await()
                feedResult.map { feed ->
                    HomeFeedData(
                        browsePlatform = MusicPlatform.KUGOU,
                        kugouDiscoverTracks = feed.discoverTracks,
                        kugouNewSongs = feed.newSongs,
                        kugouNewAlbums = feed.newAlbums,
                        kugouStyleRecommend = feed.styleRecommend,
                        kugouDailySongs = feed.dailySongs,
                        kugouPlaylists = feed.recommendPlaylists,
                        kugouToplists = feed.toplists,
                        kugouRecentPlaylists = recentPlaylists
                    )
                }
            }
        } else {
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
                            hasMore = blockPage.hasMore,
                            browsePlatform = MusicPlatform.NETEASE
                        )
                    )
                } else {
                    Result.failure(
                        blockPageResult.exceptionOrNull()
                            ?: RuntimeException(resourceProvider.getString(AppString.ErrorBizDefault))
                    )
                }
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // 滚到底时追加下一页货架。服务端目前只有两页，翻完 hasMore 即为 false
    fun loadMoreShelves() {
        val current = _uiState.value as? HomeUiState.Success ?: return
        if (current.data.browsePlatform != MusicPlatform.NETEASE) return
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
        if (state is HomeUiState.Success) {
            if (state.data.browsePlatform == MusicPlatform.KUGOU && state.data.kugouDailySongs.isNotEmpty()) {
                val queueItems = state.data.kugouDailySongs.map { QueueItem.fromUnified(it.track) }
                playerManager.playQueue(queueItems, index.coerceIn(0, queueItems.size - 1), "每日推荐")
                return
            }
            if (state.data.dailySongs.isNotEmpty()) {
                val queueItems = state.data.dailySongs.map { song ->
                    QueueItem(song.id, song.name, song.ar.joinToString { it.name }, song.al.picUrl)
                }
                playerManager.playQueue(queueItems, index.coerceIn(0, queueItems.size - 1), "每日推荐")
                return
            }
        }
        viewModelScope.launch { _toastEvent.emit("每日推荐暂无歌曲") }
    }

    fun playKugouDiscoverTrack(index: Int = 0) {
        val state = uiState.value
        if (state is HomeUiState.Success && state.data.kugouDiscoverTracks.isNotEmpty()) {
            val queueItems = state.data.kugouDiscoverTracks.map(QueueItem::fromUnified)
            playerManager.playQueue(queueItems, index.coerceIn(0, queueItems.size - 1), "酷狗发现")
        } else {
            viewModelScope.launch { _toastEvent.emit("暂无可播放的发现推荐歌曲") }
        }
    }

    fun addUnifiedTrackToPlayNext(track: UnifiedTrack) {
        playerManager.addToPlayNext(listOf(QueueItem.fromUnified(track)))
        viewModelScope.launch { _toastEvent.emit("已添加至下一首播放") }
    }

    fun playKugouRank(rank: UnifiedRank, startIndex: Int = 0) {
        val router = platformDiscoverRouter ?: return
        viewModelScope.launch {
            router.getRankTracks(rank, page = 1, pageSize = 50).first().fold(
                onSuccess = { page ->
                    if (page.tracks.isEmpty()) {
                        _toastEvent.emit("榜单暂无曲目")
                    } else {
                        val queueItems = page.tracks.map(QueueItem::fromUnified)
                        val source = PlaySource(
                            kind = PlaySource.Kind.RANK,
                            id = 0L,
                            name = rank.name,
                            coverUrl = rank.coverUrl,
                            platform = MusicPlatform.KUGOU,
                            rawId = kugouRankEntityId(rank)
                        )
                        playerManager.playQueue(queueItems, startIndex.coerceIn(0, queueItems.size - 1), rank.name, source)
                    }
                },
                onFailure = { _toastEvent.emit(it.toUserMessage(resourceProvider)) }
            )
        }
    }

    private val collectionActions = CollectionActionsDelegate(
        playlistRepository,
        playerManager,
        songDownloadManager,
        playlistMutationBus,
        resourceProvider,
        platformPlaylistRouter,
        platformAlbumRouter
    )

    // 卡片上直接播放歌单/专辑：先拉详情，歌单还要补全被服务端截断的曲目，再整列起播
    fun playCollection(id: Long, isAlbum: Boolean) {
        viewModelScope.launch { collectionActions.play(id, isAlbum) { _toastEvent.emit(it) } }
    }

    fun playCollectionEntity(entityId: String, isAlbum: Boolean) {
        viewModelScope.launch { collectionActions.playEntity(entityId, isAlbum) { _toastEvent.emit(it) } }
    }

    // 歌单或专辑全部加入下一首播放
    fun addCollectionToPlayNext(id: Long, isAlbum: Boolean) {
        viewModelScope.launch { collectionActions.addToPlayNext(id, isAlbum) { _toastEvent.emit(it) } }
    }

    fun addCollectionEntityToPlayNext(entityId: String, isAlbum: Boolean) {
        viewModelScope.launch { collectionActions.addEntityToPlayNext(entityId, isAlbum) { _toastEvent.emit(it) } }
    }

    // 批量下载歌单或专辑
    fun downloadCollection(id: Long, isAlbum: Boolean, level: String) {
        viewModelScope.launch {
            collectionActions.download(id, isAlbum, level, "home_collection_$id") { _toastEvent.emit(it) }
        }
    }

    // 收藏歌单
    fun subscribePlaylist(playlistId: Long) {
        viewModelScope.launch {
            if (collectionActions.subscribePlaylist(playlistId) { _toastEvent.emit(it) }) {
                _subscribedPlaylistIds.value = _subscribedPlaylistIds.value + playlistId
            }
        }
    }

    fun subscribePlaylistEntity(entityId: String) {
        viewModelScope.launch {
            collectionActions.subscribeEntity(entityId) { _toastEvent.emit(it) }
        }
    }

    // 播放货架里的单曲，队列取同一货架内的全部歌曲卡片，播完能自动接续下一首
    fun playShelfSong(shelfTitle: String, songs: List<HomeCard.Song>, song: HomeCard.Song) {
        val queueItems = songs.map { QueueItem(it.id, it.title, "", it.coverUrl) }
        val startIndex = songs.indexOf(song).coerceAtLeast(0)
        playerManager.playQueue(queueItems, startIndex, playContext = shelfTitle)
    }

    fun addShelfSongToPlayNext(song: HomeCard.Song) {
        playerManager.addToPlayNext(listOf(QueueItem(song.id, song.title, "", song.coverUrl)))
        viewModelScope.launch { _toastEvent.emit("已添加至下一首播放") }
    }

    fun playShelfVoice(shelfTitle: String, voices: List<HomeCard.Voice>, voice: HomeCard.Voice) {
        val queueItems = voices.map { QueueItem(it.songId, it.title, it.caption, it.coverUrl) }
        val startIndex = voices.indexOf(voice).coerceAtLeast(0)
        playerManager.playQueue(queueItems, startIndex, playContext = shelfTitle)
    }

    fun addShelfVoiceToPlayNext(voice: HomeCard.Voice) {
        playerManager.addToPlayNext(listOf(QueueItem(voice.songId, voice.title, voice.caption, voice.coverUrl)))
        viewModelScope.launch { _toastEvent.emit("已添加至下一首播放") }
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun handleLoginSuccess(cookies: String) {
        viewModelScope.launch {
            when (val result = syncProfileAfterLoginUseCase.syncAccount(cookies)) {
                is LoginSyncResult.Success -> {
                    _toastEvent.emit("登录成功，欢迎回来，${result.profile.nickname}！")
                }
                LoginSyncResult.LimitReached -> {
                    _toastEvent.emit("最多只能登录 5 个账户")
                }
                LoginSyncResult.Failed -> {
                    _toastEvent.emit("登录失败，会话无效或网络异常，请重试")
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            accountSwitcher.logoutCurrent()
            _toastEvent.emit("已退出当前账户")
        }
    }

    // 开启相似歌曲漫游
    fun startRoaming() {
        val current = playerManager.nowPlaying.value
        if (current != null) {
            val songId = current.songId ?: return
            val title = current.title
            val artist = current.artist
            val coverUrl = current.artworkUri.orEmpty()
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
        val current = playerManager.nowPlaying.value
        if (current != null) {
            val songId = current.songId ?: return
            viewModelScope.launch {
                playbackRepository.getIntelligenceSongs(songId, 0).collect { result ->
                    result.onSuccess { tracks ->
                        if (tracks.isNotEmpty()) {
                            val currentItem = QueueItem(
                                songId,
                                current.title,
                                current.artist,
                                current.artworkUri.orEmpty()
                            )
                            val items = listOf(currentItem) + tracks.map { track ->
                                QueueItem(track.id, track.name, track.ar.joinToString("/") { it.name }, track.al.picUrl)
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
                            playerManager.playQueue(items, 0, playContext = PlaybackController.CONTEXT_INTELLIGENCE)
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

    // 开启私人FM；未登录时仅提示，不修改播放队列
    fun startPersonalFm(mode: PersonalFmMode? = null) {
        viewModelScope.launch {
            val targetPlatform = resolveEffectiveBrowsePlatform()
            if (targetPlatform == MusicPlatform.KUGOU && platformDiscoverRouter != null) {
                if (userPreferences.activeAccountOf(MusicPlatform.KUGOU).first() == null) {
                    _toastEvent.emit("请先登录酷狗账号")
                    return@launch
                }
                val targetMode = mode ?: settingsPreferences.personalFmMode.first()
                platformDiscoverRouter.getPersonalFmSongs(MusicPlatform.KUGOU, targetMode).first().fold(
                    onSuccess = { tracks ->
                        val items = tracks.map(QueueItem::fromUnified)
                            .filter { it.effectiveTrackId.rawId.isNotBlank() }
                            .distinctBy { it.effectiveTrackId.canonical }
                        if (items.isNotEmpty()) {
                            playerManager.startPersonalFm(items, MusicPlatform.KUGOU)
                        } else {
                            _toastEvent.emit("暂无可播放的私人FM歌曲")
                        }
                    },
                    onFailure = { _toastEvent.emit(it.toUserMessage(resourceProvider)) }
                )
                return@launch
            }

            if (userPreferences.userProfile.first() == null) {
                _toastEvent.emit("请先登录账号")
                return@launch
            }
            if (playerManager.playContext.value == PlaybackController.CONTEXT_PERSONAL_FM && mode != null) {
                playerManager.switchPersonalFmMode(mode)
                return@launch
            }
            val targetMode = mode ?: settingsPreferences.personalFmMode.first()
            playbackRepository.getPersonalFmSongs(targetMode).collect { result ->
                result.onSuccess { tracks ->
                    val items = tracks
                        .filter { it.id > 0L }
                        .distinctBy { it.id }
                        .map { track ->
                            QueueItem(
                                songId = track.id,
                                title = track.name,
                                artist = track.ar.joinToString("/") { it.name },
                                coverUrl = track.al.picUrl
                            )
                        }
                    if (items.isNotEmpty()) {
                        if (mode != null) {
                            playerManager.switchPersonalFmMode(targetMode)
                        }
                        playerManager.startPersonalFm(items, MusicPlatform.NETEASE)
                    } else {
                        _toastEvent.emit("暂无可播放的私人FM歌曲")
                    }
                }.onFailure {
                    _toastEvent.emit(it.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun switchPersonalFmMode(mode: PersonalFmMode) {
        viewModelScope.launch {
            val targetPlatform = resolveEffectiveBrowsePlatform()
            if (targetPlatform == MusicPlatform.KUGOU) {
                if (userPreferences.activeAccountOf(MusicPlatform.KUGOU).first() == null) {
                    _toastEvent.emit("请先登录酷狗账号")
                    return@launch
                }
            } else if (userPreferences.userProfile.first() == null) {
                _toastEvent.emit("请先登录账号")
                return@launch
            }
            if (playerManager.playContext.value == PlaybackController.CONTEXT_PERSONAL_FM) {
                playerManager.switchPersonalFmMode(mode)
            } else {
                startPersonalFm(mode)
            }
        }
    }

    fun trashCurrentFmSong() {
        playerManager.trashCurrentFmSong()
    }

    fun disablePersonalFm() {
        playerManager.disablePersonalFm()
    }

    // 统一播放卡片动作，按平台分派到网易或酷狗实现
    fun playCard(
        card: HomeSectionCard,
        shelfCards: List<HomeSectionCard> = emptyList(),
        shelfTitle: String = ""
    ) {
        when (card.kind) {
            HomeCardKind.PLAYLIST, HomeCardKind.ALBUM -> {
                val isAlbum = card.kind == HomeCardKind.ALBUM
                if (card.platform == MusicPlatform.NETEASE) {
                    val id = card.entityId.toLongOrNull() ?: return
                    playCollection(id, isAlbum = isAlbum)
                } else {
                    playCollectionEntity(card.entityId, isAlbum = isAlbum)
                }
            }
            HomeCardKind.RANK -> {
                if (card.platform == MusicPlatform.KUGOU) {
                    val rank = (_uiState.value as? HomeUiState.Success)
                        ?.data?.kugouToplists
                        ?.firstOrNull { kugouRankEntityId(it) == card.entityId }
                    if (rank != null) playKugouRank(rank)
                }
            }
            HomeCardKind.SONG -> {
                if (card.platform == MusicPlatform.KUGOU) {
                    val data = (_uiState.value as? HomeUiState.Success)?.data ?: return
                    val tracks = resolveKugouShelfTracks(data, card.entityId, shelfCards)
                    val index = tracks.indexOfFirst { it.id.canonical == card.entityId }
                    if (index >= 0) {
                        val queueItems = tracks.map(QueueItem::fromUnified)
                        playerManager.playQueue(
                            queueItems,
                            index,
                            shelfTitle.ifBlank { "酷狗发现" }
                        )
                    }
                } else {
                    val songs = shelfCards.filter { it.kind == HomeCardKind.SONG }.mapNotNull { item ->
                        val id = item.entityId.toLongOrNull() ?: return@mapNotNull null
                        HomeCard.Song(id = id, title = item.title, coverUrl = item.coverUrl, caption = item.caption)
                    }
                    val target = card.entityId.toLongOrNull()?.let { id ->
                        songs.firstOrNull { it.id == id }
                    }
                    if (target != null && songs.isNotEmpty()) {
                        playShelfSong(shelfTitle, songs, target)
                    }
                }
            }
            HomeCardKind.VOICE -> {
                val voices = shelfCards.filter { it.kind == HomeCardKind.VOICE }.mapNotNull { item ->
                    val id = item.entityId.toLongOrNull() ?: return@mapNotNull null
                    val songId = item.voiceSongId ?: return@mapNotNull null
                    HomeCard.Voice(id = id, songId = songId, title = item.title, coverUrl = item.coverUrl, caption = item.caption)
                }
                val target = card.entityId.toLongOrNull()?.let { id ->
                    voices.firstOrNull { it.id == id }
                }
                if (target != null && voices.isNotEmpty()) {
                    playShelfVoice(shelfTitle, voices, target)
                }
            }
        }
    }

    // 统一加入下一首播放动作，按平台分派
    fun addCardToPlayNext(card: HomeSectionCard) {
        when (card.kind) {
            HomeCardKind.PLAYLIST, HomeCardKind.ALBUM -> {
                val isAlbum = card.kind == HomeCardKind.ALBUM
                if (card.platform == MusicPlatform.NETEASE) {
                    val id = card.entityId.toLongOrNull() ?: return
                    addCollectionToPlayNext(id, isAlbum = isAlbum)
                } else {
                    addCollectionEntityToPlayNext(card.entityId, isAlbum = isAlbum)
                }
            }
            HomeCardKind.SONG -> {
                if (card.platform == MusicPlatform.KUGOU) {
                    val data = (_uiState.value as? HomeUiState.Success)?.data ?: return
                    val track = resolveKugouShelfTracks(data, card.entityId, emptyList())
                        .firstOrNull { it.id.canonical == card.entityId }
                    if (track != null) addUnifiedTrackToPlayNext(track)
                } else {
                    val id = card.entityId.toLongOrNull() ?: return
                    addShelfSongToPlayNext(
                        HomeCard.Song(id = id, title = card.title, coverUrl = card.coverUrl, caption = card.caption)
                    )
                }
            }
            HomeCardKind.VOICE -> {
                val id = card.entityId.toLongOrNull() ?: return
                val songId = card.voiceSongId ?: return
                addShelfVoiceToPlayNext(
                    HomeCard.Voice(id = id, songId = songId, title = card.title, coverUrl = card.coverUrl, caption = card.caption)
                )
            }
            HomeCardKind.RANK -> Unit
        }
    }

    // 返回平台无关的打开目标，供界面层导航
    fun openTargetOf(card: HomeSectionCard): HomeCardTarget {
        return when (card.kind) {
            HomeCardKind.PLAYLIST, HomeCardKind.RANK -> {
                HomeCardTarget.PlaylistEntity(entityId = card.entityId, title = card.title, isAlbum = false)
            }
            HomeCardKind.ALBUM -> {
                HomeCardTarget.PlaylistEntity(entityId = card.entityId, title = card.title, isAlbum = true)
            }
            HomeCardKind.SONG, HomeCardKind.VOICE -> HomeCardTarget.None
        }
    }

    private fun buildHomeSections(data: HomeFeedData, showRecentPlay: Boolean): List<HomeSection> {
        return if (data.browsePlatform == MusicPlatform.KUGOU) {
            buildKugouSections(data)
        } else {
            buildNeteaseSections(data, showRecentPlay)
        }
    }

    private fun buildNeteaseSections(data: HomeFeedData, showRecentPlay: Boolean): List<HomeSection> {
        val result = mutableListOf<HomeSection>()
        if (showRecentPlay && data.recentPlaylists.isNotEmpty()) {
            result += HomeSection.Quick(
                items = data.recentPlaylists.map { playlist ->
                    HomeSectionCard(
                        platform = MusicPlatform.NETEASE,
                        entityId = playlist.id.toString(),
                        kind = HomeCardKind.PLAYLIST,
                        title = playlist.name,
                        coverUrl = playlist.coverUrl,
                        caption = playlist.creatorName
                    )
                }
            )
        }
        if (data.dailySongs.isNotEmpty()) {
            result += HomeSection.Daily(
                items = data.dailySongs.map { song ->
                    HomeSectionCard(
                        platform = MusicPlatform.NETEASE,
                        entityId = song.id.toString(),
                        kind = HomeCardKind.SONG,
                        title = song.name,
                        coverUrl = song.al.picUrl,
                        caption = song.ar.joinToString(" / ") { it.name }
                    )
                },
                platform = MusicPlatform.NETEASE
            )
        }
        if (data.shelves.isEmpty() && data.recommendPlaylists.isNotEmpty()) {
            result += HomeSection.Shelf(
                blockCode = "recommend",
                title = "推荐歌单",
                cards = data.recommendPlaylists.map { playlist ->
                    HomeSectionCard(
                        platform = MusicPlatform.NETEASE,
                        entityId = playlist.id.toString(),
                        kind = HomeCardKind.PLAYLIST,
                        title = playlist.name,
                        coverUrl = playlist.picUrl,
                        caption = ""
                    )
                }
            )
        }
        data.shelves.forEach { shelf ->
            result += HomeSection.Shelf(
                blockCode = shelf.blockCode,
                title = shelf.title,
                showRank = shelf.showRank,
                cards = shelf.cards.map { card ->
                    when (card) {
                        is HomeCard.Playlist -> HomeSectionCard(
                            platform = MusicPlatform.NETEASE,
                            entityId = card.id.toString(),
                            kind = HomeCardKind.PLAYLIST,
                            title = card.title,
                            coverUrl = card.coverUrl,
                            caption = card.caption
                        )
                        is HomeCard.Album -> HomeSectionCard(
                            platform = MusicPlatform.NETEASE,
                            entityId = card.id.toString(),
                            kind = HomeCardKind.ALBUM,
                            title = card.title,
                            coverUrl = card.coverUrl,
                            caption = card.caption
                        )
                        is HomeCard.Song -> HomeSectionCard(
                            platform = MusicPlatform.NETEASE,
                            entityId = card.id.toString(),
                            kind = HomeCardKind.SONG,
                            title = card.title,
                            coverUrl = card.coverUrl,
                            caption = card.caption
                        )
                        is HomeCard.Voice -> HomeSectionCard(
                            platform = MusicPlatform.NETEASE,
                            entityId = card.id.toString(),
                            kind = HomeCardKind.VOICE,
                            title = card.title,
                            coverUrl = card.coverUrl,
                            caption = card.caption,
                            voiceSongId = card.songId
                        )
                    }
                }
            )
        }
        return result
    }

    private fun kugouRecentOf(all: List<LocalRecentPlaylist>, accountKey: String?): List<LocalRecentPlaylist> {
        if (accountKey == null) return emptyList()
        return all.filter { it.platform == MusicPlatform.KUGOU && it.accountKey == accountKey }
            .sortedByDescending { it.playTime }
            .take(KUGOU_RECENT_LIMIT)
    }

    private fun buildKugouSections(data: HomeFeedData): List<HomeSection> {
        val result = mutableListOf<HomeSection>()
        val recentCards = data.kugouRecentPlaylists.take(KUGOU_RECENT_LIMIT).map { item ->
            val (kind, entityId) = when (item.kind) {
                PlaySource.Kind.ALBUM -> HomeCardKind.ALBUM to com.lin0721.linmusic.core.platform.AlbumId.of(MusicPlatform.KUGOU, item.effectiveRawId).canonical
                PlaySource.Kind.RANK -> HomeCardKind.RANK to item.effectiveRawId
                PlaySource.Kind.PLAYLIST -> HomeCardKind.PLAYLIST to com.lin0721.linmusic.core.platform.PlaylistId.of(MusicPlatform.KUGOU, item.effectiveRawId).canonical
            }
            HomeSectionCard(
                platform = MusicPlatform.KUGOU,
                entityId = entityId,
                kind = kind,
                title = item.name,
                coverUrl = item.coverUrl
            )
        }
        if (recentCards.isNotEmpty()) {
            result += HomeSection.Quick(items = recentCards)
        }

        if (data.kugouDailySongs.isNotEmpty()) {
            result += HomeSection.Daily(
                items = data.kugouDailySongs.map { daily ->
                    HomeSectionCard(
                        platform = MusicPlatform.KUGOU,
                        entityId = daily.track.id.canonical,
                        kind = HomeCardKind.SONG,
                        title = daily.track.name,
                        coverUrl = daily.track.coverUrl,
                        caption = daily.track.artists.joinToString(" / ") { it.name }
                    )
                },
                platform = MusicPlatform.KUGOU
            )
        }
        if (data.kugouDiscoverTracks.isNotEmpty()) {
            result += HomeSection.Shelf(
                blockCode = "kugou_discover_tracks",
                title = "发现新歌",
                cards = data.kugouDiscoverTracks.map { it.toHomeSongCard() }
            )
        }
        if (data.kugouNewSongs.isNotEmpty()) {
            result += HomeSection.Shelf(
                blockCode = "kugou_new_songs",
                title = "新歌速递",
                cards = data.kugouNewSongs.map { it.toHomeSongCard() }
            )
        }
        val styleRec = data.kugouStyleRecommend
        if (styleRec != null && styleRec.tracks.isNotEmpty()) {
            val styleTitle = if (styleRec.title.isNotBlank()) "风格推荐 · ${styleRec.title}" else "风格推荐"
            result += HomeSection.Shelf(
                blockCode = "kugou_style_recommend",
                title = styleTitle,
                cards = styleRec.tracks.map { it.toHomeSongCard() }
            )
        }
        if (data.kugouPlaylists.isNotEmpty()) {
            result += HomeSection.Shelf(
                blockCode = "kugou_playlists",
                title = "推荐歌单",
                cards = data.kugouPlaylists.map { playlist ->
                    HomeSectionCard(
                        platform = MusicPlatform.KUGOU,
                        entityId = playlist.id.canonical,
                        kind = HomeCardKind.PLAYLIST,
                        title = playlist.name,
                        coverUrl = playlist.coverUrl,
                        caption = playlist.creator?.nickname.orEmpty()
                    )
                }
            )
        }
        if (data.kugouNewAlbums.isNotEmpty()) {
            result += HomeSection.Shelf(
                blockCode = "kugou_new_albums",
                title = "新碟上架",
                cards = data.kugouNewAlbums.mapNotNull { album ->
                    val entityId = album.id?.canonical ?: return@mapNotNull null
                    HomeSectionCard(
                        platform = MusicPlatform.KUGOU,
                        entityId = entityId,
                        kind = HomeCardKind.ALBUM,
                        title = album.name,
                        coverUrl = album.coverUrl,
                        caption = album.artists.joinToString(" / ") { it.name }
                    )
                }
            )
        }
        if (data.kugouToplists.isNotEmpty()) {
            result += HomeSection.Shelf(
                blockCode = "kugou_toplists",
                title = "酷狗排行榜",
                showRank = true,
                cards = data.kugouToplists.map { rank ->
                    HomeSectionCard(
                        platform = MusicPlatform.KUGOU,
                        entityId = kugouRankEntityId(rank),
                        kind = HomeCardKind.RANK,
                        title = rank.name,
                        coverUrl = rank.coverUrl,
                        caption = rank.updateFrequency
                    )
                }
            )
        }
        return result
    }

    // 按所属货架的卡片顺序取曲目；无货架上下文时只取该曲
    private fun resolveKugouShelfTracks(
        data: HomeFeedData,
        entityId: String,
        shelfCards: List<HomeSectionCard>
    ): List<UnifiedTrack> {
        val byId = sequenceOf(
            data.kugouDiscoverTracks,
            data.kugouNewSongs,
            data.kugouStyleRecommend?.tracks.orEmpty(),
            data.kugouDailySongs.map { it.track }
        ).flatten().associateBy { it.id.canonical }
        if (shelfCards.isEmpty()) return listOfNotNull(byId[entityId])
        return shelfCards.mapNotNull { byId[it.entityId] }
    }

    private fun UnifiedTrack.toHomeSongCard(): HomeSectionCard = HomeSectionCard(
        platform = MusicPlatform.KUGOU,
        entityId = id.canonical,
        kind = HomeCardKind.SONG,
        title = name,
        coverUrl = coverUrl,
        caption = artists.joinToString(" / ") { it.name }
    )
}

private const val KUGOU_RECENT_LIMIT = 8
