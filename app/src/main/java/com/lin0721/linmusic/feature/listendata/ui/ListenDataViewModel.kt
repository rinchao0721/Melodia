package com.lin0721.linmusic.feature.listendata.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.songquality.SongQualityCache
import com.lin0721.linmusic.feature.listendata.data.ListenDataRepository
import com.lin0721.linmusic.feature.listendata.data.PERIOD_MONTH
import com.lin0721.linmusic.feature.listendata.data.PERIOD_WEEK
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ListenDataViewModel"

class ListenDataViewModel(
    private val repository: ListenDataRepository,
    val playerManager: PlaybackController,
    private val resourceProvider: ResourceProvider,
    private val songQualityCache: SongQualityCache? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListenDataUiState())
    val uiState: StateFlow<ListenDataUiState> = _uiState.asStateFlow()

    // 已加载过的 Tab 直接复用，来回切换不重复打接口
    private val cache = mutableMapOf<ListenTab, ListenDataContent>()

    init {
        loadTotalDuration()
        selectTab(ListenTab.WEEK)
    }

    fun selectTab(tab: ListenTab) {
        val cached = cache[tab]
        _uiState.update { it.copy(selectedTab = tab, content = cached ?: ListenDataContent.Loading) }
        if (cached == null) loadTab(tab)
    }

    fun retry() {
        val tab = _uiState.value.selectedTab
        cache.remove(tab)
        _uiState.update { it.copy(content = ListenDataContent.Loading) }
        if (_uiState.value.totalSeconds == 0L) loadTotalDuration()
        loadTab(tab)
    }

    // 以当前 Tab 的歌曲列表为队列，从点击项开始播
    fun playSongAt(index: Int) {
        val queue = when (val content = _uiState.value.content) {
            is ListenDataContent.Today -> content.songs.map {
                QueueItem(it.id, it.name, it.artistName, it.coverUrl)
            }
            is ListenDataContent.Period -> content.rank?.songs.orEmpty().map {
                QueueItem(it.id, it.name, it.artistName, it.coverUrl)
            }
            else -> emptyList()
        }
        if (index !in queue.indices) return
        playerManager.playQueue(queue, index, "听歌数据")
    }

    // 亮点卡点播。报告块不带歌手信息，能在排行里找到就走排行那条完整数据
    fun playHighlight(songId: Long) {
        val content = _uiState.value.content as? ListenDataContent.Period ?: return
        val index = content.rank?.songs.orEmpty().indexOfFirst { it.id == songId }
        if (index != null && index >= 0) {
            playSongAt(index)
            return
        }
        val highlight = content.report?.highlights?.firstOrNull { it.songId == songId } ?: return
        playerManager.playQueue(
            listOf(QueueItem(highlight.songId, highlight.songName, "", highlight.coverUrl)),
            0,
            "听歌数据"
        )
    }

    // 累计时长独立于 Tab，失败也不阻断页面主体
    private fun loadTotalDuration() {
        viewModelScope.launch {
            runCatching { repository.getTotalDuration().first() }
                .getOrNull()
                ?.onSuccess { seconds -> _uiState.update { it.copy(totalSeconds = seconds) } }
                ?.onFailure { AppLogger.w(TAG, "累计收听时长加载失败", it) }
        }
    }

    private fun loadTab(tab: ListenTab) {
        viewModelScope.launch {
            val content = when (tab) {
                ListenTab.TODAY -> loadToday()
                ListenTab.WEEK -> loadPeriod(PERIOD_WEEK)
                ListenTab.MONTH -> loadPeriod(PERIOD_MONTH)
                ListenTab.YEAR -> loadYear()
            }
            // 失败态不入缓存，下次切回来重新请求
            if (content !is ListenDataContent.Error) cache[tab] = content
            // 期间可能已切走，只在仍停留于该 Tab 时落状态
            _uiState.update { if (it.selectedTab == tab) it.copy(content = content) else it }
        }
    }

    private suspend fun loadToday(): ListenDataContent =
        fetch("今日播放") { repository.getTodaySongs().first() }
            .fold(
                onSuccess = { songs ->
                    songQualityCache?.prefetchIds(songs.map { it.id })
                    ListenDataContent.Today(songs)
                },
                onFailure = { ListenDataContent.Error(it.toUserMessage(resourceProvider)) }
            )

    private suspend fun loadPeriod(period: String): ListenDataContent {
        val reportDeferred = viewModelScope.async { fetch("收听报告") { repository.getReport(period).first() } }
        val rankDeferred = viewModelScope.async { fetch("播放排行") { repository.getSongRank(period).first() } }

        val report = reportDeferred.await()
        val rank = rankDeferred.await()

        // 两个接口都挂了才算失败，只挂一个时另一半照常展示
        if (report.isFailure && rank.isFailure) {
            val cause = report.exceptionOrNull() ?: rank.exceptionOrNull()
            return ListenDataContent.Error(
                cause?.toUserMessage(resourceProvider) ?: "加载失败，请稍后重试"
            )
        }
        val rankData = rank.getOrNull()
        rankData?.songs?.takeIf { it.isNotEmpty() }?.let { songs ->
            songQualityCache?.prefetchIds(songs.map { it.id })
        }
        return ListenDataContent.Period(
            report = report.getOrNull(),
            rank = rankData
        )
    }

    private suspend fun loadYear(): ListenDataContent =
        fetch("历年收听") { repository.getYearStats().first() }
            .fold(
                onSuccess = { ListenDataContent.Year(it) },
                onFailure = { ListenDataContent.Error(it.toUserMessage(resourceProvider)) }
            )

    // 单个请求的异常兜底，避免并行分支里任一抛出后另一半结果一起丢失
    private suspend fun <T> fetch(label: String, block: suspend () -> Result<T>): Result<T> =
        runCatching { block() }
            .getOrElse { e ->
                AppLogger.w(TAG, "$label 加载异常", e)
                Result.failure(e)
            }
            .onFailure { AppLogger.w(TAG, "$label 加载失败", it) }
}
