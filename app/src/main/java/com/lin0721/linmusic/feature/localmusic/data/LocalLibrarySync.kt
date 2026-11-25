package com.lin0721.linmusic.feature.localmusic.data

import com.lin0721.linmusic.core.localmusic.db.LocalTrackEntity
import com.lin0721.linmusic.feature.localmusic.data.metadata.WavMetadata
import com.lin0721.linmusic.feature.localmusic.data.metadata.mergeWavMetadata
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource

data class LocalLibrarySyncDiff(
    val upserts: List<LocalTrackEntity>,
    val deleteUris: Set<String>
)

// 保留 SAF 引用、来源、关联以及原有日�?音频属性，不能用一次标签补读重建导入记录�?
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

// 扫描耗时较长，期间被删除或改动的条目不写回，留给下次同步
internal fun stableImportUpdates(
    snapshot: List<LocalTrackEntity>,
    current: List<LocalTrackEntity>,
    refreshed: List<LocalTrackEntity>
): List<LocalTrackEntity> {
    val snapshotByUri = snapshot.associateBy { it.location }
    val currentByUri = current.associateBy { it.location }
    return refreshed.filter { update ->
        val row = currentByUri[update.location]
        row != null && row == snapshotByUri[update.location]
    }
}

// 只写入变化的条目；导入条目扫不到不代表已删，�?isImportedAlive 判断
fun computeLocalLibrarySyncDiff(
    existing: List<LocalTrackEntity>,
    scanned: List<LocalTrackEntity>,
    isImportedAlive: (LocalTrackEntity) -> Boolean
): LocalLibrarySyncDiff {
    val existingByUri = existing.associateBy { it.location }
    val scannedUris = scanned.mapTo(HashSet()) { it.location }

    val upserts = scanned.filter { existingByUri[it.location] != it }
    val deleteUris = existing.asSequence()
        .filter { it.location !in scannedUris }
        .filter { it.source != LocalTrackSource.IMPORTED.name || !isImportedAlive(it) }
        .mapTo(HashSet()) { it.location }

    return LocalLibrarySyncDiff(upserts, deleteUris)
}
