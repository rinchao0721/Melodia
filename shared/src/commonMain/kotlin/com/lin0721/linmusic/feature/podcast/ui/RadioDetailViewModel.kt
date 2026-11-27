package com.lin0721.linmusic.feature.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.download.toToastMessage
import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.feature.playlist.presentation.TrackSelection
import com.lin0721.linmusic.feature.podcast.data.PodcastProgressPreferences
import com.lin0721.linmusic.feature.podcast.data.PodcastRepository
import com.lin0721.linmusic.feature.podcast.data.PodcastSeenPreferences
import com.lin0721.linmusic.feature.podcast.data.RADIO_PROGRAM_PAGE_SIZE
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.playPodcastPrograms
import com.lin0721.linmusic.feature.podcast.domain.toDownloadTrackInfo
import com.lin0721.linmusic.feature.podcast.domain.toQueueItem
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
    private val resourceProvider: ResourceProvider,
    private val songDownloader: SongDownloader? = null,
    private val settingsPreferences: SettingsPreferences? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<RadioDetailUiState>(RadioDetailUiState.Loading)
    val uiState: StateFlow<RadioDetailUiState> = _uiState.asStateFlow()

    private val _selection = MutableStateFlow(TrackSelection())
    val selection: StateFlow<TrackSelection> = _selection.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private var currentRadioId: Long = 0
    private var progress: Map<Long, PodcastProgressEntry> = emptyMap()
    private var defaultDownloadQuality: String = "lossless"
    private var askDownloadQuality: Boolean = false

    init {
        viewModelScope.launch {
            progressPreferences.entries.collect { entries ->
                progress = entries.associateBy { it.songId }
                _uiState.update { state ->
                    if (state is RadioDetailUiState.Success) state.copy(progress = progress) else state
                }
            }
        }
        settingsPreferences?.let { prefs ->
            viewModelScope.launch {
                prefs.downloadQuality.collect { level ->
                    defaultDownloadQuality = level
                    _uiState.update { state ->
                        if (state is RadioDetailUiState.Success) state.copy(defaultDownloadQuality = level) else state
                    }
                }
            }
            viewModelScope.launch {
                prefs.askDownloadQuality.collect { ask ->
                    askDownloadQuality = ask
                    _uiState.update { state ->
                        if (state is RadioDetailUiState.Success) state.copy(askDownloadQuality = ask) else state
                    }
                }
            }
        }
    }

    fun load(radioId: Long) {
        // 同一个电台重复进入不必重拉
        if (currentRadioId == radioId && _uiState.value is RadioDetailUiState.Success) return
        currentRadioId = radioId
        _selection.value = TrackSelection()
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
                    progress = progress,
                    defaultDownloadQuality = defaultDownloadQuality,
                    askDownloadQuality = askDownloadQuality
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

    fun enterSelection(firstSongId: Long? = null) {
        if (_uiState.value !is RadioDetailUiState.Success) return
        _selection.value = _selection.value.enter(firstSongId)
    }

    fun toggleSelected(songId: Long) {
        _selection.update { it.toggle(songId) }
    }

    fun selectRangeTo(targetSongId: Long) {
        val current = _uiState.value as? RadioDetailUiState.Success ?: return
        val orderedIds = current.programs.map { it.songId }
        _selection.update { sel ->
            if (!sel.active) sel.enter(targetSongId) else sel.selectRangeTo(targetSongId, orderedIds)
        }
    }

    fun toggleSelectAll() {
        val current = _uiState.value as? RadioDetailUiState.Success ?: return
        val ids = current.programs.map { it.songId }
        _selection.update { it.toggleAll(ids) }
    }

    fun exitSelection() {
        _selection.value = _selection.value.exit()
    }

    // 选中 1-based 第 startNumber ~ endNumber 期；若超出已加载条数则自动翻页补齐
    fun selectEpisodeRange(startNumber: Int, endNumber: Int) {
        val current = _uiState.value as? RadioDetailUiState.Success ?: return
        if (current.isLoadingRange) return

        val maxLimit = current.detail.programCount.takeIf { it > 0 } ?: Int.MAX_VALUE
        val rawStart = minOf(startNumber, endNumber).coerceAtLeast(1)
        val rawEnd = maxOf(startNumber, endNumber).coerceAtLeast(rawStart)
        if (rawStart > maxLimit) {
            viewModelScope.launch { _toastEvent.emit("起始期数超过总节目数（$maxLimit 期）") }
            return
        }
        val targetCount = minOf(rawEnd, maxLimit)

        if (current.programs.size >= targetCount || !current.hasMore) {
            val ids = current.programs.map { it.songId }
            val startIdx = (rawStart - 1).coerceAtMost((ids.size - 1).coerceAtLeast(0))
            val endIdx = (targetCount - 1).coerceAtMost((ids.size - 1).coerceAtLeast(0))
            _selection.update { it.selectIndexRange(ids, startIdx, endIdx) }
            return
        }

        val snapshotRadioId = currentRadioId
        val snapshotAscending = current.sortAscending
        _uiState.update { state ->
            if (state is RadioDetailUiState.Success) state.copy(isLoadingRange = true) else state
        }

        viewModelScope.launch {
            var accumulated = current.programs
            var hasMore = current.hasMore
            var fetchFailed = false

            while (accumulated.size < targetCount && hasMore) {
                val latest = _uiState.value as? RadioDetailUiState.Success
                if (latest == null || currentRadioId != snapshotRadioId || latest.sortAscending != snapshotAscending) {
                    return@launch
                }
                val result = runCatching {
                    podcastRepository.getRadioPrograms(
                        radioId = snapshotRadioId,
                        offset = accumulated.size,
                        asc = snapshotAscending
                    ).first()
                }.getOrElse { Result.failure(it) }

                val page = result.getOrNull()
                if (page == null) {
                    fetchFailed = true
                    break
                }
                if (page.isEmpty()) {
                    hasMore = false
                    break
                }
                accumulated = accumulated + page
                hasMore = page.size >= PAGE_SIZE
            }

            val latest = _uiState.value as? RadioDetailUiState.Success ?: return@launch
            if (currentRadioId != snapshotRadioId || latest.sortAscending != snapshotAscending) return@launch

            _uiState.value = latest.copy(
                programs = accumulated,
                hasMore = hasMore,
                isLoadingRange = false,
                loadMoreError = null
            )

            val ids = accumulated.map { it.songId }
            if (ids.isNotEmpty() && rawStart <= ids.size) {
                val startIdx = rawStart - 1
                val endIdx = (targetCount - 1).coerceAtMost(ids.lastIndex)
                _selection.update { it.selectIndexRange(ids, startIdx, endIdx) }
                val selectedCount = endIdx - startIdx + 1
                _toastEvent.emit(
                    if (fetchFailed && accumulated.size < targetCount) {
                        "网络波动，已为您选中第 $rawStart ~ ${endIdx + 1} 期（共 $selectedCount 期）"
                    } else {
                        "已选中第 $rawStart ~ ${endIdx + 1} 期（共 $selectedCount 期）"
                    }
                )
            } else if (fetchFailed) {
                _toastEvent.emit("拉取区间节目失败，请稍后重试")
            }
        }
    }

    fun playSelectedNext() {
        val current = _uiState.value as? RadioDetailUiState.Success ?: return
        val selectedIds = _selection.value.selectedIds
        val picked = current.programs.filter { it.songId in selectedIds && it.songId > 0L }
        if (picked.isEmpty()) return
        playbackController.addToPlayNext(picked.map { it.toQueueItem() })
        viewModelScope.launch { _toastEvent.emit("已将 ${picked.size} 期添加到下一首播放") }
        exitSelection()
    }

    fun downloadProgram(program: PodcastProgram, level: String = defaultDownloadQuality) {
        val downloader = songDownloader ?: return
        if (program.songId <= 0L) {
            viewModelScope.launch { _toastEvent.emit("该节目暂无可下载音频") }
            return
        }
        downloader.enqueueSingle(program.toDownloadTrackInfo(), level)
        viewModelScope.launch { _toastEvent.emit("开始下载：${program.name}") }
    }

    fun downloadSelected(level: String = defaultDownloadQuality) {
        val downloader = songDownloader ?: return
        val current = _uiState.value as? RadioDetailUiState.Success ?: return
        val selectedIds = _selection.value.selectedIds
        val tracks = current.programs
            .filter { it.songId in selectedIds && it.songId > 0L }
            .map { it.toDownloadTrackInfo() }
        if (tracks.isEmpty()) {
            viewModelScope.launch { _toastEvent.emit("请先选择要下载的节目") }
            return
        }
        viewModelScope.launch {
            val result = downloader.enqueueBatch(
                tracks = tracks,
                level = level,
                batchTag = "podcast_$currentRadioId",
                batchLabel = current.detail.name
            )
            _toastEvent.emit(result.toToastMessage(unit = "期", noun = "节目"))
            exitSelection()
        }
    }
}
