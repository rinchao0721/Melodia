package com.lin0721.linmusic.core.deeplink

import com.lin0721.linmusic.core.source.SourceHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.concurrent.TimeUnit

// 外链可能指向的具体目标
sealed interface SongLinkTarget {
    data class Song(val id: Long) : SongLinkTarget
    data class Playlist(val id: Long, val isAlbum: Boolean) : SongLinkTarget
    data class Artist(val id: Long) : SongLinkTarget
}

val SongLinkTarget.typeLabel: String
    get() = when (this) {
        is SongLinkTarget.Song -> "单曲"
        is SongLinkTarget.Playlist -> if (isAlbum) "专辑" else "歌单"
        is SongLinkTarget.Artist -> "歌手"
    }

// 网易云分享出去的链接形如 https://music.163.com/song?id=2023954033，
// 也有 RESTful 路径 /song/2023954033、带 #/ 或 m/ 前缀、y.music.163.com 域名、163cn.tv 短链和 orpheus:// 私有协议
object NeteaseLink {

    private const val MAX_REDIRECT_HOPS = 5
    private const val SHORT_LINK_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val shortLinkClient by lazy {
        SourceHttpClient.client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    // 域名后可带 /m/ 与 /#/ 前缀，类型关键字必须在 ? 之前，禁止跨越 ? 匹配查询参数名
    private val QUERY_LINK = Regex(
        """(?:^|[^A-Za-z0-9.])(?:https?://)?(?:y\.)?music\.163\.com/(?:m/)?(?:#/)?(?:m/)?(song|playlist|album|artist)(?:/[^\s?#]*)?\?(?:[^\s#]*&)?id=(\d+)""",
        RegexOption.IGNORE_CASE
    )

    // RESTful 路径形式：music.163.com/song/12345、music.163.com/m/song/12345、y.music.163.com/m/playlist/12345?xxx
    private val PATH_LINK = Regex(
        """(?:^|[^A-Za-z0-9.])(?:https?://)?(?:y\.)?music\.163\.com/(?:m/)?(?:#/)?(?:m/)?(song|playlist|album|artist)/(\d+)(?=$|[^0-9])""",
        RegexOption.IGNORE_CASE
    )

    private val SHORT_LINK = Regex("""https?://163cn\.tv/[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)

    // 网页上「打开 App」按钮用的私有协议，形如 orpheus://song/411314657
    private val ORPHEUS_LINK = Regex("""orpheus://(song|playlist|album|artist)/(\d+)""", RegexOption.IGNORE_CASE)

    private val ALLOWED_SCHEMES = setOf("http", "https", "orpheus")

    private fun toTarget(type: String, id: Long): SongLinkTarget = when (type.lowercase()) {
        "song" -> SongLinkTarget.Song(id)
        "album" -> SongLinkTarget.Playlist(id, isAlbum = true)
        "playlist" -> SongLinkTarget.Playlist(id, isAlbum = false)
        else -> SongLinkTarget.Artist(id)
    }

    private fun hasSupportedSchemeOrBareDomain(text: String): Boolean {
        val schemeMatch = Regex("""([A-Za-z][A-Za-z0-9+.-]*):""").find(text)
        // 如果文本本身以某私有 scheme 开头（例如 javascript: 或 intent:），仅允许 http/https/orpheus
        // 但整段分享文案可能包含 "《歌名》: ..."，因此重点校验 URL 自身的 scheme
        val candidateUrlScheme = Regex("""([A-Za-z][A-Za-z0-9+.-]*)://""").findAll(text)
            .map { it.groupValues[1].lowercase() }
            .toList()
        if (candidateUrlScheme.isEmpty()) {
            // 无 scheme 仅接受 domain 形式（如 music.163.com/song?id=123），排除 javascript: 等
            return schemeMatch == null || !schemeMatch.groupValues[1].equals("javascript", ignoreCase = true)
        }
        return candidateUrlScheme.any { it in ALLOWED_SCHEMES }
    }

    // 从链接或整段分享文案里取歌曲 id，取不到说明不是单曲链接
    fun songId(text: String?): Long? = (target(text) as? SongLinkTarget.Song)?.id

    // 解析链接或分享文案中的网易云目标（支持单曲、歌单、专辑、歌手）
    fun target(text: String?): SongLinkTarget? {
        if (text.isNullOrBlank()) return null
        if (!hasSupportedSchemeOrBareDomain(text)) return null

        ORPHEUS_LINK.find(text)?.let { match ->
            val id = match.groupValues[2].toLongOrNull() ?: return null
            return toTarget(match.groupValues[1], id)
        }
        PATH_LINK.find(text)?.let { match ->
            val id = match.groupValues[2].toLongOrNull() ?: return null
            return toTarget(match.groupValues[1], id)
        }
        QUERY_LINK.find(text)?.let { match ->
            val id = match.groupValues[2].toLongOrNull() ?: return null
            return toTarget(match.groupValues[1], id)
        }
        return null
    }

    fun shortLink(text: String?): String? = text?.let { SHORT_LINK.find(it)?.value }

    // 判断文本是否包含直接可识别链接或待解析短链
    fun containsRecognizableLink(text: String?): Boolean =
        target(text) != null || shortLink(text) != null

    // 短链本身不含 id，跟随重定向最多 5 跳拿最终地址
    suspend fun resolve(shortLink: String): SongLinkTarget? = withContext(Dispatchers.IO) {
        runCatching {
            var currentUrl = shortLink
            repeat(MAX_REDIRECT_HOPS) {
                target(currentUrl)?.let { return@runCatching it }
                val parsed = currentUrl.toHttpUrlOrNull() ?: return@runCatching null
                if (parsed.scheme.lowercase() !in setOf("http", "https")) {
                    return@runCatching null
                }
                val request = Request.Builder()
                    .url(parsed)
                    .header("User-Agent", SHORT_LINK_UA)
                    .get()
                    .build()
                val location = shortLinkClient.newCall(request).execute().use { response ->
                    val finalUrl = response.request.url.toString()
                    target(finalUrl)?.let { return@runCatching it }
                    if (response.code in 300..399) {
                        response.header("Location")
                    } else {
                        null
                    }
                } ?: return@runCatching target(currentUrl)

                if (location.startsWith("orpheus://", ignoreCase = true)) {
                    return@runCatching target(location)
                }
                val resolved = parsed.resolve(location) ?: return@runCatching null
                if (resolved.scheme.lowercase() !in setOf("http", "https")) {
                    return@runCatching null
                }
                currentUrl = resolved.toString()
            }
            target(currentUrl)
        }.getOrNull()
    }
}
