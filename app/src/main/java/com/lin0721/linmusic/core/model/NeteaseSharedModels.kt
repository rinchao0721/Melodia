package com.lin0721.linmusic.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

// 跨业务域共享的网易云基础数据模型，被 playlist/player/artist/library/home/search 等多个域的接口响应体引用。

// 空请求体，用于不需要参数的 POST 接口
@Serializable
class EmptyBody

@Serializable
data class Artist(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
    val img1v1Url: String = "",
)

@Serializable
data class Album(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
    // 仅专辑搜索结果（cloudsearch type=10）下发，歌曲内嵌 al 字段中不含此二项，已真机核实
    val artists: List<Artist> = emptyList(),
    val publishTime: Long = 0
)

@Serializable
data class Track(
    val id: Long = 0,
    val name: String = "",
    // 同时兼容 "ar" (常规接口) 和 "artists" (相似歌曲接口)
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @JsonNames("ar", "artists")
    val ar: List<Artist> = emptyList(),
    // 同时兼容 "al" (常规接口) 和 "album" (相似歌曲接口)
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @JsonNames("al", "album")
    val al: Album = Album(),
    val fee: Int = 0,
    val publishTime: Long = 0, // 歌曲发行时间戳，部分接口在歌曲详情中包含
    val dt: Long = 0
)

@Serializable
data class PlaylistCreator(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = "",
)

// 歌单/专辑统一详情模型，playlist 域产出，create 域新建歌单后同样返回该结构
@Serializable
data class PlaylistDetail(
    val id: Long = 0,
    val name: String = "",
    val coverImgUrl: String = "",
    val description: String? = null,
    val playCount: Long = 0,
    val subscribed: Boolean = false,
    val creator: PlaylistCreator? = null,
    val tracks: List<Track> = emptyList(),
    // 仅歌单搜索结果（cloudsearch type=1000）下发，详情接口里 tracks 本身已能反映曲目数，此字段该场景不下发，已真机核实
    val trackCount: Int = 0
)

// "我喜欢的音乐"是网易云的系统歌单，不能改名/改简介/换封面/删除/手动调整曲目顺序。
fun isLikedSongsPlaylist(name: String, playlistId: Long, uid: Long?): Boolean =
    name.contains("喜欢的音乐") || (uid != null && playlistId == uid)

// 歌手领域模型，artist/player/home 等多域共用
data class ArtistInfo(
    val id: Long,
    val name: String,
    val avatarUrl: String
)

// ======================= 歌手载荷模型（artist 域产出，player 域同样消费）=======================

@Serializable
data class ArtistDetailInfo(
    val id: Long = 0,
    val name: String = "",
    val cover: String = "",
    val avatar: String = "",
    val briefDesc: String = "",
    val albumSize: Int = 0,
    val musicSize: Int = 0,
    val identifyTag: List<String>? = null,
    val trans: String? = null, // 翻译名称
    val alias: List<String>? = null // 别名列表
)

@Serializable
data class ArtistAlbum(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
    val publishTime: Long = 0,
    val size: Int = 0
)

// 歌手 MV 载荷模型；封面/播放量字段名未经真机数据验证，用 JsonNames 兜底常见别名
@Serializable
data class ArtistMv(
    val id: Long = 0,
    val name: String = "",
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @JsonNames("imgurl16v9", "imgurl", "cover")
    val cover: String = "",
    val duration: Long = 0,
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @JsonNames("playCount", "playcount")
    val playCount: Long = 0,
    val publishTime: String = ""
)

// ======================= 评论载荷模型（core/comment 产出，player/playlist 消费）=======================

@Serializable
data class CommentItem(
    val commentId: Long = 0,
    val user: CommentUser = CommentUser(),
    val content: String = "",
    val time: Long = 0,
    val timeStr: String? = null,
    val likedCount: Int = 0,
    val liked: Boolean = false,
    val beReplied: List<BeRepliedComment>? = null
)

@Serializable
data class CommentUser(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = ""
)

@Serializable
data class BeRepliedComment(
    val user: CommentUser? = null,
    val content: String? = null,
    val beRepliedCommentId: Long? = null
)
