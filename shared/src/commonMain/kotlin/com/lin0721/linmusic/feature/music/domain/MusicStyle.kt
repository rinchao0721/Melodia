package com.lin0721.linmusic.feature.music.domain

import com.lin0721.linmusic.core.model.Track

// 一级曲风，children 为二级子标签。二级标签服务端不给配色，UI 用描边样式呈现
data class MusicStyle(
    val id: Long,
    val name: String,
    val enName: String,
    // 六位 hex，不带 #。服务端有五个曲风下发空串，此处统一为 null 交由 UI 兜底
    val colorHex: String?,
    val children: List<MusicStyle> = emptyList()
)

// 曲风画像。服务端给模板串与变量表，客户端替换后即为成稿，不自行拼文案
data class StylePortrait(
    val content: String,
    val dataTip: String
)

// 我的偏好项，ratio 为百分比整数
data class StylePreference(
    val id: Long,
    val name: String,
    val ratio: Int,
    val colorHex: String?
)

// 曲风详情头部
data class StyleHead(
    val id: Long,
    val name: String,
    val enName: String,
    // 曲风简介，服务端写的运营文案，比封顶的数量统计有信息量
    val desc: String,
    val coverUrl: String?,
    val colorHex: String?,
    // 热门曲风一律返回 999999+ / 1000+ 这类封顶值，展示前需判断是否为真实数字
    val songNum: String,
    val artistNum: String,
    // 该曲风下用户最爱的一首，未登录时为 null
    val favouriteSong: Track?,
    val portrait: StylePortrait?
)

// 曲风下的歌单
data class StylePlaylistItem(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val playCount: Long
)

// 曲风下的歌手
data class StyleArtistItem(
    val id: Long,
    val name: String,
    val picUrl: String,
    val musicSize: Int
)

// 一个曲风页的完整内容
data class StyleContent(
    val head: StyleHead?,
    val playlists: List<StylePlaylistItem>,
    val songs: List<Track>,
    val artists: List<StyleArtistItem>
)
