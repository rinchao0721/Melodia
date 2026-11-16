package com.lin0721.linmusic.feature.message.data

import com.lin0721.linmusic.feature.message.domain.CommentMessage
import com.lin0721.linmusic.feature.message.domain.ForwardMessage
import com.lin0721.linmusic.feature.message.domain.MessagePage
import com.lin0721.linmusic.feature.message.domain.NoticeItem
import com.lin0721.linmusic.feature.message.domain.PrivateConversation
import com.lin0721.linmusic.feature.message.domain.PrivateMessage
import kotlinx.coroutines.flow.Flow

// 消息数据仓储；cursor 的含义随接口而异，首页传各接口约定的起始值（见 MessageRepositoryImpl）
interface MessageRepository {

    // 回复我的评论，cursor 为 beforeTime，首页 -1
    fun getReceivedComments(uid: Long, cursor: Long, limit: Int): Flow<Result<MessagePage<CommentMessage>>>

    // @我，cursor 为 offset，首页 0
    fun getForwards(cursor: Long, limit: Int): Flow<Result<MessagePage<ForwardMessage>>>

    // 通知，cursor 为 time，首页 -1
    fun getNotices(cursor: Long, limit: Int): Flow<Result<MessagePage<NoticeItem>>>

    // 私信会话列表；selfUid 用于从 fromUserId/toUserId 识别对方，cursor 为 offset，首页 0
    fun getPrivateConversations(selfUid: Long, cursor: Long, limit: Int): Flow<Result<MessagePage<PrivateConversation>>>

    // 与某人的私信历史；cursor 为上一页最后一条的 time，首页 0
    fun getPrivateHistory(userId: Long, cursor: Long, limit: Int): Flow<Result<MessagePage<PrivateMessage>>>

    fun sendPrivateText(userId: Long, text: String): Flow<Result<Unit>>

    fun sendPrivateSong(userId: Long, songId: Long, text: String): Flow<Result<Unit>>
}
