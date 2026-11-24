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

    enum class Kind { PLAYLIST, ALBUM, RANK }
}
