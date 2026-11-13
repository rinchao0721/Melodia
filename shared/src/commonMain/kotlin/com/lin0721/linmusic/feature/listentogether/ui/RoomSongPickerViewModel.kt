package com.lin0721.linmusic.feature.listentogether.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.listentogether.ListenTogetherSession
import com.lin0721.linmusic.core.listentogether.data.RoomSongSource
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomSongPickerState(
    val query: String = "",
    val loading: Boolean = false,
    val tracks: List<QueueItem> = emptyList(),
    // 列表标题：「我喜欢的音乐」或搜索结果
    val title: String = "",
    val error: String? = null,
    // 本次面板内已提交添加的歌曲，用于按钮置为「已添加」
    val added: Set<Long> = emptySet()
)

class RoomSongPickerViewModel(
    private val source: RoomSongSource,
    private val session: ListenTogetherSession,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    companion object {
        private const val LIKED_LIMIT = 50
        private const val SEARCH_LIMIT = 30
        private const val SEARCH_DEBOUNCE_MS = 400L
    }

    private val _state = MutableStateFlow(RoomSongPickerState())
    val state: StateFlow<RoomSongPickerState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // 已在房间队列中的歌曲，面板打开期间他人点的歌也会及时标为已添加
    val roomSongIds: StateFlow<Set<Long>> = session.state
        .map { it.roomSongIds.toSet() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, session.state.value.roomSongIds.toSet())

    private var loadJob: Job? = null

    // 每次打开面板重置为「我喜欢的音乐」
    fun open() {
        _state.value = RoomSongPickerState()
        load("", debounce = false)
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        load(query.trim(), debounce = true)
    }

    fun add(item: QueueItem) {
        if (item.songId <= 0L || item.songId in _state.value.added) return
        _state.update { it.copy(added = it.added + item.songId) }
        viewModelScope.launch {
            session.addToRoomQueue(listOf(item.songId))
                .onSuccess { count -> _messages.tryEmit(if (count > 0) "已添加《${item.title}》" else "《${item.title}》已在一起听队列中") }
                .onFailure { error ->
                    _state.update { it.copy(added = it.added - item.songId) }
                    _messages.tryEmit(error.toUserMessage(resourceProvider))
                }
        }
    }

    private fun load(keyword: String, debounce: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(loading = true, error = null) }
            try {
                val tracks = if (keyword.isEmpty()) source.liked(LIKED_LIMIT) else source.search(keyword, SEARCH_LIMIT)
                _state.update {
                    it.copy(
                        loading = false,
                        tracks = tracks.filter { track -> track.songId > 0L },
                        title = if (keyword.isEmpty()) "我喜欢的音乐" else "搜索结果"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, tracks = emptyList(), error = e.toUserMessage(resourceProvider)) }
            }
        }
    }
}
