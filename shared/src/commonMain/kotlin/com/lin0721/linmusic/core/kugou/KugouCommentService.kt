package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.AppError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// 酷狗评论服务（单曲/歌单/专辑评论、楼层回复及发评/回复/删评）
class KugouCommentService(
    private val config: KugouConfig,
    private val deviceStore: KugouDeviceStore,
    private val gatewayClient: KugouGatewayClient,
    private val userPreferences: UserPreferences,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    // module/comment_music.js
    suspend fun getMusicComments(
        mixSongId: Long,
        page: Int = 1,
        pageSize: Int = 30
    ): Result<KugouCommentPage> = runCatching {
        require(mixSongId > 0L) { "缺少有效 mixsongid: $mixSongId" }
        val safePage = page.coerceAtLeast(1)
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/mcomment/v1/cmtlist",
                params = mapOf(
                    "ver" to 6,
                    "mixsongid" to mixSongId,
                    "need_show_image" to 1,
                    "p" to safePage,
                    "pagesize" to pageSize,
                    "show_classify" to 1,
                    "show_hotword_list" to 1,
                    "extdata" to "0",
                    "code" to KugouCommentResourceType.SONG.code
                ),
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureCommentBizSuccess(response, "获取酷狗歌曲评论失败")
        parseCommentPage(response.bodyJson, safePage, pageSize)
    }

    // module/comment_playlist.js
    suspend fun getPlaylistComments(
        playlistId: String,
        page: Int = 1,
        pageSize: Int = 30
    ): Result<KugouCommentPage> = runCatching {
        val cleanId = playlistId.trim()
        require(cleanId.isNotEmpty()) { "缺少歌单评论 ID" }
        val safePage = page.coerceAtLeast(1)
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/m.comment.service/v1/cmtlist",
                params = mapOf(
                    "childrenid" to cleanId,
                    "need_show_image" to 1,
                    "p" to safePage,
                    "pagesize" to pageSize,
                    "show_classify" to 1,
                    "show_hotword_list" to 1,
                    "code" to KugouCommentResourceType.PLAYLIST.code,
                    "content_type" to 0,
                    "tag" to 5
                ),
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureCommentBizSuccess(response, "获取酷狗歌单评论失败")
        parseCommentPage(response.bodyJson, safePage, pageSize, fallbackSpecialId = cleanId)
    }

    // module/comment_album.js
    suspend fun getAlbumComments(
        albumId: String,
        page: Int = 1,
        pageSize: Int = 30
    ): Result<KugouCommentPage> = runCatching {
        val cleanId = albumId.trim()
        require(cleanId.isNotEmpty()) { "缺少专辑评论 ID" }
        val safePage = page.coerceAtLeast(1)
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/m.comment.service/v1/cmtlist",
                params = mapOf(
                    "childrenid" to cleanId,
                    "need_show_image" to 1,
                    "p" to safePage,
                    "pagesize" to pageSize,
                    "show_classify" to 1,
                    "show_hotword_list" to 1,
                    "code" to KugouCommentResourceType.ALBUM.code
                ),
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureCommentBizSuccess(response, "获取酷狗专辑评论失败")
        parseCommentPage(response.bodyJson, safePage, pageSize, fallbackSpecialId = cleanId)
    }

    // module/comment_floor.js
    suspend fun getFloorComments(
        resourceType: KugouCommentResourceType,
        specialId: String,
        tid: String,
        mixSongId: Long? = null,
        page: Int = 1,
        pageSize: Int = 30
    ): Result<KugouCommentPage> = runCatching {
        val cleanSpecialId = specialId.trim()
        val cleanTid = tid.trim()
        require(cleanSpecialId.isNotEmpty()) { "缺少评论资源 special_id" }
        require(cleanTid.isNotEmpty()) { "缺少楼层主评论 tid" }
        val safePage = page.coerceAtLeast(1)
        val url = when (resourceType) {
            KugouCommentResourceType.PLAYLIST,
            KugouCommentResourceType.ALBUM -> "/m.comment.service/v1/hot_replylist"
            KugouCommentResourceType.SONG -> "/mcomment/v1/hot_replylist"
        }
        val params = LinkedHashMap<String, Any?>().apply {
            put("childrenid", cleanSpecialId)
            put("need_show_image", 1)
            put("p", safePage)
            put("pagesize", pageSize)
            put("show_classify", 1)
            put("show_hotword_list", 1)
            put("code", resourceType.code)
            put("tid", cleanTid)
            if (mixSongId != null && mixSongId > 0L) {
                put("mixsongid", mixSongId)
            }
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = url,
                params = params,
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureCommentBizSuccess(response, "获取酷狗楼层回复失败")
        parseCommentPage(response.bodyJson, safePage, pageSize, fallbackSpecialId = cleanSpecialId)
    }

    // module/_comment.js + comment_{music,playlist,album}_send.js
    suspend fun sendComment(
        resourceType: KugouCommentResourceType,
        content: String,
        specialId: String? = null,
        mixSongId: Long? = null,
        resourceName: String? = null
    ): Result<KugouCommentItemDto?> = runCatching {
        config.ensureModeSynced()
        val cleanContent = content.trim()
        require(cleanContent.isNotEmpty()) { "评论内容不能为空" }
        val session = requireActiveKugouSession(userPreferences)
        val device = deviceStore.getOrCreateDeviceIdentity()

        var resolvedId = specialId?.trim().orEmpty()
        var resolvedName = resourceName?.trim().orEmpty()
        if (resolvedId.isEmpty() || resolvedName.isEmpty()) {
            val lookup = when (resourceType) {
                KugouCommentResourceType.SONG -> mixSongId?.takeIf { it > 0L }
                    ?.let { getMusicComments(it, page = 1, pageSize = 1).getOrNull() }
                KugouCommentResourceType.PLAYLIST -> resolvedId.takeIf { it.isNotEmpty() }
                    ?.let { getPlaylistComments(it, page = 1, pageSize = 1).getOrNull() }
                KugouCommentResourceType.ALBUM -> resolvedId.takeIf { it.isNotEmpty() }
                    ?.let { getAlbumComments(it, page = 1, pageSize = 1).getOrNull() }
            }
            if (resolvedId.isEmpty()) resolvedId = lookup?.specialId.orEmpty()
            if (resolvedName.isEmpty()) resolvedName = lookup?.specialName.orEmpty()
        }
        require(resolvedId.isNotEmpty()) { "无法解析评论资源 special_id" }

        val dataJson = buildJsonObject {
            put(
                "data",
                buildJsonObject {
                    put("content", cleanContent)
                    if (mixSongId != null && mixSongId > 0L) {
                        put("album_audio_id", mixSongId)
                    }
                    put("images", buildJsonArray { })
                }
            )
        }.toString()

        val sec = nowMs() / 1000L
        val key = KugouSigner.signParamsKey(
            data = "${sec}${device.mid}$dataJson",
            mode = config.mode
        )
        val params = LinkedHashMap<String, Any?>().apply {
            put("r", "commentsv3/add")
            put("code", resourceType.code)
            put("childrenid", resolvedId)
            if (resolvedName.isNotEmpty()) put("childrenname", resolvedName)
            put("kugouid", session.userIdStr)
            put("ver", 6)
            put("clienttoken", session.token)
            put("appid", config.effectiveAppId())
            put("clientver", config.effectiveClientVer())
            put("mid", device.mid)
            put("clienttime", sec)
            put("key", key)
            put("uuid", "-")
            put("dfid", device.effectiveDfid)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/index.php",
                params = params,
                data = dataJson,
                headers = mapOf(
                    "x-router" to COMMENT_HOST,
                    "Content-Type" to "application/json; charset=UTF-8"
                ),
                clearDefaultParams = true,
                notSignature = true
            )
        )
        ensureCommentBizSuccess(response, "发表酷狗评论失败")
        extractSingleComment(response.bodyJson, resolvedId, resolvedName)
    }

    // module/_comment.js + comment_floor_send.js
    suspend fun replyComment(
        resourceType: KugouCommentResourceType,
        specialId: String,
        tid: String,
        content: String,
        pid: String = "0",
        replyUserName: String? = null,
        replyContent: String? = null,
        resourceName: String? = null
    ): Result<KugouCommentItemDto?> = runCatching {
        config.ensureModeSynced()
        val cleanContent = content.trim()
        val cleanSpecialId = specialId.trim()
        val cleanTid = tid.trim()
        require(cleanContent.isNotEmpty()) { "回复内容不能为空" }
        require(cleanSpecialId.isNotEmpty()) { "缺少评论资源 special_id" }
        require(cleanTid.isNotEmpty()) { "缺少主评论 tid" }

        val session = requireActiveKugouSession(userPreferences)
        val device = deviceStore.getOrCreateDeviceIdentity()

        var resolvedName = resourceName?.trim().orEmpty()
        if (resolvedName.isEmpty()) {
            resolvedName = getFloorComments(
                resourceType = resourceType,
                specialId = cleanSpecialId,
                tid = cleanTid,
                page = 1,
                pageSize = 1
            ).getOrNull()?.specialName.orEmpty()
        }

        val cleanPid = pid.trim().ifBlank { "0" }
        val isTopLevelReply = if (cleanPid == "0") 1 else 0
        val finalContent = if (!replyUserName.isNullOrBlank() &&
            !replyContent.isNullOrBlank() &&
            !cleanContent.contains("//@")
        ) {
            "$cleanContent//@${replyUserName.trim()}:${replyContent.trim()}"
        } else {
            cleanContent
        }

        val sec = nowMs() / 1000L
        val key = KugouSigner.signParamsKey(data = "${sec}${device.mid}", mode = config.mode)
        val params = LinkedHashMap<String, Any?>().apply {
            put("r", "commentsv2/reply")
            put("code", resourceType.code)
            put("childrenid", cleanSpecialId)
            if (resolvedName.isNotEmpty()) put("childrenname", resolvedName)
            put("kugouid", session.userIdStr)
            put("ver", 6)
            put("clienttoken", session.token)
            put("appid", config.effectiveAppId())
            put("clientver", config.effectiveClientVer())
            put("mid", device.mid)
            put("clienttime", sec)
            put("key", key)
            put("uuid", "-")
            put("dfid", device.effectiveDfid)
            put("content", finalContent)
            put("tid", cleanTid)
            put("is_t", isTopLevelReply)
            put("pid", cleanPid)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/index.php",
                params = params,
                headers = mapOf("x-router" to COMMENT_HOST),
                clearDefaultParams = true,
                notSignature = true
            )
        )
        ensureCommentBizSuccess(response, "回复酷狗评论失败")
        extractSingleComment(response.bodyJson, cleanSpecialId, resolvedName)
    }

    // module/_comment.js + comment_music_del.js
    suspend fun deleteComment(
        resourceType: KugouCommentResourceType,
        cid: String,
        specialId: String? = null,
        tid: String? = null,
        mixSongId: Long? = null
    ): Result<Unit> = runCatching {
        config.ensureModeSynced()
        val cleanCid = cid.trim()
        require(cleanCid.isNotEmpty()) { "缺少待删除评论 cid" }
        val session = requireActiveKugouSession(userPreferences)
        val device = deviceStore.getOrCreateDeviceIdentity()

        var resolvedId = specialId?.trim().orEmpty()
        if (resolvedId.isEmpty() && mixSongId != null && mixSongId > 0L) {
            resolvedId = getMusicComments(mixSongId, page = 1, pageSize = 1).getOrNull()?.specialId.orEmpty()
        }
        require(resolvedId.isNotEmpty()) { "无法解析评论资源 special_id" }

        val sec = nowMs() / 1000L
        val key = KugouSigner.signParamsKey(data = "${sec}${device.mid}", mode = config.mode)
        val params = LinkedHashMap<String, Any?>().apply {
            put("r", "commentsv2/delcomment")
            put("code", resourceType.code)
            put("childrenid", resolvedId)
            put("cid", cleanCid)
            if (!tid.isNullOrBlank()) put("tid", tid.trim())
            put("kugouid", session.userIdStr)
            put("ver", 6)
            put("clienttoken", session.token)
            put("appid", config.effectiveAppId())
            put("clientver", config.effectiveClientVer())
            put("mid", device.mid)
            put("clienttime", sec)
            put("key", key)
            put("uuid", "-")
            put("dfid", device.effectiveDfid)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                url = "/index.php",
                params = params,
                headers = mapOf("x-router" to COMMENT_HOST),
                clearDefaultParams = true,
                notSignature = true
            )
        )
        ensureCommentBizSuccess(response, "删除酷狗评论失败")
    }

    private fun ensureCommentBizSuccess(response: KugouRawResponse, fallbackMessage: String) {
        ensureKugouBizSuccess(response, fallbackMessage)
        val errCode = response.bodyJson?.int("err_code", 0) ?: 0
        if (errCode != 0) {
            val msg = response.bodyJson?.firstNonBlank("err_msg", "msg", "error_msg")
                .orEmpty()
                .ifBlank { fallbackMessage }
            throw AppError.BizError(errCode, msg)
        }
    }

    private fun parseCommentPage(
        body: JsonObject?,
        page: Int,
        pageSize: Int,
        fallbackSpecialId: String = ""
    ): KugouCommentPage {
        val root = body?.get("data")?.jsonObjectOrNull() ?: body ?: JsonObject(emptyMap())
        val listNode = root["list"]?.jsonArrayOrNull()
            ?: body?.get("list")?.jsonArrayOrNull()
            ?: emptyList()
        val weightNode = root["weightList"]?.jsonArrayOrNull()
            ?: root["hot_list"]?.jsonArrayOrNull()
            ?: body?.get("weightList")?.jsonArrayOrNull()
            ?: emptyList()

        val comments = listNode.mapNotNull { it.jsonObjectOrNull()?.let(::parseCommentItem) }
        val weightComments = weightNode.mapNotNull { it.jsonObjectOrNull()?.let(::parseCommentItem) }
        val firstRaw = listNode.firstOrNull()?.jsonObjectOrNull() ?: weightNode.firstOrNull()?.jsonObjectOrNull()

        val resolvedSpecialId = root.firstNonBlank("childrenid", "special_child_id", "special_id")
            .ifBlank { body?.firstNonBlank("childrenid", "special_child_id", "special_id").orEmpty() }
            .ifBlank { firstRaw?.firstNonBlank("special_child_id", "childrenid", "special_id").orEmpty() }
            .ifBlank { fallbackSpecialId }
        val resolvedSpecialName = root.firstNonBlank("special_child_name", "special_name", "song_show_text")
            .ifBlank { body?.firstNonBlank("special_child_name", "special_name", "song_show_text").orEmpty() }
            .ifBlank { firstRaw?.firstNonBlank("special_child_name", "special_name", "song_show_text").orEmpty() }

        val total = root.int("count", 0)
            .takeIf { it > 0 }
            ?: root.int("total", 0).takeIf { it > 0 }
            ?: body?.int("count", 0)?.takeIf { it > 0 }
            ?: body?.int("total", 0)?.takeIf { it > 0 }
            ?: comments.size
        val hasMore = comments.isNotEmpty() && ((page - 1) * pageSize + comments.size < total || comments.size >= pageSize)

        return KugouCommentPage(
            comments = comments,
            weightComments = weightComments,
            totalCount = total,
            hasMore = hasMore,
            specialId = resolvedSpecialId,
            specialName = resolvedSpecialName
        )
    }

    private fun parseCommentItem(obj: JsonObject): KugouCommentItemDto? {
        val id = obj.firstNonBlank("id", "cid", "comment_id")
        if (id.isBlank()) return null
        val tid = obj.firstNonBlank("tid", "id").ifBlank { id }
        val userObj = obj["user"]?.jsonObjectOrNull()
        val replyObj = obj["pcomment"]?.jsonObjectOrNull()
            ?: obj["reply_info"]?.jsonObjectOrNull()
            ?: (obj["replied"] as? JsonArray)?.firstOrNull()?.jsonObjectOrNull()
        val likeObj = obj["like"]?.jsonObjectOrNull()
        val likeCount = likeObj?.int("count", 0)
            ?: obj.int("like_count", 0).takeIf { it > 0 }
            ?: obj.int("likenum", 0)
        val replyCount = obj.int("reply_num", 0).takeIf { it > 0 } ?: obj.int("replynum", 0)
        val addTimeText = obj.firstNonBlank("addtime", "add_time", "time_str")
        val addTimeMs = obj.firstPositiveLong("addtime_ms", "time")
            .takeIf { it > 0L }
            ?: parseKugouDateTimeMs(addTimeText)

        return KugouCommentItemDto(
            id = id,
            tid = tid,
            specialId = obj.firstNonBlank("special_child_id", "childrenid"),
            specialName = obj.firstNonBlank("special_child_name", "song_show_text"),
            userId = obj.firstNonBlank("user_id", "userid", "kugouid")
                .ifBlank { userObj?.firstNonBlank("id", "userid").orEmpty() },
            userName = obj.firstNonBlank("user_name", "username", "nickname")
                .ifBlank { userObj?.firstNonBlank("name", "nickname").orEmpty() },
            userAvatar = normalizeKugouCoverUrl(
                obj.firstNonBlank("user_pic", "avatar", "user_avatar")
                    .ifBlank { userObj?.firstNonBlank("pic", "avatar").orEmpty() }
            ),
            content = obj.string("content"),
            addTimeText = addTimeText,
            addTimeMs = addTimeMs,
            likeCount = likeCount,
            replyCount = replyCount,
            parentCommentId = obj.firstNonBlank("pid", "parent_id").takeIf { it.isNotBlank() && it != "0" },
            repliedUserName = replyObj?.firstNonBlank("user_name", "nickname", "puser")
                ?.ifBlank { null }
                ?: obj.firstNonBlank("puser").ifBlank { null },
            repliedContent = replyObj?.firstNonBlank("content", "pcontent")
                ?.ifBlank { null }
                ?: obj.firstNonBlank("pcontent").ifBlank { null }
        )
    }

    private fun extractSingleComment(
        body: JsonObject?,
        specialId: String,
        specialName: String
    ): KugouCommentItemDto? {
        val dataObj = body?.get("data")?.jsonObjectOrNull() ?: body ?: return null
        val parsed = parseCommentItem(dataObj)
        if (parsed != null) return parsed
        val id = dataObj.firstNonBlank("id", "cid", "comment_id")
        if (id.isBlank()) return null
        return KugouCommentItemDto(
            id = id,
            tid = dataObj.firstNonBlank("tid", "id").ifBlank { id },
            specialId = specialId,
            specialName = specialName,
            content = dataObj.string("content")
        )
    }

    private companion object {
        private const val COMMENT_HOST = "m.comment.service.kugou.com"
    }
}
