package com.lin0721.linmusic.feature.podcast.ui

import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadioDetail

// 电台详情页 UI 状态
sealed interface RadioDetailUiState {
    data object Loading : RadioDetailUiState

    data class Success(
        val detail: PodcastRadioDetail,
        val programs: List<PodcastProgram>,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false,
        // 翻页失败的提示，非空时停止自动翻页，等用户点重试
        val loadMoreError: String? = null,
        // 订阅请求进行中，按钮置为等待态防止连点
        val isSubscribing: Boolean = false,
        // 为真时按期号从旧到新排列
        val sortAscending: Boolean = false,
        // 切换排序后重新拉取节目列表期间
        val isReloadingPrograms: Boolean = false,
        // 按 songId 索引的本地进度，用于节目行的进度条与已听完标记
        val progress: Map<Long, PodcastProgressEntry> = emptyMap()
    ) : RadioDetailUiState {
        // 「继续播放」的目标：已加载节目里最近收听且未听完的一期
        val resumeProgram: PodcastProgram?
            get() {
                val bySongId = programs.associateBy { it.songId }
                val latest = progress.values
                    .filter { !it.isFinished && it.songId in bySongId }
                    .maxByOrNull { it.updatedAtMs }
                return latest?.let { bySongId[it.songId] }
            }
    }

    data class Error(val message: String) : RadioDetailUiState
}
