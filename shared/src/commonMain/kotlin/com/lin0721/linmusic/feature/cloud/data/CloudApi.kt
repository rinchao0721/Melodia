package com.lin0721.linmusic.feature.cloud.data

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

// 我的云盘相关的网易云 Retrofit 接口定义。
interface CloudApi {

    // 云盘歌曲列表，真机核实 limit/offset 真实生效，非本项目其余"忽略 limit"的接口
    @POST("/weapi/v1/cloud/get")
    suspend fun getCloudSongs(
        @Body body: CloudListRequest
    ): CloudListResponse

    // 删除云盘歌曲
    @POST("/weapi/cloud/del")
    suspend fun deleteCloudSong(
        @Body body: CloudDeleteRequest
    ): CloudActionResponse

    // 重新匹配云盘歌曲到正版曲目
    @POST("/weapi/cloud/user/song/match")
    suspend fun matchCloudSong(
        @Body body: CloudMatchRequest
    ): CloudActionResponse
}

// ======================= 请求体 =======================

@Serializable
data class CloudListRequest(
    val limit: Int = 30,
    val offset: Int = 0
)

@Serializable
data class CloudDeleteRequest(
    val songIds: List<Long>
)

@Serializable
data class CloudMatchRequest(
    val userId: Long,
    val songId: Long,
    val adjustSongId: Long
)

// ======================= 列表响应 =======================

@Serializable
data class CloudListResponse(
    val code: Int = 0,
    val data: List<CloudSongItem> = emptyList(),
    val count: Int = 0,
    val size: Long = 0,
    val maxSize: Long = 0,
    val hasMore: Boolean = false
) {
    val isSuccess: Boolean get() = code == 200
}

// 顶层字段是扁平结构，比嵌套的 privateCloud/privilege.pc 干净，多数场景 UI 直接用这些——
// 但 artist/album 是例外：真机核实匹配（matchType=matched）后服务端只更新 simpleSong.ar/al，
// 顶层 artist/album 永远保持用户最初上传时的自报值，映射时两者取哪个要按 matchType 区分
@Serializable
data class CloudSongItem(
    val songId: Long = 0,
    val songName: String = "",
    val artist: String = "",
    val album: String = "",
    val bitrate: Int = 0,
    val fileSize: Long = 0,
    val fileName: String = "",
    val addTime: Long = 0,
    // 真机核实过 "unmatched"/"matched" 两种取值，其余取值一律当作已识别处理，不假设还有别的取值
    val matchType: String = "",
    val simpleSong: CloudSimpleSong = CloudSimpleSong()
)

@Serializable
data class CloudSimpleSong(
    val ar: List<Artist> = emptyList(),
    val al: Album = Album()
)

// ======================= 写操作响应（删除/匹配共用） =======================

@Serializable
data class CloudActionResponse(
    val code: Int = 0,
    val message: String? = null
) {
    val isSuccess: Boolean get() = code == 200
}
