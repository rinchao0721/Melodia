package com.lin0721.linmusic.core.listentogether.data

import com.lin0721.linmusic.core.listentogether.domain.PlayCommand
import com.lin0721.linmusic.core.listentogether.domain.ListenTogetherRoom
import com.lin0721.linmusic.core.listentogether.domain.RoomCheck
import com.lin0721.linmusic.core.listentogether.domain.RoomPlayback
import com.lin0721.linmusic.core.listentogether.domain.RoomStatus
import com.lin0721.linmusic.core.listentogether.domain.parseRoom
import com.lin0721.linmusic.core.listentogether.domain.parseRoomPlayback
import com.lin0721.linmusic.core.AppEnvironment
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.apiFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

interface ListenTogetherRepository {
    fun status(): Flow<Result<RoomStatus>>
    fun checkRoom(roomId: String): Flow<Result<RoomCheck>>
    fun createRoom(songIds: List<Long>): Flow<Result<ListenTogetherRoom>>
    fun acceptInvitation(roomId: String, inviterId: Long): Flow<Result<ListenTogetherRoom>>

    // 房主尚未推送队列时返回 null
    fun playback(roomId: String): Flow<Result<RoomPlayback?>>

    // Boolean 为服务端是否接受该指令
    fun reportCommand(roomId: String, command: PlayCommand): Flow<Result<Boolean>>
    fun reportPlaylist(
        roomId: String,
        userId: Long,
        version: Long,
        songIds: List<Long>,
        anchorIndex: Int
    ): Flow<Result<Boolean>>

    // 返回服务端要求的心跳间隔（秒）
    fun heartbeat(roomId: String, songId: Long, playing: Boolean, progressMs: Long): Flow<Result<Int?>>
    fun endRoom(roomId: String): Flow<Result<Boolean>>
}

class ListenTogetherRepositoryImpl(
    private val api: ListenTogetherApi
) : ListenTogetherRepository {

    // 字符串化的内层 JSON 必须带出默认值字段
    private val innerJson = Json { encodeDefaults = true }

    override fun status() = apiFlow(
        request = { api.getStatus() },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { response ->
            val inRoom = response.data?.inRoom == true
            RoomStatus(inRoom, if (inRoom) parseRoom(response.data?.roomInfo) else null)
        }
    )

    override fun checkRoom(roomId: String) = apiFlow(
        request = { api.checkRoom(ListenTogetherRoomIdRequest(roomId)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { RoomCheck(it.data?.joinable == true, it.data?.copywriting) }
    )

    override fun createRoom(songIds: List<Long>) = apiFlow(
        request = { api.createRoom(ListenTogetherCreateRequest(songIds, type = "song")) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { requireRoom(parseRoom(it.data?.roomInfo)) }
    )

    override fun acceptInvitation(roomId: String, inviterId: Long) = apiFlow(
        request = { api.acceptInvitation(ListenTogetherAcceptRequest(roomId, inviterId, refer = "inbox_invite")) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { requireRoom(parseRoom(it.data?.roomInfo)) }
    )

    override fun playback(roomId: String) = apiFlow(
        request = { api.getRoomPlaylist(ListenTogetherRoomIdRequest(roomId)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { response ->
            // 调试包记录原始指令，用于确认发起人等未知字段
            if (AppEnvironment.isDebug) AppLogger.d("ListenTogether", "原始指令 ${response.data?.get("playCommand")}")
            parseRoomPlayback(response.data)
        }
    )

    override fun reportCommand(roomId: String, command: PlayCommand) = apiFlow(
        request = {
            val info = ListenTogetherCommandInfo(
                commandType = command.type.wire,
                progress = command.progressMs.coerceAtLeast(0L),
                playStatus = if (command.playing) "PLAY" else "PAUSE",
                formerSongId = (command.formerSongId ?: -1L).toString(),
                targetSongId = (command.songId ?: -1L).toString(),
                clientSeq = command.clientSeq
            )
            api.reportCommand(
                ListenTogetherCommandRequest(roomId, innerJson.encodeToString(ListenTogetherCommandInfo.serializer(), info))
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { it.data?.result == true }
    )

    override fun reportPlaylist(roomId: String, userId: Long, version: Long, songIds: List<Long>, anchorIndex: Int) = apiFlow(
        request = {
            val ids = songIds.map { it.toString() }
            val param = ListenTogetherPlaylistParam(
                version = listOf(ListenTogetherPlaylistVersion(userId, version)),
                anchorSongId = songIds.getOrNull(anchorIndex)?.toString().orEmpty(),
                anchorPosition = anchorIndex,
                randomList = ids,
                displayList = ids
            )
            api.reportPlaylist(
                ListenTogetherReportPlaylistRequest(roomId, innerJson.encodeToString(ListenTogetherPlaylistParam.serializer(), param))
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { it.data?.result == true }
    )

    override fun heartbeat(roomId: String, songId: Long, playing: Boolean, progressMs: Long) = apiFlow(
        request = {
            api.heartbeat(
                ListenTogetherHeartbeatRequest(roomId, songId, if (playing) "PLAY" else "PAUSE", progressMs.coerceAtLeast(0L))
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { it.data?.timeSpan }
    )

    override fun endRoom(roomId: String) = apiFlow(
        request = { api.endRoom(ListenTogetherRoomIdRequest(roomId)) },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { it.data?.success == true }
    )

    private fun requireRoom(room: ListenTogetherRoom?): ListenTogetherRoom =
        room ?: throw IllegalStateException("房间信息解析失败")
}
