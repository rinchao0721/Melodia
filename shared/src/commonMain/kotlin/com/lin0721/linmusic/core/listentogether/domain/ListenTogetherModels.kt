package com.lin0721.linmusic.core.listentogether.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

data class ListenTogetherMember(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String
)

// 成员对象没有房主标记，房主只能用 creatorId 比对
data class ListenTogetherRoom(
    val id: String,
    val creatorId: Long,
    val members: List<ListenTogetherMember>
) {
    fun isHost(userId: Long): Boolean = userId != 0L && userId == creatorId
}

// 房间里的播放指令，serverSeq 是服务端时间戳，暂停也会递增
data class RoomCommand(
    val commandType: String?,
    val targetSongId: Long?,
    val progressMs: Long,
    val playing: Boolean?,
    val serverSeq: Long
)

data class RoomPlayback(
    val songIds: List<Long>,
    val command: RoomCommand?
)

data class RoomStatus(
    val inRoom: Boolean,
    val room: ListenTogetherRoom?
)

data class RoomCheck(
    val joinable: Boolean,
    val message: String?
)

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.num(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

fun parseRoom(roomInfo: JsonObject?): ListenTogetherRoom? {
    val id = roomInfo?.str("roomId")?.takeIf { it.isNotBlank() } ?: return null
    val members = (roomInfo["roomUsers"] as? JsonArray).orEmpty().mapNotNull { element ->
        val user = element as? JsonObject ?: return@mapNotNull null
        ListenTogetherMember(
            userId = user.num("userId") ?: return@mapNotNull null,
            nickname = user.str("nickname").orEmpty(),
            avatarUrl = user.str("avatarUrl").orEmpty()
        )
    }
    return ListenTogetherRoom(id = id, creatorId = roomInfo.num("creatorId") ?: 0L, members = members)
}

// 房主尚未推送队列时 data 为空对象，返回 null
fun parseRoomPlayback(data: JsonObject?): RoomPlayback? {
    val playlist = data?.obj("playlist") ?: return null
    val mode = playlist.str("playMode")?.uppercase().orEmpty()
    val random = "RANDOM" in mode || "SHUFFLE" in mode
    val list = playlist.obj(if (random) "randomList" else "displayList")
    val ids = (list?.get("result") as? JsonArray).orEmpty().mapNotNull { element ->
        (element as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
    }.distinct()
    val command = data.obj("playCommand")?.let { raw ->
        RoomCommand(
            commandType = raw.str("commandType")?.uppercase(),
            targetSongId = raw.num("targetSongId")?.takeIf { it > 0 },
            progressMs = (raw.num("progress") ?: 0L).coerceAtLeast(0L),
            playing = when (raw.str("playStatus")?.uppercase()) {
                null -> null
                "PLAY", "PLAYING" -> true
                else -> false
            },
            serverSeq = raw.num("serverSeq") ?: 0L
        )
    }
    return RoomPlayback(songIds = ids, command = command)
}
