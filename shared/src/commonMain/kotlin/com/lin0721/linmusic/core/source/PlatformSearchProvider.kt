package com.lin0721.linmusic.core.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// 第三方平台歌曲搜索，仅用于聚合搜索展示，不参与播放链接解析
interface PlatformSearchProvider {

    val platform: MusicPlatform

    // 平台是否可用；插件平台取决于有没有启用的插件
    val available: Flow<Boolean> get() = flowOf(true)

    suspend fun search(
        keyword: String,
        offset: Int = 0,
        limit: Int = 30
    ): List<ExternalTrack>
}
