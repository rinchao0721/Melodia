package com.lin0721.linmusic.core.kugou

import com.lin0721.linmusic.core.platform.AlbumId
import com.lin0721.linmusic.core.platform.KugouTrackMeta
import com.lin0721.linmusic.core.platform.TrackId
import com.lin0721.linmusic.core.platform.UnifiedAlbum
import com.lin0721.linmusic.core.platform.UnifiedArtist
import com.lin0721.linmusic.core.platform.UnifiedTrack
import kotlinx.serialization.Serializable

@Serializable
data class KugouCloudFileDto(
    val kvId: Long,
    val fileId: Long = kvId,
    val hash: String,
    val name: String,
    val singerName: String = "",
    val albumName: String = "",
    val albumId: String = "",
    val albumAudioId: Long = 0L,
    val audioId: Long = 0L,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val bitrate: Int = 0,
    val extName: String = "",
    val coverUrl: String = "",
    val addTimeMs: Long = 0L
) {
    fun toUnifiedTrack(): UnifiedTrack {
        val artists = singerName.split("、", "/", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { UnifiedArtist(name = it) }
        val album = if (albumName.isNotBlank() || albumId.isNotBlank()) {
            UnifiedAlbum(
                id = albumId.takeIf { it.isNotBlank() && it != "0" }?.let { AlbumId.kugou(it) },
                name = albumName,
                coverUrl = coverUrl
            )
        } else {
            null
        }
        return UnifiedTrack(
            id = TrackId.kugou(hash),
            name = name,
            artists = artists,
            artistNames = artists.joinToString(" / ") { it.name },
            album = album,
            durationMs = durationMs,
            coverUrl = coverUrl,
            publishTimeMs = addTimeMs,
            kugouMeta = KugouTrackMeta(
                hash = hash.lowercase(),
                albumAudioId = albumAudioId.takeIf { it > 0L },
                albumId = albumId.takeIf { it.isNotBlank() && it != "0" },
                playlistFileId = fileId.takeIf { it > 0L },
                playlistAddTimeMs = addTimeMs.takeIf { it > 0L },
                isCloudTrack = true,
                cloudAudioId = audioId.takeIf { it > 0L },
                cloudName = name,
                cloudKvId = kvId.takeIf { it > 0L }
            )
        )
    }
}

@Serializable
data class KugouCloudQuotaDto(
    val usedBytes: Long = 0L,
    val maxBytes: Long = 0L,
    val totalCount: Int = 0
)

@Serializable
data class KugouCloudListPage(
    val items: List<KugouCloudFileDto> = emptyList(),
    val quota: KugouCloudQuotaDto = KugouCloudQuotaDto(),
    val page: Int = 1,
    val hasMore: Boolean = false
)

@Serializable
data class KugouCloudUrlResult(
    val hash: String,
    val url: String = "",
    val backupUrls: List<String> = emptyList(),
    val extName: String = "",
    val fileSize: Long = 0L,
    val bitRate: Int = 0,
    val durationMs: Long = 0L
)

@Serializable
data class KugouCloudDeleteItem(
    val kvId: Long,
    val albumAudioId: Long = 0L
)
