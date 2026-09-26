package com.lin0721.linmusic.feature.newworks.data

import com.lin0721.linmusic.feature.newworks.domain.NewWorksMv
import com.lin0721.linmusic.feature.newworks.domain.NewWorksReleasePage
import kotlinx.coroutines.flow.Flow

// 关注歌手新作数据仓储（feature/newworks），首页音乐 tab「最新」二级药丸的内容区消费
interface NewWorksRepository {

    // 新 MV，一次性拉取，账号数据量小、服务端本身也不做深翻页
    fun getMvs(): Flow<Result<List<NewWorksMv>>>

    // 新发布（单曲/专辑），before 为翻页游标：取当前已加载项中最早的 publishTime
    fun getReleases(before: Long, firstRequest: Boolean): Flow<Result<NewWorksReleasePage>>
}
