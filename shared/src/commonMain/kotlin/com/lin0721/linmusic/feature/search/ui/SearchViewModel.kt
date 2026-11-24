package com.lin0721.linmusic.feature.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.LoginSyncResult
import com.lin0721.linmusic.core.auth.StoredAccount
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.deeplink.LinkPreviewResolver
import com.lin0721.linmusic.core.deeplink.LinkPreviewState
import com.lin0721.linmusic.core.deeplink.NeteaseLink
import com.lin0721.linmusic.core.deeplink.SongLinkTarget
import com.lin0721.linmusic.core.deeplink.enqueueLinkTrackNext
import com.lin0721.linmusic.core.deeplink.playLinkTrack
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.platform.ArtistId
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.repository.PlatformArtistRouter
import com.lin0721.linmusic.core.platform.repository.PlatformSearchRouter
import com.lin0721.linmusic.core.platform.repository.UnifiedSearchResultItem
import com.lin0721.linmusic.core.platform.repository.UnifiedSearchSuggestion
import com.lin0721.linmusic.core.platform.repository.UnifiedSearchType
import com.lin0721.linmusic.core.platform.toDeterministicLegacyId
import com.lin0721.linmusic.core.platform.toDisplayPlaylistDetail
import com.lin0721.linmusic.core.platform.toDisplayTrack
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.songquality.SongQualityCache
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.ExternalTrackResolver
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.PlatformSearchProvider
import com.lin0721.linmusic.core.source.SourcePreferences
import com.lin0721.linmusic.core.ui.components.CollectPromptEvent
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.feature.artist.data.ArtistRepository
import com.lin0721.linmusic.feature.playlist.domain.CollectionActionsDelegate
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.podcast.data.PodcastRepository
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.playPodcastPrograms
import com.lin0721.linmusic.feature.podcast.domain.toQueueItem
import com.lin0721.linmusic.feature.search.data.SearchHistoryPreferences
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.HotSearch
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchSuggestion
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 输入时展示的歌曲命中卡片数量
private const val SONG_HIT_LIMIT = 2

class SearchViewModel(
    private val repository: SearchRepository,
    private val historyPreferences: SearchHistoryPreferences,
    val playerManager: PlaybackController,
    userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider,
    private val songCollectDelegate: SongCollectDelegate,
    private val loadLikedSongIdsUseCase: LoadLikedSongIdsUseCase,
    private val songLikeRepository: SongLikeRepository,
    private val syncProfileAfterLoginUseCase: SyncProfileAfterLoginUseCase,
    private val externalResolver: ExternalTrackResolver? = null,
    private val searchProviders: List<PlatformSearchProvider> = emptyList(),
    private val settingsPreferences: SettingsPreferences? = null,
    private val sourcePreferences: SourcePreferences? = null,
    private val collectionActions: CollectionActionsDelegate? = null,
    private val artistRepository: ArtistRepository? = null,
    private val podcastRepository: PodcastRepository? = null,
    private val linkPreviewResolver: LinkPreviewResolver? = null,
    private val songQualityCache: SongQualityCache? = null,
    private val platformSearchRouter: PlatformSearchRouter? = null,
    private val platformArtistRouter: PlatformArtistRouter? = null
) : ViewModel() {

    val userProfile: StateFlow<UserProfile?> = userPreferences.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val kugouAccount: StateFlow<StoredAccount?> = userPreferences.activeAccountOf(MusicPlatform.KUGOU)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 搜索结果里的歌曲行同样需要红心外显与收藏弹层，跟 Artist/Playlist 共用同一套委托
    val likedSongIds: StateFlow<Set<Long>> = songLikeRepository.likedSongIds
    val likedTrackIds: StateFlow<Set<TrackId>> = songLikeRepository.likedTrackIds

    val collectState: StateFlow<PlaylistCollectState> = songCollectDelegate.state
    val collectPromptEvent: SharedFlow<CollectPromptEvent> = songCollectDelegate.collectPromptEvent

    val history: StateFlow<List<String>> = historyPreferences.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _discoveryState = MutableStateFlow<DiscoveryUiState>(DiscoveryUiState.Loading)
    val discoveryState: StateFlow<DiscoveryUiState> = _discoveryState.asStateFlow()

    private val _kugouDiscoveryState = MutableStateFlow<DiscoveryUiState>(DiscoveryUiState.Loading)
    val kugouDiscoveryState: StateFlow<DiscoveryUiState> = _kugouDiscoveryState.asStateFlow()

    private val _inputState = MutableStateFlow(SearchInputState())
    val inputState: StateFlow<SearchInputState> = _inputState.asStateFlow()

    // 结果页对应的已提交关键词，与输入框里还在编辑的草稿词分开，翻页与重试只认它
    private val _committedQuery = MutableStateFlow("")
    val committedQuery: StateFlow<String> = _committedQuery.asStateFlow()

    private val _mode = MutableStateFlow(SearchMode.Discovery)
    val mode: StateFlow<SearchMode> = _mode.asStateFlow()

    // 桌面端下拉浮层才需要输入时的歌曲命中卡片，其余端不额外发请求
    var songHitsEnabled: Boolean = false

    private val _resolvingTrackKey = MutableStateFlow<String?>(null)
    val resolvingTrackKey: StateFlow<String?> = _resolvingTrackKey.asStateFlow()
    private var externalPlayJob: Job? = null

    // 热搜第一名的首条单曲，头部卡取色与播放用
    private val _featuredTrack = MutableStateFlow<Track?>(null)
    val featuredTrack: StateFlow<Track?> = _featuredTrack.asStateFlow()

    private val _unifiedTracks = MutableStateFlow<Map<Long, UnifiedTrack>>(emptyMap())
    val unifiedTracks: StateFlow<Map<Long, UnifiedTrack>> = _unifiedTracks.asStateFlow()

    val isKugouEnabled: StateFlow<Boolean> = (settingsPreferences?.kugouPlatformEnabled ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val allExternalPlatforms = listOf(
        MusicPlatform.KUGOU,
        MusicPlatform.KUWO,
        MusicPlatform.QQ,
        MusicPlatform.LX
    )

    // 酷狗原生平台受 kugouPlatformEnabled 总开关门控，其余外部源受聚合搜索开关门控
    val searchPlatforms: StateFlow<List<MusicPlatform>> = combine(
        sourcePreferences?.searchAggregationEnabled ?: flowOf(false),
        searchProviders.firstOrNull { it.platform == MusicPlatform.LX }?.available ?: flowOf(false),
        settingsPreferences?.kugouPlatformEnabled ?: flowOf(true)
    ) { aggregationEnabled, pluginAvailable, kugouEnabled ->
        val hasKugouRouter = kugouEnabled && platformSearchRouter != null
        val external = if (aggregationEnabled) {
            val available = searchProviders.mapTo(hashSetOf()) { it.platform }
            allExternalPlatforms.filter {
                it != MusicPlatform.LX &&
                    (it != MusicPlatform.KUGOU || kugouEnabled) &&
                    (it in available || (it == MusicPlatform.KUGOU && hasKugouRouter))
            }
        } else if (hasKugouRouter) {
            listOf(MusicPlatform.KUGOU)
        } else {
            emptyList()
        }
        listOf(MusicPlatform.NETEASE) + external + listOfNotNull(MusicPlatform.LX.takeIf { pluginAvailable })
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(MusicPlatform.NETEASE))

    private val _selectedPlatform = MutableStateFlow(MusicPlatform.NETEASE)
    val selectedPlatform: StateFlow<MusicPlatform> = _selectedPlatform.asStateFlow()

    private val _externalResults: Map<MusicPlatform, MutableStateFlow<ExternalSearchUiState>> =
        allExternalPlatforms.associateWith { MutableStateFlow<ExternalSearchUiState>(ExternalSearchUiState.Idle) }
    val externalResults: Map<MusicPlatform, StateFlow<ExternalSearchUiState>> = _externalResults

    private val externalOffset = mutableMapOf<MusicPlatform, Int>()
    // 每个平台独立任务，切换 Tab 不会打断其他平台正在进行的加载
    private val externalSearchJobs = mutableMapOf<MusicPlatform, Job>()

    private val _selectedType = MutableStateFlow(SearchType.SONG)
    val selectedType: StateFlow<SearchType> = _selectedType.asStateFlow()

    private val _resultsByType: Map<SearchType, MutableStateFlow<SearchResultsUiState>> =
        SearchType.entries.associateWith { MutableStateFlow(SearchResultsUiState.Idle) }
    val resultsByType: Map<SearchType, StateFlow<SearchResultsUiState>> = _resultsByType

    private val _kugouSelectedType = MutableStateFlow(KugouSearchType.SONG)
    val kugouSelectedType: StateFlow<KugouSearchType> = _kugouSelectedType.asStateFlow()

    private val _kugouResultsByType: Map<KugouSearchType, MutableStateFlow<SearchResultsUiState>> =
        KugouSearchType.entries.associateWith { MutableStateFlow(SearchResultsUiState.Idle) }
    val kugouResultsByType: Map<KugouSearchType, StateFlow<SearchResultsUiState>> = _kugouResultsByType

    private val kugouOffsetByType = mutableMapOf<KugouSearchType, Int>()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    // 各 Tab 独立维护的分页 offset，按接口原始返回条数推进（见 SearchPageResult.rawFetchedCount 注释）
    private val offsetByType = mutableMapOf<SearchType, Int>()
    private var searchJob: Job? = null
    private var kugouSearchJob: Job? = null
    private var suggestJob: Job? = null
    private var hitsJob: Job? = null
    private var linkResolveJob: Job? = null
    // 须在 init 之前声明：Main.immediate 下 init 里的收集会同步执行到 loadDiscoveryData
    private var discoveryJob: Job? = null
    private var kugouDiscoveryJob: Job? = null
    private var kugouDiscoveryLoadedForHot: Boolean? = null
    private var keywordEnabled = true
    private var hotSearchEnabled = true
    private var playlistTagsEnabled = true
    private var suggestionEnabled = true
    // 默认标签跟随当前平台；手动选过标签后，当前平台再次变化前不覆盖
    private var followCurrentPlatform = true

    init {
        viewModelScope.launch {
            // 推荐词、热搜与歌单分类开关变化时重新加载发现页，首个值即触发首次加载
            combine(
                settingsPreferences?.searchDefaultKeywordEnabled ?: flowOf(true),
                settingsPreferences?.hotSearchEnabled ?: flowOf(true),
                settingsPreferences?.playlistTagsEnabled ?: flowOf(true)
            ) { keyword, hot, tags -> Triple(keyword, hot, tags) }
                .distinctUntilChanged()
                .collect { (keyword, hot, tags) ->
                    loadDiscoveryData(keyword, hot, tags)
                    if (_selectedPlatform.value == MusicPlatform.KUGOU && isKugouNativeActive()) {
                        loadKugouDiscoveryData(force = true)
                    } else {
                        kugouDiscoveryLoadedForHot = null
                    }
                }
        }
        viewModelScope.launch {
            (settingsPreferences?.searchSuggestionEnabled ?: flowOf(true))
                .distinctUntilChanged()
                .collect { enabled ->
                    suggestionEnabled = enabled
                    if (!enabled || _mode.value == SearchMode.Typing) requestSuggestions(_inputState.value.query)
                }
        }
        loadLikedSongIds()
        viewModelScope.launch {
            var lastCurrent: MusicPlatform? = null
            combine(userPreferences.currentPlatform, searchPlatforms) { current, platforms -> current to platforms }
                .collect { (current, platforms) ->
                    if (current != lastCurrent) {
                        lastCurrent = current
                        followCurrentPlatform = true
                    }
                    val desired = if (followCurrentPlatform) current else _selectedPlatform.value
                    val next = if (desired in platforms) desired else MusicPlatform.NETEASE
                    if (next != _selectedPlatform.value) {
                        _selectedPlatform.value = next
                        if (next == MusicPlatform.KUGOU && isKugouNativeActive()) {
                            loadKugouDiscoveryData()
                        }
                    }
                }
        }
        viewModelScope.launch {
            (settingsPreferences?.kugouPlatformEnabled ?: flowOf(true))
                .distinctUntilChanged()
                .collect { enabled ->
                    if (!enabled) {
                        clearKugouSearchState()
                    }
                }
        }
    }

    private fun isKugouNativeActive(): Boolean = isKugouEnabled.value && platformSearchRouter != null

    private fun clearKugouSearchState() {
        kugouSearchJob?.cancel()
        kugouDiscoveryJob?.cancel()
        kugouOffsetByType.clear()
        kugouDiscoveryLoadedForHot = null
        _kugouResultsByType.values.forEach { it.value = SearchResultsUiState.Idle }
        _kugouDiscoveryState.value = DiscoveryUiState.Success("", emptyList(), emptyList())
        _unifiedTracks.value = emptyMap()
    }

    private fun Track.toQueueItem(): QueueItem =
        _unifiedTracks.value[id]?.let(QueueItem::fromUnified)
            ?: QueueItem(id, name, ar.joinToString("/") { it.name }, al.picUrl)

    fun loadLikedSongIds() {
        viewModelScope.launch {
            loadLikedSongIdsUseCase()
        }
    }

    // 加入下一首播放
    fun addTrackToPlayNext(track: Track) {
        playerManager.addToPlayNext(listOf(track.toQueueItem()))
        viewModelScope.launch { _toastEvent.emit("已添加至下一首播放") }
    }

    // 一键收藏到“已点赞的歌曲”，原生酷狗与外部搜索结果按平台分流
    fun quickLike(songId: Long) {
        _unifiedTracks.value[songId]?.let { unified ->
            viewModelScope.launch { songCollectDelegate.quickLikeUnified(unified) { _toastEvent.emit(it) } }
            return
        }
        searchedExternal(songId)?.let { return quickLikeExternal(it) }
        viewModelScope.launch {
            songCollectDelegate.quickLike(songId) { _toastEvent.emit(it) }
        }
    }

    fun prepareCollectDialog(songId: Long) {
        _unifiedTracks.value[songId]?.let { unified ->
            viewModelScope.launch {
                songCollectDelegate.prepareUnified(unified, songLikeRepository.likedSongIds.value) { _toastEvent.emit(it) }
            }
            return
        }
        searchedExternal(songId)?.let { return prepareExternalCollectDialog(it) }
        viewModelScope.launch {
            songCollectDelegate.prepare(songId, songLikeRepository.likedSongIds.value) { _toastEvent.emit(it) }
        }
    }

    fun savePlaylistCollection(songId: Long, items: List<PlaylistCollectItem>, silent: Boolean = false) {
        val unified = _unifiedTracks.value[songId]
        if (unified != null) {
            viewModelScope.launch {
                songCollectDelegate.saveUnified(
                    track = unified,
                    items = items,
                    likedSongIds = songLikeRepository.likedSongIds.value,
                    onToast = { _toastEvent.emit(it) },
                    onLikedChanged = { songLikeRepository.syncLikedSongIds(it) },
                    silent = silent
                )
            }
            return
        }
        searchedExternal(songId)?.let { return saveExternalCollection(it, items, silent) }
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
        val unified = _unifiedTracks.value[songId]
        if (unified != null) {
            val defaultPlatform = unified.platform.takeIf { it == MusicPlatform.KUGOU } ?: MusicPlatform.NETEASE
            createPlaylistAndAddSongForPlatform(defaultPlatform, name, songId)
            return
        }
        searchedExternal(songId)?.let { return createPlaylistAndAddExternal(name, it) }
        viewModelScope.launch {
            songCollectDelegate.createAndAdd(name, songId, songLikeRepository.likedSongIds.value) { _toastEvent.emit(it) }
        }
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

    fun handleLoginSuccess(cookies: String) {
        viewModelScope.launch {
            when (syncProfileAfterLoginUseCase.syncAccount(cookies)) {
                is LoginSyncResult.Success -> {
                    _toastEvent.emit("登录成功，正在同步数据...")
                    loadLikedSongIds()
                }
                LoginSyncResult.LimitReached -> _toastEvent.emit("最多只能登录 5 个账户")
                LoginSyncResult.Failed -> _toastEvent.emit("登录失败，会话无效或网络异常，请重试")
            }
        }
    }

    private fun loadDiscoveryData(
        keywordEnabled: Boolean = this.keywordEnabled,
        hotEnabled: Boolean = this.hotSearchEnabled,
        tagsEnabled: Boolean = this.playlistTagsEnabled
    ) {
        this.keywordEnabled = keywordEnabled
        this.hotSearchEnabled = hotEnabled
        this.playlistTagsEnabled = tagsEnabled
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            if (!hotEnabled) _featuredTrack.value = null
            // 热搜与歌单分类均关闭时，发现页正文保持空白，不展示骨架屏或错误态
            val showAnyDiscoveryBody = hotEnabled || tagsEnabled
            if (showAnyDiscoveryBody) {
                _discoveryState.value = DiscoveryUiState.Loading
            } else {
                val currentKeyword = (_discoveryState.value as? DiscoveryUiState.Success)?.defaultKeyword.orEmpty()
                _discoveryState.value = DiscoveryUiState.Success(
                    defaultKeyword = if (keywordEnabled) currentKeyword else "",
                    hotSearches = emptyList(),
                    playlistTags = emptyList()
                )
            }

            val keywordDeferred = async { if (keywordEnabled) repository.getDefaultSearchKeyword().firstOrNull() else null }
            val hotSearchDeferred = async { if (hotEnabled) repository.getHotSearches().firstOrNull() else null }
            val tagsDeferred = async { if (tagsEnabled) repository.getPlaylistTags().firstOrNull() else null }

            val keywordResult = keywordDeferred.await()
            val hotSearchResult = hotSearchDeferred.await()
            val tagsResult = tagsDeferred.await()

            if (!showAnyDiscoveryBody) {
                _discoveryState.value = DiscoveryUiState.Success(
                    defaultKeyword = keywordResult?.getOrNull().orEmpty(),
                    hotSearches = emptyList(),
                    playlistTags = emptyList()
                )
                return@launch
            }

            val failures = listOfNotNull(keywordResult, hotSearchResult, tagsResult).mapNotNull { it.exceptionOrNull() }
            val allFailed = keywordResult?.getOrNull() == null &&
                hotSearchResult?.getOrNull() == null &&
                tagsResult?.getOrNull() == null

            if (allFailed && failures.isNotEmpty()) {
                _discoveryState.value = DiscoveryUiState.Error(failures.first().toUserMessage(resourceProvider))
                return@launch
            }

            val hotSearches = hotSearchResult?.getOrNull() ?: emptyList()
            _discoveryState.value = DiscoveryUiState.Success(
                defaultKeyword = keywordResult?.getOrNull().orEmpty(),
                hotSearches = hotSearches,
                playlistTags = tagsResult?.getOrNull() ?: emptyList()
            )
            failures.firstOrNull()?.let { _toastEvent.emit(it.toUserMessage(resourceProvider)) }
            hotSearches.firstOrNull()?.let { loadFeaturedTrack(it.keyword) }
        }
    }

    private fun loadKugouDiscoveryData(force: Boolean = false) {
        val router = platformSearchRouter ?: return
        if (!isKugouEnabled.value) {
            _kugouDiscoveryState.value = DiscoveryUiState.Success("", emptyList(), emptyList())
            return
        }
        if (!hotSearchEnabled) {
            _kugouDiscoveryState.value = DiscoveryUiState.Success("", emptyList(), emptyList())
            kugouDiscoveryLoadedForHot = false
            return
        }
        if (!force && kugouDiscoveryLoadedForHot == hotSearchEnabled && _kugouDiscoveryState.value is DiscoveryUiState.Success) {
            return
        }
        kugouDiscoveryJob?.cancel()
        kugouDiscoveryJob = viewModelScope.launch {
            _kugouDiscoveryState.value = DiscoveryUiState.Loading
            val res = router.getHotSearches(MusicPlatform.KUGOU).firstOrNull()
            res?.fold(
                onSuccess = { list ->
                    val hotSearches = list.map { item ->
                        HotSearch(
                            keyword = item.keyword,
                            score = item.score,
                            description = item.description,
                            iconUrl = item.iconUrl
                        )
                    }
                    kugouDiscoveryLoadedForHot = true
                    _kugouDiscoveryState.value = DiscoveryUiState.Success(
                        defaultKeyword = if (keywordEnabled) hotSearches.firstOrNull()?.keyword.orEmpty() else "",
                        hotSearches = hotSearches,
                        playlistTags = emptyList()
                    )
                },
                onFailure = { error ->
                    kugouDiscoveryLoadedForHot = null
                    _kugouDiscoveryState.value = DiscoveryUiState.Error(error.toUserMessage(resourceProvider))
                }
            ) ?: run {
                _kugouDiscoveryState.value = DiscoveryUiState.Error("酷狗热搜加载失败")
            }
        }
    }

    private suspend fun loadFeaturedTrack(keyword: String) {
        _featuredTrack.value = null
        repository.search(keyword, SearchType.SONG, offset = 0, limit = 1).firstOrNull()
            ?.getOrNull()
            ?.items
            ?.firstNotNullOfOrNull { (it as? SearchResultItem.SongItem)?.track }
            ?.let { track ->
                songQualityCache?.prefetch(listOf(track))
                _featuredTrack.value = track
            }
    }

    // 发现页加载失败时的重试入口，UI 层错误态按钮调用
    fun retryDiscovery() {
        if (_selectedPlatform.value == MusicPlatform.KUGOU && isKugouNativeActive()) {
            loadKugouDiscoveryData(force = true)
        } else {
            loadDiscoveryData()
        }
    }

    // 搜索结果加载失败时的重试入口：重跑当前 Tab 当前关键词，不重复写历史
    fun retrySearch() {
        val keyword = _committedQuery.value
        if (keyword.isBlank()) return
        if (_selectedPlatform.value == MusicPlatform.KUGOU && isKugouNativeActive()) {
            val kType = _kugouSelectedType.value
            kugouSearchJob?.cancel()
            kugouSearchJob = viewModelScope.launch { runKugouSearch(keyword, kType, isLoadMore = false) }
            return
        }
        val type = _selectedType.value
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(keyword, type, isLoadMore = false) }
    }

    fun activateSearch() {
        if (_mode.value == SearchMode.Discovery) _mode.value = SearchMode.Typing
    }

    // 结果页点输入框回到输入面板改词，已加载结果保留
    fun editQuery() {
        if (_mode.value != SearchMode.Results) return
        _mode.value = SearchMode.Typing
        val query = _inputState.value.query
        requestSuggestions(query)
        requestLinkDetection(query)
    }

    fun cancelSearch() {
        _mode.value = SearchMode.Discovery
        resetSearchState()
    }

    private fun resetSearchState() {
        searchJob?.cancel()
        kugouSearchJob?.cancel()
        suggestJob?.cancel()
        cancelExternalJobs()
        hitsJob?.cancel()
        linkResolveJob?.cancel()
        _inputState.value = SearchInputState(platform = _selectedPlatform.value)
        _committedQuery.value = ""
        offsetByType.clear()
        kugouOffsetByType.clear()
        externalOffset.clear()
        _unifiedTracks.value = emptyMap()
        _resultsByType.values.forEach { it.value = SearchResultsUiState.Idle }
        _kugouResultsByType.values.forEach { it.value = SearchResultsUiState.Idle }
        _externalResults.values.forEach { it.value = ExternalSearchUiState.Idle }
    }

    // 输入只拉联想，提交才搜索
    fun updateQuery(newQuery: String) {
        _inputState.value = _inputState.value.copy(query = newQuery)
        if (_mode.value == SearchMode.Results) _mode.value = SearchMode.Typing
        requestSuggestions(newQuery)
        requestLinkDetection(newQuery)
    }

    // 只改输入框草稿词与联想，不动当前模式与已加载的结果；桌面端下拉浮层专用
    fun updateDraft(newQuery: String) {
        _inputState.value = _inputState.value.copy(query = newQuery)
        requestSuggestions(newQuery)
        requestLinkDetection(newQuery)
    }

    private fun requestSuggestions(query: String) {
        suggestJob?.cancel()
        hitsJob?.cancel()
        val platform = _selectedPlatform.value
        val useKugou = platform == MusicPlatform.KUGOU && isKugouNativeActive()
        if (query.isBlank() || !suggestionEnabled || (!useKugou && NeteaseLink.containsRecognizableLink(query))) {
            _inputState.value = _inputState.value.copy(
                suggestions = emptyList(),
                suggestionQuery = "",
                songHits = emptyList(),
                songHitsQuery = "",
                platform = platform,
                suggestionArtistEntityIds = emptyMap(),
                suggestionAlbumEntityIds = emptyMap()
            )
            return
        }
        if (useKugou) {
            val router = platformSearchRouter!!
            suggestJob = viewModelScope.launch {
                delay(300)
                router.getSuggestions(MusicPlatform.KUGOU, query).firstOrNull()?.onSuccess { unified ->
                    val artistIds = mutableMapOf<Long, String>()
                    val albumIds = mutableMapOf<Long, String>()
                    val mapped = unified.map { item ->
                        when (item) {
                            is UnifiedSearchSuggestion.Keyword -> SearchSuggestion.Keyword(item.text)
                            is UnifiedSearchSuggestion.ArtistMatch -> {
                                val leg = item.id.toDeterministicLegacyId()
                                artistIds[leg] = item.id.canonical
                                SearchSuggestion.ArtistMatch(leg, item.text, item.avatarUrl)
                            }
                            is UnifiedSearchSuggestion.AlbumMatch -> {
                                val leg = item.id.toDeterministicLegacyId()
                                albumIds[leg] = item.id.canonical
                                SearchSuggestion.AlbumMatch(leg, item.text, item.artistName)
                            }
                        }
                    }
                    _inputState.value = _inputState.value.copy(
                        suggestions = mapped,
                        suggestionQuery = query,
                        platform = MusicPlatform.KUGOU,
                        suggestionArtistEntityIds = artistIds,
                        suggestionAlbumEntityIds = albumIds
                    )
                }
            }
            if (songHitsEnabled) {
                hitsJob = viewModelScope.launch {
                    delay(300)
                    router.search(MusicPlatform.KUGOU, query, UnifiedSearchType.SONG, offset = 0, limit = SONG_HIT_LIMIT)
                        .firstOrNull()
                        ?.onSuccess { page ->
                            val additions = LinkedHashMap<Long, UnifiedTrack>()
                            val tracks = page.items.filterIsInstance<UnifiedSearchResultItem.SongItem>().map { item ->
                                val d = item.track.toDisplayTrack()
                                additions[d.id] = item.track
                                d
                            }
                            if (additions.isNotEmpty()) _unifiedTracks.update { it + additions }
                            _inputState.value = _inputState.value.copy(
                                songHits = tracks,
                                songHitsQuery = query,
                                platform = MusicPlatform.KUGOU
                            )
                        }
                }
            }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(300)
            repository.getSuggestions(query).firstOrNull()?.onSuccess { suggestions ->
                _inputState.value = _inputState.value.copy(
                    suggestions = suggestions,
                    suggestionQuery = query,
                    platform = MusicPlatform.NETEASE,
                    suggestionArtistEntityIds = emptyMap(),
                    suggestionAlbumEntityIds = emptyMap()
                )
            }
        }
        if (songHitsEnabled) {
            hitsJob = viewModelScope.launch {
                delay(300)
                repository.search(query, SearchType.SONG, offset = 0, limit = SONG_HIT_LIMIT).firstOrNull()?.onSuccess { page ->
                    val tracks = page.items.filterIsInstance<SearchResultItem.SongItem>().map { it.track }
                    songQualityCache?.prefetch(tracks)
                    _inputState.value = _inputState.value.copy(
                        songHits = tracks,
                        songHitsQuery = query,
                        platform = MusicPlatform.NETEASE
                    )
                }
            }
        }
    }

    private fun requestLinkDetection(query: String) {
        linkResolveJob?.cancel()
        if (query.isBlank()) {
            _inputState.value = _inputState.value.copy(linkHit = null)
            return
        }
        val direct = NeteaseLink.target(query)
        val shortLink = if (direct == null) NeteaseLink.shortLink(query) else null
        if (direct == null && shortLink == null) {
            _inputState.value = _inputState.value.copy(linkHit = null)
            return
        }
        if (direct != null) {
            _inputState.value = _inputState.value.copy(
                linkHit = SearchLinkHit(query, direct, LinkPreviewState.Loading)
            )
        } else {
            _inputState.value = _inputState.value.copy(linkHit = null)
        }
        linkResolveJob = viewModelScope.launch {
            val target = direct ?: run {
                delay(150)
                NeteaseLink.resolve(shortLink!!) ?: return@launch
            }
            if (_inputState.value.query != query) return@launch
            if (direct == null) {
                _inputState.value = _inputState.value.copy(
                    linkHit = SearchLinkHit(query, target, LinkPreviewState.Loading)
                )
            }
            val preview = linkPreviewResolver?.resolve(target)
            if (_inputState.value.query != query) return@launch
            _inputState.value = _inputState.value.copy(
                linkHit = SearchLinkHit(
                    query = query,
                    target = target,
                    previewState = if (preview != null) LinkPreviewState.Ready(preview) else LinkPreviewState.Failed
                )
            )
        }
    }

    fun playLinkSong(songId: Long, cachedTrack: Track? = null, onOpened: (() -> Unit)? = null) {
        viewModelScope.launch {
            val track = cachedTrack
                ?: (linkPreviewResolver?.resolve(SongLinkTarget.Song(songId))?.track)
            if (track == null) {
                _toastEvent.emit("这首歌打不开，可能已下架")
                return@launch
            }
            playerManager.playLinkTrack(track, "链接")
            onOpened?.invoke()
        }
    }

    fun enqueueLinkSongNext(songId: Long, cachedTrack: Track? = null) {
        viewModelScope.launch {
            val track = cachedTrack
                ?: (linkPreviewResolver?.resolve(SongLinkTarget.Song(songId))?.track)
            if (track == null) {
                _toastEvent.emit("这首歌打不开，可能已下架")
                return@launch
            }
            playerManager.enqueueLinkTrackNext(track)
            _toastEvent.emit("已添加至下一首播放")
        }
    }

    fun playSongHit(track: Track) {
        val keyword = _inputState.value.query
        if (keyword.isNotBlank()) viewModelScope.launch { historyPreferences.addKeyword(keyword) }
        val item = _unifiedTracks.value[track.id]?.let(QueueItem::fromUnified)
            ?: QueueItem(track.id, track.name, track.ar.joinToString { it.name }, track.al.picUrl)
        playerManager.playQueue(listOf(item), 0, "搜索")
    }

    // 提交搜索：写入历史并立即执行
    fun searchWithKeyword(keyword: String) {
        if (keyword.isBlank()) return
        searchJob?.cancel()
        kugouSearchJob?.cancel()
        suggestJob?.cancel()
        hitsJob?.cancel()
        linkResolveJob?.cancel()
        cancelExternalJobs()
        _mode.value = SearchMode.Results
        _committedQuery.value = keyword
        _inputState.value = SearchInputState(query = keyword, platform = _selectedPlatform.value)
        viewModelScope.launch { historyPreferences.addKeyword(keyword) }

        // 关键词已更换，其余 Tab 缓存的旧结果失效，切回时会重新拉取
        _resultsByType.forEach { (type, state) ->
            if (type != _selectedType.value) state.value = SearchResultsUiState.Idle
        }
        _kugouResultsByType.forEach { (type, state) ->
            if (type != _kugouSelectedType.value) state.value = SearchResultsUiState.Idle
        }
        _externalResults.forEach { (p, state) ->
            if (p != _selectedPlatform.value) state.value = ExternalSearchUiState.Idle
        }
        offsetByType.clear()
        kugouOffsetByType.clear()
        externalOffset.clear()

        when {
            _selectedPlatform.value == MusicPlatform.NETEASE -> {
                searchJob = viewModelScope.launch {
                    runSearch(keyword, _selectedType.value, isLoadMore = false)
                }
            }
            _selectedPlatform.value == MusicPlatform.KUGOU && isKugouNativeActive() -> {
                kugouSearchJob = viewModelScope.launch {
                    runKugouSearch(keyword, _kugouSelectedType.value, isLoadMore = false)
                }
            }
            else -> {
                searchExternal(keyword, _selectedPlatform.value, isLoadMore = false)
            }
        }
    }

    fun selectPlatform(platform: MusicPlatform) {
        if (_selectedPlatform.value == platform) return
        if (platform == MusicPlatform.KUGOU && !isKugouEnabled.value) return
        followCurrentPlatform = false
        _selectedPlatform.value = platform
        if (platform == MusicPlatform.KUGOU && isKugouNativeActive()) {
            loadKugouDiscoveryData()
        }
        if (_mode.value == SearchMode.Typing && _inputState.value.query.isNotBlank()) {
            requestSuggestions(_inputState.value.query)
        }
        val query = _committedQuery.value
        if (query.isNotBlank()) {
            when {
                platform == MusicPlatform.NETEASE -> {
                    if (_resultsByType.getValue(_selectedType.value).value is SearchResultsUiState.Idle) {
                        searchJob?.cancel()
                        searchJob = viewModelScope.launch { runSearch(query, _selectedType.value, isLoadMore = false) }
                    }
                }
                platform == MusicPlatform.KUGOU && isKugouNativeActive() -> {
                    if (_kugouResultsByType.getValue(_kugouSelectedType.value).value is SearchResultsUiState.Idle) {
                        kugouSearchJob?.cancel()
                        kugouSearchJob = viewModelScope.launch { runKugouSearch(query, _kugouSelectedType.value, isLoadMore = false) }
                    }
                }
                else -> {
                    val currentState = _externalResults[platform]?.value
                    if (currentState is ExternalSearchUiState.Idle) {
                        searchExternal(query, platform, isLoadMore = false)
                    }
                }
            }
        }
    }

    fun searchExternal(keyword: String, platform: MusicPlatform, isLoadMore: Boolean) {
        if (platform == MusicPlatform.KUGOU && isKugouNativeActive()) {
            kugouSearchJob?.cancel()
            kugouSearchJob = viewModelScope.launch { runKugouSearch(keyword, _kugouSelectedType.value, isLoadMore = isLoadMore) }
            return
        }
        val stateFlow = _externalResults[platform] ?: return
        val provider = searchProviders.find { it.platform == platform }
        if (provider == null) {
            stateFlow.value = ExternalSearchUiState.Error("未找到该平台搜索服务")
            return
        }

        if (isLoadMore) {
            val curr = stateFlow.value
            if (curr !is ExternalSearchUiState.Success || curr.isLoadingMore || !curr.hasMore) return
            stateFlow.value = curr.copy(isLoadingMore = true)
        } else {
            stateFlow.value = ExternalSearchUiState.Loading
            externalOffset[platform] = 0
        }

        externalSearchJobs[platform]?.cancel()
        externalSearchJobs[platform] = viewModelScope.launch {
            try {
                val offset = externalOffset[platform] ?: 0
                val limit = 30
                val tracks = provider.search(keyword, offset, limit)
                val currentList = if (isLoadMore) {
                    val existing = (stateFlow.value as? ExternalSearchUiState.Success)?.tracks.orEmpty()
                    val existingIds = existing.mapTo(hashSetOf()) { it.id }
                    existing + tracks.filter { it.id !in existingIds }
                } else {
                    tracks.distinctBy { it.id }
                }
                externalOffset[platform] = offset + tracks.size
                if (currentList.isEmpty()) {
                    stateFlow.value = ExternalSearchUiState.Empty
                } else {
                    stateFlow.value = ExternalSearchUiState.Success(
                        tracks = currentList,
                        hasMore = tracks.isNotEmpty() && tracks.size >= limit,
                        isLoadingMore = false
                    )
                }
            } catch (e: Exception) {
                stateFlow.value = ExternalSearchUiState.Error(e.message ?: "搜索失败")
            }
        }
    }

    private fun cancelExternalJobs() {
        externalSearchJobs.values.forEach { it.cancel() }
        externalSearchJobs.clear()
    }

    fun loadMoreExternal(platform: MusicPlatform) {
        val query = _committedQuery.value
        if (query.isNotBlank()) {
            searchExternal(query, platform, isLoadMore = true)
        }
    }

    // 点击后该行进入解析状态；解析期间重复点同一首忽略，点别的歌则取消上一首
    fun playExternalTrack(track: ExternalTrack) {
        val key = track.resolveKey
        if (_resolvingTrackKey.value == key) return
        externalPlayJob?.cancel()
        externalPlayJob = viewModelScope.launch {
            _resolvingTrackKey.value = key
            try {
                val playUrl = externalResolver?.resolve(track)
                if (playUrl == null) {
                    _toastEvent.emit(externalResolver?.explainFailure(track) ?: "当前平台暂无可用换源插件")
                    return@launch
                }
                playerManager.playQueue(listOf(track.toQueueItem(playUrl)), 0, "搜索")
            } finally {
                if (_resolvingTrackKey.value == key) _resolvingTrackKey.value = null
            }
        }
    }

    // 不预先解析，轮到时由播放器解析，避免直链在排队期间过期
    fun addExternalToPlayNext(track: ExternalTrack) {
        playerManager.addToPlayNext(listOf(track.toQueueItem(null)))
        viewModelScope.launch { _toastEvent.emit("已添加至下一首播放") }
    }

    // 外部平台搜索结果不在队列与歌单里，收藏时按展示 id 从当前结果中找回
    private fun searchedExternal(songId: Long): ExternalTrack? {
        if (songId >= 0L) return null
        return _externalResults.values.asSequence()
            .mapNotNull { (it.value as? ExternalSearchUiState.Success)?.tracks }
            .flatten()
            .firstOrNull { it.queueSongId == songId }
    }

    private fun ExternalTrack.toQueueItem(playUrl: String?) = QueueItem(
        songId = queueSongId,
        title = name,
        artist = artists,
        coverUrl = coverUrl,
        localUri = playUrl,
        external = this
    )

    fun quickLikeExternal(track: ExternalTrack) {
        viewModelScope.launch {
            songCollectDelegate.quickLikeExternal(track) { _toastEvent.emit(it) }
        }
    }

    fun prepareExternalCollectDialog(track: ExternalTrack) {
        viewModelScope.launch {
            songCollectDelegate.prepareExternal(track) { _toastEvent.emit(it) }
        }
    }

    fun saveExternalCollection(track: ExternalTrack, items: List<PlaylistCollectItem>, silent: Boolean = false) {
        viewModelScope.launch {
            songCollectDelegate.saveExternal(track, items, silent) { _toastEvent.emit(it) }
        }
    }

    fun createPlaylistAndAddExternal(name: String, track: ExternalTrack) {
        viewModelScope.launch {
            songCollectDelegate.createAndAddExternal(name, track) { _toastEvent.emit(it) }
        }
    }

    // 外部歌曲没有网易云 id，只能按歌名歌手回到网易云重新搜索
    fun searchOnNetease(track: ExternalTrack) {
        selectPlatform(MusicPlatform.NETEASE)
        searchWithKeyword("${track.name} ${track.artists}".trim())
    }

    fun playCollection(id: Long, isAlbum: Boolean) {
        val actions = collectionActions ?: return
        viewModelScope.launch { actions.play(id, isAlbum) { _toastEvent.emit(it) } }
    }

    fun playCollectionEntity(entityId: String, isAlbum: Boolean) {
        val actions = collectionActions ?: return
        viewModelScope.launch { actions.playEntity(entityId, isAlbum) { _toastEvent.emit(it) } }
    }

    fun addCollectionToPlayNext(id: Long, isAlbum: Boolean) {
        val actions = collectionActions ?: return
        viewModelScope.launch { actions.addToPlayNext(id, isAlbum) { _toastEvent.emit(it) } }
    }

    fun addCollectionEntityToPlayNext(entityId: String, isAlbum: Boolean) {
        val actions = collectionActions ?: return
        viewModelScope.launch { actions.addEntityToPlayNext(entityId, isAlbum) { _toastEvent.emit(it) } }
    }

    fun downloadCollection(id: Long, isAlbum: Boolean, level: String) {
        val actions = collectionActions ?: return
        viewModelScope.launch {
            actions.download(id, isAlbum, level, "search_collection_$id") { _toastEvent.emit(it) }
        }
    }

    fun subscribePlaylist(playlistId: Long) {
        val actions = collectionActions ?: return
        viewModelScope.launch { actions.subscribePlaylist(playlistId) { _toastEvent.emit(it) } }
    }

    fun subscribePlaylistEntity(entityId: String) {
        val actions = collectionActions ?: return
        viewModelScope.launch { actions.subscribeEntity(entityId) { _toastEvent.emit(it) } }
    }

    // 菜单展开时查询关注状态，查询失败返回 null，菜单按未关注处理
    suspend fun isArtistFollowed(artistId: Long, entityId: String? = null): Boolean? {
        val parsed = entityId?.let(ArtistId::parseOrNull)
        if (parsed != null && parsed.platform == MusicPlatform.KUGOU) {
            return platformArtistRouter?.checkArtistFollowed(parsed)?.firstOrNull()?.getOrNull()
        }
        return artistRepository?.checkArtistFollowed(artistId)?.firstOrNull()?.getOrNull()
    }

    fun setArtistFollowed(artistId: Long, follow: Boolean, entityId: String? = null) {
        val parsed = entityId?.let(ArtistId::parseOrNull)
        if (parsed != null && parsed.platform == MusicPlatform.KUGOU) {
            val router = platformArtistRouter ?: return
            viewModelScope.launch {
                router.subscribeArtist(parsed, follow).firstOrNull()?.fold(
                    onSuccess = { _toastEvent.emit(if (follow) "已关注" else "已取消关注") },
                    onFailure = { _toastEvent.emit(it.toUserMessage(resourceProvider)) }
                )
            }
            return
        }
        val repo = artistRepository ?: return
        viewModelScope.launch {
            repo.subscribeArtist(artistId, follow).firstOrNull()?.fold(
                onSuccess = { _toastEvent.emit(if (follow) "已关注" else "已取消关注") },
                onFailure = { _toastEvent.emit(it.toUserMessage(resourceProvider)) }
            )
        }
    }

    suspend fun isRadioSubscribed(radioId: Long): Boolean? =
        podcastRepository?.getRadioDetail(radioId)?.firstOrNull()?.getOrNull()?.subscribed

    fun setRadioSubscribed(radioId: Long, subscribe: Boolean) {
        val repo = podcastRepository ?: return
        viewModelScope.launch {
            repo.setRadioSubscribed(radioId, subscribe).firstOrNull()?.fold(
                onSuccess = { _toastEvent.emit(if (subscribe) "已订阅" else "已取消订阅") },
                onFailure = { _toastEvent.emit(it.toUserMessage(resourceProvider)) }
            )
        }
    }

    fun addProgramToPlayNext(program: PodcastProgram) {
        playerManager.addToPlayNext(listOf(program.toQueueItem()))
        viewModelScope.launch { _toastEvent.emit("已添加至下一首播放") }
    }

    fun selectType(type: SearchType) {
        if (_selectedType.value == type) return
        _selectedType.value = type
        val query = _committedQuery.value
        if (query.isNotBlank() && _resultsByType.getValue(type).value is SearchResultsUiState.Idle) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { runSearch(query, type, isLoadMore = false) }
        }
    }

    fun selectKugouType(type: KugouSearchType) {
        if (_kugouSelectedType.value == type) return
        _kugouSelectedType.value = type
        val query = _committedQuery.value
        if (query.isNotBlank() && isKugouNativeActive() &&
            _kugouResultsByType.getValue(type).value is SearchResultsUiState.Idle
        ) {
            kugouSearchJob?.cancel()
            kugouSearchJob = viewModelScope.launch { runKugouSearch(query, type, isLoadMore = false) }
        }
    }

    fun loadMore() {
        val keyword = _committedQuery.value
        if (keyword.isBlank()) return
        if (_selectedPlatform.value == MusicPlatform.KUGOU && isKugouNativeActive()) {
            loadMoreKugou()
            return
        }
        val type = _selectedType.value
        val current = _resultsByType.getValue(type).value
        if (current !is SearchResultsUiState.Success || current.isLoadingMore || !current.hasMore) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(keyword, type, isLoadMore = true) }
    }

    fun loadMoreKugou() {
        val keyword = _committedQuery.value
        if (keyword.isBlank() || !isKugouNativeActive()) return
        val type = _kugouSelectedType.value
        val current = _kugouResultsByType.getValue(type).value
        if (current !is SearchResultsUiState.Success || current.isLoadingMore || !current.hasMore) return

        kugouSearchJob?.cancel()
        kugouSearchJob = viewModelScope.launch { runKugouSearch(keyword, type, isLoadMore = true) }
    }

    private suspend fun runSearch(keyword: String, type: SearchType, isLoadMore: Boolean) {
        val stateFlow = _resultsByType.getValue(type)
        if (isLoadMore) {
            val current = stateFlow.value
            if (current !is SearchResultsUiState.Success || current.isLoadingMore || !current.hasMore) return
            stateFlow.value = current.copy(isLoadingMore = true)
        } else {
            offsetByType[type] = 0
            stateFlow.value = SearchResultsUiState.Loading
        }

        val offset = if (isLoadMore) offsetByType[type] ?: 0 else 0

        repository.search(keyword, type, offset = offset).firstOrNull()?.let { result ->
            result.onSuccess { page ->
                offsetByType[type] = offset + page.rawFetchedCount
                val newTracks = page.items.filterIsInstance<SearchResultItem.SongItem>().map { it.track }
                if (newTracks.isNotEmpty()) songQualityCache?.prefetch(newTracks)
                val currentItems = if (isLoadMore) {
                    val existing = (stateFlow.value as? SearchResultsUiState.Success)?.items.orEmpty()
                    val existingKeys = existing.mapTo(hashSetOf()) { it.stableKey }
                    existing + page.items.filter { it.stableKey !in existingKeys }
                } else {
                    page.items.distinctBy { it.stableKey }
                }

                stateFlow.value = if (currentItems.isEmpty()) {
                    SearchResultsUiState.Empty
                } else {
                    SearchResultsUiState.Success(
                        items = currentItems,
                        totalCount = page.totalCount,
                        hasMore = page.hasMore && page.items.isNotEmpty(),
                        isLoadingMore = false
                    )
                }
            }.onFailure { error ->
                val current = stateFlow.value
                if (isLoadMore && current is SearchResultsUiState.Success) {
                    stateFlow.value = current.copy(isLoadingMore = false)
                    _toastEvent.emit(error.toUserMessage(resourceProvider))
                } else {
                    stateFlow.value = SearchResultsUiState.Error(error.toUserMessage(resourceProvider))
                }
            }
        }
    }

    private suspend fun runKugouSearch(keyword: String, type: KugouSearchType, isLoadMore: Boolean) {
        val router = platformSearchRouter ?: return
        val stateFlow = _kugouResultsByType.getValue(type)
        if (!isKugouEnabled.value) {
            stateFlow.value = SearchResultsUiState.Error("酷狗音乐平台已关闭")
            return
        }
        if (isLoadMore) {
            val current = stateFlow.value
            if (current !is SearchResultsUiState.Success || current.isLoadingMore || !current.hasMore) return
            stateFlow.value = current.copy(isLoadingMore = true)
        } else {
            kugouOffsetByType[type] = 0
            stateFlow.value = SearchResultsUiState.Loading
        }

        val offset = if (isLoadMore) kugouOffsetByType[type] ?: 0 else 0
        router.search(MusicPlatform.KUGOU, keyword, type.unifiedType, offset = offset, limit = 30)
            .firstOrNull()
            ?.let { result ->
                result.onSuccess { page ->
                    val fetchedCount = page.rawFetchedCount.coerceAtLeast(page.items.size)
                    kugouOffsetByType[type] = offset + fetchedCount
                    val (mappedItems, newEntityIds) = mapUnifiedSearchItems(page.items)
                    val previous = if (isLoadMore) stateFlow.value as? SearchResultsUiState.Success else null
                    val currentItems = if (previous != null) {
                        val existingKeys = previous.items.mapTo(hashSetOf()) { it.stableKey }
                        previous.items + mappedItems.filter { it.stableKey !in existingKeys }
                    } else {
                        mappedItems.distinctBy { it.stableKey }
                    }
                    val mergedEntityIds = if (previous != null) previous.entityIds + newEntityIds else newEntityIds

                    stateFlow.value = if (currentItems.isEmpty()) {
                        SearchResultsUiState.Empty
                    } else {
                        SearchResultsUiState.Success(
                            items = currentItems,
                            totalCount = page.totalCount,
                            hasMore = page.hasMore && mappedItems.isNotEmpty(),
                            isLoadingMore = false,
                            entityIds = mergedEntityIds
                        )
                    }
                }.onFailure { error ->
                    val current = stateFlow.value
                    if (isLoadMore && current is SearchResultsUiState.Success) {
                        stateFlow.value = current.copy(isLoadingMore = false)
                        _toastEvent.emit(error.toUserMessage(resourceProvider))
                    } else {
                        stateFlow.value = SearchResultsUiState.Error(error.toUserMessage(resourceProvider))
                    }
                }
            }
    }

    private fun mapUnifiedSearchItems(
        items: List<UnifiedSearchResultItem>
    ): Pair<List<SearchResultItem>, Map<String, String>> {
        val mapped = ArrayList<SearchResultItem>(items.size)
        val entityIds = LinkedHashMap<String, String>(items.size)
        val unifiedAdditions = LinkedHashMap<Long, UnifiedTrack>()
        for (item in items) {
            when (item) {
                is UnifiedSearchResultItem.SongItem -> {
                    val track = item.track.toDisplayTrack()
                    unifiedAdditions[track.id] = item.track
                    val res = SearchResultItem.SongItem(track)
                    mapped.add(res)
                    entityIds[res.stableKey] = item.track.id.canonical
                }
                is UnifiedSearchResultItem.AlbumItem -> {
                    val album = Album(
                        id = item.album.id?.toDeterministicLegacyId() ?: 0L,
                        name = item.album.name,
                        picUrl = item.album.coverUrl,
                        artists = item.album.artists.map { a ->
                            Artist(
                                id = a.id?.toDeterministicLegacyId() ?: 0L,
                                name = a.name,
                                picUrl = a.picUrl,
                                img1v1Url = a.avatarUrl
                            )
                        },
                        publishTime = item.album.publishTimeMs
                    )
                    val res = SearchResultItem.AlbumItem(album)
                    mapped.add(res)
                    item.album.id?.let { entityIds[res.stableKey] = it.canonical }
                    item.album.artists.forEach { a ->
                        a.id?.let { aid -> entityIds["artist_${aid.toDeterministicLegacyId()}"] = aid.canonical }
                    }
                }
                is UnifiedSearchResultItem.ArtistItem -> {
                    val artist = Artist(
                        id = item.artist.id?.toDeterministicLegacyId() ?: 0L,
                        name = item.artist.name,
                        picUrl = item.artist.picUrl.ifBlank { item.artist.avatarUrl },
                        img1v1Url = item.artist.avatarUrl.ifBlank { item.artist.picUrl }
                    )
                    val res = SearchResultItem.ArtistItem(artist)
                    mapped.add(res)
                    item.artist.id?.let { entityIds[res.stableKey] = it.canonical }
                }
                is UnifiedSearchResultItem.PlaylistItem -> {
                    val playlist = item.playlist.toDisplayPlaylistDetail()
                    val res = SearchResultItem.PlaylistItem(playlist)
                    mapped.add(res)
                    entityIds[res.stableKey] = item.playlist.id.canonical
                }
                is UnifiedSearchResultItem.RadioItem -> mapped.add(SearchResultItem.RadioItem(item.radio))
                is UnifiedSearchResultItem.ProgramItem -> mapped.add(SearchResultItem.ProgramItem(item.program))
            }
        }
        if (unifiedAdditions.isNotEmpty()) {
            _unifiedTracks.update { it + unifiedAdditions }
        }
        return mapped to entityIds
    }

    fun clearHistory() {
        viewModelScope.launch { historyPreferences.clear() }
    }

    fun removeHistory(keyword: String) {
        viewModelScope.launch { historyPreferences.removeKeyword(keyword) }
    }

    fun playFeaturedTrack() {
        val track = _featuredTrack.value ?: return
        val item = QueueItem(track.id, track.name, track.ar.joinToString { it.name }, track.al.picUrl)
        playerManager.playQueue(listOf(item), 0, "搜索")
    }

    fun playSong(track: Track) {
        val state = if (_selectedPlatform.value == MusicPlatform.KUGOU && isKugouNativeActive()) {
            _kugouResultsByType.getValue(_kugouSelectedType.value).value
        } else {
            _resultsByType.getValue(SearchType.SONG).value
        }
        if (state !is SearchResultsUiState.Success) {
            playerManager.playQueue(listOf(track.toQueueItem()), 0, "搜索")
            return
        }
        val tracks = state.items.filterIsInstance<SearchResultItem.SongItem>().map { it.track }
        val queueItems = tracks.map { t ->
            _unifiedTracks.value[t.id]?.let(QueueItem::fromUnified)
                ?: QueueItem(t.id, t.name, t.ar.joinToString { it.name }, t.al.picUrl)
        }
        val startIndex = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerManager.playQueue(queueItems, startIndex, "搜索")
    }

    // 从点击的节目起播，当前节目搜索结果作为队列。搜索结果不带收听进度，从头播放
    fun playProgram(program: PodcastProgram) {
        val state = _resultsByType.getValue(SearchType.PROGRAM).value
        if (state !is SearchResultsUiState.Success) return
        val programs = state.items.filterIsInstance<SearchResultItem.ProgramItem>().map { it.program }
        val startIndex = programs.indexOfFirst { it.id == program.id }.coerceAtLeast(0)
        playerManager.playPodcastPrograms(programs, startIndex, emptyMap())
    }
}
