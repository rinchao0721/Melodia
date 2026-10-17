package com.lin0721.linmusic.feature.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.feature.music.data.MusicRepository
import com.lin0721.linmusic.feature.music.domain.StyleAlbumItem
import com.lin0721.linmusic.feature.music.domain.StyleArtistItem
import com.lin0721.linmusic.feature.music.domain.StylePlaylistItem
import com.lin0721.linmusic.feature.music.domain.StyleSongPage
import com.lin0721.linmusic.feature.music.domain.StyleSort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "StyleDetailViewModel"

// 曲风详情页 ViewModel，双端共用。由页面按 tagId 调 load，同一曲风重复进入时复用已加载内容
class StyleDetailViewModel(
    private val musicRepository: MusicRepository,
    private val playbackRepository: PlaybackRepository,
    private val playbackController: PlaybackController,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<StyleDetailUiState>(StyleDetailUiState.Loading)
    val uiState: StateFlow<StyleDetailUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    val nowPlaying: StateFlow<NowPlaying?> = playbackController.nowPlaying

    private var loadingTagId: Long? = null
    private var loadJob: Job? = null
    // 切二级标签：四段内容一起刷新
    private var contentJob: Job? = null
    // 切排序与翻页共用，后发起的覆盖先发起的
    private var songsJob: Job? = null

    private class Sections(
        val songs: Result<StyleSongPage>,
        val playlists: List<StylePlaylistItem>,
        val albums: List<StyleAlbumItem>,
        val artists: List<StyleArtistItem>
    )

    fun load(tagId: Long) {
        val current = _uiState.value
        if (current is StyleDetailUiState.Success && current.data.tagId == tagId) return
        if (loadJob?.isActive == true && loadingTagId == tagId) return

        loadingTagId = tagId
        cancelJobs()
        _uiState.value = StyleDetailUiState.Loading

        loadJob = viewModelScope.launch {
            try {
                val (stylesResult, headResult, sections) = coroutineScope {
                    val styles = async { musicRepository.getStyleList().awaitResult() }
                    val head = async { musicRepository.getStyleHead(tagId).awaitResult() }
                    val content = async { fetchSections(tagId, StyleSort.Hot) }
                    Triple(styles.await(), head.await(), content.await())
                }
                val head = headResult.getOrNull()
                val songPage = sections.songs.getOrNull()

                if (head == null && songPage == null && sections.playlists.isEmpty() &&
                    sections.albums.isEmpty() && sections.artists.isEmpty()
                ) {
                    val error = headResult.exceptionOrNull() ?: sections.songs.exceptionOrNull()
                    _uiState.value = StyleDetailUiState.Error(
                        error?.toUserMessage(resourceProvider) ?: resourceProvider.getString(AppString.ErrorBizDefault)
                    )
                    return@launch
                }

                // 二级标签只在曲风列表里有，列表拿不到就不展示筛选
                val children = stylesResult.getOrNull()?.firstOrNull { it.id == tagId }?.children.orEmpty()
                _uiState.value = StyleDetailUiState.Success(
                    StyleDetailData(
                        tagId = tagId,
                        head = head,
                        children = children,
                        songs = songPage?.songs.orEmpty(),
                        nextCursor = songPage?.nextCursor ?: 0,
                        hasMoreSongs = songPage?.hasMore ?: false,
                        playlists = sections.playlists,
                        albums = sections.albums,
                        artists = sections.artists
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = StyleDetailUiState.Error(e.toUserMessage(resourceProvider))
            }
        }
    }

    fun retry() {
        loadingTagId?.let(::load)
    }

    fun selectChild(childId: Long?) {
        val current = currentData() ?: return
        if (current.selectedChildId == childId) return

        contentJob?.cancel()
        songsJob?.cancel()
        val next = current.copy(
            selectedChildId = childId,
            isContentLoading = true,
            isSongsLoading = false,
            isLoadingMore = false
        )
        _uiState.value = StyleDetailUiState.Success(next)

        val requestTagId = next.activeTagId
        val requestSort = next.sort
        contentJob = viewModelScope.launch {
            val sections = fetchSections(requestTagId, requestSort)
            val latest = currentData() ?: return@launch
            if (latest.activeTagId != requestTagId) {
                AppLogger.d(TAG, "二级标签已切换，丢弃 tagId=$requestTagId 的返回")
                return@launch
            }
            val songPage = sections.songs.getOrNull()
            // 期间又切了排序的话，曲目交给排序那次请求
            val keepSongs = latest.sort != requestSort
            _uiState.value = StyleDetailUiState.Success(
                latest.copy(
                    isContentLoading = false,
                    songs = if (keepSongs) latest.songs else songPage?.songs.orEmpty(),
                    nextCursor = if (keepSongs) latest.nextCursor else songPage?.nextCursor ?: 0,
                    hasMoreSongs = if (keepSongs) latest.hasMoreSongs else songPage?.hasMore ?: false,
                    playlists = sections.playlists,
                    albums = sections.albums,
                    artists = sections.artists
                )
            )
        }
    }

    fun selectSort(sort: StyleSort) {
        val current = currentData() ?: return
        if (current.sort == sort) return

        songsJob?.cancel()
        val previousSort = current.sort
        _uiState.value = StyleDetailUiState.Success(
            current.copy(sort = sort, isSongsLoading = true, isLoadingMore = false)
        )

        val requestTagId = current.activeTagId
        songsJob = viewModelScope.launch {
            val result = musicRepository.getStyleSongs(requestTagId, 0, sort).awaitResult()
            val latest = currentData() ?: return@launch
            if (latest.activeTagId != requestTagId || latest.sort != sort) return@launch

            val page = result.getOrNull()
            if (page == null) {
                // 失败时退回原排序，列表仍是原排序的内容
                _uiState.value = StyleDetailUiState.Success(latest.copy(sort = previousSort, isSongsLoading = false))
                _toastEvent.emit(result.exceptionOrNull()?.toUserMessage(resourceProvider)
                    ?: resourceProvider.getString(AppString.ErrorBizDefault))
                return@launch
            }
            _uiState.value = StyleDetailUiState.Success(
                latest.copy(
                    isSongsLoading = false,
                    songs = page.songs,
                    nextCursor = page.nextCursor,
                    hasMoreSongs = page.hasMore
                )
            )
        }
    }

    fun loadMoreSongs() {
        val current = currentData() ?: return
        if (!current.hasMoreSongs || current.isLoadingMore || current.isContentLoading || current.isSongsLoading) return

        _uiState.value = StyleDetailUiState.Success(current.copy(isLoadingMore = true))
        val requestTagId = current.activeTagId
        val requestSort = current.sort
        val requestCursor = current.nextCursor
        songsJob = viewModelScope.launch {
            val page = musicRepository.getStyleSongs(requestTagId, requestCursor, requestSort).awaitResult().getOrNull()
            val latest = currentData() ?: return@launch
            if (latest.activeTagId != requestTagId || latest.sort != requestSort || latest.nextCursor != requestCursor) return@launch

            // 失败时保留 hasMore，滚到底会再次触发
            if (page == null) {
                _uiState.value = StyleDetailUiState.Success(latest.copy(isLoadingMore = false))
                return@launch
            }
            val knownIds = latest.songs.mapTo(HashSet()) { it.id }
            _uiState.value = StyleDetailUiState.Success(
                latest.copy(
                    isLoadingMore = false,
                    songs = latest.songs + page.songs.filter { knownIds.add(it.id) },
                    nextCursor = page.nextCursor,
                    hasMoreSongs = page.hasMore
                )
            )
        }
    }

    // 已加载的整段作为播放队列
    fun playSongAt(index: Int) {
        val data = currentData() ?: return
        val songs = data.songs
        if (songs.isEmpty()) return

        val queue = songs.map { track ->
            QueueItem(track.id, track.name, track.ar.joinToString("/") { it.name }, track.al.picUrl)
        }
        val styleName = data.head?.name ?: "曲风"
        playbackController.playQueue(queue, index.coerceIn(queue.indices), playContext = "style_$styleName")
    }

    fun playFavourite() {
        val track: Track = currentData()?.head?.favouriteSong ?: return
        playbackController.playQueue(
            listOf(QueueItem(track.id, track.name, track.ar.joinToString("/") { it.name }, track.al.picUrl)),
            0, playContext = "style_favourite"
        )
    }

    private fun currentData(): StyleDetailData? = (_uiState.value as? StyleDetailUiState.Success)?.data

    private fun cancelJobs() {
        loadJob?.cancel()
        contentJob?.cancel()
        songsJob?.cancel()
    }

    // 四段各自兜底：某一段拿不到就空着，不让整个曲风页变成错误页
    private suspend fun fetchSections(tagId: Long, sort: StyleSort): Sections = coroutineScope {
        val songs = async { musicRepository.getStyleSongs(tagId, 0, sort).awaitResult() }
        val playlists = async { musicRepository.getStylePlaylists(tagId).awaitResult().getOrNull().orEmpty() }
        val albums = async { musicRepository.getStyleAlbums(tagId, StyleSort.Hot).awaitResult().getOrNull().orEmpty() }
        val artists = async { musicRepository.getStyleArtists(tagId).awaitResult().getOrNull().orEmpty() }
        Sections(songs.await(), playlists.await(), albums.await(), artists.await())
    }

    // 取首个结果，流本身抛出的异常也折成 Result.failure；取消照常向上传播
    private suspend fun <T> Flow<Result<T>>.awaitResult(): Result<T> = try {
        first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
