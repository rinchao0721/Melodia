package com.lin0721.linmusic.feature.home.data

import kotlinx.serialization.Serializable

// 首页区块页 /eapi/homepage/block/page 的响应模型。
// 建模依据是真机抓取的样本，几处与直觉不符、照猜必崩的地方：
// resourceId 是字符串且存在 null；shelf 标题在 mainTitle 与 subTitle 两处都可能出现；
// 标签有 labelTexts 数组与 labelText 单对象两种形态；extInfo 的字段随 blockCode 变化。

@Serializable
data class HomeBlockPageRequest(
    // 下拉刷新时置 true，服务端会换一批内容
    val refresh: Boolean = false,
    // 首页传空串，翻页时原样回传上一次响应里的 cursor
    val cursor: String = ""
)

@Serializable
data class HomeBlockPageResponse(
    val code: Int = 0,
    val data: HomeBlockPageData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class HomeBlockPageData(
    // 翻页游标，原样回传给下一次请求；翻到底时为 null
    val cursor: String? = null,
    val hasMore: Boolean = false,
    val blocks: List<HomeBlockDto> = emptyList()
)

@Serializable
data class HomeBlockDto(
    val blockCode: String = "",
    val showType: String = "",
    val uiElement: HomeUiElementDto? = null,
    val creatives: List<HomeCreativeDto> = emptyList(),
    val sort: Int = 0
)

// 一个 creative 装若干张卡片，卡片本体是 resources 而非 creative 自身
@Serializable
data class HomeCreativeDto(
    val creativeType: String = "",
    val resources: List<HomeResourceDto> = emptyList()
)

@Serializable
data class HomeResourceDto(
    val resourceType: String = "",
    val resourceId: String? = null,
    val uiElement: HomeUiElementDto? = null,
    val action: String? = null,
    val actionType: String? = null,
    // 播客类资源把整个节目对象塞在这里，可播的 mainSong 就在其中，无需再查节目详情
    val resourceExtInfo: HomeResourceExtInfoDto? = null
)

@Serializable
data class HomeResourceExtInfoDto(
    val djProgram: HomeDjProgramDto? = null
)

@Serializable
data class HomeDjProgramDto(
    val id: Long = 0,
    val name: String = "",
    val coverUrl: String? = null,
    val duration: Long = 0,
    val mainSong: HomeDjMainSongDto? = null,
    val radio: HomeDjRadioDto? = null,
    val dj: HomeDjUserDto? = null
)

@Serializable
data class HomeDjMainSongDto(
    val id: Long = 0
)

@Serializable
data class HomeDjRadioDto(
    val id: Long = 0,
    val name: String = ""
)

@Serializable
data class HomeDjUserDto(
    val nickname: String? = null
)

// block 与 resource 共用同一套 uiElement，字段按需出现
@Serializable
data class HomeUiElementDto(
    val mainTitle: HomeTitleDto? = null,
    val subTitle: HomeTitleDto? = null,
    val description: String? = null,
    val image: HomeImageDto? = null,
    val labelTexts: List<String> = emptyList(),
    val labelText: HomeLabelDto? = null
)

@Serializable
data class HomeTitleDto(
    val title: String? = null
)

@Serializable
data class HomeImageDto(
    val imageUrl: String? = null
)

@Serializable
data class HomeLabelDto(
    val text: String? = null
)

