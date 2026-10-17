package com.lin0721.linmusic.feature.localmusic.data

import com.lin0721.linmusic.feature.localmusic.data.db.LocalTrackEntity
import com.lin0721.linmusic.feature.localmusic.data.metadata.WavMetadata
import com.lin0721.linmusic.feature.localmusic.data.metadata.mergeWavMetadata
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource

data class LocalLibrarySyncDiff(
    val upserts: List<LocalTrackEntity>,
    val deleteUris: Set<String>
)

// 保留 SAF 引用、来源、关联以及原有日期/音频属性，不能用一次标签补读重建导入记录。
internal fun mergeImportedWavTags(existing: LocalTrackEntity, embedded: WavMetadata): LocalTrackEntity {
    val tags = mergeWavMetadata(WavMetadata(existing.title, existing.artist, existing.album,
        existing.albumArtist, existing.year, existing.trackNumber), embedded)
    return existing.copy(
        title = tags.title ?: existing.title,
        artist = tags.artist ?: existing.artist,
        album = tags.album,
        albumArtist = tags.albumArtist,
        year = tags.year,
        trackNumber = tags.trackNumber
    )
}

// 只写入变化的条目；导入条目扫不到不代表已删，由 isImportedAlive 判断
fun computeLocalLibrarySyncDiff(
    existing: List<LocalTrackEntity>,
    scanned: List<LocalTrackEntity>,
    isImportedAlive: (LocalTrackEntity) -> Boolean
): LocalLibrarySyncDiff {
    val existingByUri = existing.associateBy { it.uri }
    val scannedUris = scanned.mapTo(HashSet()) { it.uri }

    val upserts = scanned.filter { existingByUri[it.uri] != it }
    val deleteUris = existing.asSequence()
        .filter { it.uri !in scannedUris }
        .filter { it.source != LocalTrackSource.IMPORTED.name || !isImportedAlive(it) }
        .mapTo(HashSet()) { it.uri }

    return LocalLibrarySyncDiff(upserts, deleteUris)
}
