package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.platform.UnifiedAudioQuality
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
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "KugouPlaybackService"

// 酷狗播放直链、音质权益、音频元信息、KRC/LRC 歌词与播放历史服务
// 所有端点参数、请求头与签名逻辑严格对照 F:\dev\KuGouMusicApi\module\ 对应模块
class KugouPlaybackService(
    private val config: KugouConfig,
    private val deviceStore: KugouDeviceStore,
    private val gatewayClient: KugouGatewayClient,
    private val userPreferences: UserPreferences,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    // 老版本持久化或短信登录缺少 auth 时，播前按需校验补齐（失败冷却防抖，不阻塞播放）
    private val verifyPlaybackAuth: (suspend () -> Result<String>)? = null
) {
    private val authVerifyMutex = Mutex()
    private val authVerifyLastAttemptByUid = ConcurrentHashMap<String, Long>()

    companion object {
        private const val AUTH_VERIFY_COOLDOWN_MS = 5 * 60_000L
        private val PRIVILEGE_QUALITIES = listOf(
            "128", "320", "flac", "high", "viper_atmos", "viper_tape", "viper_clear", "super", "multitrack"
        )
        private val PRIV_URL_QUALITIES = listOf(
            "128", "320", "flac", "high", "multitrack", "viper_atmos", "viper_tape", "viper_clear", "super"
        )
        private val QUALITY_ORDER = listOf(
            "128", "320", "flac", "high", "super", "viper_clear", "viper_tape", "viper_atmos", "multitrack"
        )
        private val QUALITY_LEVEL_MAP: Map<String, List<Int>> = mapOf(
            "128" to listOf(1, 2),
            "320" to listOf(4),
            "flac" to listOf(5),
            "high" to listOf(6),
            "super" to listOf(7)
        )
        private val QUALITY_VIP_TYPE: Map<String, String> = mapOf(
            "viper_tape" to "suvip",
            "viper_clear" to "suvip",
            "viper_atmos" to "suvip"
        )
    }

    // 对照 module/privilege_lite.js：批量查询音质权益、album_audio_id/mixsongid 及 relate_goods 各档位 hash
    suspend fun getPrivilegeLite(
        hashes: List<String>,
        albumIds: List<String> = emptyList()
    ): Result<List<KugouPrivilegeResult>> = runCatchingCancellable {
        config.ensureModeSynced()
        val hashList = hashes.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        require(hashList.isNotEmpty()) { "缺少歌曲 hash" }

        val resourceArray = buildJsonArray {
            hashList.forEachIndexed { index, hash ->
                val rawAlbumId = albumIds.getOrNull(index)?.trim().orEmpty()
                val albumIdNum = rawAlbumId.toLongOrNull() ?: 0L
                add(
                    buildJsonObject {
                        put("type", "audio")
                        put("page_id", 0)
                        put("hash", hash)
                        // 注意：resource 里不要传 album_audio_id，否则酷狗会省略 128 ogg 条目
                        put("album_id", albumIdNum)
                    }
                )
            }
        }
        val qualitiesArray = buildJsonArray {
            PRIVILEGE_QUALITIES.forEach { add(JsonPrimitive(it)) }
        }
        val dataObj = buildJsonObject {
            put("appid", config.effectiveAppId())
            put("area_code", 1)
            put("behavior", "play")
            put("clientver", config.effectiveClientVer())
            put("need_hash_offset", 1)
            put("relate", 1)
            put("support_verify", 1)
            put("resource", resourceArray)
            put("qualities", qualitiesArray)
        }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/v2/get_res_privilege/lite",
                data = dataObj,
                headers = mapOf(
                    "x-router" to "media.store.kugou.com",
                    "Content-Type" to "application/json"
                ),
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureBizSuccess(response, "获取酷狗音质权益失败")

        val dataArray = response.bodyJson?.get("data")?.jsonArrayOrNull() ?: emptyList()
        val isLite = config.mode.isLite

        hashList.mapIndexed { index, hash ->
            val matchedItem = dataArray.firstOrNull { elem ->
                elem.jsonObjectOrNull()?.string("hash").equals(hash, ignoreCase = true)
            }?.jsonObjectOrNull()
                ?: dataArray.getOrNull(index)?.jsonObjectOrNull()
                ?: JsonObject(emptyMap())
            val reqAlbumId = albumIds.getOrNull(index)?.trim().orEmpty()
            parsePrivilegeItem(hash, reqAlbumId, matchedItem, isLite)
        }
    }

    // 对照 module/song_url_new.js：主链路获取播放直链（/v6/priv_url）
    suspend fun getSongUrlNew(
        hash: String,
        albumId: String? = null,
        albumAudioId: Long? = null,
        quality: UnifiedAudioQuality = UnifiedAudioQuality.EXHIGH
    ): Result<KugouSongUrlResult> = runCatchingCancellable {
        config.ensureModeSynced()
        val cleanHash = hash.trim().lowercase()
        require(cleanHash.isNotEmpty()) { "缺少歌曲 hash" }

        val device = deviceStore.getOrCreateDeviceIdentity()
        val activeAccount = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
        val rawUserId = activeAccount?.credentials?.get("userid")
            ?: activeAccount?.effectivePlatformUid
            ?: "0"
        val userIdLong = rawUserId.toLongOrNull() ?: 0L
        val userIdStr = userIdLong.toString()
        val token = activeAccount?.credentials?.get("token").orEmpty()
        val vipToken = activeAccount?.credentials?.get("vip_token").orEmpty()
        val vipType = activeAccount?.credentials?.get("vip_type")?.toIntOrNull() ?: 0
        var auth = activeAccount?.credentials?.get("auth").orEmpty()
        if (activeAccount != null && auth.isBlank() && verifyPlaybackAuth != null) {
            auth = backfillAuthIfNeeded(userIdStr)
        }
        val clientTimeMs = nowMs()

        val trackerKey = KugouSigner.signKey(
            hash = cleanHash,
            mid = device.mid,
            userid = userIdLong,
            appid = config.effectiveAppId(),
            mode = config.mode
        )

        val dataObj = buildJsonObject {
            put("area_code", "1")
            put("behavior", "play")
            put(
                "qualities",
                buildJsonArray { PRIV_URL_QUALITIES.forEach { add(JsonPrimitive(it)) } }
            )
            put(
                "resource",
                buildJsonObject {
                    put("album_audio_id", albumAudioId ?: 0L)
                    put("collect_list_id", "3")
                    put("collect_time", clientTimeMs)
                    put("hash", cleanHash)
                    put("id", 0)
                    put("page_id", 1)
                    put("type", "audio")
                }
            )
            put("token", token)
            put(
                "tracker_param",
                buildJsonObject {
                    put("all_m", 1)
                    put("auth", auth)
                    put("is_free_part", 0)
                    put("key", trackerKey)
                    put("module_id", 0)
                    put("need_climax", 1)
                    put("need_xcdn", 1)
                    put("open_time", "")
                    put("pid", "411")
                    put("pidversion", "3001")
                    put("priv_vip_type", "6")
                    put("viptoken", vipToken)
                }
            )
            put("userid", userIdStr)
            put("vip", vipType)
        }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/v6/priv_url",
                data = dataObj,
                headers = mapOf("x-router" to "tracker.kugou.com"),
                encryptType = KugouEncryptType.ANDROID,
                cookieOverrides = mapOf("dfid" to device.effectiveDfid)
            )
        )
        ensureBizSuccess(response, "获取酷狗播放直链失败")
        val body = response.bodyJson ?: throw AppError.NetworkError
        parseSongUrlNewResponse(body, cleanHash, quality, albumId)
    }

    // 仅在缺失 auth 时按账号冷却限流补齐，失败静默忽略以免阻塞正常播放直链解析
    private suspend fun backfillAuthIfNeeded(uid: String): String {
        val verify = verifyPlaybackAuth ?: return ""
        if (uid.isBlank() || uid == "0") return ""
        val lastAttempt = authVerifyLastAttemptByUid[uid]
        if (lastAttempt != null && (nowMs() - lastAttempt) < AUTH_VERIFY_COOLDOWN_MS) {
            return ""
        }
        return authVerifyMutex.withLock {
            val latestAuth = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
                ?.credentials?.get("auth")
                ?.takeIf { it.isNotBlank() }
            if (latestAuth != null) return@withLock latestAuth

            val lockedLastAttempt = authVerifyLastAttemptByUid[uid]
            val currentNow = nowMs()
            if (lockedLastAttempt != null && (currentNow - lockedLastAttempt) < AUTH_VERIFY_COOLDOWN_MS) {
                return@withLock ""
            }
            authVerifyLastAttemptByUid[uid] = currentNow
            verify.invoke()
                .onFailure { err ->
                    AppLogger.w(TAG, "按需补齐酷狗 auth 失败 uid=$uid", err)
                }
                .getOrNull()
                .orEmpty()
        }
    }

    // 对照 module/song_url.js：备选回退链路（/v5/url，clientver=11430，encryptKey + notSignature）
    suspend fun getSongUrlLegacy(
        hash: String,
        albumId: String? = null,
        quality: UnifiedAudioQuality = UnifiedAudioQuality.EXHIGH
    ): Result<KugouSongUrlResult> = runCatchingCancellable {
        config.ensureModeSynced()
        val cleanHash = hash.trim().lowercase()
        require(cleanHash.isNotEmpty()) { "缺少歌曲 hash" }

        val device = deviceStore.getOrCreateDeviceIdentity()
        val isLite = config.mode.isLite
        val pageId = if (isLite) 967177915 else 151369488
        val pid = if (isLite) 411 else 2
        val ppageId = if (isLite) {
            "356753938,823673182,967485191"
        } else {
            "463467626,350369493,788954147"
        }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                url = "/v5/url",
                params = mapOf(
                    "album_id" to (albumId?.trim()?.toLongOrNull() ?: 0L),
                    "area_code" to 1,
                    "hash" to cleanHash,
                    "ssa_flag" to "is_fromtrack",
                    "version" to 11430,
                    "page_id" to pageId,
                    "quality" to quality.kugouCode,
                    "album_audio_id" to 0L,
                    "behavior" to "play",
                    "pid" to pid,
                    "cmd" to 26,
                    "pidversion" to 3001,
                    "IsFreePart" to 0,
                    "ppage_id" to ppageId,
                    "cdnBackup" to 1,
                    "module" to "",
                    "clientver" to 11430
                ),
                headers = mapOf("x-router" to "trackercdn.kugou.com"),
                encryptType = KugouEncryptType.ANDROID,
                encryptKey = true,
                notSignature = true,
                overrideClientVer = 11430,
                cookieOverrides = mapOf("dfid" to device.effectiveDfid)
            )
        )
        ensureBizSuccess(response, "酷狗备选直链获取失败")
        val body = response.bodyJson ?: throw AppError.NetworkError
        parseSongUrlLegacyResponse(body, cleanHash, quality)
    }

    // 对照 module/audio.js：批量获取音频元数据（时长、码率、封面、album_audio_id 等）
    suspend fun getAudioMetadata(hashes: List<String>): Result<List<KugouAudioMetadataDto>> = runCatchingCancellable {
        config.ensureModeSynced()
        val cleanHashes = hashes.map { it.trim() }.filter { it.isNotEmpty() }
        require(cleanHashes.isNotEmpty()) { "缺少歌曲 hash" }

        val device = deviceStore.getOrCreateDeviceIdentity()
        val activeAccount = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
        val token = activeAccount?.credentials?.get("token").orEmpty()
        val userid = activeAccount?.credentials?.get("userid")
            ?: activeAccount?.effectivePlatformUid
            ?: ""
        val dateTimeMs = nowMs()
        val key = KugouSigner.signParamsKey(dateTimeMs.toString(), mode = config.mode)

        val dataObj = buildJsonObject {
            put("appid", config.effectiveAppId())
            put("clienttime", dateTimeMs)
            put("clientver", config.effectiveClientVer())
            put(
                "data",
                buildJsonArray {
                    cleanHashes.forEach { h ->
                        add(
                            buildJsonObject {
                                put("hash", h)
                                put("audio_id", 0)
                            }
                        )
                    }
                }
            )
            put("dfid", device.effectiveDfid)
            put("key", key)
            put("mid", device.mid)
            if (token.isNotBlank()) put("token", token)
            if (userid.isNotBlank() && userid != "0") put("userid", userid)
        }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                baseUrl = "https://kmr.service.kugou.com",
                url = "/v1/audio/audio",
                data = dataObj,
                headers = mapOf(
                    "x-router" to "kmr.service.kugou.com",
                    "Content-Type" to "application/json"
                ),
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureBizSuccess(response, "获取酷狗音频元信息失败")

        val dataArray = response.bodyJson?.get("data")?.jsonArrayOrNull() ?: emptyList()
        dataArray.mapNotNull { elem ->
            val obj = elem.jsonObjectOrNull() ?: return@mapNotNull null
            val itemHash = obj.string("hash").ifBlank { return@mapNotNull null }
            val rawDuration = obj.long("duration").takeIf { it > 0L } ?: obj.long("timelength")
            val durationMs = if (rawDuration in 1L..9_999L) rawDuration * 1000L else rawDuration
            val cover = obj["trans_param"]?.jsonObjectOrNull()?.string("union_cover")
                ?.replace("{size}", "400")
                ?.replace("http://", "https://")
                .orEmpty()
            KugouAudioMetadataDto(
                hash = itemHash,
                audioId = obj.long("audio_id"),
                albumId = obj.string("album_id"),
                albumAudioId = obj.long("album_audio_id"),
                songName = obj.string("songname").ifBlank { obj.string("audio_name") },
                authorName = obj.string("author_name").ifBlank { obj.string("singername") },
                albumName = obj.string("album_name"),
                durationMs = durationMs,
                fileSize = obj.long("filesize").takeIf { it > 0L } ?: obj.long("fileSize"),
                bitRate = obj.int("bitrate"),
                extName = obj.string("extname"),
                coverUrl = cover
            )
        }
    }

    // 对照 module/search_lyric.js：搜索歌词候选（返回 id / accesskey / score）
    suspend fun searchLyrics(
        hash: String,
        keyword: String = "",
        durationMs: Long = 0L
    ): Result<List<KugouLyricCandidateDto>> = runCatchingCancellable {
        config.ensureModeSynced()
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                baseUrl = "https://lyrics.kugou.com",
                url = "/v1/search",
                params = mapOf(
                    "album_audio_id" to 0,
                    "appid" to config.effectiveAppId(),
                    "clientver" to config.effectiveClientVer(),
                    "duration" to durationMs,
                    "hash" to hash.trim(),
                    "keyword" to keyword,
                    "lrctxt" to 1,
                    "man" to "no"
                ),
                encryptType = KugouEncryptType.ANDROID,
                clearDefaultParams = true,
                notSignature = true
            )
        )
        ensureBizSuccess(response, "搜索酷狗歌词失败")
        val candidates = response.bodyJson?.get("candidates")?.jsonArrayOrNull() ?: emptyList()
        candidates.mapNotNull { elem ->
            val obj = elem.jsonObjectOrNull() ?: return@mapNotNull null
            val id = obj.string("id")
            val accessKey = obj.string("accesskey")
            if (id.isBlank() || accessKey.isBlank()) return@mapNotNull null
            val krctype = obj.int("krctype", -1)
            val fmt = obj.string("fmt").ifBlank {
                if (krctype == 1) "lrc" else "krc"
            }
            KugouLyricCandidateDto(
                id = id,
                accessKey = accessKey,
                score = obj.int("score"),
                song = obj.string("song"),
                singer = obj.string("singer"),
                durationMs = obj.long("duration"),
                language = obj.string("language"),
                fmt = fmt
            )
        }
    }

    // 对照 module/lyric.js：下载歌词并在 fmt=krc 时 Base64 解码 + KugouCrypto.decodeKrcLyrics 解压明文
    suspend fun downloadLyrics(
        id: String,
        accessKey: String,
        fmt: String = "krc"
    ): Result<KugouLyricDownloadResult> = runCatchingCancellable {
        require(id.isNotBlank() && accessKey.isNotBlank()) { "歌词 id 与 accesskey 不能为空" }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                baseUrl = "https://lyrics.kugou.com",
                url = "/download",
                params = mapOf(
                    "ver" to 1,
                    "client" to "android",
                    "id" to id,
                    "accesskey" to accessKey,
                    "fmt" to fmt,
                    "charset" to "utf8"
                ),
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureBizSuccess(response, "下载酷狗歌词失败")
        val body = response.bodyJson ?: throw AppError.NetworkError
        val rawBase64 = body.string("content")
        val contentType = body["contenttype"]?.jsonPrimitive?.intOrNull
        val respFmt = body.string("fmt").ifBlank { fmt }

        val decoded = if (rawBase64.isNotBlank()) {
            val rawBytes = Base64.getDecoder().decode(rawBase64)
            val hasKrcHeader = rawBytes.size >= 4 &&
                rawBytes[0] == 'k'.code.toByte() &&
                rawBytes[1] == 'r'.code.toByte() &&
                rawBytes[2] == 'c'.code.toByte() &&
                rawBytes[3] == '1'.code.toByte()
            when {
                respFmt.equals("lrc", ignoreCase = true) -> rawBytes.toString(Charsets.UTF_8)
                contentType != null && contentType != 0 && !hasKrcHeader -> rawBytes.toString(Charsets.UTF_8)
                hasKrcHeader -> KugouCrypto.decodeKrcLyrics(rawBytes)
                contentType == 0 -> KugouCrypto.decodeKrcLyrics(rawBytes)
                else -> KugouCrypto.decodeKrcLyrics(rawBytes).ifBlank { rawBytes.toString(Charsets.UTF_8) }
            }
        } else {
            ""
        }

        KugouLyricDownloadResult(
            id = id,
            fmt = respFmt,
            contentType = contentType,
            rawContentBase64 = rawBase64,
            decodedContent = decoded
        )
    }

    // 对照 module/playhistory_upload.js：上报酷狗播放历史（op=1，plat=3）
    suspend fun uploadPlayHistory(
        mixSongId: Long,
        playedSeconds: Long = 0L
    ): Result<Unit> = runCatchingCancellable {
        require(mixSongId > 0L) { "mixSongId 必须为正整数: $mixSongId" }
        val activeAccount = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
        val token = activeAccount?.credentials?.get("token").orEmpty()
        val rawUserId = activeAccount?.credentials?.get("userid")
            ?: activeAccount?.effectivePlatformUid
            ?: "0"
        val userIdValue = rawUserId.toLongOrNull() ?: 0L
        val otSec = nowMs() / 1000L

        val dataObj = buildJsonObject {
            put(
                "songs",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("mxid", mixSongId)
                            put("op", 1)
                            put("ot", otSec)
                            put("pc", if (playedSeconds > 0L) 1 else 1)
                        }
                    )
                }
            )
            put("token", token)
            put("userid", userIdValue)
        }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/playhistory/v1/upload_songs",
                params = mapOf("plat" to 3),
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureBizSuccess(response, "上报酷狗播放历史失败")
    }

    // 对照 module/user_history.js：分页获取酷狗云端播放历史
    suspend fun getUserPlayHistory(bp: String? = null): Result<KugouPlayHistoryPage> = runCatchingCancellable {
        val activeAccount = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
        val token = activeAccount?.credentials?.get("token").orEmpty()
        val rawUserId = activeAccount?.credentials?.get("userid")
            ?: activeAccount?.effectivePlatformUid
            ?: "0"
        val userIdValue = rawUserId.toLongOrNull() ?: 0L

        val dataObj = buildJsonObject {
            put("token", token)
            put("userid", userIdValue)
            put("source_classify", "app")
            put("to_subdivide_sr", 1)
            if (!bp.isNullOrBlank()) {
                put("bp", bp)
            }
        }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/playhistory/v1/get_songs",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureBizSuccess(response, "获取酷狗播放历史失败")
        val dataNode = response.bodyJson?.get("data")?.jsonObjectOrNull() ?: JsonObject(emptyMap())
        val nextBp = dataNode.string("bp").takeIf { it.isNotBlank() }
        val hasMore = dataNode.int("has_more", 0) == 1 ||
            dataNode["has_more"]?.jsonPrimitive?.contentOrNull?.equals("true", ignoreCase = true) == true ||
            nextBp != null
        val songsArray = dataNode["songs"]?.jsonArrayOrNull()
            ?: dataNode["list"]?.jsonArrayOrNull()
            ?: emptyList()

        val items = songsArray.mapNotNull { elem ->
            val obj = elem.jsonObjectOrNull() ?: return@mapNotNull null
            val infoObj = obj["info"]?.jsonObjectOrNull()
            val itemHash = obj.string("hash").ifBlank { infoObj?.string("hash").orEmpty() }
            val mxid = obj.long("mxid")
                .takeIf { it > 0L }
                ?: obj.long("mixsongid").takeIf { it > 0L }
                ?: obj.long("id")
            if (itemHash.isBlank() && mxid <= 0L) return@mapNotNull null
            val rawDur = obj.long("duration").takeIf { it > 0L }
                ?: obj.long("timelength").takeIf { it > 0L }
                ?: infoObj?.long("duration") ?: 0L
            val durationMs = if (rawDur in 1L..9_999L) rawDur * 1000L else rawDur
            val rawCover = obj.string("image")
                .ifBlank { obj.string("cover") }
                .ifBlank { infoObj?.string("image").orEmpty() }
                .replace("{size}", "400")
                .replace("http://", "https://")
            KugouPlayHistoryItem(
                mixSongId = mxid,
                hash = itemHash,
                songName = obj.string("name").ifBlank { obj.string("songname") },
                singerName = obj.string("singername").ifBlank { obj.string("author_name") },
                albumId = obj.string("album_id"),
                albumName = obj.string("album_name").ifBlank { obj.string("albumname") },
                albumAudioId = obj.long("album_audio_id"),
                playedAtSec = obj.long("ot").takeIf { it > 0L } ?: obj.long("play_time"),
                playCount = obj.int("pc", 1),
                durationMs = durationMs,
                coverUrl = rawCover
            )
        }

        KugouPlayHistoryPage(
            bp = nextBp,
            hasMore = hasMore && items.isNotEmpty(),
            items = items
        )
    }

    private fun parsePrivilegeItem(
        hash: String,
        reqAlbumId: String,
        resource: JsonObject,
        isLite: Boolean
    ): KugouPrivilegeResult {
        val relateGoods = resource["relate_goods"]?.jsonArrayOrNull()
            ?.mapNotNull { it.jsonObjectOrNull() }
            .orEmpty()
        val transParam = resource["trans_param"]?.jsonObjectOrNull()
        val ogg128Hash = transParam?.string("ogg_128_hash")?.takeIf { it.isNotBlank() }?.lowercase()
        val ogg128Size = transParam?.long("ogg_128_filesize") ?: 0L
        val ogg320Hash = transParam?.string("ogg_320_hash")?.takeIf { it.isNotBlank() }?.lowercase()
        val ogg320Size = transParam?.long("ogg_320_filesize") ?: 0L

        val qualityHashes = LinkedHashMap<String, String>()
        val items = PRIVILEGE_QUALITIES.map { quality ->
            val relateItem = pickRelate(relateGoods, quality)
            val exists = relateItem != null
            val privilege = relateItem?.int("privilege", -1) ?: -1
            val status = relateItem?.int("status", 0) ?: 0
            val needVip = exists && privilege != 0
            val freeListen = isLite && quality == "128" && needVip
            val obtainable = freeListen || (exists && !needVip && status != 0)
            val oggPair = when (quality) {
                "128" -> ogg128Hash to ogg128Size
                "320" -> ogg320Hash to ogg320Size
                else -> null to 0L
            }
            val infoObj = relateItem?.get("info")?.jsonObjectOrNull()
            val itemHash = if (exists) {
                oggPair.first ?: relateItem?.string("hash").orEmpty().lowercase()
            } else {
                ""
            }
            if (exists && itemHash.isNotBlank()) {
                qualityHashes[quality] = relateItem?.string("hash")?.takeIf { it.isNotBlank() }?.lowercase() ?: itemHash
            }
            val fileSize = if (exists) {
                oggPair.second.takeIf { it > 0L } ?: infoObj?.long("filesize") ?: 0L
            } else {
                0L
            }
            val extname = if (exists) {
                if (!oggPair.first.isNullOrBlank()) "ogg" else infoObj?.string("extname").orEmpty()
            } else {
                ""
            }
            val vipType = if (exists && !obtainable && needVip) {
                QUALITY_VIP_TYPE[quality] ?: "concept"
            } else {
                ""
            }
            KugouQualityItem(
                quality = quality,
                unifiedQuality = UnifiedAudioQuality.fromKugouCode(quality),
                exists = exists,
                level = relateItem?.int("level", 0) ?: 0,
                bitrate = infoObj?.int("bitrate", 0) ?: 0,
                extname = extname,
                hash = itemHash,
                fileSize = fileSize,
                privilege = privilege,
                status = status,
                payType = relateItem?.int("pay_type", -1) ?: -1,
                price = relateItem?.int("price", 0) ?: 0,
                failProcess = relateItem?.int("fail_process", 0) ?: 0,
                needVip = needVip,
                obtainable = obtainable,
                freeListen = freeListen,
                requireVipType = vipType,
                message = relateItem?.string("_msg").orEmpty()
            )
        }

        val freeQualities = items.filter { it.obtainable }.map { it.quality }
        val vipQualities = items.filter { it.exists && it.needVip && !it.obtainable }.map { it.quality }
        val blockedQualities = items.filter { it.exists && !it.obtainable && !it.needVip }.map { it.quality }
        val missingQualities = items.filter { !it.exists }.map { it.quality }
        val superVipQualities = items.filter { it.requireVipType == "suvip" }.map { it.quality }
        val freeSong = items.isNotEmpty() && items.all { !it.needVip }

        val bestFree = items
            .filter { it.obtainable }
            .sortedWith(
                compareBy<KugouQualityItem> { it.level }
                    .thenBy { QUALITY_ORDER.indexOf(it.quality) }
            )
            .lastOrNull()

        val highestUnified = items
            .filter { it.exists }
            .mapNotNull { it.unifiedQuality }
            .maxByOrNull { it.rank }
            ?: UnifiedAudioQuality.fromKugouCode(resource.string("quality"))

        val topInfo = resource["info"]?.jsonObjectOrNull()
        val rawCover = transParam?.string("union_cover").orEmpty()
            .ifBlank { topInfo?.string("image").orEmpty() }
            .replace("{size}", "400")
            .replace("http://", "https://")
        val duration = topInfo?.long("duration") ?: 0L
        val durationMs = if (duration in 1L..9_999L) duration * 1000L else duration

        return KugouPrivilegeResult(
            hash = hash,
            albumId = resource.string("album_id").ifBlank { reqAlbumId },
            albumAudioId = resource.long("album_audio_id"),
            mixSongId = resource.long("id").takeIf { it > 0L } ?: resource.long("mixsongid"),
            name = resource.string("name"),
            singerName = resource.string("singername"),
            albumName = resource.string("albumname"),
            durationMs = durationMs,
            coverUrl = rawCover,
            freeSong = freeSong,
            freeListen = items.any { it.freeListen },
            qualities = items,
            freeQualities = freeQualities,
            vipQualities = vipQualities,
            superVipQualities = superVipQualities,
            blockedQualities = blockedQualities,
            missingQualities = missingQualities,
            bestFreeQuality = bestFree?.quality.orEmpty(),
            highestQuality = highestUnified,
            qualityHashes = qualityHashes,
            ogg128Hash = ogg128Hash,
            ogg320Hash = ogg320Hash
        )
    }

    private fun pickRelate(relateGoods: List<JsonObject>, quality: String): JsonObject? {
        for (level in QUALITY_LEVEL_MAP[quality].orEmpty()) {
            relateGoods.firstOrNull { it.int("level", 0) == level && it.string("quality") == quality }
                ?.let { return it }
        }
        return relateGoods.firstOrNull { it.string("quality") == quality }
    }

    private fun parseSongUrlNewResponse(
        body: JsonObject,
        fallbackHash: String,
        preferredQuality: UnifiedAudioQuality,
        albumId: String?
    ): KugouSongUrlResult {
        val dataElement = body["data"]
        val candidateObj = when (dataElement) {
            is JsonObject -> {
                val goods = dataElement["goods"]?.jsonArrayOrNull()
                    ?.mapNotNull { it.jsonObjectOrNull() }
                    .orEmpty()
                if (goods.isNotEmpty()) {
                    pickBestUrlCandidate(goods, preferredQuality, fallbackHash)
                } else {
                    dataElement
                }
            }
            is JsonArray -> {
                val list = dataElement.mapNotNull { it.jsonObjectOrNull() }
                if (list.isNotEmpty()) {
                    pickBestUrlCandidate(list, preferredQuality, fallbackHash)
                } else {
                    body
                }
            }
            else -> body
        }

        val infoObj = when (val infoElem = candidateObj["info"]) {
            is JsonObject -> infoElem
            is JsonArray -> infoElem.mapNotNull { it.jsonObjectOrNull() }.firstOrNull()
            else -> null
        }

        val urlList = extractUrlList(candidateObj, infoObj)
        val primaryUrl = urlList.firstOrNull().orEmpty()
        val backupUrls = if (urlList.size > 1) urlList.drop(1) else emptyList()

        val respQuality = candidateObj.string("quality")
            .ifBlank { infoObj?.string("quality").orEmpty() }
            .ifBlank { preferredQuality.kugouCode }
        val respHash = candidateObj.string("hash")
            .ifBlank { infoObj?.string("hash").orEmpty() }
            .ifBlank { fallbackHash }
            .lowercase()
        val extName = candidateObj.string("extName")
            .ifBlank { candidateObj.string("extname") }
            .ifBlank { infoObj?.string("extname").orEmpty() }
        val fileSize = candidateObj.long("fileSize")
            .takeIf { it > 0L }
            ?: candidateObj.long("filesize").takeIf { it > 0L }
            ?: infoObj?.long("filesize") ?: 0L
        val bitRate = candidateObj.int("bitRate")
            .takeIf { it > 0 }
            ?: candidateObj.int("bitrate").takeIf { it > 0 }
            ?: infoObj?.int("bitrate") ?: 0
        val rawDur = candidateObj.long("timeLength")
            .takeIf { it > 0L }
            ?: candidateObj.long("timelength").takeIf { it > 0L }
            ?: infoObj?.long("duration") ?: 0L
        val durationMs = if (rawDur in 1L..9_999L) rawDur * 1000L else rawDur

        val transParam = candidateObj["trans_param"]?.jsonObjectOrNull()
        val isFreeTrial = candidateObj.int("is_free_part", 0) == 1 ||
            candidateObj.int("free_part", 0) == 1 ||
            candidateObj.int("isFreePart", 0) == 1 ||
            transParam?.int("is_free_part", 0) == 1 ||
            candidateObj.int("fail_process", 0) == 4 ||
            candidateObj.int("priv_status", 0) == 5 ||
            (primaryUrl.contains("preview", ignoreCase = true) && primaryUrl.isNotBlank())

        val volume = candidateObj.doubleOrNull("volume") ?: candidateObj.doubleOrNull("volume_gain")
        val peak = candidateObj.doubleOrNull("volume_peak")

        if (primaryUrl.isBlank() && albumId == null) {
            // 保留合法空直链供上层 KugouPlaybackProvider 判定回退或换源
        }

        return KugouSongUrlResult(
            hash = respHash,
            url = primaryUrl,
            backupUrls = backupUrls,
            quality = respQuality,
            unifiedQuality = UnifiedAudioQuality.fromKugouCode(respQuality),
            extName = extName,
            fileSize = fileSize,
            bitRate = bitRate,
            durationMs = durationMs,
            isFreeTrial = isFreeTrial,
            volume = volume,
            volumePeak = peak,
            fromLegacyFallback = false
        )
    }

    private fun parseSongUrlLegacyResponse(
        body: JsonObject,
        fallbackHash: String,
        preferredQuality: UnifiedAudioQuality
    ): KugouSongUrlResult {
        val rootOrData = body["data"]?.jsonObjectOrNull() ?: body
        val urlList = extractUrlList(rootOrData, null)
        val primaryUrl = urlList.firstOrNull().orEmpty()
        val backupUrls = if (urlList.size > 1) urlList.drop(1) else emptyList()

        val respQuality = rootOrData.string("quality")
            .ifBlank { preferredQuality.kugouCode }
        val fileSize = rootOrData.long("fileSize").takeIf { it > 0L } ?: rootOrData.long("filesize")
        val bitRate = rootOrData.int("bitRate").takeIf { it > 0 } ?: rootOrData.int("bitrate")
        val rawDur = rootOrData.long("timeLength").takeIf { it > 0L } ?: rootOrData.long("timelength")
        val durationMs = if (rawDur in 1L..9_999L) rawDur * 1000L else rawDur
        val isFreeTrial = rootOrData.int("is_free_part", 0) == 1 ||
            rootOrData.int("IsFreePart", 0) == 1 ||
            rootOrData.int("free_part", 0) == 1 ||
            rootOrData.int("privilege", 0) == 5

        return KugouSongUrlResult(
            hash = rootOrData.string("hash").ifBlank { fallbackHash }.lowercase(),
            url = primaryUrl,
            backupUrls = backupUrls,
            quality = respQuality,
            unifiedQuality = UnifiedAudioQuality.fromKugouCode(respQuality),
            extName = rootOrData.string("extName").ifBlank { rootOrData.string("extname") },
            fileSize = fileSize,
            bitRate = bitRate,
            durationMs = durationMs,
            isFreeTrial = isFreeTrial,
            volume = rootOrData.doubleOrNull("volume") ?: rootOrData.doubleOrNull("volume_gain"),
            volumePeak = rootOrData.doubleOrNull("volume_peak"),
            fromLegacyFallback = true
        )
    }

    private fun pickBestUrlCandidate(
        candidates: List<JsonObject>,
        preferredQuality: UnifiedAudioQuality,
        targetHash: String
    ): JsonObject {
        candidates.firstOrNull {
            it.string("quality").equals(preferredQuality.kugouCode, ignoreCase = true) &&
                extractUrlList(it, it["info"]?.jsonObjectOrNull()).isNotEmpty()
        }?.let { return it }

        candidates.firstOrNull {
            it.string("hash").equals(targetHash, ignoreCase = true) &&
                extractUrlList(it, it["info"]?.jsonObjectOrNull()).isNotEmpty()
        }?.let { return it }

        return candidates.firstOrNull {
            extractUrlList(it, it["info"]?.jsonObjectOrNull()).isNotEmpty()
        } ?: candidates.first()
    }

    private fun extractUrlList(primary: JsonObject, nestedInfo: JsonObject?): List<String> {
        val urls = LinkedHashSet<String>()
        fun collect(elem: JsonElement?) {
            when (elem) {
                is JsonPrimitive -> elem.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let(urls::add)
                is JsonArray -> elem.forEach(::collect)
                else -> Unit
            }
        }
        collect(primary["url"])
        collect(primary["tracker_url"])
        collect(nestedInfo?.get("tracker_url"))
        collect(nestedInfo?.get("url"))
        collect(primary["backupUrl"])
        collect(primary["backup_url"])
        return urls.toList()
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

    private fun JsonObject.doubleOrNull(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull
}
