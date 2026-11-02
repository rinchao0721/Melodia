package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.QrcLyricParser
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.SongMatcher
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/** 独立 QQ 客户端，不继承网易的 Cookie、鉴权头或拦截器。 */
class QqLyricsClient internal constructor(
    private val client: OkHttpClient,
    private val endpoint: String,
    private val publicSearchEndpoint: String? = null
) {
    constructor() : this(
        OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES)
            .callTimeout(4, TimeUnit.SECONDS).connectTimeout(2, TimeUnit.SECONDS).build(),
        "https://u.y.qq.com/cgi-bin/musicu.fcg",
        "https://c.y.qq.com/soso/fcgi-bin/search_for_qq_cp"
    )

    private companion object {
        const val POSITIVE_TTL_MS = 30 * 60_000L
        const val NEGATIVE_TTL_MS = 5 * 60_000L
        const val MAX_CACHE_ENTRIES = 32
    }

    private val json = Json { ignoreUnknownKeys = true }
    private data class Cached(val lines: List<LyricLine>, val expiresAt: Long)
    private val cache = LinkedHashMap<Track, Cached>()

    suspend fun fetch(track: Track): List<LyricLine> = withContext(Dispatchers.IO) {
        if (track.name.isBlank() || track.ar.none { it.name.isNotBlank() } || track.dt <= 0) return@withContext emptyList()
        synchronized(cache) { cache[track]?.takeIf { it.expiresAt > System.currentTimeMillis() } }
            ?.let { return@withContext it.lines }
        try {
            val cleanedTitle = track.name.replace(Regex("""[\(（][^)）]*[\)）]"""), "").trim()
            val artist = track.ar.firstOrNull()?.name.orEmpty()
            val keywords = listOf("${track.name} $artist", "$cleanedTitle $artist", track.name).distinct()
            for (keyword in keywords) {
                val candidates = search(keyword)
                val match = pickCandidate(candidates, track) ?: continue
                val data = lyrics(match, wordSynced = true)
                val original = QqLyricDecoder.decodeLyric(data.string("lyric"))
                val translation = QqLyricDecoder.decodeLyric(data.string("trans"))
                val roma = QqLyricDecoder.decodeLyric(data.string("roma"))
                var lines = QrcLyricParser.parse(original, translation, roma)
                // QRC 损坏或为空时另取普通 LRC；原响应本身是 LRC 时解析器也能直接处理。
                if (lines.isEmpty()) {
                    val lrc = lyrics(match, wordSynced = false)
                    lines = QrcLyricParser.parse(QqLyricDecoder.decodeLyric(lrc.string("lyric")), translation, roma)
                }
                if (lines.isNotEmpty()) {
                    remember(track, lines, POSITIVE_TTL_MS)
                    return@withContext lines
                }
            }
            // QQ 正常响应但无匹配或无歌词：短期缓存，避免切歌、切开关时反复请求；异常不缓存
            remember(track, emptyList(), NEGATIVE_TTL_MS)
            emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // QQ 无结果或网络错误不影响网易与本地歌词。
            emptyList()
        }
    }

    private fun remember(track: Track, lines: List<LyricLine>, ttlMs: Long) {
        synchronized(cache) {
            cache[track] = Cached(lines, System.currentTimeMillis() + ttlMs)
            while (cache.size > MAX_CACHE_ENTRIES) cache.remove(cache.keys.first())
        }
    }

    internal fun pickCandidate(candidates: List<ExternalTrack>, track: Track): ExternalTrack? =
        SongMatcher.pickBestCandidate(
            candidates.take(5).filter {
                (it.id.toLongOrNull() ?: 0) > 0 && it.artists.isNotBlank() &&
                    it.durationMs > 0 && abs(it.durationMs - track.dt) <= 5000
            },
            track.name, track.ar.map { it.name }, track.al.name, track.dt
        )

    private suspend fun search(keyword: String): List<ExternalTrack> {
        val data = try {
            request("search", "music.search.SearchCgiService", "DoSearchForQQMusicLite", buildJsonObject {
                put("query", keyword); put("search_type", 0); put("num_per_page", 5); put("page_num", 1)
                put("highlight", 0); put("nqc_flag", 0); put("page_id", 1); put("grp", 1)
            })
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { null }
        val items = data?.get("body")?.jsonObject?.get("item_song")?.jsonArray
        if (items.isNullOrEmpty()) return searchPublic(keyword)
        return items.mapNotNull { item ->
            val song = item.jsonObject
            val id = song.string("id").takeIf { it.toLongOrNull() != null } ?: return@mapNotNull null
            val artists = song["singer"]?.jsonArray.orEmpty().map { it.jsonObject.string("name") }
            ExternalTrack(
                id = id, name = song.string("title").ifBlank { song.string("name") },
                artists = artists.joinToString(" / "), artistList = artists,
                albumName = song["album"]?.jsonObject?.let { it.string("title").ifBlank { it.string("name") } }.orEmpty(),
                durationMs = (song.string("interval").toLongOrNull() ?: 0L) * 1000,
                platform = MusicPlatform.QQ
            )
        }
    }

    // Lite 接口未登录时可能返回 2001；公开搜索保留数字 songid，供歌词接口使用。
    private suspend fun searchPublic(keyword: String): List<ExternalTrack> {
        val url = publicSearchEndpoint?.toHttpUrl()?.newBuilder()
            ?.addQueryParameter("w", keyword)?.addQueryParameter("format", "json")
            ?.addQueryParameter("p", "1")?.addQueryParameter("n", "5")?.build() ?: return emptyList()
        val body = execute(Request.Builder().url(url).header("Referer", "https://y.qq.com/")
            .header("User-Agent", "Mozilla/5.0").build())
        val root = json.parseToJsonElement(body).jsonObject
        if (root.string("code") != "0") return emptyList()
        val songs = root["data"]?.jsonObject?.get("song")?.jsonObject?.get("list")?.jsonArray.orEmpty()
        return songs.mapNotNull { item ->
            val song = item.jsonObject
            val id = song.string("songid").takeIf { it.toLongOrNull() != null } ?: return@mapNotNull null
            val artists = song["singer"]?.jsonArray.orEmpty().map { it.jsonObject.string("name") }
            ExternalTrack(id, song.string("songname"), artists.joinToString(" / "), artistList = artists,
                albumName = song.string("albumname"), durationMs = (song.string("interval").toLongOrNull() ?: 0L) * 1000,
                platform = MusicPlatform.QQ)
        }
    }

    private suspend fun lyrics(track: ExternalTrack, wordSynced: Boolean): JsonObject = request(
        "lyric", "music.musichallSong.PlayLyricInfo", "GetPlayLyricInfo", buildJsonObject {
            fun encoded(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
            put("songID", track.id.toLong()); put("songName", encoded(track.name))
            put("singerName", encoded(track.artistList.joinToString(","))); put("albumName", encoded(track.albumName))
            put("interval", track.durationMs / 1000); put("crypt", 1); put("ct", 19); put("cv", 2111)
            put("qrc", if (wordSynced) 1 else 0); put("qrc_t", 0); put("lrc_t", 0)
            put("trans", 1); put("trans_t", 0); put("roma", 1); put("roma_t", 0); put("type", 0)
        }
    )

    private suspend fun request(key: String, module: String, method: String, params: JsonObject): JsonObject {
        val body = buildJsonObject {
            putJsonObject("comm") {
                put("ct", 11); put("cv", "1003006"); put("tmeAppID", "qqmusiclight"); put("tmeLoginType", 0)
                put("v", "1003006"); put("os_ver", "15"); put("phonetype", "24122RKC7C")
                put("nettype", "NETWORK_WIFI"); put("udid", "0")
            }
            putJsonObject(key) { put("module", module); put("method", method); put("param", params) }
        }
        val request = Request.Builder().url(endpoint).header("Referer", "https://y.qq.com/")
            .header("User-Agent", "Mozilla/5.0")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val root = json.parseToJsonElement(execute(request)).jsonObject
        check(root.string("code").let { it.isEmpty() || it == "0" }) { "QQ request failed" }
        val payload = checkNotNull(root[key]?.jsonObject) { "QQ payload missing" }
        check(payload.string("code").let { it.isEmpty() || it == "0" }) { "QQ method failed" }
        return checkNotNull(payload["data"]?.jsonObject) { "QQ data missing" }
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val value = response.use {
                            check(it.isSuccessful) { "QQ HTTP ${it.code}" }
                            val source = checkNotNull(it.body).source()
                            check(!source.request(2 * 1024 * 1024L + 1)) { "QQ response too large" }
                            source.readUtf8()
                        }
                        continuation.resume(value)
                    } catch (e: Exception) { continuation.resumeWithException(e) }
                }
            })
    }
}

private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
