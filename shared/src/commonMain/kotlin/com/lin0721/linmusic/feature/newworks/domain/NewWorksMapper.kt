package com.lin0721.linmusic.feature.newworks.domain

import com.lin0721.linmusic.feature.newworks.data.NewWorksMvDto
import com.lin0721.linmusic.feature.newworks.data.NewWorksReleaseItem

fun NewWorksMvDto.toDomain(): NewWorksMv = NewWorksMv(
    id = mvId,
    name = mvName,
    coverUrl = mvCoverUrl,
    durationMs = duration,
    playCount = playCount,
    artistName = artistName
)

// blockType 非 song/album（未知新类型）时返回 null，交给上层过滤，不硬渲染陌生结构
fun NewWorksReleaseItem.toReleaseDomain(): NewWorksRelease? {
    val blockTitle = info.blockTitle
    val isAlbum = info.blockType == "album"
    if (info.blockType != "song" && !isAlbum) return null

    val firstTrack = info.songLists.firstOrNull()
    val id = if (isAlbum) blockTitle.resourceId else (firstTrack?.id ?: blockTitle.resourceId)
    if (id <= 0) return null

    val cover = blockTitle.resourcePicUrl ?: firstTrack?.al?.picUrl ?: blockTitle.imgUrl
    val artistName = firstTrack?.ar?.joinToString(" / ") { it.name }
        ?.ifBlank { null }
        ?: blockTitle.artistName

    return NewWorksRelease(
        id = id,
        title = blockTitle.resourceName,
        coverUrl = cover,
        artistName = artistName,
        isAlbum = isAlbum,
        trackCount = if (isAlbum) info.albumSongCount else 1
    )
}
