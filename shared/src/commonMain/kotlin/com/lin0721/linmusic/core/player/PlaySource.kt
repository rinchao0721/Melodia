package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.PlaylistId
import com.lin0721.linmusic.core.source.MusicPlatform
import kotlinx.serialization.Serializable

// 当前队列的来源容器，供"正在播放"处显示名称并跳转；与仅用于显示的 playContext 相互独立
@Serializable
data class PlaySource(
    val kind: Kind,
    val id: Long,
    val name: String,
    val coverUrl: String = "",
    val platform: MusicPlatform = MusicPlatform.NETEASE,
    val rawId: String = ""
) {
    val effectiveRawId: String
        get() = rawId.trim().takeIf { it.isNotEmpty() } ?: id.toString()

    val playlistIdOrNull: PlaylistId?
        get() = if (kind == Kind.PLAYLIST && effectiveRawId.isNotBlank()) {
            PlaylistId.of(platform, effectiveRawId)
        } else {
            null
        }

    val albumIdOrNull: AlbumId?
        get() = if (kind == Kind.ALBUM && effectiveRawId.isNotBlank()) {
            AlbumId.of(platform, effectiveRawId)
        } else {
            null
        }

    val radioIdOrNull: Long?
        get() = if (kind == Kind.RADIO && id > 0L) id else null

    enum class Kind { PLAYLIST, ALBUM, RANK, RADIO }
}

sealed interface QueueSourceDestination {
    val label: String

    data class Playlist(val id: Long, val isAlbum: Boolean, override val label: String) : QueueSourceDestination
    data class PlaylistEntity(val entityId: String, val isAlbum: Boolean, override val label: String) : QueueSourceDestination
    data class Radio(val radioId: Long, override val label: String) : QueueSourceDestination
}

// 从队列的 PlaySource 或当前曲目的播客 radioId 解析可点击跳转的来源页
fun resolveQueueSourceDestination(
    playSource: PlaySource?,
    playContext: String?,
    currentItem: QueueItem?
): QueueSourceDestination? {
    if (playSource != null) {
        val label = playSource.name.ifBlank { playContext.orEmpty() }
        when (playSource.kind) {
            PlaySource.Kind.RADIO -> {
                val rid = playSource.id.takeIf { it > 0L } ?: currentItem?.radioId?.takeIf { it > 0L } ?: 0L
                if (rid > 0L) return QueueSourceDestination.Radio(rid, label.ifBlank { "播客" })
            }
            PlaySource.Kind.ALBUM -> {
                if (playSource.platform != MusicPlatform.NETEASE) {
                    playSource.albumIdOrNull?.let {
                        return QueueSourceDestination.PlaylistEntity(it.canonical, isAlbum = true, label = label.ifBlank { "专辑" })
                    }
                } else if (playSource.id > 0L) {
                    return QueueSourceDestination.Playlist(playSource.id, isAlbum = true, label = label.ifBlank { "专辑" })
                }
            }
            PlaySource.Kind.PLAYLIST, PlaySource.Kind.RANK -> {
                if (playSource.platform != MusicPlatform.NETEASE) {
                    if (playSource.kind == PlaySource.Kind.PLAYLIST) {
                        playSource.playlistIdOrNull?.let {
                            return QueueSourceDestination.PlaylistEntity(it.canonical, isAlbum = false, label = label.ifBlank { "歌单" })
                        }
                    } else if (playSource.effectiveRawId.isNotBlank()) {
                        return QueueSourceDestination.PlaylistEntity(playSource.effectiveRawId, isAlbum = false, label = label.ifBlank { "排行榜" })
                    }
                } else if (playSource.id > 0L) {
                    return QueueSourceDestination.Playlist(playSource.id, isAlbum = false, label = label.ifBlank { if (playSource.kind == PlaySource.Kind.RANK) "排行榜" else "歌单" })
                }
            }
        }
    }
    val fallbackRadioId = currentItem?.radioId?.takeIf { it > 0L }
    if (fallbackRadioId != null) {
        val name = currentItem.artist.substringBefore(" · ").ifBlank { "播客" }
        return QueueSourceDestination.Radio(fallbackRadioId, name)
    }
    return null
}
