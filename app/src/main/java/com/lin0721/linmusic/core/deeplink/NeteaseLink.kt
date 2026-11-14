package com.lin0721.linmusic.core.deeplink

import java.net.HttpURLConnection
import java.net.URL

// 网易云分享出去的链接形如 https://music.163.com/song?id=2023954033，
// 也有带 #/ 或 m/ 前缀、以及 y.music.163.com 域名和 163cn.tv 短链的情况
object NeteaseLink {

    private fun link(type: String) = Regex(
        """(?:music\.163\.com|y\.music\.163\.com)/\S*?$type\S*?[?&]id=(\d+)""",
        RegexOption.IGNORE_CASE
    )

    private val SONG_LINK = link("song")
    private val PLAYLIST_LINK = link("playlist")
    private val ALBUM_LINK = link("album")
    private val ARTIST_LINK = link("artist")

    private val SHORT_LINK = Regex("""https?://163cn\.tv/[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)

    // 网页上「打开 App」按钮用的私有协议，形如 orpheus://song/411314657
    private val ORPHEUS_LINK = Regex("""orpheus://(song|playlist|album|artist)/(\d+)""", RegexOption.IGNORE_CASE)

    private fun match(regex: Regex, text: String?): Long? =
        text?.let { regex.find(it)?.groupValues?.get(1)?.toLongOrNull() }

    // 从链接或整段分享文案里取歌曲 id，取不到说明不是单曲链接
    fun songId(text: String?): Long? = match(SONG_LINK, text)

    // 歌单、专辑、歌手页的分享链接，打开应用时直接落到对应页面
    fun target(text: String?): SongLinkTarget? {
        if (text.isNullOrBlank()) return null
        ORPHEUS_LINK.find(text)?.let { match ->
            val id = match.groupValues[2].toLongOrNull() ?: return null
            return when (match.groupValues[1].lowercase()) {
                "song" -> SongLinkTarget.Song(id)
                "album" -> SongLinkTarget.Playlist(id, isAlbum = true)
                "playlist" -> SongLinkTarget.Playlist(id, isAlbum = false)
                else -> SongLinkTarget.Artist(id)
            }
        }
        songId(text)?.let { return SongLinkTarget.Song(it) }
        match(PLAYLIST_LINK, text)?.let { return SongLinkTarget.Playlist(it, isAlbum = false) }
        match(ALBUM_LINK, text)?.let { return SongLinkTarget.Playlist(it, isAlbum = true) }
        match(ARTIST_LINK, text)?.let { return SongLinkTarget.Artist(it) }
        return null
    }

    fun shortLink(text: String?): String? = text?.let { SHORT_LINK.find(it)?.value }

    // 短链本身不含 id，只能跟跳转拿最终地址，必须在 IO 线程调用
    fun resolve(shortLink: String): SongLinkTarget? = runCatching {
        var url = shortLink
        repeat(5) {
            target(url)?.let { return@runCatching it }
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 8000
                readTimeout = 8000
            }
            val next = (try {
                val code = connection.responseCode
                if (code in 300..399) {
                    connection.getHeaderField("Location")
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }) ?: return@runCatching target(url)
            url = if (next.startsWith("http")) next else URL(URL(url), next).toString()
        }
        target(url)
    }.getOrNull()
}

// 外链可能指向的具体目标
sealed interface SongLinkTarget {
    data class Song(val id: Long) : SongLinkTarget
    data class Playlist(val id: Long, val isAlbum: Boolean) : SongLinkTarget
    data class Artist(val id: Long) : SongLinkTarget
}
