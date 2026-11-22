package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.AppError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

// 酷狗歌单与曲目云端操作服务
class KugouPlaylistService(
    private val config: KugouConfig,
    private val gatewayClient: KugouGatewayClient,
    private val userPreferences: UserPreferences,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val json = Json { ignoreUnknownKeys = true }

    // module/user_playlist.js
    suspend fun getUserPlaylists(
        page: Int = 1,
        pageSize: Int = 30
    ): Result<KugouUserPlaylistsPage> = runCatching {
        val session = requireActiveKugouSession(userPreferences)
        val dataObj = buildJsonObject {
            put("userid", session.userIdStr)
            put("token", session.token)
            put("total_ver", 979)
            put("type", 2)
            put("page", page)
            put("pagesize", pageSize)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/v7/get_all_list",
                params = mapOf(
                    "plat" to 1,
                    "userid" to session.userIdLong,
                    "token" to session.token
                ),
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf("x-router" to "cloudlist.service.kugou.com")
            )
        )
        ensureKugouBizSuccess(response, "获取酷狗用户歌单失败")

        val dataObjResp = response.bodyJson?.get("data")?.jsonObjectOrNull()
        val totalVer = dataObjResp?.long("total_ver") ?: 0L
        val rawList = when (val dataNode = response.bodyJson?.get("data")) {
            is JsonObject -> dataNode["info"]?.jsonArrayOrNull()
                ?: dataNode["list"]?.jsonArrayOrNull()
                ?: emptyList()
            is JsonArray -> dataNode
            else -> response.bodyJson?.get("info")?.jsonArrayOrNull() ?: emptyList()
        }

        val playlists = rawList.mapNotNull { elem ->
            val obj = elem.jsonObjectOrNull() ?: return@mapNotNull null
            parseUserPlaylist(obj, totalVer)
        }
        KugouUserPlaylistsPage(
            totalVer = totalVer,
            playlists = playlists,
            hasMore = rawList.size >= pageSize
        )
    }

    // module/playlist_detail.js
    suspend fun getPublicPlaylistDetail(
        globalCollectionId: String
    ): Result<KugouPlaylistDetailDto> = runCatching {
        val cleanId = globalCollectionId.trim()
        require(cleanId.isNotEmpty()) { "缺少歌单 global_collection_id" }
        val session = optionalKugouSession(userPreferences)
        val dataObj = buildJsonObject {
            put(
                "data",
                buildJsonArray {
                    add(buildJsonObject { put("global_collection_id", cleanId) })
                }
            )
            put("userid", session?.userIdLong ?: 0L)
            put("token", session?.token.orEmpty())
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/v3/get_list_info",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf("x-router" to "pubsongs.kugou.com")
            )
        )
        ensureKugouBizSuccess(response, "获取酷狗歌单详情失败")

        val infoObj = when (val dataNode = response.bodyJson?.get("data")) {
            is JsonArray -> dataNode.firstOrNull()?.jsonObjectOrNull()
            is JsonObject -> dataNode["info"]?.jsonArrayOrNull()?.firstOrNull()?.jsonObjectOrNull()
                ?: dataNode["list"]?.jsonArrayOrNull()?.firstOrNull()?.jsonObjectOrNull()
                ?: dataNode
            else -> null
        } ?: throw AppError.BizError(-1, "未找到酷狗歌单详情")

        parsePlaylistDetail(infoObj, cleanId)
    }

    // module/playlist_track_all.js
    suspend fun getPublicPlaylistTracks(
        globalCollectionId: String,
        page: Int = 1,
        pageSize: Int = 30
    ): Result<KugouPlaylistTracksPage> = runCatching {
        val cleanId = globalCollectionId.trim()
        require(cleanId.isNotEmpty()) { "缺少歌单 global_collection_id" }
        val safePage = page.coerceAtLeast(1)
        val beginIdx = (safePage - 1) * pageSize

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                url = "/pubsongs/v2/get_other_list_file_nofilt",
                params = mapOf(
                    "area_code" to 1,
                    "begin_idx" to beginIdx,
                    "plat" to 1,
                    "type" to 1,
                    "mode" to 1,
                    "personal_switch" to 1,
                    "extend_fields" to "abtags,hot_cmt,popularization",
                    "pagesize" to pageSize,
                    "global_collection_id" to cleanId
                ),
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureKugouBizSuccess(response, "获取酷狗公开歌单曲目失败")

        val dataObj = response.bodyJson?.get("data")?.jsonObjectOrNull()
        val rawItems = dataObj?.get("songs")?.jsonArrayOrNull()
            ?: dataObj?.get("info")?.jsonArrayOrNull()
            ?: dataObj?.get("list")?.jsonArrayOrNull()
            ?: response.bodyJson?.get("songs")?.jsonArrayOrNull()
            ?: emptyList()

        val tracks = rawItems.mapNotNull { elem ->
            elem.jsonObjectOrNull()?.let(::parseKugouTrackItem)
        }
        val totalCount = dataObj?.int("count")
            ?.takeIf { it > 0 }
            ?: dataObj?.int("total")
            ?: tracks.size
        val hasMore = rawItems.isNotEmpty() && (beginIdx + rawItems.size < totalCount || rawItems.size >= pageSize)

        KugouPlaylistTracksPage(
            globalCollectionId = cleanId,
            listVer = dataObj?.long("list_ver") ?: 0L,
            tracks = tracks,
            totalCount = totalCount,
            hasMore = hasMore
        )
    }

    // module/playlist_track_all_new.js
    suspend fun getCloudPlaylistTracks(
        listId: Long,
        type: Int = 0,
        page: Int = 1,
        pageSize: Int = 300
    ): Result<KugouPlaylistTracksPage> = runCatching {
        require(listId > 0L) { "缺少有效云歌单 listid: $listId" }
        val session = requireActiveKugouSession(userPreferences)
        val safePage = page.coerceAtLeast(1)

        val dataObj = buildJsonObject {
            put("listid", listId)
            put("userid", session.userIdStr)
            put("type", type)
            put("page", safePage)
            put("pagesize", pageSize)
            put("area_code", 1)
            put("allplatform", 1)
            put("show_cover", 1)
            put("token", session.token)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/v4/get_list_all_file_v3",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf("x-router" to "cloudlist.service.kugou.com")
            )
        )
        ensureKugouBizSuccess(response, "获取酷狗云歌单曲目失败")

        val dataObjResp = response.bodyJson?.get("data")?.jsonObjectOrNull()
        val rawItems = dataObjResp?.get("info")?.jsonArrayOrNull()
            ?: dataObjResp?.get("songs")?.jsonArrayOrNull()
            ?: dataObjResp?.get("list")?.jsonArrayOrNull()
            ?: response.bodyJson?.get("data")?.jsonArrayOrNull()
            ?: emptyList()

        val tracks = rawItems.mapNotNull { elem ->
            elem.jsonObjectOrNull()?.let(::parseKugouTrackItem)
        }
        val listVer = dataObjResp?.long("list_ver")
            ?.takeIf { it > 0L }
            ?: response.bodyJson?.long("list_ver")
            ?: 0L
        val totalCount = dataObjResp?.int("count")
            ?.takeIf { it > 0 }
            ?: tracks.size
        val hasMore = rawItems.size >= pageSize

        KugouPlaylistTracksPage(
            listId = listId,
            listVer = listVer,
            tracks = tracks,
            totalCount = totalCount,
            hasMore = hasMore
        )
    }

    // module/playlist_add.js（type=0 自建歌单，type=1 收藏歌单）
    suspend fun createPlaylist(
        name: String,
        isPrivate: Boolean = false
    ): Result<KugouUserPlaylistDto> = runCatching {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "歌单名称不能为空" }
        val session = requireActiveKugouSession(userPreferences)
        val sec = nowMs() / 1000L

        val dataObj = buildJsonObject {
            put("userid", session.userIdLong)
            put("token", session.token)
            put("total_ver", 0)
            put("name", cleanName)
            put("type", 0)
            put("source", 1)
            put("is_pri", if (isPrivate) 1 else 0)
            put("list_create_userid", session.userIdLong)
            put("list_create_gid", "")
            put("from_shupinmv", 0)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/cloudlist.service/v5/add_list",
                params = mapOf(
                    "last_time" to sec,
                    "last_area" to "gztx",
                    "userid" to session.userIdLong,
                    "token" to session.token
                ),
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureKugouBizSuccess(response, "创建酷狗歌单失败")

        val dataNode = response.bodyJson?.get("data")?.jsonObjectOrNull() ?: JsonObject(emptyMap())
        val newListId = dataNode.firstPositiveLong("listid", "list_id")
            .takeIf { it > 0L }
            ?: response.bodyJson?.firstPositiveLong("listid", "list_id")
            ?: 0L
        KugouUserPlaylistDto(
            listId = newListId,
            globalCollectionId = dataNode.firstNonBlank("global_collection_id", "list_create_gid"),
            name = dataNode.string("name").ifBlank { cleanName },
            listVer = dataNode.long("list_ver"),
            totalVer = dataNode.long("total_ver"),
            type = 0,
            isPrivate = isPrivate,
            createUserId = session.userIdLong
        )
    }

    // module/playlist_add.js（type=1 收藏公开歌单）
    suspend fun collectPlaylist(
        name: String,
        listCreateUserId: Long,
        listCreateListId: Long,
        listCreateGid: String = "",
        source: Int = 1
    ): Result<KugouUserPlaylistDto> = runCatching {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "歌单名称不能为空" }
        val session = requireActiveKugouSession(userPreferences)

        val dataObj = buildJsonObject {
            put("userid", session.userIdLong)
            put("token", session.token)
            put("total_ver", 0)
            put("name", cleanName)
            put("type", 1)
            put("source", source)
            put("is_pri", 0)
            put("list_create_userid", listCreateUserId)
            put("list_create_listid", listCreateListId)
            put("list_create_gid", listCreateGid)
            put("from_shupinmv", 0)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/cloudlist.service/v5/add_list",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureKugouBizSuccess(response, "收藏酷狗歌单失败")

        val dataNode = response.bodyJson?.get("data")?.jsonObjectOrNull() ?: JsonObject(emptyMap())
        val newListId = dataNode.firstPositiveLong("listid", "list_id")
            .takeIf { it > 0L }
            ?: listCreateListId
        KugouUserPlaylistDto(
            listId = newListId,
            globalCollectionId = dataNode.firstNonBlank("global_collection_id", "list_create_gid")
                .ifBlank { listCreateGid },
            name = dataNode.string("name").ifBlank { cleanName },
            type = 1,
            source = source,
            createUserId = listCreateUserId,
            createListId = listCreateListId,
            createGid = listCreateGid
        )
    }

    // module/playlist_del.js（请求体与响应体均走 playlistAes 加解密 + RSA p 参数）
    suspend fun deletePlaylist(listId: Long): Result<Unit> = runCatching {
        require(listId > 0L) { "无效歌单 listid: $listId" }
        config.ensureModeSynced()
        val session = requireActiveKugouSession(userPreferences)
        val sec = nowMs() / 1000L

        val payloadJson = buildJsonObject {
            put("listid", listId)
            put("total_ver", 0)
            put("type", 1)
        }.toString()
        val aes = KugouCrypto.playlistAesEncrypt(payloadJson)
        val portraitPlain = buildJsonObject {
            put("aes", aes.key)
            put("uid", session.userIdLong)
            put("token", session.token)
        }.toString()
        val p = KugouCrypto.rsaEncrypt2(portraitPlain, config.effectivePublicKeyPem()).uppercase()

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/v2/delete_list",
                params = mapOf(
                    "clienttime" to sec,
                    "key" to KugouSigner.signParamsKey(sec.toString(), mode = config.mode),
                    "last_area" to "gztx",
                    "clientver" to config.effectiveClientVer(),
                    "appid" to config.effectiveAppId(),
                    "last_time" to sec,
                    "p" to p
                ),
                data = aes.str,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf("x-router" to "cloudlist.service.kugou.com"),
                binaryResponse = true
            )
        )

        if (response.httpCode !in 200..299) {
            throw AppError.NetworkError()
        }
        val rawBytes = response.bodyBytes
        val decryptedText = if (rawBytes.isNotEmpty()) {
            val b64 = Base64.getEncoder().encodeToString(rawBytes)
            runCatching { KugouCrypto.playlistAesDecrypt(b64, aes.key) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: rawBytes.toString(Charsets.UTF_8)
        } else {
            ""
        }
        val parsedJson = runCatching { json.parseToJsonElement(decryptedText).jsonObject }.getOrNull()
        val decodedResponse = response.copy(bodyText = decryptedText, bodyJson = parsedJson)
        ensureKugouBizSuccess(decodedResponse, "删除酷狗歌单失败")
    }

    // module/playlist_update.js
    suspend fun updatePlaylist(
        listId: Long,
        totalVer: Long = 0L,
        type: Int = 0,
        name: String? = null,
        intro: String = "",
        tags: String = "",
        sort: Int = 0,
        pic: String? = null
    ): Result<Unit> = runCatching {
        require(listId > 0L) { "无效歌单 listid: $listId" }
        val bodyJson = buildJsonObject {
            put("total_ver", totalVer)
            put("listid", listId)
            put("type", type)
            put("sort", sort)
            put("tags", tags)
            put("intro", intro)
            if (!name.isNullOrBlank()) {
                put("name", name.trim())
            }
            if (!pic.isNullOrBlank()) {
                put("pic", pic.trim())
            }
        }.toString()
        val response = gatewayClient.executeCloudRequest(
            url = "/v1/modify_list",
            bodyJson = bodyJson
        )
        ensureKugouBizSuccess(response, "更新酷狗歌单信息失败")
    }

    // module/playlist_pic_upload.js
    suspend fun uploadPlaylistCover(
        imageBytes: ByteArray,
        extendName: String = ".jpg"
    ): Result<KugouCoverUploadResult> = runCatching {
        require(imageBytes.isNotEmpty()) { "封面图片字节不能为空" }
        val dateStr = Instant.ofEpochMilli(nowMs())
            .atZone(ZoneOffset.ofHours(8))
            .toLocalDate()
            .format(DateTimeFormatter.BASIC_ISO_DATE)
        val md5 = KugouCrypto.md5Hex("${dateStr}hewry678WEK23D")

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                baseUrl = "http://imgphp.kugou.com/imageupload",
                url = "/stream.php",
                params = mapOf(
                    "type" to "custom",
                    "extendName" to extendName,
                    "md5" to md5,
                    "jsonResponse" to 1
                ),
                data = imageBytes,
                headers = mapOf("Content-Type" to "application/octet-stream"),
                clearDefaultParams = true,
                clearDefaultHeaders = true,
                notSignature = true
            )
        )
        if (response.httpCode !in 200..299) {
            throw AppError.NetworkError()
        }
        val body = response.bodyJson ?: throw AppError.BizError(-1, "酷狗封面上传响应解析失败")
        val isSuccess = body.bool("IsSuccess", false)
        val fileName = body.string("FileName").trim()
        if (!isSuccess || fileName.isEmpty()) {
            val msg = body.string("Message").ifBlank { "上传酷狗歌单封面失败" }
            throw AppError.BizError(-1, msg)
        }
        KugouCoverUploadResult(
            fileName = fileName,
            picValue = "custom/$fileName"
        )
    }

    // module/playlist_tracks_add.js
    suspend fun addTracksToPlaylist(
        listId: Long,
        tracks: List<KugouPlaylistAddTrackInput>
    ): Result<List<Long>> = runCatching {
        require(listId > 0L) { "无效歌单 listid: $listId" }
        if (tracks.isEmpty()) return@runCatching emptyList()
        val session = requireActiveKugouSession(userPreferences)
        val sec = nowMs() / 1000L

        val dataObj = buildJsonObject {
            put("userid", session.userIdLong)
            put("token", session.token)
            put("listid", listId)
            put("list_ver", 0)
            put("type", 0)
            put("slow_upload", 1)
            put("scene", "false;null")
            put(
                "data",
                buildJsonArray {
                    tracks.forEach { item ->
                        val cleanHash = item.hash.trim().lowercase()
                        add(
                            buildJsonObject {
                                put("number", 1)
                                put("name", item.name.trim().ifBlank { cleanHash })
                                put("hash", cleanHash)
                                put("size", 0)
                                put("sort", 0)
                                put("timelen", 0)
                                put("bitrate", 0)
                                put("album_id", item.albumId?.trim()?.toLongOrNull() ?: 0L)
                                put("mixsongid", item.mixSongId?.takeIf { it > 0L } ?: 0L)
                            }
                        )
                    }
                }
            )
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/cloudlist.service/v6/add_song",
                params = mapOf(
                    "last_time" to sec,
                    "last_area" to "gztx",
                    "userid" to session.userIdLong,
                    "token" to session.token
                ),
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureKugouBizSuccess(response, "向酷狗歌单添加曲目失败")

        val dataNode = response.bodyJson?.get("data")
        when (dataNode) {
            is JsonArray -> dataNode.mapNotNull { elem ->
                elem.jsonObjectOrNull()?.firstPositiveLong("fileid", "file_id")?.takeIf { it > 0L }
            }
            is JsonObject -> {
                val single = dataNode.firstPositiveLong("fileid", "file_id").takeIf { it > 0L }
                val list = dataNode["info"]?.jsonArrayOrNull()?.mapNotNull { elem ->
                    elem.jsonObjectOrNull()?.firstPositiveLong("fileid", "file_id")?.takeIf { it > 0L }
                }.orEmpty()
                if (list.isNotEmpty()) list else listOfNotNull(single)
            }
            else -> emptyList()
        }
    }

    // module/playlist_tracks_del.js
    suspend fun removeTracksFromPlaylist(
        listId: Long,
        fileIds: List<Long>
    ): Result<Unit> = runCatching {
        require(listId > 0L) { "无效歌单 listid: $listId" }
        val cleanIds = fileIds.filter { it > 0L }.distinct()
        if (cleanIds.isEmpty()) return@runCatching
        val session = requireActiveKugouSession(userPreferences)

        val dataObj = buildJsonObject {
            put("listid", listId)
            put("userid", session.userIdLong)
            put(
                "data",
                buildJsonArray {
                    cleanIds.forEach { fid ->
                        add(buildJsonObject { put("fileid", fid) })
                    }
                }
            )
            put("type", 0)
            put("token", session.token)
            put("list_ver", 0)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/v4/delete_songs",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf("x-router" to "cloudlist.service.kugou.com")
            )
        )
        ensureKugouBizSuccess(response, "从酷狗歌单删除曲目失败")
    }

    // module/playlist_tracks_sort.js
    suspend fun sortPlaylistTracks(
        listId: Long,
        listVer: Long,
        items: List<KugouPlaylistTrackSortInput>,
        type: Int = 0
    ): Result<Unit> = runCatching {
        require(listId > 0L) { "无效歌单 listid: $listId" }
        if (items.isEmpty()) return@runCatching

        val bodyJson = buildJsonObject {
            put("listid", listId)
            put("list_ver", listVer)
            put("type", type)
            put(
                "data",
                buildJsonArray {
                    items.forEach { item ->
                        add(
                            buildJsonObject {
                                put("fileid", item.fileId)
                                put("sort", item.sort)
                            }
                        )
                    }
                }
            )
        }.toString()
        val response = gatewayClient.executeCloudRequest(
            url = "/v1/modify_song_sort",
            bodyJson = bodyJson
        )
        ensureKugouBizSuccess(response, "调整酷狗歌单曲目顺序失败")
    }

    private fun parseUserPlaylist(obj: JsonObject, totalVer: Long): KugouUserPlaylistDto? {
        val listId = obj.firstPositiveLong("listid", "list_id")
        if (listId <= 0L) return null
        val name = obj.string("name").trim()
        val isDef = obj.int("is_def", 0) == 1 || name == "我喜欢"
        return KugouUserPlaylistDto(
            listId = listId,
            globalCollectionId = obj.firstNonBlank("global_collection_id", "list_create_gid"),
            name = name,
            coverUrl = normalizeKugouCoverUrl(obj.firstNonBlank("pic", "cover", "img")),
            intro = obj.string("intro").trim(),
            trackCount = obj.int("count", 0),
            listVer = obj.long("list_ver", 0L),
            totalVer = obj.long("total_ver", totalVer).takeIf { it > 0L } ?: totalVer,
            type = obj.int("type", 0),
            source = obj.int("source", 1),
            isDefaultLike = isDef,
            isPrivate = obj.int("is_pri", 0) == 1,
            createUserId = obj.long("list_create_userid", 0L),
            createUsername = obj.string("list_create_username").trim(),
            createListId = obj.long("list_create_listid", 0L),
            createGid = obj.string("list_create_gid").trim(),
            sort = obj.int("sort", 0),
            tags = obj.string("tags").trim()
        )
    }

    private fun parsePlaylistDetail(obj: JsonObject, fallbackGid: String): KugouPlaylistDetailDto {
        val tagsText = obj.string("tags").trim()
        val tags = if (tagsText.isNotEmpty()) {
            tagsText.split(",", "，", " ").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            obj["tags"]?.jsonArrayOrNull()?.mapNotNull { elem ->
                elem.jsonObjectOrNull()?.string("tag_name")
                    ?: (elem as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()
            }.orEmpty().filter { it.isNotEmpty() }
        }
        return KugouPlaylistDetailDto(
            globalCollectionId = obj.firstNonBlank("global_collection_id", "specialid").ifBlank { fallbackGid },
            listId = obj.firstPositiveLong("listid", "list_id", "specialid"),
            name = obj.firstNonBlank("name", "specialname"),
            coverUrl = normalizeKugouCoverUrl(obj.firstNonBlank("pic", "imgurl", "flexible_cover")),
            intro = obj.firstNonBlank("intro", "description"),
            trackCount = obj.int("count", 0).takeIf { it > 0 } ?: obj.int("songcount", 0),
            playCount = obj.long("play_count", 0L).takeIf { it > 0L } ?: obj.long("playcount", 0L),
            createUserId = obj.firstPositiveLong("list_create_userid", "uid", "userid"),
            createUsername = obj.firstNonBlank("list_create_username", "nickname", "username"),
            createAvatarUrl = normalizeKugouCoverUrl(obj.firstNonBlank("user_avatar", "avatar")),
            listVer = obj.long("list_ver", 0L),
            tags = tags,
            isPrivate = obj.int("is_pri", 0) == 1,
            isCollected = obj.int("is_collect", 0) == 1 || obj.int("type", 0) == 1
        )
    }
}
