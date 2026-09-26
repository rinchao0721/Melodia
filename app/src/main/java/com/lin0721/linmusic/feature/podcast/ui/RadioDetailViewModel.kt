package com.lin0721.linmusic.feature.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.podcast.data.PodcastRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 服务端一页给 30 条
private const val PAGE_SIZE = 30

// 电台详情页 ViewModel
class RadioDetailViewModel(
    private val podcastRepository: PodcastRepository,
    private val playerManager: PlayerManager,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<RadioDetailUiState>(RadioDetailUiState.Loading)
    val uiState: StateFlow<RadioDetailUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private var currentRadioId: Long = 0

    fun load(radioId: Long) {
        // 同一个电台重复进入不必重拉
        if (currentRadioId == radioId && _uiState.value is RadioDetailUiState.Success) return
        currentRadioId = radioId
        _uiState.value = RadioDetailUiState.Loading

        viewModelScope.launch {
            try {
                val detailDeferred = async { podcastRepository.getRadioDetail(radioId).first() }
                val programsDeferred = async {
                    runCatching { podcastRepository.getRadioPrograms(radioId).first() }
                        .getOrDefault(Result.success(emptyList()))
                }

                val detailResult = detailDeferred.await()
                val detail = detailResult.getOrNull()

                if (detail == null) {
                    _uiState.value = RadioDetailUiState.Error(
                        detailResult.exceptionOrNull()?.toUserMessage(resourceProvider)
                            ?: resourceProvider.getString(AppString.ErrorBizDefault)
                    )
                    return@launch
                }

                val programs = programsDeferred.await().getOrDefault(emptyList())
                _uiState.value = RadioDetailUiState.Success(
                    detail = detail,
                    programs = programs,
                    hasMore = programs.size >= PAGE_SIZE
                )
            } catch (e: Exception) {
                _uiState.value = RadioDetailUiState.Error(e.toUserMessage(resourceProvider))
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value as? RadioDetailUiState.Success ?: return
        if (!current.hasMore || current.isLoadingMore) return

        _uiState.value = current.copy(isLoadingMore = true)

        viewModelScope.launch {
            val more = runCatching {
                podcastRepository.getRadioPrograms(currentRadioId, offset = current.programs.size).first()
            }.getOrNull()?.getOrNull()

            val latest = _uiState.value as? RadioDetailUiState.Success ?: return@launch
            _uiState.value = if (more.isNullOrEmpty()) {
                // 追加失败或已到底，停止继续翻页但保留已有内容
                latest.copy(isLoadingMore = false, hasMore = false)
            } else {
                latest.copy(
                    programs = latest.programs + more,
                    isLoadingMore = false,
                    hasMore = more.size >= PAGE_SIZE
                )
            }
        }
    }

    // 订阅接口在未登录时同样返回 code 200 却不会真的生效，所以必须在这里拦住，
    // 否则会给出「已订阅」的假反馈
    fun toggleSubscribe() {
        val current = _uiState.value as? RadioDetailUiState.Success ?: return
        if (current.isSubscribing) return

        viewModelScope.launch {
            if (userPreferences.userProfile.first() == null) {
                _toastEvent.emit("登录后才能订阅电台")
                return@launch
            }

            val target = !current.detail.subscribed
            _uiState.value = current.copy(isSubscribing = true)

            val result = runCatching {
                podcastRepository.setRadioSubscribed(current.detail.id, target).first()
            }.getOrNull()

            val latest = _uiState.value as? RadioDetailUiState.Success ?: return@launch
            if (result?.isSuccess == true) {
                _uiState.value = latest.copy(
                    detail = latest.detail.copy(subscribed = target),
                    isSubscribing = false
                )
                _toastEvent.emit(if (target) "已订阅" else "已取消订阅")
            } else {
                _uiState.value = latest.copy(isSubscribing = false)
                _toastEvent.emit(
                    result?.exceptionOrNull()?.toUserMessage(resourceProvider)
                        ?: resourceProvider.getString(AppString.ErrorBizDefault)
                )
            }
        }
    }

    // 从指定一期起播，整个已加载列表作为队列
    fun playAt(index: Int) {
        val current = _uiState.value as? RadioDetailUiState.Success ?: return
        if (current.programs.isEmpty()) return

        val queue = current.programs.map { program ->
            QueueItem(
                songId = program.songId,
                title = program.name,
                artist = listOfNotNull(
                    program.radioName.takeIf { it.isNotBlank() },
                    program.djName.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                coverUrl = program.coverUrl
            )
        }
        playerManager.playQueue(queue, index.coerceIn(queue.indices), playContext = "radio_${current.detail.id}")
    }
}
