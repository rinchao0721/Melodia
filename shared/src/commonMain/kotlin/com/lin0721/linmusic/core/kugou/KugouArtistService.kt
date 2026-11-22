package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.AppError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// 酷狗歌手信息、歌曲/专辑列表与关注服务
class KugouArtistService(
    private val config: KugouConfig,
    private val deviceStore: KugouDeviceStore,
    private val gatewayClient: KugouGatewayClient,
    private val userPreferences: UserPreferences,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    // module/artist_detail.js
    suspend fun getArtistDetail(authorId: String): Result<KugouArtistDetailDto> = runCatching {
        val cleanId = authorId.trim()
        require(cleanId.isNotEmpty()) { "缺少歌手 author_id" }
        val dataObj = buildJsonObject {
            put("author_id", cleanId)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/kmr/v3/author",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf(
                    "x-router" to "openapi.kugou.com",
                    "kg-tid" to "36"
                )
            )
        )
        ensureKugouBizSuccess(response, "获取酷狗歌手详情失败")

        val dataObjResp = when (val dataNode = response.bodyJson?.get("data")) {
            is JsonObject -> dataNode
            is JsonArray -> dataNode.firstOrNull()?.jsonObjectOrNull()
            else -> null
        } ?: throw AppError.BizError(-1, "未找到酷狗歌手详情")

        val avatar = normalizeKugouCoverUrl(dataObjResp.firstNonBlank("sizable_avatar", "avatar", "k_pic", "pic"))
        val cover = normalizeKugouCoverUrl(dataObjResp.firstNonBlank("cover", "sizable_cover", "sizable_avatar", "avatar"))
        KugouArtistDetailDto(
            authorId = dataObjResp.firstNonBlank("author_id", "id").ifBlank { cleanId },
            name = dataObjResp.firstNonBlank("author_name", "name"),
            avatarUrl = avatar,
            coverUrl = cover,
            intro = dataObjResp.string("intro").trim(),
            albumCount = dataObjResp.int("album_count", 0),
            songCount = dataObjResp.int("song_count", 0).takeIf { it > 0 } ?: dataObjResp.int("audio_count", 0),
            mvCount = dataObjResp.int("mv_count", 0),
            fansCount = dataObjResp.firstPositiveLong("fans_count", "fansnums", "fans_num"),
            isFollowed = dataObjResp.bool("is_follow", false) || dataObjResp.int("is_follow", 0) == 1
        )
    }

    // module/artist_audios.js
    suspend fun getArtistAudios(
        authorId: String,
        page: Int = 1,
        pageSize: Int = 30,
        sort: String = "hot"
    ): Result<KugouArtistSongsPage> = runCatching {
        config.ensureModeSynced()
        val cleanId = authorId.trim()
        require(cleanId.isNotEmpty()) { "缺少歌手 author_id" }
        val safePage = page.coerceAtLeast(1)
        val device = deviceStore.getOrCreateDeviceIdentity()
        val sec = nowMs() / 1000L

        val dataObj = buildJsonObject {
            put("appid", config.effectiveAppId())
            put("clientver", config.effectiveClientVer())
            put("mid", device.mid)
            put("clienttime", sec)
            put("key", KugouSigner.signParamsKey(sec.toString(), mode = config.mode))
            put("author_id", cleanId)
            put("pagesize", pageSize)
            put("page", safePage)
            put("sort", if (sort.equals("hot", ignoreCase = true)) 1 else 2)
            put("area_code", "all")
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                baseUrl = "https://openapi.kugou.com",
                url = "/kmr/v1/audio_group/author",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf(
                    "x-router" to "openapi.kugou.com",
                    "kg-tid" to "220"
                )
            )
        )
        ensureKugouBizSuccess(response, "获取酷狗歌手单曲失败")

        val dataNode = response.bodyJson?.get("data")
        val rawItems = when (dataNode) {
            is JsonArray -> dataNode
            is JsonObject -> dataNode["info"]?.jsonArrayOrNull()
                ?: dataNode["list"]?.jsonArrayOrNull()
                ?: dataNode["songs"]?.jsonArrayOrNull()
                ?: emptyList()
            else -> emptyList()
        }
        val songs = rawItems.mapNotNull { it.jsonObjectOrNull()?.let(::parseKugouTrackItem) }
        val total = (dataNode as? JsonObject)?.int("total", 0)?.takeIf { it > 0 }
            ?: response.bodyJson?.int("total", 0)?.takeIf { it > 0 }
            ?: songs.size
        val hasMore = rawItems.isNotEmpty() && ((safePage - 1) * pageSize + rawItems.size < total || rawItems.size >= pageSize)

        KugouArtistSongsPage(
            songs = songs,
            totalCount = total,
            hasMore = hasMore
        )
    }

    // module/artist_albums.js
    suspend fun getArtistAlbums(
        authorId: String,
        page: Int = 1,
        pageSize: Int = 30,
        sort: String = "hot"
    ): Result<KugouArtistAlbumsPage> = runCatching {
        val cleanId = authorId.trim()
        require(cleanId.isNotEmpty()) { "缺少歌手 author_id" }
        val safePage = page.coerceAtLeast(1)

        val dataObj = buildJsonObject {
            put("author_id", cleanId)
            put("pagesize", pageSize)
            put("page", safePage)
            put("sort", if (sort.equals("hot", ignoreCase = true)) 3 else 1)
            put("category", 1)
            put("area_code", "all")
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/kmr/v1/author/albums",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf(
                    "x-router" to "openapi.kugou.com",
                    "kg-tid" to "36"
                )
            )
        )
        ensureKugouBizSuccess(response, "获取酷狗歌手专辑失败")

        val dataNode = response.bodyJson?.get("data")
        val rawItems = when (dataNode) {
            is JsonArray -> dataNode
            is JsonObject -> dataNode["info"]?.jsonArrayOrNull()
                ?: dataNode["list"]?.jsonArrayOrNull()
                ?: emptyList()
            else -> emptyList()
        }
        val albums = rawItems.mapNotNull { elem ->
            val obj = elem.jsonObjectOrNull() ?: return@mapNotNull null
            val albumId = obj.firstNonBlank("album_id", "albumid")
            if (albumId.isBlank()) return@mapNotNull null
            val pubDate = obj.firstNonBlank("publish_date", "publish_time")
            KugouArtistAlbumDto(
                albumId = albumId,
                name = obj.firstNonBlank("album_name", "albumname"),
                coverUrl = normalizeKugouCoverUrl(obj.firstNonBlank("sizable_cover", "cover", "img")),
                publishDate = pubDate,
                publishTimeMs = parseKugouDateTimeMs(pubDate),
                songCount = obj.int("song_count", 0).takeIf { it > 0 } ?: obj.int("songcount", 0),
                intro = obj.string("intro").trim(),
                authorName = obj.firstNonBlank("author_name", "singername")
            )
        }
        val total = (dataNode as? JsonObject)?.int("total", 0)?.takeIf { it > 0 }
            ?: response.bodyJson?.int("total", 0)?.takeIf { it > 0 }
            ?: albums.size
        val hasMore = rawItems.isNotEmpty() && ((safePage - 1) * pageSize + rawItems.size < total || rawItems.size >= pageSize)

        KugouArtistAlbumsPage(
            albums = albums,
            totalCount = total,
            hasMore = hasMore
        )
    }

    // module/artist_follow.js
    suspend fun followArtist(singerId: String): Result<Unit> = runCatching {
        config.ensureModeSynced()
        val session = requireActiveKugouSession(userPreferences)
        val cleanSingerId = singerId.trim()
        val singerIdNum = cleanSingerId.toLongOrNull()
            ?: throw IllegalArgumentException("酷狗歌手 ID 格式无效: $singerId")
        val sec = nowMs() / 1000L

        val encryptPlain = buildJsonObject {
            put("singerid", singerIdNum)
            put("token", session.token)
        }.toString()
        val encrypted = KugouCrypto.cryptoAesEncrypt(encryptPlain)
        val pPlain = buildJsonObject {
            put("clienttime", sec)
            put("key", encrypted.key)
        }.toString()
        val p = KugouCrypto.rsaEncrypt2(pPlain, config.effectivePublicKeyPem())

        val dataObj = buildJsonObject {
            put("plat", 0)
            put("userid", session.userIdLong)
            put("singerid", singerIdNum)
            put("source", 7)
            put("p", p)
            put("params", encrypted.str)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/followservice/v3/follow_singer",
                params = mapOf("clienttime" to sec),
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureKugouBizSuccess(response, "关注酷狗歌手失败")
    }

    // module/artist_unfollow.js
    suspend fun unfollowArtist(singerId: String): Result<Unit> = runCatching {
        config.ensureModeSynced()
        val session = requireActiveKugouSession(userPreferences)
        val cleanSingerId = singerId.trim()
        val singerIdNum = cleanSingerId.toLongOrNull()
        val sec = nowMs() / 1000L

        val encryptPlain = buildJsonObject {
            if (singerIdNum != null) put("singerid", singerIdNum) else put("singerid", cleanSingerId)
            put("token", session.token)
        }.toString()
        val encrypted = KugouCrypto.cryptoAesEncrypt(encryptPlain)
        val pPlain = buildJsonObject {
            put("clienttime", sec)
            put("key", encrypted.key)
        }.toString()
        val p = KugouCrypto.rsaEncrypt2(pPlain, config.effectivePublicKeyPem())

        val dataObj = buildJsonObject {
            put("plat", 0)
            put("userid", session.userIdLong)
            if (singerIdNum != null) put("singerid", singerIdNum) else put("singerid", cleanSingerId)
            put("source", 7)
            put("p", p)
            put("params", encrypted.str)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/followservice/v3/unfollow_singer",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureKugouBizSuccess(response, "取消关注酷狗歌手失败")
    }

    // module/user_follow.js
    suspend fun getUserFollowedArtists(): Result<List<KugouFollowedArtistDto>> = runCatching {
        config.ensureModeSynced()
        val session = requireActiveKugouSession(userPreferences)
        val sec = nowMs() / 1000L
        val pPlain = buildJsonObject {
            put("clienttime", sec)
            put("token", session.token)
        }.toString()
        val p = KugouCrypto.cryptoRSAEncrypt(pPlain, config.effectivePublicKeyPem()).uppercase()

        val dataObj = buildJsonObject {
            put("merge", 2)
            put("need_iden_type", 1)
            put("ext_params", "k_pic,jumptype,singerid,score")
            put("userid", session.userIdStr)
            put("type", 0)
            put("id_type", 0)
            put("p", p)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/v4/follow_list",
                params = mapOf("plat" to 1),
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf("x-router" to "relationuser.kugou.com")
            )
        )
        ensureKugouBizSuccess(response, "获取酷狗已关注歌手失败")

        val dataNode = response.bodyJson?.get("data")
        val list = when (dataNode) {
            is JsonObject -> dataNode["lists"]?.jsonArrayOrNull()
                ?: dataNode["list"]?.jsonArrayOrNull()
                ?: dataNode["info"]?.jsonArrayOrNull()
                ?: emptyList()
            is JsonArray -> dataNode
            else -> emptyList()
        }
        list.mapNotNull { elem ->
            val obj = elem.jsonObjectOrNull() ?: return@mapNotNull null
            val ext = obj["ext_params"]?.jsonObjectOrNull()
            val singerId = obj.firstNonBlank("singerid", "author_id", "userid", "id")
                .ifBlank { ext?.firstNonBlank("singerid").orEmpty() }
            if (singerId.isBlank()) return@mapNotNull null
            val authorId = obj.firstNonBlank("author_id", "singerid", "id").ifBlank { singerId }
            KugouFollowedArtistDto(
                singerId = singerId,
                authorId = authorId,
                name = obj.firstNonBlank("nickname", "name", "author_name", "singername"),
                avatarUrl = normalizeKugouCoverUrl(
                    obj.firstNonBlank("k_pic", "pic", "avatar", "sizable_avatar")
                        .ifBlank { ext?.firstNonBlank("k_pic").orEmpty() }
                ),
                intro = obj.string("intro").trim()
            )
        }
    }
}
