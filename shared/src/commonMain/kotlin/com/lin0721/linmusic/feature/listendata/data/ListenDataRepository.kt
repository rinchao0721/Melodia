package com.lin0721.linmusic.feature.listendata.data

import com.lin0721.linmusic.feature.listendata.domain.ListenReport
import com.lin0721.linmusic.feature.listendata.domain.SongRank
import com.lin0721.linmusic.feature.listendata.domain.TodaySong
import com.lin0721.linmusic.feature.listendata.domain.YearStat
import kotlinx.coroutines.flow.Flow

// 听歌数据仓储（feature/listendata）
interface ListenDataRepository {

    // 累计收听秒数
    fun getTotalDuration(): Flow<Result<Long>>

    // 今日播放列表
    fun getTodaySongs(): Flow<Result<List<TodaySong>>>

    // 周/月播放排行，period 取 week 或 month
    fun getSongRank(period: String): Flow<Result<SongRank>>

    // 周/月收听报告
    fun getReport(period: String): Flow<Result<ListenReport>>

    // 历年收听汇总
    fun getYearStats(): Flow<Result<List<YearStat>>>
}
