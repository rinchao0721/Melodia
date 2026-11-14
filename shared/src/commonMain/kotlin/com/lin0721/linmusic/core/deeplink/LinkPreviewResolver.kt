package com.lin0721.linmusic.core.deeplink

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.feature.artist.data.ArtistRepository
import com.lin0721.linmusic.feature.playlist.data.PlaylistRepository
import kotlinx.coroutines.flow.firstOrNull

sealed interface LinkPreviewState {
    data object Loading : LinkPreviewState
    data class Ready(val preview: LinkPreview) : LinkPreviewState
    data object Failed : LinkPreviewState
}

data class LinkPreview(
    val target: SongLinkTarget,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val typeLabel: String,
    val isCircleCover: Boolean = false,
    val track: Track? = null
)

// 为外链或剪贴板目标异步拉取轻量预览元信息，供浮条与搜索直达卡片展示
class LinkPreviewResolver(
    private val playbackRepository: PlaybackRepository,
    private val playlistRepository: PlaylistRepository,
    private val artistRepository: ArtistRepository
) {

    suspend fun resolve(target: SongLinkTarget): LinkPreview? = runCatching {
        when (target) {
            is SongLinkTarget.Song -> {
                val track = playbackRepository.getSongDetail(target.id).firstOrNull()?.getOrNull()
                    ?: return@runCatching null
                val artists = track.ar.joinToString(" / ") { it.name }
                val subtitle = listOf(artists, track.al.name)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                LinkPreview(
                    target = target,
                    title = track.name.ifBlank { "未知歌曲" },
                    subtitle = subtitle,
                    coverUrl = track.al.picUrl,
                    typeLabel = target.typeLabel,
                    track = track
                )
            }

            is SongLinkTarget.Playlist -> {
                if (target.isAlbum) {
                    val detail = playlistRepository.getAlbumDetail(target.id).firstOrNull()?.getOrNull()
                        ?: return@runCatching null
                    val artists = detail.artists.joinToString(" / ") { it.name }
                    val count = detail.trackCount.takeIf { it > 0 } ?: detail.tracks.size
                    val subtitle = listOfNotNull(
                        artists.takeIf { it.isNotBlank() },
                        count.takeIf { it > 0 }?.let { "${it} 首" }
                    ).joinToString(" · ")
                    LinkPreview(
                        target = target,
                        title = detail.name.ifBlank { "未知专辑" },
                        subtitle = subtitle,
                        coverUrl = detail.coverImgUrl,
                        typeLabel = target.typeLabel
                    )
                } else {
                    val detail = playlistRepository.getPlaylistDetail(target.id).firstOrNull()?.getOrNull()
                        ?: return@runCatching null
                    val count = detail.trackIds.size.takeIf { it > 0 }
                        ?: detail.tracks.size.takeIf { it > 0 }
                        ?: detail.trackCount
                    val creator = detail.creator?.nickname.orEmpty()
                    val subtitle = listOfNotNull(
                        count.takeIf { it > 0 }?.let { "${it} 首" },
                        creator.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    LinkPreview(
                        target = target,
                        title = detail.name.ifBlank { "未知歌单" },
                        subtitle = subtitle,
                        coverUrl = detail.coverImgUrl,
                        typeLabel = target.typeLabel
                    )
                }
            }

            is SongLinkTarget.Artist -> {
                val detail = artistRepository.getArtistDetail(target.id).firstOrNull()?.getOrNull()
                    ?: return@runCatching null
                val tags = detail.identifyTag?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                val counts = listOfNotNull(
                    detail.musicSize.takeIf { it > 0 }?.let { "${it} 首单曲" },
                    detail.albumSize.takeIf { it > 0 }?.let { "${it} 张专辑" }
                ).joinToString(" · ")
                LinkPreview(
                    target = target,
                    title = detail.name.ifBlank { "未知歌手" },
                    subtitle = tags ?: counts,
                    coverUrl = detail.avatar.ifBlank { detail.cover },
                    typeLabel = target.typeLabel,
                    isCircleCover = true
                )
            }
        }
    }.getOrNull()
}
