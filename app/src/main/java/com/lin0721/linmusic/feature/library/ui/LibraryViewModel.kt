package com.lin0721.linmusic.feature.library.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.auth.SyncProfileAfterLoginUseCase
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.userartist.UserArtistRepository
import com.lin0721.linmusic.feature.create.data.CreateRepository
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import com.lin0721.linmusic.feature.library.data.LibraryRepository
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationBus
import com.lin0721.linmusic.core.playlistmutation.PlaylistMutationEvent
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val TAG = "LibraryViewModel"

enum class LibraryItemType {
    PLAYLIST, ARTIST, ALBUM, MV
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
    val isOwnedByMe: Boolean = false
)

enum class LibraryFilter {
    PLAYLIST, ALBUM, ARTIST, MV
}

// 歌单二级筛选：仅在 LibraryFilter.PLAYLIST 生效，区分自建歌单与收藏他人歌单
enum class LibraryPlaylistOwnerFilter {
    MINE, OTHERS
}

enum class LibrarySortOrder {
    RECENTLY_PLAYED, CREATE_TIME, NAME
}

class LibraryViewModel(
    private val syncProfileAfterLoginUseCase: SyncProfileAfterLoginUseCase,
    private val createRepository: CreateRepository,
    private val libraryRepository: LibraryRepository,
    private val userPlaylistRepository: UserPlaylistRepository,
    private val userArtistRepository: UserArtistRepository,
    private val userPreferences: UserPreferences,
    val playerManager: PlayerManager,
    private val context: Context,
    private val resourceProvider: ResourceProvider,
    private val playlistMutationBus: PlaylistMutationBus
) : ViewModel() {

    private val sharedPrefs = context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)
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

    private val _sortOrder = MutableStateFlow(LibrarySortOrder.RECENTLY_PLAYED)
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
                }
            }
        }
    }

    private fun getPinnedIdsFromPrefs(): Set<String> {
        return sharedPrefs.getStringSet("pinned_ids", emptySet()) ?: emptySet()
    }

    private fun savePinnedIdsToPrefs(ids: Set<String>) {
        sharedPrefs.edit().putStringSet("pinned_ids", ids).apply()
    }

    private fun getGridViewFromPrefs(): Boolean {
        // 读取视图切换记忆，默认列表视图 (false)
        return sharedPrefs.getBoolean("is_grid_view", false)
    }

    fun updateGridView(isGrid: Boolean) {
        _isGridView.value = isGrid
        sharedPrefs.edit().putBoolean("is_grid_view", isGrid).apply()
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
                    result?.getOrNull() ?: emptyList()
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

                val playlists = playlistsDeferred.await()
                val artists = artistsDeferred.await()
                val albums = albumsDeferred.await()
                val subcount = subcountDeferred.await()

                // 数据归一化 (Mapping)
                val mappedPlaylists = playlists.mapIndexed { index, playlist ->
                    LibraryItem(
                        id = playlist.id.toString(),
                        title = playlist.name,
                        subtitle = "歌单 · ${playlist.creator?.nickname ?: ""}",
                        coverUrl = playlist.coverImgUrl,
                        type = LibraryItemType.PLAYLIST,
                        updateTime = playlist.updateTime,
                        trackCount = playlist.trackCount,
                        playCount = playlist.playCount,
                        isLikedSongs = index == 0 && playlist.creator?.userId == profile.uid,
                        isOwnedByMe = playlist.creator?.userId == profile.uid
                    )
                }.toMutableList()

                val recordPlaylist = LibraryItem(
                    id = "-2",
                    title = "听歌排行的歌单",
                    subtitle = "歌单 · 听歌排行统计",
                    coverUrl = "",
                    type = LibraryItemType.PLAYLIST,
                    updateTime = System.currentTimeMillis(),
                    trackCount = 0,
                    playCount = 0,
                    isLikedSongs = false,
                    isOwnedByMe = true
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

                val combinedItems = mappedPlaylists + mappedArtists + mappedAlbums

                _uiState.update { state ->
                    LibraryUiState.Success(
                        allItems = combinedItems,
                        filteredItems = (state as? LibraryUiState.Success)?.filteredItems ?: emptyList(),
                        artistCount = subcount?.artistCount ?: artists.size,
                        playlistCount = subcount?.playlistCount ?: playlists.size,
                        albumCount = subcount?.albumCount ?: albums.size
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
            LibraryFilter.MV -> list.filter { it.type == LibraryItemType.MV }
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

        val sortedUnpinned = when (sort) {
            LibrarySortOrder.RECENTLY_PLAYED -> {
                unpinnedItems.sortedByDescending { it.updateTime }
            }
            LibrarySortOrder.CREATE_TIME -> {
                unpinnedItems.sortedByDescending { it.updateTime }
            }
            LibrarySortOrder.NAME -> {
                unpinnedItems.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            }
        }

        val finalList = pinnedItems + sortedUnpinned

        val listWithoutSpecial = finalList.filter { it.id != "-2" && !it.isLikedSongs }
        val likedSongsItem = finalList.find { it.isLikedSongs }
        val recordItem = finalList.find { it.id == "-2" }

        val finalListAdjusted = mutableListOf<LibraryItem>()
        if (likedSongsItem != null) {
            finalListAdjusted.add(likedSongsItem)
        }
        if (recordItem != null) {
            finalListAdjusted.add(recordItem)
        }
        finalListAdjusted.addAll(listWithoutSpecial)

        _uiState.update { current ->
            if (current is LibraryUiState.Success) current.copy(filteredItems = finalListAdjusted) else current
        }
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

        val title = (_uiState.value as? LibraryUiState.Success)?.allItems?.firstOrNull { it.id == itemId }?.title
        if (title != null) {
            viewModelScope.launch {
                _toastEvent.emit("已${if (willPin) "置顶" else "取消置顶"}: $title")
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            createRepository.createPlaylist(name).collect { result ->
                result.onSuccess {
                    loadLibraryData()
                    _toastEvent.emit("歌单创建成功！")
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

    fun logout() {
        viewModelScope.launch {
            userPreferences.clearUserProfile()
        }
    }
}
