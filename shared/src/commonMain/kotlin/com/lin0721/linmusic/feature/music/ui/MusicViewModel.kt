package com.lin0721.linmusic.feature.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.AccountEvents
import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.feature.music.data.MusicRepository
import com.lin0721.linmusic.feature.music.domain.StylePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 「音乐」tab 曲风浏览页 ViewModel：曲风列表与偏好只在首次进入时拉一次
class MusicViewModel(
    private val musicRepository: MusicRepository,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<MusicUiState>(MusicUiState.Loading)
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            AccountEvents.switched.collect {
                // 仅在用户已进入过音乐页后才随账户切换刷新，保持首屏懒加载
                if (_uiState.value !is MusicUiState.Loading || loadJob != null) {
                    loadStyles()
                }
            }
        }
    }

    // 刻意不在 init 里拉数据：曲风列表 140KB 起步，进首页就请求会拖慢「全部」的首屏。
    // 首次切到「音乐」tab 时由 UI 触发，已加载过则直接复用。
    fun loadIfNeeded() {
        if (_uiState.value is MusicUiState.Success || loadJob?.isActive == true) return
        loadStyles()
    }

    fun loadStyles() {
        loadJob?.cancel()
        _uiState.value = MusicUiState.Loading

        loadJob = viewModelScope.launch {
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

                _uiState.value = MusicUiState.Success(MusicBrowseData(styles = styles, preferences = preferences))
                if (preferences.isNotEmpty()) loadPreferenceHeads(preferences)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = MusicUiState.Error(e.toUserMessage(resourceProvider))
            }
        }
    }

    // 偏好色块的封面与画像都在各曲风 head 里，逐个并发拉，单个失败只缺那一块封面
    private suspend fun loadPreferenceHeads(preferences: List<StylePreference>) {
        val heads = coroutineScope {
            preferences.map { pref ->
                async { runCatching { musicRepository.getStyleHead(pref.id).first() }.getOrNull()?.getOrNull() }
            }.awaitAll()
        }

        val covers = preferences.zip(heads)
            .mapNotNull { (pref, head) -> head?.coverUrl?.let { pref.id to it } }
            .toMap()
        val topId = preferences.maxByOrNull { it.ratio }?.id
        val portrait = preferences.zip(heads).firstOrNull { (pref, _) -> pref.id == topId }?.second?.portrait

        val latest = _uiState.value as? MusicUiState.Success ?: return
        _uiState.value = MusicUiState.Success(latest.data.copy(preferenceCovers = covers, portrait = portrait))
    }
}
