package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.ArtistId
import com.lin0721.linmusic.core.player.QueueItem

data class KugouArtistLink(val id: String, val name: String) {
    val entityId: String get() = ArtistId.kugou(id).canonical
}

// 酷狗曲目可跳转的专辑与歌手规范 id
data class KugouTrackLinks(
    val albumId: String? = null,
    val artists: List<KugouArtistLink> = emptyList()
) {
    val albumEntityId: String? get() = albumId?.let { AlbumId.kugou(it).canonical }
}

// 队列项缺专辑或歌手 id 时补查：hash -> privilege/lite 取专辑 id，专辑详情 authors 取歌手 id
class KugouTrackLinkResolver(
    private val albumIdOfHash: suspend (hash: String) -> String?,
    private val authorsOfAlbum: suspend (albumId: String) -> List<KugouArtistLink>
) {

    // 只用队列项里已有的信息，不请求网络
    fun known(item: QueueItem): KugouTrackLinks {
        var artists = item.kugouArtists
            .filter { validId(it.id) != null }
            .map { KugouArtistLink(it.id.trim(), it.name) }
        if (artists.isEmpty()) {
            validId(item.kugouSingerId)?.let { artists = listOf(KugouArtistLink(it, firstName(item.artist))) }
        }
        return KugouTrackLinks(albumId = validId(item.kugouMeta?.albumId), artists = artists)
    }

    suspend fun resolve(item: QueueItem): KugouTrackLinks {
        val known = known(item)
        var albumId = known.albumId
        var artists = known.artists
        val hash = item.kugouMeta?.hash?.trim().orEmpty().ifEmpty { item.trackId?.rawId?.trim().orEmpty() }
        if (albumId == null && hash.isNotEmpty()) {
            albumId = validId(runCatchingCancellable { albumIdOfHash(hash) }.getOrNull())
        }
        if (artists.isEmpty() && albumId != null) {
            val authors = runCatchingCancellable { authorsOfAlbum(albumId) }.getOrNull().orEmpty()
            artists = matchAuthors(authors, item.artist)
        }
        return KugouTrackLinks(albumId = albumId, artists = artists)
    }

    private fun validId(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() && it != "0" }

    private fun splitNames(artist: String): List<String> =
        artist.split(" / ", "/", "、", ",", "&").map { it.trim() }.filter { it.isNotEmpty() }

    private fun firstName(artist: String): String = splitNames(artist).firstOrNull() ?: artist

    // 专辑作者按歌手名对上歌曲；对不上且只有一位作者时取该作者
    private fun matchAuthors(authors: List<KugouArtistLink>, artist: String): List<KugouArtistLink> {
        val usable = authors.filter { validId(it.id) != null }
        val names = splitNames(artist)
        val matched = usable.filter { author -> names.any { it.equals(author.name, ignoreCase = true) } }
        return matched.ifEmpty { usable.takeIf { it.size == 1 }.orEmpty() }
    }
}
