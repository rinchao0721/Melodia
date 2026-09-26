package com.lin0721.linmusic.feature.podcast.data

import com.lin0721.linmusic.feature.podcast.domain.PodcastCategory
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadioDetail
import kotlinx.coroutines.flow.Flow

// 「播客」tab 数据仓储
interface PodcastRepository {

    // 电台分类
    fun getCategories(): Flow<Result<List<PodcastCategory>>>

    // 推荐节目，cateId 为空表示不限分类
    fun getRecommendPrograms(cateId: Long? = null): Flow<Result<List<PodcastProgram>>>

    // 猜你喜欢的电台，未登录返回空列表
    fun getPersonalizedRadios(): Flow<Result<List<PodcastRadio>>>

    // 精选电台
    fun getRecommendRadios(): Flow<Result<List<PodcastRadio>>>

    // 热门电台榜
    fun getToplistRadios(): Flow<Result<List<PodcastRadio>>>

    // 电台详情
    fun getRadioDetail(radioId: Long): Flow<Result<PodcastRadioDetail>>

    // 订阅或取消订阅电台。调用方需自行确保已登录
    fun setRadioSubscribed(radioId: Long, subscribe: Boolean): Flow<Result<Unit>>

    // 电台下的节目列表
    fun getRadioPrograms(radioId: Long, offset: Int = 0): Flow<Result<List<PodcastProgram>>>
}
