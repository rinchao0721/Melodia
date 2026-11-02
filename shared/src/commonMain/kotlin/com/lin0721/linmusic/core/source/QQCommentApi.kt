package com.lin0721.linmusic.core.source

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val QQ_COMMENT_URL = "https://c.y.qq.com/base/fcgi-bin/fcg_global_comment_h5.fcg"

@Serializable
data class QQCommentResponse(
    val code: Int = -1,
    val comment: QQCommentSection? = null,
    @SerialName("hot_comment") val hotComment: QQCommentSection? = null
)

@Serializable
data class QQCommentSection(
    // 没有评论时下发 null
    val commentlist: List<QQComment>? = null,
    val commenttotal: Int = 0
)

@Serializable
data class QQComment(
    val commentid: String = "",
    val rootcommentid: String = "",
    val nick: String = "",
    val avatarurl: String = "",
    val rootcommentcontent: String = "",
    val praisenum: Int = 0,
    // 秒级时间戳
    val time: Long = 0
)

// QQ 音乐歌曲评论，参照 qq-music-api 的 getComments；热评只在首页下发
class QQCommentApi(private val baseUrl: String = QQ_COMMENT_URL) {

    // 参照实现用 GB2312 输出，这里统一按 UTF-8 解码，改为 utf-8 才不乱码
    suspend fun fetch(topId: Long, pageNum: Int, pageSize: Int, lastCommentId: String): QQCommentResponse {
        val query = listOf(
            "biztype" to "1",
            "topid" to topId.toString(),
            "cid" to "205360772",
            "cmd" to "8",
            "reqtype" to "2",
            "pagenum" to pageNum.toString(),
            "pagesize" to pageSize.toString(),
            "lasthotcommentid" to lastCommentId,
            "format" to "json",
            "outCharset" to "utf-8",
            "domain" to "qq.com",
            "ct" to "24",
            "cv" to "10101010",
            "needmusiccrit" to "0"
        ).joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }
        val body = SourceHttpClient.get("$baseUrl?$query", mapOf("Referer" to "https://y.qq.com/"))
        val response = SourceHttpClient.json.decodeFromString(QQCommentResponse.serializer(), body)
        if (response.code != 0) throw SourceException("QQ 音乐评论接口返回 code=${response.code}")
        return response
    }
}
