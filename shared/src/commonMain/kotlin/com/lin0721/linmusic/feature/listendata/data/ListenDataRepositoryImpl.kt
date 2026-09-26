package com.lin0721.linmusic.feature.listendata.data

import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.feature.listendata.domain.ListenReport
import com.lin0721.linmusic.feature.listendata.domain.SongRank
import com.lin0721.linmusic.feature.listendata.domain.TodaySong
import com.lin0721.linmusic.feature.listendata.domain.YearStat
import com.lin0721.linmusic.feature.listendata.domain.toDomain
import kotlinx.coroutines.flow.Flow

const val PERIOD_WEEK = "week"
const val PERIOD_MONTH = "month"

class ListenDataRepositoryImpl(
    private val apiService: ListenDataApi
) : ListenDataRepository {

    override fun getTotalDuration(): Flow<Result<Long>> = apiFlow(
        request = { apiService.getTotalDuration() },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { it.data!!.totalDuration }
    )

    override fun getTodaySongs(): Flow<Result<List<TodaySong>>> = apiFlow(
        request = { apiService.getTodayRank() },
        // 当天无收听时 data 是空对象，仍算成功，交给 UI 走空态
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.data?.toDomain().orEmpty() }
    )

    override fun getSongRank(period: String): Flow<Result<SongRank>> = apiFlow(
        request = { apiService.getSongPlayRank(ListenPeriodRequest(period)) },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { it.data!!.toDomain() }
    )

    override fun getReport(period: String): Flow<Result<ListenReport>> = apiFlow(
        request = { apiService.getReport(ListenPeriodRequest(period)) },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { it.data!!.toDomain(isWeek = period == PERIOD_WEEK) }
    )

    override fun getYearStats(): Flow<Result<List<YearStat>>> = apiFlow(
        request = { apiService.getYearReport() },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        // 服务端按年份倒序返回，保持原序展示
        transform = { it.data!!.toDomain() }
    )
}
