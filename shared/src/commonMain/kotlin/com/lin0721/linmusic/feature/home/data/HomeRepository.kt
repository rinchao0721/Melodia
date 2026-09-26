package com.lin0721.linmusic.feature.home.data

import com.lin0721.linmusic.feature.home.domain.HomeBlockPage
import com.lin0721.linmusic.feature.home.domain.ToplistInfo
import kotlinx.coroutines.flow.Flow

// 首页数据仓储（home 业务域）
interface HomeRepository {

    // 获取首页区块页，cursor 为空取第一页
    fun getHomeBlockPage(refresh: Boolean = false, cursor: String = ""): Flow<Result<HomeBlockPage>>

    // 获取个性化推荐歌单（公开接口，无需登录）
    fun getPersonalizedPlaylists(): Flow<Result<PersonalizedData>>

    // 获取排行榜详情（DTO 映射至领域模型）
    fun getToplistDetail(): Flow<Result<List<ToplistInfo>>>

    // 获取每日推荐歌曲（需登录）
    fun getDailyRecommendSongs(): Flow<Result<List<DailySong>>>

    // 获取历史日推可用日期列表（VIP）
    fun getHistoryRecommendDates(): Flow<Result<List<String>>>

    // 获取指定日期的历史日推详情
    fun getHistoryRecommendDetail(date: String): Flow<Result<List<DailySong>>>
}
