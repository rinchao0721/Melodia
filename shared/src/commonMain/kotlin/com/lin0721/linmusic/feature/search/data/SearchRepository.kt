package com.lin0721.linmusic.feature.search.data

import com.lin0721.linmusic.feature.search.domain.HotSearch
import com.lin0721.linmusic.feature.search.domain.PlaylistCategoryPage
import com.lin0721.linmusic.feature.search.domain.PlaylistTag
import com.lin0721.linmusic.feature.search.domain.SearchPageResult
import com.lin0721.linmusic.feature.search.domain.SearchSuggestion
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.flow.Flow

// 搜索数据仓储（search 业务域）
interface SearchRepository {

    // 获取默认搜索词
    fun getDefaultSearchKeyword(): Flow<Result<String>>

    // 分类型云搜索
    fun search(keyword: String, type: SearchType, offset: Int = 0, limit: Int = 30): Flow<Result<SearchPageResult>>

    // 获取热搜榜
    fun getHotSearches(): Flow<Result<List<HotSearch>>>

    // 获取精品歌单标签（含封面图）
    fun getPlaylistTags(): Flow<Result<List<PlaylistTag>>>

    // 获取输入联想词
    fun getSuggestions(keyword: String): Flow<Result<List<SearchSuggestion>>>

    // 按分类分页获取精品歌单列表，cursor 对应网易接口的 lasttime 游标
    fun getPlaylistsByCategory(category: String, cursor: Long = 0, limit: Int = 30): Flow<Result<PlaylistCategoryPage>>
}
