package com.lin0721.linmusic.core.listentogether.domain

data class InviteTarget(val roomId: String, val inviterId: Long)

// 邀请文案：房间号与邀请人 uid 编码成可复制粘贴的文本
object ListenTogetherInvite {

    const val PREFIX = "【Melodia 一起听】"
    const val DEFAULT_DISPLAY_TEXT = "邀请你一起听"
    private const val MAX_SONG_NAME_LENGTH = 40
    private const val MAX_TEXT_LENGTH = 4096

    // 匹配完整 token 再做严格校验，避免在非法字符处截断误解析
    private val roomTokenPattern = Regex("""roomId\s*=\s*([^\s;；,，]+)""")
    private val inviterTokenPattern = Regex("""inviterId\s*=\s*([^\s;；,，]+)""")
    private val validRoomIdPattern = Regex("""[0-9A-Za-z_-]{1,128}""")
    private val inviteCodePattern = Regex(
        """(?:【Melodia 一起听】\s*)?(?:roomId\s*=\s*[^\s;；,，]+\s*[;；,，\s]+\s*inviterId\s*=\s*[^\s;；,，]+|inviterId\s*=\s*[^\s;；,，]+\s*[;；,，\s]+\s*roomId\s*=\s*[^\s;；,，]+)"""
    )
    private val trailingSongPattern = Regex("""邀请你一起听「(.+)」\s*$""")
    private val inlineSongPattern = Regex("""邀请你一起听「([^」]+)」""")
    private val lineBreakPattern = Regex("""[\r\n]+""")

    fun format(roomId: String, inviterId: Long, songName: String? = null): String {
        val cleanSong = normalizeSongName(songName)
        val readable = if (cleanSong != null) "$DEFAULT_DISPLAY_TEXT「$cleanSong」" else DEFAULT_DISPLAY_TEXT
        return "$readable${PREFIX}roomId=${roomId.trim()};inviterId=$inviterId"
    }

    // 解析不出房间号或邀请人返回 null，不拿残缺参数请求接口
    fun parse(text: String): InviteTarget? {
        if (text.isBlank() || text.length > MAX_TEXT_LENGTH) return null
        val roomId = roomTokenPattern.find(text)?.groupValues?.get(1)
            ?.takeIf { validRoomIdPattern.matches(it) }
            ?: return null
        val inviterId = inviterTokenPattern.find(text)?.groupValues?.get(1)
            ?.toLongOrNull()
            ?.takeIf { it >= 0L }
            ?: return null
        return InviteTarget(roomId, inviterId)
    }

    // 去掉文本码保留可读前缀；无前缀的旧格式返回默认邀请文案
    fun displayText(text: String): String {
        if (text.isBlank()) return ""
        if (parse(text) == null) return text
        val stripped = stripInviteCode(text)
        return stripped.ifBlank { DEFAULT_DISPLAY_TEXT }
    }

    fun extractSongName(text: String): String? {
        if (parse(text) == null) return null
        val stripped = stripInviteCode(text)
        val raw = trailingSongPattern.find(stripped)?.groupValues?.get(1)
            ?: inlineSongPattern.find(stripped)?.groupValues?.get(1)
            ?: return null
        return normalizeSongName(raw)
    }

    private fun stripInviteCode(text: String): String =
        inviteCodePattern.replace(text, "").replace(PREFIX, "").trim()

    private fun normalizeSongName(songName: String?): String? =
        songName?.replace(lineBreakPattern, " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(MAX_SONG_NAME_LENGTH)
}
