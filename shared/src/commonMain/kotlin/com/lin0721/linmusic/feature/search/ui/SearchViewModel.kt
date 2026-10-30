package com.lin0721.linmusic.feature.search.ui

import kotlinx.coroutines.flow.combine
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.feature.artist.data.ArtistRepository
import com.lin0721.linmusic.feature.playlist.domain.CollectionActionsDelegate
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.podcast.data.PodcastRepository
import com.lin0721.linmusic.feature.podcast.domain.toQueueItem
import com.lin0721.linmusic.feature.search.data.SearchHistoryPreferences
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.playPodcastPrograms
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.ExternalTrackResolver
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.PlatformSearchProvider
import com.lin0721.linmusic.core.source.SourcePreferences
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val podcastRepository: PodcastRepository? = null
) : ViewModel() {

    val userProfile: StateFlow<UserProfile?> = userPreferences.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 搜索结果里的歌曲行同样需要红心外显与收藏弹层，跟 Artist/Playlist 共用同一套委托
    val likedSongIds: StateFlow<Set<Long>> = songLikeRepository.likedSongIds

    val collectState: StateFlow<PlaylistCollectState> = songCollectDelegate.state

    val history: StateFlow<List<String>> = historyPreferences.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _discoveryState = MutableStateFlow<DiscoveryUiState>(DiscoveryUiState.Loading)
    val discoveryState: StateFlow<DiscoveryUiState> = _discoveryState.asStateFlow()

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

    private val allExternalPlatforms = listOf(
        MusicPlatform.KUGOU,
        MusicPlatform.KUWO,
        MusicPlatform.QQ,
        MusicPlatform.LX
    )

    // 开关打开后才展示已注册搜索源的平台 Tab；插件平台由插件自己决定，不受开关影响
    val searchPlatforms: StateFlow<List<MusicPlatform>> = combine(
        sourcePreferences?.searchAggregationEnabled ?: flowOf(false),
        searchProviders.firstOrNull { it.platform == MusicPlatform.LX }?.available ?: flowOf(false)
    ) { enabled, pluginAvailable ->
        val platforms = if (enabled) {
            val available = searchProviders.mapTo(hashSetOf()) { it.platform }
            allExternalPlatforms.filter { it in available && it != MusicPlatform.LX }
        } else {
            emptyList()
        }
        listOf(MusicPlatform.NETEASE) + platforms + listOfNotNull(MusicPlatform.LX.takeIf { pluginAvailable })
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

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    // 各 Tab 独立维护的分页 offset，按接口原始返回条数推进（见 SearchPageResult.rawFetchedCount 注释）
    private val offsetByType = mutableMapOf<SearchType, Int>()
    private var searchJob: Job? = null
    private var suggestJob: Job? = null
    private var hitsJob: Job? = null

    init {
        loadDiscoveryData()
        loadLikedSongIds()
        viewModelScope.launch {
            searchPlatforms.collect { platforms ->
                if (_selectedPlatform.value !in platforms) {
                    _selectedPlatform.value = MusicPlatform.NETEASE
                }
            }
        }
    }

    fun loadLikedSongIds() {
        viewModelScope.launch {
            loadLikedSongIdsUseCase()
        }
    }

    // 加入下一首播放
    fun addTrackToPlayNext(track: Track) {
        val queueItem = QueueItem(track.id, track.name, track.ar.joinToString("/") { it.name }, track.al.picUrl)
        playerManager.addToPlayNext(listOf(queueItem))
        viewModelScope.launch { _toastEvent.emit("已添加至下一首播放") }
    }

    fun prepareCollectDialog(songId: Long) {
        searchedExternal(songId)?.let { return prepareExternalCollectDialog(it) }
        viewModelScope.launch {
            songCollectDelegate.prepare(songId, songLikeRepository.likedSongIds.value) { _toastEvent.emit(it) }
        }
    }

    fun savePlaylistCollection(songId: Long, items: List<PlaylistCollectItem>) {
        searchedExternal(songId)?.let { return saveExternalCollection(it, items) }
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
        searchedExternal(songId)?.let { return createPlaylistAndAddExternal(name, it) }
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
            loadLikedSongIds()
        }
    }

    private fun loadDiscoveryData() {
        viewModelScope.launch {
            _discoveryState.value = DiscoveryUiState.Loading

            val keywordDeferred = async { repository.getDefaultSearchKeyword().firstOrNull() }
            val hotSearchDeferred = async { repository.getHotSearches().firstOrNull() }
            val tagsDeferred = async { repository.getPlaylistTags().firstOrNull() }

            val keywordResult = keywordDeferred.await()
            val hotSearchResult = hotSearchDeferred.await()
            val tagsResult = tagsDeferred.await()

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
                defaultKeyword = keywordResult?.getOrNull() ?: "搜索你想听的",
                hotSearches = hotSearches,
                playlistTags = tagsResult?.getOrNull() ?: emptyList()
            )
            failures.firstOrNull()?.let { _toastEvent.emit(it.toUserMessage(resourceProvider)) }
            hotSearches.firstOrNull()?.let { loadFeaturedTrack(it.keyword) }
        }
    }

    private suspend fun loadFeaturedTrack(keyword: String) {
        _featuredTrack.value = null
        repository.search(keyword, SearchType.SONG, offset = 0, limit = 1).firstOrNull()
            ?.getOrNull()
            ?.items
            ?.firstNotNullOfOrNull { (it as? SearchResultItem.SongItem)?.track }
            ?.let { _featuredTrack.value = it }
    }

    // 发现页加载失败时的重试入口，UI 层错误态按钮调用
    fun retryDiscovery() {
        loadDiscoveryData()
    }

    // 搜索结果加载失败时的重试入口：重跑当前 Tab 当前关键词，不重复写历史
    fun retrySearch() {
        val type = _selectedType.value
        val keyword = _committedQuery.value
        if (keyword.isBlank()) return
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
        requestSuggestions(_inputState.value.query)
    }

    fun cancelSearch() {
        _mode.value = SearchMode.Discovery
        resetSearchState()
    }

    private fun resetSearchState() {
        searchJob?.cancel()
        suggestJob?.cancel()
        cancelExternalJobs()
        hitsJob?.cancel()
        _inputState.value = SearchInputState()
        _committedQuery.value = ""
        offsetByType.clear()
        externalOffset.clear()
        _resultsByType.values.forEach { it.value = SearchResultsUiState.Idle }
        _externalResults.values.forEach { it.value = ExternalSearchUiState.Idle }
    }

    // 输入只拉联想，提交才搜索
    fun updateQuery(newQuery: String) {
        _inputState.value = _inputState.value.copy(query = newQuery)
        if (_mode.value == SearchMode.Results) _mode.value = SearchMode.Typing
        requestSuggestions(newQuery)
    }

    // 只改输入框草稿词与联想，不动当前模式与已加载的结果；桌面端下拉浮层专用
    fun updateDraft(newQuery: String) {
        _inputState.value = _inputState.value.copy(query = newQuery)
        requestSuggestions(newQuery)
    }

    private fun requestSuggestions(query: String) {
        suggestJob?.cancel()
        hitsJob?.cancel()
        if (query.isBlank()) {
            _inputState.value = _inputState.value.copy(
                suggestions = emptyList(), suggestionQuery = "", songHits = emptyList(), songHitsQuery = ""
            )
            return
        }
        suggestJob = viewModelScope.launch {
            delay(300)
            repository.getSuggestions(query).firstOrNull()?.onSuccess { suggestions ->
                _inputState.value = _inputState.value.copy(suggestions = suggestions, suggestionQuery = query)
            }
        }
        if (songHitsEnabled) {
            hitsJob = viewModelScope.launch {
                delay(300)
                repository.search(query, SearchType.SONG, offset = 0, limit = SONG_HIT_LIMIT).firstOrNull()?.onSuccess { page ->
                    val tracks = page.items.filterIsInstance<SearchResultItem.SongItem>().map { it.track }
                    _inputState.value = _inputState.value.copy(songHits = tracks, songHitsQuery = query)
                }
            }
        }
    }

    fun playSongHit(track: Track) {
        val keyword = _inputState.value.query
        if (keyword.isNotBlank()) viewModelScope.launch { historyPreferences.addKeyword(keyword) }
        playerManager.playQueue(
            listOf(QueueItem(track.id, track.name, track.ar.joinToString { it.name }, track.al.picUrl)),
            0,
            "搜索"
        )
    }

    // 提交搜索：写入历史并立即执行
    fun searchWithKeyword(keyword: String) {
        if (keyword.isBlank()) return
        searchJob?.cancel()
        suggestJob?.cancel()
        hitsJob?.cancel()
        cancelExternalJobs()
        _mode.value = SearchMode.Results
        _committedQuery.value = keyword
        _inputState.value = SearchInputState(query = keyword)
        viewModelScope.launch { historyPreferences.addKeyword(keyword) }

        // 关键词已更换，其余 Tab 缓存的旧结果失效，切回时会重新拉取
        _resultsByType.forEach { (type, state) ->
            if (type != _selectedType.value) state.value = SearchResultsUiState.Idle
        }
        _externalResults.forEach { (p, state) ->
            if (p != _selectedPlatform.value) state.value = ExternalSearchUiState.Idle
        }
        offsetByType.clear()
        externalOffset.clear()

        if (_selectedPlatform.value == MusicPlatform.NETEASE) {
            searchJob = viewModelScope.launch {
                runSearch(keyword, _selectedType.value, isLoadMore = false)
            }
        } else {
            searchExternal(keyword, _selectedPlatform.value, isLoadMore = false)
        }
    }

    fun selectPlatform(platform: MusicPlatform) {
        if (_selectedPlatform.value == platform) return
        _selectedPlatform.value = platform
        val query = _committedQuery.value
        if (query.isNotBlank()) {
            if (platform == MusicPlatform.NETEASE) {
                if (_resultsByType.getValue(_selectedType.value).value is SearchResultsUiState.Idle) {
                    searchJob?.cancel()
                    searchJob = viewModelScope.launch { runSearch(query, _selectedType.value, isLoadMore = false) }
                }
            } else {
                val currentState = _externalResults[platform]?.value
                if (currentState is ExternalSearchUiState.Idle) {
                    searchExternal(query, platform, isLoadMore = false)
                }
            }
        }
    }

    fun searchExternal(keyword: String, platform: MusicPlatform, isLoadMore: Boolean) {
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

    fun prepareExternalCollectDialog(track: ExternalTrack) {
        viewModelScope.launch {
            songCollectDelegate.prepareExternal(track) { _toastEvent.emit(it) }
        }
    }

    fun saveExternalCollection(track: ExternalTrack, items: List<PlaylistCollectItem>) {
        viewModelScope.launch {
            songCollectDelegate.saveExternal(track, items) { _toastEvent.emit(it) }
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

    fun addCollectionToPlayNext(id: Long, isAlbum: Boolean) {
        val actions = collectionActions ?: return
        viewModelScope.launch { actions.addToPlayNext(id, isAlbum) { _toastEvent.emit(it) } }
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

    // 菜单展开时查询关注状态，查询失败返回 null，菜单按未关注处理
    suspend fun isArtistFollowed(artistId: Long): Boolean? =
        artistRepository?.checkArtistFollowed(artistId)?.firstOrNull()?.getOrNull()

    fun setArtistFollowed(artistId: Long, follow: Boolean) {
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

    fun loadMore() {
        val type = _selectedType.value
        val keyword = _committedQuery.value
        if (keyword.isBlank()) return
        val current = _resultsByType.getValue(type).value
        if (current !is SearchResultsUiState.Success || current.isLoadingMore || !current.hasMore) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(keyword, type, isLoadMore = true) }
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
        val state = _resultsByType.getValue(SearchType.SONG).value
        if (state !is SearchResultsUiState.Success) return
        val tracks = state.items.filterIsInstance<SearchResultItem.SongItem>().map { it.track }
        val queueItems = tracks.map { t ->
            QueueItem(t.id, t.name, t.ar.joinToString { it.name }, t.al.picUrl)
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
