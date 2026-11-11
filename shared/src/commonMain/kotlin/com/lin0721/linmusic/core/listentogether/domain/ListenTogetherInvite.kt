package com.lin0721.linmusic.core.listentogether.domain

data class InviteTarget(val roomId: String, val inviterId: Long)

// 邀请文案：房间号与邀请人 uid 编码成可复制粘贴的文本
object ListenTogetherInvite {

    const val PREFIX = "【Melodia 一起听】"

    private val roomPattern = Regex("""roomId\s*=\s*([0-9A-Za-z_-]+)""")
    private val inviterPattern = Regex("""inviterId\s*=\s*(\d+)""")

    fun format(roomId: String, inviterId: Long): String = "${PREFIX}roomId=$roomId;inviterId=$inviterId"

    // 解析不出房间号或邀请人返回 null，不拿残缺参数请求接口
    fun parse(text: String): InviteTarget? {
        val roomId = roomPattern.find(text)?.groupValues?.get(1) ?: return null
        val inviterId = inviterPattern.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return InviteTarget(roomId, inviterId)
    }
}
