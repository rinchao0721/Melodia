package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

// 酷狗云端红心服务：对应「我喜欢」默认云歌单的读取、收藏与取消收藏
// 严格对照 F:\dev\KuGouMusicApi\module\{user_playlist,playlist_track_all_new,playlist_tracks_add,playlist_tracks_del}.js
open class KugouLikeService(
    private val gatewayClient: KugouGatewayClient,
    private val userPreferences: UserPreferences,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val stateMutex = Mutex()
    private val hashToFileId = ConcurrentHashMap<String, Long>()

    @Volatile
    private var cachedUserId: String? = null

    @Volatile
    private var cachedLikeListId: Long? = null

    open fun clearCache() {
        hashToFileId.clear()
        cachedUserId = null
        cachedLikeListId = null
    }

    // 拉取当前酷狗用户「我喜欢」云歌单的 hash -> fileid 映射并刷新本地缓存
    open suspend fun fetchLikedTrackMap(): Result<Map<String, Long>> = runCatchingCancellable {
        val session = requireActiveKugouSession()
        val listId = ensureLikeListId(session)

        val collected = LinkedHashMap<String, Long>()
        var page = 1
        while (page <= MAX_PAGES) {
            val dataObj = buildJsonObject {
                put("listid", listId)
                put("userid", session.userIdStr)
                put("type", 0)
                put("page", page)
                put("pagesize", TRACK_PAGE_SIZE)
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
            ensureBizSuccess(response, "获取酷狗「我喜欢」曲目失败")

            val items = extractTrackArray(response.bodyJson)
            if (items.isEmpty()) break

            for (item in items) {
                val obj = item.jsonObjectOrNull() ?: continue
                val hash = obj.string("hash")
                    .ifBlank { obj["audio_info"]?.jsonObjectOrNull()?.string("hash").orEmpty() }
                    .trim()
                    .lowercase()
                if (hash.isEmpty()) continue
                val fileId = obj.long("fileid")
                    .takeIf { it > 0L }
                    ?: obj.long("file_id").takeIf { it > 0L }
                    ?: obj.long("id")
                collected[hash] = fileId
            }

            if (items.size < TRACK_PAGE_SIZE) break
            page++
        }

        stateMutex.withLock {
            if (cachedUserId == session.userIdStr) {
                hashToFileId.clear()
                hashToFileId.putAll(collected)
            }
        }
        collected.toMap()
    }

    // 将曲目加入「我喜欢」云歌单并更新 hash -> fileid 缓存
    open suspend fun likeTrack(
        trackName: String,
        hash: String,
        albumId: String? = null,
        mixSongId: Long? = null
    ): Result<Unit> = runCatchingCancellable {
        val cleanHash = hash.trim().lowercase()
        require(cleanHash.isNotEmpty()) { "缺少歌曲 hash" }

        val session = requireActiveKugouSession()
        val listId = ensureLikeListId(session)
        val effectiveName = trackName.trim().ifBlank { cleanHash }
        val albumIdNum = albumId?.trim()?.toLongOrNull() ?: 0L
        val mixSongIdNum = mixSongId?.takeIf { it > 0L } ?: 0L

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
                    add(
                        buildJsonObject {
                            put("number", 1)
                            put("name", effectiveName)
                            put("hash", cleanHash)
                            put("size", 0)
                            put("sort", 0)
                            put("timelen", 0)
                            put("bitrate", 0)
                            put("album_id", albumIdNum)
                            put("mixsongid", mixSongIdNum)
                        }
                    )
                }
            )
        }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/cloudlist.service/v6/add_song",
                params = mapOf(
                    "last_time" to (nowMs() / 1000L),
                    "last_area" to "gztx",
                    "userid" to session.userIdLong,
                    "token" to session.token
                ),
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureBizSuccess(response, "添加到酷狗「我喜欢」失败")

        val addedFileId = extractAddedFileId(response.bodyJson)
        if (addedFileId > 0L) {
            hashToFileId[cleanHash] = addedFileId
        } else {
            hashToFileId.putIfAbsent(cleanHash, 0L)
        }
    }

    // 按缓存 fileid（缺失时先刷新「我喜欢」列表获取）从云歌单移出
    open suspend fun unlikeTrack(hash: String): Result<Unit> = runCatchingCancellable {
        val cleanHash = hash.trim().lowercase()
        require(cleanHash.isNotEmpty()) { "缺少歌曲 hash" }

        val session = requireActiveKugouSession()
        val listId = ensureLikeListId(session)

        var fileId = hashToFileId[cleanHash] ?: 0L
        if (fileId <= 0L) {
            fetchLikedTrackMap().getOrThrow()
            fileId = hashToFileId[cleanHash] ?: 0L
        }
        if (fileId <= 0L) {
            hashToFileId.remove(cleanHash)
            return@runCatchingCancellable
        }

        val dataObj = buildJsonObject {
            put("listid", listId)
            put("userid", session.userIdLong)
            put(
                "data",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("fileid", fileId)
                        }
                    )
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
        ensureBizSuccess(response, "从酷狗「我喜欢」移除失败")
        hashToFileId.remove(cleanHash)
    }

    private suspend fun requireActiveKugouSession(): KugouSession {
        val account = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
            ?: throw IllegalStateException("请先登录酷狗账号")
        val token = account.credentials["token"].orEmpty().trim()
        val userIdStr = account.credentials["userid"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: account.effectivePlatformUid.trim()
        val userIdLong = userIdStr.toLongOrNull() ?: 0L
        if (token.isEmpty() || userIdLong <= 0L) {
            throw IllegalStateException("酷狗登录凭证无效，请重新登录")
        }
        return KugouSession(
            userIdStr = userIdLong.toString(),
            userIdLong = userIdLong,
            token = token
        )
    }

    private suspend fun ensureLikeListId(session: KugouSession): Long = stateMutex.withLock {
        if (cachedUserId != session.userIdStr) {
            hashToFileId.clear()
            cachedUserId = session.userIdStr
            cachedLikeListId = null
        }
        cachedLikeListId?.takeIf { it > 0L }?.let { return@withLock it }

        var fallbackCandidate: Long? = null
        var page = 1
        while (page <= MAX_PLAYLIST_PAGES) {
            val dataObj = buildJsonObject {
                put("userid", session.userIdStr)
                put("token", session.token)
                put("total_ver", 979)
                put("type", 2)
                put("page", page)
                put("pagesize", PLAYLIST_PAGE_SIZE)
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
            ensureBizSuccess(response, "获取酷狗歌单列表失败")

            val playlists = extractPlaylistArray(response.bodyJson)
            if (playlists.isEmpty()) break

            val parsed = playlists.mapNotNull { elem ->
                val obj = elem.jsonObjectOrNull() ?: return@mapNotNull null
                val listId = obj.long("listid").takeIf { it > 0L } ?: obj.long("list_id")
                if (listId <= 0L) return@mapNotNull null
                PlaylistCandidate(
                    listId = listId,
                    name = obj.string("name").trim(),
                    isDef = obj.int("is_def", 0),
                    source = obj.int("source", 0),
                    type = obj.int("type", -1),
                    createUserId = obj.long("list_create_userid")
                )
            }

            // 优先匹配默认「我喜欢」歌单（is_def == 1 或 名称含「我喜欢」）
            val exactLike = parsed
                .filter { it.name == "我喜欢" }
                .sortedWith(
                    compareByDescending<PlaylistCandidate> { it.isDef == 1 }
                        .thenByDescending { it.createUserId == session.userIdLong }
                        .thenByDescending { it.source == 1 }
                )
                .firstOrNull()
            if (exactLike != null) {
                cachedLikeListId = exactLike.listId
                return@withLock exactLike.listId
            }

            if (fallbackCandidate == null) {
                fallbackCandidate = parsed.firstOrNull {
                    it.isDef == 1 || it.name.contains("我喜欢")
                }?.listId
            }

            if (playlists.size < PLAYLIST_PAGE_SIZE) break
            page++
        }

        val resolved = fallbackCandidate ?: throw AppError.BizError(-1, "未找到酷狗「我喜欢」默认歌单")
        cachedLikeListId = resolved
        resolved
    }

    private fun extractPlaylistArray(body: JsonObject?): List<JsonElement> {
        val dataNode = body?.get("data")
        return when (dataNode) {
            is JsonObject -> dataNode["info"]?.jsonArrayOrNull()
                ?: dataNode["list"]?.jsonArrayOrNull()
                ?: emptyList()
            is JsonArray -> dataNode
            else -> body?.get("info")?.jsonArrayOrNull() ?: emptyList()
        }
    }

    private fun extractTrackArray(body: JsonObject?): List<JsonElement> {
        val dataNode = body?.get("data")
        return when (dataNode) {
            is JsonObject -> dataNode["info"]?.jsonArrayOrNull()
                ?: dataNode["songs"]?.jsonArrayOrNull()
                ?: dataNode["list"]?.jsonArrayOrNull()
                ?: emptyList()
            is JsonArray -> dataNode
            else -> body?.get("info")?.jsonArrayOrNull() ?: emptyList()
        }
    }

    private fun extractAddedFileId(body: JsonObject?): Long {
        val dataNode = body?.get("data") ?: return 0L
        return when (dataNode) {
            is JsonArray -> dataNode.firstOrNull()?.jsonObjectOrNull()?.let { obj ->
                obj.long("fileid").takeIf { it > 0L } ?: obj.long("file_id")
            } ?: 0L
            is JsonObject -> dataNode.long("fileid")
                .takeIf { it > 0L }
                ?: dataNode.long("file_id").takeIf { it > 0L }
                ?: dataNode["info"]?.jsonArrayOrNull()?.firstOrNull()?.jsonObjectOrNull()?.let { obj ->
                    obj.long("fileid").takeIf { it > 0L } ?: obj.long("file_id")
                } ?: 0L
            else -> 0L
        }
    }

    private fun ensureBizSuccess(response: KugouRawResponse, fallbackMessage: String) {
        ensureKugouBizSuccess(response, fallbackMessage)
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String, defaultValue: Int = 0): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: defaultValue

    private fun JsonObject.long(key: String, defaultValue: Long = 0L): Long =
        (this[key] as? JsonPrimitive)?.longOrNull ?: defaultValue

    private data class KugouSession(
        val userIdStr: String,
        val userIdLong: Long,
        val token: String
    )

    private data class PlaylistCandidate(
        val listId: Long,
        val name: String,
        val isDef: Int,
        val source: Int,
        val type: Int,
        val createUserId: Long
    )

    private companion object {
        private const val PLAYLIST_PAGE_SIZE = 30
        private const val TRACK_PAGE_SIZE = 300
        private const val MAX_PLAYLIST_PAGES = 10
        private const val MAX_PAGES = 50
    }
}
