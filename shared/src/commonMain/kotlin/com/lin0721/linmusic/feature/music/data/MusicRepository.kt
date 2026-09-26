package com.lin0721.linmusic.feature.music.data

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.music.domain.MusicStyle
import com.lin0721.linmusic.feature.music.domain.StyleArtistItem
import com.lin0721.linmusic.feature.music.domain.StyleHead
import com.lin0721.linmusic.feature.music.domain.StylePlaylistItem
import com.lin0721.linmusic.feature.music.domain.StylePreference
import kotlinx.coroutines.flow.Flow

// 「音乐」tab 数据仓储
interface MusicRepository {

    // 曲风列表，含二级子标签（公开接口）
    fun getStyleList(): Flow<Result<List<MusicStyle>>>

    // 我的曲风偏好，未登录返回空列表
    fun getStylePreferences(): Flow<Result<List<StylePreference>>>

    // 曲风详情
    fun getStyleHead(tagId: Long): Flow<Result<StyleHead>>

    // 曲风下的热门歌单
    fun getStylePlaylists(tagId: Long): Flow<Result<List<StylePlaylistItem>>>

    // 曲风下的单曲
    fun getStyleSongs(tagId: Long): Flow<Result<List<Track>>>

    // 曲风下的代表歌手
    fun getStyleArtists(tagId: Long): Flow<Result<List<StyleArtistItem>>>
}
