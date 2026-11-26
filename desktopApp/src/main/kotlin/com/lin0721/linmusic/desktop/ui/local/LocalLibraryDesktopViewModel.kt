package com.lin0721.linmusic.desktop.ui.local

import com.lin0721.linmusic.core.localmusic.data.LocalSearch
import com.lin0721.linmusic.core.localmusic.data.LocalSearchResult
import com.lin0721.linmusic.core.localmusic.domain.LocalAlbum
import com.lin0721.linmusic.core.localmusic.domain.LocalArtist
import com.lin0721.linmusic.core.localmusic.domain.LocalLibraryIndex
import com.lin0721.linmusic.core.localmusic.domain.LocalPlaylist
import com.lin0721.linmusic.core.localmusic.domain.LocalTrack
import com.lin0721.linmusic.core.localmusic.ui.LocalFolderNode
import com.lin0721.linmusic.core.localmusic.ui.LocalFolderTree
import com.lin0721.linmusic.core.localmusic.ui.LocalTrackSortOrder
import com.lin0721.linmusic.core.localmusic.ui.ResumePlaybackInfo
import com.lin0721.linmusic.core.localmusic.ui.filterDownloadTracks
import com.lin0721.linmusic.core.localmusic.ui.resolveResumePlayback
import com.lin0721.linmusic.core.localmusic.ui.sortLocalTracks
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.QueueState
import com.lin0721.linmusic.desktop.data.local.LocalLibraryRepository
import com.lin0721.linmusic.desktop.data.local.toDesktopTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LocalLibraryNav(val label: String) {
    SONGS("歌曲"),
    ALBUMS("专辑"),
    ARTISTS("歌手"),
    FOLDERS("文件夹"),
    DOWNLOADS("下载")
}

// 桌面本地模式曲库状态与操作协调
class LocalLibraryDesktopViewModel(
    private val localLibrary: LocalLibraryRepository,
    private val controller: PlaybackController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _selectedNav = MutableStateFlow(LocalLibraryNav.SONGS)
    val selectedNav: StateFlow<LocalLibraryNav> = _selectedNav.asStateFlow()

    private val _sortOrder = MutableStateFlow(LocalTrackSortOrder.DATE_DESC)
    val sortOrder: StateFlow<LocalTrackSortOrder> = _sortOrder.asStateFlow()

    private val _selectedFolderPath = MutableStateFlow("")
    val selectedFolderPath: StateFlow<String> = _selectedFolderPath.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<LocalAlbum?>(null)
    val selectedAlbum: StateFlow<LocalAlbum?> = _selectedAlbum.asStateFlow()

    private val _selectedArtist = MutableStateFlow<LocalArtist?>(null)
    val selectedArtist: StateFlow<LocalArtist?> = _selectedArtist.asStateFlow()

    val index: StateFlow<LocalLibraryIndex> = localLibrary.sharedLibrary.observeIndex()
        .stateIn(scope, SharingStarted.Eagerly, LocalLibraryIndex(emptyList(), emptyList(), emptyList(), emptyList()))

    val playlists: StateFlow<List<LocalPlaylist>> = localLibrary.sharedPlaylists.observePlaylists()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val sortedTracks: StateFlow<List<LocalTrack>> = combine(index, _sortOrder) { idx, order ->
        sortLocalTracks(idx.tracks, order)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val downloadTracks: StateFlow<List<LocalTrack>> = combine(index, _sortOrder) { idx, order ->
        sortLocalTracks(filterDownloadTracks(idx.tracks), order)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val folderTree: StateFlow<LocalFolderNode> = index.map { idx ->
        LocalFolderTree.buildTree(idx.tracks)
    }.stateIn(scope, SharingStarted.Eagerly, LocalFolderTree.buildTree(emptyList()))

    val resumeInfo: StateFlow<ResumePlaybackInfo?> = combine(controller.queue, controller.currentIndex) { queue, idx ->
        resolveResumePlayback(QueueState(queue, idx))
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun selectNav(nav: LocalLibraryNav) {
        _selectedNav.value = nav
        if (nav != LocalLibraryNav.ALBUMS) _selectedAlbum.value = null
        if (nav != LocalLibraryNav.ARTISTS) _selectedArtist.value = null
    }

    fun setSortOrder(order: LocalTrackSortOrder) {
        _sortOrder.value = order
    }

    fun selectFolder(path: String) {
        _selectedFolderPath.value = path
    }

    fun selectAlbum(album: LocalAlbum?) {
        _selectedAlbum.value = album
    }

    fun selectArtist(artist: LocalArtist?) {
        _selectedArtist.value = artist
    }

    fun searchLocal(query: String): LocalSearchResult = LocalSearch.search(query, index.value)

    fun shuffleAll(tracks: List<LocalTrack>) {
        if (tracks.isEmpty()) return
        val shuffled = tracks.shuffled()
        controller.playQueue(shuffled.map(::toQueueItem), 0)
    }

    fun playTracks(tracks: List<LocalTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return
        controller.playQueue(tracks.map(::toQueueItem), startIndex.coerceIn(0, tracks.lastIndex))
    }

    fun resumeLastQueue() {
        val queue = controller.queue.value
        if (queue.isEmpty()) return
        val index = controller.currentIndex.value.coerceIn(0, queue.lastIndex)
        controller.playQueue(queue, index)
    }

    fun reorderPlaylists(orderedIds: List<Long>) {
        scope.launch {
            localLibrary.sharedPlaylists.reorderPlaylists(orderedIds)
        }
    }

    fun createPlaylist(name: String) {
        scope.launch {
            localLibrary.createPlaylist(name)
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        localLibrary.rename(id, name)
    }

    fun setPinned(id: Long, pinned: Boolean) {
        localLibrary.setPinned(id, pinned)
    }

    fun deletePlaylist(id: Long) {
        localLibrary.removePlaylist(id)
    }

    fun addTrackToPlaylist(playlistId: Long, track: LocalTrack) {
        scope.launch {
            localLibrary.sharedPlaylists.addTracks(playlistId, listOf(track.location))
        }
    }

    // 与歌单页共用同一套占位 id 与封面，保证按 songId 反查本地路径一致
    fun toQueueItem(track: LocalTrack): QueueItem = localLibrary.toQueueItem(track.toDesktopTrack())
}
