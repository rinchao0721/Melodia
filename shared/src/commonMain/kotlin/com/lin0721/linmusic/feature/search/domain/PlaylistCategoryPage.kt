package com.lin0721.linmusic.feature.search.domain

import com.lin0721.linmusic.core.model.PlaylistDetail

// 精品歌单分类下的分页结果，nextCursor 对应网易接口的 lasttime 游标（非 offset）
data class PlaylistCategoryPage(
    val playlists: List<PlaylistDetail>,
    val hasMore: Boolean,
    val nextCursor: Long,
    val total: Int
)
