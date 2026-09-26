package com.lin0721.linmusic.feature.search.domain

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track

// 分类型搜索结果条目，按 SearchType 承载对应的共享领域模型，不新建重复模型
sealed interface SearchResultItem {
    data class SongItem(val track: Track) : SearchResultItem
    data class AlbumItem(val album: Album) : SearchResultItem
    data class ArtistItem(val artist: Artist) : SearchResultItem
    data class PlaylistItem(val playlist: PlaylistDetail) : SearchResultItem
}

// 单个 SearchType 的分页搜索结果
data class SearchPageResult(
    val items: List<SearchResultItem>,
    val totalCount: Int,
    val hasMore: Boolean,
    // 本页从接口实际拉取的原始条数（内容过滤前），分页 offset 必须按此推进，
    // 若按过滤后的 items.size 推进，屏蔽歌手造成的缺口会让下一页请求重复拉取已消费的原始数据
    val rawFetchedCount: Int
)
