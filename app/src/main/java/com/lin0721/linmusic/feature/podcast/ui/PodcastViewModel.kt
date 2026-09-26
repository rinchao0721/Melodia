package com.lin0721.linmusic.feature.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.feature.podcast.data.PodcastRepository
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "PodcastViewModel"

// 「播客」tab ViewModel。与音乐 tab 同样是懒加载：首次切到播客才请求
class PodcastViewModel(
    private val podcastRepository: PodcastRepository,
    private val playerManager: PlayerManager,
    private val userPreferences: UserPreferences,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<PodcastUiState>(PodcastUiState.Loading)
    val uiState: StateFlow<PodcastUiState> = _uiState.asStateFlow()

    fun loadIfNeeded() {
        if (_uiState.value is PodcastUiState.Success) return
        loadFeed()
    }

    fun loadFeed() {
        _uiState.value = PodcastUiState.Loading

        viewModelScope.launch {
            try {
                // 节目是主数据源，它失败才算整页失败；分类与三段电台各自兜底
                val programsDeferred = async { podcastRepository.getRecommendPrograms().first() }
                val categoriesDeferred = async {
                    runCatching { podcastRepository.getCategories().first() }.getOrDefault(Result.success(emptyList()))
                }
                // 该接口未登录也会返回内容，但那是通用推荐而非个性化。
                // 顶着「根据你的收听」展示给未登录用户是假话，故未登录时直接不请求。
                val isLoggedIn = userPreferences.userProfile.first() != null
                val personalizedDeferred = async {
                    if (!isLoggedIn) {
                        Result.success(emptyList())
                    } else {
                        runCatching { podcastRepository.getPersonalizedRadios().first() }
                            .getOrDefault(Result.success(emptyList()))
                    }
                }
                val recommendDeferred = async {
                    runCatching { podcastRepository.getRecommendRadios().first() }.getOrDefault(Result.success(emptyList()))
                }
                val toplistDeferred = async {
                    runCatching { podcastRepository.getToplistRadios().first() }.getOrDefault(Result.success(emptyList()))
                }

                val programsResult = programsDeferred.await()
                val programs = programsResult.getOrNull()

                if (programs == null) {
                    _uiState.value = PodcastUiState.Error(
                        programsResult.exceptionOrNull()?.toUserMessage(resourceProvider)
                            ?: resourceProvider.getString(AppString.ErrorBizDefault)
                    )
                    return@launch
                }

                _uiState.value = PodcastUiState.Success(
                    PodcastFeedData(
                        categories = categoriesDeferred.await().getOrDefault(emptyList()),
                        programs = programs,
                        personalizedRadios = personalizedDeferred.await().getOrDefault(emptyList()),
                        recommendRadios = recommendDeferred.await().getOrDefault(emptyList()),
                        toplistRadios = toplistDeferred.await().getOrDefault(emptyList())
                    )
                )
            } catch (e: Exception) {
                _uiState.value = PodcastUiState.Error(e.toUserMessage(resourceProvider))
            }
        }
    }

    fun selectCategory(categoryId: Long?) {
        val current = _uiState.value as? PodcastUiState.Success ?: return
        if (current.data.selectedCategoryId == categoryId) return

        _uiState.value = PodcastUiState.Success(
            current.data.copy(selectedCategoryId = categoryId, isProgramLoading = true)
        )

        viewModelScope.launch {
            val programs = runCatching { podcastRepository.getRecommendPrograms(categoryId).first() }
                .getOrNull()?.getOrNull()

            val latest = _uiState.value as? PodcastUiState.Success ?: return@launch
            // 期间可能又切了分类，晚到的响应丢弃
            if (latest.data.selectedCategoryId != categoryId) {
                AppLogger.d(TAG, "分类已切换，丢弃 cateId=$categoryId 的返回")
                return@launch
            }
            _uiState.value = PodcastUiState.Success(
                latest.data.copy(programs = programs ?: latest.data.programs, isProgramLoading = false)
            )
        }
    }

    // 从指定节目起播，整段节目列表作为播放队列
    fun playProgramAt(index: Int) {
        val current = _uiState.value as? PodcastUiState.Success ?: return
        playPrograms(current.data.programs, index)
    }

    // 节目的播放 id 是 mainSong，不是节目自身 id
    fun playPrograms(programs: List<PodcastProgram>, index: Int) {
        if (programs.isEmpty()) return
        val queue = programs.map { program ->
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
        playerManager.playQueue(queue, index.coerceIn(queue.indices), playContext = "podcast")
    }
}
