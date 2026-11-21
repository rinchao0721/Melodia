package com.lin0721.linmusic.feature.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.AccountEvents
import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.feature.music.data.MusicRepository
import com.lin0721.linmusic.feature.music.data.StyleCoverCache
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// 全部曲风封面按需并发上限，避免一次性打满触发服务端限流
private const val MAX_CONCURRENT_STYLE_COVER_REQUESTS = 3

// 「音乐」tab 曲风浏览页 ViewModel：曲风列表与偏好只在首次进入时拉一次
class MusicViewModel(
    private val musicRepository: MusicRepository,
    private val resourceProvider: ResourceProvider,
    private val styleCoverCache: StyleCoverCache = StyleCoverCache()
) : ViewModel() {

    private val _uiState = MutableStateFlow<MusicUiState>(MusicUiState.Loading)
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private val coverSemaphore = Semaphore(MAX_CONCURRENT_STYLE_COVER_REQUESTS)
    private val coverStateLock = Any()
    private val inFlightCoverIds = HashSet<Long>()
    private val failedCoverIds = HashSet<Long>()

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
        synchronized(coverStateLock) {
            failedCoverIds.clear()
        }

        loadJob = viewModelScope.launch {
            try {
                val cachedCoversDeferred = async { styleCoverCache.ensureLoaded() }
                val stylesDeferred = async { musicRepository.getStyleList().first() }
                // 偏好需登录，失败不应拖垮整页，未登录时按空列表处理
                val preferencesDeferred = async {
                    runCatching { musicRepository.getStylePreferences().first() }
                        .getOrDefault(Result.success(emptyList()))
                }

                val stylesResult = stylesDeferred.await()
                val preferences = preferencesDeferred.await().getOrDefault(emptyList())
                cachedCoversDeferred.await()
                val styles = stylesResult.getOrNull()

                if (styles.isNullOrEmpty()) {
                    _uiState.value = MusicUiState.Error(
                        stylesResult.exceptionOrNull()?.toUserMessage(resourceProvider)
                            ?: resourceProvider.getString(AppString.ErrorBizDefault)
                    )
                    return@launch
                }

                _uiState.value = MusicUiState.Success(
                    MusicBrowseData(
                        styles = styles,
                        preferences = preferences,
                        styleCovers = styleCoverCache.freshCovers()
                    )
                )
                if (preferences.isNotEmpty()) loadPreferenceHeads(preferences)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = MusicUiState.Error(e.toUserMessage(resourceProvider))
            }
        }
    }

    // 色块出现在屏幕时按需请求封面：已有缓存、正在请求或本次会话已失败的直接跳过
    fun requestStyleCover(styleId: Long) {
        if (styleId <= 0L) return
        val shouldFetch = synchronized(coverStateLock) {
            val currentCovers = (_uiState.value as? MusicUiState.Success)?.data?.styleCovers
            when {
                styleId in inFlightCoverIds -> false
                styleId in failedCoverIds -> false
                styleCoverCache.getFresh(styleId) != null -> false
                currentCovers?.containsKey(styleId) == true -> false
                else -> {
                    inFlightCoverIds.add(styleId)
                    true
                }
            }
        }
        if (!shouldFetch) return

        viewModelScope.launch {
            try {
                styleCoverCache.ensureLoaded()
                val cachedUrl = styleCoverCache.getFresh(styleId)
                if (cachedUrl != null) {
                    publishCover(styleId, cachedUrl)
                    return@launch
                }

                val head = coverSemaphore.withPermit {
                    try {
                        musicRepository.getStyleHead(styleId).first().getOrNull()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }

                val coverUrl = head?.coverUrl?.takeIf { it.isNotBlank() }
                if (coverUrl != null) {
                    styleCoverCache.put(styleId, coverUrl)
                    publishCover(styleId, coverUrl)
                } else {
                    // 单个失败或无封面只保留纯色块，本次会话内不再反复请求
                    synchronized(coverStateLock) {
                        failedCoverIds.add(styleId)
                    }
                }
            } finally {
                synchronized(coverStateLock) {
                    inFlightCoverIds.remove(styleId)
                }
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
            .mapNotNull { (pref, head) -> head?.coverUrl?.takeIf { it.isNotBlank() }?.let { pref.id to it } }
            .toMap()
        val topId = preferences.maxByOrNull { it.ratio }?.id
        val portrait = preferences.zip(heads).firstOrNull { (pref, _) -> pref.id == topId }?.second?.portrait

        if (covers.isNotEmpty()) {
            styleCoverCache.putAll(covers)
        }

        _uiState.update { state ->
            val latest = state as? MusicUiState.Success ?: return@update state
            MusicUiState.Success(
                latest.data.copy(
                    styleCovers = latest.data.styleCovers + covers,
                    portrait = portrait
                )
            )
        }
    }

    private fun publishCover(styleId: Long, coverUrl: String) {
        _uiState.update { state ->
            val latest = state as? MusicUiState.Success ?: return@update state
            if (latest.data.styleCovers[styleId] == coverUrl) return@update state
            MusicUiState.Success(latest.data.copy(styleCovers = latest.data.styleCovers + (styleId to coverUrl)))
        }
    }
}
