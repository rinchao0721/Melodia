package com.lin0721.linmusic.feature.music.data

import com.lin0721.linmusic.core.model.Track
import kotlinx.serialization.Serializable

// 「音乐」tab 的曲风体系接口模型，字段以真机抓取的样本为准。
// 几处照猜会崩的地方：songNum/artistNum 是 "999999+" 这类字符串；childrenTags 到二级为 null；
// colorDeep/colorShallow 是不带 # 的六位 hex 且部分曲风为空串。

// ==================== 曲风列表 ====================

@Serializable
data class StyleListResponse(
    val code: Int = 0,
    val data: List<StyleTagDto> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class StyleTagDto(
    val tagId: Long = 0,
    val tagName: String = "",
    val enName: String? = null,
    val level: Int = 0,
    // 一级曲风带 1~29 个子标签，二级曲风此项为 null
    val childrenTags: List<StyleTagDto> = emptyList(),
    val colorDeep: String? = null,
    val colorShallow: String? = null
)

// ==================== 我的曲风偏好 ====================

@Serializable
data class StylePreferenceResponse(
    val code: Int = 0,
    val data: StylePreferenceData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class StylePreferenceData(
    // 带占比的偏好项，未登录时为空
    val tagPreferenceVos: List<StylePreferenceItemDto> = emptyList(),
    // 与曲风列表同结构，用于补全偏好项缺失的配色
    val tags: List<StyleTagDto> = emptyList()
)

@Serializable
data class StylePreferenceItemDto(
    val tagId: Long = 0,
    val tagName: String = "",
    // 百分比数值，服务端以字符串下发
    val ratio: String = "",
    val picUrl: String? = null
)

// ==================== 曲风详情 ====================

@Serializable
data class StyleHeadResponse(
    val code: Int = 0,
    val data: StyleHeadDto? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class StyleHeadDto(
    val tagId: Long = 0,
    val name: String = "",
    val enName: String? = null,
    val desc: String? = null,
    // 完整 URL 且无扩展名，与曲风列表里那份拼不出地址的相对路径不是一回事
    val cover: List<String> = emptyList(),
    val colorDeep: String? = null,
    val colorShallow: String? = null,
    val songNum: String? = null,
    val artistNum: String? = null,
    val tagPortrait: StyleTagPortraitDto? = null,
    val favouriteSong: StyleFavouriteSongDto? = null
)

// 服务端下发的曲风画像：模板串里带 ${xxx} 占位符，实际值在 pattern 里按同名 key 取
@Serializable
data class StyleTagPortraitDto(
    val templateContent: String? = null,
    val pattern: Map<String, StylePortraitValueDto> = emptyMap(),
    val dataTip: String? = null
)

@Serializable
data class StylePortraitValueDto(
    val text: String? = null
)

@Serializable
data class StyleFavouriteSongDto(
    val favouriteSong: Track? = null,
    val markHeart: Int = 0
)

// ==================== 曲风内容三段 ====================

@Serializable
data class StylePlaylistResponse(
    val code: Int = 0,
    val data: StylePlaylistData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class StylePlaylistData(
    val playlist: List<StylePlaylistDto> = emptyList(),
    val page: StylePageDto? = null
)

@Serializable
data class StylePlaylistDto(
    val id: Long = 0,
    val name: String = "",
    // 已自带 imageView 查询串，追加 param 前需判断
    val cover: String? = null,
    val songCount: Int = 0,
    val userName: String? = null,
    val playCount: Long = 0
)

@Serializable
data class StyleSongResponse(
    val code: Int = 0,
    val data: StyleSongData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class StyleSongData(
    val songs: List<Track> = emptyList(),
    val page: StylePageDto? = null
)

@Serializable
data class StyleArtistResponse(
    val code: Int = 0,
    val data: StyleArtistData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class StyleArtistData(
    val artists: List<StyleArtistDto> = emptyList(),
    val page: StylePageDto? = null
)

@Serializable
data class StyleArtistDto(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    val img1v1Url: String? = null,
    val albumSize: Int = 0,
    val musicSize: Int = 0
)

// 三个内容接口共用的翻页信息
@Serializable
data class StylePageDto(
    val cursor: Int = 0,
    val size: Int = 0,
    val more: Boolean = false,
    val total: Int = 0
)

// ==================== 请求体 ====================

@Serializable
data class StyleHeadRequest(
    val tagId: Long
)

@Serializable
data class StyleContentRequest(
    val tagId: Long,
    val cursor: Int = 0,
    val size: Int = 20,
    val sort: Int = 0
)
