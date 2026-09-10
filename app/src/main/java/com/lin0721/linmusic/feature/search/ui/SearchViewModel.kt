package com.lin0721.linmusic.feature.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.songlike.LoadLikedSongIdsUseCase
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.search.data.SearchHistoryPreferences
import com.lin0721.linmusic.feature.search.data.SearchRepository
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: SearchRepository,
    private val historyPreferences: SearchHistoryPreferences,
    val playerManager: PlayerManager,
    userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider,
    private val songCollectDelegate: SongCollectDelegate,
    private val loadLikedSongIdsUseCase: LoadLikedSongIdsUseCase,
    private val songLikeRepository: SongLikeRepository,
    private val syncProfileAfterLoginUseCase: SyncProfileAfterLoginUseCase
) : ViewModel() {

    val userProfile: StateFlow<UserProfile?> = userPreferences.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 搜索结果里的歌曲行同样需要红心外显与收藏弹层，跟 Artist/Playlist 共用同一套委托
    private val _likedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val likedSongIds: StateFlow<Set<Long>> = _likedSongIds.asStateFlow()

    val collectState: StateFlow<PlaylistCollectState> = songCollectDelegate.state

    val history: StateFlow<List<String>> = historyPreferences.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _discoveryState = MutableStateFlow<DiscoveryUiState>(DiscoveryUiState.Loading)
    val discoveryState: StateFlow<DiscoveryUiState> = _discoveryState.asStateFlow()

    private val _inputState = MutableStateFlow(SearchInputState())
    val inputState: StateFlow<SearchInputState> = _inputState.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

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

    init {
        loadDiscoveryData()
        loadLikedSongIds()
    }

    fun loadLikedSongIds() {
        viewModelScope.launch {
            loadLikedSongIdsUseCase()?.let { _likedSongIds.value = it }
        }
    }

    // 加入下一首播放
    fun addTrackToPlayNext(track: Track) {
        val queueItem = QueueItem(track.id, track.name, track.ar.joinToString("/") { it.name }, track.al.picUrl)
        playerManager.addToPlayNext(listOf(queueItem))
        viewModelScope.launch { _toastEvent.emit("已添加至下一首播放") }
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
        }
    }

    fun createPlaylistAndAddSong(name: String, songId: Long) {
        viewModelScope.launch {
            songCollectDelegate.createAndAdd(name, songId, _likedSongIds.value) { _toastEvent.emit(it) }
        }
    }

    // 歌曲"喜欢"开关，供「更多操作」菜单调用（与红心图标的收藏弹层入口独立）
    fun toggleLikeSong(songId: Long, like: Boolean) {
        viewModelScope.launch {
            songLikeRepository.likeSong(songId, like).collect { result ->
                result.onSuccess {
                    _toastEvent.emit(if (like) "已添加到我喜欢的音乐" else "已从我喜欢的音乐中移除")
                    val currentLiked = _likedSongIds.value.toMutableSet()
                    if (like) currentLiked.add(songId) else currentLiked.remove(songId)
                    _likedSongIds.value = currentLiked
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

            _discoveryState.value = DiscoveryUiState.Success(
                defaultKeyword = keywordResult?.getOrNull() ?: "搜索你想听的",
                hotSearches = hotSearchResult?.getOrNull() ?: emptyList(),
                playlistTags = tagsResult?.getOrNull() ?: emptyList()
            )
            failures.firstOrNull()?.let { _toastEvent.emit(it.toUserMessage(resourceProvider)) }
        }
    }

    // 发现页加载失败时的重试入口，UI 层错误态按钮调用
    fun retryDiscovery() {
        loadDiscoveryData()
    }

    // 搜索结果加载失败时的重试入口：重跑当前 Tab 当前关键词，不重复写历史
    fun retrySearch() {
        val type = _selectedType.value
        val keyword = _inputState.value.query
        if (keyword.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(keyword, type, isLoadMore = false) }
    }

    fun activateSearch() {
        _isSearchActive.value = true
    }

    fun deactivateSearch() {
        _isSearchActive.value = false
        resetSearchState()
    }

    private fun resetSearchState() {
        searchJob?.cancel()
        suggestJob?.cancel()
        _inputState.value = SearchInputState()
        offsetByType.clear()
        _resultsByType.values.forEach { it.value = SearchResultsUiState.Idle }
    }

    fun updateQuery(newQuery: String) {
        _inputState.value = _inputState.value.copy(query = newQuery)
        searchJob?.cancel()
        suggestJob?.cancel()

        if (newQuery.isBlank()) {
            _inputState.value = _inputState.value.copy(isSuggesting = false, suggestions = emptyList())
            offsetByType.clear()
            _resultsByType.values.forEach { it.value = SearchResultsUiState.Idle }
            return
        }

        _inputState.value = _inputState.value.copy(isSuggesting = true)
        suggestJob = viewModelScope.launch {
            delay(300)
            repository.getSuggestions(newQuery).firstOrNull()?.onSuccess { suggestions ->
                _inputState.value = _inputState.value.copy(suggestions = suggestions)
            }
        }

        searchJob = viewModelScope.launch {
            delay(400)
            offsetByType.clear()
            runSearch(newQuery, _selectedType.value, isLoadMore = false)
        }
    }

    // 明确提交搜索：选中联想词 / 历史词 / 热搜词，写入历史并立即执行（不走防抖）
    // 热搜/精品歌单等入口是在发现页（isSearchActive 尚为 false）触发的，必须一并置为激活态，
    // 否则 query 已经写入但 UI 判断展示结果区的条件不满足，页面停留在发现页看起来像没反应
    fun searchWithKeyword(keyword: String) {
        searchJob?.cancel()
        suggestJob?.cancel()
        _isSearchActive.value = true
        _inputState.value = _inputState.value.copy(query = keyword, isSuggesting = false, suggestions = emptyList())
        viewModelScope.launch { historyPreferences.addKeyword(keyword) }

        // 关键词已更换，其余 Tab 缓存的旧结果失效，切回时会重新拉取
        _resultsByType.forEach { (type, state) ->
            if (type != _selectedType.value) state.value = SearchResultsUiState.Idle
        }
        offsetByType.clear()

        searchJob = viewModelScope.launch {
            runSearch(keyword, _selectedType.value, isLoadMore = false)
        }
    }

    fun selectType(type: SearchType) {
        if (_selectedType.value == type) return
        _selectedType.value = type
        val query = _inputState.value.query
        if (query.isNotBlank() && _resultsByType.getValue(type).value is SearchResultsUiState.Idle) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { runSearch(query, type, isLoadMore = false) }
        }
    }

    fun loadMore() {
        val type = _selectedType.value
        val keyword = _inputState.value.query
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
                val mergedItems = if (isLoadMore) {
                    (stateFlow.value as? SearchResultsUiState.Success)?.items.orEmpty() + page.items
                } else {
                    page.items
                }
                stateFlow.value = if (mergedItems.isEmpty()) {
                    SearchResultsUiState.Empty
                } else {
                    SearchResultsUiState.Success(
                        items = mergedItems,
                        totalCount = page.totalCount,
                        hasMore = page.hasMore,
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
}
