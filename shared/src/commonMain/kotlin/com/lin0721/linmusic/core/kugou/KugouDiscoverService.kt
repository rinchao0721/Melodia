package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.platform.UnifiedAudioQuality
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

// 酷狗发现页、每日推荐、私人 FM、推荐歌单广场与排行榜原生服务
class KugouDiscoverService(
    private val config: KugouConfig,
    private val deviceStore: KugouDeviceStore,
    private val gatewayClient: KugouGatewayClient,
    private val userPreferences: UserPreferences,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    // module/everyday_recommend.js
    suspend fun getDailyRecommendSongs(
        platform: String = "ios"
    ): Result<KugouDailyRecommendResult> = runCatchingCancellable {
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/everyday_song_recommend",
                params = mapOf("platform" to platform),
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf("x-router" to "everydayrec.service.kugou.com")
            )
        )
        ensureDiscoverBizSuccess(response, "获取酷狗每日推荐失败")

        val dataObj = response.bodyJson?.get("data")?.jsonObjectOrNull()
        val rawItems = extractArray(dataObj, response.bodyJson, "song_list", "songs", "info", "list")
        val tracks = rawItems.mapNotNull { elem ->
            elem.jsonObjectOrNull()?.let(::parseDiscoverTrackItem)
        }
        KugouDailyRecommendResult(
            date = dataObj?.string("date").orEmpty(),
            songCount = dataObj?.int("song_count", tracks.size)?.takeIf { it > 0 } ?: tracks.size,
            tracks = tracks
        )
    }

    // module/personal_fm.js
    suspend fun getPersonalFm(
        action: String = "play",
        mode: String = "normal",
        songPoolId: Long = 0L,
        remainSongCnt: Int = 0,
        isOverplay: Boolean = false,
        platform: String = "ios",
        hash: String? = null,
        songId: Long? = null,
        playTime: Long? = null,
        curMark: String? = null
    ): Result<KugouPersonalFmResult> = runCatchingCancellable {
        config.ensureModeSynced()
        val device = deviceStore.getOrCreateDeviceIdentity()
        val activeAccount = userPreferences.activeAccountOf(MusicPlatform.KUGOU).first()
        val session = optionalKugouSession(userPreferences)
        val vipType = activeAccount?.credentials?.get("vip_type")?.toIntOrNull() ?: 0
        val dateTimeMs = nowMs()

        val dataObj = buildJsonObject {
            put("appid", config.effectiveAppId())
            put("clienttime", dateTimeMs)
            put("mid", device.mid)
            put("action", action)
            put("recommend_source_locked", 0)
            put("song_pool_id", songPoolId)
            put("callerid", 0)
            put("m_type", 1)
            put("platform", platform)
            put("area_code", 1)
            put("remain_songcnt", remainSongCnt)
            put("clientver", config.effectiveClientVer())
            put("is_overplay", if (isOverplay) 1 else 0)
            put("mode", mode)
            put("fakem", "ca981cfc583a4c37f28d2d49000013c16a0a")
            put("key", KugouSigner.signParamsKey(dateTimeMs.toString(), mode = config.mode))
            if (session != null && session.userIdLong > 0L) {
                put("userid", session.userIdLong)
                put("kguid", session.userIdLong)
            }
            if (session != null && session.token.isNotBlank()) {
                put("token", session.token)
            }
            if (vipType > 0) {
                put("vip_type", vipType)
            }
            if (!hash.isNullOrBlank()) put("hash", hash.trim().lowercase())
            if (songId != null && songId > 0L) put("songid", songId)
            if (playTime != null && playTime >= 0L) put("playtime", playTime)
            if (!curMark.isNullOrBlank()) put("cur_mark", curMark)
        }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/v2/personal_recommend",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf("x-router" to "persnfm.service.kugou.com")
            )
        )
        ensureDiscoverBizSuccess(response, "获取酷狗私人 FM 失败")

        val dataNode = response.bodyJson?.get("data")
        val dataObjResp = dataNode?.jsonObjectOrNull()
        val rawItems = when (dataNode) {
            is JsonArray -> dataNode
            is JsonObject -> extractArray(dataNode, response.bodyJson, "song_list", "list", "info", "songs")
            else -> extractArray(null, response.bodyJson, "song_list", "list", "info", "songs")
        }
        val tracks = rawItems.mapNotNull { elem ->
            elem.jsonObjectOrNull()?.let(::parseDiscoverTrackItem)
        }
        KugouPersonalFmResult(
            songPoolId = dataObjResp?.firstPositiveLong("song_pool_id", "songPoolId") ?: songPoolId,
            curMark = dataObjResp?.firstNonBlank("cur_mark", "curMark").orEmpty(),
            tracks = tracks
        )
    }

    // module/home_discover.js（必须显式覆盖 clientver=20809，避免 LITE 模式 11440 返回空列表）
    suspend fun getHomeDiscover(
        pageSize: Int = 4,
        todayPlayNum: Int = 0,
        recallType: String = "song",
        support: String = "only_song",
        goKyExtra: List<Pair<String, String>> = DEFAULT_HOME_DISCOVER_EXTRA
    ): Result<List<KugouHomeDiscoverItemDto>> = runCatchingCancellable {
        val session = optionalKugouSession(userPreferences)
        val userId = session?.userIdLong ?: 0L
        val safePageSize = pageSize.coerceAtLeast(1)

        val bodyObj = buildJsonObject {
            put("support", support)
            put("userid", userId)
            put("recall_type", recallType)
            put("today_play_num", todayPlayNum)
            put("pagesize", safePageSize)
            put(
                "go_ky_extra",
                buildJsonArray {
                    goKyExtra.forEach { (k, v) ->
                        add(buildJsonObject {
                            put("key", k)
                            put("val", v)
                        })
                    }
                }
            )
        }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/homediscoverrec/v1/client/home_discover_rec",
                params = mapOf(
                    "module_key" to "home_discover_rec",
                    "module_id" to 1,
                    "area_code" to 1,
                    "platform" to "android",
                    "userid" to userId,
                    "clientver" to HOME_DISCOVER_CLIENT_VER
                ),
                data = bodyObj,
                encryptType = KugouEncryptType.ANDROID,
                overrideClientVer = HOME_DISCOVER_CLIENT_VER
            )
        )
        ensureDiscoverBizSuccess(response, "获取酷狗首页刷歌推荐失败")
        parseHomeDiscoverItems(response.bodyJson)
    }

    // module/top_playlist.js
    suspend fun getRecommendedPlaylists(
        categoryId: Int = 0,
        moduleId: Int = 1,
        page: Int = 1,
        pageSize: Int = 30,
        sort: Int = 1,
        withTag: Int = 1,
        withSong: Int = 1
    ): Result<KugouRecommendedPlaylistsPage> = runCatchingCancellable {
        config.ensureModeSynced()
        val device = deviceStore.getOrCreateDeviceIdentity()
        val session = optionalKugouSession(userPreferences)
        val safePage = page.coerceAtLeast(1)
        val sec = nowMs() / 1000L

        val dataObj = buildJsonObject {
            put("appid", config.effectiveAppId())
            put("mid", device.mid)
            put("clientver", config.effectiveClientVer())
            put("platform", "android")
            put("clienttime", sec)
            put("userid", session?.userIdLong ?: 0L)
            put("module_id", moduleId)
            put("page", safePage)
            put("pagesize", pageSize)
            put("key", KugouSigner.signParamsKey(sec.toString(), mode = config.mode))
            put(
                "special_recommend",
                buildJsonObject {
                    put("withtag", withTag)
                    put("withsong", withSong)
                    put("sort", sort)
                    put("ugc", 1)
                    put("is_selected", 0)
                    put("withrecommend", 1)
                    put("area_code", 1)
                    put("categoryid", categoryId)
                }
            )
            put("req_multi", 1)
            put("retrun_min", 5)
            put("return_special_falg", 1)
        }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/v2/special_recommend",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf("x-router" to "specialrec.service.kugou.com")
            )
        )
        ensureDiscoverBizSuccess(response, "获取酷狗推荐歌单广场失败")
        parseRecommendedPlaylistsPage(response.bodyJson, safePage)
    }

    // module/rank_list.js
    suspend fun getRankList(withSong: Int = 1): Result<List<KugouRankDto>> = runCatchingCancellable {
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                url = "/ocean/v6/rank/list",
                params = mapOf(
                    "plat" to 2,
                    "withsong" to withSong,
                    "parentid" to 0
                ),
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureDiscoverBizSuccess(response, "获取酷狗排行榜列表失败")
        parseRankList(response.bodyJson)
    }

    // module/rank_audio.js
    suspend fun getRankTracks(
        rankId: Long,
        rankCid: Long = 0L,
        page: Int = 1,
        pageSize: Int = 30
    ): Result<KugouRankTracksPage> = runCatchingCancellable {
        require(rankId > 0L) { "无效酷狗排行榜 rankId: $rankId" }
        val safePage = page.coerceAtLeast(1)
        val dataObj = buildJsonObject {
            put("show_portrait_mv", 1)
            put("show_type_total", 1)
            put("filter_original_remarks", 1)
            put("area_code", 1)
            put("pagesize", pageSize)
            put("rank_cid", rankCid)
            put("type", 1)
            put("page", safePage)
            put("rank_id", rankId)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/openapi/kmr/v2/rank/audio",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf("kg-tid" to "369")
            )
        )
        ensureDiscoverBizSuccess(response, "获取酷狗排行榜曲目失败")

        val dataObjResp = response.bodyJson?.get("data")?.jsonObjectOrNull()
        val rawItems = extractArray(dataObjResp, response.bodyJson, "songlist", "info", "songs", "list")
        val tracks = rawItems.mapNotNull { elem ->
            elem.jsonObjectOrNull()?.let(::parseDiscoverTrackItem)
        }
        val totalCount = dataObjResp?.int("total")
            ?.takeIf { it > 0 }
            ?: dataObjResp?.int("count")?.takeIf { it > 0 }
            ?: tracks.size
        val hasMore = rawItems.isNotEmpty() && (safePage * pageSize < totalCount || rawItems.size >= pageSize)

        KugouRankTracksPage(
            rankId = rankId,
            rankCid = rankCid,
            tracks = tracks,
            totalCount = totalCount,
            page = safePage,
            hasMore = hasMore
        )
    }

    // KuGouMusicApi/module/top_song.js
    suspend fun getNewSongs(
        rankId: Long = 21608L,
        page: Int = 1,
        pageSize: Int = 30
    ): Result<List<KugouTrackItemDto>> = runCatchingCancellable {
        val session = optionalKugouSession(userPreferences)
        val dataObj = buildJsonObject {
            put("rank_id", rankId)
            put("userid", session?.userIdLong ?: 0L)
            put("page", page.coerceAtLeast(1))
            put("pagesize", pageSize.coerceAtLeast(1))
            put("tags", buildJsonArray {})
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/musicadservice/container/v1/newsong_publish",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureDiscoverBizSuccess(response, "获取酷狗新歌速递失败")
        val rawItems = response.bodyJson?.get("data")?.jsonArrayOrNull() ?: emptyList()
        rawItems.mapNotNull { elem ->
            elem.jsonObjectOrNull()?.let(::parseDiscoverTrackItem)
        }
    }

    // KuGouMusicApi/module/top_album.js
    suspend fun getNewAlbums(
        page: Int = 1,
        pageSize: Int = 30
    ): Result<List<KugouNewAlbumDto>> = runCatchingCancellable {
        val session = optionalKugouSession(userPreferences)
        val dataObj = buildJsonObject {
            put("apiver", KugouConfig.APIVER)
            put("token", session?.token.orEmpty())
            put("page", page.coerceAtLeast(1))
            put("pagesize", pageSize.coerceAtLeast(1))
            put("withpriv", 1)
        }
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/musicadservice/v1/mobile_newalbum_sp",
                data = dataObj,
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureDiscoverBizSuccess(response, "获取酷狗新碟上架失败")
        parseNewAlbums(response.bodyJson)
    }

    // KuGouMusicApi/module/everyday_style_recommend.js
    suspend fun getStyleRecommend(
        tagIds: String = ""
    ): Result<KugouStyleRecommendResult> = runCatchingCancellable {
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "POST",
                url = "/everydayrec.service/everyday_style_recommend",
                params = mapOf("tagids" to tagIds),
                data = buildJsonObject {},
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureDiscoverBizSuccess(response, "获取酷狗风格推荐失败")
        val dataObj = response.bodyJson?.get("data")?.jsonObjectOrNull()
        val rawItems = extractArray(dataObj, response.bodyJson, "song_list", "songs", "list")
        val tracks = rawItems.mapNotNull { elem ->
            elem.jsonObjectOrNull()?.let(::parseDiscoverTrackItem)
        }
        KugouStyleRecommendResult(
            title = dataObj?.string("title").orEmpty(),
            tracks = tracks
        )
    }

    private fun ensureDiscoverBizSuccess(response: KugouRawResponse, fallbackMessage: String) {
        ensureKugouBizSuccess(response, fallbackMessage)
        val errCode = response.bodyJson?.int("errcode", 0) ?: 0
        if (errCode != 0) {
            val msg = response.bodyJson?.firstNonBlank("errmsg", "error", "error_msg", "msg").orEmpty()
            throw AppError.BizError(errCode, msg.ifBlank { fallbackMessage })
        }
    }

    private fun extractArray(primary: JsonObject?, root: JsonObject?, vararg keys: String): List<JsonElement> {
        for (key in keys) {
            primary?.get(key)?.jsonArrayOrNull()?.let { return it }
        }
        for (key in keys) {
            root?.get(key)?.jsonArrayOrNull()?.let { return it }
        }
        return emptyList()
    }

    companion object {
        const val HOME_DISCOVER_CLIENT_VER = 20809

        private val DEFAULT_HOME_DISCOVER_EXTRA = listOf(
            "network" to "2",
            "play_mode" to "2",
            "no_mv_ret" to "1"
        )

        // 解析首页刷歌 JSON，支持真实抓包中的 song_info/hash_128/hash_320/hash_flac 结构
        internal fun parseHomeDiscoverItems(root: JsonObject?): List<KugouHomeDiscoverItemDto> {
            val dataObj = root?.get("data")?.jsonObjectOrNull() ?: root
            val rawItems = dataObj?.get("items")?.jsonArrayOrNull()
                ?: dataObj?.get("info")?.jsonArrayOrNull()
                ?: dataObj?.get("list")?.jsonArrayOrNull()
                ?: emptyList()
            return rawItems.mapNotNull { elem ->
                val obj = elem.jsonObjectOrNull() ?: return@mapNotNull null
                val songObj = obj["song_info"]?.jsonObjectOrNull() ?: obj
                val track = parseDiscoverTrackItem(songObj) ?: return@mapNotNull null
                val extra = obj["extra"]?.jsonObjectOrNull()
                KugouHomeDiscoverItemDto(
                    itemId = obj.firstNonBlank("item_id", "itemId").ifBlank { track.mixSongId.toString() },
                    itemType = obj.firstNonBlank("item_type", "itemType").ifBlank { "song" },
                    algPath = obj.firstNonBlank("alg_path", "algPath"),
                    startTimeMs = extra?.long("start_time", 0L) ?: 0L,
                    track = track
                )
            }
        }

        // 解析推荐歌单广场响应（shared/src/desktopTest/resources/kugou/top_playlist.json）
        internal fun parseRecommendedPlaylistsPage(
            root: JsonObject?,
            page: Int = 1
        ): KugouRecommendedPlaylistsPage {
            val dataObj = root?.get("data")?.jsonObjectOrNull() ?: root
            val rawList = dataObj?.get("special_list")?.jsonArrayOrNull()
                ?: dataObj?.get("info")?.jsonArrayOrNull()
                ?: dataObj?.get("list")?.jsonArrayOrNull()
                ?: emptyList()
            val playlists = rawList.mapNotNull { elem ->
                val obj = elem.jsonObjectOrNull() ?: return@mapNotNull null
                val specialId = obj.firstPositiveLong("specialid", "special_id", "listid")
                val gid = obj.firstNonBlank("global_collection_id", "globalCollectionId")
                val name = obj.firstNonBlank("specialname", "name")
                if (name.isBlank() && gid.isBlank() && specialId <= 0L) return@mapNotNull null
                val tags = obj["tags"]?.jsonArrayOrNull()?.mapNotNull { tagElem ->
                    tagElem.jsonObjectOrNull()?.firstNonBlank("tag_name", "name")?.takeIf { it.isNotBlank() }
                        ?: (tagElem as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                }.orEmpty()
                KugouSpecialPlaylistDto(
                    specialId = specialId,
                    globalCollectionId = gid,
                    name = name,
                    coverUrl = normalizeKugouCoverUrl(obj.firstNonBlank("imgurl", "flexible_cover", "pic", "cover")),
                    intro = obj.firstNonBlank("intro", "description"),
                    playCount = obj.firstPositiveLong("play_count", "playcount"),
                    collectCount = obj.firstPositiveLong("collectcount", "collect_count"),
                    creatorUid = obj.firstPositiveLong("suid", "userid", "uid"),
                    creatorNickname = obj.firstNonBlank("nickname", "username"),
                    creatorAvatarUrl = normalizeKugouCoverUrl(obj.firstNonBlank("pic", "user_avatar", "avatar")),
                    tags = tags
                )
            }
            val hasNextInt = dataObj?.int("has_next", -1) ?: -1
            val hasMore = when {
                hasNextInt >= 0 -> hasNextInt == 1
                else -> playlists.isNotEmpty()
            }
            return KugouRecommendedPlaylistsPage(
                playlists = playlists,
                page = page,
                hasMore = hasMore
            )
        }

        // 解析排行榜列表响应（shared/src/desktopTest/resources/kugou/rank_list.json）
        internal fun parseRankList(root: JsonObject?): List<KugouRankDto> {
            val dataObj = root?.get("data")?.jsonObjectOrNull() ?: root
            val rawList = dataObj?.get("info")?.jsonArrayOrNull()
                ?: dataObj?.get("list")?.jsonArrayOrNull()
                ?: emptyList()
            return rawList.mapNotNull { elem ->
                val obj = elem.jsonObjectOrNull() ?: return@mapNotNull null
                val rankId = obj.firstPositiveLong("rankid", "rank_id", "id")
                val name = obj.firstNonBlank("rankname", "name")
                if (rankId <= 0L || name.isBlank()) return@mapNotNull null
                val topSongs = obj["songinfo"]?.jsonArrayOrNull()?.mapNotNull { songElem ->
                    val sObj = songElem.jsonObjectOrNull() ?: return@mapNotNull null
                    val rawSongName = sObj.firstNonBlank("songname", "name")
                    val rawAuthor = sObj.firstNonBlank("author", "singername")
                    when {
                        rawSongName.contains(" - ") -> rawSongName
                        rawSongName.isNotBlank() && rawAuthor.isNotBlank() -> "$rawSongName - $rawAuthor"
                        rawSongName.isNotBlank() -> rawSongName
                        else -> null
                    }
                }.orEmpty()
                KugouRankDto(
                    rankId = rankId,
                    rankCid = obj.firstPositiveLong("rank_cid", "rankCid"),
                    name = name,
                    coverUrl = normalizeKugouCoverUrl(
                        obj.firstNonBlank("imgurl", "album_img_9", "img_cover", "img_9", "banner7url")
                    ),
                    intro = obj.firstNonBlank("intro", "description"),
                    updateFrequency = obj.firstNonBlank("update_frequency", "updateFrequency"),
                    playTimes = obj.firstPositiveLong("play_times", "playTimes"),
                    classify = obj.int("classify", 0),
                    topSongs = topSongs
                )
            }
        }

        internal fun parseDiscoverTrackItem(rawObj: JsonObject): KugouTrackItemDto? {
            val obj = rawObj["song_info"]?.jsonObjectOrNull() ?: rawObj
            parseKugouTrackItem(obj)?.let { return it }

            val audioInfo = obj["audio_info"]?.jsonObjectOrNull()
            val transParam = obj["trans_param"]?.jsonObjectOrNull()
            val albumInfo = obj["album_info"]?.jsonObjectOrNull()

            // 首页刷歌与部分新接口返回 hash_128 / hash_320 / hash_flac
            val hash = listOf(
                obj.string("hash"),
                obj.string("hash_128"),
                obj.string("hash_320"),
                obj.string("hash_flac"),
                audioInfo?.string("hash").orEmpty(),
                audioInfo?.string("hash_128").orEmpty()
            ).firstOrNull { it.isNotBlank() }?.trim()?.lowercase() ?: return null

            val rawName = obj.firstNonBlank("songname", "song_name", "audio_name", "ori_audio_name", "name", "SongName", "filename")
            val rawSinger = obj.firstNonBlank("author_name", "singername", "author", "SingerName")
            val (resolvedName, resolvedSinger) = resolveKugouTitleAndSinger(
                title = stripKugouEmTags(rawName),
                singer = stripKugouEmTags(rawSinger),
                filename = stripKugouEmTags(obj.string("filename"))
            )

            val singers = buildList {
                val singersArr = obj["singers"]?.jsonArrayOrNull() ?: obj["authors"]?.jsonArrayOrNull()
                singersArr?.forEach { elem ->
                    val sObj = elem.jsonObjectOrNull() ?: return@forEach
                    val sName = stripKugouEmTags(sObj.firstNonBlank("name", "author_name"))
                    if (sName.isNotBlank()) {
                        add(
                            KugouTrackArtistDto(
                                id = sObj.firstNonBlank("id", "author_id"),
                                name = sName,
                                avatarUrl = normalizeKugouCoverUrl(sObj.firstNonBlank("avatar", "sizable_avatar"))
                            )
                        )
                    }
                }
                if (isEmpty() && resolvedSinger.isNotBlank()) {
                    resolvedSinger.split("、", "/", ",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                        add(KugouTrackArtistDto(name = it))
                    }
                }
            }

            val albumId = obj.firstNonBlank("album_id", "ori_album_id", "AlbumID")
                .ifBlank { albumInfo?.firstNonBlank("album_id").orEmpty() }
            val albumName = stripKugouEmTags(
                obj.firstNonBlank("album_name", "albumname", "AlbumName")
                    .ifBlank { albumInfo?.firstNonBlank("album_name").orEmpty() }
            )
            val albumAudioId = obj.firstPositiveLong("album_audio_id", "AlbumAudioID")
                .takeIf { it > 0L } ?: audioInfo?.firstPositiveLong("album_audio_id") ?: 0L
            val mixSongId = obj.firstPositiveLong("mixsongid", "MixSongID", "audio_id", "songid", "id")
                .takeIf { it > 0L } ?: audioInfo?.firstPositiveLong("mixsongid", "audio_id") ?: 0L
            val rawDur = obj.firstPositiveLong(
                "timelen", "duration", "time_length", "timelength",
                "timelength_128", "timelength_320", "timelength_flac"
            ).takeIf { it > 0L } ?: audioInfo?.firstPositiveLong("timelength", "duration") ?: 0L

            val coverUrl = normalizeKugouCoverUrl(
                listOf(
                    transParam?.string("union_cover").orEmpty(),
                    obj.firstNonBlank("album_cover", "cover", "pic", "image", "img", "sizable_cover", "album_sizable_cover"),
                    albumInfo?.firstNonBlank("sizable_cover", "cover").orEmpty()
                ).firstOrNull { it.isNotBlank() }
            )

            val maxQuality = resolveKugouMaxQualityFromObj(obj) ?: listOfNotNull(
                UnifiedAudioQuality.EXHIGH.takeIf { obj.string("hash_320").isNotBlank() || obj.long("filesize_320") > 0L },
                UnifiedAudioQuality.LOSSLESS.takeIf { obj.string("hash_flac").isNotBlank() || obj.long("filesize_flac") > 0L },
                UnifiedAudioQuality.HIRES.takeIf { obj.string("hash_high").isNotBlank() },
                UnifiedAudioQuality.STANDARD.takeIf { hash.isNotBlank() }
            ).maxByOrNull { it.rank }

            val privilege = obj.int("privilege", -1)
            val fee = if (privilege != 0 && obj.int("pay_type", 0) > 0) 1 else 0

            return KugouTrackItemDto(
                hash = hash,
                name = resolvedName.ifBlank { hash },
                artists = singers,
                albumId = albumId,
                albumName = albumName,
                albumAudioId = albumAudioId,
                mixSongId = mixSongId,
                durationMs = normalizeKugouDurationMs(rawDur),
                coverUrl = coverUrl,
                maxQuality = maxQuality,
                fee = fee,
                playable = obj.int("status", 1) != 0
            )
        }

        // 按华语、欧美、日、韩顺序合并新碟上架并按 albumId 去重，最多取 30 条
        internal fun parseNewAlbums(root: JsonObject?, limit: Int = 30): List<KugouNewAlbumDto> {
            val dataObj = root?.get("data")?.jsonObjectOrNull() ?: return emptyList()
            val regions = listOf("chn", "eur", "jpn", "kor")
            val seen = HashSet<Long>()
            val result = mutableListOf<KugouNewAlbumDto>()
            for (region in regions) {
                val arr = dataObj[region]?.jsonArrayOrNull() ?: continue
                for (elem in arr) {
                    val obj = elem.jsonObjectOrNull() ?: continue
                    val albumId = obj.firstPositiveLong("albumid", "album_id")
                    val albumName = obj.firstNonBlank("albumname", "album_name")
                    if (albumId <= 0L || albumName.isBlank() || !seen.add(albumId)) continue
                    result += KugouNewAlbumDto(
                        albumId = albumId,
                        albumName = albumName,
                        singerName = obj.firstNonBlank("singername", "singer_name", "author_name"),
                        coverUrl = normalizeKugouCoverUrl(obj.firstNonBlank("imgurl", "img_url", "cover")),
                        publishTime = obj.firstNonBlank("publishtime", "publish_time"),
                        songCount = obj.int("songcount", 0)
                    )
                    if (result.size >= limit) return result
                }
            }
            return result
        }
    }
}
