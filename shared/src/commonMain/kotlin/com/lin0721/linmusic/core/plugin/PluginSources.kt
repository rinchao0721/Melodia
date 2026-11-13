package com.lin0721.linmusic.core.plugin

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricParser
import com.lin0721.linmusic.core.player.domain.TtmlLyricParser
import com.lin0721.linmusic.core.source.AudioSourceProvider
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.SourceResult
import com.lin0721.linmusic.core.source.isTrialPlayUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "PluginSources"
private const val RESOLVE_BUDGET_MS = 15_000L

// 交给插件音源与歌词源的歌曲信息；origin 为 songId 所属平台
data class PluginTrackQuery(
    val songId: String,
    val title: String,
    val artists: String,
    val album: String,
    val durationMs: Long,
    val origin: MusicPlatform
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("songId", songId)
        put("title", title)
        put("artists", artists)
        put("album", album)
        put("durationMs", durationMs)
        put("origin", origin.key)
    }

    companion object {
        fun of(track: ExternalTrack): PluginTrackQuery =
            PluginTrackQuery(track.id, track.name, track.artists, track.albumName, track.durationMs, track.platform)
    }
}

// 插件音源：按插件名称顺序逐个尝试，参与 AudioSourceRouter 的社区源轮询
class PluginAudioSourceProvider(private val registry: ExtensionRegistry) : AudioSourceProvider {

    override val platform: MusicPlatform = MusicPlatform.LX

    override suspend fun resolveUrl(
        songId: String,
        songName: String,
        artists: String,
        albumName: String?,
        durationMs: Long,
        quality: String,
        origin: MusicPlatform
    ): SourceResult? {
        val sources = registry.get(ExtensionPoints.AUDIO_SOURCE)
        if (sources.isEmpty()) return null
        val query = PluginTrackQuery(songId, songName, artists, albumName.orEmpty(), durationMs, origin)
        // 单个音源最多等 8 秒，插件多时要有总上限，后面还有其他换源方式
        return withTimeoutOrNull(RESOLVE_BUDGET_MS) {
            for (source in sources) {
                val url = PluginResults.url(source.value.resolve(query, quality))
                when {
                    url == null -> continue
                    isTrialPlayUrl(url) -> AppLogger.w(TAG, "插件 [${source.pluginName}] 返回试听链接，继续尝试: $songName")
                    else -> {
                        AppLogger.i(TAG, "插件 [${source.pluginName}] 音源 ${source.localId} 解析成功: $songName")
                        return@withTimeoutOrNull SourceResult(url = url, platform = platform, quality = quality, pluginId = source.pluginId)
                    }
                }
            }
            null
        }
    }

    override suspend fun search(keyword: String, offset: Int, limit: Int) = emptyList<ExternalTrack>()
}

// 插件下载源返回的链接；type、quality 由下载链路再做取舍
data class PluginDownloadLink(
    val url: String,
    val type: String?,
    val quality: String?,
    val headers: Map<String, String>
)

// 插件下载源：按插件名称顺序逐个尝试，只在官方下载链接不可用时调用
class PluginDownloadSources(private val registry: ExtensionRegistry) {

    suspend fun resolve(query: PluginTrackQuery, quality: String): PluginDownloadLink? {
        val sources = registry.get(ExtensionPoints.DOWNLOAD_SOURCE)
        if (sources.isEmpty()) return null
        return withTimeoutOrNull(RESOLVE_BUDGET_MS) {
            for (source in sources) {
                val link = PluginResults.downloadLink(source.value.resolve(query, quality))
                when {
                    link == null -> continue
                    isTrialPlayUrl(link.url) -> AppLogger.w(TAG, "插件 [${source.pluginName}] 下载源返回试听链接，继续尝试: ${query.title}")
                    else -> {
                        AppLogger.i(TAG, "插件 [${source.pluginName}] 下载源 ${source.localId} 解析成功: ${query.title}")
                        return@withTimeoutOrNull link
                    }
                }
            }
            null
        }
    }
}

// 插件歌词源：所有歌词源并发查询，按插件名称顺序取第一份非空结果
class PluginLyricsSource(
    private val registry: ExtensionRegistry,
    // 网易歌曲只有 id，查询前补全歌名、歌手等信息
    private val neteaseTrackOf: suspend (songId: Long) -> PluginTrackQuery? = { null }
) {

    fun hasSources(): Boolean = registry.get(ExtensionPoints.LYRICS_SOURCE).isNotEmpty()

    suspend fun fetch(songId: Long, external: ExternalTrack?): List<LyricLine> {
        val query = external?.let(PluginTrackQuery::of) ?: songId.takeIf { it > 0 }?.let { neteaseTrackOf(it) } ?: return emptyList()
        return fetch(query)
    }

    suspend fun fetch(query: PluginTrackQuery): List<LyricLine> = coroutineScope {
        val sources = registry.get(ExtensionPoints.LYRICS_SOURCE)
        if (sources.isEmpty()) return@coroutineScope emptyList()
        val results = sources.map { source -> async { PluginResults.lyrics(source.value.fetch(query)) } }
        // 排在前面的有结果就不用等后面的
        for (result in results) {
            val lines = result.await()
            if (lines.isNotEmpty()) {
                coroutineContext.cancelChildren()
                return@coroutineScope lines
            }
        }
        emptyList()
    }
}

// 插件返回值的宽松解析：取不到就当没有结果
internal object PluginResults {

    // 字符串直接作为链接，或对象的 url 字段；只接受 http(s)
    fun url(result: JsonElement?): String? {
        val text = when (result) {
            is JsonPrimitive -> result.takeIf { it.isString }?.content
            is JsonObject -> (result["url"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            else -> null
        }?.trim()
        return text?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
    }

    // 链接规则同 url；对象可再给 type、quality 与 headers，headers 里非字符串的值忽略
    fun downloadLink(result: JsonElement?): PluginDownloadLink? {
        val url = url(result) ?: return null
        val obj = result as? JsonObject
        val headers = (obj?.get("headers") as? JsonObject)?.mapNotNull { (name, value) ->
            (value as? JsonPrimitive)?.takeIf { it.isString }?.let { name to it.content }
        }?.toMap().orEmpty()
        return PluginDownloadLink(url, obj?.string("type"), obj?.string("quality"), headers)
    }

    // 字符串按 LRC 解析；对象可以给 lrc 或 ttml 字段
    fun lyrics(result: JsonElement?): List<LyricLine> {
        val (lrc, ttml) = when (result) {
            is JsonPrimitive -> result.takeIf { it.isString }?.content to null
            is JsonObject -> result.string("lrc") to result.string("ttml")
            else -> null to null
        }
        return try {
            when {
                !ttml.isNullOrBlank() -> TtmlLyricParser.parse(ttml)
                !lrc.isNullOrBlank() -> LyricParser.parseLocal(lrc)
                else -> emptyList()
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "插件歌词解析失败: ${e.message}")
            emptyList()
        }
    }
}
