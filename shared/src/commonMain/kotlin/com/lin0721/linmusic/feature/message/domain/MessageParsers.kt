package com.lin0721.linmusic.feature.message.domain

import com.lin0721.linmusic.core.share.ShareCardMessageCodec
import com.lin0721.linmusic.feature.message.data.MessageCommentDto
import com.lin0721.linmusic.feature.message.data.MessageNoticeDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// 消息接口里大量字段是"字符串形式的 JSON"，这里统一二次解析；任何一步失败都降级，不向上抛异常

private const val NOTICE_TYPE_TRACK = 1
private const val NOTICE_TYPE_PLAYLIST = 2
private const val NOTICE_TYPE_COMMENT = 6
private const val PRIVATE_TYPE_TEXT = 6

private val parserJson = Json { ignoreUnknownKeys = true; isLenient = true }

// 单曲评论的 threadId 形如 R_SO_4_<songId>
private val SONG_THREAD_REGEX = Regex("^R_SO_4_(\\d+)$")

fun parseSongIdFromThreadId(threadId: String): Long? =
    SONG_THREAD_REGEX.matchEntire(threadId)?.groupValues?.get(1)?.toLongOrNull()

fun parseCommentMessage(dto: MessageCommentDto): CommentMessage {
    val resource = parseObject(dto.resourceJson)
    return CommentMessage(
        commentId = dto.commentId,
        user = MessageUser(dto.user.userId, dto.user.nickname, dto.user.avatarUrl),
        content = dto.content,
        time = dto.time,
        repliedContent = dto.beRepliedContent?.takeIf { it.isNotBlank() },
        resourceName = resource?.nonBlankString("name"),
        resourceCreator = resource?.nonBlankString("creator")
    )
}

fun parseNotice(dto: MessageNoticeDto): NoticeItem {
    val unsupported = NoticeItem.Unsupported(dto.id, dto.time)
    val root = parseObject(dto.notice) ?: return unsupported
    val rootUser = parseUser(root.obj("user")) ?: return unsupported
    return when (root.int("type")) {
        NOTICE_TYPE_COMMENT -> parseCommentLikeNotice(dto, root, rootUser) ?: unsupported
        NOTICE_TYPE_TRACK -> parseEventLikeNotice(dto, root, rootUser) ?: unsupported
        NOTICE_TYPE_PLAYLIST -> parsePlaylistCollectedNotice(dto, root, rootUser) ?: unsupported
        else -> unsupported
    }
}

// @我 元素结构暂无真实样本，字段名按动态结构推测，拿到样本后校正
fun parseForward(key: Long, obj: JsonObject): ForwardMessage {
    val innerJson = parseObject(obj.string("json"))
    return ForwardMessage(
        key = key,
        user = parseUser(obj.obj("user")),
        content = innerJson?.string("msg") ?: obj.string("msg") ?: obj.string("content") ?: "",
        time = obj.long("eventTime") ?: obj.long("time") ?: 0L
    )
}

// 样本核实：comment 是"我的评论"（comment.user 恒为我自己），外层 user 才是发起动作的人
private fun parseCommentLikeNotice(dto: MessageNoticeDto, root: JsonObject, rootUser: MessageUser): NoticeItem? {
    val comment = root.obj("comment") ?: return null
    return NoticeItem.CommentLike(
        id = dto.id,
        time = dto.time,
        user = rootUser,
        commentContent = comment.string("content").orEmpty(),
        songId = comment.string("threadId")?.let(::parseSongIdFromThreadId)
    )
}

private fun parseEventLikeNotice(dto: MessageNoticeDto, root: JsonObject, rootUser: MessageUser): NoticeItem? {
    val track = root.obj("track") ?: return null
    val inner = parseObject(track.string("json"))
    val songName = inner?.obj("song")?.nonBlankString("name")
    val text = inner?.string("msg")?.let(::cleanDisplayText)?.takeIf { it.isNotEmpty() }
        ?: songName?.let { "分享单曲《$it》" }
        .orEmpty()
    return NoticeItem.EventLike(dto.id, dto.time, rootUser, text)
}

private fun parsePlaylistCollectedNotice(dto: MessageNoticeDto, root: JsonObject, rootUser: MessageUser): NoticeItem? {
    val playlist = root.obj("playlist") ?: return null
    return NoticeItem.PlaylistCollected(
        id = dto.id,
        time = dto.time,
        user = rootUser,
        playlistName = playlist.string("name").orEmpty(),
        trackCount = playlist.int("trackCount") ?: 0
    )
}

// ======================= 私信解析 =======================

private data class PrivateBody(
    val type: Int,
    val text: String?,
    val title: String?,
    val card: PrivateCard?
)

// lastMsg / msg 为双层 JSON 字符串；type=6 为纯文本，type=1 为分享资源
private fun parsePrivateBody(raw: String?): PrivateBody? {
    val first = parseObject(raw) ?: return null
    // 会话列表偶现外层多包一层 {msgId, msg:"<内层JSON>"}，仅在含 msgId 且内层是对象时解包
    val obj = if (first.long("msgId") != null) {
        parseObject(first.string("msg")) ?: first
    } else {
        first
    }
    val text = obj.nonBlankString("msg")
        ?: obj.obj("generalMsg")?.nonBlankString("inboxBriefContent")
    return PrivateBody(
        type = obj.int("type") ?: -1,
        text = text,
        title = obj.nonBlankString("title"),
        card = parsePrivateCard(obj)
    )
}

private fun parsePrivateCard(obj: JsonObject): PrivateCard? {
    obj.obj("song")?.let { song ->
        val id = song.long("id") ?: 0L
        if (id > 0L) {
            // 顶层 song.picUrl 常为空，优先取 song.album.picUrl，兜底 blurPicUrl
            val album = song.obj("album")
            val cover = album?.nonBlankString("picUrl")
                ?: album?.nonBlankString("blurPicUrl")
                ?: song.nonBlankString("picUrl")
            return PrivateCard(
                kind = PrivateCardKind.Song,
                title = song.string("name").orEmpty(),
                subtitle = song.names("ar").ifBlank { song.names("artists") },
                cover = cover,
                resourceId = id
            )
        }
    }
    obj.obj("playlist")?.let { playlist ->
        val id = playlist.long("id") ?: 0L
        if (id > 0L) {
            return PrivateCard(
                kind = PrivateCardKind.Playlist,
                title = playlist.string("name").orEmpty(),
                subtitle = "",
                cover = playlist.nonBlankString("coverImgUrl") ?: playlist.nonBlankString("picUrl"),
                resourceId = id
            )
        }
    }
    obj.obj("album")?.let { album ->
        val id = album.long("id") ?: 0L
        if (id > 0L) {
            return PrivateCard(
                kind = PrivateCardKind.Album,
                title = album.string("name").orEmpty(),
                subtitle = album.obj("artist")?.string("name").orEmpty(),
                cover = album.nonBlankString("picUrl") ?: album.nonBlankString("blurPicUrl"),
                resourceId = id
            )
        }
    }
    obj.obj("mv")?.let { mv ->
        val id = mv.long("id") ?: 0L
        if (id > 0L) {
            return PrivateCard(
                kind = PrivateCardKind.Mv,
                title = mv.string("name").orEmpty(),
                subtitle = mv.string("artistName").orEmpty(),
                cover = mv.nonBlankString("imgurl") ?: mv.nonBlankString("imgurl16v9"),
                resourceId = id
            )
        }
    }
    val general = obj.obj("generalMsg") ?: return null
    val title = general.string("title").orEmpty()
    val cover = general.nonBlankString("cover")
    if (title.isBlank() && cover == null) return null
    return PrivateCard(
        kind = PrivateCardKind.General,
        title = title,
        subtitle = "",
        cover = cover,
        resourceId = 0L
    )
}

private fun summarizePrivateBody(body: PrivateBody?): String? {
    body ?: return null
    val card = body.card
    return when {
        body.type == PRIVATE_TYPE_TEXT -> body.text
        card?.kind == PrivateCardKind.Song && ShareCardMessageCodec.decode(body.text).message != null ->
            "分享卡片：${card.title}"
        body.title != null -> body.title
        card != null && card.title.isNotBlank() -> "分享${card.kind.label}：${card.title}"
        else -> body.text
    }
}

// 必须用 selfUid 从 fromUserId/toUserId 挑出对方，否则自己发出的最后一条会把对方识别成自己
fun parsePrivateConversation(obj: JsonObject, selfUid: Long): PrivateConversation? {
    val user = obj.obj("user") ?: return null
    val fromUserId = user.long("fromUserId") ?: 0L
    val toUserId = user.long("toUserId") ?: 0L
    val peerUid = if (fromUserId == selfUid) toUserId else fromUserId
    if (peerUid <= 0L) return null
    val peerProfile = obj.obj("fromUser")?.takeIf { it.long("userId") == peerUid }
        ?: obj.obj("toUser")?.takeIf { it.long("userId") == peerUid }
        ?: return null
    val body = parsePrivateBody(obj.string("lastMsg"))
    return PrivateConversation(
        id = user.long("id") ?: peerUid,
        user = parseUser(peerProfile) ?: return null,
        lastMessage = summarizePrivateBody(body),
        lastTime = obj.long("lastMsgTime") ?: user.long("lastMsgTime") ?: 0L,
        unreadCount = obj.int("newMsgCount") ?: user.int("newMsgCount") ?: 0
    )
}

fun parsePrivateMessage(obj: JsonObject): PrivateMessage? {
    val id = obj.long("id")?.takeIf { it > 0L } ?: return null
    val raw = obj.string("msg")
    val body = parsePrivateBody(raw)
    val fromUser = parseUser(obj.obj("fromUser"))
    val toUser = parseUser(obj.obj("toUser"))
    val decodedCard = if (body?.card?.kind == PrivateCardKind.Song) ShareCardMessageCodec.decode(body.text) else null
    val text = when {
        decodedCard?.message != null -> decodedCard.displayText.ifBlank { null }
        else -> body?.text ?: if (body?.type == PRIVATE_TYPE_TEXT) null else body?.title
    }
    return PrivateMessage(
        id = id,
        time = obj.long("time") ?: 0L,
        fromUserId = fromUser?.uid ?: obj.obj("fromUser")?.long("userId") ?: 0L,
        toUserId = toUser?.uid ?: obj.obj("toUser")?.long("userId") ?: 0L,
        type = body?.type ?: -1,
        text = text,
        card = body?.card,
        raw = raw,
        fromUser = fromUser,
        toUser = toUser,
        shareCard = decodedCard?.message
    )
}

// 动态文案常以零宽空格 + 换行开头，String.trim() 不处理零宽字符
private val ZERO_WIDTH_REGEX = Regex("[\u200B-\u200D\u2060\uFEFF]")

private fun cleanDisplayText(raw: String): String = raw.replace(ZERO_WIDTH_REGEX, "").trim()

private fun parseUser(obj: JsonObject?): MessageUser? {
    obj ?: return null
    val uid = obj.long("userId")?.takeIf { it > 0 } ?: return null
    return MessageUser(uid, obj.string("nickname").orEmpty(), obj.string("avatarUrl").orEmpty())
}

private fun parseObject(raw: String?): JsonObject? {
    if (raw.isNullOrBlank()) return null
    return runCatching { parserJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.nonBlankString(key: String): String? =
    string(key)?.takeIf { it.isNotBlank() }

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.names(key: String): String =
    ((this[key] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.string("name") } ?: emptyList())
        .filter { it.isNotBlank() }
        .joinToString("/")
