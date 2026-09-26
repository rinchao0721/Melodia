package com.lin0721.linmusic.feature.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.feature.music.data.MusicRepository
import com.lin0721.linmusic.feature.music.domain.StyleContent
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "MusicViewModel"

// 「音乐」tab ViewModel：曲风列表与偏好只在首次进入时拉一次，之后切曲风只刷新内容区
class MusicViewModel(
    private val musicRepository: MusicRepository,
    private val playbackRepository: PlaybackRepository,
    private val playerManager: PlayerManager,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<MusicUiState>(MusicUiState.Loading)
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    // 刻意不在 init 里拉数据：曲风列表 140KB 起步，进首页就请求会拖慢「全部」的首屏。
    // 首次切到「音乐」tab 时由 UI 触发，已加载过则直接复用。
    fun loadIfNeeded() {
        if (_uiState.value is MusicUiState.Success) return
        loadStyles()
    }

    fun loadStyles() {
        _uiState.value = MusicUiState.Loading

        viewModelScope.launch {
            try {
                val stylesDeferred = async { musicRepository.getStyleList().first() }
                // 偏好需登录，失败不应拖垮整页，未登录时按空列表处理
                val preferencesDeferred = async {
                    runCatching { musicRepository.getStylePreferences().first() }
                        .getOrDefault(Result.success(emptyList()))
                }

                val stylesResult = stylesDeferred.await()
                val preferences = preferencesDeferred.await().getOrDefault(emptyList())
                val styles = stylesResult.getOrNull()

                if (styles.isNullOrEmpty()) {
                    _uiState.value = MusicUiState.Error(
                        stylesResult.exceptionOrNull()?.toUserMessage(resourceProvider)
                            ?: resourceProvider.getString(AppString.ErrorBizDefault)
                    )
                    return@launch
                }

                // 有偏好就落在偏好页，否则直接落到第一个曲风
                val selection = if (preferences.isNotEmpty()) {
                    StyleSelection.Preference
                } else {
                    StyleSelection.Style(styles.first().id)
                }

                _uiState.value = MusicUiState.Success(
                    MusicFeedData(styles = styles, preferences = preferences, selection = selection)
                )
                loadContent()
            } catch (e: Exception) {
                _uiState.value = MusicUiState.Error(e.toUserMessage(resourceProvider))
            }
        }
    }

    fun selectStyle(selection: StyleSelection) {
        val current = _uiState.value as? MusicUiState.Success ?: return
        if (current.data.selection == selection && current.data.selectedChildId == null) return

        // 换一级曲风时清掉二级选中，否则会拿着上一个曲风的子标签去查
        _uiState.value = MusicUiState.Success(
            current.data.copy(selection = selection, selectedChildId = null, content = null)
        )
        loadContent()
    }

    fun selectChildStyle(childId: Long?) {
        val current = _uiState.value as? MusicUiState.Success ?: return
        if (current.data.selectedChildId == childId) return

        _uiState.value = MusicUiState.Success(current.data.copy(selectedChildId = childId, content = null))
        loadContent()
    }

    private fun loadContent() {
        val current = _uiState.value as? MusicUiState.Success ?: return
        val tagId = current.data.activeTagId ?: return

        _uiState.value = MusicUiState.Success(current.data.copy(isContentLoading = true))

        viewModelScope.launch {
            // 四个请求各自兜底：某一段拿不到就空着，不让整个曲风页变成错误页
            val headDeferred = async { runCatching { musicRepository.getStyleHead(tagId).first() }.getOrNull()?.getOrNull() }
            val playlistsDeferred = async { runCatching { musicRepository.getStylePlaylists(tagId).first() }.getOrNull()?.getOrNull().orEmpty() }
            val songsDeferred = async { runCatching { musicRepository.getStyleSongs(tagId).first() }.getOrNull()?.getOrNull().orEmpty() }
            val artistsDeferred = async { runCatching { musicRepository.getStyleArtists(tagId).first() }.getOrNull()?.getOrNull().orEmpty() }

            val content = StyleContent(
                head = headDeferred.await(),
                playlists = playlistsDeferred.await(),
                songs = songsDeferred.await(),
                artists = artistsDeferred.await()
            )

            val latest = _uiState.value as? MusicUiState.Success ?: return@launch
            // 期间用户可能又切了曲风，晚到的响应直接丢弃
            if (latest.data.activeTagId != tagId) {
                AppLogger.d(TAG, "曲风已切换，丢弃 tagId=$tagId 的返回")
                return@launch
            }
            _uiState.value = MusicUiState.Success(latest.data.copy(content = content, isContentLoading = false))
        }
    }

    // 从曲风单曲列表指定位置起播，整段作为播放队列
    fun playSongAt(index: Int) {
        val current = _uiState.value as? MusicUiState.Success ?: return
        val songs = current.data.content?.songs.orEmpty()
        if (songs.isEmpty()) return

        val queue = songs.map { track ->
            QueueItem(track.id, track.name, track.ar.joinToString("/") { it.name }, track.al.picUrl)
        }
        val styleName = current.data.content?.head?.name ?: "曲风"
        playerManager.playQueue(queue, index.coerceIn(queue.indices), playContext = "style_$styleName")
    }

    // 播放曲风头部的「你在此曲风最爱」
    fun playFavouriteSong(track: Track) {
        viewModelScope.launch {
            playbackRepository.getSongUrl(track.id).collect { result ->
                result.onSuccess { url ->
                    playerManager.playAudio(
                        track.id,
                        url,
                        track.name,
                        track.ar.joinToString("/") { it.name },
                        track.al.picUrl,
                        playContext = "style_favourite"
                    )
                }.onFailure { error ->
                    _toastEvent.emit(error.toUserMessage(resourceProvider))
                }
            }
        }
    }
}
