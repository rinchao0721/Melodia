package com.lin0721.linmusic.feature.recent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.songquality.SongQualityCache
import com.lin0721.linmusic.feature.recent.data.RecentRepository
import com.lin0721.linmusic.feature.recent.domain.RecentSong
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "RecentPlayViewModel"

class RecentPlayViewModel(
    private val recentRepository: RecentRepository,
    val playerManager: PlaybackController,
    private val resourceProvider: ResourceProvider,
    private val songQualityCache: SongQualityCache? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecentPlayUiState>(RecentPlayUiState.Loading)
    val uiState: StateFlow<RecentPlayUiState> = _uiState.asStateFlow()

    private val _selectedTab = MutableStateFlow(RecentTab.SONG)
    val selectedTab: StateFlow<RecentTab> = _selectedTab.asStateFlow()

    init {
        load()
    }

    fun selectTab(tab: RecentTab) {
        _selectedTab.value = tab
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RecentPlayUiState.Loading

            val songsDeferred = async { fetch("最近播放歌曲") { recentRepository.getRecentSongs().first() } }
            val playlistsDeferred = async { fetch("最近播放歌单") { recentRepository.getRecentPlaylists().first() } }
            val albumsDeferred = async { fetch("最近播放专辑") { recentRepository.getRecentAlbums().first() } }

            val songs = songsDeferred.await()
            val playlists = playlistsDeferred.await()
            val albums = albumsDeferred.await()

            // 三类全军覆没才算失败；只挂一两类时仍把拿到的展示出来，对应 Tab 走空态
            val allFailed = songs.isFailure && playlists.isFailure && albums.isFailure
            if (allFailed) {
                val cause = songs.exceptionOrNull() ?: Exception("加载失败")
                _uiState.value = RecentPlayUiState.Error(cause.toUserMessage(resourceProvider))
                return@launch
            }

            val songList = songs.getOrDefault(emptyList())
            songQualityCache?.prefetch(songList.map { it.track })

            _uiState.value = RecentPlayUiState.Success(
                songs = songList,
                playlists = playlists.getOrDefault(emptyList()),
                albums = albums.getOrDefault(emptyList())
            )
        }
    }

    // 单类请求的异常兜底，避免任一类抛出后另外两类的结果一起丢失
    private suspend fun <T> fetch(label: String, block: suspend () -> Result<List<T>>): Result<List<T>> =
        runCatching { block() }
            .getOrElse { e ->
                AppLogger.w(TAG, "$label 加载异常", e)
                Result.failure(e)
            }
            .onFailure { AppLogger.w(TAG, "$label 加载失败", it) }

    // 以当前歌曲列表为播放队列，从点击项开始播
    fun playSong(song: RecentSong) {
        val state = _uiState.value as? RecentPlayUiState.Success ?: return
        if (state.songs.isEmpty()) return

        val queueItems = state.songs.map { item ->
            QueueItem(
                songId = item.track.id,
                title = item.track.name,
                artist = item.track.ar.joinToString(" / ") { artist -> artist.name },
                coverUrl = item.track.al.picUrl
            )
        }
        val startIndex = state.songs.indexOfFirst { it.track.id == song.track.id }.coerceAtLeast(0)
        playerManager.playQueue(queueItems, startIndex, "最近播放")
    }
}
