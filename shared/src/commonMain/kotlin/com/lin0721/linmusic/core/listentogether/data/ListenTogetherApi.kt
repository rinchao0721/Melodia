package com.lin0721.linmusic.core.listentogether.data

import com.lin0721.linmusic.core.model.EmptyBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST

// 网易云「一起听」官方接口，参考 api-enhanced 的 listentogether_*.js，全部走 eapi。
interface ListenTogetherApi {

    // 当前账号所在房间状态（需登录）
    @POST("/eapi/listen/together/status/get")
    suspend fun getStatus(@Body body: EmptyBody = EmptyBody()): ListenTogetherStatusResponse

    // 校验房间能否加入（需登录）
    @POST("/eapi/listen/together/room/check")
    suspend fun checkRoom(@Body body: ListenTogetherRoomIdRequest): ListenTogetherCheckResponse

    // 创建房间（需登录）
    @POST("/eapi/listen/together/room/create")
    suspend fun createRoom(@Body body: ListenTogetherCreateRequest): ListenTogetherRoomResponse

    // 接受邀请进房（需登录）
    @POST("/eapi/listen/together/play/invitation/accept")
    suspend fun acceptInvitation(@Body body: ListenTogetherAcceptRequest): ListenTogetherRoomResponse

    // 获取房间队列与当前播放指令（需登录）
    @POST("/eapi/listen/together/sync/playlist/get")
    suspend fun getRoomPlaylist(@Body body: ListenTogetherRoomIdRequest): ListenTogetherDataResponse

    // 上报播放指令（需登录）
    @POST("/eapi/listen/together/play/command/report")
    suspend fun reportCommand(@Body body: ListenTogetherCommandRequest): ListenTogetherResultResponse

    // 上报房间队列（需登录）
    @POST("/eapi/listen/together/sync/list/command/report")
    suspend fun reportPlaylist(@Body body: ListenTogetherReportPlaylistRequest): ListenTogetherResultResponse

    // 心跳（需登录）
    @POST("/eapi/listen/together/heartbeat")
    suspend fun heartbeat(@Body body: ListenTogetherHeartbeatRequest): ListenTogetherHeartbeatResponse

    // 结束房间（需登录）
    @POST("/eapi/listen/together/end/v2")
    suspend fun endRoom(@Body body: ListenTogetherRoomIdRequest): ListenTogetherEndResponse
}

@Serializable
data class ListenTogetherRoomIdRequest(val roomId: String)

// 建房的种子曲目不能为空
@Serializable
data class ListenTogetherCreateRequest(val songIds: List<Long>, val type: String)

@Serializable
data class ListenTogetherAcceptRequest(
    val roomId: String,
    val inviterId: Long,
    val refer: String
)

// 服务端要求 commandInfo 为字符串化的 JSON，id 字段也是字符串
@Serializable
data class ListenTogetherCommandInfo(
    val commandType: String,
    val progress: Long,
    val playStatus: String,
    val formerSongId: String,
    val targetSongId: String,
    val clientSeq: Long
)

@Serializable
data class ListenTogetherCommandRequest(val roomId: String, val commandInfo: String)

@Serializable
data class ListenTogetherPlaylistVersion(val userId: Long, val version: Long)

// displayList/randomList 必须是字符串数组
@Serializable
data class ListenTogetherPlaylistParam(
    val commandType: String = "REPLACE",
    val version: List<ListenTogetherPlaylistVersion>,
    val anchorSongId: String = "",
    val anchorPosition: Int = -1,
    val randomList: List<String>,
    val displayList: List<String>
)

@Serializable
data class ListenTogetherReportPlaylistRequest(val roomId: String, val playlistParam: String)

@Serializable
data class ListenTogetherHeartbeatRequest(
    val roomId: String,
    val songId: Long,
    val playStatus: String,
    val progress: Long
)

@Serializable
data class ListenTogetherStatusResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: ListenTogetherStatusData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class ListenTogetherStatusData(
    val inRoom: Boolean = false,
    val status: String? = null,
    val roomInfo: JsonObject? = null
)

@Serializable
data class ListenTogetherCheckResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: ListenTogetherCheckData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class ListenTogetherCheckData(
    val joinable: Boolean = false,
    val status: String? = null,
    val copywriting: String? = null
)

@Serializable
data class ListenTogetherRoomResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: ListenTogetherRoomData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class ListenTogetherRoomData(val roomInfo: JsonObject? = null)

// 房间队列与指令结构随房间状态变化，保留原始 JSON 由 domain 层解析
@Serializable
data class ListenTogetherDataResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: JsonObject? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class ListenTogetherResultResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: ListenTogetherResultData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class ListenTogetherResultData(val result: Boolean = false)

@Serializable
data class ListenTogetherHeartbeatResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: ListenTogetherHeartbeatData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

// timeSpan 为服务端要求的心跳间隔（秒）
@Serializable
data class ListenTogetherHeartbeatData(val result: Boolean = false, val timeSpan: Int? = null)

@Serializable
data class ListenTogetherEndResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: ListenTogetherEndData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class ListenTogetherEndData(val success: Boolean = false)
