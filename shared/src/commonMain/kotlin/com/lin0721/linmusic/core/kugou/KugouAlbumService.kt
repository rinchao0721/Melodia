package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.network.AppError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// 酷狗专辑详情与专辑曲目服务
class KugouAlbumService(
    private val gatewayClient: KugouGatewayClient
) {
    // module/album_detail.js
    suspend fun getAlbumDetail(
        albumId: String,
        isBuy: Int = 0
    ): Result<KugouAlbumDetailDto> = runCatching {
        val cleanId = albumId.trim()
        require(cleanId.isNotEmpty()) { "缺少专辑 album_id" }

        val dataObj = buildJsonObject {
            put(
                "data",
                buildJsonArray {
                    add(buildJsonObject { put("album_id", cleanId) })
                }
            )
            put("is_buy", isBuy)
            put(
                "fields",
                "album_id,album_name,publish_date,sizable_cover,intro,language,is_publish,heat,type,quality,authors,exclusive,author_name,trans_param"
            )
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/kmr/v2/albums",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf(
                    "x-router" to "openapi.kugou.com",
                    "kg-tid" to "255"
                )
            )
        )
        ensureKugouBizSuccess(response, "获取酷狗专辑详情失败")

        val albumObj = when (val dataNode = response.bodyJson?.get("data")) {
            is JsonArray -> dataNode.firstOrNull()?.jsonObjectOrNull()
            is JsonObject -> dataNode["info"]?.jsonArrayOrNull()?.firstOrNull()?.jsonObjectOrNull()
                ?: dataNode
            else -> null
        } ?: throw AppError.BizError(-1, "未找到酷狗专辑详情")

        val authors = albumObj["authors"]?.jsonArrayOrNull()?.mapNotNull { elem ->
            val aObj = elem.jsonObjectOrNull() ?: return@mapNotNull null
            val name = aObj.firstNonBlank("author_name", "name")
            if (name.isBlank()) return@mapNotNull null
            KugouTrackArtistDto(
                id = aObj.firstNonBlank("author_id", "id"),
                name = name,
                avatarUrl = normalizeKugouCoverUrl(aObj.firstNonBlank("sizable_avatar", "avatar"))
            )
        }.orEmpty()

        val pubDate = albumObj.firstNonBlank("publish_date", "publish_time")
        KugouAlbumDetailDto(
            albumId = albumObj.firstNonBlank("album_id", "albumid").ifBlank { cleanId },
            name = albumObj.firstNonBlank("album_name", "albumname"),
            coverUrl = normalizeKugouCoverUrl(albumObj.firstNonBlank("sizable_cover", "cover", "img")),
            intro = albumObj.string("intro").trim(),
            publishDate = pubDate,
            publishTimeMs = parseKugouDateTimeMs(pubDate),
            language = albumObj.string("language").trim(),
            heat = albumObj.long("heat", 0L),
            authors = authors,
            authorName = albumObj.firstNonBlank("author_name", "singername"),
            songCount = albumObj.int("song_count", 0).takeIf { it > 0 } ?: albumObj.int("songcount", 0)
        )
    }

    // module/album_songs.js
    suspend fun getAlbumSongs(
        albumId: String,
        page: Int = 1,
        pageSize: Int = 30,
        isBuy: String = ""
    ): Result<KugouAlbumSongsPage> = runCatching {
        val cleanId = albumId.trim()
        require(cleanId.isNotEmpty()) { "缺少专辑 album_id" }
        val safePage = page.coerceAtLeast(1)

        val dataObj = buildJsonObject {
            put("album_id", cleanId)
            put("is_buy", isBuy)
            put("page", safePage)
            put("pagesize", pageSize)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/v1/album_audio/lite",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf(
                    "x-router" to "openapi.kugou.com",
                    "kg-tid" to "255"
                )
            )
        )
        ensureKugouBizSuccess(response, "获取酷狗专辑曲目失败")

        val dataObjResp = response.bodyJson?.get("data")?.jsonObjectOrNull()
        val rawItems = dataObjResp?.get("songs")?.jsonArrayOrNull()
            ?: dataObjResp?.get("info")?.jsonArrayOrNull()
            ?: dataObjResp?.get("list")?.jsonArrayOrNull()
            ?: response.bodyJson?.get("data")?.jsonArrayOrNull()
            ?: emptyList()

        val songs = rawItems.mapNotNull { it.jsonObjectOrNull()?.let(::parseKugouTrackItem) }
        val total = dataObjResp?.int("total", 0)?.takeIf { it > 0 }
            ?: response.bodyJson?.int("total", 0)?.takeIf { it > 0 }
            ?: songs.size
        val hasMore = rawItems.isNotEmpty() && ((safePage - 1) * pageSize + rawItems.size < total || rawItems.size >= pageSize)

        KugouAlbumSongsPage(
            songs = songs,
            totalCount = total,
            hasMore = hasMore
        )
    }
}
