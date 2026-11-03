package com.lin0721.linmusic.desktop.plugin

import androidx.compose.runtime.Composable
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.plugin.ActionExtension
import com.lin0721.linmusic.core.plugin.AudioSourceExtension
import com.lin0721.linmusic.core.plugin.BackdropExtension
import com.lin0721.linmusic.core.plugin.CommandExtension
import com.lin0721.linmusic.core.plugin.ComposePageExtension
import com.lin0721.linmusic.core.plugin.ContentSourceExtension
import com.lin0721.linmusic.core.plugin.EventExtension
import com.lin0721.linmusic.core.plugin.Extension
import com.lin0721.linmusic.core.plugin.ExtensionPoints
import com.lin0721.linmusic.core.plugin.LyricsSourceExtension
import com.lin0721.linmusic.core.plugin.PluginCallException
import com.lin0721.linmusic.core.plugin.PluginEvent
import com.lin0721.linmusic.core.plugin.PluginHostApi
import com.lin0721.linmusic.core.plugin.PluginManifest
import com.lin0721.linmusic.core.plugin.PluginPaths
import com.lin0721.linmusic.core.plugin.PluginPermission
import com.lin0721.linmusic.core.plugin.PluginTrackQuery
import com.lin0721.linmusic.feature.create.data.playlistPrivacyOf
import com.lin0721.linmusic.plugin.api.AudioSource
import com.lin0721.linmusic.plugin.api.BackdropFrame
import com.lin0721.linmusic.plugin.api.BackdropSource
import com.lin0721.linmusic.plugin.api.ContentPlaylist
import com.lin0721.linmusic.plugin.api.ContentSource
import com.lin0721.linmusic.plugin.api.ContentTrack
import com.lin0721.linmusic.plugin.api.LibraryApi
import com.lin0721.linmusic.plugin.api.LibraryPlaylist
import com.lin0721.linmusic.plugin.api.LyricsSource
import com.lin0721.linmusic.plugin.api.NeteaseAlbum
import com.lin0721.linmusic.plugin.api.NeteaseApi
import com.lin0721.linmusic.plugin.api.NeteaseArtist
import com.lin0721.linmusic.plugin.api.NeteaseArtistRef
import com.lin0721.linmusic.plugin.api.NeteaseLyricLine
import com.lin0721.linmusic.plugin.api.NeteasePlaylist
import com.lin0721.linmusic.plugin.api.NeteaseSearchItem
import com.lin0721.linmusic.plugin.api.NeteaseSearchPage
import com.lin0721.linmusic.plugin.api.NeteaseSearchType
import com.lin0721.linmusic.plugin.api.NeteaseTrack
import com.lin0721.linmusic.plugin.api.NowPlayingSong
import com.lin0721.linmusic.plugin.api.PlaybackListener
import com.lin0721.linmusic.plugin.api.PlaybackState
import com.lin0721.linmusic.plugin.api.PlayerApi
import com.lin0721.linmusic.plugin.api.PluginApiException
import com.lin0721.linmusic.plugin.api.PluginContext
import com.lin0721.linmusic.plugin.api.PluginExtensions
import com.lin0721.linmusic.plugin.api.PluginLogger
import com.lin0721.linmusic.plugin.api.PluginSettings
import com.lin0721.linmusic.plugin.api.PluginStorage
import com.lin0721.linmusic.plugin.api.PluginUi
import com.lin0721.linmusic.plugin.api.QueueSong
import com.lin0721.linmusic.plugin.api.SpectrumApi
import com.lin0721.linmusic.plugin.api.SpectrumFrame
import com.lin0721.linmusic.plugin.api.TrackQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "NativePlugin"

// 把 plugin-api 的类型化调用转成 PluginHostApi 的 JSON 调用，权限、限频与确认都复用沙盒那一套
internal class NativePluginContext(
    private val manifest: PluginManifest,
    private val guard: NativeCallGuard,
    private val api: PluginHostApi,
    private val workDirectory: File,
    dataRoot: File,
    private val spectrumFrames: Flow<SpectrumFrame> = emptyFlow()
) : PluginContext {
    private val registered = ConcurrentHashMap.newKeySet<String>()
    val registrations: Set<String> get() = registered.toSet()

    override val pluginId: String = manifest.id
    override val scope: CoroutineScope = guard.scope
    override val dataDirectory: File = dataRoot.also { it.mkdirs() }

    override val logger: PluginLogger = object : PluginLogger {
        override fun info(message: String) = AppLogger.i(TAG, "[$pluginId] $message")
        override fun warn(message: String, error: Throwable?) = AppLogger.w(TAG, "[$pluginId] $message", error)
        override fun error(message: String, error: Throwable?) = AppLogger.e(TAG, "[$pluginId] $message", error)
    }

    override fun nativeLibrary(fileName: String): File? {
        val path = PluginPaths.NATIVE_LIBRARY_PREFIX + fileName
        return File(workDirectory, path).takeIf { PluginPaths.isNativeLibrary(path) && it.isFile }
    }

    private suspend fun call(method: String, args: JsonObject = JsonObject(emptyMap())): JsonElement = try {
        api.handle(manifest, method, args)
    } catch (e: PluginCallException) {
        throw PluginApiException(e.message.orEmpty())
    }

    private suspend fun netease(method: String, vararg params: Pair<String, Any?>): JsonElement =
        call(PluginHostApi.METHOD_NETEASE, buildJsonObject {
            put("method", method)
            put("params", toJson(params.toMap()))
        })

    override val extensions: PluginExtensions = object : PluginExtensions {
        private fun track(id: String) = registered.add(id)

        override fun audioSource(id: String, source: AudioSource) {
            val order = indexOf(manifest.contributes.audioSources.map { it.id }, id, "音源")
            track("audioSource:$id")
            guard.register(Extension(ExtensionPoints.AUDIO_SOURCE, pluginId, manifest.name, id, object : AudioSourceExtension {
                override suspend fun resolve(query: PluginTrackQuery, quality: String): JsonElement? =
                    guard.guarded("音源 $id", DesktopNativePluginHost.CALL_TIMEOUT_MS) { source.resolve(query.toApi(), quality) }?.let(::JsonPrimitive)
            }, order))
        }

        override fun lyricsSource(id: String, source: LyricsSource) {
            val order = indexOf(manifest.contributes.lyricsSources.map { it.id }, id, "歌词源")
            track("lyricsSource:$id")
            guard.register(Extension(ExtensionPoints.LYRICS_SOURCE, pluginId, manifest.name, id, object : LyricsSourceExtension {
                override suspend fun fetch(query: PluginTrackQuery): JsonElement? {
                    val result = guard.guarded("歌词源 $id", DesktopNativePluginHost.CALL_TIMEOUT_MS) { source.fetch(query.toApi()) } ?: return null
                    return toJson(mapOf("lrc" to result.lrc, "ttml" to result.ttml))
                }
            }, order))
        }

        override fun contentSource(id: String, source: ContentSource) {
            val entries = manifest.contributes.contentSources
            val order = indexOf(entries.map { it.id }, id, "内容源")
            track("contentSource:$id")
            val extension = object : ContentSourceExtension {
                override val searchable: Boolean = entries[order].search

                override suspend fun search(keyword: String, offset: Int, limit: Int): JsonElement? =
                    guard.guarded("内容源 $id 搜索", DesktopNativePluginHost.CALL_TIMEOUT_MS) { source.search(keyword, offset, limit) }?.let(::tracksJson)

                override suspend fun playlist(id: String): JsonElement? =
                    guard.guarded("内容源歌单", DesktopNativePluginHost.CALL_TIMEOUT_MS) { source.playlist(id) }?.let(::playlistJson)

                override suspend fun resolve(trackId: String, quality: String): JsonElement? =
                    guard.guarded("内容源取链", DesktopNativePluginHost.CALL_TIMEOUT_MS) { source.resolve(trackId, quality) }?.let(::JsonPrimitive)
            }
            guard.register(Extension(ExtensionPoints.CONTENT_SOURCE, pluginId, manifest.name, id, extension, order))
        }

        override fun command(id: String, handler: suspend (Map<String, Any?>) -> Unit) {
            val entries = manifest.contributes.commands
            val order = indexOf(entries.map { it.id }, id, "命令")
            track("command:$id")
            val extension = object : CommandExtension {
                override val title: String = entries[order].title

                override suspend fun execute(args: JsonElement): Boolean = runHandler("命令 $id", args, handler)
            }
            guard.register(Extension(ExtensionPoints.COMMAND, pluginId, manifest.name, id, extension, order))
        }

        override fun action(name: String, handler: suspend (Map<String, Any?>) -> Unit) {
            track("action:$name")
            val extension = object : ActionExtension {
                override suspend fun run(args: JsonElement): Boolean = runHandler("动作 $name", args, handler)
            }
            guard.register(Extension(ExtensionPoints.ACTION, pluginId, manifest.name, name, extension))
        }

        override fun backdropSource(id: String, source: BackdropSource) {
            val order = indexOf(manifest.contributes.backdrops.map { it.id }, id, "动态背景")
            track("backdropSource:$id")
            val extension = object : BackdropExtension {
                override val maxWidth: Int = source.maxWidth.coerceIn(MIN_BACKDROP_WIDTH, MAX_BACKDROP_WIDTH)

                override fun render(width: Int, height: Int, timeMs: Long, pixels: IntArray): Boolean = try {
                    source.render(BackdropFrame(width, height, timeMs, pixels))
                    guard.recordSuccess()
                    true
                } catch (e: Throwable) {
                    AppLogger.w(TAG, "[$pluginId] 动态背景 $id 出错", e)
                    guard.recordError("动态背景 $id 出错：${e.message ?: e.javaClass.simpleName}")
                    false
                }

                override fun reportStalled() = guard.recordError("动态背景 $id 长时间没有返回")
            }
            guard.register(Extension(ExtensionPoints.BACKDROP, pluginId, manifest.name, id, extension, order))
        }

        override fun composePage(id: String, content: @Composable () -> Unit) {
            val pages = manifest.contributes.pages.filter { it.compose }
            val order = indexOf(pages.map { it.id }, id, " Compose 页面")
            track("composePage:$id")
            val extension = ComposePageExtension(content) { error ->
                AppLogger.w(TAG, "[$pluginId] Compose 页面 $id 出错", error)
                guard.recordError("页面 $id 出错：${error.message ?: error.javaClass.simpleName}")
            }
            guard.register(Extension(ExtensionPoints.COMPOSE_PAGE, pluginId, manifest.name, id, extension, order))
        }

        override fun playbackListener(listener: PlaybackListener) {
            val subscribed = manifest.contributes.events
            require(subscribed.isNotEmpty()) { "清单 events 没有声明要订阅的事件" }
            val extension = object : EventExtension {
                override val events: Set<PluginEvent> = subscribed

                override fun onEvent(event: PluginEvent, data: JsonElement) {
                    guard.scope.launch { dispatchEvent(listener, event, data) }
                }
            }
            guard.register(Extension(ExtensionPoints.EVENT, pluginId, manifest.name, pluginId, extension))
        }
    }

    private suspend fun dispatchEvent(listener: PlaybackListener, event: PluginEvent, data: JsonElement) {
        guard.guarded("播放事件", DesktopNativePluginHost.ACTION_TIMEOUT_MS) {
            val fields = data as? JsonObject
            when (event) {
                PluginEvent.SONG_CHANGED -> listener.onSongChanged(fields?.let(::nowPlaying))
                PluginEvent.PLAY_STATE_CHANGED -> listener.onPlayStateChanged(fields?.bool("playing") == true)
                PluginEvent.PROGRESS -> listener.onProgress(fields?.long("positionMs") ?: 0, fields?.long("durationMs") ?: 0)
            }
        }
    }

    private suspend fun runHandler(label: String, args: JsonElement, handler: suspend (Map<String, Any?>) -> Unit): Boolean =
        guard.guarded(label, DesktopNativePluginHost.ACTION_TIMEOUT_MS) { handler(toMap(args)) } != null

    private fun indexOf(declared: List<String>, id: String, label: String): Int =
        declared.indexOf(id).also { require(it >= 0) { "清单里没有声明${label} $id" } }

    override val player: PlayerApi = object : PlayerApi {
        override suspend fun current(): NowPlayingSong? = (call("player.current") as? JsonObject)?.let(::nowPlaying)

        override suspend fun state(): PlaybackState {
            val fields = call("player.state") as? JsonObject ?: JsonObject(emptyMap())
            return PlaybackState(fields.bool("playing"), fields.long("positionMs"), fields.long("durationMs"))
        }

        override suspend fun play() { call("player.play") }
        override suspend fun pause() { call("player.pause") }
        override suspend fun next() { call("player.next") }
        override suspend fun previous() { call("player.previous") }

        override suspend fun enqueue(song: QueueSong) {
            call("player.enqueue", toJson(mapOf("songId" to song.songId, "title" to song.title, "artist" to song.artist, "cover" to song.cover)) as JsonObject)
        }
    }

    override val spectrum: SpectrumApi = object : SpectrumApi {
        // 无声时不出帧，要靠作用域结束来停止收集，否则插件卸载后采集停不下来
        override fun frames(): Flow<SpectrumFrame> = channelFlow {
            if (PluginPermission.PLAYBACK_READ !in manifest.permissions) {
                throw PluginApiException("缺少权限 ${PluginPermission.PLAYBACK_READ.key}")
            }
            val collecting = launch { spectrumFrames.collect { send(it) } }
            val stop = guard.scope.coroutineContext.job.invokeOnCompletion { collecting.cancel() }
            try {
                collecting.join()
            } finally {
                stop.dispose()
            }
        }.conflate()
    }

    override val library: LibraryApi = object : LibraryApi {
        override suspend fun likedSongIds(): Set<Long> = idSet(call("library.likedSongIds"))
        override suspend fun playlists(): List<LibraryPlaylist> = libraryPlaylists(call("library.playlists"))
    }

    override val storage: PluginStorage = object : PluginStorage {
        override suspend fun get(key: String): String? =
            (call("storage.get", buildJsonObject { put("key", key) }) as? JsonPrimitive)?.contentOrNull

        override suspend fun set(key: String, value: String) {
            call("storage.set", buildJsonObject {
                put("key", key)
                put("value", value)
            })
        }

        override suspend fun remove(key: String) {
            call("storage.remove", buildJsonObject { put("key", key) })
        }
    }

    override val settings: PluginSettings = object : PluginSettings {
        override suspend fun get(key: String): Any? = toPlain(call("settings.get", buildJsonObject { put("key", key) }))
        override suspend fun all(): Map<String, Any?> = toMap(call("settings.all"))
    }

    override val ui: PluginUi = object : PluginUi {
        override suspend fun toast(text: String) {
            call("ui.toast", buildJsonObject { put("text", text) })
        }

        override suspend fun openUrl(url: String) {
            call("ui.openUrl", buildJsonObject { put("url", url) })
        }

        override suspend fun setState(pageId: String, patch: Map<String, Any?>) {
            call("ui.setState", buildJsonObject {
                put("page", pageId)
                put("patch", toJson(patch))
            })
        }

        override suspend fun openContent(sourceId: String, contentId: String, title: String) {
            call("content.open", buildJsonObject {
                put("source", sourceId)
                put("id", contentId)
                put("title", title)
            })
        }
    }

    override val netease: NeteaseApi = object : NeteaseApi {
        override suspend fun songDetails(ids: List<Long>) = trackList(netease("song.detail", "ids" to ids))
        override suspend fun lyric(id: Long) = (netease("song.lyric", "id" to id) as? JsonArray).orEmpty().mapNotNull { item ->
            (item as? JsonObject)?.let { NeteaseLyricLine(it.long("timeMs"), it.text("text"), it.textOrNull("translation")) }
        }

        override suspend fun search(keyword: String, type: NeteaseSearchType, offset: Int, limit: Int): NeteaseSearchPage {
            val page = netease("search", "keyword" to keyword, "type" to type.key, "offset" to offset, "limit" to limit) as? JsonObject
                ?: return NeteaseSearchPage(0, false, emptyList())
            val items = (page["items"] as? JsonArray).orEmpty().mapNotNull { item ->
                val fields = item as? JsonObject ?: return@mapNotNull null
                when (type) {
                    NeteaseSearchType.SONG -> NeteaseSearchItem.Song(track(fields))
                    NeteaseSearchType.ALBUM -> NeteaseSearchItem.Album(NeteaseAlbum(fields.long("id"), fields.text("name"), fields.text("cover"), fields.text("artist")))
                    NeteaseSearchType.ARTIST -> NeteaseSearchItem.Artist(artist(fields))
                    NeteaseSearchType.PLAYLIST -> NeteaseSearchItem.Playlist(playlist(fields))
                }
            }
            return NeteaseSearchPage(page.int("total"), page.bool("hasMore"), items)
        }

        override suspend fun playlist(id: Long) = playlist(netease("playlist.detail", "id" to id) as JsonObject)
        override suspend fun album(id: Long) = playlist(netease("album.detail", "id" to id) as JsonObject)
        override suspend fun artist(id: Long) = artist(netease("artist.detail", "id" to id) as JsonObject)
        override suspend fun artistTopSongs(id: Long) = trackList(netease("artist.topSongs", "id" to id))
        override suspend fun userPlaylists() = libraryPlaylists(netease("user.playlists"))
        override suspend fun userLikedSongs() = idSet(netease("user.likedSongs"))
        override suspend fun likeSong(id: Long, like: Boolean) { netease("song.like", "id" to id, "like" to like) }

        override suspend fun createPlaylist(name: String, private: Boolean) =
            playlist(netease("playlist.create", "name" to name, "privacy" to playlistPrivacyOf(private)) as JsonObject)

        override suspend fun addTracks(playlistId: Long, trackIds: List<Long>) { netease("playlist.addTracks", "id" to playlistId, "trackIds" to trackIds) }
        override suspend fun removeTracks(playlistId: Long, trackIds: List<Long>) { netease("playlist.removeTracks", "id" to playlistId, "trackIds" to trackIds) }
        override suspend fun subscribePlaylist(id: Long, subscribe: Boolean) { netease("playlist.subscribe", "id" to id, "subscribe" to subscribe) }
        override suspend fun subscribeAlbum(id: Long, subscribe: Boolean) { netease("album.subscribe", "id" to id, "subscribe" to subscribe) }
        override suspend fun followArtist(id: Long, follow: Boolean) { netease("artist.follow", "id" to id, "follow" to follow) }
        override suspend fun deletePlaylist(id: Long) { netease("playlist.delete", "id" to id) }
    }

    private fun PluginTrackQuery.toApi() = TrackQuery(songId, title, artists, album, durationMs, origin.key)

    private fun nowPlaying(fields: JsonObject) = NowPlayingSong(
        fields.text("id"),
        (fields["songId"] as? JsonPrimitive)?.longOrNull,
        fields.text("title"),
        fields.text("artist"),
        fields.text("cover")
    )

    private fun idSet(value: JsonElement): Set<Long> =
        (value as? JsonArray).orEmpty().mapNotNullTo(LinkedHashSet()) { (it as? JsonPrimitive)?.longOrNull }

    private fun libraryPlaylists(value: JsonElement): List<LibraryPlaylist> = (value as? JsonArray).orEmpty().mapNotNull { item ->
        (item as? JsonObject)?.let { LibraryPlaylist(it.long("id"), it.text("name"), it.text("cover"), it.int("trackCount"), it.bool("liked")) }
    }

    private fun trackList(value: JsonElement): List<NeteaseTrack> = (value as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::track) }

    private fun track(fields: JsonObject) = NeteaseTrack(
        (fields["songId"] as? JsonPrimitive)?.longOrNull,
        fields.text("title"),
        fields.text("artist"),
        (fields["artists"] as? JsonArray).orEmpty().mapNotNull { artist ->
            (artist as? JsonObject)?.let { NeteaseArtistRef(it.long("id"), it.text("name")) }
        },
        fields.text("album"),
        fields.long("albumId"),
        fields.text("cover"),
        fields.long("durationMs")
    )

    private fun playlist(fields: JsonObject) = NeteasePlaylist(
        fields.long("id"),
        fields.text("name"),
        fields.text("cover"),
        fields.text("description"),
        fields.long("playCount"),
        fields.int("trackCount"),
        fields.bool("subscribed"),
        fields.text("creator"),
        trackList(fields["tracks"] ?: JsonNull)
    )

    private fun artist(fields: JsonObject) = NeteaseArtist(
        fields.long("id"),
        fields.text("name"),
        fields.text("cover"),
        fields.text("avatar"),
        fields.text("description"),
        fields.int("albumCount"),
        fields.int("songCount")
    )

    private fun tracksJson(tracks: List<ContentTrack>): JsonElement = JsonArray(tracks.map { track ->
        toJson(
            mapOf(
                "id" to track.id,
                "title" to track.title,
                "artist" to track.artist,
                "album" to track.album,
                "cover" to track.cover,
                "durationMs" to track.durationMs
            )
        )
    })

    private fun playlistJson(playlist: ContentPlaylist): JsonElement = JsonObject(
        mapOf(
            "name" to JsonPrimitive(playlist.name),
            "cover" to JsonPrimitive(playlist.cover),
            "description" to JsonPrimitive(playlist.description),
            "tracks" to tracksJson(playlist.tracks)
        )
    )

    private fun JsonObject.textOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.text(key: String): String = textOrNull(key).orEmpty()

    private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

    private companion object {
        const val MIN_BACKDROP_WIDTH = 16
        const val MAX_BACKDROP_WIDTH = 1920
    }
}

internal fun toJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Iterable<*> -> JsonArray(value.map(::toJson))
    is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to toJson(item) })
    else -> throw PluginApiException("不支持的数据类型 ${value.javaClass.simpleName}")
}

internal fun toPlain(value: JsonElement): Any? = when (value) {
    JsonNull -> null
    is JsonPrimitive -> if (value.isString) value.content else value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull
    is JsonArray -> value.map(::toPlain)
    is JsonObject -> value.mapValues { toPlain(it.value) }
}

internal fun toMap(value: JsonElement): Map<String, Any?> = (value as? JsonObject)?.mapValues { toPlain(it.value) }.orEmpty()
