package com.lin0721.linmusic.feature.cloud.ui

import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.platform.repository.PlatformCloudRouter
import com.lin0721.linmusic.core.platform.repository.UnifiedCloudQuota
import com.lin0721.linmusic.core.platform.repository.UnifiedCloudTrack
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface PlatformCloudUiState {
    data object Idle : PlatformCloudUiState
    data object Loading : PlatformCloudUiState
    data class Success(
        val platform: MusicPlatform,
        val tracks: List<UnifiedCloudTrack>,
        val quota: UnifiedCloudQuota,
        val page: Int = 1,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        val isDeleting: Boolean = false
    ) : PlatformCloudUiState

    data class Error(val message: String, val platform: MusicPlatform) : PlatformCloudUiState
}

// 跨平台云盘共享状态管理器（供 Android 与 Desktop 云盘页按平台加载、翻页、删除与生成播放队列）
class PlatformCloudStateHolder(
    private val cloudRouter: PlatformCloudRouter,
    private val settingsPreferences: SettingsPreferences,
    private val resourceProvider: ResourceProvider,
    private val scope: CoroutineScope,
    private val pageSize: Int = 30
) {
    private val _activePlatform = MutableStateFlow(MusicPlatform.NETEASE)
    val activePlatform: StateFlow<MusicPlatform> = _activePlatform.asStateFlow()

    private val _uiState = MutableStateFlow<PlatformCloudUiState>(PlatformCloudUiState.Idle)
    val uiState: StateFlow<PlatformCloudUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        scope.launch {
            settingsPreferences.kugouPlatformEnabled
                .distinctUntilChanged()
                .collect { enabled ->
                    if (!enabled && _activePlatform.value == MusicPlatform.KUGOU) {
                        _activePlatform.value = MusicPlatform.NETEASE
                        load(refresh = true)
                    }
                }
        }
    }

    fun selectPlatform(platform: MusicPlatform) {
        scope.launch {
            val resolved = cloudRouter.resolveCloudPlatform(platform)
            if (platform == MusicPlatform.KUGOU && resolved != MusicPlatform.KUGOU) {
                _toastEvent.emit("酷狗音乐平台未启用")
                if (_activePlatform.value != MusicPlatform.NETEASE) {
                    _activePlatform.value = MusicPlatform.NETEASE
                    load(refresh = true)
                }
                return@launch
            }
            if (_activePlatform.value != resolved || _uiState.value is PlatformCloudUiState.Idle) {
                _activePlatform.value = resolved
                load(refresh = true)
            }
        }
    }

    fun load(refresh: Boolean = false) {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        loadJob = scope.launch {
            val requested = _activePlatform.value
            val target = cloudRouter.resolveCloudPlatform(requested)
            if (_activePlatform.value != target) {
                _activePlatform.value = target
            }
            val shouldShowLoading = refresh || _uiState.value !is PlatformCloudUiState.Success
            if (shouldShowLoading) {
                _uiState.value = PlatformCloudUiState.Loading
            }
            cloudRouter.getCloudTracks(target, page = 1, pageSize = pageSize).first().fold(
                onSuccess = { page ->
                    _uiState.value = PlatformCloudUiState.Success(
                        platform = page.platform,
                        tracks = page.tracks,
                        quota = page.quota,
                        page = page.page,
                        hasMore = page.hasMore
                    )
                },
                onFailure = { err ->
                    _uiState.value = PlatformCloudUiState.Error(
                        message = err.toUserMessage(resourceProvider),
                        platform = target
                    )
                }
            )
        }
    }

    fun loadMore() {
        val current = _uiState.value as? PlatformCloudUiState.Success ?: return
        if (!current.hasMore || current.isLoadingMore || current.isDeleting) return

        _uiState.value = current.copy(isLoadingMore = true)
        loadMoreJob?.cancel()
        loadMoreJob = scope.launch {
            val target = cloudRouter.resolveCloudPlatform(current.platform)
            if (target != current.platform) {
                _activePlatform.value = target
                load(refresh = true)
                return@launch
            }
            val nextPage = current.page + 1
            cloudRouter.getCloudTracks(target, page = nextPage, pageSize = pageSize).first().fold(
                onSuccess = { page ->
                    val latest = _uiState.value as? PlatformCloudUiState.Success ?: return@fold
                    if (latest.platform != target) return@fold
                    val existingKeys = latest.tracks.mapTo(HashSet()) { it.track.id.canonical }
                    val merged = latest.tracks + page.tracks.filter { it.track.id.canonical !in existingKeys }
                    _uiState.value = latest.copy(
                        tracks = merged,
                        quota = page.quota,
                        page = page.page,
                        hasMore = page.hasMore,
                        isLoadingMore = false
                    )
                },
                onFailure = { err ->
                    val latest = _uiState.value as? PlatformCloudUiState.Success ?: return@fold
                    _uiState.value = latest.copy(isLoadingMore = false)
                    _toastEvent.emit(err.toUserMessage(resourceProvider))
                }
            )
        }
    }

    fun delete(tracks: List<UnifiedCloudTrack>, onComplete: (Boolean) -> Unit = {}) {
        if (tracks.isEmpty()) {
            onComplete(true)
            return
        }
        val current = _uiState.value as? PlatformCloudUiState.Success ?: return
        if (current.isDeleting) return
        _uiState.value = current.copy(isDeleting = true)

        scope.launch {
            cloudRouter.deleteCloudTracks(current.platform, tracks).first().fold(
                onSuccess = {
                    val deleteKeys = tracks.mapTo(HashSet()) { it.deleteKey }
                    val canonicalIds = tracks.mapTo(HashSet()) { it.track.id.canonical }
                    val removedBytes = tracks.sumOf { it.fileSizeBytes.coerceAtLeast(0L) }
                    val latest = _uiState.value as? PlatformCloudUiState.Success
                    if (latest != null && latest.platform == current.platform) {
                        val remaining = latest.tracks.filter {
                            it.deleteKey !in deleteKeys && it.track.id.canonical !in canonicalIds
                        }
                        val updatedQuota = latest.quota.copy(
                            usedBytes = (latest.quota.usedBytes - removedBytes).coerceAtLeast(0L),
                            totalCount = (latest.quota.totalCount - tracks.size).coerceAtLeast(remaining.size)
                        )
                        _uiState.value = latest.copy(
                            tracks = remaining,
                            quota = updatedQuota,
                            isDeleting = false
                        )
                    }
                    _toastEvent.emit("已从云盘移除 ${tracks.size} 首歌曲")
                    onComplete(true)
                },
                onFailure = { err ->
                    val latest = _uiState.value as? PlatformCloudUiState.Success
                    if (latest != null) {
                        _uiState.value = latest.copy(isDeleting = false)
                    }
                    _toastEvent.emit(err.toUserMessage(resourceProvider))
                    onComplete(false)
                }
            )
        }
    }

    fun toQueueItems(
        tracks: List<UnifiedCloudTrack> = (_uiState.value as? PlatformCloudUiState.Success)?.tracks.orEmpty()
    ): List<QueueItem> = tracks.map { QueueItem.fromUnified(it.track) }
}
