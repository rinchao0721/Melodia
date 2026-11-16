package com.lin0721.linmusic.feature.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.feature.podcast.data.PodcastProgressPreferences
import com.lin0721.linmusic.feature.podcast.data.PodcastRepository
import com.lin0721.linmusic.feature.podcast.data.PodcastSeenPreferences
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.podcast.domain.PodcastSubscriptionUpdates
import com.lin0721.linmusic.feature.podcast.domain.playPodcastPrograms
import com.lin0721.linmusic.feature.podcast.domain.resumePodcast
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MAX_CONTINUE_LISTENING = 8

// 「播客」tab ViewModel。首次切到播客才加载，之后保留已加载内容
class PodcastHomeViewModel(
    private val podcastRepository: PodcastRepository,
    private val playbackController: PlaybackController,
    private val userPreferences: UserPreferences,
    private val progressPreferences: PodcastProgressPreferences,
    private val seenPreferences: PodcastSeenPreferences,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _state = MutableStateFlow(PodcastHomeState())
    val state: StateFlow<PodcastHomeState> = _state.asStateFlow()

    private var started = false
    private var subscribedJob: Job? = null
    private var categoryRadiosJob: Job? = null

    fun loadIfNeeded() {
        if (started) return
        started = true
        observeProgress()
        observeSeen()
        observeLogin()
        loadPublicSections()
    }

    // 重新拉取全部区块；已有内容在刷新失败时保留
    fun refresh() {
        if (!started) {
            loadIfNeeded()
            return
        }
        loadPublicSections()
        if (_state.value.isLoggedIn) loadSubscribed()
        (_state.value.filter as? PodcastFilter.Category)?.let { loadCategoryRadios(it.id) }
    }

    fun selectFilter(filter: PodcastFilter) {
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter) }
        if (filter is PodcastFilter.Category) loadCategoryRadios(filter.id)
    }

    fun retryPicks() = loadPicks()

    fun retryCategoryGroups() = loadCategoryGroups()

    fun retryToplistRadios() = loadToplistRadios()

    fun retrySubscribed() {
        if (_state.value.isLoggedIn) loadSubscribed()
    }

    fun retryCategoryRadios() {
        (_state.value.filter as? PodcastFilter.Category)?.let { loadCategoryRadios(it.id) }
    }

    fun playPicks(index: Int) = playPrograms(_state.value.picks.itemsOrEmpty(), index)

    // 从本地进度续播
    fun resume(entry: PodcastProgressEntry) = playbackController.resumePodcast(entry)

    private fun playPrograms(programs: List<PodcastProgram>, index: Int) {
        playbackController.playPodcastPrograms(programs, index, _state.value.progress)
    }

    private fun observeProgress() {
        viewModelScope.launch {
            progressPreferences.entries.collect { entries ->
                _state.update { state ->
                    state.copy(
                        continueListening = entries.filterNot { it.isFinished }.take(MAX_CONTINUE_LISTENING),
                        progress = entries.associateBy { it.songId }
                    )
                }
            }
        }
    }

    // 已见时间变化（如在详情页看过更新）时重算「新」标记
    private fun observeSeen() {
        viewModelScope.launch {
            seenPreferences.seen.collect { seen ->
                _state.update { state ->
                    val radios = (state.subscribed as? PodcastSection.Success)?.data ?: return@update state
                    state.copy(updatedRadioIds = PodcastSubscriptionUpdates.check(radios, seen).updatedRadioIds)
                }
            }
        }
    }

    // 登录态或活跃账户变化时订阅区块随之加载或清空
    private fun observeLogin() {
        viewModelScope.launch {
            userPreferences.userProfile.map { it?.uid }.distinctUntilChanged().collect { uid ->
                val loggedIn = uid != null
                _state.update { it.copy(isLoggedIn = loggedIn) }
                if (loggedIn) {
                    loadSubscribed()
                } else {
                    subscribedJob?.cancel()
                    _state.update {
                        it.copy(subscribed = PodcastSection.Success(emptyList()), updatedRadioIds = emptySet())
                    }
                }
            }
        }
    }

    private fun loadPublicSections() {
        loadCategories()
        loadPicks()
        loadCategoryGroups()
        loadToplistRadios()
    }

    // 分类只是筛选胶囊，失败了不影响页面其它部分
    private fun loadCategories() {
        viewModelScope.launch {
            val section = podcastRepository.getCategories().awaitSection(resourceProvider)
            if (section is PodcastSection.Success) {
                _state.update { it.copy(categories = section.data) }
            }
        }
    }

    private fun loadPicks() = reload(
        read = { it.picks },
        write = { state, section -> state.copy(picks = section) },
        fetch = { podcastRepository.getRecommendPrograms(null) }
    )

    private fun loadCategoryGroups() = reload(
        read = { it.categoryGroups },
        write = { state, section -> state.copy(categoryGroups = section) },
        fetch = { podcastRepository.getCategoryGroups() }
    )

    private fun loadToplistRadios() = reload(
        read = { it.toplistRadios },
        write = { state, section -> state.copy(toplistRadios = section) },
        fetch = { podcastRepository.getToplistRadios() }
    )

    private fun <T> reload(
        read: (PodcastHomeState) -> PodcastSection<T>,
        write: (PodcastHomeState, PodcastSection<T>) -> PodcastHomeState,
        fetch: () -> Flow<Result<T>>
    ) {
        viewModelScope.launch {
            _state.update { if (read(it) is PodcastSection.Success) it else write(it, PodcastSection.Loading) }
            val result = fetch().awaitSection(resourceProvider)
            _state.update { write(it, settleSection(read(it), result)) }
        }
    }

    private fun loadSubscribed() {
        subscribedJob?.cancel()
        subscribedJob = viewModelScope.launch {
            _state.update {
                if (it.subscribed is PodcastSection.Success) it else it.copy(subscribed = PodcastSection.Loading)
            }
            when (val section = podcastRepository.getSubscribedRadios().awaitSection(resourceProvider)) {
                is PodcastSection.Success -> applySubscribed(section.data.items)
                is PodcastSection.Error -> _state.update {
                    it.copy(subscribed = settleSection(it.subscribed, section))
                }
                PodcastSection.Loading -> Unit
            }
        }
    }

    private suspend fun applySubscribed(radios: List<PodcastRadio>) {
        val updated = seenPreferences.evaluate(radios)
        _state.update { it.copy(subscribed = PodcastSection.Success(radios), updatedRadioIds = updated) }
    }

    private fun loadCategoryRadios(categoryId: Long) {
        categoryRadiosJob?.cancel()
        categoryRadiosJob = viewModelScope.launch {
            _state.update { it.copy(categoryRadios = PodcastSection.Loading) }
            val result = when (val section = podcastRepository.getCategoryHotRadios(categoryId).awaitSection(resourceProvider)) {
                is PodcastSection.Success -> PodcastSection.Success(section.data.items)
                is PodcastSection.Error -> section
                PodcastSection.Loading -> PodcastSection.Loading
            }
            _state.update { state ->
                // 期间又切了筛选，晚到的响应丢弃
                if ((state.filter as? PodcastFilter.Category)?.id != categoryId) state
                else state.copy(categoryRadios = result)
            }
        }
    }
}
