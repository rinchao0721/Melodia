package com.lin0721.linmusic.core.source.provider

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.PlatformSearchProvider
import com.lin0721.linmusic.core.source.SourceHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

private const val TAG = "KugouSearchProvider"

// 酷狗音乐搜索，基于公开搜索端点
class KugouSearchProvider(
    private val isEnabled: suspend () -> Boolean = { true }
) : PlatformSearchProvider {

    override val platform: MusicPlatform = MusicPlatform.KUGOU

    override suspend fun search(
        keyword: String,
        offset: Int,
        limit: Int
    ): List<ExternalTrack> {
        if (!isEnabled()) return emptyList()
        return try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val pn = (offset / limit) + 1
            val url = "http://mobilecdn.kugou.com/api/v3/search/song?keyword=$encoded&page=$pn&pagesize=$limit"
            val resp = SourceHttpClient.getOrNull(url) ?: return emptyList()

            val root = SourceHttpClient.json.parseToJsonElement(resp).jsonObject
            val dataObj = root["data"]?.jsonObject ?: return emptyList()
            val infoList = dataObj["info"]?.jsonArray ?: return emptyList()

            infoList.mapNotNull { itemElem ->
                val obj = itemElem.jsonObject
                val hash = obj["hash"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val songName = obj["songname"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                val artist = obj["singername"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
                val album = obj["album_name"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
                val durationSec = obj["duration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                val transParamElem = obj["trans_param"]
                val unionCover = when {
                    transParamElem is JsonObject -> {
                        transParamElem["union_cover"]?.jsonPrimitive?.contentOrNull
                    }
                    transParamElem != null -> {
                        val str = transParamElem.jsonPrimitive.contentOrNull.orEmpty()
                        Regex("""union_cover=([^;,\s}]+)""").find(str)?.groupValues?.get(1)
                    }
                    else -> null
                }
                val coverUrl = unionCover?.replace("{size}", "400")?.replace("http://", "https://").orEmpty()

                ExternalTrack(
                    id = hash,
                    name = songName,
                    artists = artist,
                    artistList = if (artist.isNotBlank()) artist.split("、", "/") else emptyList(),
                    albumName = album,
                    durationMs = durationSec * 1000L,
                    coverUrl = coverUrl,
                    platform = platform,
                    hash = hash
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "酷狗搜索异常 [keyword=$keyword]: ${e.message}")
            emptyList()
        }
    }
}
