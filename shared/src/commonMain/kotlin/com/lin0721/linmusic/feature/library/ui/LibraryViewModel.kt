package com.lin0721.linmusic.feature.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.playlistexternal.PlaylistExternalTrackStore
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.download.DownloadTrackInfo
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.download.toToastMessage
import com.lin0721.linmusic.feature.library.data.LibraryPreferences
import com.lin0721.linmusic.core.download.yearFromEpochMillis
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.userartist.UserArtistRepository
import com.lin0721.linmusic.feature.create.data.CreateRepository
import com.lin0721.linmusic.feature.create.data.playlistPrivacyOf
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import com.lin0721.linmusic.feature.library.data.LibraryRepository
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationEvent
import com.lin0721.linmusic.core.network.OnlineStateProvider
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.playlist.domain.UpdatePlaylistCoverUseCase
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import com.lin0721.linmusic.feature.recent.data.RecentRepository
import com.lin0721.linmusic.feature.artist.data.ArtistRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

private const val TAG = "LibraryViewModel"

// 中文排序用 Collator 而非裸字符串比较，Android 上 Locale.CHINA 的默认强度即按拼音排序
private val zhCollator: Collator = Collator.getInstance(Locale.CHINA)

enum class LibraryItemType {
    PLAYLIST, ARTIST, ALBUM
}

data class LibraryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val type: LibraryItemType,
    val isPinned: Boolean = false,
    val updateTime: Long = 0,
    val trackCount: Int = 0,
    val playCount: Long = 0,
    val isLikedSongs: Boolean = false,
    val isOwnedByMe: Boolean = false,
    val lastPlayTime: Long = 0
)

enum class LibraryFilter {
    PLAYLIST, ALBUM, ARTIST
}

// 歌单二级筛选：仅在 LibraryFilter.PLAYLIST 生效，区分自建歌单与收藏他人歌单
enum class LibraryPlaylistOwnerFilter {
    MINE, OTHERS
}

enum class LibrarySortOrder {
    RECENTLY_PLAYED, NAME, CUSTOM
}

class LibraryViewModel(
    private val syncProfileAfterLoginUseCase: SyncProfileAfterLoginUseCase,
    private val createRepository: CreateRepository,
    private val libraryRepository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val artistRepository: ArtistRepository,
    private val userPlaylistRepository: UserPlaylistRepository,
    private val userArtistRepository: UserArtistRepository,
    private val userPreferences: UserPreferences,
    val playerManager: PlaybackController,
    private val libraryPreferences: LibraryPreferences,
    private val resourceProvider: ResourceProvider,
    private val playlistMutationBus: PlaylistMutationBus,
    private val songDownloadManager: SongDownloader,
    private val recentRepository: RecentRepository,
    private val playbackRepository: PlaybackRepository,
    private val onlineState: OnlineStateProvider,
    private val updatePlaylistCoverUseCase: UpdatePlaylistCoverUseCase,
    private val externalStore: PlaylistExternalTrackStore
) : ViewModel() {

    private val _pinnedIds = MutableStateFlow<Set<String>>(getPinnedIdsFromPrefs())

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    val userProfile: StateFlow<UserProfile?> = userPreferences.userProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // null 表示未筛选（默认展示全部），仅在用户点击某个分类胶囊后才收窄为对应类型
    private val _selectedFilter = MutableStateFlow<LibraryFilter?>(null)
    val selectedFilter: StateFlow<LibraryFilter?> = _selectedFilter.asStateFlow()

    // 歌单二级筛选状态，切换/清除主筛选时一并重置
    private val _selectedPlaylistOwnerFilter = MutableStateFlow<LibraryPlaylistOwnerFilter?>(null)
    val selectedPlaylistOwnerFilter: StateFlow<LibraryPlaylistOwnerFilter?> = _selectedPlaylistOwnerFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(getSortOrderFromPrefs())
    val sortOrder: StateFlow<LibrarySortOrder> = _sortOrder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isGridView = MutableStateFlow(getGridViewFromPrefs())
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.userProfile.collect { profile ->
                if (profile != null) {
                    loadLibraryData(profile)
                } else {
                    _uiState.value = LibraryUiState.Loading
                }
            }
        }

        viewModelScope.launch {
            playlistMutationBus.events.collect { event ->
                when (event) {
                    is PlaylistMutationEvent.Deleted -> {
                        val current = _uiState.value
                        if (current is LibraryUiState.Success) {
                            val idStr = event.playlistId.toString()
                            val updatedAll = current.allItems.filterNot { it.id == idStr && it.type == LibraryItemType.PLAYLIST }
                            _uiState.value = current.copy(allItems = updatedAll)
                            applyFilterAndSort()
                        }
                        loadLibraryData()
                    }
                    is PlaylistMutationEvent.Renamed -> {
                        val current = _uiState.value
                        if (current is LibraryUiState.Success) {
                            val idStr = event.playlistId.toString()
                            val updatedAll = current.allItems.map { item ->
                                if (item.id == idStr && item.type == LibraryItemType.PLAYLIST) {
                                    item.copy(title = event.newName)
                                } else {
                                    item
                                }
                            }
                            _uiState.value = current.copy(allItems = updatedAll)
                            applyFilterAndSort()
                        }
                        loadLibraryData()
                    }
                    is PlaylistMutationEvent.DescriptionUpdated -> {
                        loadLibraryData()
                    }
                    is PlaylistMutationEvent.CoverUpdated -> {
                        loadLibraryData()
                    }
                    is PlaylistMutationEvent.Created -> {
                        loadLibraryData()
                    }
                    is PlaylistMutationEvent.Subscribed -> {
                        loadLibraryData()
                    }
                }
            }
        }

        // 离线转在线后重新拉取，替换离线时展示的缓存
        viewModelScope.launch {
            var wasOnline = onlineState.isOnline()
            onlineState.online.collect { online ->
                if (online && !wasOnline) loadLibraryData()
                wasOnline = online
            }
        }

        viewModelScope.launch {
            playbackRepository.playlistRecorded.collect {
                refreshRecentPlaylists()
            }
        }
    }

    private fun getPinnedIdsFromPrefs(): Set<String> = libraryPreferences.pinnedIds()

    private fun savePinnedIdsToPrefs(ids: Set<String>) {
        libraryPreferences.setPinnedIds(ids)
    }

    private fun getCustomPlaylistOrderFromPrefs(): List<String> = libraryPreferences.customPlaylistOrder()

    private fun saveCustomPlaylistOrderToPrefs(order: List<String>) {
        libraryPreferences.setCustomPlaylistOrder(order)
    }

    private fun getSortOrderFromPrefs(): LibrarySortOrder {
        val raw = libraryPreferences.sortOrderName()
        return raw?.let { runCatching { LibrarySortOrder.valueOf(it) }.getOrNull() } ?: LibrarySortOrder.CUSTOM
    }

    private fun saveSortOrderToPrefs(order: LibrarySortOrder) {
        libraryPreferences.setSortOrderName(order.name)
    }

    private fun getGridViewFromPrefs(): Boolean = libraryPreferences.isGridView()

    fun updateGridView(isGrid: Boolean) {
        _isGridView.value = isGrid
        libraryPreferences.setGridView(isGrid)
    }

    fun loadLibraryData(profileOverride: UserProfile? = null) {
        val profile = profileOverride ?: userProfile.value ?: return
        val isRefresh = _uiState.value is LibraryUiState.Success

        viewModelScope.launch {
            if (!isRefresh) {
                _uiState.value = LibraryUiState.Loading
            }

            try {
                // 1. 并行获取歌单
                val playlistsDeferred = async {
                    val result = userPlaylistRepository.getUserPlaylists(profile.uid).firstOrNull()
                    result?.exceptionOrNull()?.let { AppLogger.w(TAG, "获取歌单列表失败", it) }
                    result?.getOrNull() ?: emptyList()
                }

                // 2. 并行获取歌手
                val artistsDeferred = async {
                    val result = userArtistRepository.getFavoriteArtists().firstOrNull()
                    result?.exceptionOrNull()?.let { AppLogger.w(TAG, "获取收藏歌手失败", it) }
                    result
                }

                // 3. 并行获取专辑
                val albumsDeferred = async {
                    val result = libraryRepository.getCollectedAlbums().firstOrNull()
                    result?.exceptionOrNull()?.let { AppLogger.w(TAG, "获取收藏专辑失败", it) }
                    result?.getOrNull() ?: emptyList()
                }

                // 4. 并行获取用户收藏统计数
                val subcountDeferred = async {
                    val result = libraryRepository.getUserSubcount().firstOrNull()
                    result?.exceptionOrNull()?.let { AppLogger.w(TAG, "获取收藏统计数失败", it) }
                    result?.getOrNull()
                }

                // 5. 并行获取最近播放歌单
                val recentPlaylistsDeferred = async {
                    runCatching { recentRepository.getRecentPlaylists().first() }
                        .getOrDefault(Result.success(emptyList()))
                        .getOrDefault(emptyList())
                }

                val playlists = playlistsDeferred.await()
                if (playlists.isNotEmpty()) {
                    saveCustomPlaylistOrderToPrefs(playlists.map { it.id.toString() })
                    val cloudPinnedIds = playlists.filter { it.top }.map { it.id.toString() }.toSet()
                    val ownedPlaylistIds = playlists.filter { it.userId == profile.uid || it.creator?.userId == profile.uid }.map { it.id.toString() }.toSet()
                    val currentLocalPinned = getPinnedIdsFromPrefs()
                    val updatedPinned = (currentLocalPinned - ownedPlaylistIds) + cloudPinnedIds
                    _pinnedIds.value = updatedPinned
                    savePinnedIdsToPrefs(updatedPinned)
                }
                val artistsResult = artistsDeferred.await()
                val artists = artistsResult?.getOrNull() ?: emptyList()
                val artistsError = artistsResult?.exceptionOrNull()
                val albums = albumsDeferred.await()
                val subcount = subcountDeferred.await()
                val recentPlaylists = recentPlaylistsDeferred.await()
                val recentPlayTimes = recentPlaylists.associate { it.id.toString() to it.playTime }

                // 数据归一化 (Mapping)
                val mappedPlaylists = playlists.mapIndexed { index, playlist ->
                    val playlistIdStr = playlist.id.toString()
                    LibraryItem(
                        id = playlistIdStr,
                        title = playlist.name,
                        subtitle = "歌单 · ${playlist.creator?.nickname ?: ""}",
                        coverUrl = playlist.coverImgUrl,
                        type = LibraryItemType.PLAYLIST,
                        updateTime = playlist.updateTime,
                        trackCount = playlist.trackCount,
                        playCount = playlist.playCount,
                        isLikedSongs = index == 0 && playlist.creator?.userId == profile.uid,
                        isOwnedByMe = playlist.creator?.userId == profile.uid,
                        lastPlayTime = recentPlayTimes[playlistIdStr] ?: 0L
                    )
                }.toMutableList()

                val recordPlaylist = LibraryItem(
                    id = "-2",
                    title = "听歌排行的歌单",
                    subtitle = "歌单 · 听歌排行统计",
                    coverUrl = "",
                    type = LibraryItemType.PLAYLIST,
                    updateTime = 0L,
                    trackCount = 0,
                    playCount = 0,
                    isLikedSongs = false,
                    isOwnedByMe = true,
                    lastPlayTime = recentPlayTimes["-2"] ?: 0L
                )
                if (mappedPlaylists.isNotEmpty()) {
                    mappedPlaylists.add(1, recordPlaylist)
                } else {
                    mappedPlaylists.add(recordPlaylist)
                }

                val mappedArtists = artists.map { artist ->
                    LibraryItem(
                        id = artist.id.toString(),
                        title = artist.name,
                        subtitle = "歌手",
                        coverUrl = artist.avatarUrl,
                        type = LibraryItemType.ARTIST,
                        updateTime = 0
                    )
                }

                val mappedAlbums = albums.map { album ->
                    LibraryItem(
                        id = album.id.toString(),
                        title = album.name,
                        subtitle = "专辑 · ${album.artists.joinToString(" • ") { it.name }}",
                        coverUrl = album.picUrl,
                        type = LibraryItemType.ALBUM,
                        updateTime = album.subTime
                    )
                }

                // 歌手请求失败时提示用户；刷新场景下沿用上次已加载的歌手，避免列表被清空
                val displayArtists = if (artistsError != null && isRefresh) {
                    (_uiState.value as? LibraryUiState.Success)?.allItems
                        ?.filter { it.type == LibraryItemType.ARTIST } ?: mappedArtists
                } else {
                    mappedArtists
                }
                if (artistsError != null) {
                    _toastEvent.emit(artistsError.toUserMessage(resourceProvider))
                }

                val combinedItems = mappedPlaylists + displayArtists + mappedAlbums

                _uiState.update { state ->
                    LibraryUiState.Success(
                        allItems = combinedItems,
                        filteredItems = (state as? LibraryUiState.Success)?.filteredItems ?: emptyList(),
                        artistCount = subcount?.artistCount ?: displayArtists.size,
                        playlistCount = subcount?.playlistCount ?: playlists.size,
                        albumCount = albums.size
                    )
                }

                applyFilterAndSort()

            } catch (e: Exception) {
                AppLogger.e(TAG, "音乐库加载最终失败 isRefresh=$isRefresh", e)
                if (isRefresh) {
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                } else {
                    _uiState.value = LibraryUiState.Error(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    private fun applyFilterAndSort() {
        val state = _uiState.value as? LibraryUiState.Success ?: return
        val pinned = _pinnedIds.value
        val filter = _selectedFilter.value
        val ownerFilter = _selectedPlaylistOwnerFilter.value
        val sort = _sortOrder.value
        val query = _searchQuery.value

        var list = state.allItems.map { item ->
            item.copy(isPinned = pinned.contains(item.id))
        }

        if (query.isNotBlank()) {
            list = list.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.subtitle.contains(query, ignoreCase = true)
            }
        }

        list = when (filter) {
            null -> list
            LibraryFilter.PLAYLIST -> list.filter { it.type == LibraryItemType.PLAYLIST }
            LibraryFilter.ALBUM -> list.filter { it.type == LibraryItemType.ALBUM }
            LibraryFilter.ARTIST -> list.filter { it.type == LibraryItemType.ARTIST }
        }

        // 歌单二级筛选：我创建的 / 他人创建的（"我喜欢的音乐"与"听歌排行"视为我的）
        if (filter == LibraryFilter.PLAYLIST && ownerFilter != null) {
            list = list.filter { item ->
                val ownedByMe = item.isOwnedByMe || item.isLikedSongs || item.id == "-2"
                if (ownerFilter == LibraryPlaylistOwnerFilter.MINE) ownedByMe else !ownedByMe
            }
        }

        val pinnedItems = list.filter { it.isPinned }
        val unpinnedItems = list.filter { !it.isPinned }

        val customPlaylistOrder = getCustomPlaylistOrderFromPrefs()
        val customOrderMap = customPlaylistOrder.mapIndexed { index, id -> id to index }.toMap()

        val sortedUnpinned = when (sort) {
            LibrarySortOrder.RECENTLY_PLAYED -> {
                unpinnedItems.sortedWith(
                    compareByDescending<LibraryItem> { it.lastPlayTime }
                        .thenByDescending { it.updateTime }
                )
            }
            LibrarySortOrder.NAME -> {
                unpinnedItems.sortedWith(compareBy(zhCollator) { it.title })
            }
            LibrarySortOrder.CUSTOM -> {
                // 未进入过自定义顺序的条目（新歌单、首次纳入排序的特殊条目）排在最前面，而非末尾
                unpinnedItems.sortedWith(compareBy<LibraryItem> { item ->
                    if (item.type == LibraryItemType.PLAYLIST) {
                        customOrderMap[item.id] ?: -1
                    } else {
                        Int.MAX_VALUE
                    }
                }.thenByDescending { it.updateTime })
            }
        }

        val finalList = pinnedItems + sortedUnpinned

        _uiState.update { current ->
            if (current is LibraryUiState.Success) current.copy(filteredItems = finalList) else current
        }
    }

    // 监听歌单播放事件后刷新最近播放时间，并触发列表重排
    private suspend fun refreshRecentPlaylists() {
        val latestRecent = runCatching { recentRepository.getRecentPlaylists().first() }
            .getOrNull()?.getOrNull() ?: return
        val current = _uiState.value as? LibraryUiState.Success ?: return

        val recentPlayTimes = latestRecent.associate { it.id.toString() to it.playTime }
        val updatedAll = current.allItems.map { item ->
            val playTime = recentPlayTimes[item.id]
            if (playTime != null && playTime != item.lastPlayTime) {
                item.copy(lastPlayTime = playTime)
            } else {
                item
            }
        }
        _uiState.value = current.copy(allItems = updatedAll)
        applyFilterAndSort()
    }

    fun togglePin(itemId: String) {
        val currentPinned = _pinnedIds.value.toMutableSet()
        val willPin = !currentPinned.contains(itemId)
        if (willPin) {
            currentPinned.add(itemId)
        } else {
            currentPinned.remove(itemId)
        }
        _pinnedIds.value = currentPinned
        savePinnedIdsToPrefs(currentPinned)
        applyFilterAndSort()

        val item = (_uiState.value as? LibraryUiState.Success)?.allItems?.firstOrNull { it.id == itemId }
        val title = item?.title
        if (title != null) {
            viewModelScope.launch {
                _toastEvent.emit("已${if (willPin) "置顶" else "取消置顶"}: $title")
            }
        }

        val playlistId = itemId.toLongOrNull()
        if (playlistId != null && playlistId > 0 && item?.type == LibraryItemType.PLAYLIST) {
            if (item.isOwnedByMe) {
                viewModelScope.launch {
                    userPlaylistRepository.setPlaylistTop(playlistId, willPin).collect { result ->
                        result.onSuccess {
                            syncPlaylistOrderToCloud()
                        }.onFailure { e ->
                            AppLogger.w(TAG, "云端歌单置顶状态同步失败", e)
                            _toastEvent.emit(e.toUserMessage(resourceProvider))
                        }
                    }
                }
            } else {
                syncPlaylistOrderToCloud()
            }
        }
    }

    private fun syncPlaylistOrderToCloud() {
        val state = _uiState.value as? LibraryUiState.Success ?: return
        val currentPinned = _pinnedIds.value
        val allPlaylists = state.allItems.filter { it.type == LibraryItemType.PLAYLIST }
        val customOrderMap = getCustomPlaylistOrderFromPrefs().mapIndexed { index, id -> id to index }.toMap()

        val pinnedPlaylists = allPlaylists.filter { currentPinned.contains(it.id) }
        val unpinnedPlaylists = allPlaylists.filter { !currentPinned.contains(it.id) }
            .sortedWith(compareBy<LibraryItem> { customOrderMap[it.id] ?: -1 }.thenByDescending { it.updateTime })

        val orderedPlaylists = pinnedPlaylists + unpinnedPlaylists
        val validIds = orderedPlaylists.mapNotNull { it.id.toLongOrNull() }.filter { it > 0 }
        if (validIds.isEmpty()) return

        saveCustomPlaylistOrderToPrefs(orderedPlaylists.map { it.id })

        viewModelScope.launch {
            userPlaylistRepository.updatePlaylistOrder(validIds).collect { result ->
                result.onFailure { e ->
                    AppLogger.w(TAG, "同步歌单顺序至云端失败", e)
                }
            }
        }
    }

    // onCreated 在创建成功后回调新歌单的 id 与名称，供调用方跳转
    fun createPlaylist(name: String, isPrivate: Boolean = false, onCreated: ((id: Long, name: String) -> Unit)? = null) {
        viewModelScope.launch {
            createRepository.createPlaylist(name, playlistPrivacyOf(isPrivate)).collect { result ->
                result.onSuccess { playlist ->
                    playlistMutationBus.emit(PlaylistMutationEvent.Created(playlist.id))
                    loadLibraryData()
                    _toastEvent.emit("歌单创建成功！")
                    onCreated?.invoke(playlist.id, playlist.name)
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 点击胶囊：再次点击已选中的胶囊会取消筛选，回到展示全部；切换主筛选时重置歌单二级筛选
    fun toggleFilter(filter: LibraryFilter) {
        _selectedFilter.value = if (_selectedFilter.value == filter) null else filter
        _selectedPlaylistOwnerFilter.value = null
        applyFilterAndSort()
    }

    // 直接设定主筛选，供桌面端前进/后退还原；同样重置歌单二级筛选
    fun selectFilter(filter: LibraryFilter?) {
        if (_selectedFilter.value == filter) return
        _selectedFilter.value = filter
        _selectedPlaylistOwnerFilter.value = null
        applyFilterAndSort()
    }

    fun clearFilter() {
        _selectedFilter.value = null
        _selectedPlaylistOwnerFilter.value = null
        applyFilterAndSort()
    }

    // 歌单二级筛选：再次点击已选中的胶囊会取消，恢复展示全部歌单
    fun togglePlaylistOwnerFilter(filter: LibraryPlaylistOwnerFilter) {
        _selectedPlaylistOwnerFilter.value = if (_selectedPlaylistOwnerFilter.value == filter) null else filter
        applyFilterAndSort()
    }

    fun updateSortOrder(order: LibrarySortOrder) {
        _sortOrder.value = order
        saveSortOrderToPrefs(order)
        applyFilterAndSort()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilterAndSort()
    }

    fun handleLoginSuccess(cookies: String) {
        viewModelScope.launch {
            _toastEvent.emit("登录成功，正在同步乐库...")
            val profile = syncProfileAfterLoginUseCase(cookies) ?: return@launch
            loadLibraryData(profile)
        }
    }

    fun savePlaylistOrder(orderedItems: List<LibraryItem>) {
        val playlistIds = orderedItems.filter { it.type == LibraryItemType.PLAYLIST }.map { it.id }
        saveCustomPlaylistOrderToPrefs(playlistIds)
        _sortOrder.value = LibrarySortOrder.CUSTOM
        saveSortOrderToPrefs(LibrarySortOrder.CUSTOM)
        applyFilterAndSort()

        val validPlaylistIds = playlistIds.mapNotNull { it.toLongOrNull() }.filter { it > 0 }
        if (validPlaylistIds.isEmpty()) return

        viewModelScope.launch {
            userPlaylistRepository.updatePlaylistOrder(validPlaylistIds).collect { result ->
                result.onSuccess {
                    _toastEvent.emit("歌单排序已保存")
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 拖拽调整排序页的初始顺序：沿用已保存的自定义顺序，未纳入过的条目排在最前面
    fun getPlaylistsForReorder(): List<LibraryItem> {
        val state = _uiState.value as? LibraryUiState.Success ?: return emptyList()
        val playlists = state.allItems.filter { it.type == LibraryItemType.PLAYLIST }
        val customOrderMap = getCustomPlaylistOrderFromPrefs().mapIndexed { index, id -> id to index }.toMap()
        return playlists.sortedWith(
            compareBy<LibraryItem> { customOrderMap[it.id] ?: -1 }.thenByDescending { it.updateTime }
        )
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId).collect { result ->
                result.onSuccess {
                    externalStore.clearPlaylist(playlistId)
                    _toastEvent.emit("歌单已删除")
                    playlistMutationBus.emit(PlaylistMutationEvent.Deleted(playlistId))
                    loadLibraryData()
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun unsubscribePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.subscribePlaylist(playlistId, subscribe = false).collect { result ->
                result.onSuccess {
                    _toastEvent.emit("已取消收藏歌单")
                    playlistMutationBus.emit(PlaylistMutationEvent.Subscribed(playlistId, false))
                    loadLibraryData()
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun unsubscribeAlbum(albumId: Long) {
        viewModelScope.launch {
            playlistRepository.subscribeAlbum(albumId, subscribe = false).collect { result ->
                result.onSuccess {
                    _toastEvent.emit("已取消收藏专辑")
                    loadLibraryData()
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    fun unsubscribeArtist(artistId: Long) {
        viewModelScope.launch {
            artistRepository.subscribeArtist(artistId, subscribe = false).collect { result ->
                result.onSuccess {
                    _toastEvent.emit("已取消关注歌手")
                    loadLibraryData()
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 批量下载歌单或专辑
    fun downloadLibraryItem(item: LibraryItem, level: String) {
        val id = item.id.toLongOrNull()
        if (id == null) {
            viewModelScope.launch { _toastEvent.emit("歌单/专辑信息不完整，无法下载") }
            return
        }
        val isAlbum = item.type == LibraryItemType.ALBUM
        val detailFlow = if (isAlbum) playlistRepository.getAlbumDetail(id) else playlistRepository.getPlaylistDetail(id)
        viewModelScope.launch {
            detailFlow.collect { result ->
                result.onSuccess { detail ->
                    // 超过1000首的歌单详情接口只下发前一部分曲目，需按 trackIds 补全后再入队
                    val tracksResult = if (!isAlbum && detail.trackIds.size > detail.tracks.size) {
                        playlistRepository.loadAllTracks(detail).first()
                    } else {
                        Result.success(detail.tracks)
                    }
                    tracksResult.onSuccess { allTracks ->
                        if (allTracks.isEmpty()) {
                            _toastEvent.emit(if (isAlbum) "该专辑没有可下载的歌曲" else "该歌单没有可下载的歌曲")
                        } else {
                            val tracks = allTracks.map { track ->
                                DownloadTrackInfo(
                                    track.id, track.name, track.ar.joinToString("/") { it.name },
                                    track.al.name, track.al.picUrl.takeIf { it.isNotBlank() },
                                    yearFromEpochMillis(track.publishTime)
                                )
                            }
                            val enqueueResult = songDownloadManager.enqueueBatch(
                                tracks, level, batchTag = "library_${item.type}_$id", batchLabel = item.title
                            )
                            _toastEvent.emit(enqueueResult.toToastMessage())
                        }
                    }.onFailure { e ->
                        _toastEvent.emit(e.toUserMessage(resourceProvider))
                    }
                }.onFailure { e ->
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
            }
        }
    }

    // 播放媒体库条目（歌单、专辑或艺人热门曲）
    fun playLibraryItem(item: LibraryItem) {
        val id = item.id.toLongOrNull()
        if (id == null) {
            viewModelScope.launch { _toastEvent.emit("数据信息不完整，无法播放") }
            return
        }
        viewModelScope.launch {
            when (item.type) {
                LibraryItemType.PLAYLIST, LibraryItemType.ALBUM -> {
                    val isAlbum = item.type == LibraryItemType.ALBUM
                    val detailFlow = if (isAlbum) playlistRepository.getAlbumDetail(id) else playlistRepository.getPlaylistDetail(id)
                    val detail = detailFlow.first().getOrElse {
                        _toastEvent.emit(it.toUserMessage(resourceProvider))
                        return@launch
                    }
                    val tracks = if (!isAlbum && detail.trackIds.size > detail.tracks.size) {
                        playlistRepository.loadAllTracks(detail).first().getOrElse { detail.tracks }
                    } else {
                        detail.tracks
                    }
                    if (tracks.isEmpty()) {
                        _toastEvent.emit(if (isAlbum) "该专辑没有可播放的歌曲" else "该歌单没有可播放的歌曲")
                        return@launch
                    }
                    val queueItems = tracks.map { QueueItem(it.id, it.name, it.ar.joinToString("/") { ar -> ar.name }, it.al.picUrl) }
                    val kind = if (isAlbum) PlaySource.Kind.ALBUM else PlaySource.Kind.PLAYLIST
                    playerManager.playQueue(queueItems, 0, detail.name, PlaySource(kind, detail.id, detail.name, detail.coverImgUrl))
                }
                LibraryItemType.ARTIST -> {
                    artistRepository.getArtistTopSongs(id).first().onSuccess { songs ->
                        if (songs.isEmpty()) {
                            _toastEvent.emit("该歌手暂无热门歌曲")
                            return@launch
                        }
                        val queueItems = songs.map { QueueItem(it.id, it.name, it.ar.joinToString("/") { ar -> ar.name }, it.al.picUrl) }
                        playerManager.playQueue(queueItems, 0, item.title)
                    }.onFailure { e ->
                        _toastEvent.emit(e.toUserMessage(resourceProvider))
                    }
                }
            }
        }
    }

    // 全部加入下一首播放
    fun addLibraryItemToPlayNext(item: LibraryItem) {
        val id = item.id.toLongOrNull()
        if (id == null) {
            viewModelScope.launch { _toastEvent.emit("数据信息不完整，无法添加到下一首") }
            return
        }
        viewModelScope.launch {
            when (item.type) {
                LibraryItemType.PLAYLIST, LibraryItemType.ALBUM -> {
                    val isAlbum = item.type == LibraryItemType.ALBUM
                    val detailFlow = if (isAlbum) playlistRepository.getAlbumDetail(id) else playlistRepository.getPlaylistDetail(id)
                    val detail = detailFlow.first().getOrElse {
                        _toastEvent.emit(it.toUserMessage(resourceProvider))
                        return@launch
                    }
                    val tracks = if (!isAlbum && detail.trackIds.size > detail.tracks.size) {
                        playlistRepository.loadAllTracks(detail).first().getOrElse { detail.tracks }
                    } else {
                        detail.tracks
                    }
                    if (tracks.isEmpty()) {
                        _toastEvent.emit(if (isAlbum) "该专辑没有可播放的歌曲" else "该歌单没有可播放的歌曲")
                        return@launch
                    }
                    val queueItems = tracks.map { QueueItem(it.id, it.name, it.ar.joinToString("/") { ar -> ar.name }, it.al.picUrl) }
                    playerManager.addToPlayNext(queueItems)
                    _toastEvent.emit("已添加到下一首播放")
                }
                LibraryItemType.ARTIST -> {
                    artistRepository.getArtistTopSongs(id).first().onSuccess { songs ->
                        if (songs.isEmpty()) {
                            _toastEvent.emit("该歌手暂无热门歌曲")
                            return@launch
                        }
                        val queueItems = songs.map { QueueItem(it.id, it.name, it.ar.joinToString("/") { ar -> ar.name }, it.al.picUrl) }
                        playerManager.addToPlayNext(queueItems)
                        _toastEvent.emit("已添加到下一首播放")
                    }.onFailure { e ->
                        _toastEvent.emit(e.toUserMessage(resourceProvider))
                    }
                }
            }
        }
    }

    // 更新自建歌单信息
    fun updatePlaylistInfo(
        id: Long,
        originalName: String,
        newName: String,
        originalDesc: String?,
        newDesc: String,
        coverBytes: ByteArray?,
        onComplete: (Boolean) -> Unit
    ) {
        val nameChanged = newName.isNotBlank() && newName != originalName
        val descChanged = newDesc != (originalDesc ?: "")

        if (!nameChanged && !descChanged && coverBytes == null) {
            onComplete(true)
            return
        }

        viewModelScope.launch {
            try {
                if (coverBytes != null) {
                    val newCoverUrl = updatePlaylistCoverUseCase(id, coverBytes).getOrElse { e ->
                        _toastEvent.emit(e.toUserMessage(resourceProvider))
                        onComplete(false)
                        return@launch
                    }
                    playlistMutationBus.emit(PlaylistMutationEvent.CoverUpdated(id, newCoverUrl))
                    if (!nameChanged && !descChanged) {
                        _toastEvent.emit("封面已更新")
                        onComplete(true)
                        return@launch
                    }
                }

                val nameDeferred = if (nameChanged) {
                    async { playlistRepository.renamePlaylist(id, newName).first() }
                } else null

                val descDeferred = if (descChanged) {
                    async { playlistRepository.updateDescription(id, newDesc).first() }
                } else null

                val nameResult = nameDeferred?.await()
                val descResult = descDeferred?.await()

                val nameFailed = nameResult?.isFailure == true
                val descFailed = descResult?.isFailure == true

                when {
                    nameFailed && descFailed -> {
                        _toastEvent.emit("修改歌单名称和简介失败")
                        onComplete(false)
                    }
                    nameFailed -> {
                        descResult?.getOrNull()?.let { playlistMutationBus.emit(PlaylistMutationEvent.DescriptionUpdated(id, newDesc)) }
                        _toastEvent.emit("修改歌单名称失败")
                        onComplete(false)
                    }
                    descFailed -> {
                        nameResult?.getOrNull()?.let { playlistMutationBus.emit(PlaylistMutationEvent.Renamed(id, newName)) }
                        _toastEvent.emit("修改歌单简介失败")
                        onComplete(false)
                    }
                    else -> {
                        if (nameChanged) playlistMutationBus.emit(PlaylistMutationEvent.Renamed(id, newName))
                        if (descChanged) playlistMutationBus.emit(PlaylistMutationEvent.DescriptionUpdated(id, newDesc))
                        _toastEvent.emit("歌单信息已更新")
                        onComplete(true)
                    }
                }
            } catch (e: Exception) {
                _toastEvent.emit(e.toUserMessage(resourceProvider))
                onComplete(false)
            }
        }
    }
}
