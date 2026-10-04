package com.lin0721.linmusic.feature.listendata.ui

import com.lin0721.linmusic.feature.listendata.domain.ListenReport
import com.lin0721.linmusic.feature.listendata.domain.SongRank
import com.lin0721.linmusic.feature.listendata.domain.TodaySong
import com.lin0721.linmusic.feature.listendata.domain.YearStat

// 四个时间范围。今日与年度的数据源和周月完全不同，页面结构随之变化
enum class ListenTab(val label: String) {
    TODAY("今日"),
    WEEK("本周"),
    MONTH("月度"),
    YEAR("年度")
}

// 当前 Tab 的内容。累计时长跨 Tab 共用，放在外层状态里
sealed interface ListenDataContent {
    data object Loading : ListenDataContent

    data class Today(val songs: List<TodaySong>) : ListenDataContent

    // 报告与排行分属两个接口，只挂一个时另一个为 null，对应区块隐藏
    data class Period(
        val report: ListenReport?,
        val rank: SongRank?
    ) : ListenDataContent

    data class Year(val stats: List<YearStat>) : ListenDataContent

    data class Error(val message: String) : ListenDataContent
}

data class ListenDataUiState(
    val totalSeconds: Long = 0,
    val selectedTab: ListenTab = ListenTab.WEEK,
    val content: ListenDataContent = ListenDataContent.Loading
)
