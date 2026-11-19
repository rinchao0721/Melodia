package com.lin0721.linmusic.feature.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.feature.podcast.data.PodcastProgressPreferences
import com.lin0721.linmusic.feature.podcast.data.PodcastRepository
import com.lin0721.linmusic.feature.podcast.data.PodcastSeenPreferences
import com.lin0721.linmusic.feature.podcast.data.RADIO_PROGRAM_PAGE_SIZE
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.playPodcastPrograms
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_SIZE = RADIO_PROGRAM_PAGE_SIZE

// 电台详情页 ViewModel
class RadioDetailViewModel(
    private val podcastRepository: PodcastRepository,
    private val playbackController: PlaybackController,
    private val userPreferences: UserPreferences,
    private val progressPreferences: PodcastProgressPreferences,
    private val seenPreferences: PodcastSeenPreferences,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<RadioDetailUiState>(RadioDetailUiState.Loading)
    val uiState: StateFlow<RadioDetailUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private var currentRadioId: Long = 0
    private var progress: Map<Long, PodcastProgressEntry> = emptyMap()

    init {
        viewModelScope.launch {
            progressPreferences.entries.collect { entries ->
                progress = entries.associateBy { it.songId }
                _uiState.update { state ->
                    if (state is RadioDetailUiState.Success) state.copy(progress = progress) else state
                }
            }
        }
    }

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
                    hasMore = programs.size >= PAGE_SIZE,
                    progress = progress
                )
                // 进到详情页即视为看过该电台当前的最新一期，订阅列表上的「新」标记随之消失
                programs.maxOfOrNull { it.createTimeMs }?.let { seenPreferences.markSeen(radioId, it) }
            } catch (e: Exception) {
                _uiState.value = RadioDetailUiState.Error(e.toUserMessage(resourceProvider))
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value as? RadioDetailUiState.Success ?: return
        if (!current.hasMore || current.isLoadingMore || current.isReloadingPrograms) return

        _uiState.value = current.copy(isLoadingMore = true, loadMoreError = null)

        viewModelScope.launch {
            val result = runCatching {
                podcastRepository.getRadioPrograms(
                    radioId = currentRadioId,
                    offset = current.programs.size,
                    asc = current.sortAscending
                ).first()
            }.getOrElse { Result.failure(it) }

            val latest = _uiState.value as? RadioDetailUiState.Success ?: return@launch
            // 翻页期间切了排序，晚到的追加结果丢弃
            if (latest.sortAscending != current.sortAscending) return@launch
            val more = result.getOrNull()
            _uiState.value = when {
                // 失败不等于到底：保留 hasMore，交给用户重试
                more == null -> latest.copy(
                    isLoadingMore = false,
                    loadMoreError = result.exceptionOrNull()?.toUserMessage(resourceProvider)
                        ?: resourceProvider.getString(AppString.ErrorBizDefault)
                )
                more.isEmpty() -> latest.copy(isLoadingMore = false, hasMore = false)
                else -> latest.copy(
                    programs = latest.programs + more,
                    isLoadingMore = false,
                    hasMore = more.size >= PAGE_SIZE
                )
            }
        }
    }

    // 切换期号排序，重新从第一页拉取；失败时退回原排序
    fun setSortAscending(ascending: Boolean) {
        val current = _uiState.value as? RadioDetailUiState.Success ?: return
        if (current.sortAscending == ascending || current.isReloadingPrograms) return

        _uiState.value = current.copy(sortAscending = ascending, isReloadingPrograms = true, isLoadingMore = false)

        viewModelScope.launch {
            val section = podcastRepository.getRadioPrograms(currentRadioId, asc = ascending).awaitSection(resourceProvider)
            var failure: String? = null
            _uiState.update { state ->
                if (state !is RadioDetailUiState.Success || state.sortAscending != ascending) return@update state
                when (section) {
                    is PodcastSection.Success -> state.copy(
                        programs = section.data,
                        hasMore = section.data.size >= PAGE_SIZE,
                        loadMoreError = null,
                        isReloadingPrograms = false
                    )
                    is PodcastSection.Error -> {
                        failure = section.message
                        state.copy(sortAscending = !ascending, isReloadingPrograms = false)
                    }
                    PodcastSection.Loading -> state
                }
            }
            failure?.let { _toastEvent.emit(it) }
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

    // 从指定一期起播，整个已加载列表作为队列；该期有未听完的进度则续播
    fun playAt(index: Int) {
        val current = _uiState.value as? RadioDetailUiState.Success ?: return
        playbackController.playPodcastPrograms(current.programs, index, current.progress)
    }

    // 主播放键：有未听完的一期就继续它，否则播当前列表第一期
    fun playPrimary() {
        val current = _uiState.value as? RadioDetailUiState.Success ?: return
        val target: PodcastProgram? = current.resumeProgram
        val index = if (target != null) current.programs.indexOf(target) else 0
        playAt(index.coerceAtLeast(0))
    }
}
