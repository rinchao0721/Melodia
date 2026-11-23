package com.lin0721.linmusic.core.kugou

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// 酷狗搜索服务（单曲/专辑/歌单/歌手/歌词/MV/综合搜索/热搜/搜索建议）
class KugouSearchService(
    private val config: KugouConfig,
    private val gatewayClient: KugouGatewayClient
) {
    // module/search.js
    suspend fun search(
        keyword: String,
        type: KugouSearchType = KugouSearchType.SONG,
        page: Int = 1,
        pageSize: Int = 30,
        sortType: Int = 0,
        searchSong: Int = 0,
        tag: String? = null
    ): Result<KugouSearchResultPage> = runCatchingCancellable {
        config.ensureModeSynced()
        val safePage = page.coerceAtLeast(1)
        val headers = mapOf("x-router" to "complexsearch.kugou.com")
        val isLite = config.mode.isLite

        val (url, params, overrideClientVer) = when (type) {
            KugouSearchType.SONG -> {
                val map = LinkedHashMap<String, Any?>().apply {
                    put("keyword", keyword)
                    put("page", safePage)
                    put("pagesize", pageSize)
                    put("platform", "AndroidFilter")
                    put("iscorrection", 1)
                    put("privilegefilter", 0)
                    put("area_code", 1)
                    put("dopicfull", 1)
                    if (!tag.isNullOrBlank()) put("tag", tag)
                }
                Triple("/v2/search/song", map, if (isLite) 201 else null)
            }
            KugouSearchType.ALBUM -> {
                val map = LinkedHashMap<String, Any?>().apply {
                    put("keyword", keyword)
                    put("page", safePage)
                    put("pagesize", pageSize)
                    put("platform", "AndroidFilter")
                    put("iscorrection", 1)
                    put("category", "1")
                    put("sorttype", if (sortType == 1) 1 else 0)
                    put("searchsong", if (searchSong == 1) 1 else 0)
                    if (!tag.isNullOrBlank()) put("tag", tag)
                }
                Triple("/v1/search/album", map, if (isLite) 201 else null)
            }
            KugouSearchType.SPECIAL,
            KugouSearchType.AUTHOR,
            KugouSearchType.LYRIC,
            KugouSearchType.MV -> {
                val map = mapOf<String, Any?>(
                    "albumhide" to 0,
                    "iscorrection" to 1,
                    "keyword" to keyword,
                    "nocollect" to 0,
                    "page" to safePage,
                    "pagesize" to pageSize,
                    "platform" to "AndroidFilter"
                )
                Triple("/v1/search/${type.wireValue}", map, null)
            }
        }

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                url = url,
                params = params,
                headers = headers,
                encryptType = KugouEncryptType.ANDROID,
                overrideClientVer = overrideClientVer
            )
        )
        ensureKugouBizSuccess(response, "酷狗搜索失败")

        val dataObj = response.bodyJson?.get("data")?.jsonObjectOrNull() ?: JsonObject(emptyMap())
        val rawLists = extractListArray(dataObj)
        val total = dataObj.int("total", 0).takeIf { it > 0 } ?: rawLists.size

        parseTypedSearchPage(
            type = type,
            rawLists = rawLists,
            total = total,
            page = safePage,
            pageSize = pageSize
        )
    }

    // module/search_complex.js
    suspend fun complexSearch(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 30
    ): Result<KugouComplexSearchResult> = runCatchingCancellable {
        val safePage = page.coerceAtLeast(1)
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                baseUrl = "https://complexsearch.kugou.com",
                url = "/v6/search/complex",
                params = mapOf(
                    "platform" to "AndroidFilter",
                    "keyword" to keyword,
                    "page" to safePage,
                    "pagesize" to pageSize,
                    "cursor" to 0
                ),
                encryptType = KugouEncryptType.ANDROID
            )
        )
        ensureKugouBizSuccess(response, "酷狗综合搜索失败")

        val dataObj = response.bodyJson?.get("data")?.jsonObjectOrNull() ?: JsonObject(emptyMap())
        val songs = ArrayList<KugouTrackItemDto>()
        val albums = ArrayList<KugouSearchAlbumDto>()
        val artists = ArrayList<KugouSearchArtistDto>()
        val playlists = ArrayList<KugouSearchPlaylistDto>()

        fun consumeNamedBuckets(container: JsonObject) {
            extractSectionLists(container["song"]).mapNotNullTo(songs, ::parseKugouTrackItem)
            extractSectionLists(container["album"]).mapNotNullTo(albums, ::parseSearchAlbum)
            extractSectionLists(container["author"]).mapNotNullTo(artists, ::parseSearchArtist)
            extractSectionLists(container["special"]).mapNotNullTo(playlists, ::parseSearchPlaylist)
        }

        consumeNamedBuckets(dataObj)
        when (val listsNode = dataObj["lists"]) {
            is JsonObject -> consumeNamedBuckets(listsNode)
            is JsonArray -> listsNode.forEach { elem ->
                val sec = elem.jsonObjectOrNull() ?: return@forEach
                val secType = sec.firstNonBlank("type", "module_type", "tab_type").lowercase()
                val items = extractListArray(sec)
                when {
                    secType.contains("song") -> items.mapNotNullTo(songs) { it.jsonObjectOrNull()?.let(::parseKugouTrackItem) }
                    secType.contains("album") -> items.mapNotNullTo(albums) { it.jsonObjectOrNull()?.let(::parseSearchAlbum) }
                    secType.contains("author") || secType.contains("singer") ->
                        items.mapNotNullTo(artists) { it.jsonObjectOrNull()?.let(::parseSearchArtist) }
                    secType.contains("special") || secType.contains("playlist") ->
                        items.mapNotNullTo(playlists) { it.jsonObjectOrNull()?.let(::parseSearchPlaylist) }
                    else -> {
                        // 无显式 type 时按单曲兜底解析
                        items.mapNotNullTo(songs) { it.jsonObjectOrNull()?.let(::parseKugouTrackItem) }
                    }
                }
            }
            else -> Unit
        }

        KugouComplexSearchResult(
            songs = songs,
            albums = albums,
            artists = artists,
            playlists = playlists
        )
    }

    // module/search_hot.js
    suspend fun getHotSearches(): Result<List<KugouHotSearchItem>> = runCatchingCancellable {
        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                url = "/api/v3/search/hot_tab",
                params = mapOf(
                    "navid" to 1,
                    "plat" to 2
                ),
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf("x-router" to "msearch.kugou.com")
            )
        )
        ensureKugouBizSuccess(response, "获取酷狗热搜失败")

        val dataNode = response.bodyJson?.get("data")
        val rawTabsOrList = when (dataNode) {
            is JsonObject -> dataNode["list"]?.jsonArrayOrNull()
                ?: dataNode["info"]?.jsonArrayOrNull()
                ?: emptyList()
            is JsonArray -> dataNode
            else -> emptyList()
        }

        val collected = ArrayList<KugouHotSearchItem>()
        for (elem in rawTabsOrList) {
            val obj = elem.jsonObjectOrNull() ?: continue
            val nested = obj["keywords"]?.jsonArrayOrNull() ?: obj["list"]?.jsonArrayOrNull()
            if (nested != null) {
                nested.mapNotNull { it.jsonObjectOrNull()?.let(::parseHotItem) }
                    .forEach { collected.add(it) }
            } else {
                parseHotItem(obj)?.let { collected.add(it) }
            }
        }
        collected.distinctBy { it.keyword }
    }

    // module/search_suggest.js
    suspend fun getSuggestions(keyword: String): Result<KugouSearchSuggestResult> = runCatchingCancellable {
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isEmpty()) return@runCatchingCancellable KugouSearchSuggestResult()

        val response = gatewayClient.executeRequest(
            KugouRequestOptions(
                method = "GET",
                url = "/v2/getSearchTip",
                params = mapOf(
                    "keyword" to cleanKeyword,
                    "AlbumTipCount" to 10,
                    "CorrectTipCount" to 10,
                    "MVTipCount" to 10,
                    "MusicTipCount" to 10,
                    "radiotip" to 1
                ),
                encryptType = KugouEncryptType.ANDROID,
                headers = mapOf("x-router" to "searchtip.kugou.com")
            )
        )
        ensureKugouBizSuccess(response, "获取酷狗搜索建议失败")

        val dataNode = response.bodyJson?.get("data")
        val keywords = ArrayList<KugouSearchSuggestItem>()
        val albums = ArrayList<KugouSearchAlbumDto>()
        val artists = ArrayList<KugouSearchArtistDto>()

        fun consumeTipEntry(obj: JsonObject, label: String) {
            val hint = stripKugouEmTags(obj.firstNonBlank("HintInfo", "keyword", "name"))
            if (hint.isBlank()) return
            keywords += KugouSearchSuggestItem(
                keyword = hint,
                category = label,
                hot = obj.int("Hot", 0)
            )
        }

        when (dataNode) {
            is JsonArray -> dataNode.forEach { elem ->
                val sec = elem.jsonObjectOrNull() ?: return@forEach
                val label = sec.firstNonBlank("LableName", "LabelName", "label")
                val records = sec["RecordDatas"]?.jsonArrayOrNull()
                if (records != null) {
                    records.forEach { r -> r.jsonObjectOrNull()?.let { consumeTipEntry(it, label) } }
                } else {
                    consumeTipEntry(sec, label)
                }
            }
            is JsonObject -> {
                dataNode["RecordDatas"]?.jsonArrayOrNull()?.forEach { r ->
                    r.jsonObjectOrNull()?.let { consumeTipEntry(it, "") }
                }
                dataNode["MusicTip"]?.jsonArrayOrNull()?.forEach { r ->
                    r.jsonObjectOrNull()?.let { consumeTipEntry(it, "单曲") }
                }
                dataNode["CorrectTip"]?.jsonArrayOrNull()?.forEach { r ->
                    r.jsonObjectOrNull()?.let { consumeTipEntry(it, "热词") }
                }
                dataNode["AlbumTip"]?.jsonArrayOrNull()?.forEach { r ->
                    r.jsonObjectOrNull()?.let {
                        consumeTipEntry(it, "专辑")
                        parseSearchAlbum(it)?.let(albums::add)
                    }
                }
            }
            else -> Unit
        }

        KugouSearchSuggestResult(
            keywords = keywords.distinctBy { it.keyword },
            albums = albums,
            artists = artists
        )
    }

    private fun parseTypedSearchPage(
        type: KugouSearchType,
        rawLists: List<JsonElement>,
        total: Int,
        page: Int,
        pageSize: Int
    ): KugouSearchResultPage {
        val fetchedSize = rawLists.size
        val hasMore = fetchedSize > 0 && ((page - 1) * pageSize + fetchedSize < total || fetchedSize >= pageSize)

        return when (type) {
            KugouSearchType.SONG -> KugouSearchResultPage(
                type = type,
                totalCount = total,
                hasMore = hasMore,
                songs = rawLists.mapNotNull { it.jsonObjectOrNull()?.let(::parseKugouTrackItem) }
            )
            KugouSearchType.ALBUM -> KugouSearchResultPage(
                type = type,
                totalCount = total,
                hasMore = hasMore,
                albums = rawLists.mapNotNull { it.jsonObjectOrNull()?.let(::parseSearchAlbum) }
            )
            KugouSearchType.SPECIAL -> KugouSearchResultPage(
                type = type,
                totalCount = total,
                hasMore = hasMore,
                playlists = rawLists.mapNotNull { it.jsonObjectOrNull()?.let(::parseSearchPlaylist) }
            )
            KugouSearchType.AUTHOR -> KugouSearchResultPage(
                type = type,
                totalCount = total,
                hasMore = hasMore,
                artists = rawLists.mapNotNull { it.jsonObjectOrNull()?.let(::parseSearchArtist) }
            )
            KugouSearchType.LYRIC -> KugouSearchResultPage(
                type = type,
                totalCount = total,
                hasMore = hasMore,
                lyrics = rawLists.mapNotNull { elem ->
                    val obj = elem.jsonObjectOrNull() ?: return@mapNotNull null
                    val track = parseKugouTrackItem(obj) ?: return@mapNotNull null
                    KugouSearchLyricDto(
                        track = track,
                        lyricSnippet = stripKugouEmTags(obj.firstNonBlank("lyric", "Lyric"))
                    )
                }
            )
            KugouSearchType.MV -> KugouSearchResultPage(
                type = type,
                totalCount = total,
                hasMore = hasMore,
                mvs = rawLists.mapNotNull { elem ->
                    val obj = elem.jsonObjectOrNull() ?: return@mapNotNull null
                    val hash = obj.firstNonBlank("MvHash", "mvhash", "hash").lowercase()
                    if (hash.isBlank()) return@mapNotNull null
                    KugouSearchMvDto(
                        hash = hash,
                        name = stripKugouEmTags(obj.firstNonBlank("MvName", "mvname", "name")),
                        singerName = stripKugouEmTags(obj.firstNonBlank("SingerName", "singername")),
                        coverUrl = normalizeKugouCoverUrl(obj.firstNonBlank("Pic", "pic", "img")),
                        durationMs = normalizeKugouDurationMs(obj.firstPositiveLong("Duration", "duration"))
                    )
                }
            )
        }
    }

    private fun extractListArray(obj: JsonObject): List<JsonElement> =
        obj["lists"]?.jsonArrayOrNull()
            ?: obj["info"]?.jsonArrayOrNull()
            ?: obj["list"]?.jsonArrayOrNull()
            ?: emptyList()

    private fun extractSectionLists(node: JsonElement?): List<JsonObject> = when (node) {
        is JsonObject -> extractListArray(node).mapNotNull { it.jsonObjectOrNull() }
        is JsonArray -> node.mapNotNull { it.jsonObjectOrNull() }
        else -> emptyList()
    }

    private fun parseSearchAlbum(obj: JsonObject): KugouSearchAlbumDto? {
        val albumId = obj.firstNonBlank("albumid", "album_id", "AlbumID")
        val name = stripKugouEmTags(obj.firstNonBlank("albumname", "album_name", "AlbumName"))
        if (albumId.isBlank() && name.isBlank()) return null
        return KugouSearchAlbumDto(
            albumId = albumId.ifBlank { name },
            name = name.ifBlank { albumId },
            coverUrl = normalizeKugouCoverUrl(obj.firstNonBlank("img", "sizable_cover", "cover", "pic")),
            singerName = stripKugouEmTags(obj.firstNonBlank("singer", "singername", "author_name")),
            publishTimeMs = parseKugouDateTimeMs(obj.firstNonBlank("publish_time", "publish_date")),
            songCount = obj.int("songcount", 0).takeIf { it > 0 } ?: obj.int("song_count", 0),
            intro = obj.string("intro").trim()
        )
    }

    private fun parseSearchArtist(obj: JsonObject): KugouSearchArtistDto? {
        val authorId = obj.firstNonBlank("AuthorId", "author_id", "singerid", "id")
        val name = stripKugouEmTags(obj.firstNonBlank("AuthorName", "author_name", "singername", "name"))
        if (authorId.isBlank() && name.isBlank()) return null
        return KugouSearchArtistDto(
            authorId = authorId.ifBlank { name },
            name = name.ifBlank { authorId },
            avatarUrl = normalizeKugouCoverUrl(obj.firstNonBlank("Avatar", "avatar", "sizable_avatar", "pic")),
            albumCount = obj.int("AlbumCount", 0).takeIf { it > 0 } ?: obj.int("albumcount", 0),
            songCount = obj.int("AudioCount", 0).takeIf { it > 0 } ?: obj.int("songcount", 0),
            fansCount = obj.firstPositiveLong("FansNum", "fans_num", "fanscount")
        )
    }

    private fun parseSearchPlaylist(obj: JsonObject): KugouSearchPlaylistDto? {
        val gid = obj.firstNonBlank("gid", "global_collection_id")
        val specialId = obj.firstPositiveLong("specialid", "listid", "id")
        val name = stripKugouEmTags(obj.firstNonBlank("specialname", "name"))
        if (gid.isBlank() && specialId <= 0L) return null
        return KugouSearchPlaylistDto(
            globalCollectionId = gid,
            specialId = specialId,
            name = name,
            coverUrl = normalizeKugouCoverUrl(obj.firstNonBlank("img", "imgurl", "flexible_cover", "pic")),
            creatorName = stripKugouEmTags(obj.firstNonBlank("nickname", "username", "creator")),
            creatorId = obj.firstPositiveLong("suid", "userid", "uid"),
            trackCount = obj.int("song_count", 0).takeIf { it > 0 } ?: obj.int("songcount", 0),
            playCount = obj.firstPositiveLong("play_count", "playcount"),
            intro = obj.string("intro").trim()
        )
    }

    private fun parseHotItem(obj: JsonObject): KugouHotSearchItem? {
        val keyword = stripKugouEmTags(obj.firstNonBlank("keyword", "searchWord", "name"))
        if (keyword.isBlank()) return null
        return KugouHotSearchItem(
            keyword = keyword,
            reason = obj.firstNonBlank("reason", "desc", "description"),
            iconUrl = obj.firstNonBlank("icon", "icon_url").takeIf { it.isNotBlank() }?.let(::normalizeKugouCoverUrl),
            jumpUrl = obj.string("jumpurl").trim()
        )
    }
}
