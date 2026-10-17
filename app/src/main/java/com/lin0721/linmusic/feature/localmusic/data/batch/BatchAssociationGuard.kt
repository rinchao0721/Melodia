package com.lin0721.linmusic.feature.localmusic.data.batch

import com.lin0721.linmusic.feature.localmusic.domain.BatchAssociationSaveResult
import com.lin0721.linmusic.feature.localmusic.domain.BatchAssociationSaveStatus

/** Called with the latest preferences inside their edit transaction. Null permits one new reference. */
internal fun batchAssociationGuard(
    songId: Long,
    uri: String,
    identity: String,
    excluded: Set<String>,
    links: Map<Long, String>,
    identityFor: (String) -> String?
): BatchAssociationSaveResult? {
    if (songId.toString() in excluded) return BatchAssociationSaveResult(BatchAssociationSaveStatus.CONFLICT,
        "此网易云歌曲曾被解除关联，请手动确认后再建立")
    links[songId]?.let { current ->
        return if (current == uri || identityFor(current) == identity) {
            BatchAssociationSaveResult(BatchAssociationSaveStatus.ALREADY_LINKED, "此文件已关联同一网易云歌曲")
        } else BatchAssociationSaveResult(BatchAssociationSaveStatus.CONFLICT,
            "此网易云歌曲已关联其他本地文件，保留原关联；可手动更换")
    }
    if (links.values.any { it == uri || identityFor(it) == identity }) return BatchAssociationSaveResult(
        BatchAssociationSaveStatus.CONFLICT, "此本地文件已关联其他网易云歌曲，保留原关联")
    return null
}
