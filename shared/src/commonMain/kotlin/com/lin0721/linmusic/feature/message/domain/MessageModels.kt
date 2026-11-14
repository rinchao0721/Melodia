package com.lin0721.linmusic.feature.message.domain

import com.lin0721.linmusic.core.share.ShareCardMessage

data class MessageUser(
    val uid: Long,
    val nickname: String,
    val avatarUrl: String
)

// 一页消息；nextCursor 由各接口自己的翻页语义决定，hasMore 已排除"空页仍称有更多"的情况
data class MessagePage<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val nextCursor: Long,
    val newMsgCount: Int = 0
)

// 回复我的评论 Tab 的条目
data class CommentMessage(
    val commentId: Long,
    val user: MessageUser,
    val content: String,
    val time: Long,
    val repliedContent: String?,
    val resourceName: String?,
    val resourceCreator: String?
)

// @我 Tab 的条目；key 为翻页偏移生成的稳定标识
data class ForwardMessage(
    val key: Long,
    val user: MessageUser?,
    val content: String,
    val time: Long
)

// 通知 Tab 的条目，按通知内部 type 区分
sealed interface NoticeItem {
    val id: Long
    val time: Long

    // 别人对我的评论有了动作（推断为点赞）；user 是发起人，commentContent 是我的评论原文，
    // songId 来自 threadId，仅单曲评论可解析，歌名由 ViewModel 补查
    data class CommentLike(
        override val id: Long,
        override val time: Long,
        val user: MessageUser,
        val commentContent: String,
        val songId: Long?
    ) : NoticeItem

    // 别人赞了我的动态（样本核实：track.user 恒为我自己，发起人是外层 user）；eventText 是我的动态文案
    data class EventLike(
        override val id: Long,
        override val time: Long,
        val user: MessageUser,
        val eventText: String
    ) : NoticeItem

    // 我的歌单被别人操作（推断为收藏：playlist.userId 恒为我自己，发起人是外层 user）
    data class PlaylistCollected(
        override val id: Long,
        override val time: Long,
        val user: MessageUser,
        val playlistName: String,
        val trackCount: Int
    ) : NoticeItem

    // 解析失败或未知类型
    data class Unsupported(
        override val id: Long,
        override val time: Long
    ) : NoticeItem
}

data class PrivateConversation(
    val id: Long,
    val user: MessageUser,
    val lastMessage: String?,
    val lastTime: Long,
    val unreadCount: Int
)

enum class PrivateCardKind(val label: String) {
    Song("单曲"),
    Playlist("歌单"),
    Album("专辑"),
    Mv("MV"),
    General("分享")
}

data class PrivateCard(
    val kind: PrivateCardKind,
    val title: String,
    val subtitle: String,
    val cover: String?,
    val resourceId: Long
)

// 私信正文是字符串化的 JSON；解析失败或无法识别时保留原文于 raw，界面按 unsupported 降级
data class PrivateMessage(
    val id: Long,
    val time: Long,
    val fromUserId: Long,
    val toUserId: Long,
    val type: Int,
    val text: String?,
    val card: PrivateCard?,
    val raw: String?,
    val fromUser: MessageUser? = null,
    val toUser: MessageUser? = null,
    // Melodia 之间附带的分享卡片参数，仅歌曲卡片可能携带
    val shareCard: ShareCardMessage? = null
) {
    val isUnsupported: Boolean get() = text.isNullOrBlank() && card == null
}
