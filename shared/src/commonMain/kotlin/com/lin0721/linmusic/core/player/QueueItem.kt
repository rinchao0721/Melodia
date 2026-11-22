package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedTrack
import com.lin0721.linmusic.core.platform.toLegacyExternalTrackOrNull
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.serialization.Serializable
import kotlin.math.absoluteValue

enum class PlayMode { LIST_LOOP, SINGLE_LOOP, SHUFFLE }

@Serializable
data class QueueItem(
    val songId: Long,
    val title: String,
    val artist: String,
    val coverUrl: String,
    // 本地外部音频 Uri，非空时直接本地播放
    val localUri: String? = null,
    // 播客节目所属电台 id，非播客为 0
    val radioId: Long = 0,
    // 外部平台歌曲，localUri 为空时由播放器轮到时再经换源插件解析直链
    val external: ExternalTrack? = null,
    // P2 跨平台统一曲目标识与酷狗扩展元数据，旧队列 JSON 缺失时保持 null
    val trackId: TrackId? = null,
    val kugouMeta: KugouTrackMeta? = null
) {
    // 统一实体标识解析：显式 trackId -> 本地路径 -> 外部平台 -> 网易云兜底
    val effectiveTrackId: TrackId
        get() = trackId
            ?: localUri?.takeIf { it.isNotBlank() }?.let(TrackId::local)
            ?: external?.let { ext ->
                val raw = if (ext.platform == MusicPlatform.KUGOU) {
                    ext.hash?.trim()?.takeIf { it.isNotEmpty() } ?: ext.id
                } else {
                    ext.id
                }
                TrackId(ext.platform, raw)
            }
            ?: if (songId >= 0L) {
                TrackId.netease(songId)
            } else {
                TrackId(MusicPlatform.NETEASE, songId.toString())
            }

    val effectivePlatform: MusicPlatform
        get() = effectiveTrackId.platform

    companion object {
        fun fromUnified(track: UnifiedTrack): QueueItem {
            val legacySongId = when (track.platform) {
                MusicPlatform.NETEASE -> track.id.neteaseIdOrNull() ?: 0L
                MusicPlatform.LOCAL -> 0L
                else -> -1L - track.id.canonical.hashCode().toLong().absoluteValue
            }
            val resolvedArtists = track.artistNames.ifBlank {
                track.artists.joinToString(" / ") { it.name }
            }
            val resolvedCover = track.coverUrl.ifBlank {
                track.album?.coverUrl.orEmpty()
            }
            return QueueItem(
                songId = legacySongId,
                title = track.name,
                artist = resolvedArtists,
                coverUrl = resolvedCover,
                localUri = track.localUri,
                external = track.toLegacyExternalTrackOrNull(),
                trackId = track.id,
                kugouMeta = track.kugouMeta
            )
        }
    }
}
