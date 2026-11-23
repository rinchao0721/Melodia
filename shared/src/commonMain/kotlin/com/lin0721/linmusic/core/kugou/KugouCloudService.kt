package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.AppError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Base64

// 酷狗音乐云盘服务（列表查询、直链获取、文件删除）
class KugouCloudService(
    private val config: KugouConfig,
    private val deviceStore: KugouDeviceStore,
    private val gatewayClient: KugouGatewayClient,
    private val userPreferences: UserPreferences,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // module/user_cloud.js（mcloudservice.kugou.com AES+RSA 二进制通道）
    suspend fun getCloudList(
        page: Int = 1,
        pageSize: Int = 30
    ): Result<KugouCloudListPage> = runCatchingCancellable {
        config.ensureModeSynced()
        val session = requireActiveKugouSession(userPreferences)
        val device = deviceStore.getOrCreateDeviceIdentity()
        val safePage = page.coerceAtLeast(1)
        val clientTime = nowMs() / 1000L

        val payloadJson = buildJsonObject {
            put("page", safePage)
            put("pagesize", pageSize)
            put("getkmr", 1)
        }.toString()
        val response = executeMcloudEncryptedPost(
            path = "/v1/get_list",
            payloadJson = payloadJson,
            clientTime = clientTime,
            mid = device.mid,
            userIdLong = session.userIdLong,
            token = session.token,
            fallbackErrorMsg = "获取酷狗云盘列表失败"
        )
        parseCloudListPage(response.bodyJson, safePage, pageSize)
    }

    // module/user_cloud_url.js
    suspend fun getCloudPlayUrl(
        hash: String,
        albumAudioId: Long = 0L,
        audioId: Long = 0L,
        name: String = ""
    ): Result<KugouCloudUrlResult> = runCatchingCancellable {
        val cleanHash = hash.trim().lowercase()
        require(cleanHash.isNotEmpty()) { "缺少云盘歌曲 hash" }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                url = "/bsstrackercdngz/v2/query_musicclound_url",
                params = mapOf(
                    "hash" to cleanHash,
                    "ssa_flag" to "is_fromtrack",
                    "version" to "20102",
                    "ssl" to 0,
                    "album_audio_id" to albumAudioId,
                    "pid" to CLOUD_PID,
                    "audio_id" to audioId,
                    "kv_id" to 2,
                    "key" to KugouSigner.signCloudKey(cleanHash, CLOUD_PID.toString()),
                    "bucket" to "musicclound",
                    "name" to name,
                    "with_res_tag" to 0
                ),
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureKugouBizSuccess(response, "获取酷狗云盘播放链接失败")
        val root = response.bodyJson ?: throw AppError.BizError(-1, "酷狗云盘直链响应为空")
        val dataObj = root["data"]?.jsonObjectOrNull() ?: root

        val primaryUrl = extractUrlList(dataObj, root, "url", "play_url", "playUrl")
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val backups = extractUrlList(dataObj, root, "backup_url", "backupUrl", "urls")
            .filter { it.isNotBlank() && it != primaryUrl }

        if (primaryUrl.isBlank() && backups.isEmpty()) {
            throw AppError.BizError(-1, "酷狗云盘未返回有效播放直链")
        }

        val resolvedUrl = primaryUrl.ifBlank { backups.first() }
        KugouCloudUrlResult(
            hash = cleanHash,
            url = resolvedUrl,
            backupUrls = backups,
            extName = dataObj.firstNonBlank("extname", "extName", "ext"),
            fileSize = dataObj.firstPositiveLong("filesize", "fileSize", "size"),
            bitRate = dataObj.int("bitrate", 0).takeIf { it > 0 } ?: dataObj.int("bitRate", 0),
            durationMs = normalizeKugouDurationMs(dataObj.firstPositiveLong("timelen", "timelength", "duration"))
        )
    }

    // module/user_cloud_del.js
    suspend fun deleteCloudFiles(items: List<KugouCloudDeleteItem>): Result<Unit> = runCatchingCancellable {
        require(items.isNotEmpty()) { "请提供要删除的云盘文件" }
        config.ensureModeSynced()
        val session = requireActiveKugouSession(userPreferences)
        val device = deviceStore.getOrCreateDeviceIdentity()
        val clientTime = nowMs() / 1000L

        val payloadJson = buildJsonObject {
            put(
                "data",
                buildJsonArray {
                    items.forEach { item ->
                        add(
                            buildJsonObject {
                                put("kv_id", item.kvId)
                                put("album_audio_id", item.albumAudioId)
                            }
                        )
                    }
                }
            )
        }.toString()

        executeMcloudEncryptedPost(
            path = "/v1/del_files",
            payloadJson = payloadJson,
            clientTime = clientTime,
            mid = device.mid,
            userIdLong = session.userIdLong,
            token = session.token,
            fallbackErrorMsg = "删除酷狗云盘歌曲失败"
        )
    }

    private suspend fun executeMcloudEncryptedPost(
        path: String,
        payloadJson: String,
        clientTime: Long,
        mid: String,
        userIdLong: Long,
        token: String,
        fallbackErrorMsg: String
    ): KugouRawResponse {
        val aes = KugouCrypto.playlistAesEncrypt(payloadJson)
        val portraitPlain = buildJsonObject {
            put("aes", aes.key)
            put("uid", userIdLong)
            put("token", token)
        }.toString()
        val p = KugouCrypto.rsaEncrypt2(portraitPlain, config.effectivePublicKeyPem()).uppercase()
        val appId = config.effectiveAppId()
        val clientVer = config.effectiveClientVer()
        val rawBodyBytes = Base64.getDecoder().decode(aes.str)

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                baseUrl = MCLOUD_BASE_URL,
                url = path,
                params = mapOf(
                    "clienttime" to clientTime,
                    "mid" to mid,
                    "key" to KugouSigner.signParamsKey(
                        data = clientTime.toString(),
                        appid = appId,
                        clientver = clientVer,
                        mode = config.mode
                    ),
                    "clientver" to clientVer,
                    "appid" to appId,
                    "p" to p
                ),
                data = rawBodyBytes,
                encryptType = KugouEncryptType.ANDROID,
                clearDefaultParams = true,
                notSignature = true,
                binaryResponse = true
            )
        )
        if (response.httpCode !in 200..299) {
            throw AppError.NetworkError()
        }

        val rawBytes = response.bodyBytes
        val decryptedText = if (rawBytes.isNotEmpty()) {
            val b64 = Base64.getEncoder().encodeToString(rawBytes)
            runCatchingCancellable { KugouCrypto.playlistAesDecrypt(b64, aes.key) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: rawBytes.toString(Charsets.UTF_8)
        } else {
            ""
        }
        val parsedJson = runCatchingCancellable { json.parseToJsonElement(decryptedText).jsonObject }.getOrNull()
        val decoded = response.copy(bodyText = decryptedText, bodyJson = parsedJson)
        ensureKugouBizSuccess(decoded, fallbackErrorMsg)
        return decoded
    }

    private fun parseCloudListPage(root: JsonObject?, page: Int, pageSize: Int): KugouCloudListPage {
        val dataNode = root?.get("data")
        val dataObj = dataNode?.jsonObjectOrNull() ?: root
        val rawList = when (dataNode) {
            is JsonArray -> dataNode
            is JsonObject -> dataNode["list"]?.jsonArrayOrNull()
                ?: dataNode["info"]?.jsonArrayOrNull()
                ?: dataNode["files"]?.jsonArrayOrNull()
                ?: emptyList()
            else -> root?.get("list")?.jsonArrayOrNull()
                ?: root?.get("info")?.jsonArrayOrNull()
                ?: emptyList()
        }

        val items = rawList.mapNotNull { elem ->
            val obj = elem.jsonObjectOrNull() ?: return@mapNotNull null
            val hash = obj.firstNonBlank("hash", "filehash", "file_hash", "Hash").lowercase()
            if (hash.isBlank()) return@mapNotNull null
            val kvId = obj.firstPositiveLong("kv_id", "kvid", "fileid", "file_id", "id")
            val rawName = stripKugouEmTags(obj.firstNonBlank("name", "filename", "file_name", "songname", "audio_name"))
            val rawSinger = stripKugouEmTags(obj.firstNonBlank("author_name", "singername", "singer", "artist"))
            val (songName, singerName) = if (rawSinger.isBlank() && rawName.contains(" - ")) {
                val parts = rawName.split(" - ", limit = 2)
                parts[1].trim() to parts[0].trim()
            } else {
                rawName to rawSinger
            }
            KugouCloudFileDto(
                kvId = kvId,
                fileId = obj.firstPositiveLong("fileid", "file_id", "kv_id", "id").takeIf { it > 0L } ?: kvId,
                hash = hash,
                name = songName.ifBlank { hash },
                singerName = singerName,
                albumName = stripKugouEmTags(obj.firstNonBlank("album_name", "albumname")),
                albumId = obj.firstNonBlank("album_id"),
                albumAudioId = obj.firstPositiveLong("album_audio_id", "mixsongid"),
                audioId = obj.firstPositiveLong("audio_id"),
                sizeBytes = obj.firstPositiveLong("filesize", "file_size", "size"),
                durationMs = normalizeKugouDurationMs(obj.firstPositiveLong("timelen", "timelength", "duration")),
                bitrate = obj.int("bitrate", 0),
                extName = obj.firstNonBlank("extname", "ext", "format"),
                coverUrl = normalizeKugouCoverUrl(obj.firstNonBlank("cover", "pic", "sizable_cover")),
                addTimeMs = parseKugouDateTimeMs(obj.firstNonBlank("addtime", "add_time", "create_time"))
            )
        }

        val totalCount = dataObj?.int("count", 0)?.takeIf { it > 0 }
            ?: dataObj?.int("total", 0)?.takeIf { it > 0 }
            ?: items.size
        val usedBytes = dataObj?.firstPositiveLong("used_size", "usedSize", "use_size")
            ?: items.sumOf { it.sizeBytes }
        val maxBytes = dataObj?.firstPositiveLong("total_size", "max_size", "totalSize", "maxSize") ?: 0L
        val hasMore = items.isNotEmpty() && (page * pageSize < totalCount || items.size >= pageSize)

        return KugouCloudListPage(
            items = items,
            quota = KugouCloudQuotaDto(usedBytes = usedBytes, maxBytes = maxBytes, totalCount = totalCount),
            page = page,
            hasMore = hasMore
        )
    }

    private fun extractUrlList(primary: JsonObject?, root: JsonObject?, vararg keys: String): List<String> {
        val result = mutableListOf<String>()
        for (obj in listOfNotNull(primary, root)) {
            for (key in keys) {
                when (val node = obj[key]) {
                    is JsonPrimitive -> node.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let(result::add)
                    is JsonArray -> node.forEach { elem ->
                        (elem as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let(result::add)
                    }
                    else -> Unit
                }
            }
        }
        return result
    }

    companion object {
        const val MCLOUD_BASE_URL = "https://mcloudservice.kugou.com"
        const val CLOUD_PID = 20026
    }
}
