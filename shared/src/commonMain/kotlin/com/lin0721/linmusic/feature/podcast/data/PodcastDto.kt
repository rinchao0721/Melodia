package com.lin0721.linmusic.feature.podcast.data

import kotlinx.serialization.Serializable

// 「播客」tab 的电台与节目模型，字段以真机抓取的样本为准。
// 最要紧的一条：节目自身的 id 播不了，可播的是 mainSong.id，两者是不同的数。

// ==================== 分类 ====================

@Serializable
data class PodcastCategoryResponse(
    val code: Int = 0,
    val categories: List<PodcastCategoryDto> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class PodcastCategoryDto(
    val id: Long = 0,
    val name: String = ""
)

// ==================== 电台 ====================

// 精选、猜你喜欢、榜单三个接口的电台结构一致，只是外层包装字段名不同
@Serializable
data class PodcastRadioDto(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    val desc: String? = null,
    val rcmdtext: String? = null,
    val programCount: Int = 0,
    val subCount: Long = 0,
    val category: String? = null,
    val dj: PodcastDjDto? = null
)

@Serializable
data class PodcastDjDto(
    val nickname: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class PodcastRecommendResponse(
    val code: Int = 0,
    val djRadios: List<PodcastRadioDto> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class PodcastPersonalizeResponse(
    val code: Int = 0,
    val data: List<PodcastRadioDto> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class PodcastToplistResponse(
    val code: Int = 0,
    val toplist: List<PodcastRadioDto> = emptyList()
) {
    val isSuccess: Boolean get() = code == 200
}

// 电台详情外层是 data 而非 djRadio
@Serializable
data class PodcastRadioDetailResponse(
    val code: Int = 0,
    val data: PodcastRadioDetailDto? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class PodcastRadioDetailDto(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    val desc: String? = null,
    val category: String? = null,
    val programCount: Int = 0,
    val subCount: Long = 0,
    val dj: PodcastDjDto? = null,
    val subed: Boolean = false
)

// ==================== 节目 ====================

@Serializable
data class PodcastProgramRecommendResponse(
    val code: Int = 0,
    val programs: List<PodcastProgramDto> = emptyList(),
    val more: Boolean = false
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class PodcastProgramListResponse(
    val code: Int = 0,
    val programs: List<PodcastProgramDto> = emptyList(),
    val count: Int = 0,
    val more: Boolean = false
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class PodcastProgramDto(
    val id: Long = 0,
    val name: String = "",
    val coverUrl: String? = null,
    // 毫秒
    val duration: Long = 0,
    // 毫秒时间戳
    val createTime: Long = 0,
    val listenerCount: Long = 0,
    // 期号
    val serialNum: Int = 0,
    val description: String? = null,
    // 真正可播放的那首歌，缺失即为不可播节目
    val mainSong: PodcastMainSongDto? = null,
    val radio: PodcastRadioDto? = null,
    val dj: PodcastDjDto? = null
)

// mainSong 实为完整歌曲结构，此处只取播放所需的 id
@Serializable
data class PodcastMainSongDto(
    val id: Long = 0
)

// ==================== 请求体 ====================

@Serializable
data class PodcastProgramRecommendRequest(
    // 分类 id，为空表示不限分类
    val cateId: Long? = null,
    val limit: Int = 30,
    val offset: Int = 0
)

@Serializable
data class PodcastPersonalizeRequest(
    val limit: Int = 10
)

@Serializable
data class PodcastToplistRequest(
    val limit: Int = 20,
    val offset: Int = 0,
    // 0 新晋，1 热门
    val type: Int = 1
)

@Serializable
data class PodcastRadioDetailRequest(
    val id: Long
)

// 订阅与取消订阅共用同一请求体，靠不同路径区分
@Serializable
data class PodcastSubscribeRequest(
    val id: Long
)

// 未登录调用同样返回 code 200，故不能仅凭它判定订阅成功，调用前必须先校验登录态
@Serializable
data class PodcastSubscribeResponse(
    val code: Int = 0
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class PodcastProgramListRequest(
    val radioId: Long,
    val limit: Int = 30,
    val offset: Int = 0,
    val asc: Boolean = false
)
