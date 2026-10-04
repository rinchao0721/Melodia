package com.lin0721.linmusic.feature.newworks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.songlike.SongLikeRepository
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.core.userplaylist.UserPlaylistRepository
import com.lin0721.linmusic.feature.library.data.LibraryRepository
import com.lin0721.linmusic.feature.library.domain.LibraryCollectionMutationBus
import com.lin0721.linmusic.feature.library.domain.LibraryCollectionMutationEvent
import com.lin0721.linmusic.feature.newworks.data.NewWorksRepository
import com.lin0721.linmusic.feature.newworks.domain.NewWorksRelease
import com.lin0721.linmusic.feature.newworks.domain.NewWorksTrack
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import com.lin0721.linmusic.feature.playlist.domain.CreatePlaylistAndAddSongUseCase
import com.lin0721.linmusic.feature.playlist.domain.SongCollectDelegate
import com.lin0721.linmusic.feature.playlist.ui.PlaylistImportState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "NewWorksViewModel"
private const val PLAY_CONTEXT = "音乐新作"

class NewWorksViewModel(
    private val repository: NewWorksRepository,
    private val playbackController: PlaybackController,
    private val resourceProvider: ResourceProvider,
    private val playlistRepository: PlaylistRepository,
    private val userPlaylistRepository: UserPlaylistRepository,
    private val createPlaylistAndAddSongUseCase: CreatePlaylistAndAddSongUseCase,
    private val songCollectDelegate: SongCollectDelegate,
    private val songLikeRepository: SongLikeRepository,
    private val userPreferences: UserPreferences,
    private val libraryRepository: LibraryRepository,
    private val libraryCollectionMutationBus: LibraryCollectionMutationBus
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewWorksUiState>(NewWorksUiState.Loading)
    val uiState: StateFlow<NewWorksUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    val collectState: StateFlow<PlaylistCollectState> = songCollectDelegate.state

    private val _importState = MutableStateFlow(PlaylistImportState())
    val importState: StateFlow<PlaylistImportState> = _importState.asStateFlow()

    private val libraryAlbumIds = MutableStateFlow<Set<Long>>(emptySet())

    // 卡片按钮状态：已收藏专辑、已喜欢单曲、当前播放曲目
    val releaseStatus: StateFlow<NewWorksReleaseStatus> = combine(
        libraryAlbumIds,
        songLikeRepository.likedSongIds,
        playbackController.nowPlaying,
        playbackController.isPlaying
    ) { albumIds, likedIds, nowPlaying, playing ->
        NewWorksReleaseStatus(albumIds, likedIds, nowPlaying?.songId, playing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), currentStatus())

    // 动作里直接读各来源的当前值，不依赖 releaseStatus 是否有订阅者
    private fun currentStatus() = NewWorksReleaseStatus(
        libraryAlbumIds.value,
        songLikeRepository.likedSongIds.value,
        playbackController.nowPlaying.value?.songId,
        playbackController.isPlaying.value
    )

    private var cursor: Long = System.currentTimeMillis()
    private var loaded = false
    private var job: Job? = null

    // 首页音乐 tab 每次挂载都会调，避免切来切去重复请求
    fun loadIfNeeded() {
        if (!loaded) load()
    }

    fun load() {
        loaded = true
        cursor = System.currentTimeMillis()
        job?.cancel()
        viewModelScope.launch { refreshLibraryAlbums() }
        job = viewModelScope.launch {
            _uiState.value = NewWorksUiState.Loading

            fetch("新发布") { repository.getReleases(cursor, firstRequest = true) }
                .onSuccess { page ->
                    cursor = page.nextCursor
                    _uiState.value = NewWorksUiState.Success(
                        releases = page.items.distinctByReleaseKey(),
                        hasMore = page.hasMore,
                        isLoadingMore = false
                    )
                }.onFailure { error ->
                    _uiState.value = NewWorksUiState.Error(error.toUserMessage(resourceProvider))
                }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current !is NewWorksUiState.Success || current.isLoadingMore || !current.hasMore) return

        job?.cancel()
        job = viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true)
            fetch("新发布-翻页") { repository.getReleases(cursor, firstRequest = false) }
                .onSuccess { page ->
                    cursor = page.nextCursor
                    val latest = _uiState.value
                    if (latest is NewWorksUiState.Success) {
                        _uiState.value = latest.copy(
                            releases = (latest.releases + page.items).distinctByReleaseKey(),
                            hasMore = page.hasMore,
                            isLoadingMore = false
                        )
                    }
                }
                .onFailure {
                    val latest = _uiState.value
                    if (latest is NewWorksUiState.Success) {
                        _uiState.value = latest.copy(isLoadingMore = false)
                    }
                }
        }
    }

    // 该发布正在播放时切换暂停/继续，否则从头播放该发布
    fun togglePlayRelease(release: NewWorksRelease) {
        if (currentStatus().containsNowPlaying(release)) {
            playbackController.togglePlayPause()
        } else {
            playRelease(release)
        }
    }

    // 单曲直接播放，专辑播放整张
    fun playRelease(release: NewWorksRelease) {
        viewModelScope.launch {
            val tracks = resolveTracks(release)
            if (tracks.isEmpty()) return@launch
            playbackController.playQueue(tracks.map { it.toQueueItem() }, 0, PLAY_CONTEXT)
        }
    }

    fun addToPlayNext(release: NewWorksRelease) {
        viewModelScope.launch {
            val tracks = resolveTracks(release)
            if (tracks.isEmpty()) return@launch
            playbackController.addToPlayNext(tracks.map { it.toQueueItem() })
            _toastEvent.emit("已添加至下一首播放")
        }
    }

    // 专辑 = 收藏/取消收藏专辑，单曲 = 喜欢/取消喜欢歌曲，按卡片当前状态反向切换
    fun toggleInLibrary(release: NewWorksRelease) {
        viewModelScope.launch {
            if (!ensureLoggedIn()) return@launch
            val inLibrary = currentStatus().isInLibrary(release)
            if (release.isAlbum) {
                playlistRepository.subscribeAlbum(release.id, subscribe = !inLibrary).firstOrNull()
                    ?.onSuccess {
                        libraryAlbumIds.update { if (inLibrary) it - release.id else it + release.id }
                        val ownerUid = userPreferences.userProfile.first()?.uid ?: return@onSuccess
                        libraryCollectionMutationBus.emit(
                            ownerUid,
                            LibraryCollectionMutationEvent.AlbumChanged(
                                id = release.id,
                                isCollected = !inLibrary,
                                name = release.title,
                                artistNames = release.artistName,
                                coverUrl = release.coverUrl,
                                updateTime = System.currentTimeMillis()
                            )
                        )
                        _toastEvent.emit(if (inLibrary) "已取消收藏专辑" else "已收藏专辑")
                    }
                    ?.onFailure { e -> _toastEvent.emit(e.toUserMessage(resourceProvider)) }
            } else {
                // 喜欢状态由 SongLikeRepository 乐观更新并在失败时回滚
                songLikeRepository.likeSong(release.id, like = !inLibrary).firstOrNull()
                    ?.onSuccess { _toastEvent.emit(if (inLibrary) "已从我喜欢的音乐中移除" else "已添加到我喜欢的音乐") }
                    ?.onFailure { e -> _toastEvent.emit(e.toUserMessage(resourceProvider)) }
            }
        }
    }

    // 已登录才回调 onReady（由界面据此打开弹层），未登录只提示
    fun prepareCollectDialog(songId: Long, onReady: () -> Unit) {
        viewModelScope.launch {
            if (!ensureLoggedIn()) return@launch
            onReady()
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
                onLikedChanged = { songLikeRepository.syncLikedSongIds(it) }
            )
        }
    }

    fun createPlaylistAndAddSong(name: String, songId: Long) {
        viewModelScope.launch {
            songCollectDelegate.createAndAdd(name, songId, songLikeRepository.likedSongIds.value) { _toastEvent.emit(it) }
        }
    }

    // 拉取自建歌单（不含「我喜欢的音乐」）作为「加入歌单」的目标
    fun prepareImportTargets(onReady: () -> Unit) {
        viewModelScope.launch {
            val profile = userPreferences.userProfile.first()
            if (profile == null) {
                _toastEvent.emit(LOGIN_REQUIRED_MESSAGE)
                return@launch
            }
            onReady()
            _importState.update { it.copy(isLoading = true) }
            userPlaylistRepository.getUserPlaylists(profile.uid).firstOrNull()
                ?.onSuccess { playlists ->
                    _importState.value = PlaylistImportState(
                        items = playlists.filter { it.userId == profile.uid && it.id != profile.uid },
                        isLoading = false
                    )
                }
                ?.onFailure { e ->
                    _importState.update { it.copy(isLoading = false) }
                    _toastEvent.emit(e.toUserMessage(resourceProvider))
                }
                ?: _importState.update { it.copy(isLoading = false) }
        }
    }

    fun addToPlaylist(release: NewWorksRelease, playlistId: Long) {
        viewModelScope.launch {
            val ids = resolveTracks(release).map { it.id }
            if (ids.isEmpty()) return@launch
            playlistRepository.manipulatePlaylistTracks("add", playlistId, ids).firstOrNull()
                ?.onSuccess { _toastEvent.emit("已添加 ${ids.size} 首歌曲到歌单") }
                ?.onFailure { e -> _toastEvent.emit(e.toUserMessage(resourceProvider)) }
        }
    }

    fun createPlaylistAndAdd(release: NewWorksRelease, name: String) {
        viewModelScope.launch {
            val ids = resolveTracks(release).map { it.id }
            if (ids.isEmpty()) return@launch
            createPlaylistAndAddSongUseCase(name, ids).firstOrNull()
                ?.onSuccess { _toastEvent.emit("已创建歌单并添加 ${ids.size} 首歌曲") }
                ?.onFailure { e -> _toastEvent.emit(e.toUserMessage(resourceProvider)) }
        }
    }

    // 接口没内联曲目的专辑，点开操作时再按需补拉专辑详情
    private suspend fun resolveTracks(release: NewWorksRelease): List<NewWorksTrack> {
        if (release.tracks.isNotEmpty() || !release.isAlbum) return release.tracks
        val detail = runCatching { playlistRepository.getAlbumDetail(release.id).firstOrNull() }
            .getOrNull()
            ?.onFailure { e -> _toastEvent.emit(e.toUserMessage(resourceProvider)) }
            ?.getOrNull()
        return detail?.tracks.orEmpty().map { track ->
            NewWorksTrack(
                id = track.id,
                title = track.name,
                artistName = track.ar.joinToString(" / ") { it.name },
                coverUrl = track.al.picUrl.ifBlank { release.coverUrl }
            )
        }
    }

    // 已收藏专辑列表只用于卡片状态展示，失败或未登录时按空集处理
    private suspend fun refreshLibraryAlbums() {
        if (userPreferences.userProfile.first() == null) {
            libraryAlbumIds.value = emptySet()
            return
        }
        libraryRepository.getCollectedAlbums().firstOrNull()
            ?.onSuccess { albums -> libraryAlbumIds.value = albums.map { it.id }.toSet() }
            ?.onFailure { AppLogger.w(TAG, "收藏专辑列表加载失败", it) }
    }

    private suspend fun ensureLoggedIn(): Boolean {
        if (userPreferences.userProfile.first() != null) return true
        _toastEvent.emit(LOGIN_REQUIRED_MESSAGE)
        return false
    }

    private fun NewWorksTrack.toQueueItem() = QueueItem(id, title, artistName, coverUrl)

    // 单类请求的异常兜底，避免一类失败连累另一类结果丢失
    private suspend fun <T> fetch(label: String, block: suspend () -> Flow<Result<T>>): Result<T> =
        runCatching { block().firstOrNull() ?: Result.failure(IllegalStateException("$label 无响应")) }
            .getOrElse { e -> Result.failure(e) }
            .onFailure { AppLogger.w(TAG, "$label 加载失败", it) }

    // 真机实测：同一专辑会在单页内重复出现，翻页游标边界重叠时也会跨页重复——
    // 网格用 (isAlbum, id) 做 key，重复项会直接让 LazyVerticalGrid 崩溃，必须去重
    private fun List<NewWorksRelease>.distinctByReleaseKey(): List<NewWorksRelease> =
        distinctBy { it.isAlbum to it.id }

    private companion object {
        const val LOGIN_REQUIRED_MESSAGE = "请先登录账号"
    }
}
