package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.feature.search.domain.SearchPageResult
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "PluginNetease"
private const val CONFIRM_TIMEOUT_MS = 60_000L
private const val WRITE_LIMIT = 30
private const val WRITE_WINDOW_MS = 60_000L
private const val MAX_IDS = 200
private const val MAX_SEARCH_LIMIT = 100
private const val MAX_NAME_LENGTH = 40

// 插件能调用的全部网易方法；不在表里的一律拒绝。confirmEach 用于不可恢复的操作
enum class PluginNeteaseMethod(val key: String, val write: Boolean = false, val confirmEach: Boolean = false) {
    SONG_DETAIL("song.detail"),
    SONG_LYRIC("song.lyric"),
    SEARCH("search"),
    PLAYLIST_DETAIL("playlist.detail"),
    ALBUM_DETAIL("album.detail"),
    ARTIST_DETAIL("artist.detail"),
    ARTIST_TOP_SONGS("artist.topSongs"),
    USER_PLAYLISTS("user.playlists"),
    USER_LIKED_SONGS("user.likedSongs"),
    SONG_LIKE("song.like", write = true),
    PLAYLIST_CREATE("playlist.create", write = true),
    PLAYLIST_ADD_TRACKS("playlist.addTracks", write = true),
    PLAYLIST_REMOVE_TRACKS("playlist.removeTracks", write = true),
    PLAYLIST_SUBSCRIBE("playlist.subscribe", write = true),
    ALBUM_SUBSCRIBE("album.subscribe", write = true),
    ARTIST_FOLLOW("artist.follow", write = true),
    PLAYLIST_DELETE("playlist.delete", write = true, confirmEach = true);

    companion object {
        fun fromKey(key: String): PluginNeteaseMethod? = entries.firstOrNull { it.key == key }
    }
}

// 宿主侧能力，由现有 Repository 实现；uid 为 null 表示未登录
interface PluginNeteaseBackend {
    suspend fun uid(): Long?
    suspend fun songDetails(ids: List<Long>): Result<List<Track>>
    suspend fun lyrics(id: Long): Result<List<LyricLine>>
    suspend fun search(keyword: String, type: SearchType, offset: Int, limit: Int): Result<SearchPageResult>
    suspend fun playlistDetail(id: Long): Result<PlaylistDetail>
    suspend fun albumDetail(id: Long): Result<PlaylistDetail>
    suspend fun artistDetail(id: Long): Result<ArtistDetailInfo>
    suspend fun artistTopSongs(id: Long): Result<List<Track>>
    suspend fun userPlaylists(uid: Long): Result<List<UserPlaylist>>
    fun likedSongIds(): Set<Long>
    suspend fun likeSong(id: Long, like: Boolean): Result<Unit>
    suspend fun createPlaylist(name: String, privacy: Int): Result<PlaylistDetail>
    suspend fun addTracks(playlistId: Long, trackIds: List<Long>): Result<Unit>
    suspend fun removeTracks(playlistId: Long, trackIds: List<Long>): Result<Unit>
    suspend fun subscribePlaylist(id: Long, subscribe: Boolean): Result<Unit>
    suspend fun subscribeAlbum(id: Long, subscribe: Boolean): Result<Unit>
    suspend fun followArtist(id: Long, follow: Boolean): Result<Unit>
    suspend fun deletePlaylist(id: Long): Result<Unit>
}

data class PluginConfirmRequest(val title: String, val message: String)

// 需要用户确认的请求排队展示；超时按拒绝处理
class PluginConfirmations {
    private val queue = Mutex()
    private val answer = MutableStateFlow<Boolean?>(null)
    private val _pending = MutableStateFlow<PluginConfirmRequest?>(null)
    val pending: StateFlow<PluginConfirmRequest?> = _pending.asStateFlow()

    suspend fun ask(title: String, message: String): Boolean = queue.withLock {
        answer.value = null
        _pending.value = PluginConfirmRequest(title, message)
        try {
            withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { answer.filterNotNull().first() } ?: false
        } finally {
            _pending.value = null
        }
    }

    fun respond(accepted: Boolean) {
        if (_pending.value != null) answer.value = accepted
    }
}

// 按插件限频，窗口内超过上限直接拒绝
private class PluginWriteLimiter(private val now: () -> Long) {
    private val calls = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun tryAcquire(pluginId: String): Boolean {
        val history = calls.getOrPut(pluginId) { ArrayDeque() }
        synchronized(history) {
            val threshold = now() - WRITE_WINDOW_MS
            while (history.isNotEmpty() && history.first() <= threshold) history.removeFirst()
            if (history.size >= WRITE_LIMIT) return false
            history.addLast(now())
            return true
        }
    }
}

// 插件访问网易账号的唯一入口：校验方法、权限与登录态，响应裁剪后再交给插件
class PluginNeteaseApi(
    private val backend: PluginNeteaseBackend,
    private val store: PluginStore,
    private val confirmations: PluginConfirmations,
    now: () -> Long = System::currentTimeMillis
) {
    private val limiter = PluginWriteLimiter(now)

    suspend fun call(manifest: PluginManifest, methodKey: String, params: JsonObject): JsonElement {
        val method = PluginNeteaseMethod.fromKey(methodKey) ?: throw PluginCallException("不支持的网易接口 $methodKey")
        val permission = if (method.write) PluginPermission.NETEASE_WRITE else PluginPermission.NETEASE_READ
        if (permission !in manifest.permissions) throw PluginCallException("缺少权限 ${permission.key}")
        return if (method.write) write(manifest, method, params) else read(method, params)
    }

    fun likedSongIds(manifest: PluginManifest): JsonElement {
        requireLibrary(manifest)
        return buildJsonArray { backend.likedSongIds().forEach { add(JsonPrimitive(it)) } }
    }

    suspend fun libraryPlaylists(manifest: PluginManifest): JsonElement {
        requireLibrary(manifest)
        return userPlaylists()
    }

    private fun requireLibrary(manifest: PluginManifest) {
        if (PluginPermission.LIBRARY_READ !in manifest.permissions) {
            throw PluginCallException("缺少权限 ${PluginPermission.LIBRARY_READ.key}")
        }
    }

    private suspend fun read(method: PluginNeteaseMethod, params: JsonObject): JsonElement = when (method) {
        PluginNeteaseMethod.SONG_DETAIL -> tracksJson(backend.songDetails(params.ids("ids")).unwrap())
        PluginNeteaseMethod.SONG_LYRIC -> lyricsJson(backend.lyrics(params.id("id")).unwrap())
        PluginNeteaseMethod.SEARCH -> search(params)
        PluginNeteaseMethod.PLAYLIST_DETAIL -> playlistJson(backend.playlistDetail(params.id("id")).unwrap())
        PluginNeteaseMethod.ALBUM_DETAIL -> playlistJson(backend.albumDetail(params.id("id")).unwrap())
        PluginNeteaseMethod.ARTIST_DETAIL -> artistJson(backend.artistDetail(params.id("id")).unwrap())
        PluginNeteaseMethod.ARTIST_TOP_SONGS -> tracksJson(backend.artistTopSongs(params.id("id")).unwrap())
        PluginNeteaseMethod.USER_PLAYLISTS -> userPlaylists()
        PluginNeteaseMethod.USER_LIKED_SONGS -> {
            requireUid()
            buildJsonArray { backend.likedSongIds().forEach { add(JsonPrimitive(it)) } }
        }
        else -> throw PluginCallException("不支持的网易接口 ${method.key}")
    }

    private suspend fun write(manifest: PluginManifest, method: PluginNeteaseMethod, params: JsonObject): JsonElement {
        val uid = requireUid()
        // 参数与归属先校验，避免无效调用弹确认或占用额度
        val ownedPlaylist = when (method) {
            PluginNeteaseMethod.PLAYLIST_ADD_TRACKS,
            PluginNeteaseMethod.PLAYLIST_REMOVE_TRACKS,
            PluginNeteaseMethod.PLAYLIST_DELETE -> ownPlaylist(uid, params.id("id"), forDelete = method.confirmEach)
            else -> null
        }
        ensureApproved(manifest, method, ownedPlaylist)
        if (!limiter.tryAcquire(manifest.id)) throw PluginCallException("写入过于频繁，请稍后再试")
        val result = perform(method, params)
        AppLogger.i(TAG, "[${manifest.id}] ${method.key} ${if (result.isSuccess) "成功" else "失败"}")
        return result.unwrap()
    }

    private suspend fun perform(method: PluginNeteaseMethod, params: JsonObject): Result<JsonElement> = when (method) {
        PluginNeteaseMethod.SONG_LIKE -> backend.likeSong(params.id("id"), params.flag("like")).asNull()
        PluginNeteaseMethod.PLAYLIST_CREATE -> {
            val name = params["name"].let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }?.trim()?.take(MAX_NAME_LENGTH)
            if (name.isNullOrEmpty()) throw PluginCallException("缺少歌单名称")
            val privacy = (params["privacy"] as? JsonPrimitive)?.intOrNull ?: 0
            if (privacy != 0 && privacy != PRIVATE_PLAYLIST) throw PluginCallException("privacy 只能是 0 或 $PRIVATE_PLAYLIST")
            backend.createPlaylist(name, privacy).map { playlistJson(it) }
        }
        PluginNeteaseMethod.PLAYLIST_ADD_TRACKS -> backend.addTracks(params.id("id"), params.ids("trackIds")).asNull()
        PluginNeteaseMethod.PLAYLIST_REMOVE_TRACKS -> backend.removeTracks(params.id("id"), params.ids("trackIds")).asNull()
        PluginNeteaseMethod.PLAYLIST_SUBSCRIBE -> backend.subscribePlaylist(params.id("id"), params.flag("subscribe")).asNull()
        PluginNeteaseMethod.ALBUM_SUBSCRIBE -> backend.subscribeAlbum(params.id("id"), params.flag("subscribe")).asNull()
        PluginNeteaseMethod.ARTIST_FOLLOW -> backend.followArtist(params.id("id"), params.flag("follow")).asNull()
        PluginNeteaseMethod.PLAYLIST_DELETE -> backend.deletePlaylist(params.id("id")).asNull()
        else -> throw PluginCallException("不支持的网易接口 ${method.key}")
    }

    // 每个插件首次写入确认一次并记住；不可恢复的操作每次都确认
    private suspend fun ensureApproved(manifest: PluginManifest, method: PluginNeteaseMethod, playlist: UserPlaylist?) {
        val approved = store.state(manifest.id).neteaseWriteApproved
        if (approved && !method.confirmEach) return
        val message = if (method.confirmEach) {
            "「${manifest.name}」请求删除歌单「${playlist?.name.orEmpty()}」，删除后无法恢复。"
        } else {
            "「${manifest.name}」请求修改你的网易云账号（收藏、歌单与关注）。允许后不再询问，重新安装插件时需要再次确认。"
        }
        if (!confirmations.ask("允许插件修改账号？", message)) throw PluginCallException("用户拒绝了写入")
        if (!approved) store.updateState(manifest.id) { it.copy(neteaseWriteApproved = true) }
    }

    private suspend fun ownPlaylist(uid: Long, id: Long, forDelete: Boolean): UserPlaylist {
        val playlist = backend.userPlaylists(uid).unwrap().firstOrNull { it.id == id && (it.creator?.userId ?: it.userId) == uid }
            ?: throw PluginCallException("只能操作自己创建的歌单")
        if (forDelete && playlist.isLikedPlaylist) throw PluginCallException("不能删除「我喜欢的音乐」")
        return playlist
    }

    private suspend fun requireUid(): Long = backend.uid() ?: throw PluginCallException("用户尚未登录")

    private suspend fun userPlaylists(): JsonElement {
        val playlists = backend.userPlaylists(requireUid()).unwrap()
        return buildJsonArray {
            playlists.forEach { playlist ->
                add(buildJsonObject {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("cover", playlist.coverImgUrl)
                    put("trackCount", playlist.trackCount)
                    put("liked", playlist.isLikedPlaylist)
                })
            }
        }
    }

    private suspend fun search(params: JsonObject): JsonElement {
        val keyword = (params["keyword"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
        if (keyword.isNullOrEmpty()) throw PluginCallException("缺少搜索词")
        val type = when (val key = (params["type"] as? JsonPrimitive)?.content ?: "song") {
            "song" -> SearchType.SONG
            "album" -> SearchType.ALBUM
            "artist" -> SearchType.ARTIST
            "playlist" -> SearchType.PLAYLIST
            else -> throw PluginCallException("不支持的搜索类型 $key")
        }
        val offset = (params["offset"] as? JsonPrimitive)?.intOrNull?.coerceAtLeast(0) ?: 0
        val limit = (params["limit"] as? JsonPrimitive)?.intOrNull?.coerceIn(1, MAX_SEARCH_LIMIT) ?: DEFAULT_SEARCH_LIMIT
        val page = backend.search(keyword, type, offset, limit).unwrap()
        return buildJsonObject {
            put("total", page.totalCount)
            put("hasMore", page.hasMore)
            put("items", JsonArray(page.items.mapNotNull(::searchItemJson)))
        }
    }

    private fun searchItemJson(item: SearchResultItem): JsonElement? = when (item) {
        is SearchResultItem.SongItem -> trackJson(item.track)
        is SearchResultItem.AlbumItem -> albumJson(item.album)
        is SearchResultItem.ArtistItem -> buildJsonObject {
            put("id", item.artist.id)
            put("name", item.artist.name)
            put("cover", item.artist.picUrl)
        }
        is SearchResultItem.PlaylistItem -> playlistJson(item.playlist.copy(tracks = emptyList()))
        else -> null
    }

    private fun tracksJson(tracks: List<Track>): JsonElement = JsonArray(tracks.map(::trackJson))

    private fun trackJson(track: Track): JsonElement = buildJsonObject {
        if (track.id > 0) put("songId", track.id)
        put("title", track.name)
        put("artist", track.ar.joinToString(" / ") { it.name })
        putJsonArray("artists") {
            track.ar.forEach { artist ->
                add(buildJsonObject {
                    put("id", artist.id)
                    put("name", artist.name)
                })
            }
        }
        put("album", track.al.name)
        put("albumId", track.al.id)
        put("cover", track.al.picUrl)
        put("durationMs", track.dt)
    }

    private fun albumJson(album: Album): JsonElement = buildJsonObject {
        put("id", album.id)
        put("name", album.name)
        put("cover", album.picUrl)
        put("artist", album.artists.joinToString(" / ") { it.name })
    }

    private fun playlistJson(playlist: PlaylistDetail): JsonElement = buildJsonObject {
        put("id", playlist.id)
        put("name", playlist.name)
        put("cover", playlist.coverImgUrl)
        put("description", playlist.description.orEmpty())
        put("playCount", playlist.playCount)
        put("trackCount", maxOf(playlist.trackCount, playlist.tracks.size))
        put("subscribed", playlist.subscribed)
        put("creator", playlist.creator?.nickname.orEmpty())
        put("tracks", tracksJson(playlist.tracks))
    }

    private fun artistJson(artist: ArtistDetailInfo): JsonElement = buildJsonObject {
        put("id", artist.id)
        put("name", artist.name)
        put("cover", artist.cover)
        put("avatar", artist.avatar)
        put("description", artist.briefDesc)
        put("albumCount", artist.albumSize)
        put("songCount", artist.musicSize)
    }

    private fun lyricsJson(lines: List<LyricLine>): JsonElement = JsonArray(
        lines.map { line ->
            buildJsonObject {
                put("timeMs", line.timeMs)
                put("text", line.text)
                line.translation?.let { put("translation", it) }
            }
        }
    )

    private fun <T> Result<T>.unwrap(): T = getOrElse { e ->
        if (e is CancellationException) throw e
        throw PluginCallException(e.message ?: "请求失败")
    }

    private fun Result<Unit>.asNull(): Result<JsonElement> = map { JsonNull }

    private fun JsonObject.id(key: String): Long =
        (this[key] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: throw PluginCallException("缺少有效的 $key")

    private fun JsonObject.flag(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: throw PluginCallException("缺少参数 $key")

    private fun JsonObject.ids(key: String): List<Long> {
        val values = (this[key] as? JsonArray)?.map { (it as? JsonPrimitive)?.longOrNull?.takeIf { id -> id > 0 } }.orEmpty()
        if (values.isEmpty() || values.any { it == null }) throw PluginCallException("缺少有效的 $key")
        if (values.size > MAX_IDS) throw PluginCallException("$key 不能超过 $MAX_IDS 个")
        return values.filterNotNull()
    }

    private companion object {
        const val PRIVATE_PLAYLIST = 10
        const val DEFAULT_SEARCH_LIMIT = 30
    }
}

