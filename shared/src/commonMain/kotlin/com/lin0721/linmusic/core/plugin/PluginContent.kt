package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.PlaylistCreator
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.playlistexternal.toPlaylistTrack
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.ExternalTrackDirectResolver
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.PlatformSearchProvider
import com.lin0721.linmusic.core.source.isTrialPlayUrl
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

private const val TAG = "PluginContent"
private const val TRACK_ID_PREFIX = "plugin"
private const val ID_SEPARATOR = '\u0000'
private const val MIN_COLLECTION_ID = 1000L
private const val MAX_TRACKS = 1000
private const val MAX_TEXT = 200

// 插件内容没有评论、收藏、分享和下载，页面据此隐藏入口
fun isPluginCollectionId(id: Long): Boolean = id <= -MIN_COLLECTION_ID

data class PluginContentOpen(val id: Long, val title: String)

private data class CollectionRef(val pluginId: String, val sourceId: String, val contentId: String)

// 插件内容进入原生页面的桥：歌单用负数合成 id，歌曲用外部歌曲表示，页面与播放器不用区分来源
class PluginContent(private val registry: ExtensionRegistry) : ExternalTrackDirectResolver {
    private val collections = ConcurrentHashMap<Long, CollectionRef>()
    private val tracks = ConcurrentHashMap<Long, ExternalTrack>()
    private val _opens = MutableSharedFlow<PluginContentOpen>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val opens: SharedFlow<PluginContentOpen> = _opens.asSharedFlow()

    fun collectionId(pluginId: String, sourceId: String, contentId: String): Long {
        val id = -MIN_COLLECTION_ID - "$pluginId/$sourceId/$contentId".hashCode().toLong().absoluteValue
        collections[id] = CollectionRef(pluginId, sourceId, contentId)
        return id
    }

    fun open(pluginId: String, sourceId: String, contentId: String, title: String) {
        _opens.tryEmit(PluginContentOpen(collectionId(pluginId, sourceId, contentId), title))
    }

    fun ownsCollection(id: Long): Boolean = collections.containsKey(id)

    fun externalTrack(queueSongId: Long): ExternalTrack? = tracks[queueSongId]

    suspend fun detail(id: Long): Result<PlaylistDetail> {
        val ref = collections[id] ?: return Result.failure(IllegalStateException("插件内容已失效"))
        val source = source(ref.pluginId, ref.sourceId) ?: return Result.failure(IllegalStateException("提供这个内容的插件已停用"))
        val data = source.value.playlist(ref.contentId) as? JsonObject
            ?: return Result.failure(IllegalStateException("插件没有返回内容"))
        val parsed = parseTracks(data["tracks"], ref.pluginId, ref.sourceId).map { it.toPlaylistTrack() }
        return Result.success(
            PlaylistDetail(
                id = id,
                name = data.text("name").ifEmpty { ref.contentId },
                coverImgUrl = data.text("cover"),
                description = data.text("description").ifEmpty { null },
                creator = PlaylistCreator(nickname = source.pluginName),
                tracks = parsed,
                trackCount = parsed.size
            )
        )
    }

    // 多个插件并发搜索，出错的按没有结果处理
    suspend fun search(keyword: String, offset: Int, limit: Int): List<ExternalTrack> = coroutineScope {
        registry.get(ExtensionPoints.CONTENT_SOURCE).filter { it.value.searchable }.map { source ->
            async {
                parseTracks(source.value.search(keyword, offset, limit), source.pluginId, source.localId)
            }
        }.awaitAll().flatten()
    }

    fun hasSearch(): Boolean = registry.get(ExtensionPoints.CONTENT_SOURCE).any { it.value.searchable }

    fun searchAvailable(): Flow<Boolean> = registry.entries.map { hasSearch() }.distinctUntilChanged()

    override fun owns(track: ExternalTrack): Boolean = parseTrackId(track.id) != null

    override suspend fun resolve(track: ExternalTrack, quality: String): String? {
        val (pluginId, sourceId, trackId) = parseTrackId(track.id) ?: return null
        val source = source(pluginId, sourceId) ?: return null
        val url = PluginResults.url(source.value.resolve(trackId, quality)) ?: return null
        if (isTrialPlayUrl(url)) {
            AppLogger.w(TAG, "插件 [${source.pluginName}] 返回试听链接: ${track.name}")
            return null
        }
        return url
    }

    private fun source(pluginId: String, sourceId: String): Extension<ContentSourceExtension>? =
        registry.get(ExtensionPoints.CONTENT_SOURCE).firstOrNull { it.pluginId == pluginId && it.localId == sourceId }

    // 单项缺少 id 或标题时跳过，不影响其他歌曲
    private fun parseTracks(value: JsonElement?, pluginId: String, sourceId: String): List<ExternalTrack> {
        val array = value as? JsonArray ?: return emptyList()
        return array.take(MAX_TRACKS).mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.text("id")
            val title = item.text("title")
            if (id.isEmpty() || title.isEmpty()) return@mapNotNull null
            ExternalTrack(
                id = listOf(TRACK_ID_PREFIX, pluginId, sourceId, id).joinToString(ID_SEPARATOR.toString()),
                name = title,
                artists = item.text("artist"),
                albumName = item.text("album"),
                durationMs = (item["durationMs"] as? JsonPrimitive)?.longOrNull?.coerceAtLeast(0) ?: 0,
                coverUrl = item.text("cover"),
                platform = MusicPlatform.LX
            ).also { tracks[it.queueSongId] = it }
        }
    }

    private fun parseTrackId(id: String): Triple<String, String, String>? {
        val parts = id.split(ID_SEPARATOR, limit = 4)
        return if (parts.size == 4 && parts[0] == TRACK_ID_PREFIX) Triple(parts[1], parts[2], parts[3]) else null
    }

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.take(MAX_TEXT).orEmpty()
}

// 聚合搜索的「自定义插件」平台
class PluginPlatformSearchProvider(private val content: PluginContent) : PlatformSearchProvider {
    override val platform: MusicPlatform = MusicPlatform.LX

    override val available: Flow<Boolean> get() = content.searchAvailable()

    override suspend fun search(keyword: String, offset: Int, limit: Int): List<ExternalTrack> =
        content.search(keyword, offset, limit)
}

// 插件内容用负数合成 id，走这里取详情；其余调用原样转发，写操作的入口已被页面按 id 隐藏
class PluginAwarePlaylistRepository(
    private val delegate: PlaylistRepository,
    private val content: PluginContent
) : PlaylistRepository by delegate {

    override fun getPlaylistDetail(id: Long): Flow<Result<PlaylistDetail>> =
        if (id < 0 && content.ownsCollection(id)) flow { emit(content.detail(id)) } else delegate.getPlaylistDetail(id)

    override fun loadAllTracks(detail: PlaylistDetail): Flow<Result<List<Track>>> =
        if (content.ownsCollection(detail.id)) flow { emit(Result.success(detail.tracks)) } else delegate.loadAllTracks(detail)
}
