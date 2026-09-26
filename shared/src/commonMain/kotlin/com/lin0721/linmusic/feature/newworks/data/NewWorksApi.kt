package com.lin0721.linmusic.feature.newworks.data

import com.lin0721.linmusic.core.model.Track
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

// 关注歌手新作（新 MV / 新发布）相关的网易云 Retrofit 接口定义。
interface NewWorksApi {

    // 关注歌手的新 MV，独立轻量接口——真机实测合并 feed 里几乎不会带出 MV，只能从这里拿
    @POST("/weapi/sub/artist/new/works/mv/list")
    suspend fun getNewMvs(
        @Body body: NewWorksMvRequest = NewWorksMvRequest()
    ): NewWorksMvResponse

    // 关注歌手的新发布（单曲/专辑），按 startTimestamp 向历史翻页。
    // 真机实测：album 类型区块会把该专辑全部曲目内联，单个区块可达 157 首，
    // limit 只控制区块数不控制总曲目数，故 limit 保持参考实现的默认值不放大
    @POST("/eapi/sub/artist/new/works/song-mv/list/v2")
    suspend fun getNewReleases(
        @Body body: NewWorksReleaseRequest
    ): NewWorksReleaseResponse
}

// ======================= 新 MV =======================

@Serializable
data class NewWorksMvRequest(
    val limit: Int = 20,
    val startTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class NewWorksMvResponse(
    val code: Int = 0,
    val data: NewWorksMvData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class NewWorksMvData(
    val hasMore: Boolean = false,
    val newWorks: List<NewWorksMvDto> = emptyList()
)

@Serializable
data class NewWorksMvDto(
    val mvId: Long = 0,
    val mvName: String = "",
    val mvCoverUrl: String = "",
    val duration: Long = 0,
    val playCount: Long = 0,
    val publishTime: Long = 0,
    val artistName: String = ""
)

// ======================= 新发布 =======================

@Serializable
data class NewWorksReleaseRequest(
    val startTimestamp: Long = System.currentTimeMillis(),
    val sourceType: Int = 1,
    val limit: Int = 10,
    val firstRequest: Boolean = true
)

@Serializable
data class NewWorksReleaseResponse(
    val code: Int = 0,
    val data: NewWorksReleaseData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class NewWorksReleaseData(
    val hasMore: Boolean = false,
    val newWorks: List<NewWorksReleaseItem> = emptyList()
)

@Serializable
data class NewWorksReleaseItem(
    val publishTime: Long = 0,
    val info: NewWorksReleaseInfo = NewWorksReleaseInfo()
)

@Serializable
data class NewWorksReleaseInfo(
    val blockTitle: NewWorksBlockTitle = NewWorksBlockTitle(),
    val blockType: String = "",
    // 专辑区块会内联全部曲目，这里只取 firstOrNull() 做代表封面/歌手，绝不整表渲染
    val songLists: List<Track> = emptyList(),
    val albumSongCount: Int = 0
)

@Serializable
data class NewWorksBlockTitle(
    val artistName: String = "",
    val artistId: Long = 0,
    val imgUrl: String = "",
    val resourceId: Long = 0,
    val resourceName: String = "",
    val resourcePicUrl: String? = null
)
