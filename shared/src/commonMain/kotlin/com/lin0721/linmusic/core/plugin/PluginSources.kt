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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "PluginSources"

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
class PluginAudioSourceProvider(private val runtime: PluginRuntime) : AudioSourceProvider {

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
        val sources = runtime.registeredSources(PluginPrelude.KIND_AUDIO_SOURCE)
        if (sources.isEmpty()) return null
        val query = PluginTrackQuery(songId, songName, artists, albumName.orEmpty(), durationMs, origin)
        for ((manifest, source) in sources) {
            val args = buildJsonObject {
                put("method", "resolve")
                put("params", JsonObject(query.toJson() + ("quality" to JsonPrimitive(quality))))
            }
            val result = runtime.invoke(manifest.id, PluginPrelude.KIND_AUDIO_SOURCE, source.id, args, PluginRuntime.SOURCE_TIMEOUT_MS)
            val url = PluginResults.url(result)
            when {
                url == null -> continue
                isTrialPlayUrl(url) -> AppLogger.w(TAG, "插件 [${manifest.name}] 返回试听链接，继续尝试: $songName")
                else -> {
                    AppLogger.i(TAG, "插件 [${manifest.name}] 音源 ${source.id} 解析成功: $songName")
                    return SourceResult(url = url, platform = platform, quality = quality, pluginId = manifest.id)
                }
            }
        }
        return null
    }

    override suspend fun search(keyword: String, offset: Int, limit: Int) = emptyList<ExternalTrack>()
}

// 插件歌词源：所有歌词源并发查询，按插件名称顺序取第一份非空结果
class PluginLyricsSource(
    private val runtime: PluginRuntime,
    // 网易歌曲只有 id，查询前补全歌名、歌手等信息
    private val neteaseTrackOf: suspend (songId: Long) -> PluginTrackQuery? = { null }
) {

    fun hasSources(): Boolean = runtime.registeredSources(PluginPrelude.KIND_LYRICS_SOURCE).isNotEmpty()

    suspend fun fetch(songId: Long, external: ExternalTrack?): List<LyricLine> {
        val query = external?.let(PluginTrackQuery::of) ?: songId.takeIf { it > 0 }?.let { neteaseTrackOf(it) } ?: return emptyList()
        return fetch(query)
    }

    suspend fun fetch(query: PluginTrackQuery): List<LyricLine> = coroutineScope {
        val sources = runtime.registeredSources(PluginPrelude.KIND_LYRICS_SOURCE)
        if (sources.isEmpty()) return@coroutineScope emptyList()
        val args = buildJsonObject {
            put("method", "fetch")
            put("params", query.toJson())
        }
        sources.map { (manifest, source) ->
            async {
                val result = runtime.invoke(manifest.id, PluginPrelude.KIND_LYRICS_SOURCE, source.id, args, PluginRuntime.SOURCE_TIMEOUT_MS)
                PluginResults.lyrics(result)
            }
        }.awaitAll().firstOrNull { it.isNotEmpty() }.orEmpty()
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

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
